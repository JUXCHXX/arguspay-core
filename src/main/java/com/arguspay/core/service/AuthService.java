package com.arguspay.core.service;

import com.arguspay.core.dto.AuthRequest;
import com.arguspay.core.dto.AuthResponse;
import com.arguspay.core.entity.AppUser;
import com.arguspay.core.exception.EmailAlreadyExistsException;
import com.arguspay.core.exception.InvalidCredentialsException;
import com.arguspay.core.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Email already registered");
        }
        AppUser user = users.save(new AppUser(email, passwordEncoder.encode(request.password())));
        return new AuthResponse(jwtService.generate(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        String email = request.email().trim().toLowerCase();
        AppUser user = users.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }
        return new AuthResponse(jwtService.generate(user));
    }
}