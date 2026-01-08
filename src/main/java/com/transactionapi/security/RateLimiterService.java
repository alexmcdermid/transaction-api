package com.transactionapi.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RateLimiterService {

    private final int maxRequests;
    private final int maxPublicShareRequests;
    private final long windowMillis;
    private final Map<String, ConcurrentLinkedDeque<Long>> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(
            @Value("${app.rate-limit.per-minute:100}") int maxRequests,
            @Value("${app.rate-limit.public-share-per-minute:20}") int maxPublicShareRequests,
            @Value("${app.rate-limit.window-ms:60000}") long windowMillis
    ) {
        this.maxRequests = maxRequests;
        this.maxPublicShareRequests = maxPublicShareRequests;
        this.windowMillis = windowMillis;
    }

    public boolean allow(String userId) {
        return allowWithLimit(userId, maxRequests);
    }

    public boolean allowPublicShare(String ipAddress) {
        return allowWithLimit("ip:" + ipAddress, maxPublicShareRequests);
    }

    private boolean allowWithLimit(String key, int limit) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        ConcurrentLinkedDeque<Long> deque = buckets.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
            deque.pollFirst();
        }

        if (deque.size() >= limit) {
            return false;
        }

        deque.addLast(now);
        return true;
    }
}
