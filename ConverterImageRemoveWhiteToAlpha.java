package main;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

import javax.imageio.ImageIO;

public class ConverterImageRemoveWhiteToAlpha {

    public static void removeWhite(File imageFile) {
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                System.err.println("Bild konnte nicht geladen werden: " + imageFile.getName());
                return;
            }

            if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
                BufferedImage converted = new BufferedImage(
                        image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                converted.getGraphics().drawImage(image, 0, 0, null);
                image = converted;
            }

            floodFillWhiteToTransparent(image);

            // Überschreibe Datei
            ImageIO.write(image, "PNG", imageFile);
            System.out.println("✅ Fertig: " + imageFile.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void floodFillWhiteToTransparent(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[][] visited = new boolean[h][w];
        Queue<int[]> queue = new LinkedList<>();

        // Startpunkte: Ränder
        for (int x = 0; x < w; x++) {
            queue.add(new int[]{x, 0});
            queue.add(new int[]{x, h - 1});
        }
        for (int y = 1; y < h - 1; y++) {
            queue.add(new int[]{0, y});
            queue.add(new int[]{w - 1, y});
        }

        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int x = p[0];
            int y = p[1];

            if (x < 0 || y < 0 || x >= w || y >= h || visited[y][x])
                continue;

            int rgb = image.getRGB(x, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            if (isWhite(r, g, b)) {
                image.setRGB(x, y, 0x00FFFFFF); // alpha = 0, RGB bleibt
                visited[y][x] = true;

                queue.add(new int[]{x + 1, y});
                queue.add(new int[]{x - 1, y});
                queue.add(new int[]{x, y + 1});
                queue.add(new int[]{x, y - 1});
            }
        }
    }

    private static boolean isWhite(int r, int g, int b) {
        return r > 245 && g > 245 && b > 245;
    }
}
