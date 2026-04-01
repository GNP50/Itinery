package com.travel.config.client;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

/**
 * Spring Boot {@link AutoConfiguration} for the gRPC-based configuration client.
 *
 * <h3>Activation</h3>
 * <p>The entire auto-configuration is guarded by:
 * <pre>
 * config.client.enabled=true   # default
 * </pre>
 * Setting the property to {@code false} (or leaving it absent when
 * {@code matchIfMissing=false}) prevents any beans from being registered.
 *
 * <h3>Bean registration order</h3>
 * <ol>
 *   <li>{@link ConfigClientProperties} – binds {@code config.client.*} properties.</li>
 *   <li>{@link ConfigGrpcClient} – opens the gRPC channel and warms the cache.</li>
 * </ol>
 *
 * <p>Both beans are annotated with {@link ConditionalOnMissingBean} so that
 * application modules can override them by declaring their own instances.
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "config.client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(ConfigClientProperties.class)
public class ConfigClientAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConfigClientAutoConfiguration.class);

    /**
     * Registers the {@link ConfigGrpcClient} bean.
     *
     * <p>An {@link Optional}{@code <MeterRegistry>} is injected so that the
     * starter remains usable in applications that do not include
     * {@code micrometer-core} or Actuator on the classpath – in which case no
     * metrics are recorded.
     *
     * @param properties    bound {@link ConfigClientProperties}
     * @param meterRegistry optional Micrometer registry
     * @return a fully initialised {@link ConfigGrpcClient}
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigGrpcClient configGrpcClient(
            ConfigClientProperties properties,
            Optional<MeterRegistry> meterRegistry) {

        log.info("Registering ConfigGrpcClient → {}:{} (namespace='{}', TTL={}s)",
                properties.getServerHost(),
                properties.getServerPort(),
                properties.getDefaultNamespace(),
                properties.getCacheTtlSeconds());

        return new ConfigGrpcClient(properties, meterRegistry);
    }
}
