import com.sun.tools.attach.VirtualMachine;
import com.zaxxer.hikari.HikariDataSource;
import io.netty.channel.EventLoopGroup;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.coroutines.ContinuationInterceptor;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.ExecutorCoroutineDispatcher;
import kotlinx.coroutines.Job;

/** One-time bridge for the installed Central 2.0.1 lifecycle defect; invokes existing native owners. */
public final class CentralLegacyNativeHandover {
    static final Path ROOT = Path.of("C:/Users/norma/Documents/Codex/2026-08-10/lets-work-on-the-openrune-server/work/OpenRune-Server");
    static final Path LIVE_LOG = ROOT.resolve("logs/crash-recovery-20260913-025956/restored.stdout.log");
    static final String LINK = "org.rsmod.api.net.central.OpenRuneCentralWorldLink";
    static final SessionSlot<Session> RETAINED = new SessionSlot<>();

    public static void main(String[] args) throws Exception {
        if ((args.length != 2 && args.length != 3) || (args.length == 3 && !args[2].equals("inspect")) || !Path.of("").toAbsolutePath().equals(ROOT))
            throw new IllegalArgumentException("pid agent-jar [inspect] from canonical root");
        boolean inspect = args.length == 3;
        String nonce = UUID.randomUUID().toString();
        Path receipt = receipt(nonce);
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try {
            Properties props = vm.getSystemProperties();
            require(Path.of(props.getProperty("user.dir")).equals(ROOT), "Wrong workspace");
            require(props.getProperty("sun.java.command", "").startsWith("org.rsmod.server.app.GameServerKt"), "Wrong server");
            try { vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(), (inspect ? "inspect:" : "") + nonce); }
            catch (java.io.IOException exited) { if (inspect || !Files.exists(receipt)) throw exited; }
            if (inspect) { System.out.println("Read-only native owner inspection succeeded; no handover requested"); return; }
            require(Files.readString(receipt).equals(args[0] + "|" + ROOT), "Missing exact fresh handover receipt");
            System.out.println("Verified native legacy Central handover: " + receipt);
        } finally {
            try { vm.detach(); } catch (java.io.IOException exited) { if (inspect || !Files.exists(receipt)) throw exited; }
        }
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) throws Exception {
        boolean inspect = arguments.startsWith("inspect:");
        String nonce = inspect ? arguments.substring("inspect:".length()) : arguments;
        require(Path.of(System.getProperty("user.dir")).equals(ROOT), "Wrong target root");
        require(!Files.exists(receipt(nonce)), "Reused receipt");
        instrumentation.redefineModule(Object.class.getModule(), Set.of(), Map.of(),
            Map.of("java.lang", Set.of(CentralLegacyNativeHandover.class.getModule()),
                "java.util.concurrent", Set.of(CentralLegacyNativeHandover.class.getModule())), Set.of(), Map.of());
        final Attempt<Session> attempt;
        synchronized (RETAINED) {
            RETAINED.requireUnused();
            Session captured = new Session(bootstrap());
            if (inspect) {
                // No slot claim, countdown, queue consumption, executor shutdown, or receipt.
                // Session capture reads native owners and runs read-only authority observations.
                System.out.println("OPENRUNE LEGACY CENTRAL INSPECTION OK: injectionPaths="
                    + captured.clients.stream().mapToInt(client -> client.paths.size()).sum()
                    + " uniqueClients=" + captured.clients.size()
                    + " liveWatchers=" + captured.clients.stream().filter(client -> client.watcher != null && client.watcher.isAlive()).count()
                    + " nativeServiceExecutors=3 centralOwners=" + captured.owners.size()
                    + " notifierAlive=" + captured.notifier.isAlive()
                    + " activityDatasourceOpen=" + !captured.activityDataSource.isClosed()
                    + " retainedSession=" + (RETAINED.get() != null));
                return;
            }
            // Resolve every required owner before the first native mutation. Keep the complete
            // session strongly reachable even after Koin clears its registry or a stage fails.
            attempt = RETAINED.claim(captured);
        }
        Session session = attempt.owner;
        Thread thread = new Thread(() -> runRetained(attempt, () -> {
            CompletableFuture<?> saved = prepareOnOwner(session.gameExecutor,
                () -> session.checkAuthority(),
                () -> (CompletableFuture<?>) invoke(session.bootstrap, "prepareShutdown"));
            handover(saved,
                () -> closeIngress(session.network),
                () -> drainAcceptedWork(session),
                () -> stopClients(session.clients),
                () -> drainAcceptedWork(session),
                () -> stopActivityWriter(session),
                () -> closeCentral(session.resources, session.owners, session.notifier),
                () -> {
                    verifyQuiescent(session);
                    invoke(session.bootstrap, "shutdown");
                    require(!ProcessHandle.of(session.postgres).map(ProcessHandle::isAlive).orElse(false), "PostgreSQL still alive");
                    for (int port : new int[]{43594, 8080, 9091}) requirePortClosed(port);
                },
                () -> {
                    Files.writeString(receipt(nonce), ProcessHandle.current().pid() + "|" + ROOT, StandardOpenOption.CREATE_NEW);
                    System.out.println("OPENRUNE LEGACY CENTRAL HANDOVER COMPLETE: native saves, ingress, Central, services and PostgreSQL closed");
                });
        }), "openrune-legacy-central-handover");
        attempt.worker = thread;
        thread.setDaemon(false);
        try { thread.start(); }
        catch (Throwable failure) { attempt.completion.completeExceptionally(failure); }
        attempt.completion.get();
        // No System.exit: native bootstrap's successful cleanup permits the JVM to finish normally.
    }

    @FunctionalInterface interface Step { void run() throws Exception; }
    static void handover(CompletableFuture<?> saved, Step... stages) throws Exception {
        saved.get(90, TimeUnit.SECONDS);
        for (Step stage : stages) stage.run();
    }

    static final class Attempt<T> {
        final T owner;
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        volatile Thread worker;
        Attempt(T owner) { this.owner = Objects.requireNonNull(owner); }
    }
    static final class SessionSlot<T> {
        private Attempt<T> retained;
        synchronized void requireUnused() { require(retained == null, "A handover session already exists; its native owners and terminal result are retained. No retry is permitted."); }
        synchronized Attempt<T> claim(T owner) { requireUnused(); return retained = new Attempt<>(owner); }
        synchronized Attempt<T> get() { return retained; }
    }
    static void runRetained(Attempt<?> attempt, Step action) {
        try { action.run(); attempt.completion.complete(null); }
        catch (Throwable failure) { attempt.completion.completeExceptionally(failure); }
    }
    static CompletableFuture<?> prepareOnOwner(ExecutorService owner, Step authority, Callable<CompletableFuture<?>> prepare) throws Exception {
        return owner.submit(() -> { authority.run(); return prepare.call(); }).get(10, TimeUnit.SECONDS);
    }

    static final class ClientOwner {
        final Object client;
        final ExecutorService executor;
        final ThreadPoolExecutor pool;
        final Thread watcher;
        final List<String> paths = new ArrayList<>();
        ClientOwner(Object client) throws Exception {
            this.client = exact(client, LINK);
            executor = (ExecutorService) field(client, "authExecutor");
            pool = poolOf(executor);
            require(!executor.isShutdown(), "Central auth owner already closing");
            watcher = (Thread) field(client, "inboundWatchThread");
        }
    }

    static final class Session {
        final Object bootstrap, game, resources, network, loader, dbSync, gateway, activity;
        final ExecutorService gameExecutor, loaderExecutor, gatewayExecutor, activityExecutor;
        final ThreadPoolExecutor gatewayWorkers, activityPool;
        final CoroutineScope gatewayWorkerScope;
        final HikariDataSource activityDataSource;
        final Thread notifier;
        final Map<String, Object> owners;
        final List<ClientOwner> clients;
        final long postgres;

        Session(Object bootstrap) throws Exception {
            this.bootstrap = bootstrap;
            game = exact(field(bootstrap, "gameService"), "org.rsmod.server.app.GameService");
            resources = exact(field(bootstrap, "resources"), "org.rsmod.server.app.GameBootstrap$1");
            Object manager = field(bootstrap, "serviceManager");
            Collection<?> services = (Collection<?>) field(manager, "services");
            gameExecutor = nativeExecutor(manager, game);
            loader = uniqueService(services, "org.rsmod.api.account.loader.AccountLoaderService");
            loaderExecutor = nativeExecutor(manager, loader);
            network = field(uniqueService(services, "org.rsmod.api.net.rsprot.RspService"), "service");
            Object process = exact(field(game, "process"), "org.rsmod.api.game.process.MainGameProcess");
            dbSync = field(process, "dbSync");
            gateway = field(dbSync, "gateway");
            require(gateway == uniqueService(services, "org.rsmod.api.db.gateway.service.ResponseDbGatewayService"), "Different gateway owner");
            gatewayExecutor = nativeExecutor(manager, gateway);
            gatewayWorkers = poolOf((ExecutorService) field(gateway, "workerExecutor"));
            gatewayWorkerScope = (CoroutineScope) field(gateway, "workerScope");
            require(gatewayWorkerScope.getCoroutineContext().get(Job.Key) != null, "Gateway worker root unavailable");
            owners = captureOwners();
            notifier = (Thread) field(owners.get("notify"), "thread");
            require(notifier != null && notifier.isAlive(), "Original Central notifier unavailable");
            require(field(owners.get("notify"), "dataSource") == owners.get("datasource"), "Unrelated Central datasource");
            poolOf((ExecutorService) field(owners.get("social"), "executor"));
            require(owners.get("world").getClass().getProtectionDomain().getCodeSource().getLocation().toString().contains("openrune-central-2.0.1.jar"), "Bridge is only for installed Central 2.0.1");
            require(Files.readString(LIVE_LOG).contains("Discord bot disabled (no bot token configured)"), "Unexpected active Discord producer");
            requireNoWriteFailures();
            // Script registration is immutable after startup; read it on the native game owner.
            Object[] scripts = gameExecutor.submit(() -> captureScripts(process)).get(10, TimeUnit.SECONDS);
            activity = exact(field(scripts[0], "centralActivityLogWriter"), "org.rsmod.api.net.central.logging.CentralActivityLogWriter");
            activityExecutor = (ExecutorService) field(activity, "executor");
            activityPool = poolOf(activityExecutor);
            require(!activityExecutor.isShutdown(), "Activity logger already closing");
            activityDataSource = (HikariDataSource) field(activity, "dataSource");
            require(activityDataSource != null && !activityDataSource.isClosed(), "Original activity datasource unavailable");
            clients = captureClients(resources, network, scripts, activity, field(game, "gameUpdate"));
            postgres = Long.parseLong(Files.readAllLines(ROOT.resolve(".data/postgres/postmaster.pid")).getFirst());
            gameExecutor.submit(() -> { checkAuthority(); return null; }).get(10, TimeUnit.SECONDS);
        }

        void checkAuthority() throws Exception {
            require(!((AtomicBoolean) field(bootstrap, "stopRequested")).get() && field(bootstrap, "cleanupFailure") == null, "Bootstrap already stopping or failed");
            require(!((AtomicBoolean) field(game, "shutdownSignaled")).get() && !Boolean.TRUE.equals(field(game, "drainComplete")), "Game already quiescing");
            require(((java.util.concurrent.atomic.AtomicReference<?>) field(game, "drainRequest")).get() == null, "Native shutdown preflight already requested");
            for (Object player : (Iterable<?>) field(game, "players"))
                require(((Number) invoke(player, "getCharacterId")).intValue() == 1, "Unrelated online player; no logout requested");
            Object registry = field(owners.get("world"), "registry");
            for (Object id : (Iterable<?>) invoke(registry, "registeredWorldIds"))
                require(((Number) id).intValue() == 255, "Unrelated Central world; no shutdown requested");
        }
    }

    static Object[] captureScripts(Object process) throws Exception {
        Object bus = field(process, "eventBus");
        Object unbound = invoke(bus, "getUnbound");
        Object shutdownCallbacks = unbound.getClass().getMethod("get", Class.class).invoke(unbound,
            Class.forName("org.rsmod.api.game.process.GameLifecycle$Shutdown"));
        require(shutdownCallbacks instanceof Collection<?>, "Native shutdown callbacks missing");
        Object networkScript = directCapturedOwner((Collection<?>) shutdownCallbacks, "org.rsmod.api.net.rsprot.NetworkScript");
        Object keyed = invoke(bus, "getKeyed");
        Object loginCallback = keyed.getClass().getMethod("get", Class.class, long.class).invoke(keyed,
            Class.forName("org.rsmod.game.entity.player.SessionStateEvent$EngineLogin"), 0L);
        require(loginCallback != null, "Native engine login callback missing");
        Object loginScript = directCapturedOwner(List.of(loginCallback), "org.rsmod.content.other.login.LoginScript");
        return new Object[]{networkScript, loginScript};
    }

    static Object directCapturedOwner(Collection<?> callbacks, String ownerClass) throws Exception {
        // NetworkScript.startup and LoginScript.startup pass `this` directly to an invokedynamic
        // Function1 lambda (verified against native bytecode). Inspect only that one capture level.
        Set<Object> owners = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object callback : callbacks) for (Field capture : callback.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(capture.getModifiers()) || !capture.getType().getName().equals(ownerClass)) continue;
            capture.setAccessible(true);
            owners.add(exact(capture.get(callback), ownerClass));
        }
        require(owners.size() == 1, "Missing or ambiguous native callback owner: " + ownerClass);
        return owners.iterator().next();
    }

    static List<ClientOwner> captureClients(Object resources, Object network, Object[] scripts, Object activity, Object gameUpdate) throws Exception {
        // Exhaustive constructor-use list at live 9e592b389: Bootstrap, embedded lifecycle,
        // ConnectionHandler (also passes this same client to AccountLoadResponseHook), the two
        // scripts, and six handler-specific CentralSocialService instances. No @Singleton or
        // explicit binding exists for that revision's WorldLink/CentralSocialService classes.
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("bootstrap.resources.$openRuneCentral", field(resources, "$openRuneCentral"));
        Object embedded = exact(field(resources, "$centralEmbedded"), "org.rsmod.api.net.central.embed.CentralEmbeddedLifecycle");
        paths.put("bootstrap.resources.$centralEmbedded.openRuneCentral", field(embedded, "openRuneCentral"));
        Object connection = exact(field(network, "gameConnectionHandler"), "org.rsmod.api.net.rsprot.ConnectionHandler");
        paths.put("network.gameConnectionHandler.openRuneCentral", field(connection, "openRuneCentral"));
        require(field(scripts[0], "service") == network, "NetworkScript belongs to another network");
        paths.put("GameLifecycle.Shutdown.NetworkScript.openRuneCentral", field(scripts[0], "openRuneCentral"));
        paths.put("EngineLogin[0].LoginScript.openRuneCentral", field(scripts[1], "openRuneCentral"));

        Object provider = exact(field(network, "gameMessageConsumerRepositoryProvider"),
            "net.rsprot.protocol.message.codec.incoming.provider.DefaultGameMessageConsumerRepositoryProvider");
        Object repository = exact(field(provider, "repository"), "net.rsprot.protocol.message.codec.incoming.GameMessageConsumerRepository");
        Map<?, ?> consumers = (Map<?, ?>) invoke(repository, "getConsumers");
        require(((Collection<?>) invoke(repository, "getGlobalConsumers")).isEmpty(), "Unexpected global network consumers");
        String prefix = "org.rsmod.api.net.rsprot.handlers.";
        Set<String> expected = Set.of("FriendListAddHandler", "FriendListDeleteHandler", "IgnoreListAddHandler",
            "IgnoreListDeleteHandler", "MessagePrivateHandler", "SetChatFilterSettingsHandler");
        for (String name : expected) {
            Object handler = uniqueService(consumers.values(), prefix + name);
            Object social = exact(field(handler, "social"), "org.rsmod.api.net.central.CentralSocialService");
            paths.put("network.consumers." + name + ".social.central", field(social, "central"));
        }
        // A bounded completeness check over the native registered handler fields, not a graph walk.
        for (Object handler : consumers.values()) for (Field dependency : handler.getClass().getDeclaredFields()) {
            if (dependency.getType().getName().equals("org.rsmod.api.net.central.CentralSocialService"))
                require(expected.stream().anyMatch(name -> handler.getClass().getName().equals(prefix + name)), "Unexpected Central social handler owner");
            require(!dependency.getType().getName().equals(LINK), "Unexpected direct Central handler owner");
        }
        Object cheat = uniqueService(consumers.values(), prefix + "ClientCheatHandler");
        require(field(cheat, "centralActivityLogWriter") == activity, "Activity writer singleton identity mismatch");
        require(paths.size() == 11, "Incomplete native client provenance list");
        IdentityHashMap<Object, ClientOwner> deduped = new IdentityHashMap<>();
        List<ClientOwner> result = new ArrayList<>();
        for (var entry : paths.entrySet()) {
            Object client = exact(entry.getValue(), LINK);
            require(field(client, "gameUpdate") == gameUpdate, "Client belongs to another game update owner");
            require(field(client, "serverConfig") == field(resources, "$serverConfig"), "Client belongs to another server config");
            ClientOwner owner = deduped.get(client);
            if (owner == null) { owner = new ClientOwner(client); deduped.put(client, owner); result.add(owner); }
            owner.paths.add(entry.getKey());
        }
        return List.copyOf(result);
    }

    static Object uniqueService(Collection<?> values, String name) {
        List<?> matching = values.stream().filter(value -> value.getClass().getName().equals(name)).toList();
        require(matching.size() == 1, "Missing or ambiguous native owner: " + name);
        return matching.getFirst();
    }

    static ExecutorService nativeExecutor(Object manager, Object service) throws Exception {
        CoroutineScope scope = (CoroutineScope) ((Map<?, ?>) field(manager, "activeScopes")).get(service);
        require(scope != null, "Native service scope unavailable");
        Object dispatcher = scope.getCoroutineContext().get(ContinuationInterceptor.Key);
        require(dispatcher instanceof ExecutorCoroutineDispatcher, "Unexpected native service dispatcher");
        ExecutorService executor = (ExecutorService) ((ExecutorCoroutineDispatcher) dispatcher).getExecutor();
        require(executor != null && !executor.isShutdown(), "Native service executor unavailable");
        return executor;
    }

    static ThreadPoolExecutor poolOf(ExecutorService executor) throws Exception {
        Object current = executor;
        for (int depth = 0; depth < 3; depth++) {
            if (current instanceof ThreadPoolExecutor pool) return pool;
            require(Set.of("java.util.concurrent.Executors$AutoShutdownDelegatedExecutorService",
                "java.util.concurrent.Executors$FinalizableDelegatedExecutorService",
                "java.util.concurrent.Executors$DelegatedExecutorService").contains(current.getClass().getName()), "Unexpected native executor wrapper");
            current = field(current, "e");
        }
        throw new IllegalStateException("Native executor pool unavailable");
    }

    static Object exact(Object value, String name) {
        require(value != null && value.getClass().getName().equals(name), "Unexpected or absent owner: " + name);
        return value;
    }

    static void closeIngress(Object network) throws Exception {
        Object future = network.getClass().getMethod("shutdown", long.class, long.class, TimeUnit.class).invoke(network, 2L, 15L, TimeUnit.SECONDS);
        ((CompletableFuture<?>) future).get(30, TimeUnit.SECONDS);
        for (String name : List.of("bossGroup", "childGroup")) awaitGroup((EventLoopGroup) field(network, name));
        requirePortClosed(43594);
    }

    static void drainAcceptedWork(Session session) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (true) {
            session.gameExecutor.submit(() -> {
                require(Boolean.TRUE.equals(field(session.game, "drainComplete")), "Native world preflight did not quiesce");
                Object accounts = field(session.game, "accounts");
                accounts.getClass().getMethod("handleLogins", int.class).invoke(accounts, Integer.MAX_VALUE);
                invokeDbCallbacks(session.dbSync, session.gateway);
                return null;
            }).get(10, TimeUnit.SECONDS);
            // Both services poll work into mutable batches/worker jobs. Sample on the actual
            // scheduling executors so an in-progress poll cannot masquerade as an empty queue.
            boolean loaded = session.loaderExecutor.submit(() -> loaderEmpty(session.loader)).get(10, TimeUnit.SECONDS);
            boolean gatewayEmpty = session.gatewayExecutor.submit(() -> gatewayEmpty(session)).get(10, TimeUnit.SECONDS);
            boolean callbacksEmpty = session.gameExecutor.submit(() -> callbackQueuesEmpty(session)).get(10, TimeUnit.SECONDS);
            if (loaded && gatewayEmpty && callbacksEmpty) break;
            require(System.nanoTime() < deadline, "Accepted loader/database work did not drain; Central remains live");
            Thread.sleep(100);
        }
        verifyQuiescent(session);
    }

    static void invokeDbCallbacks(Object synchronizer, Object gateway) throws Exception {
        require(((Collection<?>) field(synchronizer, "callbackBuffer")).isEmpty(), "Native callback buffer retained a previous failure");
        // Game preflight has stopped world ticks. Only this game-owner action consumes the queue;
        // worker threads can append. Deliver the observed prefix through the native dispatcher,
        // then refuse further shutdown if any accepted database operation returned an error.
        List<?> callbacks = new ArrayList<>((Collection<?>) field(gateway, "pendingCallbacks"));
        boolean failed = false;
        Class<?> error = Class.forName("org.rsmod.api.db.gateway.model.GameDbResult$Err");
        for (Object callback : callbacks) failed |= error.isInstance(invoke(callback, "getResult"));
        synchronizer.getClass().getMethod("invokeCallbacks", int.class).invoke(synchronizer, callbacks.size());
        require(!failed, "An accepted database request failed; native callbacks delivered, handover retained");
    }

    static boolean callbackQueuesEmpty(Session session) throws Exception {
        Object accounts = field(session.game, "accounts");
        return ((Collection<?>) field(accounts, "pendingLogins")).isEmpty()
            && ((Collection<?>) field(accounts, "pendingLogouts")).isEmpty()
            && ((Collection<?>) field(session.dbSync, "callbackBuffer")).isEmpty()
            && ((Collection<?>) field(session.gateway, "pendingCallbacks")).isEmpty();
    }

    static boolean gatewayEmpty(Session session) throws Exception {
        Job workers = session.gatewayWorkerScope.getCoroutineContext().get(Job.Key);
        return !workers.getChildren().iterator().hasNext()
            && session.gatewayWorkers.getActiveCount() == 0 && session.gatewayWorkers.getQueue().isEmpty()
            && ((Collection<?>) field(session.gateway, "pendingRequests")).isEmpty()
            && ((Collection<?>) field(session.gateway, "pendingCallbacks")).isEmpty();
    }

    static void verifyQuiescent(Session session) throws Exception {
        session.gameExecutor.submit(() -> {
            require(Boolean.TRUE.equals(field(session.game, "drainComplete")), "Native game no longer quiescent");
            checkAccounts(session.game);
            require(callbackQueuesEmpty(session), "Native callback queue is not empty");
            return null;
        }).get(10, TimeUnit.SECONDS);
        require(session.loaderExecutor.submit(() -> loaderEmpty(session.loader)).get(10, TimeUnit.SECONDS), "Loader still active");
        require(session.gatewayExecutor.submit(() -> gatewayEmpty(session)).get(10, TimeUnit.SECONDS), "Gateway still active");
        requireNoWriteFailures();
    }

    static void checkAccounts(Object game) throws Exception {
        require(!((Iterable<?>) field(game, "players")).iterator().hasNext(), "Player remains attached");
        Object saver = field(game, "saver");
        require(Boolean.TRUE.equals(invoke(invoke(saver, "drainStatus"), "isDrained")), "Native save did not commit");
        Object accounts = field(game, "accounts");
        require(Boolean.TRUE.equals(invoke(invoke(accounts, "logoutStatus"), "isDrained")), "Native logout incomplete");
        for (String name : List.of("pendingLogins", "pendingLogouts")) require(((Collection<?>) field(accounts, name)).isEmpty(), "Pending account callback");
    }

    static boolean loaderEmpty(Object loader) throws Exception {
        return ((Collection<?>) field(loader, "pendingRequests")).isEmpty()
            && ((Collection<?>) field(loader, "pendingBatch")).isEmpty()
            && ((AtomicInteger) field(loader, "activeRequests")).get() == 0;
    }

    static void stopClients(List<ClientOwner> clients) throws Exception {
        // Retain the original watcher references before old stopInboundWatch nulls its field.
        for (ClientOwner owner : clients) {
            require(field(owner.client, "inboundWatchThread") == owner.watcher, "Central watcher owner changed");
            invoke(owner.client, "stopInboundWatch");
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        for (ClientOwner owner : clients) if (owner.watcher != null) {
            long remaining = deadline - System.nanoTime();
            require(remaining > 0, "Central watchers did not stop");
            TimeUnit.NANOSECONDS.timedJoin(owner.watcher, remaining);
            require(!owner.watcher.isAlive(), "Original Central watcher remains active");
        }
        // Native watcher finally closes its session. Loader/game/DB callers have already drained;
        // no beginAuthenticate or heartbeat producer can enqueue more auth work.
        for (ClientOwner owner : clients) owner.executor.shutdown();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        for (ClientOwner owner : clients) awaitPool(owner.executor, owner.pool, deadline, "Central auth");
    }

    static void stopActivityWriter(Session session) throws Exception {
        session.activityExecutor.shutdown();
        awaitPool(session.activityExecutor, session.activityPool,
            System.nanoTime() + TimeUnit.SECONDS.toNanos(45), "Activity log");
        requireNoWriteFailures();
        require(session.activityDataSource.getHikariPoolMXBean().getActiveConnections() == 0, "Activity datasource still in use");
        invoke(session.activity, "stop"); // Legacy forced timeout branch is unreachable after termination.
        require(session.activityDataSource.isClosed(), "Original activity datasource not closed");
    }

    static void awaitPool(ExecutorService executor, ThreadPoolExecutor pool, long deadline, String label) throws Exception {
        long remaining = deadline - System.nanoTime();
        require(remaining > 0 && executor.awaitTermination(remaining, TimeUnit.NANOSECONDS), label + " work retained after drain timeout");
        require(executor.isTerminated() && pool.getActiveCount() == 0 && pool.getQueue().isEmpty(), label + " work remains");
    }

    static void closeCentral(Object resources, Map<String, Object> owners, Thread notifier) throws Exception {
        Object world = owners.get("world"), notify = owners.get("notify"), social = owners.get("social");
        HikariDataSource ds = (HikariDataSource) owners.get("datasource");
        require(field(notify, "dataSource") == ds, "Unrelated datasource");
        require(field(notify, "thread") == notifier, "Central notifier owner changed");
        ScheduledExecutorService scheduler = (ScheduledExecutorService) owners.get("scheduler");
        ExecutorService socialExecutor = (ExecutorService) field(social, "executor");
        ThreadPoolExecutor socialPool = poolOf(socialExecutor);
        invoke(resources, "stopCentral"); // HTTP admission ends. Captured native owners survive Koin registry teardown.
        requirePortClosed(8080);
        Object registry = field(world, "registry");
        List<Integer> offlineWorlds = new ArrayList<>();
        for (Object id : (Iterable<?>) invoke(registry, "registeredWorldIds")) {
            int worldId = ((Number) id).intValue();
            require(worldId == 255, "Unrelated Central world appeared");
            Object connection = registry.getClass().getMethod("get", int.class).invoke(registry, worldId);
            if (connection != null && Boolean.FALSE.equals(invoke(connection, "getSubscribedForPush"))) offlineWorlds.add(worldId);
        }
        invoke(world, "stop");
        for (String name : List.of("bossGroup", "workerGroup")) awaitGroup((EventLoopGroup) field(world, name));
        requirePortClosed(9091);
        require(((Collection<?>) invoke(registry, "registeredWorldIds")).isEmpty(), "Native WorldLink detach incomplete");
        verifyNativeOfflineState(world, ds, offlineWorlds);
        invoke(notify, "stop");
        if (notifier != null) { notifier.join(10000); require(!notifier.isAlive(), "Central notifier still active"); }
        scheduler.shutdown();
        require(scheduler.awaitTermination(30, TimeUnit.SECONDS), "Central scheduler still active");
        // Do not call old destructive flush until its original accepted work has already completed.
        socialExecutor.shutdown();
        require(socialExecutor.awaitTermination(45, TimeUnit.SECONDS), "Social work retained after drain timeout");
        require(socialPool.getActiveCount() == 0 && socialPool.getQueue().isEmpty(), "Social work remains");
        requireNoWriteFailures();
        invoke(social, "flush");
        require(ds.getHikariPoolMXBean().getActiveConnections() == 0, "Central database still in use");
        ds.close();
        require(ds.isClosed(), "Central datasource not closed");
    }

    static void verifyNativeOfflineState(Object world, HikariDataSource ds, List<Integer> offlineWorlds) throws Exception {
        // Old Netty exceptionCaught swallows onChannelClosed errors. Observe the actual persistent
        // postconditions for native non-push disconnects; push reconnects intentionally skip these.
        Object presence = field(field(world, "handler"), "worldPresenceService");
        Object sessions = field(presence, "sessionRepository");
        for (int worldId : offlineWorlds) {
            Object rows = sessions.getClass().getMethod("listByWorldId", int.class).invoke(sessions, worldId);
            require(((Collection<?>) rows).isEmpty(), "Native world sessions did not finalize");
            try (var connection = ds.getConnection()) {
                connection.setReadOnly(true);
                try (var query = connection.prepareStatement("SELECT COUNT(*) FROM account_characters WHERE online_central_world_id = ?")) {
                    query.setQueryTimeout(10); query.setInt(1, worldId);
                    try (var result = query.executeQuery()) { require(result.next() && result.getLong(1) == 0, "Native character online markers did not finalize"); }
                }
            }
        }
    }

    static Map<String, Object> captureOwners() throws Exception {
        Class<?> global = Class.forName("org.koin.core.context.GlobalContext");
        Object koin = global.getMethod("getOrNull").invoke(global.getField("INSTANCE").get(null));
        require(koin != null, "Central Koin owner missing");
        require(Boolean.FALSE.equals(invoke(invoke(invoke(koin, "getScopeRegistry"), "getRootScope"), "getClosed")), "Central already closing");
        Map<?, ?> factories = (Map<?, ?>) invoke(invoke(koin, "getInstanceRegistry"), "getInstances");
        Map<String, Object> owners = new HashMap<>();
        for (Object factory : factories.values()) {
            if (!factory.getClass().getName().equals("org.koin.core.instance.SingleInstanceFactory")) continue;
            Object value = field(factory, "value");
            if (value == null) continue; // Never construct a replacement service.
            String key = switch (value.getClass().getName()) {
                case "dev.or2.central.worldlink.net.WorldLinkServer" -> "world";
                case "dev.or2.central.notify.PgNotifyService" -> "notify";
                case "dev.or2.central.social.SocialGraphPersistence" -> "social";
                case "com.zaxxer.hikari.HikariDataSource" -> "datasource";
                default -> value instanceof ScheduledExecutorService ? "scheduler" : null;
            };
            if (key != null) { Object previous = owners.putIfAbsent(key, value); require(previous == null || previous == value, "Ambiguous native Central owner"); }
        }
        require(owners.size() == 5, "Native Central owner not initialized");
        return owners;
    }

    static Object bootstrap() throws Exception {
        Class<?> hooks = Class.forName("java.lang.ApplicationShutdownHooks");
        Field registry = hooks.getDeclaredField("hooks"); registry.setAccessible(true);
        List<Thread> threads;
        synchronized (hooks) { threads = new ArrayList<>(((Map<Thread, Thread>) registry.get(null)).keySet()); }
        Object found = null;
        for (Thread thread : threads) {
            if (!thread.getName().equals("ShutdownHook")) continue;
            Object task = field(field(thread, "holder"), "task");
            if (task == null) continue;
            for (Field capture : task.getClass().getDeclaredFields()) {
                capture.setAccessible(true); Object value = capture.get(task);
                if (value != null && value.getClass().getName().equals("org.rsmod.server.app.GameBootstrap")) { require(found == null, "Ambiguous bootstrap"); found = value; }
            }
        }
        require(found != null, "Native bootstrap unavailable"); return found;
    }
    static void awaitGroup(EventLoopGroup group) throws Exception { require(group.terminationFuture().await(30, TimeUnit.SECONDS) && group.isTerminated(), "Native network workers still active"); }
    static void requireNoWriteFailures() throws Exception {
        require(!java.util.regex.Pattern.compile("Social persist[^\\r\\n]*failed|Central activity log (?:command|login|logout|drop|destroy) failed|Reached max retry attempts for database request")
            .matcher(Files.readString(LIVE_LOG)).find(), "Native database/social/activity write failure; handover refused and original owners retained");
    }
    static void requirePortClosed(int port) throws Exception {
        try (java.net.Socket socket = new java.net.Socket()) { try { socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 1000); } catch (java.net.ConnectException closed) { return; } }
        throw new IllegalStateException("Native listener remains open: " + port);
    }
    static Object field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); } catch (NoSuchFieldException absent) { }
        throw new NoSuchFieldException(name);
    }
    static Object invoke(Object owner, String name) throws Exception { Method method = owner.getClass().getMethod(name); method.setAccessible(true); return method.invoke(owner); }
    static Path receipt(String nonce) { UUID.fromString(nonce); return ROOT.resolve("logs/native-central-handover-" + nonce + ".ok"); }
    static void require(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
}
