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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        // el registro publico siempre crea usuarios normales, el admin sale del bootstrap
        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim(), Role.USER);
        return userMapper.toResponse(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        // mismo error si el correo no existe o la clave esta mal, para no dar pistas
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("credenciales invalidas"));
        return new TokenResponse(jwtService.generateToken(user), "Bearer", jwtService.expiresInSeconds());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
