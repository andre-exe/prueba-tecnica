package com.coworking.reservations.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.report")
public record ReportCacheProperties(
        @NotNull Duration ttl,
        @Min(1) long maxSize,
        @Min(1) int maxRangeDays) {
}
