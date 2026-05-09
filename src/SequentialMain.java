import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class SequentialMain {
    public static void main(String[] args) {
        Path directoryPath = Paths.get("generated_dataset");

        if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
            System.err.println("Помилка: Вказана директорія не існує!");
            return;
        }

        try {
            System.out.println("ЗАПУСК ПАЙПЛАЙНУ АНАЛІЗУ ЗОБРАЖЕНЬ\n");
            List<Path> allImages = getAllImages(directoryPath);
            System.out.printf("Знайдено файлів для аналізу: %d%n", allImages.size());

            System.out.println("Глибокий аналіз зображень");
            long hashStartTime = System.currentTimeMillis();

            // ColorFilteringStrategy<SpatialGridColorFilter.GridData> colorStrategy = new SpatialGridColorFilter();

            ImageHashingStrategy hashingStrategy = new PHashAlgorithm();
            ColorFilteringStrategy<float[]> colorStrategy = new SmartHistogramFilter();

            SimilarImageFinder<?> finder = new SimilarImageFinder<>(hashingStrategy, colorStrategy);
            List<ImageMatch> similarMatches = finder.findSimilarImages(allImages);

            long hashEndTime = System.currentTimeMillis();
            printFinalReport(similarMatches, hashEndTime - hashStartTime);

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
        System.out.println("ФІНАЛЬНИЙ ЗВІТ\n");

        int exactDupsCount = 0;
        int similarCount = 0;

        for (ImageMatch match : matches) {
            if (match.distance() == 0) {
                exactDupsCount++;
            } else {
                similarCount++;
            }
        }

        System.out.printf("Точні копії (Відстань 0): %d%n", exactDupsCount);
        for (ImageMatch match : matches) {
            if (match.distance() == 0) {
                System.out.printf("  - %s <---> %s%n", match.image1().getFileName(), match.image2().getFileName());
            }
        }

        System.out.println("Схожі зображення (Відстань > 0): " + similarCount);
        for (ImageMatch match : matches) {
            if (match.distance() > 0) {
                System.out.printf("  - Відстань: %d біт | %s <---> %s%n",
                        match.distance(), match.image1().getFileName(), match.image2().getFileName());
            }
        }

        System.out.println("Загальний час виконання програми: " + totalTime + " мс");
    }
}