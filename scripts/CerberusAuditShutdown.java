package deploydiag3;

import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Save-first native shutdown. Legacy JVMs without the approved preflight are rejected, not exited. */
public final class CerberusAuditShutdown {
    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 4) throw new IllegalArgumentException("pid canonical-root agent-jar [retry]");
        String action = args.length == 4 ? args[3] : "shutdown";
        if (!action.equals("shutdown") && !action.equals("retry")) throw new IllegalArgumentException("Unknown action: " + action);
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        String nonce = UUID.randomUUID().toString();
        Path receipt = receipt(args[1], nonce);
        try {
            verify(vm.getSystemProperties().getProperty("user.dir"), args[1]);
            String command = vm.getSystemProperties().getProperty("sun.java.command", "");
            if (!command.startsWith("org.rsmod.server.app.GameServerKt")) {
                throw new IllegalStateException("Target is not the OpenRune game server");
            }
            try {
                vm.loadAgent(Path.of(args[2]).toAbsolutePath().toString(), args[1] + "|" + nonce + "|" + action);
            } catch (java.io.IOException exitDuringReply) {
                // A fully stopped server may exit before attach sends its final reply. Only an
                // exact fresh receipt written AFTER successful native shutdown permits this case.
                if (!action.equals("shutdown") || !Files.exists(receipt)) throw exitDuringReply;
            }
            if (action.equals("shutdown")) {
                String expected = args[0] + "|" + Path.of(args[1]).toAbsolutePath().normalize();
                if (!Files.readString(receipt).equals(expected)) throw new IllegalStateException("Missing or invalid native shutdown receipt");
                System.out.println("Verified native save/shutdown receipt: " + receipt);
            }
        } finally {
            try { vm.detach(); }
            catch (java.io.IOException exitDuringDetach) { if (!action.equals("shutdown") || !Files.exists(receipt)) throw exitDuringDetach; }
        }
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) throws Exception {
        String[] parts = arguments.split("\\|", -1);
        if (parts.length != 3) throw new IllegalArgumentException("canonical-root|nonce|action");
        String root = parts[0];
        String action = parts[2];
        if (!action.equals("shutdown") && !action.equals("retry")) throw new IllegalArgumentException("Unknown action: " + action);
        Object bootstrapForDiagnostics = null;
        try {
            verify(System.getProperty("user.dir"), root);
            Path receipt = receipt(root, parts[1]);
            if (action.equals("shutdown") && Files.exists(receipt)) throw new IllegalStateException("Refusing reused shutdown receipt");
            instrumentation.redefineModule(Object.class.getModule(), Set.of(), Map.of(),
                Map.of("java.lang", Set.of(CerberusAuditShutdown.class.getModule())), Set.of(), Map.of());
            Object bootstrap = findBootstrap();
            bootstrapForDiagnostics = bootstrap;
            if (action.equals("retry")) {
                String result = retryFailedLogoutsOnGameThread(bootstrap);
                System.out.println("OPENRUNE GAME-THREAD LOGOUT RETRY COMPLETE: " + result);
            } else {
                // Resolve BOTH methods before changing any runtime state. Old unsafe builds fail here.
                Method prepare = bootstrap.getClass().getMethod("prepareShutdown");
                Method shutdown = bootstrap.getClass().getMethod("shutdown");
                runReceiptWorker(() -> {
                    CompletableFuture<?> drained = (CompletableFuture<?>) prepare.invoke(bootstrap);
                    awaitAndShutdown(drained, () -> {
                        try { shutdown.invoke(bootstrap); }
                        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native shutdown failed", failure); }
                    });
                    Files.writeString(receipt, ProcessHandle.current().pid() + "|" + Path.of(root).toAbsolutePath().normalize(), StandardOpenOption.CREATE_NEW);
                    System.out.println("OPENRUNE CONTROLLED SHUTDOWN COMPLETE: native saves and resource closure verified");
                    return null;
                });
            }
        } catch (Throwable failure) {
            Path diagnostic = Path.of(root).toAbsolutePath().normalize().resolve("logs/native-shutdown-" + parts[1] + ".error");
            try {
                java.io.StringWriter trace = new java.io.StringWriter();
                failure.printStackTrace(new java.io.PrintWriter(trace));
                if (bootstrapForDiagnostics != null) {
                    trace.write("\n--- Retained account-save failures ---\n");
                    trace.write(saveFailureDetails(bootstrapForDiagnostics));
                }
                Files.writeString(diagnostic, trace.toString(), StandardOpenOption.CREATE_NEW);
            } catch (Throwable loggingFailure) {
                failure.addSuppressed(loggingFailure);
            }
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new AssertionError(failure);
        }
    }

    private static String retryFailedLogoutsOnGameThread(Object bootstrap) throws Exception {
        Object serviceManager = read(bootstrap.getClass(), bootstrap, "serviceManager");
        @SuppressWarnings("unchecked")
        List<ExecutorService> executors = (List<ExecutorService>) read(serviceManager.getClass(), serviceManager, "activeExecutors");
        for (ExecutorService executor : executors) {
            Future<String> result = executor.submit(() -> {
                if (!Thread.currentThread().getName().equals("game")) return null;
                Object gameService = read(bootstrap.getClass(), bootstrap, "gameService");
                Object accounts = read(gameService.getClass(), gameService, "accounts");
                Method status = accounts.getClass().getMethod("logoutStatus");
                Method retry = accounts.getClass().getMethod("retryFailedLogouts");
                Object before = status.invoke(accounts);
                retry.invoke(accounts);
                Object after = status.invoke(accounts);
                return "thread=" + Thread.currentThread().getName() + "; before=" + before + "; after=" + after;
            });
            String value = result.get(15, TimeUnit.SECONDS);
            if (value != null) return value;
        }
        throw new IllegalStateException("Could not dispatch logout recovery onto the native game thread");
    }

    private static String saveFailureDetails(Object bootstrap) throws Exception {
        Object gameService = read(bootstrap.getClass(), bootstrap, "gameService");
        if (gameService == null) return "Native game service unavailable\n";
        Object saver = read(gameService.getClass(), gameService, "saver");
        Object monitor = read(saver.getClass(), saver, "requestMonitor");
        ArrayList<Object> failures;
        synchronized (monitor) {
            Map<?, ?> failed = (Map<?, ?>) read(saver.getClass(), saver, "failed");
            failures = new ArrayList<>(failed.values());
        }
        java.io.StringWriter trace = new java.io.StringWriter();
        java.io.PrintWriter writer = new java.io.PrintWriter(trace);
        writer.println("retained failure count=" + failures.size());
        for (int i = 0; i < failures.size(); i++) {
            writer.println("save failure " + (i + 1) + ":");
            ((Throwable) failures.get(i)).printStackTrace(writer);
        }
        writer.flush();
        return trace.toString();
    }

    static void runReceiptWorker(Callable<Void> action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action);
        Thread worker = new Thread(task, "openrune-native-shutdown-receipt");
        worker.setDaemon(false);
        worker.start();
        task.get();
    }

    static void awaitAndShutdown(CompletableFuture<?> drained, Runnable shutdown) throws Exception {
        drained.get(90, TimeUnit.SECONDS);
        shutdown.run();
    }

    private static Object findBootstrap() throws Exception {
        Class<?> hooksClass = Class.forName("java.lang.ApplicationShutdownHooks");
        ArrayList<Thread> hooks;
        synchronized (hooksClass) {
            @SuppressWarnings("unchecked")
            Map<Thread, Thread> registered = (Map<Thread, Thread>) read(hooksClass, null, "hooks");
            hooks = new ArrayList<>(registered.keySet());
        }
        Object found = null;
        for (Thread hook : hooks) {
            if (!hook.getName().equals("ShutdownHook")) continue;
            Object holder = read(Thread.class, hook, "holder");
            Object task = read(holder.getClass(), holder, "task");
            if (task == null) continue;
            for (Field field : task.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object candidate = field.get(task);
                if (candidate == null || !candidate.getClass().getName().equals("org.rsmod.server.app.GameBootstrap")) continue;
                if (found != null) throw new IllegalStateException("Ambiguous bootstrap ownership");
                found = candidate;
            }
        }
        if (found == null) throw new IllegalStateException("Native bootstrap unavailable; no shutdown requested");
        return found;
    }

    private static Object read(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Path receipt(String root, String nonce) {
        UUID.fromString(nonce);
        return Path.of(root).toAbsolutePath().normalize().resolve("logs/native-shutdown-" + nonce + ".ok");
    }

    private static void verify(String actual, String expected) {
        if (!Path.of(actual).toAbsolutePath().normalize().equals(Path.of(expected).toAbsolutePath().normalize())) {
            throw new IllegalStateException("Refusing to stop a JVM from a different working tree");
        }
    }
}
