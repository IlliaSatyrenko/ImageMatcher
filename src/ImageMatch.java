import java.nio.file.Path;

public record ImageMatch(Path image1, Path image2, int distance) {
}