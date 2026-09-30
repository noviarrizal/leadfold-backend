package com.leadfold.security;

import com.leadfold.entity.User;
import com.leadfold.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Protects external-integration routes (/api/zapier/**). Auth is a single
// static API key per account (the "x-api-key" header) — simple, and enough
// for one Zapier/Make connection per Leadfold account.
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public ApiKeyAuthFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("x-api-key");
        if (apiKey == null || apiKey.isBlank()) {
            writeUnauthorized(response, "Missing x-api-key header");
            return;
        }

        User user = userRepository.findByApiKey(apiKey).orElse(null);
        if (user == null) {
            writeUnauthorized(response, "Invalid API key");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
