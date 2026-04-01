package com.travel.file.strategy;

/**
 * Exception thrown when file storage operations fail.
 */
public class FileStorageException extends Exception {

    private final String errorCode;

    public FileStorageException(String message) {
        super(message);
        this.errorCode = "STORAGE_ERROR";
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "STORAGE_ERROR";
    }

    public FileStorageException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public FileStorageException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
