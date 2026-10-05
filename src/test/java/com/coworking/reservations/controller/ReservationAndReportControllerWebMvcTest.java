package com.coworking.reservations.controller;

import com.coworking.reservations.config.SecurityConfig;
import com.coworking.reservations.config.properties.JwtProperties;
import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.dto.response.ConfirmReservationResponse;
import com.coworking.reservations.dto.response.OccupancyResponse;
import com.coworking.reservations.dto.response.ReservationResponse;
import com.coworking.reservations.exception.PaymentDeclinedException;
import com.coworking.reservations.security.CurrentUser;
import com.coworking.reservations.security.RestAccessDeniedHandler;
import com.coworking.reservations.security.RestAuthenticationEntryPoint;
import com.coworking.reservations.security.SecurityProblemWriter;
import com.coworking.reservations.service.OccupancyReportService;
import com.coworking.reservations.service.ReservationConfirmationService;
import com.coworking.reservations.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ReservationController.class, ReportController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityProblemWriter.class})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
class ReservationAndReportControllerWebMvcTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2030, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReservationService reservationService;
    @MockitoBean
    private ReservationConfirmationService confirmationService;
    @MockitoBean
    private OccupancyReportService occupancyReportService;

    private final UUID userId = UUID.randomUUID();

    private RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject(userId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(userId.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private ReservationResponse reservation(ReservationStatus status) {
        return new ReservationResponse(UUID.randomUUID(), UUID.randomUUID(), "Sala Roble", userId, "ana@test.com",
                START, START.plusHours(1), 2, status, new BigDecimal("15.50"), "tok_ok", START, START);
    }

    @Test
    void everyReservationEndpointRequiresAToken() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/reservations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reservations/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/reservations/" + id + "/confirm")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/reservations/" + id + "/cancel")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/reservations/" + id + "/complete")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService, confirmationService);
    }

    @Test
    void creatingAReservationWithAnEmptyBodyReturns400WithTheMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/reservations").with(user()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("spaceId", "startTime", "endTime", "attendees", "paymentMethod")));
        verifyNoInteractions(reservationService);
    }

    @Test
    void creatingAReservationUsesTheUserFromTheTokenAndReturns201WithLocation() throws Exception {
        ReservationResponse created = reservation(ReservationStatus.PENDING);
        when(reservationService.create(eq(userId), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/reservations").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"spaceId\":\"" + UUID.randomUUID()
                                + "\",\"startTime\":\"2030-06-01T10:00:00Z\",\"endTime\":\"2030-06-01T11:00:00Z\","
                                + "\"attendees\":2,\"paymentMethod\":\"tok_ok\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(reservationService).create(eq(userId), any());
    }

    @Test
    void confirmingReturns200WhenThePaymentIsApproved() throws Exception {
        UUID id = UUID.randomUUID();
        when(confirmationService.confirm(eq(id), any(CurrentUser.class)))
                .thenReturn(new ConfirmReservationResponse("Reserva confirmada", reservation(ReservationStatus.CONFIRMED)));

        mockMvc.perform(post("/api/v1/reservations/" + id + "/confirm").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reserva confirmada"))
                .andExpect(jsonPath("$.reservation.status").value("CONFIRMED"));
    }

    @Test
    void confirmingReturns202WhenThePaymentProviderIsUnavailable() throws Exception {
        UUID id = UUID.randomUUID();
        when(confirmationService.confirm(eq(id), any(CurrentUser.class)))
                .thenReturn(new ConfirmReservationResponse("pendiente", reservation(ReservationStatus.PENDING_PAYMENT)));

        mockMvc.perform(post("/api/v1/reservations/" + id + "/confirm").with(user()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reservation.status").value("PENDING_PAYMENT"));
    }

    @Test
    void confirmingReturns402WhenThePaymentIsDeclined() throws Exception {
        UUID id = UUID.randomUUID();
        when(confirmationService.confirm(eq(id), any(CurrentUser.class)))
                .thenThrow(new PaymentDeclinedException("INSUFFICIENT_FUNDS"));

        mockMvc.perform(post("/api/v1/reservations/" + id + "/confirm").with(user()))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"));
    }

    @Test
    void onlyAnAdminCanCompleteAReservation() throws Exception {
        UUID id = UUID.randomUUID();
        when(reservationService.complete(id)).thenReturn(reservation(ReservationStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/reservations/" + id + "/complete").with(user()))
                .andExpect(status().isForbidden());
        verify(reservationService, never()).complete(any());

        mockMvc.perform(post("/api/v1/reservations/" + id + "/complete").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void theRequesterIsPassedToTheServiceWithTheirRole() throws Exception {
        UUID id = UUID.randomUUID();
        when(reservationService.cancel(eq(id), any(CurrentUser.class))).thenReturn(reservation(ReservationStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/reservations/" + id + "/cancel").with(admin())).andExpect(status().isOk());

        ArgumentCaptor<CurrentUser> requester = ArgumentCaptor.forClass(CurrentUser.class);
        verify(reservationService).cancel(eq(id), requester.capture());
        assertThat(requester.getValue().id()).isEqualTo(userId);
        assertThat(requester.getValue().admin()).isTrue();
    }

    @Test
    void theReportIsOnlyForAdminsAndValidatesItsParameters() throws Exception {
        String range = "?from=2030-06-01T00:00:00Z&to=2030-06-02T00:00:00Z";

        mockMvc.perform(get("/api/v1/reports/occupancy" + range)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reports/occupancy" + range).with(user())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/occupancy").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/reports/occupancy?from=ayer&to=hoy").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(occupancyReportService);
    }

    @Test
    void anAdminGetsTheOccupancyRows() throws Exception {
        when(occupancyReportService.occupancy(any(), any())).thenReturn(List.of(
                new OccupancyResponse(UUID.randomUUID(), "Sala Roble", new BigDecimal("4.00"),
                        new BigDecimal("24.00"), new BigDecimal("16.67"))));

        mockMvc.perform(get("/api/v1/reports/occupancy?from=2030-06-01T00:00:00Z&to=2030-06-02T00:00:00Z").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spaceName").value("Sala Roble"))
                .andExpect(jsonPath("$[0].occupancyPercentage").value(16.67));
    }
}
