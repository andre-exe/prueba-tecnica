package com.coworking.reservations.controller;

import com.coworking.reservations.config.SecurityConfig;
import com.coworking.reservations.config.properties.JwtProperties;
import com.coworking.reservations.domain.enums.Role;
import com.coworking.reservations.domain.enums.SpaceType;
import com.coworking.reservations.dto.request.SpaceFilter;
import com.coworking.reservations.dto.response.PageResponse;
import com.coworking.reservations.dto.response.SpaceResponse;
import com.coworking.reservations.dto.response.UserResponse;
import com.coworking.reservations.security.RestAccessDeniedHandler;
import com.coworking.reservations.security.RestAuthenticationEntryPoint;
import com.coworking.reservations.security.SecurityProblemWriter;
import com.coworking.reservations.service.AuthService;
import com.coworking.reservations.service.SpaceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, SpaceController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityProblemWriter.class})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
class AuthAndSpaceControllerWebMvcTest {

    private static final String VALID_SPACE =
            "{\"name\":\"Sala Roble\",\"type\":\"MEETING_ROOM\",\"capacity\":8,\"location\":\"Piso 2\",\"hourlyRate\":15.50}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private SpaceService spaceService;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private SpaceResponse space() {
        OffsetDateTime now = OffsetDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        return new SpaceResponse(UUID.randomUUID(), "Sala Roble", SpaceType.MEETING_ROOM, 8, "Piso 2",
                new BigDecimal("15.50"), true, now, now);
    }

    @Test
    void registerWithInvalidBodyReturns400WithOneErrorPerField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mal\",\"password\":\"123\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "password", "fullName")));
        verifyNoInteractions(authService);
    }

    @Test
    void registerWithAValidBodyReturns201() throws Exception {
        when(authService.register(any())).thenReturn(new UserResponse(UUID.randomUUID(), "maria@test.com", "Maria", Role.USER,
                OffsetDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC)));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria@test.com\",\"password\":\"Secreta123\",\"fullName\":\"Maria\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("maria@test.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void meWithoutATokenReturns401InProblemFormat() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void meReadsTheUserIdFromTheTokenSubject() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.me(id)).thenReturn(new UserResponse(id, "ana@test.com", "Ana", Role.USER,
                OffsetDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC)));

        mockMvc.perform(get("/api/v1/auth/me").with(jwt().jwt(j -> j.subject(id.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@test.com"));
    }

    @Test
    void spacesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/spaces")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/spaces").contentType(MediaType.APPLICATION_JSON).content(VALID_SPACE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRegularUserCannotCreateUpdateOrDeleteSpaces() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/spaces").with(user()).contentType(MediaType.APPLICATION_JSON).content(VALID_SPACE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(put("/api/v1/spaces/" + id).with(user()).contentType(MediaType.APPLICATION_JSON).content(VALID_SPACE))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/spaces/" + id).with(user())).andExpect(status().isForbidden());
        verify(spaceService, never()).create(any());
        verify(spaceService, never()).update(any(), any());
        verify(spaceService, never()).deactivate(any());
    }

    @Test
    void anAdminCreatingASpaceWithInvalidDataGets400AndAValidOneGets201() throws Exception {
        mockMvc.perform(post("/api/v1/spaces").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"type\":\"HOT_DESK\",\"capacity\":0,\"location\":\"\",\"hourlyRate\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "capacity", "location", "hourlyRate")));

        SpaceResponse created = space();
        when(spaceService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/spaces").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_SPACE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/spaces/" + created.id())))
                .andExpect(jsonPath("$.name").value("Sala Roble"));
    }

    @Test
    void anAdminCanDeleteASpaceAndGets204() throws Exception {
        mockMvc.perform(delete("/api/v1/spaces/" + UUID.randomUUID()).with(admin())).andExpect(status().isNoContent());
    }

    @Test
    void aRegularUserOnlyAsksForActiveSpacesEvenIfTheyRequestInactiveOnes() throws Exception {
        when(spaceService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/spaces?active=false").with(user())).andExpect(status().isOk());

        ArgumentCaptor<SpaceFilter> filter = ArgumentCaptor.forClass(SpaceFilter.class);
        verify(spaceService).search(filter.capture(), any(Pageable.class));
        assertThat(filter.getValue().active()).isTrue();
    }

    @Test
    void anAdminCanFilterByInactiveSpaces() throws Exception {
        when(spaceService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/spaces?active=false&type=HOT_DESK&minCapacity=2").with(admin()))
                .andExpect(status().isOk());

        ArgumentCaptor<SpaceFilter> filter = ArgumentCaptor.forClass(SpaceFilter.class);
        verify(spaceService).search(filter.capture(), any(Pageable.class));
        assertThat(filter.getValue().active()).isFalse();
        assertThat(filter.getValue().type()).isEqualTo(SpaceType.HOT_DESK);
        assertThat(filter.getValue().minCapacity()).isEqualTo(2);
    }

    @Test
    void anInvalidPathIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/spaces/no-es-un-uuid").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }
}
