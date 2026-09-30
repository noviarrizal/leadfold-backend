package com.leadfold.repository;

import com.leadfold.entity.WhatsAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WhatsAppConfigRepository extends JpaRepository<WhatsAppConfig, UUID> {
    Optional<WhatsAppConfig> findByPhoneNumberId(String phoneNumberId);
}
