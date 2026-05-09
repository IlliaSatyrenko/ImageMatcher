import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatasetEvaluator {
    public static void evaluate(String jsonGroundTruth,
                                List<ImageMatch> similarMatches) {

        System.out.println("\nОЦІНКА РЕЗУЛЬТАТІВ");

        List<List<String>> groundTruthGroups = parseGroundTruth(jsonGroundTruth);

        Set<String> groundTruthPairs = new HashSet<>();
        for (List<String> group : groundTruthGroups) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    groundTruthPairs.add(makePairKey(group.get(i), group.get(j)));
                }
            }
        }

        Set<String> predictedPairs = new HashSet<>();

        for (ImageMatch match : similarMatches) {
            predictedPairs.add(makePairKey(match.image1().getFileName().toString(),
                    match.image2().getFileName().toString()));
        }

        int tp = 0;
        int fp = 0;

        for (String predictedPair : predictedPairs) {
            if (groundTruthPairs.contains(predictedPair)) {
                tp++;
            } else {
                fp++;
                // System.out.println("  [False Positive] Алгоритм помилково з'єднав: " + predictedPair);
            }
        }

        int fn = groundTruthPairs.size() - tp;

        double precision = (tp + fp == 0) ? 0 : (double) tp / (tp + fp);
        double recall = (tp + fn == 0) ? 0 : (double) tp / (tp + fn);
        double f1 = (precision + recall == 0) ? 0 : 2 * (precision * recall) / (precision + recall);

        System.out.println("Всього правильних пар в еталоні: " + groundTruthPairs.size());
        System.out.println("Всього пар знайшов алгоритм:     " + predictedPairs.size());
        System.out.printf("\nTrue Positives (TP):  %d%n", tp);
        System.out.printf("False Positives (FP): %d%n", fp);
        System.out.printf("False Negatives (FN): %d%n", fn);
        System.out.printf("\nPrecision (Точність): %.4f (%.2f%%)%n", precision, precision * 100);
        System.out.printf("Recall (Повнота):     %.4f (%.2f%%)%n", recall, recall * 100);
        System.out.printf("F1-Score:             %.4f (%.2f%%)%n", f1, f1 * 100);
    }

    private static String makePairKey(String img1, String img2) {
        if (img1.compareTo(img2) > 0) {
            return img2 + "<->" + img1;
        }
        return img1 + "<->" + img2;
    }

    private static List<List<String>> parseGroundTruth(String json) {
        List<List<String>> groups = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"query\"\\s*:\\s*\"([^\"]+)\",\\s*\"similar\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher matcher = pattern.matcher(json);

        while (matcher.find()) {
            List<String> currentGroup = new ArrayList<>();
            currentGroup.add(matcher.group(1));

            String similarStr = matcher.group(2);
            if (!similarStr.trim().isEmpty()) {
                String[] similars = similarStr.split(",");
                for (String s : similars) {
                    currentGroup.add(s.replaceAll("\"", "").trim());
                }
            }
            groups.add(currentGroup);
        }
        return groups;
    }
}