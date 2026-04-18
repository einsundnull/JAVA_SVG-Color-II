package main;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

public class ConverterImageProcessorPosterizeTwoColorAndAlpha extends JFrame {
    private JTextField folderPathField;
    private JButton browseButton;
    private JButton processButton;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JSpinner posterizeLevelsSpinner;

    public ConverterImageProcessorPosterizeTwoColorAndAlpha() {
        setTitle("Bild Posterizer & Transparenz-Tool");
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

        // Kombiniere Einstellungen und oberen Bereich
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(topPanel, BorderLayout.NORTH);
        controlPanel.add(settingsPanel, BorderLayout.CENTER);
        controlPanel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);

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
                    return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || 
                           lowerName.endsWith(".png") || lowerName.endsWith(".bmp") ||
                           lowerName.endsWith(".gif");
                });
                
                if (files == null || files.length == 0) {
                    publish("Keine Bilder im ausgewählten Ordner gefunden.");
                    return results;
                }
                
                publish("Gefundene Bilder: " + files.length);
                
                int processedCount = 0;
                for (File file : files) {
                    try {
                        publish("Verarbeite: " + file.getName());
                        
                        // Bild laden
                        BufferedImage originalImage = ImageIO.read(file);
                        if (originalImage == null) {
                            publish("  Warnung: Konnte Bild nicht laden: " + file.getName());
                            continue;
                        }
                        
                        // Bild mit Alpha-Kanal erstellen
                        BufferedImage processedImage = new BufferedImage(
                            originalImage.getWidth(), 
                            originalImage.getHeight(), 
                            BufferedImage.TYPE_INT_ARGB
                        );
                        
                        // Posterisierung und Transparenz anwenden
                        for (int y = 0; y < originalImage.getHeight(); y++) {
                            for (int x = 0; x < originalImage.getWidth(); x++) {
                                int rgb = originalImage.getRGB(x, y);
                                
                                int alpha = 255;
                                int red = (rgb >> 16) & 0xFF;
                                int green = (rgb >> 8) & 0xFF;
                                int blue = rgb & 0xFF;
                                
                                // Posterisieren (Farben quantisieren)
                                red = posterize(red, posterizeLevels);
                                green = posterize(green, posterizeLevels);
                                blue = posterize(blue, posterizeLevels);
                                
                                // Weißes oder fast weißes in transparent umwandeln
                                if (red > 245 && green > 245 && blue > 245) {
                                    alpha = 0;
                                }
                                
                                // Neuen Pixelwert setzen
                                int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                                processedImage.setRGB(x, y, argb);
                            }
                        }
                        
                        // Neuen Dateinamen erstellen
                        String fileName = file.getName();
                        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                        File outputFile = new File(folder, baseName + "_TRS.png");
                        
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
                    }
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
    
    // Hilfsmethode zur Posterisierung (Reduzierung der Farbtiefe)
    private int posterize(int colorValue, int levels) {
        // Quantisierung auf die angegebene Anzahl von Stufen
        return Math.round(Math.round(colorValue * (levels - 1) / 255.0f) * 255.0f / (levels - 1));
    }

    public static void main(String[] args) {
        try {
            // Look and Feel des Systems verwenden
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ConverterImageProcessorPosterizeTwoColorAndAlpha().setVisible(true);
            }
        });
    }
}
