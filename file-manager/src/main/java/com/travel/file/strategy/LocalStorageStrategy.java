package com.travel.file.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Local filesystem storage strategy.
 * Stores files on the local filesystem using a configurable base directory.
 */
public class LocalStorageStrategy implements FileStorageStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageStrategy.class);

    private final String baseDir;

    public LocalStorageStrategy(String baseDir) {
        if (baseDir == null || baseDir.trim().isEmpty()) {
            this.baseDir = "/tmp/file-storage";
        } else {
            this.baseDir = baseDir;
        }
        createBaseDirectory();
    }

    private void createBaseDirectory() {
        try {
            Path path = Paths.get(baseDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created local storage directory: {}", baseDir);
            }
        } catch (IOException e) {
            logger.error("Failed to create local storage directory: {}", baseDir, e);
            throw new RuntimeException("Failed to create local storage directory: " + baseDir, e);
        }
    }

    private Path getFilePath(String objectName) {
        // Sanitize objectName to prevent directory traversal
        String sanitized = objectName.replaceAll("\\.{2,}", "_")
                .replaceAll("[^a-zA-Z0-9_\\-./]", "_");
        return Paths.get(baseDir, sanitized);
    }

    @Override
    public String upload(String objectName, InputStream data, long size, String contentType) throws FileStorageException {
        Path filePath = getFilePath(objectName);

        try {
            // Create parent directories if they don't exist
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // Copy input stream to file
            Files.copy(data, filePath, StandardCopyOption.REPLACE_EXISTING);

            // Generate and return a checksum as identifier
            String checksum = generateChecksum(filePath);
            logger.debug("Successfully uploaded file to: {}, checksum: {}", filePath, checksum);
            return checksum;
        } catch (IOException e) {
            logger.error("Failed to upload file to: {}", filePath, e);
            throw new FileStorageException(
                    "Failed to upload file: " + e.getMessage(), "UPLOAD_FAILED");
        }
    }

    @Override
    public InputStream download(String objectName) throws FileStorageException {
        Path filePath = getFilePath(objectName);

        try {
            if (!Files.exists(filePath)) {
                logger.warn("File not found: {}", filePath);
                throw new FileStorageException(
                        "File not found: " + objectName, "FILE_NOT_FOUND");
            }

            InputStream inputStream = Files.newInputStream(filePath);
            logger.debug("Successfully opened file for download: {}", filePath);
            return inputStream;
        } catch (IOException e) {
            logger.error("Failed to download file: {}", filePath, e);
            throw new FileStorageException(
                    "Failed to download file: " + e.getMessage(), "DOWNLOAD_FAILED");
        }
    }

    @Override
    public void delete(String objectName) throws FileStorageException {
        Path filePath = getFilePath(objectName);

        try {
            if (!Files.exists(filePath)) {
                logger.warn("File not found for deletion: {}", filePath);
                throw new FileStorageException(
                        "File not found: " + objectName, "FILE_NOT_FOUND");
            }

            Files.delete(filePath);
            logger.debug("Successfully deleted file: {}", filePath);
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", filePath, e);
            throw new FileStorageException(
                    "Failed to delete file: " + e.getMessage(), "DELETE_FAILED");
        }
    }

    @Override
    public String getPresignedUrl(String objectName, int expirySeconds) throws FileStorageException {
        Path filePath = getFilePath(objectName);

        try {
            if (!Files.exists(filePath)) {
                logger.warn("File not found for URL generation: {}", filePath);
                throw new FileStorageException(
                        "File not found: " + objectName, "FILE_NOT_FOUND");
            }

            // For local files, return a file:// URL
            // Note: Local file URLs don't actually support expiry like presigned URLs
            String url = "file://" + filePath.toUri().getRawPath();
            logger.debug("Generated local file URL for: {}", objectName);
            return url;
        } catch (Exception e) {
            logger.error("Failed to generate presigned URL for: {}", objectName, e);
            throw new FileStorageException(
                    "Failed to generate presigned URL: " + e.getMessage(), "URL_GENERATION_FAILED");
        }
    }

    /**
     * Lists all files in the storage directory.
     *
     * @return a stream of file paths relative to the base directory
     */
    public Stream<String> listFiles() throws FileStorageException {
        try {
            Path basePath = Paths.get(baseDir);
            if (!Files.exists(basePath)) {
                return Stream.empty();
            }
            return Files.walk(basePath)
                    .filter(Files::isRegularFile)
                    .map(basePath::relativize)
                    .map(Path::toString);
        } catch (IOException e) {
            logger.error("Failed to list files in: {}", baseDir, e);
            throw new FileStorageException(
                    "Failed to list files: " + e.getMessage(), "LIST_FAILED");
        }
    }

    /**
     * Gets the base directory path.
     *
     * @return the base directory path
     */
    public String getBaseDir() {
        return baseDir;
    }

    private String generateChecksum(Path filePath) throws IOException {
        // Simple checksum using file size and last modified time
        // In production, consider using a proper hash like SHA-256
        long size = Files.size(filePath);
        long lastModified = Files.getLastModifiedTime(filePath).toMillis();
        return "LOCAL-" + size + "-" + lastModified;
    }
}
