package com.coworking.reservations.dto.request;

import com.coworking.reservations.domain.enums.SpaceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SpaceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull SpaceType type,
        @NotNull @Min(1) Integer capacity,
        @NotBlank @Size(max = 150) String location,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal hourlyRate) {
}
