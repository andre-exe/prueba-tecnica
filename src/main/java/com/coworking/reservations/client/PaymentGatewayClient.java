package com.coworking.reservations.client;

import com.coworking.reservations.client.dto.PaymentValidationRequest;
import com.coworking.reservations.client.dto.PaymentValidationResponse;
import com.coworking.reservations.exception.PaymentDeclinedException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class PaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayClient.class);

    private final RestClient restClient;

    public PaymentGatewayClient(RestClient paymentRestClient) {
        this.restClient = paymentRestClient;
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "validateFallback")
    public PaymentResult validate(PaymentValidationRequest request) {
        PaymentValidationResponse response = restClient.post()
                .uri("/api/payments/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PaymentValidationResponse.class);

        if (response == null) {
            throw new IllegalStateException("El proveedor de pago respondio vacio");
        }
        // un rechazo es una respuesta de negocio, no una falla del proveedor, por eso el breaker lo ignora
        if (!response.approved()) {
            throw new PaymentDeclinedException(response.reason());
        }
        return PaymentResult.approved(response.transactionId());
    }

    public PaymentResult validateFallback(PaymentValidationRequest request, Throwable cause) {
        if (cause instanceof PaymentDeclinedException declined) {
            throw declined;
        }
        log.warn("pago no disponible para la reserva {} ({}): {}", request.reservationId(), describe(cause), cause.toString());
        return PaymentResult.unavailable();
    }

    private String describe(Throwable cause) {
        if (cause instanceof CallNotPermittedException) {
            return "circuito abierto";
        }
        if (cause instanceof ResourceAccessException) {
            return "timeout o conexion caida";
        }
        if (cause instanceof HttpServerErrorException) {
            return "error 5xx del proveedor";
        }
        return "error inesperado";
    }
}
