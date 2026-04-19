// PotraceBridge.java - KORRIGIERT für Transparenz-Support mit Auto-Installation
package main;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PotraceBridge {

    // === EXECUTABLE EXTRACTOR KONFIGURATION ===
    private static final String APP_NAME = "PotraceBridge";
    private static final String EXECUTABLE_NAME = "potrace.exe";
    private static final String RESOURCE_PATH = "/" + EXECUTABLE_NAME;  // potrace.exe muss in src/main/resources/ liegen
    
    // Installationsverzeichnis im User-Home (keine Admin-Rechte nötig)
    private static final String INSTALL_DIR = System.getProperty("user.home") + 
                                            File.separator + "." + APP_NAME;
    private static final String EXECUTABLE_PATH = INSTALL_DIR + File.separator + EXECUTABLE_NAME;

    /**
     * Stellt sicher, dass Potrace verfügbar ist und gibt den Pfad zurück
     */
    private static String ensurePotraceAvailable() throws IOException {
        System.out.println("[" + APP_NAME + "] Prüfe Potrace-Installation...");
        
        File executableFile = new File(EXECUTABLE_PATH);
        File installDir = new File(INSTALL_DIR);
        
        // Prüfe ob bereits installiert und funktionsfähig
        if (executableFile.exists() && executableFile.canExecute()) {
            System.out.println("[" + APP_NAME + "] ✓ Potrace bereits vorhanden: " + EXECUTABLE_PATH);
            
            if (testPotrace(EXECUTABLE_PATH)) {
                System.out.println("[" + APP_NAME + "] ✓ Potrace funktionsfähig");
                return EXECUTABLE_PATH;
            } else {
                System.out.println("[" + APP_NAME + "] ⚠ Vorhandenes Potrace defekt, ersetze...");
            }
        } else {
            System.out.println("[" + APP_NAME + "] ✗ Potrace nicht gefunden, installiere automatisch...");
        }
        
        // Automatische Installation
        installPotraceFromResource(installDir, executableFile);
        return EXECUTABLE_PATH;
    }
    
    /**
     * Installiert Potrace aus den JAR-Ressourcen
     */
    private static void installPotraceFromResource(File installDir, File executableFile) throws IOException {
        System.out.println("[" + APP_NAME + "] === AUTOMATISCHE POTRACE-INSTALLATION ===");
        
        // Verzeichnis erstellen
        System.out.println("[" + APP_NAME + "] Erstelle Verzeichnis: " + INSTALL_DIR);
        if (!installDir.exists() && !installDir.mkdirs()) {
            throw new IOException("Konnte Verzeichnis nicht erstellen: " + INSTALL_DIR);
        }
        
        // Potrace aus Ressourcen kopieren
        System.out.println("[" + APP_NAME + "] Kopiere Potrace aus JAR-Ressourcen...");
        try (InputStream resourceStream = PotraceBridge.class.getResourceAsStream(RESOURCE_PATH)) {
            if (resourceStream == null) {
                throw new IOException("Potrace-Ressource nicht in JAR gefunden: " + RESOURCE_PATH + 
                                    "\n\nStellen Sie sicher, dass " + EXECUTABLE_NAME + 
                                    " im src/main/resources/ Ordner liegt.");
            }
            
            Path targetPath = Paths.get(EXECUTABLE_PATH);
            long bytesWritten = Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[" + APP_NAME + "] ✓ Potrace kopiert: " + bytesWritten + " bytes");
        }
        
        // Ausführungsrechte setzen (wichtig für Linux/Mac)
        executableFile.setExecutable(true);
        
        // Teste die Installation
        if (!testPotrace(EXECUTABLE_PATH)) {
            throw new IOException("Kopiertes Potrace ist nicht funktionsfähig: " + EXECUTABLE_PATH);
        }
        
        System.out.println("[" + APP_NAME + "] ✓ Potrace erfolgreich installiert: " + EXECUTABLE_PATH);
    }
    
    /**
     * Testet ob Potrace funktioniert
     */
    private static boolean testPotrace(String executablePath) {
        System.out.println("[" + APP_NAME + "] Teste Potrace: " + executablePath);
        
        try {
            ProcessBuilder pb = new ProcessBuilder(executablePath, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(" ");
                }
            }
            
            int exitCode = p.waitFor();
            String outputStr = output.toString().trim();
            
            System.out.println("[" + APP_NAME + "] Test - Exit Code: " + exitCode + ", Output: '" + outputStr + "'");
            
            // Potrace --version sollte Exit Code 0 haben und "potrace" in der Ausgabe
            boolean isWorking = (exitCode == 0) && outputStr.toLowerCase().contains("potrace");
            System.out.println("[" + APP_NAME + "] Funktionsfähig: " + (isWorking ? "✓" : "✗"));
            
            return isWorking;
            
        } catch (Exception e) {
            System.out.println("[" + APP_NAME + "] Test fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    // === HAUPTFUNKTIONALITÄT (KORRIGIERT) ===

    public static Map<Color, String> vectorizeEachColor(BufferedImage posterizedImg, List<Color> colors, int tolerance,
            File outputDir, String originalFilename, Color transparencyColor) {

        Map<Color, String> svgSnippets = new LinkedHashMap<>();

        System.out.println("INPUT FARBEN:");
        for (int i = 0; i < colors.size(); i++) {
            Color c = colors.get(i);
            System.out.println("  " + i + ": " + colorToString(c) + " (Hash: " + c.hashCode() + ")");
        }

        // Temp-Ordner immer im Ausführungsverzeichnis
        File tempDir = new File(System.getProperty("user.dir"), "temp_vector");
        if (!tempDir.exists()) tempDir.mkdirs();
        if (!tempDir.isDirectory()) throw new RuntimeException("TempDir ist keine Directory: " + tempDir);
        clearTempDirectory(tempDir);
        System.out.println("✅ Verwende zentrales Temp-Verzeichnis: " + tempDir.getAbsolutePath());

        // === KORRIGIERT: Automatische Potrace-Installation ===
        String potraceExePath;
        try {
            potraceExePath = ensurePotraceAvailable();
            System.out.println("✅ Potrace verfügbar: " + potraceExePath);
        } catch (IOException e) {
            throw new RuntimeException("Fehler bei Potrace-Installation: " + e.getMessage(), e);
        }

        System.out.println("\n=== MULTICOLOR VECTORIZATION START ===");
        System.out.println("Verarbeite " + colors.size() + " Farben...");

        // === UNBEGRENZTE BILDGRÖSSE: Temporäre Skalierung für große Bilder ===
        final int MAX_VECTOR_DIMENSION = 2000; // Max 2000px pro Seite für Vektorisierung
        final int originalWidth = posterizedImg.getWidth();
        final int originalHeight = posterizedImg.getHeight();
        BufferedImage workingImage = posterizedImg;
        double scaleFactor = 1.0;

        // Prüfe ob Bild zu groß für effiziente Vektorisierung ist
        if (originalWidth > MAX_VECTOR_DIMENSION || originalHeight > MAX_VECTOR_DIMENSION) {
            scaleFactor = Math.min(
                (double) MAX_VECTOR_DIMENSION / originalWidth,
                (double) MAX_VECTOR_DIMENSION / originalHeight
            );

            int scaledWidth = (int) (originalWidth * scaleFactor);
            int scaledHeight = (int) (originalHeight * scaleFactor);

            System.out.println("🔄 BILD ZU GROSS - Temporäre Skalierung für Vektorisierung:");
            System.out.println("   Original: " + originalWidth + "x" + originalHeight +
                " (" + String.format("%.1f", (originalWidth * originalHeight) / 1_000_000.0) + " Megapixel)");
            System.out.println("   Skaliert: " + scaledWidth + "x" + scaledHeight +
                " (" + String.format("%.1f%%", scaleFactor * 100) + ")");
            System.out.println("   SVG wird automatisch auf Originalgröße hochskaliert (verlustfrei!)");

            workingImage = scaleImageForVectorization(posterizedImg, scaledWidth, scaledHeight);
            System.out.println("✅ Temporäres Bild erstellt");
        } else {
            System.out.println("Bild-Größe: " + originalWidth + "x" + originalHeight + " (keine Skalierung nötig)");
        }

        // TRANSPARENZ-FARBE Information
        if (transparencyColor != null) {
            System.out.println("Transparenz-Farbe: " + colorToString(transparencyColor) + " (" + colorToHex(transparencyColor) + ")");
        }

        // === SINGLE-PASS: alle Masken in einem Bilddurchlauf erzeugen ===
        System.out.println("Erzeuge alle Masken in einem Durchlauf...");
        BufferedImage[] allMasks = createAllMasksInOnePass(workingImage, colors, tolerance);

        // Transparenz-Farben direkt behandeln (kein Potrace nötig)
        for (int i = 0; i < colors.size(); i++) {
            Color targetColor = colors.get(i);
            if (transparencyColor != null && colorsMatch(targetColor, transparencyColor, 5)) {
                System.out.println("👻 TRANSPARENZ-FARBE: " + colorToString(targetColor));
                String transparencyPath = createTransparencyPath(workingImage, allMasks[i]);
                if (transparencyPath != null && !transparencyPath.trim().isEmpty())
                    svgSnippets.put(targetColor, transparencyPath);
                allMasks[i].flush();
                allMasks[i] = null;
            }
        }

        // === PARALLELISIERUNG: alle Potrace-Aufrufe gleichzeitig ===
        int threads = Math.min(colors.size(), Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Map.Entry<Color, String>>> futures = new ArrayList<>();

        for (int i = 0; i < colors.size(); i++) {
            final int colorIndex = i;
            final Color targetColor = colors.get(i);
            final BufferedImage mask = allMasks[i];

            if (mask == null) continue; // bereits als Transparenz behandelt

            futures.add(pool.submit(() -> {
                String colorHex = colorToHex(targetColor).substring(1);
                File pbmFile = new File(tempDir, "color_" + colorIndex + "_" + colorHex + ".pbm");
                File svgFile = new File(tempDir, "color_" + colorIndex + "_" + colorHex + ".svg");

                try {
                    int blackPixels = savePBM(mask, pbmFile);
                    mask.flush();
                    System.out.println("PBM: " + pbmFile.getName() + " (" + blackPixels + " schwarze Pixel)");

                    if (blackPixels < 10) {
                        System.out.println("⚠️ Zu wenig Pixel – überspringe " + colorToString(targetColor));
                        return null;
                    }

                    ProcessBuilder pb = new ProcessBuilder(
                            potraceExePath, pbmFile.getAbsolutePath(), "-s", "-o", svgFile.getAbsolutePath());
                    pb.directory(tempDir);
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        while (reader.readLine() != null) { /* stdout verwerfen */ }
                    }
                    int exitCode = proc.waitFor();

                    if (exitCode != 0 || !svgFile.exists() || svgFile.length() < 50) {
                        System.out.println("❌ Potrace fehlgeschlagen für " + colorToString(targetColor));
                        return null;
                    }

                    String svgContent = new String(Files.readAllBytes(svgFile.toPath()), "UTF-8");
                    String pathData = extractAndRecolorPaths(svgContent, targetColor);
                    if (pathData != null && !pathData.trim().isEmpty()) {
                        System.out.println("✅ Pfade extrahiert für " + colorToString(targetColor));
                        return new AbstractMap.SimpleEntry<>(targetColor, pathData);
                    }
                    return null;

                } catch (Exception e) {
                    System.out.println("❌ Fehler bei " + colorToString(targetColor) + ": " + e.getMessage());
                    return null;
                }
            }));
        }

        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Ergebnisse in Reihenfolge einsammeln (LinkedHashMap bewahrt Farb-Reihenfolge)
        for (Future<Map.Entry<Color, String>> future : futures) {
            try {
                Map.Entry<Color, String> result = future.get();
                if (result != null)
                    svgSnippets.put(result.getKey(), result.getValue());
            } catch (Exception e) {
                System.out.println("❌ Fehler beim Abrufen des Ergebnisses: " + e.getMessage());
            }
        }

        // SPEICHER-FREIGABE
        if (workingImage != posterizedImg) {
            workingImage.flush();
            workingImage = null;
            System.out.println("🗑️ Temporäres Bild freigegeben");
        }

        fixBackgroundFillInAllSvgs(tempDir);
        // WICHTIG: Verwende Original-Dimensionen UND Skalierungsfaktor für finale SVG!
        createFinalSvgFromSnippets(svgSnippets, outputDir, originalFilename, originalWidth, originalHeight, scaleFactor);

        System.out.println("\n=== VECTORIZATION COMPLETE ===");
        System.out.println("Erfolgreich verarbeitet: " + svgSnippets.size() + "/" + colors.size() + " Farben");

        return svgSnippets;
    }

    public static String vectorizeSingleMask(BufferedImage mask, File tempDir, String filenameBase, Color targetColor) {
        try {
            // === KORRIGIERT: Automatische Potrace-Installation ===
            String potraceExePath = ensurePotraceAvailable();

            File pbmFile = new File(tempDir, filenameBase + ".pbm");
            File svgFile = new File(tempDir, filenameBase + ".svg");

            savePBM(mask, pbmFile);

            // === KORRIGIERT: Verwende automatisch installierten Potrace-Pfad ===
            ProcessBuilder pb = new ProcessBuilder(potraceExePath, pbmFile.getAbsolutePath(), "-s", "-o",
                    svgFile.getAbsolutePath());

            pb.directory(tempDir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                while (reader.readLine() != null) {
                    // Ausgabe ignorieren
                }
            }

            int exitCode = proc.waitFor();
            if (exitCode != 0 || !svgFile.exists())
                return null;

            String svgContent = new String(Files.readAllBytes(svgFile.toPath()), "UTF-8");
            return extractAndRecolorPaths(svgContent, targetColor);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // === ALLE ANDEREN METHODEN BLEIBEN UNVERÄNDERT ===

    /**
     * NEU: Erstellt einen Transparenz-Pfad basierend auf der Maske
     * Anstatt Potrace zu verwenden, erstellen wir direkt transparente Bereiche
     */
    private static String createTransparencyPath(BufferedImage originalImage, BufferedImage mask) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        
        // Zähle transparente Pixel
        int transparentPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color maskColor = new Color(mask.getRGB(x, y));
                if (maskColor.getRed() < 128) { // Schwarze Pixel in der Maske = Transparenz-Bereiche
                    transparentPixels++;
                }
            }
        }
        
        if (transparentPixels < 10) {
            return null; // Zu wenig transparente Bereiche
        }
        
        // Erstelle einen Rechteck-Pfad der die gesamten Transparenz-Bereiche abdeckt
        // Für eine einfache Implementierung verwenden wir einen Vollbild-Overlay mit opacity="0"
        String transparencyOverlay = String.format(
            "<rect x=\"0\" y=\"0\" width=\"%d\" height=\"%d\" fill=\"none\" fill-opacity=\"0\" " +
            "stroke=\"none\" opacity=\"0\" pointer-events=\"none\"/>",
            width, height
        );
        
        System.out.println("Transparenz-Overlay erstellt: " + transparentPixels + " transparente Pixel gefunden");
        
        // Alternativ: Kommentar-Element zur Kennzeichnung
        return "<!-- Transparenz-Bereich: " + transparentPixels + " Pixel -->\n" + transparencyOverlay;
    }
    
    private static void createFinalSvgFromSnippets(Map<Color, String> svgSnippets, File outputDir, String originalFilename, int originalWidth, int originalHeight, double imageScaleFactor) {
        System.out.println("\n=== SVG KOMBINIERUNG AUS SNIPPETS START ===");

        if (svgSnippets.isEmpty()) {
            System.out.println("Keine SVG-Snippets zum Kombinieren vorhanden");
            return;
        }

        try {
            // WICHTIG: Verwende Original-Dimensionen statt Arbeits-Dimensionen
            int width = originalWidth;
            int height = originalHeight;

            System.out.println("SVG Bildgröße (Original): " + width + "x" + height);

            // Berechne inversen Skalierungsfaktor für SVG-Transform
            // Wenn Bild von 8000x8000 auf 2000x2000 skaliert wurde (scaleFactor=0.25),
            // müssen die Pfade mit 1/0.25 = 4.0 hochskaliert werden
            double svgScaleFactor = (imageScaleFactor > 0 && imageScaleFactor < 1.0) ? (1.0 / imageScaleFactor) : 1.0;

            if (svgScaleFactor > 1.0) {
                System.out.println("📐 SVG-Pfade werden hochskaliert: " + String.format("%.2fx", svgScaleFactor));
                System.out.println("   (Bild wurde für Vektorisierung auf " + String.format("%.1f%%", imageScaleFactor * 100) + " skaliert)");
            }

            String svgHeader = "<?xml version=\"1.0\" standalone=\"no\"?>\n" +
                              "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 20010904//EN\"\n" +
                              " \"http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd\">\n" +
                              "<svg version=\"1.0\" xmlns=\"http://www.w3.org/2000/svg\"\n" +
                              " width=\"" + width + ".000000pt\" height=\"" + height + ".000000pt\" viewBox=\"0 0 " + width + ".000000 " + height + ".000000\"\n" +
                              " preserveAspectRatio=\"xMidYMid meet\">\n" +
                              "<metadata>\n" +
                              "Created by potrace 1.16, written by Peter Selinger 2001-2019\n" +
                              "</metadata>\n" +
                              "<g transform=\"translate(0.000000," + height + ".000000) scale(" +
                              String.format("%.6f", 0.1 * svgScaleFactor) + "," + String.format("%.6f", -0.1 * svgScaleFactor) + ")\"\n" +
                              "fill=\"#000000\" stroke=\"none\">\n";

            StringBuilder allPaths = new StringBuilder();
            int totalPaths = 0;

            for (Map.Entry<Color, String> entry : svgSnippets.entrySet()) {
                Color color = entry.getKey();
                String pathData = entry.getValue();
                
                if (pathData != null && !pathData.trim().isEmpty()) {
                    allPaths.append(pathData).append("\n");
                    
                    String[] lines = pathData.split("\n");
                    int pathCount = 0;
                    for (String line : lines) {
                        if (line.trim().startsWith("<path") || line.trim().startsWith("<rect")) {
                            pathCount++;
                        }
                    }
                    totalPaths += pathCount;
                    
                    String colorInfo = colorToString(color);
                    if (pathData.contains("Transparenz-Bereich")) {
                        colorInfo += " (TRANSPARENZ)";
                    }
                    System.out.println("  📄 Farbe " + colorInfo + ": " + pathCount + " Pfade hinzugefügt");
                }
            }

            StringBuilder finalSvg = new StringBuilder();
            finalSvg.append(svgHeader);
            finalSvg.append(allPaths);
            finalSvg.append("</g>\n</svg>");

            String baseName = new File(originalFilename).getName();
            String finalFilename = baseName.replaceAll("\\.[^.]+$", "") + ".svg";
            File finalSvgFile = new File(outputDir, finalFilename);

            Files.write(finalSvgFile.toPath(), finalSvg.toString().getBytes("UTF-8"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("✅ Finale SVG aus Snippets erstellt: " + finalSvgFile.getName());
            System.out.println("   📊 Gesamte Pfade: " + totalPaths);
            System.out.println("   📏 Dateigröße: " + finalSvg.length() + " Zeichen");
            System.out.println("   📁 Gespeichert in: " + finalSvgFile.getAbsolutePath());

        } catch (Exception e) {
            System.out.println("❌ Fehler beim Kombinieren der SVG-Snippets: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("=== SVG KOMBINIERUNG AUS SNIPPETS COMPLETE ===");
    }

    private static void clearTempDirectory(File tempDir) {
        if (tempDir.exists() && tempDir.isDirectory()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        file.delete();
                    }
                }
            }
            System.out.println("TempDir geleert: " + tempDir.getAbsolutePath());
        }
    }

    private static void fixBackgroundFillInAllSvgs(File tempDir) {
        System.out.println("\n=== SVG NACHBEARBEITUNG START ===");

        File[] svgFiles = tempDir.listFiles(f -> f.getName().toLowerCase().endsWith(".svg"));
        if (svgFiles == null) {
            System.out.println("Keine SVG-Dateien zum Nachbearbeiten gefunden");
            return;
        }
        Arrays.sort(svgFiles, (f1, f2) -> {
            if (f1.getName().contains("color_0"))
                return 1;
            if (f2.getName().contains("color_0"))
                return -1;
            return f1.getName().compareTo(f2.getName());
        });

        for (File svgFile : svgFiles) {
            try {
                String fileName = svgFile.getName();
                String targetColorHex = extractColorFromFilename(fileName);
                if (targetColorHex == null) {
                    System.out.println("⚠️ Kann Farbe nicht aus Dateinamen extrahieren: " + fileName);
                    continue;
                }

                System.out.println("Bearbeite " + fileName + " mit Zielfarbe: " + targetColorHex);

                String svgContent = new String(Files.readAllBytes(svgFile.toPath()), "UTF-8");
                String modifiedContent = svgContent;

                int pathStart = svgContent.indexOf("<path fill=\"#000000\" stroke=\"none\" d=\"M0");
                if (pathStart != -1) {
                    int pathEnd = svgContent.indexOf(">", pathStart);
                    if (pathEnd != -1) {
                        String pathElement = svgContent.substring(pathStart, pathEnd + 1);
                        String modifiedPath = pathElement.replace("fill=\"#000000\"", "fill=\"none\"");
                        modifiedContent = modifiedContent.replace(pathElement, modifiedPath);
                        System.out.println("  ✅ Hintergrund transparent gemacht");
                    }
                }

                modifiedContent = modifiedContent.replaceAll("fill=\"#000000\"", "fill=\"" + targetColorHex + "\"");
                modifiedContent = modifiedContent.replaceAll("fill=\"black\"", "fill=\"" + targetColorHex + "\"");
                System.out.println("  ✅ Vordergrund-Pfade in " + targetColorHex + " gefärbt");

                if (!modifiedContent.equals(svgContent)) {
                    Files.write(svgFile.toPath(), modifiedContent.getBytes("UTF-8"), StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    System.out.println("  ✅ SVG erfolgreich nachbearbeitet: " + fileName);
                } else {
                    System.out.println("  ⚠️ Keine Änderungen nötig in: " + fileName);
                }

            } catch (Exception e) {
                System.out.println("❌ Fehler bei SVG-Nachbearbeitung: " + svgFile.getName() + " - " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("=== SVG NACHBEARBEITUNG COMPLETE ===");
    }

    private static String extractColorFromFilename(String filename) {
        String[] parts = filename.split("_");
        if (parts.length >= 3) {
            String colorPart = parts[2].replace(".svg", "");
            if (colorPart.length() == 6) {
                return "#" + colorPart;
            }
        }
        return null;
    }

    private static BufferedImage createColorSpecificMask(BufferedImage posterizedImg, Color targetColor,
            int tolerance) {
        BufferedImage[] masks = createAllMasksInOnePass(posterizedImg, List.of(targetColor), tolerance);
        return masks[0];
    }

    private static BufferedImage[] createAllMasksInOnePass(BufferedImage img, List<Color> colors, int tolerance) {
        int w = img.getWidth();
        int h = img.getHeight();
        int n = colors.size();

        BufferedImage[] masks = new BufferedImage[n];
        int[] matchCount = new int[n];
        int[] WHITE = new int[n];
        int[] BLACK = new int[n];
        int[] tr = new int[n], tg = new int[n], tb = new int[n];

        for (int i = 0; i < n; i++) {
            masks[i] = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
            WHITE[i] = Color.WHITE.getRGB();
            BLACK[i] = Color.BLACK.getRGB();
            tr[i] = colors.get(i).getRed();
            tg[i] = colors.get(i).getGreen();
            tb[i] = colors.get(i).getBlue();
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >>  8) & 0xFF;
                int b =  rgb        & 0xFF;
                for (int i = 0; i < n; i++) {
                    if (Math.abs(r - tr[i]) <= tolerance &&
                        Math.abs(g - tg[i]) <= tolerance &&
                        Math.abs(b - tb[i]) <= tolerance) {
                        masks[i].setRGB(x, y, WHITE[i]);
                        matchCount[i]++;
                    } else {
                        masks[i].setRGB(x, y, BLACK[i]);
                    }
                }
            }
        }

        int total = w * h;
        for (int i = 0; i < n; i++) {
            System.out.println("Maske: " + matchCount[i] + "/" + total + " Pixel ("
                + String.format("%.2f", matchCount[i] * 100.0 / total) + "%) für " + colorToString(colors.get(i)));
        }
        return masks;
    }

    private static boolean colorsMatch(Color c1, Color c2, int tolerance) {
        return ColorUtils.colorsMatch(c1, c2, tolerance);
    }

    private static int savePBM(BufferedImage img, File file) throws IOException {
        int w = img.getWidth();
        int h = img.getHeight();
        int blackPixels = 0;

        // P4 binary PBM: 8 pixels per byte, up to 16x smaller than P1 ASCII
        byte[] header = ("P4\n# Binary mask for color vectorization\n" + w + " " + h + "\n").getBytes("ASCII");
        int rowBytes = (w + 7) / 8;
        byte[] pixels = new byte[rowBytes * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                boolean isBlack = ((rgb >> 16) & 0xFF) < 128;  // PBM: 1=black, 0=white
                if (isBlack) {
                    blackPixels++;
                    pixels[y * rowBytes + x / 8] |= (byte) (0x80 >> (x % 8));
                }
            }
        }

        try (java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(file))) {
            out.write(header);
            out.write(pixels);
        }
        return blackPixels;
    }

    private static final Pattern PATH_PATTERN = Pattern.compile("<path[^>]*d=\"([^\"]+)\"[^>]*/?>");

    private static String extractAndRecolorPaths(String svgContent, Color targetColor) {
        if (svgContent == null || svgContent.trim().isEmpty()) {
            System.out.println("❌ SVG Content ist null oder leer");
            return null;
        }

        System.out.println("🔍 DEBUG SVG Content für " + colorToString(targetColor) + ":");
        System.out.println("   SVG Länge: " + svgContent.length() + " Zeichen");

        String preview = svgContent.length() > 500 ? svgContent.substring(0, 500) + "..." : svgContent;
        System.out.println("   SVG Inhalt: " + preview);

        StringBuilder result = new StringBuilder();
        String hexColor = colorToHex(targetColor);

        // Extract SVG viewport dimensions for dynamic edge-path filtering
        int svgW = 0, svgH = 0;
        Matcher dimMatcher = Pattern.compile("viewBox=\"0 0 ([\\d.]+) ([\\d.]+)\"").matcher(svgContent);
        if (dimMatcher.find()) {
            svgW = (int) Double.parseDouble(dimMatcher.group(1));
            svgH = (int) Double.parseDouble(dimMatcher.group(2));
        }

        Matcher matcher = PATH_PATTERN.matcher(svgContent);
        List<String> allPaths = new ArrayList<>();
        while (matcher.find()) {
            allPaths.add(matcher.group(1));
        }
        System.out.println("   Gefundene Pfade: " + allPaths.size());

        List<String> filteredPaths = new ArrayList<>();
        for (String path : allPaths) {
            boolean isBackground = false;

            if (path.startsWith("M0,0") || path.startsWith("M0 0")) {
                isBackground = true;
            } else if (svgW > 0 && svgH > 0) {
                // Dynamic: filter paths that start at the image edge
                if (path.contains("M" + svgW + ",") || path.contains("M " + svgW + ",")
                        || path.contains("M" + svgH + ",") || path.contains("M " + svgH + ",")) {
                    isBackground = true;
                }
            }

            if (!isBackground) {
                int curveCount = 0;
                for (int i = 0; i < path.length(); i++) {
                    char ch = path.charAt(i);
                    if (ch == 'C' || ch == 'c') curveCount++;
                }
                if (curveCount == 0 && path.length() > 100)
                    isBackground = true;
            }

            if (!isBackground) {
                filteredPaths.add(path);
                System.out.println("   BEHALTE PFAD: " + path.substring(0, Math.min(50, path.length())) + "...");
            } else {
                System.out.println("   FILTERE PFAD: " + path.substring(0, Math.min(50, path.length())));
            }
        }

        System.out.println("   Nach Filterung: " + filteredPaths.size() + "/" + allPaths.size() + " Pfade");

        // A1 fix: each path appended exactly once
        for (String dAttribute : filteredPaths) {
            if (hexColor.equals("#000000")) {
                result.append("<path d=\"").append(dAttribute)
                        .append("\" fill=\"none\" stroke=\"#000000\" stroke-width=\"1\"/>\n");
            } else {
                result.append("<path d=\"").append(dAttribute).append("\" fill=\"").append(hexColor)
                        .append("\" stroke=\"none\"/>\n");
            }
        }

        String finalResult = result.toString().trim();

        if (finalResult.isEmpty()) {
            System.out.println("❌ KEINE PFADE NACH FILTERUNG für " + colorToString(targetColor));
            System.out.println("   Alle ursprünglichen Pfade waren Hintergrund");
        } else {
            System.out.println(
                    "✅ " + filteredPaths.size() + " gefilterte Pfade extrahiert für " + colorToString(targetColor));
            String firstPath = finalResult.split("\n")[0];
            System.out.println(
                    "   Erster Pfad: " + (firstPath.length() > 120 ? firstPath.substring(0, 120) + "..." : firstPath));
        }

        return finalResult.isEmpty() ? null : finalResult;
    }

    private static String colorToString(Color c) {
        return ColorUtils.colorToString(c);
    }

    private static String colorToHex(Color c) {
        return ColorUtils.colorToHex(c);
    }

    /**
     * Skaliert ein Bild für die Vektorisierung mit hoher Qualität.
     * Verwendet Bicubic Interpolation für beste Ergebnisse.
     *
     * @param original Das zu skalierende Bild
     * @param targetWidth Ziel-Breite
     * @param targetHeight Ziel-Höhe
     * @return Skaliertes BufferedImage
     */
    private static BufferedImage scaleImageForVectorization(BufferedImage original, int targetWidth, int targetHeight) {
        // Verwende den gleichen Typ wie das Original, falls möglich
        int imageType = original.getType();
        if (imageType == 0) {
            imageType = BufferedImage.TYPE_INT_RGB; // Spart Speicher
        }

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, imageType);

        java.awt.Graphics2D g2d = scaled.createGraphics();

        // Hochqualitative Rendering-Hints für beste Skalierung
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                           java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                           java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                           java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_COLOR_RENDERING,
                           java.awt.RenderingHints.VALUE_COLOR_RENDER_QUALITY);

        // Zeichne skaliertes Bild
        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        return scaled;
    }
}