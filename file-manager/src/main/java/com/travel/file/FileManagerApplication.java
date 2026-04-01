package com.travel.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main application class for the File Manager service.
 * Provides file storage capabilities using various strategies (S3, Local, Azure).
 */
@SpringBootApplication
@EnableCaching
public class FileManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileManagerApplication.class, args);
    }
}
