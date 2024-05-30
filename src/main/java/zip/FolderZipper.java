package zip;

import java.io.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import config.ConfigManager;
import fileops.FileOperations;

public class FolderZipper {
	private FolderZipper() {
	}

	public static void zip(String sourceDir, boolean includeParentFolder) {
		if (!ConfigManager.getBooleanProperty("Package_Files"))
			return;

		File source = new File(sourceDir);
		String packagedDir = ConfigManager.getProperty("Package_Dir");
		if (packagedDir == null || packagedDir.isBlank())
			packagedDir = "Packaged";

		FileOperations.createDirectory(packagedDir);

		System.out.println("Packaging files...");
		if (source.exists() && source.isDirectory()) {
			File[] subDirs = source.listFiles(File::isDirectory);
			if (subDirs != null) {
				ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
				for (File folder : subDirs) {
					if (containsNonIgnoredFiles(folder, ".acb", ".awb")) {
						String outputDir = packagedDir;
						executor.submit(() -> {
							try {
								zipFolder(folder, outputDir, includeParentFolder, ".acb", ".awb");
							} catch (IOException e) {
								System.err.println("Error zipping folder: " + folder.getName());
								e.printStackTrace();
							}
						});
					}
				}
				executor.shutdown();
				try {
					executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
				} catch (InterruptedException e) {
					System.err.println("Executor was interrupted");
					e.printStackTrace();
				}
			}
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


	private static void zipFolder(File folder, String packagedDir, boolean includeParentFolder, String... ignoredExtensions) throws IOException {
		String zipFileName = packagedDir + File.separator + folder.getName() + ".zip";
		try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFileName))) {
			String basePath = includeParentFolder ? folder.getName() : "";
			zipFolderHelper(folder, basePath, zipOut, ignoredExtensions);
		}
	}

	private static void zipFolderHelper(File folder, String basePath, ZipOutputStream zipOut, String... ignoredExtensions) throws IOException {
		File[] files = folder.listFiles();
		if (files != null) {
			for (File file : files) {
				String zipEntryName = basePath.isEmpty() ? file.getName() : basePath + File.separator + file.getName();
				if (file.isDirectory()) {
					zipFolderHelper(file, zipEntryName, zipOut, ignoredExtensions);
				} else if (!isIgnoredExtension(file.getName(), ignoredExtensions)) {
					try (FileInputStream fis = new FileInputStream(file)) {
						ZipEntry zipEntry = new ZipEntry(zipEntryName);
						zipEntry.setMethod(ZipEntry.DEFLATED);

						zipOut.putNextEntry(zipEntry);

						byte[] buffer = new byte[1024];
						int length;
						while ((length = fis.read(buffer)) >= 0) {
							zipOut.write(buffer, 0, length);
						}
					}
				}
			}
		}
	}

}