package com.travel.itinerary.api.port.outbound;

import java.io.InputStream;
import java.time.Duration;

/**
 * Outbound SPI: binary object storage operations.
 * <p>
 * Implementations may delegate to MinIO, AWS S3, Google Cloud Storage, or any
 * compatible object-store API.
 */
public interface FileStoragePort {

    /**
     * Upload a file to the object store.
     *
     * @param bucket      target bucket or container name; must not be {@code null}
     * @param objectKey   unique object key (path) within the bucket; must not be
     *                    {@code null}
     * @param inputStream stream of bytes to upload; must not be {@code null};
     *                    the caller is responsible for closing the stream
     * @param contentType MIME type of the content (e.g. {@code "application/json"});
     *                    must not be {@code null}
     * @throws FileStorageException if the upload fails
     */
    void upload(String bucket, String objectKey, InputStream inputStream, String contentType);

    /**
     * Download a file from the object store.
     *
     * @param bucket    source bucket or container name; must not be {@code null}
     * @param objectKey object key to retrieve; must not be {@code null}
     * @return an {@link InputStream} of the object's bytes; the caller must
     *         close the stream when finished
     * @throws FileStorageException if the object is not found or the download fails
     */
    InputStream download(String bucket, String objectKey);

    /**
     * Permanently delete an object from the store.
     *
     * @param bucket    bucket containing the object; must not be {@code null}
     * @param objectKey key of the object to delete; must not be {@code null}
     * @throws FileStorageException if the deletion fails
     */
    void delete(String bucket, String objectKey);

    /**
     * Generate a pre-signed URL that grants temporary read access to an object.
     *
     * @param bucket    bucket containing the object; must not be {@code null}
     * @param objectKey key of the object; must not be {@code null}
     * @param ttl       duration for which the URL remains valid; must be positive
     *                  and not {@code null}
     * @return pre-signed URL string; never {@code null}
     * @throws FileStorageException if the URL cannot be generated
     */
    String getPresignedUrl(String bucket, String objectKey, Duration ttl);

    /**
     * Unchecked exception thrown when a file storage operation cannot be completed.
     */
    class FileStorageException extends RuntimeException {

        public FileStorageException(String message) {
            super(message);
        }

        public FileStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
