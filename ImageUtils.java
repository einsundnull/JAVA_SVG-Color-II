package main;
// offline Version
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

public class ImageUtils {

	/** Posterisiert mit expliziter Fallback-Farbe für Pixel ohne Palette-Treffer. */
	public static BufferedImage posterize(BufferedImage original, List<Color> palette, int tolerance, Color noMatchColor) {
		int width = original.getWidth();
		int height = original.getHeight();
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		int n = palette.size();
		int[] pr = new int[n], pg = new int[n], pb = new int[n], prgb = new int[n];
		for (int i = 0; i < n; i++) {
			Color c = palette.get(i);
			pr[i] = c.getRed(); pg[i] = c.getGreen(); pb[i] = c.getBlue();
			prgb[i] = c.getRGB();
		}
		int noMatchRGB = noMatchColor.getRGB();
		double tol2 = (double) tolerance * tolerance * 3;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int rgb = original.getRGB(x, y);
				int r = (rgb >> 16) & 0xFF;
				int g = (rgb >>  8) & 0xFF;
				int b =  rgb        & 0xFF;

				int bestIdx = -1;
				double bestDist = Double.MAX_VALUE;
				for (int i = 0; i < n; i++) {
					int dr = r - pr[i], dg = g - pg[i], db = b - pb[i];
					double dist = dr*dr + dg*dg + db*db;
					if (dist <= tol2 && dist < bestDist) {
						bestDist = dist;
						bestIdx = i;
					}
				}
				result.setRGB(x, y, bestIdx >= 0 ? prgb[bestIdx] : noMatchRGB);
			}
		}
		return result;
	}

	/** Posterisiert; Pixel ohne Palette-Treffer werden mit der globalen Transparenzfarbe markiert. */
	public static BufferedImage posterize(BufferedImage original, List<Color> palette, int tolerance) {
		return posterize(original, palette, tolerance, ColorUtils.TRANSPARENCY_COLOR);
	}
	public static BufferedImage convertToGrayscale(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				Color c = new Color(image.getRGB(x, y), true);
				int gray = (int) (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
				Color grayColor = new Color(gray, gray, gray, c.getAlpha());
				result.setRGB(x, y, grayColor.getRGB());
			}
		}
		return result;
	}

	public static BufferedImage posterizeAndAlpha(BufferedImage original, int levels) {
		int w = original.getWidth();
		int h = original.getHeight();
		BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = original.getRGB(x, y);

				int alpha = 255;
				int red = (rgb >> 16) & 0xFF;
				int green = (rgb >> 8) & 0xFF;
				int blue = rgb & 0xFF;

				red = posterize(red, levels);
				green = posterize(green, levels);
				blue = posterize(blue, levels);

				if (red > 245 && green > 245 && blue > 245)
					alpha = 0;

				int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
				result.setRGB(x, y, argb);
			}
		}

		return result;
	}

	private static int posterize(int value, int levels) {
		return Math.round(Math.round(value * (levels - 1) / 255.0f) * 255.0f / (levels - 1));
	}

	private static Color findClosestColor(Color target, List<Color> palette, int tolerance) {
		// ImageUtils.java findClosestColor()
		Color closest = null;
		double minDistance = Double.MAX_VALUE;

		for (Color candidate : palette) {
			double distance = colorDistance(target, candidate);
			if (distance <= tolerance && distance < minDistance) {
				minDistance = distance;
				closest = candidate;
			}
		}
		return closest;
	}

	public static BufferedImage removeColorToAlpha(BufferedImage image, Color transparencyColor) {
		int width = image.getWidth();
		int height = image.getHeight();
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int targetRGB = transparencyColor.getRGB();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = image.getRGB(x, y);
				if (pixel == targetRGB) {
					result.setRGB(x, y, (pixel & 0x00FFFFFF));
				} else {
					result.setRGB(x, y, pixel);
				}
			}
		}
		return result;
	}

	/**
	 * Berechnet den RGB-Abstand zweier Farben.
	 */
