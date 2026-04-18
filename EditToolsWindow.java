package main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

public class EditToolsWindow extends JFrame {

	public enum EditMode {
		COLOR_PICKER("🎨 Farbauswahl", "Farben zum Sammeln anklicken"),
		FLOODFILL("🌊 Floodfill", "Bereiche mit gewählter Farbe füllen"),
		TRANSPARENCY("👻 Transparenz", "Bereiche transparent machen");

		private final String displayName;
		private final String description;

		EditMode(String displayName, String description) {
			this.displayName = displayName;
			this.description = description;
		}

		public String getDisplayName() {
			return displayName;
		}

		public String getDescription() {
			return description;
		}

		@Override
		public String toString() {
			return displayName;
		}
	}

	// Interface für Kommunikation mit MainApp
	public interface EditToolsListener {
		void onModeChanged(EditMode newMode);

		void onColorSelected(Color color);

		void onTransparencyColorChanged(Color color);

		void onToolAction(String action);
	}

	private EditToolsListener listener;
	private EditMode currentMode = EditMode.COLOR_PICKER;

	private Color selectedColor = Color.RED;
	private Color transparencyColor = new Color(255, 0, 255); // Magenta
	private List<Color> colorPalette;
	private JPanel palettePanel;

	private JButton colorPickerBtn;
	private JButton floodfillBtn;
	private JButton transparencyBtn;
	private JButton transparencyColorButton;
	private JLabel statusLabel;

	public EditToolsWindow(EditToolsListener listener) {
		this.listener = listener;

		initializePalette();
		setupWindow();
		setupModeButtons();
		setupColorPalette();
		setupActionButtons();
		setupStatusPanel();

		updateModeDisplay();
		setVisible(true);

		System.out.println("🔧 Edit Tools Window geöffnet");
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

		// Weitere Farben
		colorPalette.add(new Color(255, 165, 0)); // Orange
		colorPalette.add(new Color(128, 0, 128)); // Purple
		colorPalette.add(new Color(165, 42, 42)); // Brown
		colorPalette.add(new Color(255, 192, 203)); // Pink
		colorPalette.add(new Color(0, 128, 0)); // Dark Green
	}

	private void setupWindow() {
		setTitle("Edit Tools - Paint & Color Tools");
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setSize(350, 600);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());
		setAlwaysOnTop(true); // Immer im Vordergrund

