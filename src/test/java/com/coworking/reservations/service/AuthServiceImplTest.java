package com.coworking.reservations.service;

import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.Role;
import com.coworking.reservations.dto.request.LoginRequest;
import com.coworking.reservations.dto.request.RegisterRequest;
import com.coworking.reservations.dto.response.TokenResponse;
import com.coworking.reservations.dto.response.UserResponse;
import com.coworking.reservations.exception.EmailAlreadyExistsException;
import com.coworking.reservations.exception.ResourceNotFoundException;
import com.coworking.reservations.mapper.UserMapper;
import com.coworking.reservations.repository.UserRepository;
import com.coworking.reservations.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, Mappers.getMapper(UserMapper.class));
    }

    private User storedUser(String email, String rawPassword, Role role) {
        User user = new User(email, passwordEncoder.encode(rawPassword), "Ana", role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void registerNormalizesTheEmailHashesThePasswordAndAlwaysCreatesAUser() {
        when(userRepository.existsByEmail("maria@test.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.register(new RegisterRequest("  Maria@Test.COM ", "Secreta123", " Maria "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("maria@test.com");
        assertThat(saved.getValue().getFullName()).isEqualTo("Maria");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("Secreta123").startsWith("$2");
        assertThat(passwordEncoder.matches("Secreta123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(response.email()).isEqualTo("maria@test.com");
    }

    @Test
    void registerRejectsAnEmailThatAlreadyExists() {
        when(userRepository.existsByEmail("maria@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("MARIA@test.com", "Secreta123", "Maria")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginReturnsABearerTokenForValidCredentials() {
        User user = storedUser("ana@test.com", "Secreta123", Role.USER);
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("token-falso");
        when(jwtService.expiresInSeconds()).thenReturn(3600L);

        TokenResponse response = service.login(new LoginRequest(" Ana@Test.com ", "Secreta123"));

        assertThat(response.accessToken()).isEqualTo("token-falso");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    void loginFailsWithTheSameErrorForAWrongPasswordAndAnUnknownEmail() {
        User user = storedUser("ana@test.com", "Secreta123", Role.USER);
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("nadie@test.com")).thenReturn(Optional.empty());

        Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login(new LoginRequest("ana@test.com", "otra-clave")));
        Throwable unknownEmail = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login(new LoginRequest("nadie@test.com", "Secreta123")));

        assertThat(wrongPassword).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownEmail).isInstanceOf(BadCredentialsException.class);
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
    }

    @Test
    void meReturnsTheUserData() {
        User user = storedUser("ana@test.com", "Secreta123", Role.ADMIN);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserResponse response = service.me(user.getId());

        assertThat(response.email()).isEqualTo("ana@test.com");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void meFailsWhenTheUserNoLongerExists() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
