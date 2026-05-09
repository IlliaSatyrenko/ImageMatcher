import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class SmartHistogramFilter implements ColorFilteringStrategy<float[]> {
    private static final int BUCKETS = 14;

    private static final float BLACK_THRESHOLD = 0.13f;
    private static final float SATURATION_THRESHOLD = 0.1f;

    private static final float INTERSECTION_THRESHOLD = 0.85f;

    @Override
    public float[] extractColorData(BufferedImage img) {
        int sampleSize = 50;
        BufferedImage pixelated = new BufferedImage(sampleSize, sampleSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = pixelated.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, sampleSize, sampleSize, null);
        g.dispose();

        float[] histogram = new float[BUCKETS];
        float[] hsb = new float[3];
        int totalPixels = sampleSize * sampleSize;

        for (int y = 0; y < sampleSize; y++) {
            for (int x = 0; x < sampleSize; x++) {
                int rgb = pixelated.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gChan = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                Color.RGBtoHSB(r, gChan, b, hsb);

                float hue = hsb[0] * 360.0f;
                float sat = hsb[1];
                float val = hsb[2];

                if (val < BLACK_THRESHOLD) {
                    histogram[0]++;
                } else if (sat < SATURATION_THRESHOLD) {
                    histogram[1]++;
                } else {
                    int hueBucket = (int) (hue / 30.0f);
                    if (hueBucket >= 12) hueBucket = 11;
                    histogram[2 + hueBucket]++;
                }
            }
        }

        for (int i = 0; i < BUCKETS; i++) {
            histogram[i] /= totalPixels;
        }

        return histogram;
    }

    @Override
    public boolean areColorsSimilar(float[] hist1, float[] hist2) {
        float intersectionScore = 0.0f;

        for (int i = 0; i < BUCKETS; i++) {
            intersectionScore += Math.min(hist1[i], hist2[i]);
        }

        return intersectionScore >= INTERSECTION_THRESHOLD;
    }
}