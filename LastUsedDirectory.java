package main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LastUsedDirectory {
	private static final String FILE_NAME = "last_used_path.txt";
	private static final Path APP_FOLDER = Paths.get(System.getProperty("user.home"), ".my_potrace_app");
	private static final Path PATH_FILE = APP_FOLDER.resolve(FILE_NAME);

	public static void save(File directory) {
		try {
			if (!APP_FOLDER.toFile().exists())
				Files.createDirectories(APP_FOLDER);
			Files.write(PATH_FILE, directory.getAbsolutePath().getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static File load() {
		try {
			if (Files.exists(PATH_FILE)) {
				String path = new String(Files.readAllBytes(PATH_FILE));
				File dir = new File(path);
				if (dir.exists() && dir.isDirectory())
					return dir;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new File(System.getProperty("user.home"));
	}
}
