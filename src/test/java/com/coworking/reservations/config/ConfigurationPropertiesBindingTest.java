package com.coworking.reservations.config;

import com.coworking.reservations.config.properties.JwtProperties;
import com.coworking.reservations.config.properties.ReservationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPropertiesBindingTest {

    private final ApplicationContextRunner jwtRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class);

    private final ApplicationContextRunner reservationRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReservationConfig.class);

    @Test
    void bindsValidProperties() {
        jwtRunner.withPropertyValues(
                        "app.jwt.secret=0123456789abcdef0123456789abcdef",
                        "app.jwt.issuer=test",
                        "app.jwt.expiration=2h")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    JwtProperties jwt = ctx.getBean(JwtProperties.class);
                    assertThat(jwt.expiration()).isEqualTo(Duration.ofHours(2));
                });
    }

    @Test
    void rejectsShortJwtSecret() {
        jwtRunner.withPropertyValues(
                        "app.jwt.secret=too-short",
                        "app.jwt.issuer=test",
                        "app.jwt.expiration=1h")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void bindsReservationDurations() {
        reservationRunner.withPropertyValues(
                        "app.reservation.min-duration=30m",
                        "app.reservation.max-duration=8h",
                        "app.reservation.max-advance-days=90",
                        "app.reservation.completion-job-cron=0 */5 * * * *")
                .run(ctx -> {
                    ReservationProperties props = ctx.getBean(ReservationProperties.class);
                    assertThat(props.minDuration()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(props.maxDuration()).isEqualTo(Duration.ofHours(8));
                });
    }

    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtConfig {
    }

    @EnableConfigurationProperties(ReservationProperties.class)
    static class ReservationConfig {
    }
}
