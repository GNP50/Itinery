package com.travel.file.grpc;

import com.travel.file.grpc.v1.*;
import com.travel.file.service.FileManagerService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * gRPC service implementation for file management operations.
 * Supports client-streaming upload, server-streaming download, and other file operations.
 */
@GrpcService
public class FileGrpcServiceImpl extends FileServiceGrpc.FileServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(FileGrpcServiceImpl.class);

    private final FileManagerService fileManagerService;

    @Autowired
    public FileGrpcServiceImpl(FileManagerService fileManagerService) {
        this.fileManagerService = fileManagerService;
    }

    @Override
    public StreamObserver<FileUploadRequest> uploadFile(StreamObserver<FileUploadResponse> responseObserver) {
        final List<byte[]> chunkDataList = new ArrayList<>();
        final UploadMetadata[] metadataHolder = new UploadMetadata[1];
        final ByteArrayOutputStream combinedStream = new ByteArrayOutputStream();

        return new StreamObserver<FileUploadRequest>() {
            @Override
            public void onNext(FileUploadRequest request) {
                if (request.hasMetadata()) {
                    metadataHolder[0] = request.getMetadata();
                }
                if (!request.getChunkData().isEmpty()) {
                    try {
                        combinedStream.write(request.getChunkData().toByteArray());
                    } catch (IOException e) {
                        onError(Status.INTERNAL.withDescription("Failed to accumulate chunks: " + e.getMessage())
                                .asRuntimeException());
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Upload file stream error", t);
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                try {
                    byte[] fileData = combinedStream.toByteArray();
                    ByteArrayInputStream dataStream = new ByteArrayInputStream(fileData);

                    UploadMetadata metadata = metadataHolder[0];
                    if (metadata == null) {
                        metadata = UploadMetadata.getDefaultInstance();
                    }

                    String objectName = metadata.getObjectKey();
                    if (objectName.isEmpty()) {
                        objectName = "files/" + java.util.UUID.randomUUID() + ".bin";
                    }

                    String contentType = metadata.getContentType();
                    if (contentType.isEmpty()) {
                        contentType = "application/octet-stream";
                    }

                    String etag = fileManagerService.upload(
                            objectName,
                            dataStream,
                            fileData.length,
                            contentType);

                    FileMetadata fileMetadata = FileMetadata.newBuilder()
                            .setObjectKey(objectName)
                            .setFileName(metadata.getFileName())
                            .setContentType(contentType)
                            .setSizeBytes(fileData.length)
                            .setEtag(etag)
                            .build();

                    String presignedUrl = fileManagerService.getPresignedUrl(objectName, 900);

                    FileUploadResponse response = FileUploadResponse.newBuilder()
                            .setMetadata(fileMetadata)
                            .setAccessUrl(presignedUrl)
                            .build();

                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    logger.error("Upload file failed", e);
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("Upload failed: " + e.getMessage())
                            .asRuntimeException());
                } finally {
                    try {
                        combinedStream.close();
                    } catch (IOException e) {
                        logger.warn("Failed to close combined stream", e);
                    }
                }
            }
        };
    }

    @Override
    public void downloadFile(DownloadFileRequest request, StreamObserver<DownloadFileResponse> responseObserver) {
        String objectName = request.getObjectKey();

        try {
            // First, send metadata
            FileMetadata metadata = FileMetadata.newBuilder()
                    .setObjectKey(objectName)
                    .build();

            responseObserver.onNext(DownloadFileResponse.newBuilder()
                    .setMetadata(metadata)
                    .build());

            // Stream file content in chunks
            try (InputStream inputStream = fileManagerService.download(objectName)) {
                byte[] buffer = new byte[64 * 1024]; // 64KB chunks
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    responseObserver.onNext(DownloadFileResponse.newBuilder()
                            .setChunkData(com.google.protobuf.ByteString.copyFrom(buffer, 0, bytesRead))
                            .build());
                }
            }

            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Download file failed: {}", objectName, e);
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("File not found: " + objectName)
                    .asRuntimeException());
        }
    }

    @Override
    public void getFileMetadata(GetFileMetadataRequest request, StreamObserver<FileMetadata> responseObserver) {
        String objectName = request.getObjectKey();

        try {
            // For now, we can only verify file exists by attempting to download metadata
            // A full implementation would query storage backend metadata directly
            fileManagerService.download(objectName).close();

            FileMetadata metadata = FileMetadata.newBuilder()
                    .setObjectKey(objectName)
                    .build();

            responseObserver.onNext(metadata);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Get file metadata failed: {}", objectName, e);
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("File not found: " + objectName)
                    .asRuntimeException());
        }
    }

    @Override
    public void listFiles(ListFilesRequest request, StreamObserver<ListFilesResponse> responseObserver) {
        // This would require additional API in FileManagerService to list files
        // For now, returning empty list as stub
        ListFilesResponse response = ListFilesResponse.newBuilder()
                .setTruncated(false)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void deleteFile(DeleteFileRequest request, StreamObserver<com.google.protobuf.Empty> responseObserver) {
        String objectName = request.getObjectKey();

        try {
            fileManagerService.delete(objectName);

            responseObserver.onNext(com.google.protobuf.Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Delete file failed: {}", objectName, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Delete failed: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void generatePresignedUrl(GeneratePresignedUrlRequest request, StreamObserver<GeneratePresignedUrlResponse> responseObserver) {
        String objectName = request.getObjectKey();
        int expirySeconds = request.getExpiresInSeconds();

        try {
            String url = fileManagerService.getPresignedUrl(objectName, expirySeconds);

            GeneratePresignedUrlResponse response = GeneratePresignedUrlResponse.newBuilder()
                    .setUrl(url)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Generate presigned URL failed: {}", objectName, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("URL generation failed: " + e.getMessage())
                    .asRuntimeException());
        }
    }
}
