// HtmlDependencyCleaner.java
package main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;

public class HtmlDependencyCleaner {

	private JFrame frame;
	private JTextField statusLabel = new JTextField(" ");
	private JButton copyErrorButton = new JButton("Kopieren");
	private JButton sortButton = new JButton("Sortieren");
	private DefaultListModel<File> referencedModel = new DefaultListModel<>();
	private DefaultListModel<File> unreferencedModel = new DefaultListModel<>();
	private File baseDir;
	private File rootHtmlFile;
	private Set<Path> referencedPaths = new HashSet<>();
	private Set<Path> allRelevantPaths = new HashSet<>();
	private Map<Path, Set<Path>> dependencyMap = new HashMap<>();

	// Erweiterte Regex-Pattern für verschiedene Referenztypen
	private static final Pattern[] HTML_PATTERNS = {
			Pattern.compile("(?:src|href)\\s*=\\s*[\"']([^\"']+\\.(html|css|js))[\"']", Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?:src|href)\\s*=\\s*([^\\s>]+\\.(html|css|js))(?=\\s|>)", Pattern.CASE_INSENSITIVE),
			Pattern.compile("@import\\s+[\"']([^\"']+\\.css)[\"']", Pattern.CASE_INSENSITIVE),
			Pattern.compile("@import\\s+url\\([\"']?([^\"'\\)]+\\.css)[\"']?\\)", Pattern.CASE_INSENSITIVE) };

	private static final Pattern[] CSS_PATTERNS = {
			Pattern.compile("@import\\s+[\"']([^\"']+\\.css)[\"']", Pattern.CASE_INSENSITIVE),
			Pattern.compile("@import\\s+url\\([\"']?([^\"'\\)]+\\.css)[\"']?\\)", Pattern.CASE_INSENSITIVE),
			Pattern.compile("url\\([\"']?([^\"'\\)]+\\.(css|js))[\"']?\\)", Pattern.CASE_INSENSITIVE) };

