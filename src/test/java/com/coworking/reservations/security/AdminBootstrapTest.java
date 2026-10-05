package com.coworking.reservations.security;

import com.coworking.reservations.config.properties.BootstrapAdminProperties;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.Role;
import com.coworking.reservations.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        BootstrapAdminProperties properties = new BootstrapAdminProperties("Admin@Coworking.Local", "Admin1234!", "Administrador");
        bootstrap = new AdminBootstrap(properties, userRepository, passwordEncoder);
    }

    @Test
    void createsTheAdminWhenItDoesNotExist() {
        when(userRepository.existsByEmail("admin@coworking.local")).thenReturn(false);

        bootstrap.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@coworking.local");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("Admin1234!", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWhenTheAdminAlreadyExists() {
        when(userRepository.existsByEmail("admin@coworking.local")).thenReturn(true);

        bootstrap.run(new DefaultApplicationArguments());
        bootstrap.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }
}
