import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class task1 {
    static final int N = 8000;
    static final long DEFAULT_STUDENT_ID = 230107142L;   

    static long splitmix64(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    static int physicalCores(int logical) {
        try {
            Process p = new ProcessBuilder("lscpu", "-p=CORE,SOCKET").redirectErrorStream(true).start();
            Set<String> uniq = new HashSet<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null)
                    if (!line.startsWith("#") && !line.isBlank()) uniq.add(line.trim());
            }
            p.waitFor();
            if (uniq.size() > 0 && uniq.size() <= logical) return uniq.size();
        } catch (Exception ignored) { }
        return logical;
    }

    interface Kernel { double run(double[] a, int from, int to); }

    static double rowSum(double[] a, int fromRow, int toRow) {
        double total = 0;
        for (int i = fromRow; i < toRow; i++) {
            int base = i * N;
            double s = 0;
            for (int j = 0; j < N; j++) s += a[base + j];
            total += s;
        }
        return total;
    }

    static double colSum(double[] a, int fromCol, int toCol) {
        double total = 0;
        for (int j = fromCol; j < toCol; j++) {
            double s = 0;
            for (int i = 0; i < N; i++) s += a[i * N + j];
            total += s;
        }
        return total;
    }

    static double parallel(double[] a, int threads, Kernel k) throws InterruptedException {
        double[] partial = new double[threads];
        Thread[] ts = new Thread[threads];
        int chunk = (N + threads - 1) / threads;
        for (int t = 0; t < threads; t++) {
            final int id = t;
            final int from = Math.min(N, t * chunk);
            final int to = Math.min(N, from + chunk);
            ts[t] = new Thread(() -> partial[id] = k.run(a, from, to));
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        double sum = 0;
        for (double p : partial) sum += p;
        return sum;
    }

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_STUDENT_ID;
        int logical = Runtime.getRuntime().availableProcessors();
        int threads = physicalCores(logical);

        double[] a = new double[N * N];   
        final int T = threads;
        Thread[] fill = new Thread[T];
        int per = (int) (((long) N * N + T - 1) / T);
        for (int t = 0; t < T; t++) {
            final int from = Math.min(N * N, t * per);
            final int to = Math.min(N * N, from + per);
            fill[t] = new Thread(() -> {
                for (int k = from; k < to; k++)
                    a[k] = (splitmix64(seed ^ k) >>> 11) / 9007199254740992.0;
            });
            fill[t].start();
        }
        for (Thread t : fill) t.join();

        for (int w = 0; w < 5; w++) { rowSum(a, 0, 100); colSum(a, 0, 100); }

        long t0 = System.nanoTime();
        double sumA = parallel(a, threads, task1::rowSum);
        double msA = (System.nanoTime() - t0) / 1e6;

        t0 = System.nanoTime();
        double sumB = parallel(a, threads, task1::colSum);
        double msB = (System.nanoTime() - t0) / 1e6;

        System.out.println("Student ID: " + seed);
        System.out.println("Logical cores: " + logical + " | Physical cores used: " + threads);
        System.out.printf("Matrix: %d x %d float64 (%.0f MB)%n", N, N, (double) N * N * 8 / 1e6);
        System.out.printf("Checksum A: %.6f | Checksum B: %.6f%n", sumA, sumB);
        System.out.printf("Mode A (Row-Major)    : %.2f ms%n", msA);
        System.out.printf("Mode B (Column-Major) : %.2f ms%n", msB);
        System.out.printf("Cache penalty factor (B/A): %.2fx%n", msB / msA);
    }
}
