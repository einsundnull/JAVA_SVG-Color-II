// Klasse: AllImageToSvgConverter.java offline
package main;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class ConverterImageToSvgClassSuitable {

	public static void convertAllImagesInFolder(File folder) {
		File[] images = folder.listFiles(f -> {
			String name = f.getName().toLowerCase();
			return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp")
					|| name.endsWith(".pgm");
		});

		if (images == null)
			return;

		for (File image : images) {
			try {
				// BMP vorbereiten
				File bmpFile;
				boolean isTempBmp = false;

				if (!image.getName().toLowerCase().endsWith(".bmp")) {
					BufferedImage img = ImageIO.read(image);
					String baseName = image.getName().replaceAll(" -_\\.[^.]+$", "");
					bmpFile = new File(folder, baseName);
					ImageIO.write(img, "bmp", bmpFile); // <--- hier kleingeschrieben
					isTempBmp = true;
				} else {
					bmpFile = image;
				}

				// Ziel-SVG-Datei
				String baseName = image.getName().replaceAll(" -_\\.[^.]+$", "");
				File svgFile = new File(folder, baseName + ".svg");

				ConverterHelperPotraceRunner.convertToSvg(bmpFile, svgFile);

				if (isTempBmp) {
					bmpFile.delete();
				}

			} catch (IOException e) {
				System.err.println("Fehler bei: " + image.getName());
				e.printStackTrace();
			}
		}
	}

	public static BufferedImage convertImageToSVG(File image) {
		BufferedImage img = null;
		try {
			boolean isTempBmp = false;
			String parent = image.getParent();

			if (!image.getName().toLowerCase().endsWith(".bmp")) {
				img = ImageIO.read(image);

				String baseName = image.getName().replaceAll(" -_\\.[^.]+$", "");
				image = new File(parent, baseName + ".bmp");
				ImageIO.write(img, "bmp", image); // <--- hier kleingeschrieben
				isTempBmp = true;
			} else {
				image = ConverterAnyImageToBMP.convertImageToBMP(image);
				img = ImageIO.read(image);

				String baseName = image.getName().replaceAll(" -_\\.[^.]+$[.png]", "");
				image = new File(parent, baseName + ".bmp");
				ImageIO.write(img, "bmp", image); // <--- hier kleingeschrieben
				isTempBmp = true;
			}

			// Ziel-SVG-Datei
			String baseName = image.getName().replaceAll(" -_\\.[^.]+$", "");
			File svgFile = new File(parent, baseName + ".svg");

			ConverterHelperPotraceRunner.convertToSvg(image, svgFile);
//			ConverterHelperPotraceRunnerFillColor.convertToSvgWithFillColor(img, svgFile, "#4287f5");

//			ConverterHelperPotraceRunnerStrokeFillColor.convertToSvg(image, svgFile, "#4287f5");

			if (isTempBmp) {
				image.delete();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return img;
	}

	public static void convertImageToSVG(File image, String newPath) {
		try {
			boolean isTempBmp = false;
			String parent = image.getParent();

			if (!image.getName().toLowerCase().endsWith(".bmp")) {
				BufferedImage img;

				img = ImageIO.read(image);

				String baseName = image.getName().replaceAll(" -_\\.[^.]+$", "");
				image = new File(parent, baseName + ".bmp");
				ImageIO.write(img, "bmp", image); // <--- hier kleingeschrieben
				isTempBmp = true;
			} else {
				image = ConverterAnyImageToBMP.convertImageToBMP(image);
				BufferedImage img;

				img = ImageIO.read(image);

				String baseName = image.getName().replaceAll(" -_\\.[^.]+$", "");
				image = new File(parent, baseName + ".bmp");
				ImageIO.write(img, "bmp", image); // <--- hier kleingeschrieben
				isTempBmp = true;
			}

			// Ziel-SVG-Datei
			String baseName = image.getName().replaceAll(" -_\\.[^.]+$", "");
			File svgFile = new File(parent, baseName + ".svg");

			ConverterHelperPotraceRunner.convertToSvg(image, svgFile);

			if (isTempBmp) {
				image.delete();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public static File convertImageToSVG(File image, File newPath) {
		BufferedImage img = null;
		File svgFile = null;
		try {
			boolean isTempBmp = false;
//			String parent = image.getParent();
			String parent = newPath.getAbsolutePath();

			if (!image.getName().toLowerCase().endsWith(".bmp")) {
				img = ImageIO.read(image);
				String baseName = image.getName().replaceAll(" -_\\.[^.]+$", "");
				image = new File(parent, baseName + "_temp.bmp");
				ImageIO.write(img, "bmp", image); // <--- hier kleingeschrieben
				isTempBmp = true;
			} else {
				image = ConverterAnyImageToBMP.convertImageToBMP(image);
				img = ImageIO.read(image);
				String baseName = image.getName().replaceAll(" -_\\.[^.]+$", "");
				image = new File(parent, baseName + "_temp.bmp");
				ImageIO.write(img, "bmp", image); // <--- hier kleingeschrieben
				isTempBmp = true;
			}

			// Ziel-SVG-Datei
			String baseName = image.getName().replaceAll(" -_\\.[^.]+$", "");
			svgFile = new File(parent, baseName + ".svg");

			ConverterHelperPotraceRunner.convertToSvg(image, svgFile);

			if (isTempBmp) {
				image.delete();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return svgFile;
	}
}
