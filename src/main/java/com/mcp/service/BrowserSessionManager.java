package com.mcp.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mcp.properties.BrowserProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import jakarta.annotation.PreDestroy;

@Service
public class BrowserSessionManager {

    private final BrowserProperties properties;
    private Playwright playwright;
    private Browser browser;
    private final Map<String, BrowserContext> sessions = new ConcurrentHashMap<>();

    /** Tracks the last-activity timestamp for each session (keyed by sessionId). */
    private final Map<String, Instant> lastActivity = new ConcurrentHashMap<>();

    public BrowserSessionManager(BrowserProperties properties) {
        this.properties = properties;
    }

    private synchronized void ensurePlaywrightInitialized() {
        if (playwright == null) {
            playwright = Playwright.create();
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(properties.isHeadless());

            String type = properties.getBrowserType().toLowerCase();
            switch (type) {
                case "firefox":
                    browser = playwright.firefox().launch(options);
                    break;
                case "webkit":
                    browser = playwright.webkit().launch(options);
                    break;
                case "chromium":
                default:
                    browser = playwright.chromium().launch(options);
                    break;
            }
        }
    }

    // Synchronized: enforceSessionLimit() check + ensurePlaywrightInitialized() +
    // session creation must be atomic to prevent TOCTOU races under concurrent calls.
    public synchronized String createSession() {
        enforceSessionLimit();
        ensurePlaywrightInitialized();
        String sessionId = UUID.randomUUID().toString();
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(properties.getViewportWidth(), properties.getViewportHeight()));
        sessions.put(sessionId, context);
        lastActivity.put(sessionId, Instant.now());
        return sessionId;
    }

    public BrowserContext getSession(String sessionId) {
        if (sessions.containsKey(sessionId)) {
            lastActivity.put(sessionId, Instant.now()); // refresh on access
        }
        return sessions.get(sessionId);
    }

    public void closeSession(String sessionId) {
        BrowserContext context = sessions.remove(sessionId);
        lastActivity.remove(sessionId);
        if (context != null) {
            context.close();
        }
    }

    /** Returns a read-only snapshot of active sessions. Callers must not mutate it. */
    public Map<String, BrowserContext> getActiveSessions() {
        return Map.copyOf(sessions);
    }

    /**
     * Enforces the max-sessions limit by closing the oldest session when the cap is
     * reached.
     */
    private void enforceSessionLimit() {
        int max = properties.getMaxSessions();
        if (sessions.size() >= max) {
            // Evict the session with the oldest last-activity timestamp
            lastActivity.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .ifPresent(this::closeSession);
        }
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
                .forEach(id -> {
                    closeSession(id);
                });
    }

    @PreDestroy
    public void cleanup() {
        sessions.values().forEach(BrowserContext::close);
        sessions.clear();
        lastActivity.clear();
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
