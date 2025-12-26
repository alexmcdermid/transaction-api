package com.transactionapi.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory sliding window rate limiter keyed by user ID.
 * Limits combined API calls per user across all endpoints within the configured window.
 */
@Component
public class RateLimiterService {

    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, ConcurrentLinkedDeque<Long>> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(
            @Value("${app.rate-limit.per-minute:100}") int maxRequests,
            @Value("${app.rate-limit.window-ms:60000}") long windowMillis
    ) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    /**
     * Returns true if the request is allowed and records it, false if the user is over limit.
     */
    public boolean allow(String userId) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        ConcurrentLinkedDeque<Long> deque = buckets.computeIfAbsent(userId, key -> new ConcurrentLinkedDeque<>());

        // prune old entries
        while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
            deque.pollFirst();
        }

        if (deque.size() >= maxRequests) {
            return false;
        }

        deque.addLast(now);
        return true;
    }
}
