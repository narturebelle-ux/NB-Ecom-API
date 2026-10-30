package com.narturebelle.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter for the public forms (careers applications, feedback). Per instance,
 * so with N Cloud Run instances the effective limit is up to N times higher -- fine for spam control,
 * not a security boundary. Swap for a shared store if that ever matters.
 */
@Component
public class RateLimiter {

    private static final int PRUNE_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** Records a hit and returns true, or returns false (recording nothing) if the key is over its limit. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = Instant.now();
        if (hits.size() > PRUNE_THRESHOLD) {
            Instant stale = now.minus(Duration.ofDays(1));
            hits.values().removeIf(deque -> {
                synchronized (deque) {
                    Instant last = deque.peekLast();
                    return last == null || last.isBefore(stale);
                }
            });
        }
        Instant cutoff = now.minus(window);
        Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
