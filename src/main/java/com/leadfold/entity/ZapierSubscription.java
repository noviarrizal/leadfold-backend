package com.leadfold.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

// Zapier's REST Hook pattern: Zapier calls POST /api/zapier/hooks/subscribe
// when a user turns on a Zap, we store the target URL here, and POST every
// new lead to it as it's captured. Make.com can reuse the same table/flow
// once its own custom app is built, or just point its generic Webhooks
// module at this same subscribe endpoint.
@Entity
@Table(name = "zapier_subscriptions")
public class ZapierSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private String targetUrl;

    @Column(nullable = false)
    private String event = "new_lead";

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
