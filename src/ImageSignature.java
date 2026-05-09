import java.nio.file.Path;

public record ImageSignature<T>(
        Path path,
        long pHash,
        T colorData
) {}
