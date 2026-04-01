package com.travel.file.strategy;

import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * S3-compatible storage strategy using MinIO SDK.
 * Implements FileStorageStrategy for S3-compatible storage backends.
 */
public class S3StorageStrategy implements FileStorageStrategy {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageStrategy.class);

    private final MinioClient minioClient;
    private final String bucketName;

    public S3StorageStrategy(MinioClient minioClient, String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    @Override
    public String upload(String objectName, InputStream data, long size, String contentType) throws FileStorageException {
        try {
            logger.debug("Uploading object: {} with size: {} bytes", objectName, size);

            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build();

            String etag = minioClient.putObject(putObjectArgs).etag();

            logger.debug("Successfully uploaded object: {}, ETag: {}", objectName, etag);
            return etag;
        } catch (Exception e) {
            logger.error("Error while uploading {}: {}", objectName, e.getMessage(), e);
            throw new FileStorageException("Upload failed: " + e.getMessage(), e, "STORAGE_ERROR");
        }
    }

    @Override
    public InputStream download(String objectName) throws FileStorageException {
        try {
            logger.debug("Downloading object: {}", objectName);

            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();

            return minioClient.getObject(getObjectArgs);
        } catch (Exception e) {
            logger.error("Error while downloading {}: {}", objectName, e.getMessage(), e);
            throw new FileStorageException("Download failed: " + e.getMessage(), e, "STORAGE_ERROR");
        }
    }

    @Override
    public void delete(String objectName) throws FileStorageException {
        try {
            logger.debug("Deleting object: {}", objectName);

            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();

            minioClient.removeObject(removeObjectArgs);
            logger.debug("Successfully deleted object: {}", objectName);
        } catch (Exception e) {
            logger.error("Error while deleting {}: {}", objectName, e.getMessage(), e);
            throw new FileStorageException("Delete failed: " + e.getMessage(), e, "STORAGE_ERROR");
        }
    }

    @Override
    public String getPresignedUrl(String objectName, int expirySeconds) throws FileStorageException {
        try {
            logger.debug("Generating presigned URL for object: {} with expiry: {} seconds",
                    objectName, expirySeconds);

            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());

            logger.debug("Generated presigned URL for object: {}", objectName);
            return url;
        } catch (Exception e) {
            logger.error("Error while generating presigned URL for {}: {}", objectName, e.getMessage(), e);
            throw new FileStorageException("URL generation failed: " + e.getMessage(), e, "STORAGE_ERROR");
        }
    }

    /**
     * Gets the bucket name used by this strategy.
     *
     * @return the bucket name
     */
    public String getBucketName() {
        return bucketName;
    }
}
