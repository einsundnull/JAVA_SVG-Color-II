package main;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

public class ConverterAnyImageToBMP {

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFileChooser chooser = new JFileChooser(LastUsedDirectory.load());
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

			int result = chooser.showOpenDialog(null);
			if (result == JFileChooser.APPROVE_OPTION) {
				File folder = chooser.getSelectedFile();
				LastUsedDirectory.save(folder);

				// KORRIGIERT: Hinweis entfernt, da automatische Installation
				System.out.println("Starte automatische Potrace-Installation falls nötig...");

				File[] images = folder.listFiles(f -> {
					String name = f.getName().toLowerCase();
					return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
							|| name.endsWith(".bmp") || name.endsWith(".pgm");
				});

				if (images == null)
					return;

				for (File image : images) {
					convertImageToBMP(image);
				}
			}
		});
	}

	public static File convertImageToBMP(File image) {
		try {
			File bmpFile;
			boolean isAlreadyBmp = image.getName().toLowerCase().endsWith(".bmp");

			if (!isAlreadyBmp) {
				String tmpName = image.getName().replaceAll("\\.[^.]+$", "") + ".tmp.bmp";
				bmpFile = new File(image.getParent(), tmpName);
				ImageConverter.convertToRealBmp(image, bmpFile);
			} else {
				bmpFile = image;
			}

			String baseName = image.getName().replaceAll("\\.[^.]+$", "");
			File svgFile = new File(image.getParent(), baseName + ".svg");

			// KORRIGIERT: Verwende die automatische Installation aus ConverterHelperPotraceRunner
			ConverterHelperPotraceRunner.convertToSvg(bmpFile, svgFile);

			if (!isAlreadyBmp) {
				bmpFile.delete();
			}
			return bmpFile;
		} catch (IOException e) {
			System.err.println("Fehler bei: " + image.getName());
			e.printStackTrace();
		}
		return image;
	}
	
	public static BufferedImage convertImageToBMP(BufferedImage image, String outputPath, String baseName) {
	    try {
	        // Temporäre BMP-Datei erstellen
	        String tmpName = baseName + ".tmp.bmp";
	        File bmpFile = new File(outputPath, tmpName);

	        // BufferedImage als BMP speichern
	        ImageIO.write(image, "bmp", bmpFile);

	        // SVG-Datei für die Konvertierung vorbereiten
	        File svgFile = new File(outputPath, baseName + ".svg");

	        // KORRIGIERT: Verwende die automatische Installation aus ConverterHelperPotraceRunner
	        BufferedImage result = ConverterHelperPotraceRunner.convertToSvg(image, svgFile);

	        // Temporäre BMP-Datei löschen
	        bmpFile.delete();

	        return result;
	    } catch (IOException e) {
	        System.err.println("Fehler bei der Konvertierung von: " + baseName);
	        e.printStackTrace();
	    }
	    return image;
	}

	public static BufferedImage convertImageToBMP(BufferedImage image) {
	    try {
	        // Bereits ein BufferedImage - BMP-Format prüfen und ggf. konvertieren
	        // Da BufferedImage bereits im Speicher ist, arbeiten wir direkt damit
	        
	        // Temporäre Dateien für SVG-Konvertierung erstellen
	        File tempBmpFile = File.createTempFile("input_", ".bmp");
	        File tempSvgFile = File.createTempFile("output_", ".svg");
	        
	        try {
	            // BufferedImage als BMP-Datei speichern (für Potrace)
	            ImageIO.write(image, "bmp", tempBmpFile);
	            
	            // KORRIGIERT: Verwende die automatische Installation aus ConverterHelperPotraceRunner
	            ConverterHelperPotraceRunner.convertToSvg(image, tempSvgFile);
	            
	            return image; // Das ursprüngliche BufferedImage zurückgeben
	            
	        } finally {
	            // Temporäre Dateien aufräumen
	            if (tempBmpFile.exists()) {
	                tempBmpFile.delete();
	            }
	            if (tempSvgFile.exists()) {
	                tempSvgFile.delete();
	            }
	        }
	        
	    } catch (IOException e) {
	        System.err.println("Fehler bei der BufferedImage-Konvertierung");
	        e.printStackTrace();
	    }
	    return image;
	}

	// KORRIGIERT: Diese Methode ist jetzt überflüssig, da ConverterHelperPotraceRunner alles macht
	// Aber falls sie noch gebraucht wird, hier die korrigierte Version:
	public static BufferedImage convertToSvg(BufferedImage bmpInput, File svgOutput) throws IOException {
		// KORRIGIERT: Delegiere an ConverterHelperPotraceRunner mit automatischer Installation
		return ConverterHelperPotraceRunner.convertToSvg(bmpInput, svgOutput);
	}
}