package fileops;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileOperations {
	protected static final Logger logger = LogManager.getLogger(FileOperations.class.getName());
	private Map<String, Path> fileIndex;

	public FileOperations() {
		fileIndex = new HashMap<>();
	}

	public void deleteFiles(List<String> extensions) {
		for (Entry<String, Path> entry : fileIndex.entrySet()) {
			File file = entry.getValue().toFile();
			for (String extension : extensions) {
				if (!file.isDirectory() && file.getName().endsWith(extension)) {
					if (!file.delete()) {
						logger.error("Failed to delete file: \n\"{}\"", file.getAbsolutePath());
					}
				}
			}
		}
	}

	public void deleteEmptyFoldersUsingIndex() {
		// Collect all directories in a Set to avoid duplicates
		Set<Path> directories = new HashSet<>();
		for (Path path : fileIndex.values()) {
			directories.add(path.getParent());
		}

		// Iterate through collected directories and delete if empty
		for (Path dir : directories) {
			File directory = dir.toFile();
			if (directory.isDirectory() && directory.list().length == 0) {
				if (!directory.delete()) {
					logger.error("Failed to delete empty directory: \n\"{}\"", directory.getAbsolutePath());
				}
			}
		}
	}

	public static void deleteEmptyFoldersRecursively(File directory) {
		if (directory.isDirectory()) {
			File[] files = directory.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isDirectory()) {
						// Recursive call to delete empty subdirectories first
						deleteEmptyFoldersRecursively(file);
					}
				}
			}
			// After processing subdirectories, check if current directory is empty
			if (directory.isDirectory() && directory.list().length == 0) {
				directory.delete();
			}
		}
	}

	public void indexDirectory(Path startPath, int maxDepth, Path ignoreDir) throws IOException {
		Files.walkFileTree(startPath, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				fileIndex.put(file.getFileName().toString(), file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				try {
					if (Files.isSameFile(dir, ignoreDir)) {
						return FileVisitResult.SKIP_SUBTREE;
					} else {
					}
				} catch (IOException e) {
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	public void indexDirectory(Path startPath, int maxDepth) throws IOException {
		Files.walkFileTree(startPath, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				fileIndex.put(file.getFileName().toString(), file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});
	}

	public void indexDirectory(Path startPath) throws IOException {
		Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				fileIndex.put(file.getFileName().toString(), file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});
	}

	public boolean moveFileByName(String fileName, String newPath) {
		Path filePath = fileIndex.get(fileName);
		if (filePath != null) {
			File file = filePath.toFile();
			File newFile = new File(newPath + File.separator + file.getName());
			if (!newFile.exists() && !file.getParent().equals(newPath)) {
				createDirectory(newPath);
				if (file.renameTo(newFile)) {
					fileIndex.put(fileName, newFile.toPath());
					logger.info("Moved: \n\"{}\" \n-> \"{}\"", file.getAbsolutePath(), newFile.getAbsolutePath());
					return true;
				} else {
					logger.error("Failed to move file: \n\"{}\"", file.getAbsolutePath());
				}
			}
		}
		return false;
	}

	public int numberOfFilesWithExtension(String... extensions) {
		int n = 0;
		for (Map.Entry<String, Path> entry : fileIndex.entrySet()) {
			String fileName = entry.getKey();
			if (hasExtension(fileName, extensions))
				n++;
		}
		return n;
	}

	public boolean moveFilesByKeyword(String keyword, String targetPath) {
		boolean movedFlag = false;
		for (Map.Entry<String, Path> entry : fileIndex.entrySet()) {
			String fileName = entry.getKey();
			Path filePath = entry.getValue();

			if (fileName.contains(keyword)) {
				File file = filePath.toFile();
				String currentPath = file.getParent();
				if (!currentPath.equals(targetPath)) {
					createDirectory(targetPath);
					File newFile = new File(targetPath + File.separator + file.getName());
					if (file.renameTo(newFile)) {
						// Update the file index with the new path
						fileIndex.put(fileName, newFile.toPath());
						movedFlag = true;
						logger.info("Moved: \n\"{}\" \n-> \"{}\"", file.getAbsolutePath(), newFile.getAbsolutePath());
					} else {
						logger.error("Failed to move file: \n\"{}\"", fileName);
					}
				}
			}
		}

		return movedFlag;
	}

	public boolean containsFileName(String fileName, String... ignoreExt) {
		for (Map.Entry<String, Path> entry : fileIndex.entrySet()) {
			if (entry.getKey().contains(fileName) && !hasExtension(entry.getKey(), ignoreExt))
				return true;
		}
		return false;
	}

	public String getPath(String fileName) {
		Path path = fileIndex.get(fileName);
		return path == null ? null : path.toString();
	}

	public boolean hasExtension(String fileName, String... extensions) {
		for (String ext : extensions) {
			if (fileName.endsWith(ext))
				return true;

		}
		return false;
	}

	public static void createDirectory(String path) {
		if (path != null) {
			File directory = new File(path);
			if (!directory.exists()) {
				directory.mkdirs();
			}
		}
	}

	public Map<String, Path> getFileIndex() {
		return fileIndex;
	}

}
