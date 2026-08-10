package com.transactionapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.transactionapi.model.User;
import com.transactionapi.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceStripeSubscriptionTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void checkoutCompletionCreatesPremiumUserAndStoresStripeIds() {
        Optional<User> updated = userService.updateStripeSubscriptionFromCheckout(
                "stripe-checkout-user",
                "cus_checkout_user",
                "sub_checkout_user"
        );

        assertThat(updated).isPresent();
        assertThat(updated.get().isPremium()).isTrue();
        assertThat(updated.get().getStripeCustomerId()).isEqualTo("cus_checkout_user");
        assertThat(updated.get().getStripeSubscriptionId()).isEqualTo("sub_checkout_user");
        assertThat(updated.get().getStripeSubscriptionStatus()).isEqualTo("checkout_completed");
    }

    @Test
    void activeAndTrialingSubscriptionStatusesKeepUserPremium() {
        User user = userService.updateStripeCustomerId(
                "stripe-active-user",
                "active@example.com",
                "cus_active_user"
        );
        user.setStripeSubscriptionId("sub_active_user");
        userRepository.save(user);

        Optional<User> active = userService.updateStripeSubscriptionStatus(
                "cus_active_user",
                "sub_active_user",
                "active"
        );
        Optional<User> trialing = userService.updateStripeSubscriptionStatus(
                "cus_active_user",
                "sub_active_user",
                "trialing"
        );

        assertThat(active).isPresent();
        assertThat(active.get().isPremium()).isTrue();
        assertThat(trialing).isPresent();
        assertThat(trialing.get().isPremium()).isTrue();
        assertThat(trialing.get().getStripeSubscriptionStatus()).isEqualTo("trialing");
    }

    @Test
    void inactiveSubscriptionStatusesRemovePremium() {
        userService.updateStripeSubscriptionFromCheckout(
                "stripe-cancel-user",
                "cus_cancel_user",
                "sub_cancel_user"
        );

        Optional<User> pastDue = userService.updateStripeSubscriptionStatus(
                "cus_cancel_user",
                "sub_cancel_user",
                "past_due"
        );
        Optional<User> canceled = userService.updateStripeSubscriptionStatus(
                "cus_cancel_user",
                "sub_cancel_user",
                "canceled"
        );

        assertThat(pastDue).isPresent();
        assertThat(pastDue.get().isPremium()).isFalse();
        assertThat(canceled).isPresent();
        assertThat(canceled.get().isPremium()).isFalse();
        assertThat(canceled.get().getStripeSubscriptionStatus()).isEqualTo("canceled");
    }

    @Test
    void subscriptionStatusCanFindUserBySubscriptionIdWhenCustomerIdIsMissing() {
        userService.updateStripeSubscriptionFromCheckout(
                "stripe-subscription-lookup-user",
                "cus_subscription_lookup_user",
                "sub_subscription_lookup_user"
        );

        Optional<User> updated = userService.updateStripeSubscriptionStatus(
                null,
                "sub_subscription_lookup_user",
                "canceled"
        );

        assertThat(updated).isPresent();
        assertThat(updated.get().isPremium()).isFalse();
        assertThat(updated.get().getStripeSubscriptionStatus()).isEqualTo("canceled");
    }

    @Test
    void unknownStripeSubscriptionDoesNotCreateUser() {
        Optional<User> updated = userService.updateStripeSubscriptionStatus(
                "cus_missing_user",
                "sub_missing_user",
                "active"
        );

        assertThat(updated).isEmpty();
    }
}
