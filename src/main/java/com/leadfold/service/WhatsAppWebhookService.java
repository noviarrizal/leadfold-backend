package com.leadfold.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// Meta's WhatsApp webhook payload is deeply nested (entry[] -> changes[] ->
// value -> messages[]) and includes non-message events too (status updates,
// etc) mixed in — this walks it defensively and pulls out only real incoming
// text messages.
@Service
public class WhatsAppWebhookService {

    public List<IncomingMessage> extractMessages(JsonNode payload) {
        List<IncomingMessage> messages = new ArrayList<>();

        for (JsonNode entry : payload.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);

                for (JsonNode msg : value.path("messages")) {
                    if (!"text".equals(msg.path("type").asText())) {
                        continue; // skip images/audio/status updates for the MVP
                    }
                    String text = msg.path("text").path("body").asText(null);
                    String from = msg.path("from").asText(null);
                    if (text != null) {
                        messages.add(new IncomingMessage(phoneNumberId, from, text));
                    }
                }
            }
        }

        return messages;
    }
}
