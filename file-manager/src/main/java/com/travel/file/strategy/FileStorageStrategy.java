package com.travel.file.strategy;

import java.io.InputStream;

/**
 * Strategy interface for file storage operations.
 * Implementations provide different storage backends (S3, Local, Azure, etc.).
 */
public interface FileStorageStrategy {

    /**
     * Uploads a file to the storage backend.
     *
     * @param objectName      the name/path of the object in storage
     * @param data            the input stream containing file data
     * @param size            the size of the file in bytes
     * @param contentType     the MIME type of the file
     * @return                the identifier (e.g., ETag) for the uploaded object
     * @throws FileStorageException if the upload fails
     */
    String upload(String objectName, InputStream data, long size, String contentType) throws FileStorageException;

    /**
     * Downloads a file from the storage backend.
     *
     * @param objectName the name/path of the object in storage
     * @return           an InputStream containing the file data
     * @throws FileStorageException if the download fails
     */
    InputStream download(String objectName) throws FileStorageException;

    /**
     * Deletes a file from the storage backend.
     *
     * @param objectName the name/path of the object in storage
     * @throws FileStorageException if the deletion fails
     */
    void delete(String objectName) throws FileStorageException;

    /**
     * Generates a presigned URL for accessing the file.
     *
     * @param objectName      the name/path of the object in storage
     * @param expirySeconds   the number of seconds until the URL expires
     * @return                the presigned URL string
     * @throws FileStorageException if URL generation fails
     */
    String getPresignedUrl(String objectName, int expirySeconds) throws FileStorageException;
}
