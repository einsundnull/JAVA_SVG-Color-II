package main;

import java.awt.Color;
import java.awt.image.BufferedImage;

import javax.swing.JCheckBox;

public class ColorEntry {
	private final Color color;
	private JCheckBox checkbox;
	private BufferedImage mask;

	public ColorEntry(Color color) {
		this.color = color;
	}

	public ColorEntry(Color color, BufferedImage mask) {
		this.color = color;
		this.mask = mask;
	}

	public Color getColor() {
		return color;
	}

	public JCheckBox getCheckbox() {
		return checkbox;
	}

	public void setCheckbox(JCheckBox checkbox) {
		this.checkbox = checkbox;
	}

	public boolean isSelected() {
		return checkbox != null && checkbox.isSelected();
	}

	public BufferedImage getMask() {
		return mask;
	}

} // ENDE ColorEntry
