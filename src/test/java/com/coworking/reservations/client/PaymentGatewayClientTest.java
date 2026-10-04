package com.coworking.reservations.client;

import com.coworking.reservations.client.dto.PaymentValidationRequest;
import com.coworking.reservations.exception.PaymentDeclinedException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentGatewayClientTest {

    private static final String URL = "http://payments.test/api/payments/validate";

    private MockRestServiceServer server;
    private PaymentGatewayClient client;
    private final PaymentValidationRequest request =
            new PaymentValidationRequest(UUID.randomUUID(), new BigDecimal("40.00"), "USD", "tok_ok");

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payments.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PaymentGatewayClient(builder.build());
    }

    @Test
    void approvedResponseBecomesApprovedResult() {
        server.expect(requestTo(URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.paymentMethod").value("tok_ok"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andRespond(withSuccess("{\"approved\":true,\"transactionId\":\"txn-1\",\"reason\":null}", MediaType.APPLICATION_JSON));

        PaymentResult result = client.validate(request);

        assertThat(result.isApproved()).isTrue();
        assertThat(result.transactionId()).isEqualTo("txn-1");
        server.verify();
    }

    @Test
    void declinedResponseThrowsPaymentDeclined() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"approved\":false,\"transactionId\":null,\"reason\":\"INSUFFICIENT_FUNDS\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.validate(request))
                .isInstanceOf(PaymentDeclinedException.class)
                .extracting(e -> ((PaymentDeclinedException) e).getReason())
                .isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void providerErrorSurfacesAsServerError() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.validate(request)).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void fallbackReturnsUnavailableWhenTheCircuitIsOpen() {
        CallNotPermittedException open = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("test"));

        assertThat(client.validateFallback(request, open).status()).isEqualTo(PaymentResult.Status.UNAVAILABLE);
    }

    @Test
    void fallbackReturnsUnavailableOnTimeoutsAndServerErrors() {
        assertThat(client.validateFallback(request, new ResourceAccessException("timeout")).status())
                .isEqualTo(PaymentResult.Status.UNAVAILABLE);
        assertThat(client.validateFallback(request, HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "x", null, null, null)).status())
                .isEqualTo(PaymentResult.Status.UNAVAILABLE);
    }

    @Test
    void fallbackNeverSwallowsADeclinedPayment() {
        PaymentDeclinedException declined = new PaymentDeclinedException("INSUFFICIENT_FUNDS");

        assertThatThrownBy(() -> client.validateFallback(request, declined)).isSameAs(declined);
    }
}
