import java.io.*;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Random;

public class task4 {
    static final int[] SIZES = {100_000, 1_000_000, 10_000_000};
    static final long DEFAULT_STUDENT_ID = 230107142L;   
    static final int RUNS = 5;                          

    static void childMain(String path, int maxN) throws Exception {
        MappedByteBuffer shared;
        try (FileChannel ch = FileChannel.open(Paths.get(path), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            shared = ch.map(FileChannel.MapMode.READ_WRITE, 0, (long) maxN * 8);
        }
        DataInputStream in = new DataInputStream(new BufferedInputStream(System.in, 1 << 16));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(System.out, 1 << 12));
        while (true) {
            int cmd = in.read();
            if (cmd == -1 || cmd == 'Q') break;
            int n = in.readInt();
            double cs;
            if (cmd == 'A') {                       
                byte[] buf = new byte[n * 8];
                in.readFully(buf);
                double[] arr = new double[n];
                ByteBuffer.wrap(buf).asDoubleBuffer().get(arr);
                cs = arr[0] + arr[n / 2] + arr[n - 1];
            } else {                                 
                cs = shared.getDouble(0) + shared.getDouble((n / 2) * 8) + shared.getDouble((n - 1) * 8);
            }
            out.writeDouble(cs);
            out.flush();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("child")) { childMain(args[1], Integer.parseInt(args[2])); return; }

        long seed = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_STUDENT_ID;
        int maxN = SIZES[SIZES.length - 1];

        Path file = Files.createTempFile("task4_shared", ".bin");
        file.toFile().deleteOnExit();
        MappedByteBuffer shared;
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            shared = ch.map(FileChannel.MapMode.READ_WRITE, 0, (long) maxN * 8);
        }
        DoubleBuffer sharedD = shared.asDoubleBuffer();

        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        Process child = new ProcessBuilder(javaBin, "-cp", System.getProperty("java.class.path"),
                "task4", "child", file.toString(), String.valueOf(maxN))
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        DataOutputStream toChild = new DataOutputStream(new BufferedOutputStream(child.getOutputStream(), 1 << 16));
        DataInputStream fromChild = new DataInputStream(new BufferedInputStream(child.getInputStream()));

        Random rng = new Random(seed);
        double[] data = new double[maxN];
        for (int i = 0; i < maxN; i++) data[i] = rng.nextDouble();

        System.out.println("Student ID: " + seed + " | Runs per size: " + RUNS);
        System.out.println("Path A = pipe: serialize -> write -> child deserializes -> ack");
        System.out.println("Path B = shared memory (mmap): copy into shared buffer + signal -> child reads in place -> ack");
        System.out.println("Path B handoff = signal + ack only (data already produced in shared memory)");
        System.out.println();
        System.out.printf("%-12s %-14s %-18s %-20s %-10s %-8s%n",
                "N", "Path A (ms)", "Path B copy (ms)", "Path B handoff (ms)", "A / B", "Verify");

        boolean allOk = true;
        for (int n : SIZES) {
            double expected = data[0] + data[n / 2] + data[n - 1];
            double msA = 0, msB = 0, msH = 0;
            boolean ok = true;
            for (int r = -1; r < RUNS; r++) {        
                long t0 = System.nanoTime();
                ByteBuffer bb = ByteBuffer.allocate(n * 8);
                bb.asDoubleBuffer().put(data, 0, n);
                toChild.writeByte('A'); toChild.writeInt(n); toChild.write(bb.array()); toChild.flush();
                double ra = fromChild.readDouble();
                double a = (System.nanoTime() - t0) / 1e6;

                t0 = System.nanoTime();
                sharedD.clear();
                sharedD.put(data, 0, n);
                toChild.writeByte('B'); toChild.writeInt(n); toChild.flush();
                double rb = fromChild.readDouble();
                double b = (System.nanoTime() - t0) / 1e6;

                t0 = System.nanoTime();
                toChild.writeByte('B'); toChild.writeInt(n); toChild.flush();
                double rh = fromChild.readDouble();
                double h = (System.nanoTime() - t0) / 1e6;

                ok &= (ra == expected) && (rb == expected) && (rh == expected);
                if (r >= 0) { msA += a; msB += b; msH += h; }
            }
            msA /= RUNS; msB /= RUNS; msH /= RUNS;
            allOk &= ok;
            System.out.printf("%-12d %-14.3f %-18.3f %-20.3f %-10.2f %-8s%n",
                    n, msA, msB, msH, msA / msB, ok ? "PASS" : "FAIL");
        }

        toChild.writeByte('Q'); toChild.flush();
        child.waitFor();
        if (!allOk) System.exit(1);
    }
}
