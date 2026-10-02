package com.coworking.reservations.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * reglas de negocio de las reservas y cron del job que las marca como COMPLETED.
 */
@Validated
@ConfigurationProperties(prefix = "app.reservation")
public record ReservationProperties(
        @NotNull Duration minDuration,
        @NotNull Duration maxDuration,
        @Min(1) int maxAdvanceDays,
        @NotBlank String completionJobCron) {
}
