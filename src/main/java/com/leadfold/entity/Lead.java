package com.leadfold.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

// A single captured + AI-parsed lead. extractedFieldsJson is a flexible bag
// (stored as raw JSON text) for anything vertical-specific — budget,
// preferred_date, service_type, etc — since the product isn't locked to one
// vertical yet.
@Entity
@Table(name = "leads", indexes = @Index(name = "idx_leads_user_created", columnList = "user_id, createdAt"))
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore // never serialize the owning user (would leak passwordHash) back to the client
    private User user;

    @Column(nullable = false)
    private String source; // "whatsapp" | "form" | "manual"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String rawMessage;

    private String name;

    private String contact; // phone or email, whichever the parser (or source) found

    @Column(columnDefinition = "TEXT")
    private String interestSummary; // one-line AI summary of what the lead wants

    @Column(columnDefinition = "TEXT")
    private String extractedFieldsJson; // raw JSON text, e.g. {"budget": "500jt", "urgency": "high"}

    @Column(nullable = false)
    private String status = "new"; // new | contacted | qualified | lost

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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getInterestSummary() {
        return interestSummary;
    }

    public void setInterestSummary(String interestSummary) {
        this.interestSummary = interestSummary;
    }

    public String getExtractedFieldsJson() {
        return extractedFieldsJson;
    }

    public void setExtractedFieldsJson(String extractedFieldsJson) {
        this.extractedFieldsJson = extractedFieldsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
