import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class DeduplicationBenchmark {
    public static void main(String[] args) throws Exception {
        List<String> datasetNames = List.of("generated_dataset", "generated_dataset_8-10",
                "generated_dataset_16-20", "generated_dataset_50-60", "generated_dataset_100-120");
        List<Integer> threadCounts = List.of(1, 4, 8, 16, 24);
        int warmupRuns = 2;
        int measureRuns = 20;

        ImageHashingStrategy hashing = new PHashAlgorithm();
        ColorFilteringStrategy<float[]> color = new SmartHistogramFilter();

        System.out.println("БЕНЧМАРК АЛГОРИТМІВ ДЕДУПЛІКАЦІЇ");
        System.out.println("Параметри: " + measureRuns + " ітерацій, середня тривалість.\n");
        System.out.printf("%-12s | %-12s | %-8s | %-10s | %-8s | %-10s%n",
                "Обсяг (N)", "Режим", "Потоки", "Час (мс)", "Speedup", "Efficiency");

        for (String name : datasetNames) {
            Path directoryPath = Paths.get(name);

            List<Path> files = new ArrayList<>();
            try (Stream<Path> paths = Files.list(directoryPath)) {
                paths.filter(Files::isRegularFile).forEach(files::add);
            }

            for (int i = 0; i < warmupRuns; i++) {
                new ParallelImageFinder<>(hashing, color, 16).findSimilarImages(files);
            }

            AbstractImageFinder<float[]> seqFinder = new SequentialImageFinder<>(hashing, color);

            double avgSeqTime = runTest(seqFinder, files, measureRuns);
            printRow(name, "Sequential", 1, avgSeqTime, 1.0, 1.0);

            for (int threads : threadCounts) {
                AbstractImageFinder<float[]> parFinder = new ParallelImageFinder<>(hashing, color, threads);

                double avgParTime = runTest(parFinder, files, measureRuns);

                double speedup = avgSeqTime / avgParTime;
                double efficiency = speedup / threads;

                printRow(name, "Parallel", threads, avgParTime, speedup, efficiency);
            }
        }
    }

    private static double runTest(AbstractImageFinder<?> finder, List<Path> files, int measure) throws Exception {
        long totalNanoTime = 0;

        for (int i = 0; i < measure; i++) {
            System.gc();

            long start = System.nanoTime();
            finder.findSimilarImages(files);
            long duration = System.nanoTime() - start;

            totalNanoTime += duration;
        }

        long avgNano = totalNanoTime / measure;
        return (double) avgNano / 1_000_000.0;
    }

    private static void printRow(String name, String mode, int threads, double time, double speedup, double efficiency) {
        System.out.printf("%12s | %10s | %7d | %9f | %7.2f | %10.2f%n",
                name, mode, threads, time, speedup, efficiency);
    }
}