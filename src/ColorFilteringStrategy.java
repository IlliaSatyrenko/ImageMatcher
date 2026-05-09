import java.awt.image.BufferedImage;

public interface ColorFilteringStrategy<T> {
    T extractColorData(BufferedImage img);
    boolean areColorsSimilar(T data1, T data2);
}