package com.travel.file.config;

import com.travel.file.service.FileManagerService;
import com.travel.file.strategy.*;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration class for file storage strategies and MinIO client.
 * Configures S3, local, and Azure storage strategies with appropriate beans.
 */
@Configuration
public class FileStorageConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${minio.access-key:minioadmin}")
    private String minioAccessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String minioSecretKey;

    @Value("${minio.bucket:travel-itinerary}")
    private String minioBucket;

    @Value("${file.storage.local-base-dir:/tmp/file-storage}")
    private String localBaseDir;

    /**
     * Creates and configures the MinIO client.
     *
     * @return configured MinioClient instance
     */
    @Bean
    public MinioClient minioClient() {
        try {
            return MinioClient.builder()
                    .endpoint(minioEndpoint)
                    .credentials(minioAccessKey, minioSecretKey)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO client", e);
        }
    }

    /**
     * Creates the S3 storage strategy bean (primary/default).
     *
     * @param minioClient the MinIO client instance
     * @return configured S3StorageStrategy instance
     */
    @Bean
    @Primary
    public FileStorageStrategy s3StorageStrategy(MinioClient minioClient) {
        return new S3StorageStrategy(minioClient, minioBucket);
    }

    /**
     * Creates the local storage strategy bean.
     *
     * @return configured LocalStorageStrategy instance
     */
    @Bean
    public FileStorageStrategy localStorageStrategy() {
        return new LocalStorageStrategy(localBaseDir);
    }

    /**
     * Creates the Azure storage strategy bean.
     *
     * @return configured AzureStorageStrategy instance (stub)
     */
    @Bean
    public FileStorageStrategy azureStorageStrategy() {
        return new AzureStorageStrategy();
    }

    /**
     * Creates the FileManagerService bean with the specified storage strategy.
     *
     * @param storageStrategy the storage strategy to use
     * @return configured FileManagerService instance
     */
    @Bean
    public FileManagerService fileManagerService(@Qualifier("s3StorageStrategy") FileStorageStrategy storageStrategy) {
        return new FileManagerService(storageStrategy);
    }
}
