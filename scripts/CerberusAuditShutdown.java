import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Save-first native shutdown. Legacy JVMs without the approved preflight are rejected, not exited. */
public final class CerberusAuditShutdown {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("pid canonical-root agent-jar");
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
                vm.loadAgent(Path.of(args[2]).toAbsolutePath().toString(), args[1] + "|" + nonce);
            } catch (java.io.IOException exitDuringReply) {
                // A fully stopped server may exit before attach sends its final reply. Only an
                // exact fresh receipt written AFTER successful native shutdown permits this case.
                if (!Files.exists(receipt)) throw exitDuringReply;
            }
            String expected = args[0] + "|" + Path.of(args[1]).toAbsolutePath().normalize();
            if (!Files.readString(receipt).equals(expected)) throw new IllegalStateException("Missing or invalid native shutdown receipt");
            System.out.println("Verified native save/shutdown receipt: " + receipt);
        } finally {
            try { vm.detach(); }
            catch (java.io.IOException exitDuringDetach) { if (!Files.exists(receipt)) throw exitDuringDetach; }
        }
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) throws Exception {
        String[] parts = arguments.split("\\|", -1);
        if (parts.length != 2) throw new IllegalArgumentException("canonical-root|nonce");
        String root = parts[0];
        verify(System.getProperty("user.dir"), root);
        Path receipt = receipt(root, parts[1]);
        if (Files.exists(receipt)) throw new IllegalStateException("Refusing reused shutdown receipt");
        instrumentation.redefineModule(Object.class.getModule(), Set.of(), Map.of(),
            Map.of("java.lang", Set.of(CerberusAuditShutdown.class.getModule())), Set.of(), Map.of());
        Object bootstrap = findBootstrap();
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
