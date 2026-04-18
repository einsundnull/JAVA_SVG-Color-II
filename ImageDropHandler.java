package main;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.TransferHandler;

public class ImageDropHandler extends TransferHandler {

	private final Consumer<File> fileConsumer;

	public ImageDropHandler(Consumer<File> fileConsumer) {
		this.fileConsumer = fileConsumer;
	}

	@Override
	public boolean canImport(TransferHandler.TransferSupport support) {
		return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
	}

	@Override
	public boolean importData(TransferHandler.TransferSupport support) {
		try {
			if (!canImport(support))
				return false;

			@SuppressWarnings("unchecked")
			List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
			if (!files.isEmpty()) {
				fileConsumer.accept(files.get(0));
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
}
