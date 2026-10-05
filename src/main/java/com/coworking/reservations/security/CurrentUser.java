package com.coworking.reservations.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public record CurrentUser(UUID id, boolean admin) {

    public static CurrentUser from(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return new CurrentUser(UUID.fromString(jwt.getSubject()), admin);
    }
}
