package com.coworking.reservations.security;

import com.coworking.reservations.config.properties.BootstrapAdminProperties;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.Role;
import com.coworking.reservations.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(BootstrapAdminProperties properties, UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            log.info("el admin {} ya existe, no se crea de nuevo", email);
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode(properties.password()),
                properties.fullName(), Role.ADMIN));
        log.info("admin {} creado", email);
    }
}
