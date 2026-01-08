package com.transactionapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

    @Test
    void blocksAfterLimit() {
        RateLimiterService limiter = new RateLimiterService(2, 2, 60_000);

        assertThat(limiter.allow("user-1")).isTrue();
        assertThat(limiter.allow("user-1")).isTrue();
        assertThat(limiter.allow("user-1")).isFalse();
    }
}
