import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean useParallel = true;
        int threadCount = Runtime.getRuntime().availableProcessors();

        if (args.length > 0) {
            try {
                if (args[0].equalsIgnoreCase("seq")) {
                    useParallel = false;
                } else {
                    threadCount = Integer.parseInt(args[0]);
                    if (threadCount <= 0) throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                System.err.println("Помилка: кількість потоків має бути цілим додатним числом.");
                System.err.println("Використання: java Main [кількість_потоків | seq]");
                return;
            }
        }
        Path directoryPath = Paths.get("generated_dataset");

        if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
            System.err.println("Помилка: Вказана директорія не існує!");
            return;
        }

        try {
            System.out.println("ЗАПУСК ПАЙПЛАЙНУ АНАЛІЗУ ЗОБРАЖЕНЬ\n");

            List<Path> allImages = getAllImages(directoryPath);
            System.out.printf("Знайдено файлів для аналізу: %d%n", allImages.size());

            ImageHashingStrategy hashingStrategy = new PHashAlgorithm();
            ColorFilteringStrategy<float[]> colorStrategy = new SmartHistogramFilter();

            AbstractImageFinder<float[]> finder;

            if (useParallel) {
                System.out.printf("\n[РЕЖИМ]: ПАРАЛЕЛЬНИЙ (Потоків CPU: %d)%n", threadCount);
                finder = new ParallelImageFinder<>(hashingStrategy, colorStrategy, threadCount);
            } else {
                System.out.println("\n[РЕЖИМ]: ПОСЛІДОВНИЙ (Один потік)");
                finder = new SequentialImageFinder<>(hashingStrategy, colorStrategy);
            }

            System.out.println("Глибокий аналіз зображень розпочато");

            long startTime = System.currentTimeMillis();

            List<ImageMatch> similarMatches = finder.findSimilarImages(allImages);

            long endTime = System.currentTimeMillis();

            printFinalReport(similarMatches, endTime - startTime);

            Path jsonPath = Paths.get("hard_ground_truth.json");

            if (Files.exists(jsonPath)) {
                String groundTruthJson = Files.readString(jsonPath);
                DatasetEvaluator.evaluate(groundTruthJson, similarMatches);
            } else {
                System.err.println("Файл еталону не знайдено за шляхом: " + jsonPath.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Критична помилка під час виконання: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<Path> getAllImages(Path dir) throws Exception {
        List<Path> result = new ArrayList<>();

        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(Files::isRegularFile).forEach(result::add);
        }
        return result;
    }

    private static void printFinalReport(List<ImageMatch> matches, long totalTime) {
        System.out.println("\nФІНАЛЬНИЙ ЗВІТ\n");

        int exactDupsCount = 0;
        int similarCount = 0;

        for (ImageMatch match : matches) {
            if (match.distance() == 0) {
                exactDupsCount++;
            } else {
                similarCount++;
            }
        }

//        System.out.printf("Точні копії (Відстань 0): %d%n", exactDupsCount);
//        for (ImageMatch match : matches) {
//            if (match.distance() == 0) {
//                System.out.printf("  - %s <---> %s%n", match.image1().getFileName(), match.image2().getFileName());
//            }
//        }
//
//        System.out.printf("%nСхожі зображення (Відстань > 0): %d%n", similarCount);
//        for (ImageMatch match : matches) {
//            if (match.distance() > 0) {
//                System.out.printf("  - Відстань: %d біт | %s <---> %s%n",
//                        match.distance(), match.image1().getFileName(), match.image2().getFileName());
//            }
//        }

        System.out.printf("%nЗагальний час виконання програми: %d мс%n", totalTime);
    }
}