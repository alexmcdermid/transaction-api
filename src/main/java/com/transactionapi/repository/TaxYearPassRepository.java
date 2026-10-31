package com.transactionapi.repository;

import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.model.TaxYearPass;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
    // Native FOR UPDATE because Hibernate renders @Lock(PESSIMISTIC_WRITE) as PostgreSQL's FOR NO KEY UPDATE,
    // which the H2 test database cannot parse. They differ only for foreign keys into this table; there are none.
    @Query(value = "select * from tax_year_passes where id = :id for update", nativeQuery = true)
    Optional<TaxYearPass> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value = "select * from tax_year_passes where stripe_payment_intent_id = :paymentIntentId for update",
            nativeQuery = true
    )
    List<TaxYearPass> findByStripePaymentIntentIdForUpdate(@Param("paymentIntentId") String paymentIntentId);
}
