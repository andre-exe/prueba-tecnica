package com.coworking.reservations.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.async")
public record AsyncProperties(
        @Min(1) int corePoolSize,
        @Min(1) int maxPoolSize,
        @Min(0) int queueCapacity,
        @NotBlank String threadNamePrefix) {
}
