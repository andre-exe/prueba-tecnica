package com.coworking.reservations.service;

import com.coworking.reservations.dto.request.LoginRequest;
import com.coworking.reservations.dto.request.RegisterRequest;
import com.coworking.reservations.dto.response.TokenResponse;
import com.coworking.reservations.dto.response.UserResponse;

import java.util.UUID;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    UserResponse me(UUID userId);
}
