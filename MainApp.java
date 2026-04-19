package main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

// PosterAppMultiColorSVG
public class MainApp extends JFrame implements EditToolsWindow.EditToolsListener {
	private BufferedImage loadedImage;
	private BufferedImage originalImageBackup; // Echtes Original sichern
	private boolean isImageEdited = false;
	private JLabel imageLabel;
	private JScrollPane imageScrollPane;
	private java.util.List<ColorEntry> selectedColors = new ArrayList<>();
	private JPanel colorListPanel;
	private File originalImageFile;
	private double zoomFactor = 1.0;
	private JSlider toleranceSlider;
	private JLabel toleranceLabel;

	// Quick Transparency Features
	private boolean quickTransparentMode = false;
	private Color currentTransparencyColor = ColorUtils.TRANSPARENCY_COLOR;
	private JButton quickTransparentToggle;
	private JButton transparencyColorButton;

	// Edit Tools Integration
	private EditToolsWindow editToolsWindow;
	private EditToolsWindow.EditMode currentEditMode = EditToolsWindow.EditMode.COLOR_PICKER;
	private Color currentEditColor = Color.RED;
	private JSpinner posterizeLevelSpinner;

	public MainApp() {
		setTitle("Multicolor Posterizer & Vectorizer");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());
		setSize(1200, 700);

		setupImagePanel();
		setupControlPanel();
		updateCursorForMode();

		SwingUtilities.invokeLater(() -> imageLabel.requestFocusInWindow());

		setVisible(true);

		// Debug-Info
		System.out.println("=== POSTERIZER GESTARTET ===");
		System.out.println("Ziehen Sie ein Bild ins Fenster und klicken Sie Farben an!");
	}

	private void setupImagePanel() {
		// Bildanzeige
		imageLabel = new JLabel("Bild hier hineinziehen oder einfügen", SwingConstants.CENTER);
		imageLabel.setHorizontalAlignment(JLabel.CENTER);
		imageLabel.setVerticalAlignment(JLabel.CENTER);
		imageLabel.setBackground(Color.LIGHT_GRAY);
		imageLabel.setOpaque(true);
		imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
		imageLabel.setFocusable(true);
		imageLabel.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) { imageLabel.requestFocusInWindow(); }
		});

		// CTRL+V global — KeyEventDispatcher greift vor allen Komponenten (auch JSpinner/JTextField)
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
			if (e.getID() == KeyEvent.KEY_PRESSED
					&& e.isControlDown()
					&& e.getKeyCode() == KeyEvent.VK_V
					&& KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == MainApp.this) {
				pasteImageFromClipboard();
				return true;
			}
			return false;
		});

		imageScrollPane = new JScrollPane(imageLabel);
		imageScrollPane.setPreferredSize(new Dimension(800, 600));
		add(imageScrollPane, BorderLayout.CENTER);

		// Drag & Drop
