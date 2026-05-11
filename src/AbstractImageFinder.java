import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

public abstract class AbstractImageFinder<T> {
    protected static final int HASH_THRESHOLD = 24;

    protected final ImageHashingStrategy hashingStrategy;
    protected final ColorFilteringStrategy<T> colorStrategy;

    public AbstractImageFinder(ImageHashingStrategy hashingStrategy, ColorFilteringStrategy<T> colorStrategy) {
        this.hashingStrategy = hashingStrategy;
        this.colorStrategy = colorStrategy;
    }

    public abstract List<ImageMatch> findSimilarImages(List<Path> allImages) throws Exception;

    protected BufferedImage loadImage(Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (Exception e) {
            System.err.println("Помилка читання файлу " + path + ": " + e.getMessage());
            return null;
        }
    }

    protected ImageSignature<T> createSignature(Path path, BufferedImage image) {
        long hash = hashingStrategy.calculateHash(image);
        T colorData = colorStrategy.extractColorData(image);
        return new ImageSignature<>(path, hash, colorData);
    }

    protected ImageMatch compareSignatures(ImageSignature<T> sig1, ImageSignature<T> sig2) {
        int distance = hashingStrategy.calculateDistance(sig1.pHash(), sig2.pHash());

        if (distance <= HASH_THRESHOLD) {
            if (colorStrategy.areColorsSimilar(sig1.colorData(), sig2.colorData())) {
                return new ImageMatch(sig1.path(), sig2.path(), distance);
            }
        }
        return null;
    }
}