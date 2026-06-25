package com.mcp.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mcp.properties.BrowserProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BrowserSessionManager {

    private static final Logger log = LoggerFactory.getLogger(BrowserSessionManager.class);

    private final BrowserProperties properties;
    private volatile Playwright playwright;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final ReentrantLock sessionLock = new ReentrantLock();

    /** Tracks the last-activity timestamp for each session (keyed by sessionId). */
    private final Map<String, Instant> lastActivity = new ConcurrentHashMap<>();

    private record SessionState(Browser browser, BrowserContext context) {}

    public BrowserSessionManager(BrowserProperties properties) {
        this.properties = properties;
    }

    static {
        System.setProperty("playwright.skip.browser.download", "true");
    }

    private void ensurePlaywrightInitialized() {
        if (playwright == null) {
            sessionLock.lock();
            try {
                if (playwright == null) {
                    playwright = Playwright.create();
                }
            } finally {
                sessionLock.unlock();
            }
        }
    }

    // Lock-protected: enforceSessionLimit() check + ensurePlaywrightInitialized() +
    // session creation must be atomic to prevent TOCTOU races under concurrent calls.
    public String createSession(com.mcp.dto.browser.BrowserSessionRequest request) {
        sessionLock.lock();
        try {
            java.util.Optional<String> evictedId = enforceSessionLimit();
            evictedId.ifPresent(this::closeSession);

            ensurePlaywrightInitialized();
            String sessionId = UUID.randomUUID().toString();
            
            boolean isHeadless = request.headless() != null ? request.headless() : properties.isHeadless();
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(isHeadless);
            
            Browser browser;
            try {
                // Try launching with host's Google Chrome channel
                BrowserType.LaunchOptions chromeOptions = new BrowserType.LaunchOptions()
                        .setHeadless(isHeadless)
                        .setChannel("chrome");
                browser = playwright.chromium().launch(chromeOptions);
            } catch (Exception e) {
                log.warn("Chrome channel failed, falling back to default Chromium", e);
                // Fallback to default Chromium
                browser = playwright.chromium().launch(options);
            }

            Integer width = request.viewportWidth() != null ? request.viewportWidth() : properties.getViewportWidth();
            Integer height = request.viewportHeight() != null ? request.viewportHeight() : properties.getViewportHeight();

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(width, height));
            
            sessions.put(sessionId, new SessionState(browser, context));
            lastActivity.put(sessionId, Instant.now());
            return sessionId;
        } finally {
            sessionLock.unlock();
        }
    }

    public BrowserContext getSession(String sessionId) {
        sessionLock.lock();
        try {
            SessionState state = sessions.get(sessionId);
            if (state != null) {
                lastActivity.computeIfPresent(sessionId, (k, v) -> Instant.now()); // refresh on access
                return state.context();
            }
            return null;
        } finally {
            sessionLock.unlock();
        }
    }

    public void closeSession(String sessionId) {
        sessionLock.lock();
        try {
            SessionState state = sessions.remove(sessionId);
            lastActivity.remove(sessionId);
            if (state != null) {
                try {
                    state.context().close();
                } catch (Exception e) {
                    log.warn("Error closing browser context", e);
                }
                try {
                    state.browser().close();
                } catch (Exception e) {
                    log.warn("Error closing browser", e);
                }
            }
        } finally {
            sessionLock.unlock();
        }
    }

    /** Returns a read-only snapshot of active sessions. Callers must not mutate it. */
    public Map<String, BrowserContext> getActiveSessions() {
        return sessions.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> e.getValue().context()));
    }

    /**
     * Enforces the max-sessions limit by closing the oldest session when the cap is
     * reached.
     */
    private java.util.Optional<String> enforceSessionLimit() {
        int max = properties.getMaxSessions();
        if (sessions.size() >= max) {
            // Evict the session with the oldest last-activity timestamp
            return lastActivity.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey);
        }
        return java.util.Optional.empty();
    }

    /**
     * Scheduled task that closes sessions idle for longer than defaultTimeoutMs.
     * Runs every 2 minutes.
     */
    @Scheduled(fixedDelay = 120_000)
    public void cleanupIdleSessions() {
        long timeoutMs = properties.getDefaultTimeoutMs();
        Instant cutoff = Instant.now().minusMillis(timeoutMs);
        lastActivity.entrySet().stream()
                .filter(e -> e.getValue().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .toList() // snapshot to avoid ConcurrentModificationException
                .forEach(this::closeSession);
    }

    @PreDestroy
    public void cleanup() {
        sessionLock.lock();
        try {
            java.util.List.copyOf(sessions.keySet()).forEach(this::closeSession);
            if (playwright != null) {
                playwright.close();
                playwright = null;
            }
        } finally {
            sessionLock.unlock();
        }
    }
}
