package com.transactionapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.transactionapi.config.BillingSettings;
import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.model.TaxYearPass;
import com.transactionapi.model.User;
import com.transactionapi.repository.TaxYearPassRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TaxYearPassServiceCheckoutTest {

    private static final BillingSettings CHECKOUT_DISABLED = new BillingSettings("", "", "", "");

    @Mock
    private TaxYearPassRepository taxYearPassRepository;

    @Test
    void checkoutIsUnavailableUntilBillingIsConfigured() {
        TaxYearPassService service = new TaxYearPassService(taxYearPassRepository, CHECKOUT_DISABLED);

        assertThatThrownBy(() -> service.startCheckout(userWithId(), "buyer@example.com", 2025))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getReason()).isEqualTo("Tax year pass checkout is not configured");
                });
        verifyNoInteractions(taxYearPassRepository);
    }

    @Test
    void checkoutValidatesTheTaxYearBeforeTheConfiguration() {
        TaxYearPassService service = new TaxYearPassService(taxYearPassRepository, CHECKOUT_DISABLED);

        assertThatThrownBy(() -> service.startCheckout(userWithId(), "buyer@example.com", 1999))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void checkoutKeepsTheQueryStringAlreadyOnThePaymentLink() {
        TaxYearPassService service = new TaxYearPassService(
                taxYearPassRepository,
                new BillingSettings("whsec_test_secret", "https://buy.stripe.com/test_link?locale=fr-CA", "", "")
        );
        User user = userWithId();
        TaxYearPass pendingPass = passWithId(user, TaxYearPassStatus.PENDING);
        when(taxYearPassRepository.findFirstByUserIdAndTaxYearAndStatusInOrderByCreatedAtDesc(
                eq(user.getId()),
                eq(2025),
                anyCollection()
        )).thenReturn(Optional.of(pendingPass));

        String checkoutUrl = service.startCheckout(user, "tax+filer@example.com", 2025);

        assertThat(checkoutUrl).isEqualTo(
                "https://buy.stripe.com/test_link?locale=fr-CA&client_reference_id=" + pendingPass.getId()
                        + "&prefilled_email=tax%2Bfiler%40example.com"
        );
    }

    private static User userWithId() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static TaxYearPass passWithId(User user, TaxYearPassStatus status) {
        TaxYearPass pass = new TaxYearPass();
        ReflectionTestUtils.setField(pass, "id", UUID.randomUUID());
        pass.setUserId(user.getId());
        pass.setTaxYear(2025);
        pass.setStatus(status);
        return pass;
    }
}