		// Window Closing Handler
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				if (listener != null) {
					listener.onModeChanged(EditMode.COLOR_PICKER); // Zurück zu Standard-Modus
				}
			}
		});
	}

	private void setupModeButtons() {
		JPanel modePanel = new JPanel(new GridLayout(1, 3, 5, 5));
		modePanel.setBorder(BorderFactory.createTitledBorder("Edit Modus"));

		colorPickerBtn = new JButton("🎨");
		colorPickerBtn.setToolTipText(EditMode.COLOR_PICKER.toString());
		colorPickerBtn.addActionListener(e -> setMode(EditMode.COLOR_PICKER));

		floodfillBtn = new JButton("🌊");
		floodfillBtn.setToolTipText(EditMode.FLOODFILL.toString());
		floodfillBtn.addActionListener(e -> setMode(EditMode.FLOODFILL));

		transparencyBtn = new JButton("👻");
		transparencyBtn.setToolTipText(EditMode.TRANSPARENCY.toString());
		transparencyBtn.addActionListener(e -> setMode(EditMode.TRANSPARENCY));

		modePanel.add(colorPickerBtn);
		modePanel.add(floodfillBtn);
		modePanel.add(transparencyBtn);

		add(modePanel, BorderLayout.NORTH);
	}

	private void setupColorPalette() {
		JPanel paletteContainer = new JPanel(new BorderLayout());
		paletteContainer.setBorder(BorderFactory.createTitledBorder("Color Palette"));

		palettePanel = new JPanel(new GridLayout(0, 4, 2, 2));
		updatePaletteDisplay();

		JScrollPane paletteScroll = new JScrollPane(palettePanel);
		paletteScroll.setPreferredSize(new Dimension(320, 250));
		paletteContainer.add(paletteScroll, BorderLayout.CENTER);

		// Transparenz-Farbe Panel
		JPanel transparencyPanel = new JPanel(new BorderLayout(5, 5));
		transparencyPanel.setBorder(BorderFactory.createTitledBorder("Transparenz-Farbe"));

		transparencyColorButton = new JButton();
		transparencyColorButton.setPreferredSize(new Dimension(60, 40));
		transparencyColorButton.addActionListener(e -> chooseTransparencyColor());
		updateTransparencyColorButton();

		JLabel transparencyLabel = new JLabel("Klicken zum Ändern");
		transparencyLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));

		transparencyPanel.add(transparencyColorButton, BorderLayout.WEST);
		transparencyPanel.add(transparencyLabel, BorderLayout.CENTER);

		paletteContainer.add(transparencyPanel, BorderLayout.SOUTH);
		add(paletteContainer, BorderLayout.CENTER);
	}

	private void setupActionButtons() {
		JPanel actionPanel = new JPanel(new GridLayout(4, 1, 5, 5));
		actionPanel.setBorder(BorderFactory.createTitledBorder("Aktionen"));

		JButton customColorBtn = new JButton("Custom Color");
		customColorBtn.addActionListener(e -> openColorChooser());
		actionPanel.add(customColorBtn);

		JButton addToPaletteBtn = new JButton("Add to Palette");
		addToPaletteBtn.addActionListener(e -> addColorToPalette());
		actionPanel.add(addToPaletteBtn);

		JButton clearPaletteBtn = new JButton("Clear Palette");
		clearPaletteBtn.addActionListener(e -> clearPalette());
		actionPanel.add(clearPaletteBtn);

		JButton closeBtn = new JButton("Close Tools");
		closeBtn.setBackground(new Color(150, 0, 0));
		closeBtn.setForeground(Color.WHITE);
		closeBtn.addActionListener(e -> {
			if (listener != null) {
				listener.onModeChanged(EditMode.COLOR_PICKER);
			}
			setVisible(false);
		});
		actionPanel.add(closeBtn);

		add(actionPanel, BorderLayout.SOUTH);
	}

	private void setupStatusPanel() {
		statusLabel = new JLabel("Bereit", SwingConstants.CENTER);
		statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		statusLabel.setOpaque(true);
		statusLabel.setBackground(new Color(240, 240, 240));

		JPanel statusPanel = new JPanel(new BorderLayout());
		statusPanel.add(statusLabel, BorderLayout.CENTER);

		// Info Panel
		JPanel infoPanel = new JPanel(new FlowLayout());
		JLabel infoLabel = new JLabel("<html><center><b>Alle Klicks erfolgen im Hauptfenster!</b><br>"
				+ "Dieses Fenster nur für Tool-Auswahl</center></html>");
		infoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		infoPanel.add(infoLabel);

		statusPanel.add(infoPanel, BorderLayout.SOUTH);
		add(statusPanel, BorderLayout.EAST);
	}

	private void setMode(EditMode mode) {
		this.currentMode = mode;
		updateModeDisplay();

		if (listener != null) {
			listener.onModeChanged(mode);
		}

		System.out.println("🔧 Edit Modus geändert: " + mode.name());
	}

	private void updateModeDisplay() {
		// Reset alle Buttons
		colorPickerBtn.setBackground(null);
		floodfillBtn.setBackground(null);
		transparencyBtn.setBackground(null);

		// Markiere aktiven Modus
		Color activeColor = new Color(0, 150, 0);
		switch (currentMode) {
		case COLOR_PICKER:
			colorPickerBtn.setBackground(activeColor);
			colorPickerBtn.setOpaque(true);
			statusLabel.setText("Modus: Farbauswahl");
			break;
		case FLOODFILL:
			floodfillBtn.setBackground(activeColor);
			floodfillBtn.setOpaque(true);
			statusLabel.setText("Modus: Floodfill");
			break;
		case TRANSPARENCY:
			transparencyBtn.setBackground(activeColor);
			transparencyBtn.setOpaque(true);
			statusLabel.setText("Modus: Transparenz");
			break;
		}
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
		btn.setPreferredSize(new Dimension(40, 40));
		btn.setBackground(color);
		btn.setOpaque(true);
		btn.setBorderPainted(true);

		btn.setToolTipText("RGB(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ")");

		// Selection Border
		if (color.equals(selectedColor)) {
			btn.setBorder(BorderFactory.createLineBorder(Color.BLACK, 3));
		} else {
			btn.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
		}

		btn.addActionListener(e -> {
			selectedColor = color;
			updatePaletteDisplay();

			if (listener != null) {
				listener.onColorSelected(color);
			}

			System.out.println(
					"🎨 Farbe gewählt: RGB(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ")");
		});

		return btn;
	}

	private void updateTransparencyColorButton() {
		transparencyColorButton.setBackground(transparencyColor);
		transparencyColorButton.setOpaque(true);
		transparencyColorButton.setBorderPainted(true);

		// Kontrast-Text
		double brightness = 0.299 * transparencyColor.getRed() + 0.587 * transparencyColor.getGreen()
				+ 0.114 * transparencyColor.getBlue();
		transparencyColorButton.setForeground(brightness > 128 ? Color.BLACK : Color.WHITE);
		transparencyColorButton.setText("T");
		transparencyColorButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

		String colorInfo = String.format("Transparenz: #%02X%02X%02X", transparencyColor.getRed(),
				transparencyColor.getGreen(), transparencyColor.getBlue());
		transparencyColorButton.setToolTipText(colorInfo);
	}

	private void openColorChooser() {
		Color newColor = JColorChooser.showDialog(this, "Farbe wählen", selectedColor);
		if (newColor != null) {
			selectedColor = newColor;
			updatePaletteDisplay();

			if (listener != null) {
				listener.onColorSelected(newColor);
			}

			System.out.println("🎨 Custom Color gewählt: RGB(" + newColor.getRed() + "," + newColor.getGreen() + ","
					+ newColor.getBlue() + ")");
		}
	}

	private void addColorToPalette() {
		if (!colorPalette.contains(selectedColor)) {
			colorPalette.add(selectedColor);
			updatePaletteDisplay();
			System.out.println("➕ Farbe zur Palette hinzugefügt: RGB(" + selectedColor.getRed() + ","
					+ selectedColor.getGreen() + "," + selectedColor.getBlue() + ")");
		} else {
			System.out.println("⚠️  Farbe bereits in Palette vorhanden");
		}
	}

	private void clearPalette() {
		if (colorPalette.size() > 8) { // Behalte Standard-Farben
			colorPalette = colorPalette.subList(0, 8);
			updatePaletteDisplay();
			System.out.println("🧹 Palette auf Standard-Farben zurückgesetzt");
		}
	}

	private void chooseTransparencyColor() {
		Color newColor = JColorChooser.showDialog(this, "Transparenz-Farbe wählen", transparencyColor);

		if (newColor != null) {
			transparencyColor = newColor;
			updateTransparencyColorButton();

			if (listener != null) {
				listener.onTransparencyColorChanged(newColor);
			}

			System.out.println("🎨 Neue Transparenz-Farbe gewählt: RGB(" + newColor.getRed() + "," + newColor.getGreen()
					+ "," + newColor.getBlue() + ") "
					+ String.format("#%02X%02X%02X", newColor.getRed(), newColor.getGreen(), newColor.getBlue()));
		}
	}

	// Public Getters
	public EditMode getCurrentMode() {
		return currentMode;
	}

	public Color getSelectedColor() {
		return selectedColor;
	}

	public Color getTransparencyColor() {
		return transparencyColor;
	}
}