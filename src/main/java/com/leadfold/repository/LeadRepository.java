package com.leadfold.repository;

import com.leadfold.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    List<Lead> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Lead> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);
}
