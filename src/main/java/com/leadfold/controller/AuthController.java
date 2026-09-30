package com.leadfold.controller;

import com.leadfold.dto.LoginRequest;
import com.leadfold.dto.SignupRequest;
import com.leadfold.entity.User;
import com.leadfold.repository.UserRepository;
import com.leadfold.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "Email already registered"));
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCompanyName(request.companyName());
        user.setApiKey("lf_" + UUID.randomUUID().toString().replace("-", ""));
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId());
        return ResponseEntity.status(201).body(Map.of("token", token, "apiKey", user.getApiKey()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        String token = jwtService.generateToken(user.getId());
        return ResponseEntity.ok(Map.of("token", token, "apiKey", user.getApiKey()));
    }
}
