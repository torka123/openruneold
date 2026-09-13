import java.util.*;
import java.util.concurrent.*;

/** Standalone tests of the exact operational gate; never attaches or changes runtime state. */
public final class CentralLegacyNativeHandoverTest {
    public static void main(String[] args) throws Exception {
        List<String> order = new ArrayList<>();
        List<String> expectedOrder = List.of("ingress", "accepted callbacks", "clients", "final callbacks",
            "activity", "central", "bootstrap", "receipt");
        CentralLegacyNativeHandover.Step[] ordered = expectedOrder.stream()
            .map(name -> (CentralLegacyNativeHandover.Step) () -> order.add(name))
            .toArray(CentralLegacyNativeHandover.Step[]::new);
        CentralLegacyNativeHandover.handover(CompletableFuture.completedFuture(null), ordered);
        check(order.equals(expectedOrder), "Native closure ordering");
        int tests = 1;
        for (int failure = -1; failure < expectedOrder.size(); failure++) {
            final int rejected = failure;
            List<Integer> calls = new ArrayList<>();
            CentralLegacyNativeHandover.Step[] steps = new CentralLegacyNativeHandover.Step[expectedOrder.size()];
            for (int stage = 0; stage < steps.length; stage++) {
                final int current = stage;
                steps[stage] = () -> { calls.add(current); if (current == rejected) throw new IllegalStateException("Native stage failed"); };
            }
            CompletableFuture<?> saved = failure == -1 ? CompletableFuture.failedFuture(new IllegalStateException("Save failed")) : CompletableFuture.completedFuture(null);
            try {
                CentralLegacyNativeHandover.handover(saved, steps);
                throw new AssertionError("Failure must escape");
            } catch (ExecutionException | IllegalStateException expected) {
                List<Integer> expectedCalls = new ArrayList<>();
                for (int stage = 0; stage <= failure; stage++) expectedCalls.add(stage);
                check(calls.equals(expectedCalls), "Failure at " + failure + " must block later cleanup and receipt");
            }
            tests++;
        }
        CompletableFuture<Void> saved = new CompletableFuture<>();
        CountDownLatch launched = new CountDownLatch(1);
        List<String> asyncOrder = Collections.synchronizedList(new ArrayList<>());
        FutureTask<Void> worker = new FutureTask<>(() -> {
            launched.countDown();
            CentralLegacyNativeHandover.handover(saved, () -> asyncOrder.add("ingress"), () -> {}, () -> {}, () -> {}, () -> asyncOrder.add("receipt"));
            return null;
        });
        Thread thread = new Thread(worker);
        thread.start();
        check(launched.await(5, TimeUnit.SECONDS), "Gate started");
        check(asyncOrder.isEmpty(), "Incomplete save cannot close ingress");
        saved.complete(null);
        worker.get(5, TimeUnit.SECONDS);
        check(asyncOrder.equals(List.of("ingress", "receipt")), "Completion unblocks native sequence");
        tests++;

        Object originalOwner = new Object();
        CentralLegacyNativeHandover.SessionSlot<Object> slot = new CentralLegacyNativeHandover.SessionSlot<>();
        var attempt = slot.claim(originalOwner);
        IllegalStateException originalFailure = new IllegalStateException("Native persistence stage failed");
        CentralLegacyNativeHandover.runRetained(attempt, () -> { throw originalFailure; });
        check(slot.get() == attempt && attempt.owner == originalOwner, "Failure retains exact original owners and attempt");
        try { attempt.completion.get(); throw new AssertionError("Terminal failure expected"); }
        catch (ExecutionException failure) { check(failure.getCause() == originalFailure, "Original terminal failure retained"); }
        try { slot.claim(new Object()); throw new AssertionError("Failed session must refuse retry"); }
        catch (IllegalStateException expected) { check(slot.get() == attempt, "Rejected retry cannot replace owners or failure"); }
        tests++;

        var completedSlot = new CentralLegacyNativeHandover.SessionSlot<Object>();
        var complete = completedSlot.claim(originalOwner);
        CentralLegacyNativeHandover.runRetained(complete, () -> {});
        check(complete.completion.isDone() && !complete.completion.isCompletedExceptionally(), "Successful attempt is terminal");
        try { completedSlot.claim(new Object()); throw new AssertionError("Successful session must refuse duplicate"); }
        catch (IllegalStateException expected) { check(completedSlot.get() == complete, "Success also retains original session"); }
        tests++;

        ExecutorService ownerExecutor = Executors.newSingleThreadExecutor();
        try {
            Thread ownerThread = ownerExecutor.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
            List<String> ownerCalls = new ArrayList<>();
            CompletableFuture<Void> preflight = new CompletableFuture<>();
            CompletableFuture<?> actual = CentralLegacyNativeHandover.prepareOnOwner(ownerExecutor,
                () -> { check(Thread.currentThread() == ownerThread, "Authority must run on native owner"); ownerCalls.add("authority"); },
                () -> { check(Thread.currentThread() == ownerThread, "Prepare must run on same native owner"); ownerCalls.add("prepare"); return preflight; });
            check(actual == preflight && ownerCalls.equals(List.of("authority", "prepare")), "Authority and prepare are one ordered owner action");
            tests++;
            try {
                CentralLegacyNativeHandover.prepareOnOwner(ownerExecutor,
                    () -> { throw new IllegalStateException("Unrelated player"); },
                    () -> { throw new AssertionError("Unauthorized native prepare must not run"); });
                throw new AssertionError("Authority rejection expected");
            } catch (ExecutionException expected) {
                check(expected.getCause() instanceof IllegalStateException, "Authority failure escapes before prepare");
            }
            tests++;
        } finally {
            ownerExecutor.shutdown();
            check(ownerExecutor.awaitTermination(5, TimeUnit.SECONDS), "Test owner completes normally");
        }

        FakeScript script = new FakeScript();
        Object captured = CentralLegacyNativeHandover.directCapturedOwner(
            List.of(new DirectCapture(script), new DirectCapture(script)), FakeScript.class.getName());
        check(captured == script, "Repeated callback captures deduplicate by identity");
        tests++;
        try {
            CentralLegacyNativeHandover.directCapturedOwner(
                List.of(new DirectCapture(script), new DirectCapture(new FakeScript())), FakeScript.class.getName());
            throw new AssertionError("Ambiguous original script must refuse");
        } catch (IllegalStateException expected) { tests++; }
        try {
            CentralLegacyNativeHandover.directCapturedOwner(List.of(new NestedCapture(new DirectCapture(script))), FakeScript.class.getName());
            throw new AssertionError("Missing direct capture must refuse, without graph traversal");
        } catch (IllegalStateException expected) { tests++; }

        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> accepted = Collections.synchronizedList(new ArrayList<>());
        pool.execute(() -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException failure) { throw new AssertionError("Accepted work was interrupted", failure); }
            accepted.add("running write");
        });
        check(entered.await(5, TimeUnit.SECONDS), "Accepted worker entered");
        pool.execute(() -> accepted.add("queued write"));
        pool.shutdown();
        try {
            CentralLegacyNativeHandover.awaitPool(pool, pool, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(20), "Test persistence");
            throw new AssertionError("Unfinished accepted work must refuse closure");
        } catch (IllegalStateException expected) {
            check(pool.getQueue().size() == 1 && pool.getActiveCount() == 1, "Timeout retains accepted queue and running worker");
        } finally { release.countDown(); }
        CentralLegacyNativeHandover.awaitPool(pool, pool, System.nanoTime() + TimeUnit.SECONDS.toNanos(5), "Test persistence");
        check(accepted.equals(List.of("running write", "queued write")), "Graceful pool shutdown executes every accepted write");
        tests++;
        System.out.println("Native Central handover gate: " + tests + " tests passed; no runtime attachment");
    }
    static final class FakeScript {}
    static final class DirectCapture { final FakeScript script; DirectCapture(FakeScript script) { this.script = script; } }
    static final class NestedCapture { final DirectCapture callback; NestedCapture(DirectCapture callback) { this.callback = callback; } }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
