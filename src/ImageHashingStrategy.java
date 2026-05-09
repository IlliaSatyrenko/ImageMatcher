import java.awt.image.BufferedImage;
import java.io.IOException;

public interface ImageHashingStrategy {
    long calculateHash(BufferedImage image) throws IOException;
    int calculateDistance(long hash1, long hash2);
}