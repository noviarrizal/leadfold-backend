package com.leadfold.controller;

import com.leadfold.entity.Lead;
import com.leadfold.entity.User;
import com.leadfold.entity.ZapierSubscription;
import com.leadfold.repository.LeadRepository;
import com.leadfold.repository.ZapierSubscriptionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Implements Zapier's "REST Hook" trigger pattern (subscribe/unsubscribe +
// a polling fallback), protected by ApiKeyAuthFilter (see SecurityConfig,
// matched on /api/zapier/**). Once the official Zapier Platform CLI app is
// built, its "New Lead" trigger will call these same three endpoints.
@RestController
@RequestMapping("/api/zapier")
public class ZapierController {

    private final ZapierSubscriptionRepository subscriptionRepository;
    private final LeadRepository leadRepository;

    public ZapierController(ZapierSubscriptionRepository subscriptionRepository, LeadRepository leadRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.leadRepository = leadRepository;
    }

    // Zapier calls this when a user turns on the Zap.
    @PostMapping("/hooks/subscribe")
    public ResponseEntity<?> subscribe(@AuthenticationPrincipal User user, @RequestBody Map<String, String> body) {
        String targetUrl = body.get("targetUrl");
        if (targetUrl == null || targetUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "targetUrl is required"));
        }

        ZapierSubscription subscription = new ZapierSubscription();
        subscription.setUser(user);
        subscription.setTargetUrl(targetUrl);
        subscriptionRepository.save(subscription);

        return ResponseEntity.status(201).body(Map.of("id", subscription.getId()));
    }

    // Zapier calls this when a user turns off the Zap.
    @DeleteMapping("/hooks/subscribe/{id}")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        subscriptionRepository.findById(id)
                .filter(sub -> sub.getUser().getId().equals(user.getId()))
                .ifPresent(subscriptionRepository::delete);
        return ResponseEntity.noContent().build();
    }

    // Polling fallback, and what Zapier calls when the user clicks
    // "Test trigger" while setting up their Zap.
    @GetMapping("/triggers/new-lead")
    public List<Lead> pollNewLeads(@AuthenticationPrincipal User user) {
        return leadRepository.findTop10ByUserIdOrderByCreatedAtDesc(user.getId());
    }
}
