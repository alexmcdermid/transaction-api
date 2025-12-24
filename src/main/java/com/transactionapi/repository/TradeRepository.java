package com.transactionapi.repository;

import com.transactionapi.model.Trade;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    @Query("select t from Trade t where t.userId = :userId order by t.closedAt desc, t.createdAt desc")
    List<Trade> findAllForUser(@Param("userId") String userId);

    Optional<Trade> findByIdAndUserId(UUID id, String userId);
}
