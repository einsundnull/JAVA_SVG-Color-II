package main;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

public class ConverterImageProcessorPosterizeTwoColorAndAlpha_Fill extends JFrame {
    private JTextField folderPathField;
    private JButton browseButton;
    private JButton processButton;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JSpinner posterizeLevelsSpinner;

    public ConverterImageProcessorPosterizeTwoColorAndAlpha_Fill() {
        setTitle("Bild Posterizer & Selektive Transparenz-Tool");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();
    }

    private void initComponents() {
        folderPathField = new JTextField(20);
        browseButton = new JButton("Durchsuchen...");
        processButton = new JButton("Bilder verarbeiten");
        logArea = new JTextArea();
        logArea.setEditable(false);
        progressBar = new JProgressBar(0, 100);
        statusLabel = new JLabel("Bereit");
        
        // Spinner für Posterisierungsstufen (2-8)
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(4, 2, 8, 1);
        posterizeLevelsSpinner = new JSpinner(spinnerModel);

        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browseFolder();
            }
        });

        processButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processImages();
            }
        });
    }

    private void layoutComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Oberer Bereich für Pfad und Durchsuchen-Button
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.add(new JLabel("Bilderordner:"), BorderLayout.WEST);
        topPanel.add(folderPathField, BorderLayout.CENTER);
        topPanel.add(browseButton, BorderLayout.EAST);

        // Mittlerer Bereich mit den Einstellungen
        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        settingsPanel.add(new JLabel("Posterisierungs-Stufen:"));
        settingsPanel.add(posterizeLevelsSpinner);
        settingsPanel.add(Box.createHorizontalStrut(20));
        settingsPanel.add(processButton);

        // Info-Label hinzufügen
        JLabel infoLabel = new JLabel("<html><i>Nur Hintergrundbereiche, die nicht von schwarzen Linien umschlossen sind, werden transparent.</i></html>");
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.PLAIN, 11f));

        // Kombiniere Einstellungen und oberen Bereich
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(topPanel, BorderLayout.NORTH);
        controlPanel.add(settingsPanel, BorderLayout.CENTER);
        controlPanel.add(infoLabel, BorderLayout.SOUTH);

        // Log-Bereich
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(500, 200));

        // Status-Bereich
        JPanel statusPanel = new JPanel(new BorderLayout(5, 0));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);

        // Füge alles zum Hauptpanel hinzu
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);

        // Füge das Hauptpanel zum Frame hinzu
        add(mainPanel);
    }

    private void browseFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Bildordner auswählen");

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = fileChooser.getSelectedFile();
            folderPathField.setText(selectedFolder.getAbsolutePath());
        }
    }

    private void processImages() {
        final String folderPath = folderPathField.getText();
        if (folderPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Bitte einen gültigen Ordnerpfad angeben.", 
                "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            JOptionPane.showMessageDialog(this, 
                "Der angegebene Pfad ist kein gültiger Ordner.", 
                "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // UI-Elemente vor der Verarbeitung aktualisieren
        processButton.setEnabled(false);
        browseButton.setEnabled(false);
        folderPathField.setEnabled(false);
        posterizeLevelsSpinner.setEnabled(false);
        logArea.setText("");
        progressBar.setValue(0);
        statusLabel.setText("Verarbeite Bilder...");

        final int posterizeLevels = (Integer) posterizeLevelsSpinner.getValue();
        
        // Verarbeitung in einem SwingWorker ausführen
        SwingWorker<List<String>, String> worker = new SwingWorker<List<String>, String>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                List<String> results = new ArrayList<>();
                
                File[] files = folder.listFiles((dir, name) -> {
                    String lowerName = name.toLowerCase();
                    // Prüfe auf gültige Bilddateien
                    boolean isImageFile = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || 
                                         lowerName.endsWith(".png") || lowerName.endsWith(".bmp") ||
                                         lowerName.endsWith(".gif");
                    
                    // Extrahiere den Dateinamen ohne Erweiterung
                    String baseName = name.substring(0, name.lastIndexOf('.'));
                    boolean endsWithFLL = baseName.toLowerCase().endsWith("_fll");
                    
                    // Verarbeite alle Bilddateien AUSSER die, die auf "_FLL" enden
                    return isImageFile && !endsWithFLL;
                });
                
                if (files == null || files.length == 0) {
                    publish("Keine zu verarbeitenden Bilder im ausgewählten Ordner gefunden.");
                    return results;
                }
                
                                    publish("Gefundene Bilder (ohne _FLL-Dateien): " + files.length);
                
                int processedCount = 0;
                int skippedCount = 0;
                
                for (File file : files) {
                    try {
                        publish("Verarbeite: " + file.getName());
                        
                        // Bild laden
                        BufferedImage originalImage = ImageIO.read(file);
                        if (originalImage == null) {
                            publish("  Warnung: Konnte Bild nicht laden: " + file.getName());
                            skippedCount++;
                            continue;
                        }
                        
                        // Schritt 1: Bild posterisieren
                        BufferedImage posterizedImage = posterizeImage(originalImage, posterizeLevels);
                        publish("  Bild posterisiert");
                        
                        // Schritt 2: Mask für äußere Hintergrundbereiche erstellen
                        boolean[][] transparentMask = createTransparencyMask(posterizedImage);
                        publish("  Transparenz-Maske erstellt");
                        
                        // Schritt 3: Finales Bild mit selektiver Transparenz erstellen
                        BufferedImage processedImage = applySelectiveTransparency(posterizedImage, transparentMask);
                        publish("  Selektive Transparenz angewendet");
                        
                        // Neuen Dateinamen erstellen
                        String fileName = file.getName();
                        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                        File outputFile = new File(folder, baseName + "_FLL.png");
                        
                        // Als PNG mit Transparenz speichern
                        ImageIO.write(processedImage, "PNG", outputFile);
                        publish("  Gespeichert als: " + outputFile.getName());
                        
                        results.add(outputFile.getName());
                        processedCount++;
                        
                        // Progressbar aktualisieren
                        int progress = (int)((processedCount / (double)files.length) * 100);
                        setProgress(progress);
                    } catch (IOException e) {
                        publish("  Fehler: " + e.getMessage());
                        skippedCount++;
                    }
                }
                
                if (skippedCount > 0) {
                    publish("Übersprungene Dateien: " + skippedCount);
                }
                
                return results;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    logArea.append(message + "\n");
                    // Scrolle automatisch nach unten
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                }
            }
            
            @Override
            protected void done() {
                try {
                    List<String> results = get();
                    progressBar.setValue(100);
                    statusLabel.setText("Fertig: " + results.size() + " Bilder verarbeitet");
                } catch (InterruptedException | ExecutionException e) {
                    statusLabel.setText("Fehler: " + e.getMessage());
                } finally {
                    // UI-Elemente nach der Verarbeitung zurücksetzen
                    processButton.setEnabled(true);
                    browseButton.setEnabled(true);
                    folderPathField.setEnabled(true);
                    posterizeLevelsSpinner.setEnabled(true);
                }
            }
        };
        
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        
        worker.execute();
    }
    
    // Hilfsmethode zur Posterisierung des gesamten Bildes
    private BufferedImage posterizeImage(BufferedImage originalImage, int levels) {
        BufferedImage posterizedImage = new BufferedImage(
            originalImage.getWidth(), 
            originalImage.getHeight(), 
            BufferedImage.TYPE_INT_RGB
        );
        
        for (int y = 0; y < originalImage.getHeight(); y++) {
            for (int x = 0; x < originalImage.getWidth(); x++) {
                int rgb = originalImage.getRGB(x, y);
                
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                
                // Posterisieren (Farben quantisieren)
                red = posterize(red, levels);
                green = posterize(green, levels);
                blue = posterize(blue, levels);
                
                // Neuen Pixelwert setzen
                int newRgb = (red << 16) | (green << 8) | blue;
                posterizedImage.setRGB(x, y, newRgb);
            }
        }
        
        return posterizedImage;
    }
    
    // Erstellt eine Maske, die angibt, welche Pixel transparent werden sollen
    private boolean[][] createTransparencyMask(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] mask = new boolean[height][width];
        boolean[][] visited = new boolean[height][width];
        
        // Flood fill von allen Randpixeln aus, die hell (weiß/grau) sind
        for (int x = 0; x < width; x++) {
            // Oberer Rand
            if (!visited[0][x] && isLightPixel(image.getRGB(x, 0))) {
                floodFill(image, mask, visited, x, 0);
            }
            // Unterer Rand
            if (!visited[height-1][x] && isLightPixel(image.getRGB(x, height-1))) {
                floodFill(image, mask, visited, x, height-1);
            }
        }
        
        for (int y = 0; y < height; y++) {
            // Linker Rand
            if (!visited[y][0] && isLightPixel(image.getRGB(0, y))) {
                floodFill(image, mask, visited, 0, y);
            }
            // Rechter Rand
            if (!visited[y][width-1] && isLightPixel(image.getRGB(width-1, y))) {
                floodFill(image, mask, visited, width-1, y);
            }
        }
        
        return mask;
    }
    
    // Prüft, ob ein Pixel hell (nicht schwarz) ist
    private boolean isLightPixel(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        
        // Ein Pixel gilt als "hell", wenn es nicht sehr dunkel ist
        // Threshold kann angepasst werden
        int brightness = (red + green + blue) / 3;
        return brightness > 128;
    }
    
    // Flood Fill Algorithmus mit Stack (iterativ, um Stack Overflow zu vermeiden)
    private void floodFill(BufferedImage image, boolean[][] mask, boolean[][] visited, int startX, int startY) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        Stack<Point> stack = new Stack<>();
        stack.push(new Point(startX, startY));
        
        while (!stack.isEmpty()) {
            Point current = stack.pop();
            int x = current.x;
            int y = current.y;
            
            // Prüfe Grenzen und ob bereits besucht
            if (x < 0 || x >= width || y < 0 || y >= height || visited[y][x]) {
                continue;
            }
            
            // Prüfe, ob Pixel hell ist
            if (!isLightPixel(image.getRGB(x, y))) {
                continue;
            }
            
            // Markiere als besucht und für Transparenz
            visited[y][x] = true;
            mask[y][x] = true;
            
            // Füge Nachbarn zur Queue hinzu
            stack.push(new Point(x + 1, y));
            stack.push(new Point(x - 1, y));
            stack.push(new Point(x, y + 1));
            stack.push(new Point(x, y - 1));
        }
    }
    
    // Wendet die selektive Transparenz auf das Bild an
    private BufferedImage applySelectiveTransparency(BufferedImage posterizedImage, boolean[][] transparentMask) {
        int width = posterizedImage.getWidth();
        int height = posterizedImage.getHeight();
        
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = posterizedImage.getRGB(x, y);
                
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                
                // Alpha basierend auf der Maske setzen
                int alpha = transparentMask[y][x] ? 0 : 255;
                
                // Neuen ARGB-Wert setzen
                int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                result.setRGB(x, y, argb);
            }
        }
        
        return result;
    }
    
    // Hilfsmethode zur Posterisierung (Reduzierung der Farbtiefe)
    private int posterize(int colorValue, int levels) {
        // Quantisierung auf die angegebene Anzahl von Stufen
        return Math.round(Math.round(colorValue * (levels - 1) / 255.0f) * 255.0f / (levels - 1));
    }

    public static void main(String[] args) {
        try {
            // Look and Feel des Systems verwenden
            UIManager.setLookAndFeel(UIManager.getLookAndFeel());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ConverterImageProcessorPosterizeTwoColorAndAlpha_Fill().setVisible(true);
            }
        });
    }
}