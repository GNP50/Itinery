package com.travel.file.strategy;

import java.io.InputStream;

/**
 * Azure Blob Storage strategy - stub implementation.
 * This class provides a placeholder for Azure Blob Storage integration.
 * To implement Azure support, add the Azure SDK dependency and implement the strategy methods.
 *
 * <p>Required Azure SDK dependencies for full implementation:</p>
 * <pre>{@code
 * <dependency>
 *     <groupId>com.azure</groupId>
 *     <artifactId>azure-storage-blob</artifactId>
 *     <version>12.20.0</version>
 * </dependency>
 * }</pre>
 */
public class AzureStorageStrategy implements FileStorageStrategy {

    @Override
    public String upload(String objectName, InputStream data, long size, String contentType) throws FileStorageException {
        throw new UnsupportedOperationException("Azure not implemented");
    }

    @Override
    public InputStream download(String objectName) throws FileStorageException {
        throw new UnsupportedOperationException("Azure not implemented");
    }

    @Override
    public void delete(String objectName) throws FileStorageException {
        throw new UnsupportedOperationException("Azure not implemented");
    }

    @Override
    public String getPresignedUrl(String objectName, int expirySeconds) throws FileStorageException {
        throw new UnsupportedOperationException("Azure not implemented");
    }
}
