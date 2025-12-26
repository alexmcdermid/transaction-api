package com.transactionapi.repository;

import com.transactionapi.model.Trade;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    @Query("select t from Trade t where t.userId = :userId order by t.closedAt desc, t.createdAt desc")
    List<Trade> findAllForUser(@Param("userId") String userId);

    Page<Trade> findByUserIdOrderByClosedAtDesc(String userId, Pageable pageable);

    Page<Trade> findByUserIdAndClosedAtBetweenOrderByClosedAtDesc(
            String userId,
            LocalDate start,
            LocalDate end,
            Pageable pageable
    );

    List<Trade> findByUserIdAndClosedAtBetweenOrderByClosedAtDesc(String userId, LocalDate start, LocalDate end);

    Optional<Trade> findByIdAndUserId(UUID id, String userId);
}
