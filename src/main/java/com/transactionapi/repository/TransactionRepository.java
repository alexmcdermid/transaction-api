package com.transactionapi.repository;

import com.transactionapi.model.Transaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByAccountIdOrderByOccurredAtDesc(UUID accountId);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.account.id = :accountId")
    BigDecimal sumAmountsByAccountId(UUID accountId);
}