//	private static double colorDistance(Color c1, Color c2) {
//		int dr = c1.getRed() - c2.getRed();
//		int dg = c1.getGreen() - c2.getGreen();
//		int db = c1.getBlue() - c2.getBlue();
//		return Math.sqrt(dr * dr + dg * dg + db * db);
//	}

	/**
	 * Speichert ein BufferedImage als PNG.
	 */
	public static void savePNG(BufferedImage image, File file) throws IOException {
		ImageIO.write(image, "png", file);
		System.out.println(
				"💾 PNG gespeichert: " + file.getName() + " (" + image.getWidth() + "x" + image.getHeight() + ")");
	}

	/**
	 * Erstellt eine verkleinerte Vorschau eines Bildes.
	 */
	public static BufferedImage createPreview(BufferedImage original, int maxSize) {
		int width = original.getWidth();
		int height = original.getHeight();

		if (width <= maxSize && height <= maxSize)
			return original;

		double scale = Math.min((double) maxSize / width, (double) maxSize / height);
		int newWidth = (int) (width * scale);
		int newHeight = (int) (height * scale);

		BufferedImage preview = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = preview.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
		g2d.dispose();

		System.out.println("🔍 Vorschau erstellt: " + width + "x" + height + " -> " + newWidth + "x" + newHeight);
		return preview;
	}

	/**
	 * Schätzt dominante Farben eines Bildes (vereinfacht, ohne K-Means).
	 */
	public static List<Color> findDominantColors(BufferedImage image, int colorCount) {
		Map<Integer, Integer> colorFrequency = new HashMap<>();
		BufferedImage sample = createPreview(image, 100);

		for (int y = 0; y < sample.getHeight(); y++) {
			for (int x = 0; x < sample.getWidth(); x++) {
				int rgb = sample.getRGB(x, y);
				int quantized = quantizeColor(rgb, 64);
				colorFrequency.put(quantized, colorFrequency.getOrDefault(quantized, 0) + 1);
			}
		}

		return colorFrequency.entrySet().stream().sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
				.limit(colorCount).map(entry -> new Color(entry.getKey())).collect(Collectors.toList());
	}

	/**
	 * Reduziert die Farbauflösung zur besseren Gruppierung.
	 */
	private static int quantizeColor(int rgb, int levels) {
		Color c = new Color(rgb);
		int step = 256 / levels;
		int r = (c.getRed() / step) * step;
		int g = (c.getGreen() / step) * step;
		int b = (c.getBlue() / step) * step;
		return new Color(Math.min(r, 255), Math.min(g, 255), Math.min(b, 255)).getRGB();
	}

	/**
	 * 🔧 Erstellt eine binäre Maske (schwarz/weiß) für eine bestimmte Ziel-Farbe
	 * mit Toleranz. Wird für Vektorisierung einzelner Farben genutzt (z. B. in
	 * ColoredSVGExporter).
	 */
	public static BufferedImage createColorSpecificMask(BufferedImage image, Color targetColor, int tolerance) {
		int w = image.getWidth();
		int h = image.getHeight();
		BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				Color c = new Color(image.getRGB(x, y));
				boolean match = Math.abs(c.getRed() - targetColor.getRed()) <= tolerance
						&& Math.abs(c.getGreen() - targetColor.getGreen()) <= tolerance
						&& Math.abs(c.getBlue() - targetColor.getBlue()) <= tolerance;

				mask.setRGB(x, y, match ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
			}
		}
		return mask;
	}

	/**
	 * Findet alle einzigartigen Farben in einem Bild. Jede Farbe wird nur einmal
	 * zurückgegeben, auch wenn sie mehrfach vorkommt.
	 */
	public static List<Color> findAllUniqueColors(BufferedImage image) {
		Set<Integer> uniqueRGBValues = new HashSet<>();
		int width = image.getWidth();
		int height = image.getHeight();

		System.out.println("🔍 Suche alle einzigartigen Farben in Bild (" + width + "x" + height + ")...");

		// Sammle alle einzigartigen RGB-Werte
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int rgb = image.getRGB(x, y);
				uniqueRGBValues.add(rgb);
			}
		}

		// Konvertiere zu Color-Liste
		List<Color> colors = uniqueRGBValues.stream().map(Color::new).collect(Collectors.toList());

		System.out.println("✅ Gefunden: " + colors.size() + " einzigartige Farben");
		return colors;
	}

	public static FloodFillResult floodFill(BufferedImage image, int startX, int startY, Color fillColor,
			boolean makeTransparent, int tolerance) {
		if (startX < 0 || startY < 0 || startX >= image.getWidth() || startY >= image.getHeight())
			return new FloodFillResult(0, null, makeTransparent);

		Color origin = new Color(image.getRGB(startX, startY));
		if (ColorUtils.colorsMatch(origin, fillColor, tolerance))
			return new FloodFillResult(0, origin, makeTransparent);

		Queue<Point> queue = new LinkedList<>();
		boolean[][] visited = new boolean[image.getWidth()][image.getHeight()];
		queue.offer(new Point(startX, startY));
		int count = 0;

		while (!queue.isEmpty()) {
			Point p = queue.poll();
			int x = p.x;
			int y = p.y;
			if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight() || visited[x][y])
				continue;
			if (!ColorUtils.colorsMatch(new Color(image.getRGB(x, y)), origin, tolerance))
				continue;
			visited[x][y] = true;
			int rgb = makeTransparent
					? (fillColor.getRGB() & 0x00FFFFFF)
					: (0xFF000000 | (fillColor.getRGB() & 0x00FFFFFF));
			image.setRGB(x, y, rgb);
			count++;
			queue.offer(new Point(x + 1, y));
			queue.offer(new Point(x - 1, y));
			queue.offer(new Point(x, y + 1));
			queue.offer(new Point(x, y - 1));
		}

		return new FloodFillResult(count, origin, makeTransparent);
	}

	public static boolean performFloodFill(int startX, int startY, Color fillColor, boolean isTransparency,
			BufferedImage image) {
		return floodFill(image, startX, startY, fillColor, isTransparency, 5).changed();
	}

	public static boolean performFloodFill(int startX, int startY, Color fillColor, int tolerance,
			BufferedImage image) {
		return floodFill(image, startX, startY, fillColor, false, tolerance).changed();
	}

	public static boolean performQuickFloodFill(int startX, int startY, Color transparencyColor,
			BufferedImage image) {
		return floodFill(image, startX, startY, transparencyColor, true, 5).changed();
	}
}
