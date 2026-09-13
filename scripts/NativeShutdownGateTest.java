import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic operational regression; no attachment, process exit or database access. */
public final class NativeShutdownGateTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger stops = new AtomicInteger();
        CerberusAuditShutdown.awaitAndShutdown(CompletableFuture.completedFuture(null), stops::incrementAndGet);
        if (stops.get() != 1) throw new AssertionError("Successful drain did not stop once");
        CompletableFuture<Void> failed = CompletableFuture.failedFuture(new IllegalStateException("save failed"));
        try { CerberusAuditShutdown.awaitAndShutdown(failed, stops::incrementAndGet); throw new AssertionError("Failure accepted"); }
        catch (ExecutionException expected) { }
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        try { CerberusAuditShutdown.awaitAndShutdown(cancelled, stops::incrementAndGet); throw new AssertionError("Cancellation accepted"); }
        catch (java.util.concurrent.CancellationException expected) { }
        if (stops.get() != 1) throw new AssertionError("Uncommitted drain requested shutdown");
        try { CerberusAuditShutdown.awaitAndShutdown(CompletableFuture.completedFuture(null), () -> { throw new IllegalStateException("shutdown failed"); }); throw new AssertionError("Shutdown failure suppressed"); }
        catch (IllegalStateException expected) { }
        CerberusAuditShutdown.runReceiptWorker(() -> {
            if (Thread.currentThread().isDaemon()) throw new AssertionError("Receipt can be lost on main-thread exit");
            return null;
        });
        IllegalStateException receiptFailure = new IllegalStateException("receipt failure");
        try { CerberusAuditShutdown.runReceiptWorker(() -> { throw receiptFailure; }); throw new AssertionError("Worker failure suppressed"); }
        catch (ExecutionException expected) { if (expected.getCause() != receiptFailure) throw new AssertionError("Worker failure replaced", expected); }
        System.out.println("6 native shutdown gate regressions passed; no live JVM attached.");
    }
}
