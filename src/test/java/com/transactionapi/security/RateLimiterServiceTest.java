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

    @Test
    void evictStaleBuckets_removesBucketWithNoActivity() {
        // Window of 0ms so any timestamp is immediately stale
        RateLimiterService limiter = new RateLimiterService(100, 100, 0);

        limiter.allow("user-evict");
        limiter.evictStaleBuckets();

        // After eviction the bucket is gone; a fresh allow() should succeed (not be
        // counted against a lingering bucket)
        assertThat(limiter.allow("user-evict")).isTrue();
    }

    @Test
    void evictStaleBuckets_retainsActiveBucket() {
        RateLimiterService limiter = new RateLimiterService(3, 3, 60_000);

        limiter.allow("user-active");
        limiter.allow("user-active");
        limiter.evictStaleBuckets(); // bucket is still within the window, must not be removed

        // Third request should still be allowed (bucket was retained with 2 hits)
        assertThat(limiter.allow("user-active")).isTrue();
        // Fourth should be blocked
        assertThat(limiter.allow("user-active")).isFalse();
    }
}
