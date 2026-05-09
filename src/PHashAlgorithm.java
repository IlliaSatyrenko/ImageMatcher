import java.awt.*;
import java.awt.image.BufferedImage;

public class PHashAlgorithm implements ImageHashingStrategy {
    private static final int SIZE = 32;
    private static final int CROP_SIZE = 8;

    private static final double[][] COSINE_CACHE = new double[SIZE][SIZE];

    static {
        for (int u = 0; u < SIZE; u++) {
            for (int x = 0; x < SIZE; x++) {
                COSINE_CACHE[u][x] = Math.cos(((2.0 * x + 1.0) * u * Math.PI) / (2.0 * SIZE));
            }
        }
    }

    @Override
    public long calculateHash(BufferedImage img) {
        BufferedImage resized = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, SIZE, SIZE, null);
        g.dispose();

        double[][] pixels = new double[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                pixels[y][x] = resized.getRGB(x, y) & 0xFF;
            }
        }

        double[][] dctMatrix = apply2DDCT(pixels);

        double[] lowFrequencies = new double[CROP_SIZE * CROP_SIZE];
        double sum = 0.0;
        int index = 0;

        for (int u = 0; u < CROP_SIZE; u++) {
            for (int v = 0; v < CROP_SIZE; v++) {
                if (u == 0 && v == 0) {
                    continue;
                }

                double val = dctMatrix[u][v];
                lowFrequencies[index] = val;
                sum += val;
                index++;
            }
        }

        double average = sum / (lowFrequencies.length - 1);

        long hash = 0;
        index = 0;

        for (int u = 0; u < CROP_SIZE; u++) {
            for (int v = 0; v < CROP_SIZE; v++) {
                if (u == 0 && v == 0) {
                    continue;
                }

                if (lowFrequencies[index] > average) {
                    hash |= (1L << index);
                }
                index++;
            }
        }

        return hash;
    }

    @Override
    public int calculateDistance(long hash1, long hash2) {
        return Long.bitCount(hash1 ^ hash2);
    }

    private double[][] apply2DDCT(double[][] input) {
        double[][] temp = new double[SIZE][SIZE];
        double[][] output = new double[SIZE][SIZE];

        for (int i = 0; i < SIZE; i++) {
            for (int u = 0; u < SIZE; u++) {
                double sum = 0;
                for (int x = 0; x < SIZE; x++) {
                    sum += input[i][x] * COSINE_CACHE[u][x];
                }
                temp[i][u] = sum * (u == 0 ? 1 / Math.sqrt(2.0) : 1);
            }
        }

        for (int j = 0; j < SIZE; j++) {
            for (int v = 0; v < SIZE; v++) {
                double sum = 0;
                for (int y = 0; y < SIZE; y++) {
                    sum += temp[y][j] * COSINE_CACHE[v][y];
                }
                output[j][v] = sum * (v == 0 ? 1 / Math.sqrt(2.0) : 1) * (2.0 / SIZE);
            }
        }

        return output;
    }
}