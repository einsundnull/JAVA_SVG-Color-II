package main;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

public class ConverterHelperPotraceRunner {
    
    // Konfiguration für automatische Potrace-Installation
    private static final String APP_NAME = "PotraceConverter";
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
        try (InputStream resourceStream = ConverterHelperPotraceRunner.class.getResourceAsStream(RESOURCE_PATH)) {
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
    
    // === DEINE URSPRÜNGLICHEN METHODEN - VERBESSERT ===
    
    public static void convertToSvg(File bmpInput, File svgOutput) throws IOException {
        if (!bmpInput.getName().toLowerCase().endsWith(".bmp")) {
            throw new IllegalArgumentException("Nur .bmp-Dateien erlaubt: " + bmpInput.getName());
        }
        
        // Automatische Potrace-Installation falls nötig
        String potracePath = ensurePotraceAvailable();
        
        System.out.println("[POTRACE] Konvertiere: " + bmpInput.getName() + " -> " + svgOutput.getName());
        
        ProcessBuilder pb = new ProcessBuilder(potracePath, bmpInput.getAbsolutePath(),
                "-s", "-o", svgOutput.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[potrace] " + line); // Debug-Ausgabe
            }
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Lesen von Potrace-Ausgabe", e);
        }
        
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Potrace fehlgeschlagen (exit " + exitCode + ")");
            }
            System.out.println("[POTRACE] ✓ Konvertierung erfolgreich: " + svgOutput.getAbsolutePath());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Potrace-Prozess unterbrochen", e);
        }
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
            
            // BMP zu SVG konvertieren
            BufferedImage result = convertToSvg(image, svgFile);
            
            // Temporäre BMP-Datei löschen
            if (bmpFile.exists()) {
                bmpFile.delete();
            }
            
            return result;
        } catch (IOException e) {
            System.err.println("Fehler bei der Konvertierung von: " + baseName);
            e.printStackTrace();
        }
        return image;
    }
    
    public static BufferedImage convertToSvg(BufferedImage bmpInput, File svgOutput) throws IOException {
        // Temporäre BMP-Datei für Potrace erstellen
        File tempBmpFile = File.createTempFile("potrace_input_", ".bmp");
        
        try {
            // BufferedImage als BMP-Datei speichern
            ImageIO.write(bmpInput, "bmp", tempBmpFile);
            
            // Automatische Potrace-Installation falls nötig
            String potracePath = ensurePotraceAvailable();
            
            System.out.println("[POTRACE] Konvertiere BufferedImage -> " + svgOutput.getName());
            
            ProcessBuilder pb = new ProcessBuilder(potracePath,
                    tempBmpFile.getAbsolutePath(), "-s", "-o", svgOutput.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[potrace] " + line); // Debug-Ausgabe
                }
            } catch (IOException e) {
                throw new RuntimeException("Fehler beim Lesen von Potrace-Ausgabe", e);
            }
            
            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("Potrace fehlgeschlagen (exit " + exitCode + ")");
                }
                System.out.println("[POTRACE] ✓ BufferedImage-Konvertierung erfolgreich: " + svgOutput.getAbsolutePath());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Potrace-Prozess unterbrochen", e);
            }
            
        } finally {
            // Temporäre Datei aufräumen
            if (tempBmpFile.exists()) {
                tempBmpFile.delete();
            }
        }
        
        return bmpInput;
    }
}