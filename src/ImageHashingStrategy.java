import java.awt.image.BufferedImage;

public interface ImageHashingStrategy {
    long calculateHash(BufferedImage image);
    int calculateDistance(long hash1, long hash2);
}