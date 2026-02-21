package com.transactionapi.repository;

import com.transactionapi.model.ShareLink;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {
    Optional<ShareLink> findByCode(String code);

    List<ShareLink> findByUserIdOrderByCreatedAtDesc(String userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM ShareLink s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
