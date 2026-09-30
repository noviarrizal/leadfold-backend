package com.leadfold.repository;

import com.leadfold.entity.ZapierSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ZapierSubscriptionRepository extends JpaRepository<ZapierSubscription, UUID> {
    List<ZapierSubscription> findByUserId(UUID userId);
}
