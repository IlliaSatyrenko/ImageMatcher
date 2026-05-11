import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

public class DatasetGenerator {
    private static final String SOURCE_DIR = "source_images2";
    private static final String OUTPUT_DIR = "generated_dataset_50-60";
    private static final String JSON_OUTPUT = "hard_ground_truth_50-60.json";
    private static final Random random = new Random();

    public static void main(String[] args) {
        try {
            Path sourcePath = Paths.get(SOURCE_DIR);
            Path outputPath = Paths.get(OUTPUT_DIR);

            if (!Files.exists(sourcePath)) {
                System.err.println("Помилка: Немає папки " + SOURCE_DIR);
                return;
            }
            if (!Files.exists(outputPath)) Files.createDirectories(outputPath);

            StringBuilder jsonBuilder = new StringBuilder("{\n");
            int groupCounter = 0;
            boolean isFirstGroup = true;

            try (Stream<Path> paths = Files.list(sourcePath)) {
                List<Path> files = paths.filter(Files::isRegularFile).toList();

                for (Path file : files) {
                    BufferedImage originalImage = ImageIO.read(file.toFile());
                    if (originalImage == null) continue;

                    BufferedImage rgbImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                    rgbImage.getGraphics().drawImage(originalImage, 0, 0, null);

                    String baseName = "img_" + String.format("%04d", groupCounter);
                    String queryName = baseName + "_orig.jpg";
                    saveHighQuality(rgbImage, queryName);

                    List<String> similarNames = new ArrayList<>();

                    int variantsCount = 50 + random.nextInt(11);

                    for (int i = 0; i < variantsCount; i++) {
                        String variantName = baseName + "_var" + i + ".jpg";
                        BufferedImage variant = copyImage(rgbImage);

                        if (random.nextDouble() > 0.3) {
                            variant = randomCrop(variant);
                        }

                        if (random.nextDouble() > 0.5) {
                            variant = randomBrightness(variant);
                        }

                        if (random.nextDouble() > 0.6) {
                            addRandomWatermark(variant);
                        }

                        if (random.nextDouble() > 0.7) {
                            addNoise(variant);
                        }

                        float jpegQuality = 0.3f + random.nextFloat() * 0.5f;
                        saveWithJpegCompression(variant, variantName, jpegQuality);

                        similarNames.add(variantName);
                    }

                    if (!isFirstGroup) {
                        jsonBuilder.append(",\n");
                    }
                    jsonBuilder.append(String.format("  \"%d\": {\n", groupCounter));
                    jsonBuilder.append(String.format("    \"query\": \"%s\",\n", queryName));
                    jsonBuilder.append("    \"similar\": [\n");

                    for (int i = 0; i < similarNames.size(); i++) {
                        jsonBuilder.append(String.format("      \"%s\"", similarNames.get(i)));

                        if (i < similarNames.size() - 1) {
                            jsonBuilder.append(",");
                        }
                        jsonBuilder.append("\n");
                    }

                    jsonBuilder.append("    ]\n  }");

                    isFirstGroup = false;
                    groupCounter++;
                    originalImage.flush();
                    rgbImage.flush();
                }
            }

            jsonBuilder.append("\n}");
            try (FileWriter fileWriter = new FileWriter(JSON_OUTPUT)) {
                fileWriter.write(jsonBuilder.toString());
            }

            System.out.println("Генерацію датасету завершено!");
            System.out.println("Створено груп: " + groupCounter);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static BufferedImage randomCrop(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        double scale = 0.8 + (random.nextDouble() * 0.15);
        int targetW = (int) (w * scale);
        int targetH = (int) (h * scale);

        int x = random.nextInt((w - targetW) / 2);
        int y = random.nextInt((h - targetH) / 2);

        return img.getSubimage(x, y, targetW, targetH);
    }

    private static BufferedImage randomBrightness(BufferedImage img) {
        float scaleFactor = 0.6f + random.nextFloat() * 0.8f;
        RescaleOp op = new RescaleOp(scaleFactor, 0, null);
        return op.filter(img, null);
    }

    private static void addRandomWatermark(BufferedImage img) {
        Graphics2D g2d = img.createGraphics();

        AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f);
        g2d.setComposite(alphaChannel);
        g2d.setColor(Color.WHITE);

        int fontSize = Math.max(20, img.getWidth() / 15);
        g2d.setFont(new Font("Arial", Font.BOLD, fontSize));

        String text = "@Random_Mark_" + random.nextInt(999);
        FontMetrics fontMetrics = g2d.getFontMetrics();

        int x = random.nextInt(Math.max(1, img.getWidth() - fontMetrics.stringWidth(text)));
        int y = fontSize + random.nextInt(Math.max(1, img.getHeight() - fontSize));

        g2d.drawString(text, x, y);
        g2d.dispose();
    }

    private static void addNoise(BufferedImage img) {
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                if (random.nextDouble() > 0.85) {
                    int color = img.getRGB(x, y);
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;

                    r = Math.min(255, Math.max(0, r + random.nextInt(100) - 50));
                    g = Math.min(255, Math.max(0, g + random.nextInt(100) - 50));
                    b = Math.min(255, Math.max(0, b + random.nextInt(100) - 50));

                    img.setRGB(x, y, new Color(r, g, b).getRGB());
                }
            }
        }
    }

    private static void saveHighQuality(BufferedImage img, String fileName) throws IOException {
        saveWithJpegCompression(img, fileName, 1.0f);
    }

    private static void saveWithJpegCompression(BufferedImage img, String fileName, float quality) throws IOException {
        File outputFile = new File(OUTPUT_DIR, fileName);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

        if (!writers.hasNext()) {
            throw new IllegalStateException("No writers found");
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage copyImage(BufferedImage source) {
        BufferedImage b = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
        Graphics g = b.getGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return b;
    }
}