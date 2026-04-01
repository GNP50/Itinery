package com.travel.file.controller;

import com.travel.file.service.FileManagerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * REST controller for file management operations.
 * Provides HTTP endpoints for uploading, downloading, and managing files.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileRestController {

    private static final Logger logger = LoggerFactory.getLogger(FileRestController.class);

    private final FileManagerService fileManagerService;

    @Autowired
    public FileRestController(FileManagerService fileManagerService) {
        this.fileManagerService = fileManagerService;
    }

    /**
     * Upload a file.
     *
     * @param file the uploaded file
     * @return the object name and upload status
     */
    @PostMapping("/upload")
    @Operation(summary = "Upload a file", description = "Uploads a file to the storage backend")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File uploaded successfully",
                    content = @Content(schema = @Schema(implementation = UploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Storage error")
    })
    public ResponseEntity<UploadResponse> uploadFile(
            @Parameter(description = "The file to upload") @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            logger.warn("Upload attempted with empty file");
            return ResponseEntity.badRequest().body(new UploadResponse(null, "File is empty"));
        }

        try {
            String objectName = file.getOriginalFilename();
            if (objectName == null || objectName.isEmpty()) {
                objectName = "files/" + java.util.UUID.randomUUID() + "." + getExtension(file.getOriginalFilename());
            }

            String contentType = file.getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            String etag = fileManagerService.upload(
                    objectName,
                    file.getInputStream(),
                    file.getSize(),
                    contentType);

            logger.info("File uploaded successfully: {}, size: {}, etag: {}", objectName, file.getSize(), etag);

            return ResponseEntity.ok(new UploadResponse(objectName, "File uploaded successfully"));
        } catch (Exception e) {
            logger.error("Failed to upload file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponse(null, "Upload failed: " + e.getMessage()));
        }
    }

    /**
     * Download a file by object name.
     *
     * @param objectName the name/path of the object in storage
     * @return the file content as a streaming response
     */
    @GetMapping("/{objectName}")
    @Operation(summary = "Download a file", description = "Downloads a file from the storage backend")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File downloaded successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
            @ApiResponse(responseCode = "404", description = "File not found"),
            @ApiResponse(responseCode = "500", description = "Storage error")
    })
    public ResponseEntity<byte[]> downloadFile(
            @Parameter(description = "The name/path of the object in storage") @PathVariable String objectName) {

        try (InputStream inputStream = fileManagerService.download(objectName)) {
            byte[] content = inputStream.readAllBytes();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", URLEncoder.encode(objectName, StandardCharsets.UTF_8));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(content);
        } catch (Exception e) {
            logger.error("Failed to download file: {}", objectName, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a file by object name.
     *
     * @param objectName the name/path of the object in storage
     * @return deletion status
     */
    @DeleteMapping("/{objectName}")
    @Operation(summary = "Delete a file", description = "Deletes a file from the storage backend")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File deleted successfully"),
            @ApiResponse(responseCode = "404", description = "File not found"),
            @ApiResponse(responseCode = "500", description = "Storage error")
    })
    public ResponseEntity<DeleteResponse> deleteFile(
            @Parameter(description = "The name/path of the object in storage") @PathVariable String objectName) {

        try {
            fileManagerService.delete(objectName);
            logger.info("File deleted successfully: {}", objectName);

            return ResponseEntity.ok(new DeleteResponse(objectName, "File deleted successfully"));
        } catch (Exception e) {
            logger.error("Failed to delete file: {}", objectName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DeleteResponse(objectName, "Delete failed: " + e.getMessage()));
        }
    }

    /**
     * Generate a presigned URL for a file.
     *
     * @param objectName the name/path of the object in storage
     * @param expirySeconds the number of seconds until the URL expires (default: 900 = 15 minutes)
     * @return the presigned URL
     */
    @GetMapping("/{objectName}/url")
    @Operation(summary = "Generate presigned URL", description = "Generates a time-limited URL for file access")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL generated successfully",
                    content = @Content(schema = @Schema(implementation = PresignedUrlResponse.class))),
            @ApiResponse(responseCode = "404", description = "File not found"),
            @ApiResponse(responseCode = "500", description = "Storage error")
    })
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @Parameter(description = "The name/path of the object in storage") @PathVariable String objectName,
            @Parameter(description = "URL expiry time in seconds (default: 900)") @RequestParam(value = "expiry", defaultValue = "900") int expirySeconds) {

        try {
            String url = fileManagerService.getPresignedUrl(objectName, expirySeconds);

            logger.debug("Presigned URL generated for: {}", objectName);

            return ResponseEntity.ok(new PresignedUrlResponse(url, expirySeconds));
        } catch (Exception e) {
            logger.error("Failed to generate presigned URL for: {}", objectName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PresignedUrlResponse(null, "URL generation failed: " + e.getMessage()));
        }
    }

    /**
     * List all files in storage.
     * Note: This is a stub implementation that returns an empty list.
     *
     * @return list of file metadata
     */
    @GetMapping("/")
    @Operation(summary = "List files", description = "Lists all files in storage")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of files retrieved successfully",
                    content = @Content(schema = @Schema(implementation = FileListResponse.class)))
    })
    public ResponseEntity<FileListResponse> listFiles() {
        // This would require additional API in FileManagerService to list files
        // For now, returning empty list as stub
        return ResponseEntity.ok(new FileListResponse(java.util.Collections.emptyList()));
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    // Response DTOs

    public static class UploadResponse {
        private final String objectName;
        private final String message;

        public UploadResponse(String objectName, String message) {
            this.objectName = objectName;
            this.message = message;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class DeleteResponse {
        private final String objectName;
        private final String message;

        public DeleteResponse(String objectName, String message) {
            this.objectName = objectName;
            this.message = message;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class PresignedUrlResponse {
        private final String url;
        private final int expirySeconds;

        public PresignedUrlResponse(String url, String message) {
            this.url = url;
            this.expirySeconds = 0;
        }

        public PresignedUrlResponse(String url, int expirySeconds) {
            this.url = url;
            this.expirySeconds = expirySeconds;
        }

        public String getUrl() {
            return url;
        }

        public int getExpirySeconds() {
            return expirySeconds;
        }
    }

    public static class FileListResponse {
        private final java.util.List<String> files;

        public FileListResponse(java.util.List<String> files) {
            this.files = files;
        }

        public java.util.List<String> getFiles() {
            return files;
        }
    }
}
