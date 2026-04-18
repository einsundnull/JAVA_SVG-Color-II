package main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class ImageEditorWindow extends JFrame {
    public static final Color TRANSPARENCY_COLOR = ColorUtils.TRANSPARENCY_COLOR;
    
    private BufferedImage originalImage;
    private BufferedImage editedImage;
    private JLabel imageLabel;
    private JScrollPane imageScrollPane;
    private double zoomFactor = 1.0;
    
    private Color selectedColor = Color.RED;
    private List<Color> colorPalette;
    private JPanel palettePanel;
    private MainApp parentApp;
    private boolean hasChanges = false;
    
    public ImageEditorWindow(MainApp parent, BufferedImage image) {
        this.parentApp = parent;
        this.originalImage = image;
        this.editedImage = deepCopy(image);
        
        initializePalette();
        setupWindow();
        setupImagePanel();
        setupControlPanel();
        
        setVisible(true);
        
        System.out.println("🎨 Image Editor geöffnet - Größe: " + image.getWidth() + "x" + image.getHeight());
        System.out.println("💡 Klicken Sie auf Bereiche zum Floodfill");
        System.out.println("🔴 Transparenz-Farbe: " + String.format("#%06X", TRANSPARENCY_COLOR.getRGB() & 0xFFFFFF));
    }
    
    private void initializePalette() {
        colorPalette = new ArrayList<>();
        
        // Standard Farben
        colorPalette.add(Color.BLACK);
        colorPalette.add(Color.WHITE);
        colorPalette.add(Color.RED);
        colorPalette.add(Color.GREEN);
        colorPalette.add(Color.BLUE);
        colorPalette.add(Color.YELLOW);
        colorPalette.add(Color.CYAN);
        colorPalette.add(Color.MAGENTA);
        
        // Grautöne
        colorPalette.add(new Color(64, 64, 64));
        colorPalette.add(new Color(128, 128, 128));
        colorPalette.add(new Color(192, 192, 192));
        
        // Transparenz-Farbe (speziell markiert)
        colorPalette.add(TRANSPARENCY_COLOR);
        
        selectedColor = Color.RED;
    }
    
    private void setupWindow() {
        setTitle("Image Editor - Floodfill & Color Tools");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(parentApp);
        setLayout(new BorderLayout());
        
        // Window Closing Handler
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleClose();
            }
        });
    }
    
    private void setupImagePanel() {
        // Bildanzeige
        imageLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (editedImage != null) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    int w = (int) (editedImage.getWidth() * zoomFactor);
                    int h = (int) (editedImage.getHeight() * zoomFactor);
                    
                    int x = (getWidth() - w) / 2;
                    int y = (getHeight() - h) / 2;
                    
                    g2d.drawImage(editedImage, x, y, w, h, null);
                    g2d.dispose();
                }
            }
        };
        
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        imageLabel.setBackground(new Color(240, 240, 240));
        imageLabel.setOpaque(true);
        imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        
        // Mouse Handler für Floodfill
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleImageClick(e);
            }
        });
        
        imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        
        imageScrollPane = new JScrollPane(imageLabel);
        imageScrollPane.setPreferredSize(new Dimension(700, 500));
        add(imageScrollPane, BorderLayout.CENTER);
        
        updateImageDisplay();
        
        // Zoom mit Mausrad
        imageLabel.addMouseWheelListener(e -> {
            if (editedImage != null) {
                zoomFactor *= (e.getWheelRotation() < 0) ? 1.1 : 0.9;
                zoomFactor = Math.max(0.1, Math.min(zoomFactor, 5.0));
                updateImageDisplay();
                System.out.println("🔍 Zoom: " + String.format("%.1f", zoomFactor * 100) + "%");
            }
        });
    }
    
    private void setupControlPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(280, 600));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Tools"));
        
        // Color Palette
        setupColorPalette(rightPanel);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new GridLayout(6, 1, 5, 5));
        
        JButton colorChooserBtn = new JButton("Custom Color");
        colorChooserBtn.addActionListener(e -> openColorChooser());
        buttonPanel.add(colorChooserBtn);
        
        JButton addToPaletteBtn = new JButton("Add to Palette");
        addToPaletteBtn.addActionListener(e -> addColorToPalette());
        buttonPanel.add(addToPaletteBtn);
        
        JButton undoBtn = new JButton("Reset Image");
        undoBtn.addActionListener(e -> resetToOriginal());
        buttonPanel.add(undoBtn);
        
        JButton previewBtn = new JButton("Preview Changes");
        previewBtn.addActionListener(e -> showPreview());
        buttonPanel.add(previewBtn);
        
        JButton saveBtn = new JButton("💾 Save & Close");
        saveBtn.setBackground(new Color(0, 150, 0));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD));
        saveBtn.addActionListener(e -> saveAndClose());
        buttonPanel.add(saveBtn);
        
        JButton cancelBtn = new JButton("❌ Cancel");
        cancelBtn.setBackground(new Color(150, 0, 0));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.addActionListener(e -> handleClose());
        buttonPanel.add(cancelBtn);
        
        rightPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(rightPanel, BorderLayout.EAST);
        
        // Info Panel
        JPanel infoPanel = new JPanel(new FlowLayout());
        JLabel infoLabel = new JLabel("<html><b>Anleitung:</b> Farbe wählen → Auf Bild klicken = Floodfill<br>" +
            "<b>Transparenz:</b> Magenta (#FF00FF) = Transparent in SVG</html>");
        infoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        infoPanel.add(infoLabel);
        add(infoPanel, BorderLayout.NORTH);
    }
    
    private void setupColorPalette(JPanel parent) {
        palettePanel = new JPanel(new GridLayout(0, 4, 2, 2));
        palettePanel.setBorder(BorderFactory.createTitledBorder("Color Palette"));
        
        updatePaletteDisplay();
        
        JScrollPane paletteScroll = new JScrollPane(palettePanel);
        paletteScroll.setPreferredSize(new Dimension(260, 300));
        parent.add(paletteScroll, BorderLayout.CENTER);
    }
    
    private void updatePaletteDisplay() {
        palettePanel.removeAll();
        
        for (Color color : colorPalette) {
            JButton colorBtn = createColorButton(color);
            palettePanel.add(colorBtn);
        }
        
        palettePanel.revalidate();
        palettePanel.repaint();
    }
    
    private JButton createColorButton(Color color) {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(50, 50));
        btn.setOpaque(true);
        btn.setBorderPainted(true);
        
        // Debug: Prüfe Farbe
        if (color.equals(TRANSPARENCY_COLOR)) {
            System.out.println("🔴 Erstelle Transparenz-Button - Farbe: " + 
                String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
        }
        
        // Setze Hintergrund-Farbe IMMER
        btn.setBackground(color);
        
        // Spezielle Kennzeichnung für Transparenz-Farbe
        if (color.equals(TRANSPARENCY_COLOR)) {
            btn.setText("🔍");
            btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            btn.setForeground(Color.WHITE);
            btn.setToolTipText("Transparenz-Farbe #FF00FF (wird in SVG übersprungen)");
            
            // Force Magenta Background
            btn.setBackground(new Color(255, 0, 255));
            
            // Debug: Nach dem Setzen nochmal prüfen
            Color actualBg = btn.getBackground();
            System.out.println("   Button Background nach Setzen: " + 
                String.format("#%02X%02X%02X", actualBg.getRed(), actualBg.getGreen(), actualBg.getBlue()));
        } else {
            btn.setToolTipText("RGB(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ")");
        }
        
        // Selection Border
        if (color.equals(selectedColor)) {
            btn.setBorder(BorderFactory.createLineBorder(Color.BLACK, 3));
        } else {
            btn.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        }
        
        btn.addActionListener(e -> {
            selectedColor = color;
            updatePaletteDisplay();
            System.out.println("🎨 Farbe gewählt: " + (color.equals(TRANSPARENCY_COLOR) ? "TRANSPARENZ" : 
                "RGB(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ")"));
        });
        
        return btn;
    }
    
    private void handleImageClick(MouseEvent e) {
        if (editedImage == null) return;
        
        // Bildschirm-Koordinaten zu Bild-Koordinaten konvertieren
        int w = (int) (editedImage.getWidth() * zoomFactor);
        int h = (int) (editedImage.getHeight() * zoomFactor);
        int imgX = (imageLabel.getWidth() - w) / 2;
        int imgY = (imageLabel.getHeight() - h) / 2;
        
        int clickX = e.getX() - imgX;
        int clickY = e.getY() - imgY;
        
        if (clickX >= 0 && clickY >= 0 && clickX < w && clickY < h) {
            // Zu Original-Bild-Koordinaten
            int originalX = (int) (clickX / zoomFactor);
            int originalY = (int) (clickY / zoomFactor);
            
            if (originalX >= 0 && originalY >= 0 && 
                originalX < editedImage.getWidth() && originalY < editedImage.getHeight()) {
                
                performFloodFill(originalX, originalY, selectedColor);
            }
        }
    }
    
    private void performFloodFill(int startX, int startY, Color fillColor) {
        boolean isTransparency = fillColor.equals(TRANSPARENCY_COLOR);
        int filled = ImageUtils.floodFill(editedImage, startX, startY, fillColor, isTransparency, 5);
        if (filled > 0) {
            hasChanges = true;
            updateImageDisplay();
        }
    }
    
    private void updateImageDisplay() {
        imageLabel.repaint();
    }
    
    private void openColorChooser() {
        Color newColor = JColorChooser.showDialog(this, "Farbe wählen", selectedColor);
        if (newColor != null) {
            selectedColor = newColor;
            System.out.println("🎨 Custom Color gewählt: RGB(" + newColor.getRed() + "," + 
                newColor.getGreen() + "," + newColor.getBlue() + ")");
        }
    }
    
    private void addColorToPalette() {
        if (!colorPalette.contains(selectedColor)) {
            colorPalette.add(selectedColor);
            updatePaletteDisplay();
            System.out.println("➕ Farbe zur Palette hinzugefügt: RGB(" + selectedColor.getRed() + "," + 
                selectedColor.getGreen() + "," + selectedColor.getBlue() + ")");
        } else {
            System.out.println("⚠️  Farbe bereits in Palette vorhanden");
        }
    }
    
    private void resetToOriginal() {
        int response = JOptionPane.showConfirmDialog(this,
            "Alle Änderungen zurücksetzen?",
            "Reset bestätigen",
            JOptionPane.YES_NO_OPTION);
        
        if (response == JOptionPane.YES_OPTION) {
            editedImage = deepCopy(originalImage);
            hasChanges = false;
            updateImageDisplay();
            System.out.println("🔄 Bild auf Original zurückgesetzt");
        }
    }
    
    private void showPreview() {
        JFrame previewFrame = new JFrame("Preview - Edited Image");
        previewFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        JLabel previewLabel = new JLabel(new ImageIcon(editedImage));
        JScrollPane previewScroll = new JScrollPane(previewLabel);
        previewFrame.add(previewScroll);
        
        previewFrame.setSize(600, 500);
        previewFrame.setLocationRelativeTo(this);
        previewFrame.setVisible(true);
    }
    
    private void saveAndClose() {
        // Bearbeitetes Bild an MainApp zurückgeben
        if (parentApp != null) {
            parentApp.setEditedImage(editedImage);
            System.out.println("💾 Bearbeitetes Bild an MainApp übertragen");
        }
        dispose();
    }
    
    private void handleClose() {
        if (hasChanges) {
            int response = JOptionPane.showConfirmDialog(this,
                "Änderungen sind vorhanden. Trotzdem schließen?",
                "Ungespeicherte Änderungen",
                JOptionPane.YES_NO_OPTION);
            
            if (response == JOptionPane.YES_OPTION) {
                dispose();
            }
        } else {
            dispose();
        }
    }
    
    private BufferedImage deepCopy(BufferedImage original) {
        BufferedImage copy = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();
        return copy;
    }
}