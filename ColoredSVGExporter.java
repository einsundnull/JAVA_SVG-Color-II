package main;
// offline Version
import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ColoredSVGExporter {

	public static void exportColorEntryAsSVG(ColorEntry entry, int index) {
		File tempDir = new File("temp");
		if (!tempDir.exists())
			tempDir.mkdirs();

		String filenameBase = "color_" + index;
		Color color = entry.getColor();
		String pathData = PotraceBridge.vectorizeSingleMask(entry.getMask(), tempDir, filenameBase, color);
		if (pathData == null || pathData.isEmpty())
			return;

		String colorHex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());

		// Prüfe ob Zielfarbe weiß ist
		boolean isWhiteTarget = color.getRed() > 240 && color.getGreen() > 240 && color.getBlue() > 240;
		String backgroundColor = isWhiteTarget ? "none" : "#ffffff";

		StringBuilder svg = new StringBuilder();
		svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
		svg.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" ");
		svg.append("\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n");
		svg.append("<svg version=\"1.0\" xmlns=\"http://www.w3.org/2000/svg\"\n");
		svg.append(" width=\"983.000000pt\" height=\"709.000000pt\"\n");
		svg.append(" viewBox=\"0 0 983.000000 709.000000\"\n");
		svg.append(" preserveAspectRatio=\"xMidYMid meet\">\n");

		svg.append("  <title>").append(filenameBase).append(" - ").append(colorHex).append("</title>\n");
		svg.append("  <desc>Single color layer: ").append(colorHex).append("</desc>\n\n");

		// Hintergrund (weiß oder transparent)
		if (!isWhiteTarget) {
			svg.append("  <rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n\n");
		}

		// Transform-Gruppe mit Potrace-Koordinaten
		svg.append("  <g transform=\"translate(0.000000,709.000000) scale(0.100000,-0.100000)\">\n");

		// Füge Pfade ein - pathData enthält bereits komplette path-Elemente
		String[] lines = pathData.split("\n");
		for (String line : lines) {
			String trimmedLine = line.trim();
			if (!trimmedLine.isEmpty()) {
				// Ersetze fill-Attribute mit Zielfarbe
				if (trimmedLine.contains("fill=")) {
					trimmedLine = trimmedLine.replaceAll("fill=\"[^\"]*\"", "fill=\"" + colorHex + "\"");
				}
				svg.append("    ").append(trimmedLine).append("\n");
			}
		}

		svg.append("  </g>\n");
		svg.append("</svg>");

		try (FileWriter writer = new FileWriter(new File(tempDir, filenameBase + ".svg"))) {
			writer.write(svg.toString());
			System.out.println("💾 Colored SVG gespeichert: " + filenameBase + ".svg (Farbe: " + colorHex
					+ ", Hintergrund: " + (isWhiteTarget ? "transparent" : "weiß") + ")");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}