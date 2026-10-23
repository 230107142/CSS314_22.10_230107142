// Task 5: Thread oversubscription threshold (Java)
// Build: javac task5.java
// Run:   java task5 [studentId]
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class task5 {
    static final int TASKS = 10_000;
    static final int HASH_ROUNDS = 500;
    static final int PAYLOAD_BYTES = 1024;
    static final int RUNS = 3;                      // timed runs per pool size (median reported)
    static final double TOLERANCE = 0.03;           // 3% noise margin for "worse" / "better"
    static final long DEFAULT_STUDENT_ID = 230107142L;   // <-- your Student ID

    static byte[] payload;
    static final AtomicLong sink = new AtomicLong();    // keeps the work from being optimized away

    // One small CPU-bound task: hash a 1KB payload 500 times (one byte changes per round)
    static void work() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = payload.clone();
            long acc = 0;
            for (int i = 0; i < HASH_ROUNDS; i++) {
                buf[0] = (byte) i;
                md.update(buf);
                acc += md.digest()[0];
            }
            sink.addAndGet(acc);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static double runBatch(int poolSize, int tasks) throws Exception {
        ThreadPoolExecutor ex = new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        ex.prestartAllCoreThreads();                 // thread creation is not part of the timing
        CountDownLatch done = new CountDownLatch(tasks);
        long t0 = System.nanoTime();
        for (int i = 0; i < tasks; i++) ex.execute(() -> { work(); done.countDown(); });
        done.await();
        double ms = (System.nanoTime() - t0) / 1e6;
        ex.shutdown();
        ex.awaitTermination(1, TimeUnit.MINUTES);
        return ms;
    }

    static double median(double[] v) {
        double[] s = v.clone();
        Arrays.sort(s);
        return s[s.length / 2];
    }

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_STUDENT_ID;
        int cores = Runtime.getRuntime().availableProcessors();

        payload = new byte[PAYLOAD_BYTES];
        new Random(seed).nextBytes(payload);

        // Pool sizes: 1, Cores/2, Cores, Cores*2, Cores*4, Cores*16, 256 (deduplicated, ascending)
        TreeSet<Integer> sizes = new TreeSet<>(List.of(1, Math.max(1, cores / 2), cores, cores * 2, cores * 4, cores * 16, 256));

        System.out.println("Student ID: " + seed + " | Logical cores: " + cores + " | Tasks: " + TASKS
                + " | Work per task: SHA-256 x" + HASH_ROUNDS + " on " + PAYLOAD_BYTES + " bytes");

        runBatch(cores, 1000);                       // short warm-up (JIT)

        System.out.println();
        System.out.printf("%-10s | %-20s | %-26s | %s%n", "Pool Size", "Execution Time (ms)", "Speedup vs 1 thread", "Status");
        System.out.println("-".repeat(86));

        double baseline = 0, best = Double.MAX_VALUE;
        int bestPool = -1, degradeAt = -1;
        for (int p : sizes) {
            double[] t = new double[RUNS];
            for (int r = 0; r < RUNS; r++) t[r] = runBatch(p, TASKS);
            double ms = median(t);
            if (p == 1) baseline = ms;

            String status;
            if (p == 1) { status = "BASELINE"; }
            else if (ms < best * (1 - TOLERANCE)) { status = "SCALING (faster)"; }
            else if (ms <= best * (1 + TOLERANCE)) { status = "PLATEAU (saturated)"; }
            else if (degradeAt == -1) { status = "DEGRADING  <-- degradation starts here"; degradeAt = p; }
            else { status = "DEGRADING"; }

            if (ms < best) { best = ms; bestPool = p; }
            System.out.printf("%-10d | %-20.2f | %-26s | %s%n", p, ms, String.format("%.2fx", baseline / ms), status);
        }

        System.out.println();
        System.out.println("Fastest pool size: " + bestPool + " (" + String.format("%.2f", best) + " ms)");
        if (degradeAt == -1)
            System.out.println("No degradation beyond the " + (int) (TOLERANCE * 100) + "% noise margin was observed up to pool size "
                    + sizes.last() + ".");
        else
            System.out.println("Execution time starts worsening at pool size " + degradeAt
                    + " (context-switching / oversubscription overhead).");
        if (sink.get() == 42) System.out.println();   // use sink so it cannot be eliminated
    }
}
