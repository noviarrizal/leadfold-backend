package com.leadfold.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.leadfold.dto.ParsedLead;
import com.leadfold.entity.Lead;
import com.leadfold.entity.User;
import com.leadfold.entity.WhatsAppConfig;
import com.leadfold.repository.LeadRepository;
import com.leadfold.repository.UserRepository;
import com.leadfold.repository.WhatsAppConfigRepository;
import com.leadfold.service.IncomingMessage;
import com.leadfold.service.LeadParserService;
import com.leadfold.service.WhatsAppWebhookService;
import com.leadfold.service.ZapierNotifierService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Public endpoints — these are called directly by Meta's WhatsApp platform
// and by whatever form/widget the user embeds on their own site, not by a
// logged-in dashboard user, so no auth filter runs here (see SecurityConfig).
// The "auth" for /form/{apiKey} is just checking that apiKey belongs to a
// real account.
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WhatsAppConfigRepository whatsAppConfigRepository;
    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final LeadParserService leadParserService;
    private final ZapierNotifierService zapierNotifierService;
    private final WhatsAppWebhookService whatsAppWebhookService;

    @Value("${leadfold.whatsapp-verify-token}")
    private String whatsappVerifyToken;

    public WebhookController(
            WhatsAppConfigRepository whatsAppConfigRepository,
            UserRepository userRepository,
            LeadRepository leadRepository,
            LeadParserService leadParserService,
            ZapierNotifierService zapierNotifierService,
            WhatsAppWebhookService whatsAppWebhookService
    ) {
        this.whatsAppConfigRepository = whatsAppConfigRepository;
        this.userRepository = userRepository;
        this.leadRepository = leadRepository;
        this.leadParserService = leadParserService;
        this.zapierNotifierService = zapierNotifierService;
        this.whatsAppWebhookService = whatsAppWebhookService;
    }

    // Meta's one-time webhook verification handshake when you first register
    // the callback URL in the Meta App dashboard.
    @GetMapping("/whatsapp")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge
    ) {
        if ("subscribe".equals(mode) && whatsappVerifyToken.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).build();
    }

    @PostMapping("/whatsapp")
    public ResponseEntity<Void> receiveWhatsapp(@RequestBody JsonNode payload) {
        List<IncomingMessage> messages = whatsAppWebhookService.extractMessages(payload);

        for (IncomingMessage message : messages) {
            whatsAppConfigRepository.findByPhoneNumberId(message.phoneNumberId()).ifPresent(config -> {
                ParsedLead parsed = leadParserService.parse(message.text());
                Lead lead = buildLead(config.getUser(), "whatsapp", message.text(), parsed);
                lead.setContact(parsed.contact() != null ? parsed.contact() : message.from());
                leadRepository.save(lead);
                zapierNotifierService.notifySubscribers(config.getUser().getId(), lead);
            });
        }

        // Always ack 200 — WhatsApp expects a fast response and will retry
        // aggressively (and eventually disable the webhook) if it doesn't get one.
        return ResponseEntity.ok().build();
    }

    // Generic webhook for a form/widget embedded on the user's own site.
    // Not tied to any particular vertical's field names on purpose — pass
    // whatever raw text you have in "message" and let the parser do the work.
    @PostMapping("/form/{apiKey}")
    public ResponseEntity<?> receiveForm(@PathVariable String apiKey, @RequestBody JsonNode body) {
        User user = userRepository.findByApiKey(apiKey).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Unknown API key"));
        }

        String rawMessage = body.has("message") ? body.get("message").asText() : body.toString();
        ParsedLead parsed = leadParserService.parse(rawMessage);

        Lead lead = buildLead(user, "form", rawMessage, parsed);
        if (lead.getName() == null && body.has("name")) {
            lead.setName(body.get("name").asText(null));
        }
        if (lead.getContact() == null) {
            String fallback = body.has("email") ? body.get("email").asText(null)
                    : body.has("phone") ? body.get("phone").asText(null) : null;
            lead.setContact(fallback);
        }

        leadRepository.save(lead);
        zapierNotifierService.notifySubscribers(user.getId(), lead);
        return ResponseEntity.status(201).body(Map.of("ok", true, "leadId", lead.getId()));
    }

    private Lead buildLead(User user, String source, String rawMessage, ParsedLead parsed) {
        Lead lead = new Lead();
        lead.setUser(user);
        lead.setSource(source);
        lead.setRawMessage(rawMessage);
        lead.setName(parsed.name());
        lead.setContact(parsed.contact());
        lead.setInterestSummary(parsed.interestSummary());
        lead.setExtractedFieldsJson(parsed.extractedFieldsJson());
        return lead;
    }
}