//		imageLabel.setTransferHandler(new ImageDropHandler());
		new DropTarget(imageLabel, new DropTargetAdapter() {
			public void drop(DropTargetDropEvent dtde) {
				dtde.acceptDrop(DnDConstants.ACTION_COPY);
				try {
					Transferable t = dtde.getTransferable();
					java.util.List<File> droppedFiles = (java.util.List<File>) t
							.getTransferData(DataFlavor.javaFileListFlavor);
					if (!droppedFiles.isEmpty()) {
						loadImage(droppedFiles.get(0));
					}
				} catch (Exception ex) {
					System.out.println("❌ Fehler beim Laden der Datei: " + ex.getMessage());
					ex.printStackTrace();
				}
			}
		});

		// Farbauswahl per Mausklick mit Robot (pixelgenau) + Edit Modi
		imageLabel.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if (loadedImage == null)
					return;

				// Quick Transparent Modus (hat Priorität)
				if (quickTransparentMode) {
					handleQuickTransparentClick(e);
					return;
				}

				// Edit Tools Modi
				switch (currentEditMode) {
				case COLOR_PICKER:
					handleColorPickerClick(e);
					break;
				case FLOODFILL:
					handleFloodfillClick(e);
					break;
				case TRANSPARENCY:
					handleTransparencyClick(e);
					break;
				}
			}
		});

		// Zoom mit Mausrad
		imageLabel.addMouseWheelListener(e -> {
			if (loadedImage != null) {
				zoomFactor *= (e.getWheelRotation() < 0) ? 1.1 : 0.9;
				zoomFactor = Math.max(0.1, Math.min(zoomFactor, 10.0));
				updateImageLabel();
				System.out.println("🔍 Zoom: " + String.format("%.1f", zoomFactor * 100) + "%");
			}
		});
	}

	private void setupControlPanel() {
		// Rechte Seitenleiste
		JPanel rightPanel = new JPanel(new BorderLayout());
		rightPanel.setPreferredSize(new Dimension(300, 600));

		// Toleranz-Einstellung
		JPanel tolerancePanel = new JPanel(new FlowLayout());
		tolerancePanel.add(new JLabel("Farbtoleranz:"));
		toleranceSlider = new JSlider(0, 100, 30);
		toleranceSlider.setMajorTickSpacing(20);
		toleranceSlider.setMinorTickSpacing(10);
		toleranceSlider.setPaintTicks(true);
		toleranceSlider.setPaintLabels(true);
		toleranceLabel = new JLabel("30");

		toleranceSlider.addChangeListener(e -> {
			toleranceLabel.setText(String.valueOf(toleranceSlider.getValue()));
		});

		tolerancePanel.add(toleranceSlider);
		tolerancePanel.add(toleranceLabel);
		rightPanel.add(tolerancePanel, BorderLayout.NORTH);

		// Farbliste
		colorListPanel = new JPanel();
		colorListPanel.setLayout(new BoxLayout(colorListPanel, BoxLayout.Y_AXIS));
		JScrollPane scrollPane = new JScrollPane(colorListPanel);
		scrollPane.setBorder(BorderFactory.createTitledBorder("Ausgewählte Farben"));
		rightPanel.add(scrollPane, BorderLayout.CENTER);

		// Buttons
		JPanel buttonPanel = new JPanel(new GridLayout(13, 1, 5, 5));

		// MainApp.java setupControlPanel()
		JButton fullResetBtn = new JButton("VOLLSTÄNDIGER RESET");
		fullResetBtn.addActionListener(e -> performFullReset());
		fullResetBtn.setBackground(new Color(200, 0, 0));
		fullResetBtn.setForeground(Color.WHITE);
		fullResetBtn.setFont(fullResetBtn.getFont().deriveFont(Font.BOLD));
		fullResetBtn.setToolTipText("Setzt das gesamte Programm zurück");
		buttonPanel.add(fullResetBtn);

		JButton clearBtn = new JButton("Farben löschen");
		clearBtn.addActionListener(e -> clearColors());
		buttonPanel.add(clearBtn);

		JButton posterizeBtn = new JButton("Posterize & Vorschau");
		posterizeBtn.addActionListener(e -> showPosterizePreview());
		buttonPanel.add(posterizeBtn);

		JButton vectorizeBtn = new JButton("🎨 Multicolor SVG erstellen");
		vectorizeBtn.addActionListener(e -> runPosterizeWorkflow());
		vectorizeBtn.setBackground(new Color(0, 150, 0));
		vectorizeBtn.setForeground(Color.WHITE);
		vectorizeBtn.setFont(vectorizeBtn.getFont().deriveFont(Font.BOLD));
		buttonPanel.add(vectorizeBtn);

		JButton allColorsBtn = new JButton("🌈 SVG All Colors");
		allColorsBtn.addActionListener(e -> runAllColorsWorkflow());
		allColorsBtn.setBackground(new Color(150, 0, 150));
		allColorsBtn.setForeground(Color.WHITE);
		allColorsBtn.setFont(allColorsBtn.getFont().deriveFont(Font.BOLD));
		buttonPanel.add(allColorsBtn);

//		JButton imageEditorBtn = new JButton("🎨 Image Editor");
//		imageEditorBtn.addActionListener(e -> openImageEditor());
//		imageEditorBtn.setBackground(new Color(0, 100, 200));
//		imageEditorBtn.setForeground(Color.WHITE);
//		imageEditorBtn.setFont(imageEditorBtn.getFont().deriveFont(Font.BOLD));
//		buttonPanel.add(imageEditorBtn);

		JButton resetToOriginalBtn = new JButton("🔄 Reset zu Original");
		resetToOriginalBtn.addActionListener(e -> resetToOriginal());
		resetToOriginalBtn.setBackground(new Color(200, 100, 0));
		resetToOriginalBtn.setForeground(Color.WHITE);
		buttonPanel.add(resetToOriginalBtn);

		// Quick Transparency Panel
		JPanel quickTransPanel = new JPanel(new BorderLayout(2, 2));

		quickTransparentToggle = new JButton("⚡ Quick Transparent");
		quickTransparentToggle.addActionListener(e -> toggleQuickTransparent());
		quickTransparentToggle.setBackground(new Color(100, 100, 100));
		quickTransparentToggle.setForeground(Color.WHITE);
		quickTransPanel.add(quickTransparentToggle, BorderLayout.CENTER);

		transparencyColorButton = new JButton();
		transparencyColorButton.setPreferredSize(new Dimension(30, 30));
		transparencyColorButton.addActionListener(e -> chooseTransparencyColor());
		transparencyColorButton.setToolTipText("Transparenz-Farbe ändern");
		updateTransparencyColorButton();
		quickTransPanel.add(transparencyColorButton, BorderLayout.EAST);

		buttonPanel.add(quickTransPanel);

		JButton editToolsBtn = new JButton("🎨 Edit Tools");
		editToolsBtn.addActionListener(e -> openEditTools());
		editToolsBtn.setBackground(new Color(200, 50, 200));
		editToolsBtn.setForeground(Color.WHITE);
		editToolsBtn.setFont(editToolsBtn.getFont().deriveFont(Font.BOLD));
		buttonPanel.add(editToolsBtn);

		JButton simpleConvertBtn = new JButton("Einfache SVG-Konvertierung");
		simpleConvertBtn.addActionListener(e -> runSimpleConversion());
		buttonPanel.add(simpleConvertBtn);

		JButton alphaBtn = new JButton("Bild alphatisieren");
		alphaBtn.addActionListener(e -> alphatizeImage());
		buttonPanel.add(alphaBtn);

		JButton alphaFolderBtn = new JButton("Ordner alphatisieren");
		alphaFolderBtn.addActionListener(e -> alphatizeFolder());
		buttonPanel.add(alphaFolderBtn);

		JButton helpBtn = new JButton("Hilfe");
		helpBtn.addActionListener(e -> showHelp());
		buttonPanel.add(helpBtn);

		rightPanel.add(buttonPanel, BorderLayout.SOUTH);
		add(rightPanel, BorderLayout.EAST);

		JPanel posterizePanel = new JPanel(new BorderLayout(5, 5));
		JButton posterizeAlphaBtn = new JButton("Posterize+Alpha");
		posterizeAlphaBtn.setToolTipText("Reduziert Farben und macht helle Bereiche transparent");
		posterizeAlphaBtn.addActionListener(e -> runPosterizeAndAlpha());
		posterizeAlphaBtn.setBackground(new Color(0, 120, 255));
		posterizeAlphaBtn.setForeground(Color.WHITE);
		posterizeAlphaBtn.setFont(posterizeAlphaBtn.getFont().deriveFont(Font.BOLD));

		posterizePanel.add(posterizeAlphaBtn, BorderLayout.CENTER);

		// Spinner für Stufen (rechts daneben)
		posterizeLevelSpinner = new JSpinner(new SpinnerNumberModel(4, 2, 256, 1));
		posterizePanel.add(posterizeLevelSpinner, BorderLayout.EAST);

		buttonPanel.add(posterizePanel);
	}

	private void performFullReset() {
		// MainApp.java performFullReset()
		int response = JOptionPane.showConfirmDialog(this,
				"ACHTUNG: Vollständiger Reset!\n\n" + "Dies setzt zurück:\n" + "• Geladenes Bild\n"
						+ "• Alle Farbauswahlen\n" + "• Alle Bearbeitungen\n" + "• UI-Status\n\n" + "Fortfahren?",
				"Vollständiger Reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

		if (response == JOptionPane.YES_OPTION) {
			loadedImage = null;
			originalImageBackup = null;
			originalImageFile = null;
			isImageEdited = false;
			zoomFactor = 1.0;

			clearColors();
			currentTransparencyColor = new Color(255, 0, 255);
			updateTransparencyColorButton();

			imageLabel.setIcon(null);
			imageLabel.setText("Bild hier hineinziehen oder einfügen");
			imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

			toleranceSlider.setValue(30);
			toleranceLabel.setText("30");
			if (posterizeLevelSpinner != null) {
				posterizeLevelSpinner.setValue(4);
			}

			currentEditMode = EditToolsWindow.EditMode.COLOR_PICKER;
			currentEditColor = Color.RED;
			quickTransparentMode = false;
			quickTransparentToggle.setText("Quick Transparent");
			quickTransparentToggle.setBackground(new Color(100, 100, 100));

			if (editToolsWindow != null && editToolsWindow.isDisplayable()) {
				editToolsWindow.dispose();
				editToolsWindow = null;
			}

			setTitle("Multicolor Posterizer & Vectorizer");

			JOptionPane.showMessageDialog(this, "Vollständiger Reset abgeschlossen!");
		}
	}

	private void runPosterizeAndAlpha() {
		if (loadedImage == null || originalImageFile == null) {
			JOptionPane.showMessageDialog(this, "Bitte laden Sie zuerst ein Bild.");
			return;
		}

		// SPEICHER-OPTIMIERUNG: Keine Vorkopie nötig, arbeite direkt mit loadedImage
		// Kopie wird erst beim "Übernehmen" erstellt

		JFrame previewFrame = new JFrame("Posterize+Alpha Vorschau");
		previewFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		previewFrame.setLayout(new BorderLayout());

		JLabel previewLabel = new JLabel("", SwingConstants.CENTER);
		JScrollPane scrollPane = new JScrollPane(previewLabel);
		previewFrame.add(scrollPane, BorderLayout.CENTER);

		JSpinner spinner = new JSpinner(new SpinnerNumberModel(4, 2, 256, 1));
		JButton applyBtn = new JButton("✔ Übernehmen");

		JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		controlPanel.add(new JLabel("Stufen:"));
		controlPanel.add(spinner);
		controlPanel.add(applyBtn);
		previewFrame.add(controlPanel, BorderLayout.SOUTH);

		// Container für aktuelles Ergebnis
		final BufferedImage[] currentPosterized = new BufferedImage[1];

		Runnable updatePreview = () -> {
			int levels = (int) spinner.getValue();
			// Arbeite direkt mit loadedImage (keine Vorkopie)
			currentPosterized[0] = ImageUtils.posterizeAndAlpha(loadedImage, levels);
			BufferedImage preview = ImageUtils.createPreview(currentPosterized[0], 600);
			previewLabel.setIcon(new ImageIcon(preview));
			previewLabel.setText(null);
		};

		updatePreview.run();
		spinner.addChangeListener(e -> updatePreview.run());

		// Übernehmen-Klick
		applyBtn.addActionListener(e -> {
			if (currentPosterized[0] != null) {
				// Erst beim Übernehmen Kopie erstellen
				loadedImage = deepCopyImage(currentPosterized[0]);
				isImageEdited = true;
				updateImageLabel();
				clearColors();
				System.out.println("✅ Posterized Bild übernommen in Arbeitsfläche");
				previewFrame.dispose();
			}
		});

		previewFrame.setSize(800, 600);
		previewFrame.setLocationRelativeTo(this);
		previewFrame.setVisible(true);
	}

	private Rectangle getImageBounds() {
		if (loadedImage == null)
			return new Rectangle();

		int imageWidth = (int) (loadedImage.getWidth() * zoomFactor);
		int imageHeight = (int) (loadedImage.getHeight() * zoomFactor);

		int labelWidth = imageLabel.getWidth();
		int labelHeight = imageLabel.getHeight();

		int x = (labelWidth - imageWidth) / 2;
		int y = (labelHeight - imageHeight) / 2;

		return new Rectangle(x, y, imageWidth, imageHeight);
	}

	private void pasteImageFromClipboard() {
		try {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			Transferable content = clipboard.getContents(null);
			if (content == null) {
				System.out.println("⚠️ Clipboard leer");
				return;
			}

			System.out.println("Clipboard Flavors:");
			for (DataFlavor f : content.getTransferDataFlavors())
				System.out.println("  " + f.getMimeType());

			BufferedImage buffered = null;

			// 1. Standard Java image flavor
			if (content.isDataFlavorSupported(DataFlavor.imageFlavor)) {
				java.awt.Image img = (java.awt.Image) content.getTransferData(DataFlavor.imageFlavor);
				if (img != null) {
					// MediaTracker ensures async image is fully loaded
					java.awt.MediaTracker tracker = new java.awt.MediaTracker(this);
					tracker.addImage(img, 0);
					tracker.waitForAll();
					int w = img.getWidth(null);
					int h = img.getHeight(null);
					if (w > 0 && h > 0) {
						buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
						Graphics2D g2d = buffered.createGraphics();
						g2d.drawImage(img, 0, 0, null);
						g2d.dispose();
						System.out.println("✅ imageFlavor gelesen: " + w + "x" + h);
					}
				}
			}

			// 2. Windows DIB / PNG als InputStream (häufigster Windows-Fall)
			if (buffered == null) {
				for (DataFlavor f : content.getTransferDataFlavors()) {
					String mime = f.getMimeType();
					if ((mime.contains("image/png") || mime.contains("image/x-png")
							|| mime.contains("image/bmp") || mime.contains("image/dib"))
							&& f.getRepresentationClass() == java.io.InputStream.class) {
						try (java.io.InputStream is = (java.io.InputStream) content.getTransferData(f)) {
							buffered = ImageIO.read(is);
							if (buffered != null) {
								System.out.println("✅ InputStream-Flavor gelesen (" + mime + "): "
										+ buffered.getWidth() + "x" + buffered.getHeight());
								break;
							}
						} catch (Exception ex) {
							System.out.println("  Flavor fehlgeschlagen: " + mime + " – " + ex.getMessage());
						}
					}
				}
			}

			// 3. Datei-Liste (z.B. aus Explorer kopiert)
			if (buffered == null && content.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				@SuppressWarnings("unchecked")
				List<File> files = (List<File>) content.getTransferData(DataFlavor.javaFileListFlavor);
				if (!files.isEmpty()) { loadImage(files.get(0)); return; }
			}

			if (buffered == null) {
				System.out.println("⚠️ Kein unterstütztes Bildformat im Clipboard");
				return;
			}

			originalImageBackup = buffered;
			loadedImage = deepCopyImage(buffered);
			originalImageFile = null;
			isImageEdited = false;
			zoomFactor = 1.0;
			clearColors();
			updateImageLabel();
			setTitle("Multicolor Posterizer & Vectorizer - [Clipboard]");
			System.out.println("✅ Bild aus Clipboard eingefügt: "
					+ buffered.getWidth() + "x" + buffered.getHeight());

		} catch (Exception e) {
			System.out.println("❌ Fehler beim Einfügen: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void loadImage(File file) {
		try {
			originalImageFile = file;
			BufferedImage fileImage = ImageIO.read(file);

			if (fileImage == null) {
				JOptionPane.showMessageDialog(this, "Fehler: Bild konnte nicht gelesen werden.");
				return;
			}

			// SPEICHER-OPTIMIERUNG: Nur 2 statt 3 Kopien im Speicher
			// fileImage wird direkt zum originalImageBackup (keine zusätzliche Kopie)
			originalImageBackup = fileImage;
			// Nur loadedImage wird kopiert
			loadedImage = deepCopyImage(fileImage);
			fileImage = null; // Explizit freigeben (Referenz wird nicht mehr gebraucht)

			isImageEdited = false;

			updateImageLabel();
			clearColors();

			System.out.println("✅ Bild geladen: " + file.getName());
			System.out.println("   Größe: " + loadedImage.getWidth() + "x" + loadedImage.getHeight());
			System.out.println("   Original gesichert als Backup");

			// Speicher-Info ausgeben
			Runtime runtime = Runtime.getRuntime();
			long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
			long maxMemory = runtime.maxMemory() / 1024 / 1024;
			System.out.println("   Speicher: " + usedMemory + " MB / " + maxMemory + " MB");

		} catch (OutOfMemoryError e) {
			System.out.println("❌ FEHLER: Nicht genug Arbeitsspeicher für dieses Bild!");
			JOptionPane.showMessageDialog(this,
				"❌ Nicht genug Arbeitsspeicher!\n\n" +
				"Das Bild ist zu groß für den verfügbaren Speicher.\n" +
				"Bitte verkleinern Sie das Bild oder erhöhen Sie den Java Heap Space.\n\n" +
				"Aktueller max. Speicher: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB",
				"Speicherfehler",
				JOptionPane.ERROR_MESSAGE);
		} catch (Exception ex) {
			System.out.println("❌ Fehler beim Laden des Bildes: " + ex.getMessage());
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "Fehler beim Laden des Bildes: " + ex.getMessage());
		}
	}

	private void updateImageLabel() {
		if (loadedImage != null) {
			int w = (int) (loadedImage.getWidth() * zoomFactor);
			int h = (int) (loadedImage.getHeight() * zoomFactor);
			BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = scaled.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2d.drawImage(loadedImage, 0, 0, w, h, null);
			g2d.dispose();
			imageLabel.setIcon(new ImageIcon(scaled));
			imageLabel.revalidate();
			imageLabel.repaint();

			String status = isImageEdited ? " [BEARBEITET]" : " [ORIGINAL]";
			String fileName = (originalImageFile != null) ? originalImageFile.getName() : "Unbenannt";
			setTitle("Multicolor Posterizer & Vectorizer - " + fileName + status);
		}
	}

	private void addColorToList(Color color) {
		// Prüfe, ob die Farbe bereits existiert
		for (ColorEntry existing : selectedColors) {
			if (colorDistance(existing.getColor(), color) < 10) {
				System.out.println("⚠️  Ähnliche Farbe bereits vorhanden, überspringe");
				return;
			}
		}

		ColorEntry entry = new ColorEntry(color);
		selectedColors.add(entry);

		JPanel panel = new JPanel(new BorderLayout());
		panel.setMaximumSize(new Dimension(280, 40));
		panel.setBackground(color);
		panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));

		JCheckBox box = new JCheckBox();
		box.setOpaque(false);
		box.setSelected(true);
		panel.add(box, BorderLayout.WEST);
		entry.setCheckbox(box);

		// Farbinfo-Label
		String colorText = "RGB(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ")";
		JLabel colorLabel = new JLabel(colorText);
		colorLabel.setForeground(getContrastColor(color));
		colorLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
		panel.add(colorLabel, BorderLayout.CENTER);

		JButton removeBtn = new JButton("×");
		removeBtn.setMargin(new Insets(0, 5, 0, 5));
		removeBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		removeBtn.addActionListener(e -> {
			selectedColors.remove(entry);
			colorListPanel.remove(panel);
			colorListPanel.revalidate();
			colorListPanel.repaint();
			System.out.println("🗑️  Farbe entfernt: " + colorText);
		});
		panel.add(removeBtn, BorderLayout.EAST);

		colorListPanel.add(panel);
		colorListPanel.revalidate();

		System.out.println("➕ Farbe hinzugefügt: " + colorText + " (Total: " + selectedColors.size() + ")");
	}

	private Color getContrastColor(Color background) {
		double brightness = 0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue();
		return brightness > 128 ? Color.BLACK : Color.WHITE;
	}

	private double colorDistance(Color c1, Color c2) {
		return ColorUtils.colorDistance(c1, c2);
	}

	private void clearColors() {
		selectedColors.clear();
		colorListPanel.removeAll();
		colorListPanel.revalidate();
		colorListPanel.repaint();
		System.out.println("🧹 Alle Farben gelöscht");
	}

	private void showPosterizePreview() {
		if (loadedImage == null || selectedColors.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Bitte laden Sie ein Bild und wählen Sie Farben aus.");
			return;
		}

		List<Color> palette = selectedColors.stream().filter(ColorEntry::isSelected).map(ColorEntry::getColor)
				.collect(Collectors.toList());

		if (palette.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Bitte wählen Sie mindestens eine Farbe aus.");
			return;
		}

		try {
			BufferedImage posterized = ImageUtils.posterize(loadedImage, palette, toleranceSlider.getValue());

			JFrame previewFrame = new JFrame("Posterize Vorschau");
			previewFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			previewFrame.add(new JScrollPane(new JLabel(new ImageIcon(posterized))));
			previewFrame.pack();
			previewFrame.setVisible(true);

		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Fehler bei Posterize-Vorschau: " + ex.getMessage());
		}
	}

