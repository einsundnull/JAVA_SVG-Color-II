package main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class ConsoleWindow extends JFrame {
	private static ConsoleWindow instance;
	private JTextArea textArea;
	private JScrollPane scrollPane;
	private JCheckBox autoScrollCheckBox;
	private JButton clearButton;
	private PrintStream originalOut;
	private PrintStream originalErr;
	private boolean isCapturing = false;

	ConsoleWindow() {
		initializeWindow();
		setupComponents();
		setupRedirection();
	}

	public static ConsoleWindow getInstance() {
		if (instance == null) {
			instance = new ConsoleWindow();
		}
		return instance;
	}

	public void show() {
		SwingUtilities.invokeLater(() -> {
			ConsoleWindow window = getInstance();
			window.setVisible(true);
			window.toFront();
			window.startCapturing();
		});
	}

	public void hide() {
		SwingUtilities.invokeLater(() -> {
			if (instance != null) {
				instance.setVisible(false);
				instance.stopCapturing();
			}
		});
	}

	private void initializeWindow() {
		setTitle("Console Output");
		setSize(800, 600);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

		// Beim Schließen die Umleitung stoppen
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				stopCapturing();
			}
		});
	}

	private void setupComponents() {
		setLayout(new BorderLayout());

		// Text Area für Console Output
		textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setBackground(Color.BLACK);
		textArea.setForeground(Color.GREEN);
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);

		// Scroll Pane
		scrollPane = new JScrollPane(textArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.setPreferredSize(new Dimension(780, 520));
		add(scrollPane, BorderLayout.CENTER);

		// Control Panel
		JPanel controlPanel = new JPanel(new BorderLayout());

		// Auto-Scroll Checkbox
		autoScrollCheckBox = new JCheckBox("Auto-Scroll", true);
		autoScrollCheckBox.setToolTipText("Automatisch zum Ende scrollen");

		// Clear Button
		clearButton = new JButton("Clear");
		clearButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				clearConsole();
			}
		});

		// Capture Toggle Button
		JButton captureButton = new JButton("Start Capturing");
		captureButton.addActionListener(e -> {
			if (isCapturing) {
				stopCapturing();
				captureButton.setText("Start Capturing");
			} else {
				startCapturing();
				captureButton.setText("Stop Capturing");
			}
		});

		JPanel leftPanel = new JPanel();
		leftPanel.add(autoScrollCheckBox);
		leftPanel.add(clearButton);
		leftPanel.add(captureButton);

		controlPanel.add(leftPanel, BorderLayout.WEST);
		add(controlPanel, BorderLayout.SOUTH);
	}

	private void setupRedirection() {
		// Original Streams speichern
		originalOut = System.out;
		originalErr = System.err;
	}

	public void startCapturing() {
		if (isCapturing)
			return;

		isCapturing = true;

		// Custom Output Stream für System.out
		OutputStream outStream = new OutputStream() {
			private StringBuilder buffer = new StringBuilder();

			@Override
			public void write(int b) throws IOException {
				originalOut.write(b); // Weiterhin in originale Console ausgeben

				char c = (char) b;
				if (c == '\n') {
					// Zeile komplett, in TextArea ausgeben
					final String line = buffer.toString() + "\n";
					buffer.setLength(0);

					SwingUtilities.invokeLater(() -> {
						textArea.append(line);
						if (autoScrollCheckBox.isSelected()) {
							textArea.setCaretPosition(textArea.getDocument().getLength());
						}
					});
				} else {
					buffer.append(c);
				}
			}
		};

		// Custom Output Stream für System.err
		OutputStream errStream = new OutputStream() {
			private StringBuilder buffer = new StringBuilder();

			@Override
			public void write(int b) throws IOException {
				originalErr.write(b); // Weiterhin in originale Console ausgeben

				char c = (char) b;
				if (c == '\n') {
					// Zeile komplett, in TextArea ausgeben (mit Error-Prefix)
					final String line = "ERROR: " + buffer.toString() + "\n";
					buffer.setLength(0);

					SwingUtilities.invokeLater(() -> {
						textArea.append(line);
						if (autoScrollCheckBox.isSelected()) {
							textArea.setCaretPosition(textArea.getDocument().getLength());
						}
					});
				} else {
					buffer.append(c);
				}
			}
		};

		// System Streams umleiten
		System.setOut(new PrintStream(outStream, true));
		System.setErr(new PrintStream(errStream, true));

		appendMessage("🟢 Console Capturing gestartet\n");
	}

	public void stopCapturing() {
		if (!isCapturing)
			return;

		// Original Streams wiederherstellen
		System.setOut(originalOut);
		System.setErr(originalErr);

		isCapturing = false;
		appendMessage("🔴 Console Capturing gestoppt\n");
	}

	public void clearConsole() {
		SwingUtilities.invokeLater(() -> {
			textArea.setText("");
			appendMessage("🧹 Console geleert\n");
		});
	}

	public void appendMessage(String message) {
		SwingUtilities.invokeLater(() -> {
			textArea.append(message);
			if (autoScrollCheckBox.isSelected()) {
				textArea.setCaretPosition(textArea.getDocument().getLength());
			}
		});
	}

	public boolean isCapturing() {
		return isCapturing;
	}
}