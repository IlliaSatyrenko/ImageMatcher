import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class SpatialGridColorFilter implements ColorFilteringStrategy<SpatialGridColorFilter.GridData> {
    public record GridData (
            int[][] rgbGrid,
            float[][] hsvGrid
    ) {}

    private static final int GRID_SIZE = 3;
    private static final int MIN_MATCHING_BLOCKS = 7;
    private static final int SAMPLE_SIZE = 60;

    private static final float HUE_TOLERANCE_DEGREES = 30.0f;
    private static final double MIN_COSINE_THRESHOLD = 0.96;
    private static final double MAX_COSINE_THRESHOLD = 0.9995;
    private static final float SATURATION_THRESHOLD = 0.15f;
    private static final float BLACK_THRESHOLD = 0.05f;

    @Override
    public GridData extractColorData(BufferedImage img) {
        BufferedImage pixelated = new BufferedImage(SAMPLE_SIZE, SAMPLE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = pixelated.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(img, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE, null);
        graphics.dispose();

        int[][] rgbGrid = new int[GRID_SIZE * GRID_SIZE][3];
        float[][] hsvGrid = new float[GRID_SIZE * GRID_SIZE][3];

        int blockSize = SAMPLE_SIZE / GRID_SIZE;

        for (int blockY = 0; blockY < GRID_SIZE; blockY++) {
            for (int blockX = 0; blockX < GRID_SIZE; blockX++) {
                long sumR = 0, sumG = 0, sumB = 0;

                for (int y = 0; y < blockSize; y++) {
                    for (int x = 0; x < blockSize; x++) {
                        int pixelX = blockX * blockSize + x;
                        int pixelY = blockY * blockSize + y;

                        int rgb = pixelated.getRGB(pixelX, pixelY);

                        sumR += (rgb >> 16) & 0xFF;
                        sumG += (rgb >> 8) & 0xFF;
                        sumB += rgb & 0xFF;
                    }
                }

                int totalPixels = blockSize * blockSize;
                int r = (int) (sumR / totalPixels);
                int g = (int) (sumG / totalPixels);
                int b = (int) (sumB / totalPixels);

                int index = blockY * GRID_SIZE + blockX;
                rgbGrid[index][0] = r;
                rgbGrid[index][1] = g;
                rgbGrid[index][2] = b;

                Color.RGBtoHSB(r, g, b, hsvGrid[index]);
            }
        }

        return new GridData(rgbGrid, hsvGrid);
    }

    @Override
    public boolean areColorsSimilar(GridData data1, GridData data2) {
        int matchingBlocks = 0;
        int totalBlocks = GRID_SIZE * GRID_SIZE;

        for (int i = 0; i < totalBlocks; i++) {
            if (isBlockSimilar(data1.rgbGrid()[i], data2.rgbGrid()[i], data1.hsvGrid()[i], data2.hsvGrid()[i])) {
                matchingBlocks++;
            }
        }

        return matchingBlocks >= MIN_MATCHING_BLOCKS;
    }

    public static boolean isBlockSimilar(int[] rgb1, int[] rgb2, float[] hsv1, float[] hsv2) {
        boolean isBlack1 = hsv1[2] < BLACK_THRESHOLD;
        boolean isBlack2 = hsv2[2] < BLACK_THRESHOLD;

        if (isBlack1 && isBlack2) {
            return true;
        }

        if (isBlack1 || isBlack2) {
            return false;
        }

        if (hsv1[1] < SATURATION_THRESHOLD || hsv2[1] < SATURATION_THRESHOLD) {
            return areVectorsSimilar(rgb1, rgb2, hsv1[2], hsv2[2]);
        }

        float hue1 = hsv1[0] * 360.0f;
        float hue2 = hsv2[0] * 360.0f;

        float diff = Math.abs(hue1 - hue2);
        float shortestDistance = Math.min(diff, 360.0f - diff);

        return shortestDistance <= HUE_TOLERANCE_DEGREES;
    }


    private static boolean areVectorsSimilar(int[] rgb1, int[] rgb2, float v1, float v2) {
        double dotProduct = (rgb1[0] * rgb2[0]) + (rgb1[1] * rgb2[1]) + (rgb1[2] * rgb2[2]);

        double magnitude1 = Math.sqrt((rgb1[0] * rgb1[0]) + (rgb1[1] * rgb1[1]) + (rgb1[2] * rgb1[2]));
        double magnitude2 = Math.sqrt((rgb2[0] * rgb2[0]) + (rgb2[1] * rgb2[1]) + (rgb2[2] * rgb2[2]));

        if (magnitude1 == 0 && magnitude2 == 0) return true;
        if (magnitude1 == 0 || magnitude2 == 0) return false;

        double cosineSimilarity = dotProduct / (magnitude1 * magnitude2);

        float maxV = Math.max(v1, v2);
        double dynamicThreshold = MIN_COSINE_THRESHOLD + (MAX_COSINE_THRESHOLD - MIN_COSINE_THRESHOLD) * maxV;

        return cosineSimilarity >= dynamicThreshold;
    }
}