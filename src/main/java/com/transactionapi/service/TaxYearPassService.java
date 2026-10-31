package com.transactionapi.service;

import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.model.TaxYearPass;
import com.transactionapi.model.User;
import com.transactionapi.repository.TaxYearPassRepository;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns tax year pass entitlements. A user may read reports and exports for a tax year only while
 * they hold a PAID pass for that year. Entitlement is computed from pass rows; nothing else is a
 * source of truth.
 */
@Service
@Transactional
public class TaxYearPassService {

    public static final int EARLIEST_TAX_YEAR = 2000;

    /** Display precedence when a user has several passes for one year. */
    private static final List<TaxYearPassStatus> STATUS_PRECEDENCE = List.of(
            TaxYearPassStatus.PAID,
            TaxYearPassStatus.PROCESSING,
            TaxYearPassStatus.PENDING,
            TaxYearPassStatus.FAILED,
            TaxYearPassStatus.REFUNDED
    );

    private final TaxYearPassRepository taxYearPassRepository;

    public TaxYearPassService(TaxYearPassRepository taxYearPassRepository) {
        this.taxYearPassRepository = taxYearPassRepository;
    }

    @Transactional(readOnly = true)
    public List<TaxYearPass> listPasses(User user) {
        return taxYearPassRepository.findByUserIdOrderByTaxYearDescCreatedAtDesc(user.getId());
    }

    @Transactional(readOnly = true)
    public boolean hasPaidPass(User user, int taxYear) {
        return taxYearPassRepository.existsByUserIdAndTaxYearAndStatus(user.getId(), taxYear, TaxYearPassStatus.PAID);
    }

    /** Authoritative gate for every tax year report and export. */
    @Transactional(readOnly = true)
    public void requirePaidPass(User user, int taxYear) {
        if (!hasPaidPass(user, taxYear)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tax year pass required for " + taxYear);
        }
    }

    /** The most significant pass status per tax year, e.g. PAID wins over an abandoned PENDING. */
    @Transactional(readOnly = true)
    public Map<Integer, TaxYearPassStatus> effectiveStatusByYear(User user) {
        Map<Integer, TaxYearPassStatus> statuses = new HashMap<>();
        for (TaxYearPass pass : listPasses(user)) {
            statuses.merge(pass.getTaxYear(), pass.getStatus(), TaxYearPassService::moreSignificant);
        }
        return statuses;
    }

    public int currentTaxYear() {
        return Year.now().getValue();
    }

    /** Tax years follow the calendar year of each trade's close date. */
    public void requireValidTaxYear(int taxYear) {
        int currentTaxYear = currentTaxYear();
        if (taxYear < EARLIEST_TAX_YEAR || taxYear > currentTaxYear) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tax year must be between " + EARLIEST_TAX_YEAR + " and " + currentTaxYear
            );
        }
    }

    private static TaxYearPassStatus moreSignificant(TaxYearPassStatus left, TaxYearPassStatus right) {
        return STATUS_PRECEDENCE.indexOf(left) <= STATUS_PRECEDENCE.indexOf(right) ? left : right;
    }
}
