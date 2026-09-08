import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;

/** Operational helper: invokes the JVM's normal shutdown hooks, as the admin reboot command does. */
public final class CerberusAuditShutdown {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("pid canonical-root agent-jar");
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try {
            verify(vm.getSystemProperties().getProperty("user.dir"), args[1]);
            String command = vm.getSystemProperties().getProperty("sun.java.command", "");
            if (!command.startsWith("org.rsmod.server.app.GameServerKt")) {
                throw new IllegalStateException("Target is not the OpenRune game server");
            }
            vm.loadAgent(Path.of(args[2]).toAbsolutePath().toString(), args[1]);
        } finally {
            vm.detach();
        }
    }

    public static void agentmain(String root, Instrumentation unused) {
        verify(System.getProperty("user.dir"), root);
        Thread shutdown = new Thread(() -> System.exit(0), "cerberus-audit-controlled-shutdown");
        shutdown.setDaemon(true);
        shutdown.start();
    }

    private static void verify(String actual, String expected) {
        if (!Path.of(actual).toAbsolutePath().normalize().equals(Path.of(expected).toAbsolutePath().normalize())) {
            throw new IllegalStateException("Refusing to stop a JVM from a different working tree");
        }
    }
}
