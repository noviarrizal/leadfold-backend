package com.leadfold.service;

import com.leadfold.entity.Lead;
import com.leadfold.entity.ZapierSubscription;
import com.leadfold.repository.ZapierSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

// Fires the REST Hook side of the Zapier/Make integration: every new lead
// gets POSTed to whatever target URLs the user's active subscriptions point
// at. One subscription failing (dead URL, user's Zap got turned off without
// unsubscribing, etc) must never block saving/serving the lead itself, so
// failures here are only logged, not thrown.
@Service
public class ZapierNotifierService {

    private static final Logger log = LoggerFactory.getLogger(ZapierNotifierService.class);

    private final ZapierSubscriptionRepository subscriptionRepository;
    private final RestClient restClient = RestClient.create();

    public ZapierNotifierService(ZapierSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public void notifySubscribers(UUID userId, Lead lead) {
        List<ZapierSubscription> subscriptions = subscriptionRepository.findByUserId(userId);

        for (ZapierSubscription subscription : subscriptions) {
            try {
                restClient.post()
                        .uri(subscription.getTargetUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(lead)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("Failed to notify Zapier hook {}: {}", subscription.getTargetUrl(), e.getMessage());
            }
        }
    }
}
