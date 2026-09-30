package com.leadfold.controller;

import com.leadfold.entity.Lead;
import com.leadfold.entity.User;
import com.leadfold.repository.LeadRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Dashboard-facing endpoints — protected by JwtAuthFilter (see SecurityConfig,
// matched on /api/leads/**).
@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadRepository leadRepository;

    public LeadController(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    @GetMapping
    public List<Lead> listLeads(@AuthenticationPrincipal User user) {
        return leadRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateStatus(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        Lead lead = leadRepository.findById(id).orElse(null);
        if (lead == null || !lead.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(404).body(Map.of("error", "Lead not found"));
        }

        lead.setStatus(body.get("status"));
        leadRepository.save(lead);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
