package com.coworking.reservations.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @CsvSource({
            "not-found, 404, RESOURCE_NOT_FOUND",
            "invalid-request, 422, INVALID_RESERVATION_REQUEST",
            "declined, 402, PAYMENT_DECLINED",
            "overlap, 409, RESERVATION_OVERLAP",
            "invalid-state, 409, INVALID_RESERVATION_STATE",
            "email, 409, EMAIL_ALREADY_EXISTS",
            "space-name, 409, DUPLICATE_SPACE_NAME",
            "bad-credentials, 401, BAD_CREDENTIALS",
            "access-denied, 403, ACCESS_DENIED",
            "optimistic-lock, 409, CONCURRENT_UPDATE",
            "exclusion, 409, RESERVATION_OVERLAP",
            "unique, 409, DUPLICATE_RESOURCE",
            "no-resource, 404, NOT_FOUND",
            "bad-sort, 400, INVALID_SORT",
            "other-integrity, 500, INTERNAL_ERROR",
            "no-sqlstate, 500, INTERNAL_ERROR",
            "constraint, 400, VALIDATION_ERROR"
    })
    void mapsExceptionsToProblemDetail(String path, int expectedStatus, String expectedCode) throws Exception {
        mockMvc.perform(get("/fail/" + path))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.instance").value("/fail/" + path))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void beanValidationReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void validationOfASingleParameterReturnsItsFieldError() throws Exception {
        mockMvc.perform(get("/limit?size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void aMissingRequiredParameterReturns400() throws Exception {
        mockMvc.perform(get("/limit")).andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void wrongPathVariableTypeReturns400() throws Exception {
        mockMvc.perform(get("/items/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedErrorDoesNotLeakDetails() throws Exception {
        mockMvc.perform(get("/fail/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secreto interno"))));
    }

    @RestController
    static class FakeController {

        @GetMapping("/fail/{kind}")
        void fail(@PathVariable String kind) throws Exception {
            switch (kind) {
                case "not-found" -> throw new ResourceNotFoundException("no existe");
                case "invalid-request" -> throw new InvalidReservationRequestException("fechas invalidas");
                case "declined" -> throw new PaymentDeclinedException("INSUFFICIENT_FUNDS");
                case "overlap" -> throw new OverlappingReservationException();
                case "invalid-state" -> throw new InvalidReservationStateException("no se puede");
                case "email" -> throw new EmailAlreadyExistsException("a@a.com");
                case "space-name" -> throw new DuplicateSpaceNameException("Sala 1");
                case "bad-credentials" -> throw new BadCredentialsException("x");
                case "access-denied" -> throw new AccessDeniedException("x");
                case "optimistic-lock" -> throw new OptimisticLockingFailureException("x");
                case "exclusion" -> throw new DataIntegrityViolationException("x", new SQLException("x", "23P01"));
                case "no-resource" -> throw new org.springframework.web.servlet.resource.NoResourceFoundException(org.springframework.http.HttpMethod.GET, "nada");
                case "bad-sort" -> throw new org.springframework.data.mapping.PropertyReferenceException("noexiste", org.springframework.data.util.TypeInformation.of(String.class), java.util.List.of());
                case "other-integrity" -> throw new DataIntegrityViolationException("x", new SQLException("x", "23503"));
                case "no-sqlstate" -> throw new DataIntegrityViolationException("x");
                case "constraint" -> throw new jakarta.validation.ConstraintViolationException(
                        jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator().validate(new ItemRequest("", -1)));
                case "unique" -> throw new DataIntegrityViolationException("x", new SQLException("x", "23505"));
                default -> throw new IllegalStateException("secreto interno");
            }
        }

        @PostMapping("/items")
        void create(@Valid @RequestBody ItemRequest request) {
        }

        @GetMapping("/limit")
        void limit(@org.springframework.web.bind.annotation.RequestParam @jakarta.validation.constraints.Min(1) int size) {
        }

        @GetMapping("/items/{id}")
        void find(@PathVariable Integer id) {
        }
    }

    record ItemRequest(@NotBlank String name, @Positive int quantity) {
    }
}
