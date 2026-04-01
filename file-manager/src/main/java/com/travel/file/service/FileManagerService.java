package com.travel.file.service;

import com.travel.file.strategy.FileStorageException;
import com.travel.file.strategy.FileStorageStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Service class for managing file operations.
 * Delegates to the configured FileStorageStrategy for actual storage operations.
 */
@Service
public class FileManagerService {

    private static final Logger logger = LoggerFactory.getLogger(FileManagerService.class);

    private final FileStorageStrategy storageStrategy;

    /**
     * Constructs a new FileManagerService with the specified storage strategy.
     *
     * @param storageStrategy the storage strategy to use for file operations
     */
    @Autowired
    public FileManagerService(@Qualifier("s3StorageStrategy") FileStorageStrategy storageStrategy) {
        this.storageStrategy = storageStrategy;
    }

    /**
     * Uploads a file to the configured storage backend.
     *
     * @param objectName      the name/path of the object in storage
     * @param data            the input stream containing file data
     * @param size            the size of the file in bytes
     * @param contentType     the MIME type of the file
     * @return                the identifier (e.g., ETag) for the uploaded object
     * @throws FileStorageException if the upload fails
     */
    public String upload(String objectName, InputStream data, long size, String contentType) throws FileStorageException {
        logger.debug("FileManagerService.upload: objectName={}, size={}, contentType={}", objectName, size, contentType);
        return storageStrategy.upload(objectName, data, size, contentType);
    }

    /**
     * Downloads a file from the configured storage backend.
     *
     * @param objectName the name/path of the object in storage
     * @return           an InputStream containing the file data
     * @throws FileStorageException if the download fails
     */
    public InputStream download(String objectName) throws FileStorageException {
        logger.debug("FileManagerService.download: objectName={}", objectName);
        return storageStrategy.download(objectName);
    }

    /**
     * Deletes a file from the configured storage backend.
     *
     * @param objectName the name/path of the object in storage
     * @throws FileStorageException if the deletion fails
     */
    public void delete(String objectName) throws FileStorageException {
        logger.debug("FileManagerService.delete: objectName={}", objectName);
        storageStrategy.delete(objectName);
    }

    /**
     * Generates a presigned URL for accessing the file.
     *
     * @param objectName      the name/path of the object in storage
     * @param expirySeconds   the number of seconds until the URL expires
     * @return                the presigned URL string
     * @throws FileStorageException if URL generation fails
     */
    public String getPresignedUrl(String objectName, int expirySeconds) throws FileStorageException {
        logger.debug("FileManagerService.getPresignedUrl: objectName={}, expirySeconds={}", objectName, expirySeconds);
        return storageStrategy.getPresignedUrl(objectName, expirySeconds);
    }

    /**
     * Gets the storage strategy being used.
     *
     * @return the FileStorageStrategy instance
     */
    public FileStorageStrategy getStorageStrategy() {
        return storageStrategy;
    }
}
