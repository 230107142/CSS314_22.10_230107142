import java.util.Random;

public class task2 {
    static final long SAMPLES = 40_000_000L;
    static final int THREADS = 4;
    static final long DEFAULT_STUDENT_ID = 230107142L;

    static long runShared(long studentId) throws InterruptedException {
        final Random shared = new Random(studentId * 10);
        final Object lock = new Object();
        final long[] hits = new long[THREADS];
        Thread[] ts = new Thread[THREADS];
        final long per = SAMPLES / THREADS;
        for (int t = 0; t < THREADS; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                long h = 0;
                for (long i = 0; i < per; i++) {
                    double x, y;
                    synchronized (lock) {         
                        x = shared.nextDouble();
                        y = shared.nextDouble();
                    }
                    if (x * x + y * y <= 1.0) h++;
                }
                hits[id] = h;
            });
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        long total = 0;
        for (long h : hits) total += h;
        return total;
    }

    static long runIndependent(long studentId) throws InterruptedException {
        final long[] hits = new long[THREADS];
        Thread[] ts = new Thread[THREADS];
        final long per = SAMPLES / THREADS;
        for (int t = 0; t < THREADS; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                Random rng = new Random(studentId * 10 + id);   
                long h = 0;
                for (long i = 0; i < per; i++) {
                    double x = rng.nextDouble();
                    double y = rng.nextDouble();
                    if (x * x + y * y <= 1.0) h++;
                }
                hits[id] = h;
            });
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        long total = 0;
        for (long h : hits) total += h;
        return total;
    }

    public static void main(String[] args) throws Exception {
        long studentId = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_STUDENT_ID;

        Random w = new Random(1);
        long dummy = 0;
        for (int i = 0; i < 2_000_000; i++) { double x = w.nextDouble(), y = w.nextDouble(); if (x*x+y*y <= 1.0) dummy++; }
        if (dummy < 0) System.out.println(dummy);

        long t0 = System.nanoTime();
        long hitsShared = runShared(studentId);
        double msShared = (System.nanoTime() - t0) / 1e6;
        double piShared = 4.0 * hitsShared / SAMPLES;

        t0 = System.nanoTime();
        long hitsInd = runIndependent(studentId);
        double msInd = (System.nanoTime() - t0) / 1e6;
        double piInd = 4.0 * hitsInd / SAMPLES;

        System.out.println("Student ID: " + studentId);
        System.out.println("Samples: " + SAMPLES + " | Threads: " + THREADS);
        System.out.printf("Shared PRNG (locked)  : pi = %.8f | abs error = %.8f | time = %.2f ms%n",
                piShared, Math.abs(piShared - Math.PI), msShared);
        System.out.printf("Independent PRNGs     : pi = %.8f | abs error = %.8f | time = %.2f ms%n",
                piInd, Math.abs(piInd - Math.PI), msInd);
        System.out.printf("Speedup (shared / independent): %.2fx%n", msShared / msInd);
    }
}