// MainApp.java - Korrigierte Workflow-Methoden
// Nur die relevanten Methoden - Rest bleibt unverändert

	private void runPosterizeWorkflow() {
		if (loadedImage == null || selectedColors.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Bitte laden Sie ein Bild und wählen Sie Farben aus.");
			return;
		}

		// KORRIGIERT: Verwende die gleiche Palette-Erstellung wie All Colors
		List<Color> palette = createPaletteWithTransparency();

		if (palette.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Bitte wählen Sie mindestens eine Farbe aus.");
			return;
		}

		int tolerance = toleranceSlider.getValue();

		System.out.println("\n🚀 STARTE MULTICOLOR VECTORIZATION 🚀");
		System.out.println("Bild: " + loadedImage.getWidth() + "x" + loadedImage.getHeight());
		System.out.println("Ausgewählte Farben: " + palette.size());
		System.out.println("Toleranz: " + tolerance);
		System.out.println("Transparenz-Farbe: " + colorToString(currentTransparencyColor));
		System.out.println("Palette:");

		for (int i = 0; i < palette.size(); i++) {
			Color c = palette.get(i);
			String info = colorToString(c);
			if (isTransparencyColor(c)) {
				info += " (TRANSPARENZ-FARBE)";
			}
			System.out.println("  " + (i + 1) + ". " + info);
		}

		try {
			File outputDir = (originalImageFile != null) ? originalImageFile.getParentFile() : new File("output");
			outputDir.mkdirs();

			System.out.println("\n📷 Erstelle posterisiertes Bild...");
			BufferedImage posterized = ImageUtils.posterize(loadedImage, palette, tolerance);
			File pngFile = new File(outputDir, "posterized.png");
			ImageUtils.savePNG(posterized, pngFile);

			int vectorTolerance = Math.min(tolerance, 15);
			System.out.println("🎨 Starte Vektorisierung mit Toleranz " + vectorTolerance + "...");
			System.out.println(
					"🔍 Transparenz-Farbe wird als: " + colorToString(currentTransparencyColor) + " behandelt");

			String imageFileName = (originalImageFile != null) ? originalImageFile.getName() : "vectorized_image";

			// WICHTIG: Verwende die gleiche Methode wie All Colors
			Map<Color, String> svgParts = PotraceBridge.vectorizeEachColor(posterized, palette, vectorTolerance,
					outputDir, imageFileName, currentTransparencyColor);

			if (svgParts.isEmpty()) {
				String errorMsg = "❌ Keine SVG-Pfade erstellt!\n\n" + "Mögliche Lösungen:\n"
						+ "• Prüfen Sie, ob potrace.exe unter tools/potrace/ liegt\n"
						+ "• Wählen Sie andere Farben aus\n" + "• Erhöhen Sie die Toleranz\n"
						+ "• Schauen Sie in den temp_vector Ordner für Debug-Dateien";
				JOptionPane.showMessageDialog(this, errorMsg);
				return;
			}

			// Finale SVG wird bereits in PotraceBridge.createFinalSvgFromSnippets erstellt
			String message = "🎉 MULTICOLOR SVG ERFOLGREICH ERSTELLT! 🎉\n\n" + "📁 Verzeichnis: "
					+ outputDir.getAbsolutePath() + "\n" + "📷 Posterized PNG: " + pngFile.getName() + "\n"
					+ "🎨 Multicolor SVG: Siehe Verzeichnis\n" + "🌈 Farbebenen: " + svgParts.size() + "\n"
					+ "🔍 Debug-Dateien: temp_vector/\n" + "👻 Transparenz-Bereiche: "
					+ (containsTransparency(svgParts) ? "JA" : "NEIN");

			JOptionPane.showMessageDialog(this, message);
			System.out.println("\n=== WORKFLOW ERFOLGREICH ABGESCHLOSSEN ===");

		} catch (Exception ex) {
			System.out.println("❌ SCHWERWIEGENDER FEHLER: " + ex.getMessage());
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "❌ Fehler beim Verarbeiten:\n" + ex.getMessage()
					+ "\n\nPrüfen Sie die Konsolen-Ausgabe für Details.");
		}
	}

	private void runAllColorsWorkflow() {
		if (loadedImage == null) {
			JOptionPane.showMessageDialog(this, "Bitte laden Sie zuerst ein Bild.");
			return;
		}

		try {
			System.out.println("\n🌈 STARTE ALL COLORS WORKFLOW 🌈");

			BufferedImage base = (loadedImage != null) ? loadedImage : ImageIO.read(originalImageFile);
			List<Color> allColors = ImageUtils.findAllUniqueColors(base);

			if (allColors.size() > 500) {
				int response = JOptionPane.showConfirmDialog(this,
						"Das Bild enthält " + allColors.size() + " verschiedene Farben.\n"
								+ "Dies kann sehr lange dauern und große Dateien erzeugen.\n\n"
								+ "Trotzdem fortfahren?",
						"Viele Farben gefunden", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

				if (response != JOptionPane.YES_OPTION) {
					return;
				}
			}

			clearColors();

			System.out.println("📝 Lade " + allColors.size() + " Farben in die Auswahl...");

			// KORRIGIERT: Verwende die gleiche Logik wie Workflow A
			loadColorsIntoSelection(allColors);

			// WICHTIG: Rufe die GLEICHE Workflow-Methode auf wie [A]
			System.out.println("🚀 Starte automatisch Multicolor SVG Workflow...");
			runPosterizeWorkflow(); // Verwendet jetzt die korrigierte Version!

		} catch (Exception ex) {
			System.out.println("❌ Fehler im All Colors Workflow: " + ex.getMessage());
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "❌ Fehler beim All Colors Workflow:\n" + ex.getMessage());
		}
	}

	/**
	 * NEU: Erstellt eine Palette die sowohl ausgewählte Farben als auch die
	 * Transparenz-Farbe (falls nötig) enthält
	 */
	private List<Color> createPaletteWithTransparency() {
		// Hole ausgewählte Farben
		List<Color> selectedPalette = selectedColors.stream().filter(ColorEntry::isSelected).map(ColorEntry::getColor)
				.collect(Collectors.toList());

		System.out.println("🎨 Ausgewählte Farben: " + selectedPalette.size());

		// Prüfe ob Transparenz-Farbe im Bild vorkommt UND ob sie nicht bereits
		// ausgewählt ist
		boolean transparencyInImage = imageContainsColor(loadedImage, currentTransparencyColor, 10);
		boolean transparencyAlreadySelected = selectedPalette.stream()
				.anyMatch(color -> colorsMatch(color, currentTransparencyColor, 5));

		System.out.println("🔍 Transparenz-Farbe " + colorToString(currentTransparencyColor) + ":");
		System.out.println("   Im Bild vorhanden: " + transparencyInImage);
		System.out.println("   Bereits ausgewählt: " + transparencyAlreadySelected);

		// Füge Transparenz-Farbe automatisch hinzu wenn sie im Bild ist aber nicht
		// ausgewählt
		if (transparencyInImage && !transparencyAlreadySelected) {
			selectedPalette.add(currentTransparencyColor);
			System.out.println("➕ Transparenz-Farbe automatisch zur Palette hinzugefügt");

			// Optional: Auch zur UI-Liste hinzufügen für Sichtbarkeit
			addColorToList(currentTransparencyColor);
		}

		return selectedPalette;
	}

	/**
	 * NEU: Lädt Farben in die selectedColors Liste (für All Colors Workflow)
	 */
	private void loadColorsIntoSelection(List<Color> allColors) {
		for (Color color : allColors) {
			ColorEntry entry = new ColorEntry(color);
			selectedColors.add(entry);

			// UI-Panel für die Farbe erstellen (vereinfacht für viele Farben)
			JPanel panel = new JPanel(new BorderLayout());
			panel.setMaximumSize(new Dimension(280, 25));
			panel.setBackground(color);
			panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));

			JCheckBox box = new JCheckBox();
			box.setOpaque(false);
			box.setSelected(true); // Alle Farben standardmäßig ausgewählt
			panel.add(box, BorderLayout.WEST);
			entry.setCheckbox(box);

			String colorText = "RGB(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ")";
			if (isTransparencyColor(color)) {
				colorText += " (TRANSP)";
			}

			JLabel colorLabel = new JLabel(colorText);
			colorLabel.setForeground(getContrastColor(color));
			colorLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
			panel.add(colorLabel, BorderLayout.CENTER);

			colorListPanel.add(panel);
		}

		colorListPanel.revalidate();
		colorListPanel.repaint();
	}

	/**
	 * NEU: Hilfsmethoden für Transparenz-Behandlung
	 */
	private boolean isTransparencyColor(Color color) {
		return colorsMatch(color, currentTransparencyColor, 5);
	}

	private boolean imageContainsColor(BufferedImage image, Color targetColor, int tolerance) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				Color pixelColor = new Color(image.getRGB(x, y));
				if (colorsMatch(pixelColor, targetColor, tolerance)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean colorsMatch(Color c1, Color c2, int tolerance) {
		return ColorUtils.colorsMatch(c1, c2, tolerance);
	}

	private String colorToString(Color c) {
		return ColorUtils.colorToString(c);
	}

	private boolean containsTransparency(Map<Color, String> svgParts) {
		return svgParts.entrySet().stream().anyMatch(entry -> isTransparencyColor(entry.getKey())
				|| (entry.getValue() != null && entry.getValue().contains("Transparenz-Bereich")));
	}

	private void runSimpleConversion() {
		if (loadedImage == null || originalImageFile == null) {
			JOptionPane.showMessageDialog(this, "Bitte laden Sie zuerst ein Bild.");
			return;
		}

		try {
			System.out.println("🔄 Starte einfache SVG-Konvertierung...");
			ConverterImageToSvgClassSuitable.convertImageToSVG(originalImageFile);
			JOptionPane.showMessageDialog(this,
					"✅ SVG-Konvertierung abgeschlossen!\n\nDatei im gleichen Verzeichnis gespeichert.");
			System.out.println("✅ Einfache SVG-Konvertierung erfolgreich");
		} catch (Exception ex) {
			System.out.println("❌ Fehler bei einfacher SVG-Konvertierung: " + ex.getMessage());
			JOptionPane.showMessageDialog(this, "❌ Fehler bei SVG-Konvertierung:\n" + ex.getMessage());
		}
	}

	private void alphatizeImage() {
		if (loadedImage == null || originalImageFile == null) {
			JOptionPane.showMessageDialog(this, "Bitte laden Sie zuerst ein Bild.");
			return;
		}

		try {
			System.out.println("🔍 Starte Alphatisierung für: " + originalImageFile.getName());
			ConverterImageRemoveWhiteToAlpha.removeWhite(originalImageFile);
			JOptionPane.showMessageDialog(this, "✅ Bild alphatisiert!\n\nDatei im gleichen Verzeichnis gespeichert.");
			System.out.println("✅ Alphatisierung erfolgreich");
		} catch (Exception ex) {
			System.out.println("❌ Fehler bei Alphatisierung: " + ex.getMessage());
			JOptionPane.showMessageDialog(this, "❌ Fehler bei Alphatisierung:\n" + ex.getMessage());
		}
	}

	private void alphatizeFolder() {
		if (loadedImage == null || originalImageFile == null) {
			JOptionPane.showMessageDialog(this, "Bitte laden Sie zuerst ein Bild.");
			return;
		}

		try {
			File dir = originalImageFile.getParentFile();
			File[] files = dir.listFiles((d, name) -> {
				String lower = name.toLowerCase();
				return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
						|| lower.endsWith(".bmp");
			});

			if (files != null && files.length > 0) {
				System.out.println("🔍 Starte Ordner-Alphatisierung für " + files.length + " Dateien...");
				for (File f : files) {
					ConverterImageRemoveWhiteToAlpha.removeWhite(f);
				}
				JOptionPane.showMessageDialog(this,
						"✅ Ordner alphatisiert!\n\nVerarbeitet: " + files.length + " Dateien");
				System.out.println("✅ Ordner-Alphatisierung erfolgreich");
			} else {
				JOptionPane.showMessageDialog(this, "Keine Bilddateien im Verzeichnis gefunden.");
			}
		} catch (Exception ex) {
			System.out.println("❌ Fehler bei Ordner-Alphatisierung: " + ex.getMessage());
			JOptionPane.showMessageDialog(this, "❌ Fehler bei Ordner-Alphatisierung:\n" + ex.getMessage());
		}
	}

	private void showHelp() {
		String help = "🎨 MULTICOLOR POSTERIZER & VECTORIZER 🎨\n\n" + "1. Ziehen Sie ein Bild ins Fenster\n"
				+ "2. Klicken Sie auf Farben im Bild, um sie auszuwählen\n"
				+ "3. Passen Sie die Farbtoleranz an (höher = mehr Variationen)\n"
				+ "4. Klicken Sie 'Multicolor SVG erstellen'\n\n" + "💡 TIPPS:\n" + "• Mausrad zum Zoomen\n"
				+ "• 3-8 Farben funktionieren meist am besten\n" + "• Höhere Toleranz für weichere Übergänge\n"
				+ "• Potrace.exe muss unter tools/potrace/ liegen\n\n" + "🔧 WEITERE FUNKTIONEN:\n"
				+ "• Edit Tools: Erweiterte Bildbearbeitung mit Modus-Auswahl\n"
				+ "• Quick Transparent: Direkte Transparenz-Bearbeitung im Hauptfenster\n"
				+ "• Image Editor: Floodfill & Farben ändern mit Transparenz\n"
				+ "• Reset zu Original: Alle Bearbeitungen verwerfen\n"
				+ "• Einfache SVG-Konvertierung: Direkter Potrace-Export\n"
				+ "• Alphatisierung: Weiße Bereiche transparent machen\n"
				+ "• SVG All Colors: Alle Farben automatisch finden und vektorisieren\n\n" + "🎨 EDIT TOOLS:\n"
				+ "• Separates Tool-Fenster für erweiterte Bearbeitung\n"
				+ "• 3 Modi: Farbauswahl, Floodfill, Transparenz\n" + "• Alle Klicks erfolgen im Hauptfenster\n"
				+ "• Farbpalette und Custom Colors\n\n" + "⚡ QUICK TRANSPARENT:\n"
				+ "• Button aktivieren → Cursor ändert sich\n"
				+ "• Auf Bild klicken → Floodfill mit Transparenz-Farbe\n"
				+ "• Transparenz-Farbe per Farbquadrat änderbar\n\n" + "🛡️ SICHERHEIT:\n"
				+ "• Alle Bearbeitungen arbeiten mit Kopien\n" + "• Original bleibt immer verfügbar";

		JOptionPane.showMessageDialog(this, help, "Hilfe", JOptionPane.INFORMATION_MESSAGE);
	}

	private void openImageEditor() {
		if (loadedImage == null) {
			JOptionPane.showMessageDialog(this, "Bitte laden Sie zuerst ein Bild.");
			return;
		}

		try {
			System.out.println("🎨 Öffne Image Editor...");
			System.out.println("   Erstelle Arbeitskopie für Editor (Original bleibt unverändert)");

			// Arbeitskopie für Editor erstellen
			BufferedImage editorCopy = deepCopyImage(loadedImage);
			new ImageEditorWindow(this, editorCopy);

		} catch (Exception ex) {
			System.out.println("❌ Fehler beim Öffnen des Image Editors: " + ex.getMessage());
			JOptionPane.showMessageDialog(this, "❌ Fehler beim Öffnen des Image Editors:\n" + ex.getMessage());
		}
	}

	public Color getCurrentTransparencyColor() { return currentTransparencyColor; }

	public void setEditedImage(BufferedImage editedImage) {
		if (editedImage != null) {
			// Neue Arbeitskopie erstellen
			this.loadedImage = deepCopyImage(editedImage);
			isImageEdited = true;
			updateImageLabel();
			clearColors(); // Farbauswahl zurücksetzen da Bild geändert wurde

			System.out.println("✅ Bearbeitetes Bild empfangen und als Arbeitskopie gesetzt");
			System.out.println("   Neue Größe: " + loadedImage.getWidth() + "x" + loadedImage.getHeight());
			System.out.println("   Status: BEARBEITET (Original bleibt verfügbar)");

			// Optional: Frage ob automatisch Farben neu analysiert werden sollen
			int response = JOptionPane.showConfirmDialog(this,
					"Möchten Sie automatisch alle Farben des bearbeiteten Bildes analysieren?", "Farbanalyse",
					JOptionPane.YES_NO_OPTION);

			if (response == JOptionPane.YES_OPTION) {
				// Kurze Verzögerung damit UI sich aktualisiert
				javax.swing.Timer timer = new javax.swing.Timer(500, event -> {
					runAllColorsWorkflow();
					((javax.swing.Timer) event.getSource()).stop();
				});
				timer.setRepeats(false);
				timer.start();
			}
		}
	}

	private void resetToOriginal() {
		if (originalImageBackup == null) {
			JOptionPane.showMessageDialog(this, "Kein Original verfügbar.");
			return;
		}

		if (isImageEdited) {
			int response = JOptionPane.showConfirmDialog(this,
					"Alle Bearbeitungen verwerfen und zum Original zurückkehren?", "Zum Original zurückkehren",
					JOptionPane.YES_NO_OPTION);

			if (response == JOptionPane.YES_OPTION) {
				loadedImage = deepCopyImage(originalImageBackup);
				isImageEdited = false;
				updateImageLabel();
				clearColors();

				System.out.println("🔄 Bild auf Original zurückgesetzt");
				System.out.println("   Status: ORIGINAL");
			}
		} else {
			JOptionPane.showMessageDialog(this, "Bild ist bereits im Original-Zustand.");
		}
	}

	private BufferedImage deepCopyImage(BufferedImage original) {
		if (original == null)
			return null;

		// SPEICHER-OPTIMIERUNG: Bevorzuge TYPE_INT_RGB wenn kein Alpha-Kanal benötigt wird
		// TYPE_INT_RGB: 3 Bytes pro Pixel (RGB)
		// TYPE_INT_ARGB: 4 Bytes pro Pixel (ARGB) → 33% mehr Speicher!
		int imageType = original.getType();
		if (imageType == 0) {
			// Typ 0 = unbekannt, verwende RGB statt ARGB (spart 25% Speicher)
			imageType = BufferedImage.TYPE_INT_RGB;
		}

		BufferedImage copy = new BufferedImage(original.getWidth(), original.getHeight(), imageType);

		java.awt.Graphics2D g = copy.createGraphics();
		g.drawImage(original, 0, 0, null);
		g.dispose();

		return copy;
	}

	private void toggleQuickTransparent() {
		quickTransparentMode = !quickTransparentMode;

		if (quickTransparentMode) {
			quickTransparentToggle.setText("⚡ Quick ON");
			quickTransparentToggle.setBackground(new Color(0, 150, 0));
			imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
			System.out.println("⚡ Quick Transparent Modus AKTIVIERT - Klicken für Floodfill mit Transparenz-Farbe");
		} else {
			quickTransparentToggle.setText("⚡ Quick Transparent");
			quickTransparentToggle.setBackground(new Color(100, 100, 100));
			imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			System.out.println("⚡ Quick Transparent Modus DEAKTIVIERT");
		}
	}

	private void chooseTransparencyColor() {
		Color newColor = javax.swing.JColorChooser.showDialog(this, "Transparenz-Farbe wählen",
				currentTransparencyColor);

		if (newColor != null) {
			currentTransparencyColor = newColor;
			updateTransparencyColorButton();

			System.out.println("🎨 Neue Transparenz-Farbe gewählt: RGB(" + newColor.getRed() + "," + newColor.getGreen()
					+ "," + newColor.getBlue() + ") "
					+ String.format("#%02X%02X%02X", newColor.getRed(), newColor.getGreen(), newColor.getBlue()));
		}
	}

	private void updateTransparencyColorButton() {
		transparencyColorButton.setBackground(currentTransparencyColor);
		transparencyColorButton.setOpaque(true);
		transparencyColorButton.setBorderPainted(true);

		// Kontrast-Text für bessere Sichtbarkeit
		double brightness = 0.299 * currentTransparencyColor.getRed() + 0.587 * currentTransparencyColor.getGreen()
				+ 0.114 * currentTransparencyColor.getBlue();
		transparencyColorButton.setForeground(brightness > 128 ? Color.BLACK : Color.WHITE);
		transparencyColorButton.setText("T");
		transparencyColorButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

		String colorInfo = String.format("Transparenz: #%02X%02X%02X", currentTransparencyColor.getRed(),
				currentTransparencyColor.getGreen(), currentTransparencyColor.getBlue());
		transparencyColorButton.setToolTipText(colorInfo);
	}

	private void handleQuickTransparentClick(MouseEvent e) {
		// MainApp.java handleQuickTransparentClick()
		if (loadedImage == null) return;

		Point scrollOffset = imageScrollPane.getViewport().getViewPosition();
		int absoluteX = e.getX() + scrollOffset.x;
		int absoluteY = e.getY() + scrollOffset.y;

		Rectangle imageBounds = getImageBounds();
		int relativeX = absoluteX - imageBounds.x;
		int relativeY = absoluteY - imageBounds.y;

		if (relativeX < 0 || relativeY < 0 || relativeX >= imageBounds.width || relativeY >= imageBounds.height) {
			return;
		}

		int originalX = (int)(relativeX / zoomFactor);
		int originalY = (int)(relativeY / zoomFactor);

		if (originalX >= 0 && originalY >= 0 && originalX < loadedImage.getWidth() && originalY < loadedImage.getHeight()) {
			isImageEdited = ImageUtils.performQuickFloodFill(originalX, originalY, currentTransparencyColor, loadedImage);
			updateImageLabel();
		}
	}

	// EditToolsWindow.EditToolsListener Implementation
	@Override
	public void onModeChanged(EditToolsWindow.EditMode newMode) {
		this.currentEditMode = newMode;
		updateCursorForMode();

		System.out.println("🔧 Edit Modus geändert zu: " + newMode.name());
	}

	@Override
	public void onColorSelected(Color color) {
		this.currentEditColor = color;
		System.out.println("🎨 Edit Farbe geändert zu: RGB(" + color.getRed() + "," + color.getGreen() + ","
				+ color.getBlue() + ")");
	}

	@Override
	public void onTransparencyColorChanged(Color color) {
		this.currentTransparencyColor = color;
		updateTransparencyColorButton();
		System.out.println("👻 Transparenz-Farbe geändert zu: RGB(" + color.getRed() + "," + color.getGreen() + ","
				+ color.getBlue() + ")");
	}

	@Override
	public void onToolAction(String action) {
		System.out.println("🔧 Tool Action: " + action);
		// Für zukünftige Erweiterungen
	}

	private void openEditTools() {
		// MainApp.java openEditTools()
		System.out.println("DEBUG: openEditTools() called");
		System.out.println("DEBUG: editToolsWindow is null: " + (editToolsWindow == null));
		
		if (editToolsWindow != null) {
			System.out.println("DEBUG: editToolsWindow.isDisplayable(): " + editToolsWindow.isDisplayable());
		}
		
		if (editToolsWindow == null || !editToolsWindow.isDisplayable()) {
			System.out.println("DEBUG: Creating new EditToolsWindow...");
			editToolsWindow = new EditToolsWindow(this);
			Point mainLocation = this.getLocation();
			editToolsWindow.setLocation(mainLocation.x + this.getWidth() + 10, mainLocation.y);
			System.out.println("DEBUG: EditToolsWindow created");
		}

		System.out.println("DEBUG: Setting visible...");
		editToolsWindow.setVisible(true);
		editToolsWindow.toFront();
		System.out.println("DEBUG: openEditTools() completed");
	}

	private void updateCursorForMode() {
		if (imageLabel == null)
			return;

		switch (currentEditMode) {
		case COLOR_PICKER:
			imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			break;
		case FLOODFILL:
			imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			break;
		case TRANSPARENCY:
			imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
			break;
		}
	}

	private void handleColorPickerClick(MouseEvent e) {
		// Normale Farbauswahl (bisherige Logik)
		try {
			Robot robot = new Robot();

			// Konvertiere Mausposition zu Bildschirmkoordinaten
			Point componentLocation = imageLabel.getLocationOnScreen();
			int screenX = componentLocation.x + e.getX();
			int screenY = componentLocation.y + e.getY();

			// Lies die exakte Pixelfarbe vom Bildschirm
			Color pixelColor = robot.getPixelColor(screenX, screenY);
			addColorToList(pixelColor);
			System.out.println("🎨 Farbe ausgewählt (Robot): RGB(" + pixelColor.getRed() + "," + pixelColor.getGreen()
					+ "," + pixelColor.getBlue() + ") bei (" + screenX + "," + screenY + ")");
		} catch (Exception ex) {
			System.out.println("❌ Fehler bei Robot-Farberkennung: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	// MainApp.java

	private void handleFloodfillClick(MouseEvent e) {
		// MainApp.java handleFloodfillClick()
		try {
			Point componentLocation = imageLabel.getLocationOnScreen();
			int screenX = componentLocation.x + e.getX();
			int screenY = componentLocation.y + e.getY();
			Color robotColor = new Robot().getPixelColor(screenX, screenY);

			Point scrollOffset = imageScrollPane.getViewport().getViewPosition();
			int absoluteX = e.getX() + scrollOffset.x;
			int absoluteY = e.getY() + scrollOffset.y;

			Rectangle imageBounds = getImageBounds();
			int relativeX = absoluteX - imageBounds.x;
			int relativeY = absoluteY - imageBounds.y;

			if (relativeX < 0 || relativeY < 0 || relativeX >= imageBounds.width || relativeY >= imageBounds.height) {
				return;
			}

			int originalX = (int)(relativeX / zoomFactor);
			int originalY = (int)(relativeY / zoomFactor);

			if (originalX >= 0 && originalY >= 0 && originalX < loadedImage.getWidth() && originalY < loadedImage.getHeight()) {
				isImageEdited = ImageUtils.performFloodFill(originalX, originalY, robotColor, toleranceSlider.getValue(), loadedImage);
				updateImageLabel();
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}


	private void handleTransparencyClick(MouseEvent e) {
		// MainApp.java handleTransparencyClick()
		if (loadedImage == null)
			return;

		try {
			Point componentLocation = imageLabel.getLocationOnScreen();
			int screenX = componentLocation.x + e.getX();
			int screenY = componentLocation.y + e.getY();

			Robot robot = new Robot();
			Color robotColor = robot.getPixelColor(screenX, screenY);

			Point scrollOffset = imageScrollPane.getViewport().getViewPosition();
			int absoluteX = e.getX() + scrollOffset.x;
			int absoluteY = e.getY() + scrollOffset.y;

			Rectangle imageBounds = getImageBounds();
			int relativeX = absoluteX - imageBounds.x;
			int relativeY = absoluteY - imageBounds.y;

			if (relativeX < 0 || relativeY < 0 || relativeX >= imageBounds.width || relativeY >= imageBounds.height) {
				return;
			}

			int originalX = (int) (relativeX / zoomFactor);
			int originalY = (int) (relativeY / zoomFactor);

			if (originalX >= 0 && originalY >= 0 && originalX < loadedImage.getWidth()
					&& originalY < loadedImage.getHeight()) {
				isImageEdited = ImageUtils.performFloodFill(originalX, originalY, currentTransparencyColor, true,
						loadedImage);
				updateImageLabel();
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private boolean performPreciseTransparencyFloodFill(int startX, int startY, Color robotStartColor,
			Color transparencyColor, int tolerance) {
		// MainApp.java performPreciseTransparencyFloodFill()
		if (startX < 0 || startY < 0 || startX >= loadedImage.getWidth() || startY >= loadedImage.getHeight()) {
			return false;
		}

		if (colorsMatch(robotStartColor, transparencyColor, 5)) {
			return false;
		}

		java.util.Queue<Point> queue = new java.util.LinkedList<>();
		boolean[][] visited = new boolean[loadedImage.getWidth()][loadedImage.getHeight()];

		queue.offer(new Point(startX, startY));
		int pixelCount = 0;

		while (!queue.isEmpty()) {
			Point p = queue.poll();
			int x = p.x;
			int y = p.y;

			if (x < 0 || y < 0 || x >= loadedImage.getWidth() || y >= loadedImage.getHeight() || visited[x][y]) {
				continue;
			}

			Color currentPixelColor = new Color(loadedImage.getRGB(x, y), true);
			if (!colorsMatch(currentPixelColor, robotStartColor, tolerance)) {
				continue;
			}

			visited[x][y] = true;
			loadedImage.setRGB(x, y, transparencyColor.getRGB());
			pixelCount++;

			queue.offer(new Point(x + 1, y));
			queue.offer(new Point(x - 1, y));
			queue.offer(new Point(x, y + 1));
			queue.offer(new Point(x, y - 1));
		}

		return pixelCount > 0;
	}

	private Point getImageCoordinates(MouseEvent e) {
		if (loadedImage == null)
			return null;

		// 1. Scroll-Offset des sichtbaren Bereichs holen
		Point scrollOffset = imageScrollPane.getViewport().getViewPosition();

		// 2. Mausposition im gescrollten Bild berechnen
		int absoluteX = e.getX() + scrollOffset.x;
		int absoluteY = e.getY() + scrollOffset.y;

		// 3. Zoom zurückrechnen auf Originalbild-Koordinaten
		int imgX = (int) (absoluteX / zoomFactor);
		int imgY = (int) (absoluteY / zoomFactor);

		// 4. Bildbereich prüfen
		if (imgX < 0 || imgY < 0 || imgX >= loadedImage.getWidth() || imgY >= loadedImage.getHeight())
			return null;

		return new Point(imgX, imgY);
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new MainApp());
	}
}