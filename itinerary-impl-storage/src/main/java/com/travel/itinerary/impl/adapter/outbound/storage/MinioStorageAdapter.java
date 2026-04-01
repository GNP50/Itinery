package com.travel.itinerary.impl.adapter.outbound.storage;

import com.travel.itinerary.api.port.outbound.FileStoragePort;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Outbound adapter that fulfils {@link FileStoragePort} by delegating all
 * object-storage operations to a MinIO instance via the official MinIO Java
 * client SDK.
 *
 * <p>The default bucket name is read from the {@code minio.bucket} property
 * (defaulting to {@code travel-itinerary}).  The caller may pass any bucket
 * name at runtime, however; this class does not restrict operations to the
 * configured default bucket.
 *
 * <p>All MinIO SDK exceptions are caught and re-thrown as
 * {@link FileStorageException} so that callers depend only on the port
 * abstraction and never on MinIO-specific exception types.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorageAdapter implements FileStoragePort {

    private final MinioClient minioClient;

    @Value("${minio.bucket:travel-itinerary}")
    private String defaultBucket;

    // -------------------------------------------------------------------------
    // FileStoragePort
    // -------------------------------------------------------------------------

    /**
     * Upload an object to the specified MinIO bucket.
     *
     * @param bucket      target bucket name; must not be {@code null}
     * @param objectKey   unique key (path) within the bucket; must not be {@code null}
     * @param inputStream stream of bytes to upload; must not be {@code null}
     * @param contentType MIME type, e.g. {@code "application/octet-stream"}
     * @throws FileStorageException on upload failure
     */
    @Override
    public void upload(String bucket, String objectKey,
                       InputStream inputStream, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, -1, 10_485_760) // unknown size, 10 MiB part size
                            .contentType(contentType)
                            .build());

            log.debug("Uploaded object bucket='{}' key='{}'", bucket, objectKey);

        } catch (Exception ex) {
            log.error("MinIO upload failed bucket='{}' key='{}': {}", bucket, objectKey, ex.getMessage());
            throw new FileStorageException(
                    "Failed to upload object '" + objectKey + "' to bucket '" + bucket + "'", ex);
        }
    }

    /**
     * Download an object from the specified MinIO bucket.
     *
     * <p>The returned {@link InputStream} is backed by the MinIO HTTP response;
     * the caller <strong>must</strong> close the stream after use to release the
     * underlying connection.
     *
     * @param bucket    source bucket name; must not be {@code null}
     * @param objectKey key of the object to retrieve; must not be {@code null}
     * @return open {@link InputStream} of the object's content
     * @throws FileStorageException if the object does not exist or download fails
     */
    @Override
    public InputStream download(String bucket, String objectKey) {
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());

            log.debug("Downloaded object bucket='{}' key='{}'", bucket, objectKey);
            return stream;

        } catch (Exception ex) {
            log.error("MinIO download failed bucket='{}' key='{}': {}", bucket, objectKey, ex.getMessage());
            throw new FileStorageException(
                    "Failed to download object '" + objectKey + "' from bucket '" + bucket + "'", ex);
        }
    }

    /**
     * Permanently delete an object from the specified MinIO bucket.
     *
     * <p>Deleting a non-existent key is a no-op (MinIO does not raise an error
     * in that case).
     *
     * @param bucket    bucket containing the object; must not be {@code null}
     * @param objectKey key of the object to delete; must not be {@code null}
     * @throws FileStorageException if deletion fails for any reason other than
     *                              a missing object
     */
    @Override
    public void delete(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());

            log.debug("Deleted object bucket='{}' key='{}'", bucket, objectKey);

        } catch (Exception ex) {
            log.error("MinIO delete failed bucket='{}' key='{}': {}", bucket, objectKey, ex.getMessage());
            throw new FileStorageException(
                    "Failed to delete object '" + objectKey + "' from bucket '" + bucket + "'", ex);
        }
    }

    /**
     * Generate a pre-signed URL granting temporary HTTP GET access to an object.
     *
     * @param bucket    bucket containing the object; must not be {@code null}
     * @param objectKey key of the object; must not be {@code null}
     * @param ttl       validity duration; must be positive and not {@code null}
     * @return pre-signed URL string; never {@code null}
     * @throws FileStorageException if URL generation fails
     */
    @Override
    public String getPresignedUrl(String bucket, String objectKey, Duration ttl) {
        try {
            int expirySeconds = (int) ttl.toSeconds();
            if (expirySeconds <= 0) {
                throw new IllegalArgumentException("TTL must be positive, got: " + ttl);
            }

            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());

            log.debug("Generated presigned URL for bucket='{}' key='{}' ttl={}s",
                    bucket, objectKey, expirySeconds);
            return url;

        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Exception ex) {
            log.error("MinIO presigned URL generation failed bucket='{}' key='{}': {}",
                    bucket, objectKey, ex.getMessage());
            throw new FileStorageException(
                    "Failed to generate presigned URL for '" + objectKey
                            + "' in bucket '" + bucket + "'", ex);
        }
    }
}
