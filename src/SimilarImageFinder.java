import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class SimilarImageFinder<T> {
    private static final float HASH_THRESHOLD = 24;

    private final ImageHashingStrategy hashingStrategy;
    private final ColorFilteringStrategy<T> colorStrategy;

    public SimilarImageFinder(ImageHashingStrategy hashingStrategy, ColorFilteringStrategy<T> colorStrategy) {
        this.hashingStrategy = hashingStrategy;
        this.colorStrategy = colorStrategy;
    }

    public List<ImageMatch> findSimilarImages(List<Path> uniqueImages) {
        System.out.println("\nЕтап 1: Одноразове читання файлів та генерація сігнатур");
        List<ImageSignature<T>> signatures = new ArrayList<>();

        for (Path imgPath : uniqueImages) {
            try {
                BufferedImage img = ImageIO.read(imgPath.toFile());
                if (img == null) continue;

                long hash = hashingStrategy.calculateHash(img);

                T colorData = colorStrategy.extractColorData(img);

                signatures.add(new ImageSignature<T>(imgPath, hash, colorData));

                img.flush();
            } catch (IOException e) {
                System.err.printf("Пропущено файл: %s (%s)%n", imgPath, e.getMessage());
            }
        }

        System.out.println("Етап 2: Пошук збігів у пам'яті\n");
        List<ImageMatch> matches = new ArrayList<>();

        for (int i = 0; i < signatures.size(); i++) {
            ImageSignature<T> sig1 = signatures.get(i);

            for (int j = i + 1; j < signatures.size(); j++) {
                ImageSignature<T> sig2 = signatures.get(j);

                int distance = hashingStrategy.calculateDistance(sig1.pHash(), sig2.pHash());

                if (distance <= HASH_THRESHOLD) {
                    if (colorStrategy.areColorsSimilar(sig1.colorData(), sig2.colorData())) {
                        matches.add(new ImageMatch(sig1.path(), sig2.path(), distance));
                    }
                }
            }
        }

        return List.copyOf(matches);
    }
}