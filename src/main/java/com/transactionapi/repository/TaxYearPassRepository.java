package com.transactionapi.repository;

import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.model.TaxYearPass;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxYearPassRepository extends JpaRepository<TaxYearPass, UUID> {

    List<TaxYearPass> findByUserIdOrderByTaxYearDescCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndTaxYearAndStatus(UUID userId, int taxYear, TaxYearPassStatus status);

    Optional<TaxYearPass> findFirstByUserIdAndTaxYearAndStatusInOrderByCreatedAtDesc(
            UUID userId,
            int taxYear,
            Collection<TaxYearPassStatus> statuses
    );

    /** Row lock for webhook state changes, so concurrent Stripe deliveries apply one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TaxYearPass p where p.id = :id")
    Optional<TaxYearPass> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TaxYearPass p where p.stripePaymentIntentId = :paymentIntentId")
    List<TaxYearPass> findByStripePaymentIntentIdForUpdate(@Param("paymentIntentId") String paymentIntentId);
}
