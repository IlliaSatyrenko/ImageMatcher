import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SequentialImageFinder<T> extends AbstractImageFinder<T> {
    public SequentialImageFinder(ImageHashingStrategy hashingStrategy, ColorFilteringStrategy<T> colorStrategy) {
        super(hashingStrategy, colorStrategy);
    }

    @Override
    public List<ImageMatch> findSimilarImages(List<Path> allImages) {
        List<ImageSignature<T>> signatures = new ArrayList<>();
        List<ImageMatch> matches = new ArrayList<>();

        for (Path path : allImages) {
            BufferedImage img = loadImage(path);

            if (img != null) {
                ImageSignature<T> sig = createSignature(path, img);
                signatures.add(sig);
            }
        }

        for (int i = 0; i < signatures.size(); i++) {
            for (int j = i + 1; j < signatures.size(); j++) {

                ImageMatch match = compareSignatures(signatures.get(i), signatures.get(j));

                if (match != null) {
                    matches.add(match);
                }
            }
        }

        return matches;
    }
}