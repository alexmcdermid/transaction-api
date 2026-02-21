package com.transactionapi.repository;

import com.transactionapi.model.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Account> findByIdAndUserId(UUID id, String userId);
}
