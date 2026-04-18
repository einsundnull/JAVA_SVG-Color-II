package main;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class ImageConverter {

    public static File convertToRealBmp(File inputImage, File outputBmp) throws IOException {
        BufferedImage image = ImageIO.read(inputImage);
        if (image == null) {
            throw new IOException("Bild konnte nicht gelesen werden: " + inputImage.getName());
        }

        // Entfernt Transparenz (ersetzt mit weißem Hintergrund) + garantiert RGB
        BufferedImage rgbImage = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g = rgbImage.createGraphics();
        g.drawImage(image, 0, 0, Color.WHITE, null);
        g.dispose();

        if (!ImageIO.write(rgbImage, "bmp", outputBmp)) {
            throw new IOException("BMP konnte nicht geschrieben werden: " + outputBmp.getName());
        }

        return outputBmp;
    }
}
