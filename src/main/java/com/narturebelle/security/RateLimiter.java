package com.narturebelle.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter for the public forms (careers applications, feedback). Per instance,
 * so with N instances the effective limit is up to N times higher -- fine for spam control,
 * not a security boundary. Swap for a shared store if that ever matters.
 */
@Component
public class RateLimiter {

    private static final int PRUNE_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /**
     * The visitor's IP. On Vercel every request arrives through Vercel's proxy, which sets X-Real-IP to the
     * client's address; without it, falls back to the connection address (already resolved from
     * X-Forwarded-For by server.forward-headers-strategy when behind a trusted proxy). Off Vercel a client
     * could send its own X-Real-IP to dodge the limit -- acceptable for spam control.
     */
    public static String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp.trim() : request.getRemoteAddr();
    }

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
