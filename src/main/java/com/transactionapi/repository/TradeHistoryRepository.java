package com.transactionapi.repository;

import com.transactionapi.model.TradeHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory, UUID> {

    List<TradeHistory> findByTradeIdOrderByActionAtAsc(UUID tradeId);

    List<TradeHistory> findByTradeIdAndUserIdOrderByActionAtAsc(UUID tradeId, String userId);

    List<TradeHistory> findByUserIdOrderByActionAtDesc(String userId);
}