	private static final Pattern[] JS_PATTERNS = {
			Pattern.compile("import\\s+.*?from\\s+[\"']([^\"']+\\.(js|html|css))[\"']", Pattern.CASE_INSENSITIVE),
			Pattern.compile("import\\s+[\"']([^\"']+\\.(js|html|css))[\"']", Pattern.CASE_INSENSITIVE),
			Pattern.compile("require\\s*\\([\"']([^\"']+\\.(js|html|css))[\"']\\)", Pattern.CASE_INSENSITIVE),
			Pattern.compile("importScripts\\s*\\([\"']([^\"']+\\.js)[\"']\\)", Pattern.CASE_INSENSITIVE),
			Pattern.compile("loadScript\\s*\\([\"']([^\"']+\\.js)[\"']\\)", Pattern.CASE_INSENSITIVE) };

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new HtmlDependencyCleaner().createAndShowGUI());
	}

	private void createAndShowGUI() {
		frame = new JFrame("HTML Dependency Cleaner");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(1000, 600);

		JPanel content = new JPanel(new BorderLayout(10, 10));
		content.setBorder(new EmptyBorder(10, 10, 10, 10));
		content.add(createTopPanel(), BorderLayout.NORTH);

		// Center: Listen
		content.add(createListsPanel(), BorderLayout.CENTER);

		// Bottom: Sortieren-Button + Statusleiste
		JPanel bottom = new JPanel(new BorderLayout(5, 0));
		bottom.add(createButtonPanel(), BorderLayout.WEST);

		JPanel statusPanel = new JPanel(new BorderLayout(5, 0));
		statusLabel.setEditable(false);
		statusLabel.setBorder(null);
		statusLabel.setBackground(null);
		statusLabel.setForeground(Color.BLACK);
		statusLabel.setFocusable(false);
		statusPanel.add(statusLabel, BorderLayout.CENTER);
		statusPanel.add(copyErrorButton, BorderLayout.EAST);
		copyErrorButton.setEnabled(false);
		copyErrorButton.addActionListener(e -> {
			StringSelection sel = new StringSelection(statusLabel.getText());
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
		});

		bottom.add(statusPanel, BorderLayout.CENTER);
		content.add(bottom, BorderLayout.SOUTH);

		frame.setContentPane(content);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private JPanel createTopPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		JLabel label = new JLabel("HTML-Datei hierher ziehen", SwingConstants.CENTER);
		label.setPreferredSize(new Dimension(800, 80));
		label.setBorder(BorderFactory.createDashedBorder(Color.GRAY));
		panel.add(label, BorderLayout.CENTER);

		new DropTarget(label, new DropTargetAdapter() {
			@Override
			public void drop(DropTargetDropEvent evt) {
				evt.acceptDrop(DnDConstants.ACTION_COPY);
				try {
					@SuppressWarnings("unchecked")
					List<File> dropped = (List<File>) evt.getTransferable()
							.getTransferData(DataFlavor.javaFileListFlavor);
					if (!dropped.isEmpty()) {
						baseDir = dropped.get(0).getParentFile();
						rootHtmlFile = dropped.get(0);
						referencedPaths.clear();
						dependencyMap.clear();
						scanReferences(rootHtmlFile);
						statusLabel.setText("Datei geladen. Klicke auf 'Sortieren'.");
						copyErrorButton.setEnabled(true);
						sortButton.setEnabled(true);
					}
				} catch (Exception ex) {
					showError("Fehler beim Verarbeiten: " + ex.getMessage());
				}
			}
		});
		return panel;
	}

	private JPanel createListsPanel() {
		JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));
		JList<File> refList = new JList<>(referencedModel);
		refList.setBorder(BorderFactory.createTitledBorder("Referenzierte Dateien"));
		panel.add(new JScrollPane(refList));

		JList<File> unrefList = new JList<>(unreferencedModel);
		unrefList.setCellRenderer(new UnreferencedFileRenderer());
		unrefList.setBorder(BorderFactory.createTitledBorder("Nicht referenzierte Dateien"));
		panel.add(new JScrollPane(unrefList));

		return panel;
	}

	private JPanel createButtonPanel() {
		JPanel panel = new JPanel();
		sortButton.setEnabled(false);
		sortButton.addActionListener(this::compareAndDisplay);
		panel.add(sortButton);
		return panel;
	}

	private void scanReferences(File file) {
		try {
			scanFileForReferences(file);
		} catch (IOException e) {
			showError("Fehler beim Einlesen: " + e.getMessage());
		}
	}

	private void scanFileForReferences(File file) throws IOException {
		Path path = file.toPath();
		if (!file.exists() || referencedPaths.contains(path))
			return;
		referencedPaths.add(path);
		dependencyMap.putIfAbsent(path, new HashSet<>());

		String content = Files.readString(path);
		String fileName = file.getName().toLowerCase();

		// Dateityp-spezifisches Scannen
		Pattern[] patterns;
		if (fileName.endsWith(".html")) {
			patterns = HTML_PATTERNS;
		} else if (fileName.endsWith(".css")) {
			patterns = CSS_PATTERNS;
		} else if (fileName.endsWith(".js")) {
			patterns = JS_PATTERNS;
		} else {
			return;
		}

		// Alle Pattern durchlaufen
		for (Pattern pattern : patterns) {
			Matcher m = pattern.matcher(content);
			while (m.find()) {
				String ref = m.group(1);

				// Externe URLs überspringen
				if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("//")
						|| ref.startsWith("data:")) {
					continue;
				}

				// Pfad auflösen
				File resolvedFile = resolveReference(file, ref);
				if (resolvedFile != null) {
					Path resolvedPath = resolvedFile.toPath().normalize();
					if (resolvedPath.startsWith(baseDir.toPath()) && Files.exists(resolvedPath)) {
						dependencyMap.get(path).add(resolvedPath);
						scanFileForReferences(resolvedFile);
					}
				}
			}
		}
	}

	private File resolveReference(File currentFile, String reference) {
		try {
			// Absolute Pfade behandeln
			if (reference.startsWith("/")) {
				return new File(baseDir, reference.substring(1));
			}

			// Relative Pfade
			File parentDir = currentFile.getParentFile();
			File resolved = new File(parentDir, reference);

			// Normalisierung für ../ Pfade
			return resolved.getCanonicalFile();
		} catch (IOException e) {
			return null;
		}
	}

	private void compareAndDisplay(ActionEvent e) {
		if (baseDir == null) {
			showError("Keine Datei geladen.");
			return;
		}
		referencedModel.clear();
		unreferencedModel.clear();
		allRelevantPaths.clear();

		try {
			Files.walk(baseDir.toPath()).filter(Files::isRegularFile)
					.filter(p -> p.toString().matches(".*\\.(html|css|js)$")).forEach(allRelevantPaths::add);

			for (Path p : allRelevantPaths) {
				if (referencedPaths.contains(p))
					referencedModel.addElement(p.toFile());
				else
					unreferencedModel.addElement(p.toFile());
			}

			statusLabel.setText("Sortierung fertig: " + unreferencedModel.size() + " unreferenzierte Dateien");
			showReferenceTree();
		} catch (IOException ex) {
			showError("Fehler beim Scannen: " + ex.getMessage());
		}
	}

	// HtmlDependencyCleaner, Methode: buildTreeModel mit Visited-Check
	private DefaultMutableTreeNode buildTreeModel(File file, Set<Path> visited) {
		Path path = file.toPath();
		DefaultMutableTreeNode node = new DefaultMutableTreeNode(file.getName());
		if (visited.contains(path))
			return node;
		visited.add(path);

		for (Path child : dependencyMap.getOrDefault(path, Collections.emptySet())) {
			node.add(buildTreeModel(child.toFile(), visited));
		}
		return node;
	}

	private void showReferenceTree() {
		if (rootHtmlFile == null)
			return;
		DefaultMutableTreeNode root = buildTreeModel(rootHtmlFile, new HashSet<>());
		JTree tree = new JTree(root);
		JOptionPane.showMessageDialog(frame, new JScrollPane(tree), "Referenzbaum", JOptionPane.PLAIN_MESSAGE);
	}

	private void showError(String msg) {
		JOptionPane.showMessageDialog(frame, msg, "Fehler", JOptionPane.ERROR_MESSAGE);
		statusLabel.setText("Fehler: " + msg);
		statusLabel.setForeground(Color.RED);
		copyErrorButton.setEnabled(true);
	}

	class UnreferencedFileRenderer extends JPanel implements javax.swing.ListCellRenderer<File> {
		private JLabel label = new JLabel();
		private JButton delBtn = new JButton("[X]");

		public UnreferencedFileRenderer() {
			setLayout(new BorderLayout());
			add(label, BorderLayout.CENTER);
			add(delBtn, BorderLayout.EAST);
			delBtn.addActionListener(e -> {
				File f = (File) delBtn.getClientProperty("file");
				if (f.exists() && JOptionPane.showConfirmDialog(frame, "Löschen?\n" + f.getPath(), "Löschen",
						JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
					f.delete();
					unreferencedModel.removeElement(f);
				}
			});
		}

		public java.awt.Component getListCellRendererComponent(JList<? extends File> list, File value, int idx,
				boolean sel, boolean foc) {
			label.setText(value.getAbsolutePath());
			delBtn.putClientProperty("file", value);
			setBackground(sel ? Color.LIGHT_GRAY : Color.WHITE);
			return this;
		}
	}
}