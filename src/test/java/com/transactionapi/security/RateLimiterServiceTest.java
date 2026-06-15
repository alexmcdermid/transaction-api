package com.transactionapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

    @Test
    void blocksAfterLimit() {
        RateLimiterService limiter = new RateLimiterService(2, 2, 60_000, 100);

        assertThat(limiter.allow("user-1")).isTrue();
        assertThat(limiter.allow("user-1")).isTrue();
        assertThat(limiter.allow("user-1")).isFalse();
    }

    @Test
    void blocksNewKeysAfterBucketLimit() {
        RateLimiterService limiter = new RateLimiterService(2, 2, 60_000, 1);

        assertThat(limiter.allow("user-1")).isTrue();
        assertThat(limiter.allow("user-2")).isFalse();
    }

    @Test
    void evictsExpiredBucketsBeforeApplyingBucketLimit() throws Exception {
        RateLimiterService limiter = new RateLimiterService(2, 2, 1, 1);

        assertThat(limiter.allow("user-1")).isTrue();
        Thread.sleep(5);
        assertThat(limiter.allow("user-2")).isTrue();
    }
}
