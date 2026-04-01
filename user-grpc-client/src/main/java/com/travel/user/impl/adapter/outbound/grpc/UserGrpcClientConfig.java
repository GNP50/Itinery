package com.travel.user.impl.adapter.outbound.grpc;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration for User gRPC client adapter.
 * <p>
 * The actual gRPC client configuration (host, port, etc.) is managed
 * via application.yml properties:
 * <pre>
 * grpc:
 *   client:
 *     user-service:
 *       address: static://localhost:9091
 *       negotiationType: plaintext
 * </pre>
 */
@Configuration
public class UserGrpcClientConfig {
    // gRPC client is auto-configured by @GrpcClient annotation
    // and grpc-spring-boot-starter
}

