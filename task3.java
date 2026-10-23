import java.util.Random;

public class task3 {
    static final int N = 2_000_000;
    static final long DEFAULT_STUDENT_ID = 230107142L;   
    static final int RUNS = 10;                          

    static void serialScan(int[] in, long[] out) {
        long acc = 0;
        for (int i = 0; i < in.length; i++) {
            acc += in[i];
            out[i] = acc;                 
        }
    }

   static void parallelScan(int[] in, long[] out, int threads) throws InterruptedException {
        final int n = in.length;
        final int chunk = (n + threads - 1) / threads;
        final long[] sums = new long[threads];
        final long[] offsets = new long[threads];

        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            final int from = Math.min(n, t * chunk), to = Math.min(n, from + chunk);
            ts[t] = new Thread(() -> {
                long s = 0;
                for (int i = from; i < to; i++) s += in[i];
                sums[id] = s;
            });
            ts[t].start();
        }
        for (Thread t : ts) t.join();

        long run = 0;                      
        for (int t = 0; t < threads; t++) { offsets[t] = run; run += sums[t]; }

        for (int t = 0; t < threads; t++) {
            final int id = t;
            final int from = Math.min(n, t * chunk), to = Math.min(n, from + chunk);
            ts[t] = new Thread(() -> {
                long acc = offsets[id];
                for (int i = from; i < to; i++) {
                    acc += in[i];
                    out[i] = acc;
                }
            });
            ts[t].start();
        }
        for (Thread t : ts) t.join();
    }

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_STUDENT_ID;
        int threads = Runtime.getRuntime().availableProcessors();

        Random rng = new Random(seed);
        int[] input = new int[N];
        for (int i = 0; i < N; i++) input[i] = rng.nextInt(10) + 1;   

        long[] serialOut = new long[N];
        long[] parallelOut = new long[N];

        for (int w = 0; w < 5; w++) { serialScan(input, serialOut); parallelScan(input, parallelOut, threads); }

        long t0 = System.nanoTime();
        for (int r = 0; r < RUNS; r++) serialScan(input, serialOut);
        double serialMs = (System.nanoTime() - t0) / 1e6 / RUNS;

        t0 = System.nanoTime();
        for (int r = 0; r < RUNS; r++) parallelScan(input, parallelOut, threads);
        double parallelMs = (System.nanoTime() - t0) / 1e6 / RUNS;

        boolean pass = parallelOut[N - 1] == serialOut[N - 1] && java.util.Arrays.equals(serialOut, parallelOut);

        System.out.println("Student ID: " + seed + " | Elements: " + N + " | Threads: " + threads);
        System.out.printf("Last element -> serial: %d | parallel: %d%n", serialOut[N - 1], parallelOut[N - 1]);
        System.out.printf("Serial Time: %.3f ms | Parallel Time: %.3f ms | Speedup: %.2fx | Verification: %s%n",
                serialMs, parallelMs, serialMs / parallelMs, pass ? "PASS" : "FAIL");

        if (!pass) System.exit(1);
    }
}
