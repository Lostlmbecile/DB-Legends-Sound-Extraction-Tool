package zip;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import config.ConfigManager;
import fileops.FileOperations;

public class FolderZipper {
	private static final Logger logger = LogManager.getLogger(FolderZipper.class);

	private FolderZipper() {
	}

	public static void zip(String sourceDir, String packagedDir) {
		File source = new File(sourceDir);

		if (source.exists() && source.isDirectory()) {
			File[] subDirs = source.listFiles(File::isDirectory);
			if (subDirs != null) {
				Set<File> directories = new HashSet<>(Arrays.asList(subDirs));
				zipDirectories(directories, packagedDir);
			}
		}
	}

	public static void zipDirectories(Set<File> directories, String packagedDir) {
		if (!ConfigManager.getBooleanProperty("Package_Files"))
			return;

		boolean includeParentFolder = ConfigManager.getBooleanProperty("Inlcude_Parent_Folder_In_Package");

		FileOperations.createDirectory(packagedDir);

		System.out.println("Packaging directories...");
		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		for (File folder : directories) {
			if (folder.exists() && folder.isDirectory() && containsNonIgnoredFiles(folder, ".acb", ".awb")) {
				executor.submit(() -> {
					try {
						zipFolder(folder, packagedDir, includeParentFolder, ".acb", ".awb");
					} catch (IOException e) {
						logger.error("Error zipping folder: \n\"{}\"", folder.getName(), e);
					}
				});
			}
		}
		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			logger.error("Executor was interrupted", e);
			Thread.currentThread().interrupt();
		}
	}

	private static boolean containsNonIgnoredFiles(File folder, String... ignoredExtensions) {
		File[] files = folder.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					if (containsNonIgnoredFiles(file, ignoredExtensions)) {
						return true;
					}
				} else if (!isIgnoredExtension(file.getName(), ignoredExtensions)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isIgnoredExtension(String fileName, String... ignoredExtensions) {
		for (String ext : ignoredExtensions) {
			if (fileName.endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	private static void zipFolder(File folder, String packagedDir, boolean includeParentFolder,
			String... ignoredExtensions) throws IOException {
		String zipFileName = packagedDir + File.separator + folder.getName() + ".zip";
		try (ZipOutputStream zipOut = new ZipOutputStream(
				new BufferedOutputStream(new FileOutputStream(zipFileName)))) {
			String basePath = includeParentFolder ? folder.getName() : "";
			zipFolderHelper(folder, basePath, zipOut, ignoredExtensions);
		}
	}

	private static void zipFolderHelper(File folder, String basePath, ZipOutputStream zipOut,
			String... ignoredExtensions) throws IOException {
		File[] files = folder.listFiles();
		if (files != null) {
			for (File file : files) {
				String zipEntryName = basePath.isEmpty() ? file.getName() : basePath + File.separator + file.getName();
				if (file.isDirectory()) {
					zipFolderHelper(file, zipEntryName, zipOut, ignoredExtensions);
				} else if (!isIgnoredExtension(file.getName(), ignoredExtensions)) {
					try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
						ZipEntry zipEntry = new ZipEntry(zipEntryName);
						zipEntry.setMethod(ZipEntry.DEFLATED);

						zipOut.putNextEntry(zipEntry);

						byte[] buffer = new byte[1024];
						int length;
						while ((length = bis.read(buffer)) >= 0) {
							zipOut.write(buffer, 0, length);
						}
					}
				}
			}
		}
	}
}
