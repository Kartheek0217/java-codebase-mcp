package com.mcp.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mcp.dto.browser.BrowserSessionRequest;
import com.mcp.dto.browser.BrowserSessionResponse;
import com.mcp.entity.BrowserSession;
import com.mcp.properties.BrowserProperties;
import com.mcp.repository.BrowserSessionRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

@Service
public class BrowserSessionManager {

    private static final Logger log = LoggerFactory.getLogger(BrowserSessionManager.class);

    private final BrowserProperties properties;
    private final BrowserSessionRepository repository;
    private volatile Playwright playwright;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final ReentrantLock sessionLock = new ReentrantLock();

    /** Tracks the last-activity timestamp for each session (keyed by sessionId). */
    private final Map<String, Instant> lastActivity = new ConcurrentHashMap<>();

    private record SessionState(Browser browser, BrowserContext context) {}

    public BrowserSessionManager(BrowserProperties properties, BrowserSessionRepository repository) {
        this.properties = properties;
        this.repository = repository;
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

    public String createSession(BrowserSessionRequest request) {
        BrowserSession entity = createSessionEntity(request);
        return entity.getSessionId();
    }

    // Lock-protected: enforceSessionLimit() check + ensurePlaywrightInitialized() +
    // session creation must be atomic to prevent TOCTOU races under concurrent calls.
    public BrowserSession createSessionEntity(BrowserSessionRequest request) {
        sessionLock.lock();
        try {
            Optional<String> evictedId = enforceSessionLimit();
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

            BrowserSession entity = new BrowserSession();
            entity.setSessionId(sessionId);
            entity.setProjectId(request.projectId());
            entity.setBrowserType(request.browserType() != null ? request.browserType() : "chromium");
            entity.setHeadless(request.headless() != null ? request.headless() : true);
            if (request.viewportWidth() != null) entity.setViewportWidth(request.viewportWidth());
            if (request.viewportHeight() != null) entity.setViewportHeight(request.viewportHeight());

            return repository.save(entity);
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
            repository.findBySessionId(sessionId).ifPresent(e -> {
                e.setStatus("CLOSED");
                repository.save(e);
            });
        } finally {
            sessionLock.unlock();
        }
    }

    /** Returns a read-only snapshot of active sessions. Callers must not mutate it. */
    public Map<String, BrowserContext> getActiveSessions() {
        return sessions.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        e -> e.getKey(), e -> e.getValue().context()));
    }

    public void updateSession(String sessionId, String url) {
        repository.findBySessionId(sessionId).ifPresent(e -> {
            e.setCurrentUrl(url);
            e.setLastActive(LocalDateTime.now());
            repository.save(e);
        });
    }

    public List<BrowserSessionResponse> listSessions(Long projectId) {
        List<BrowserSession> entities = projectId != null
                ? repository.findByProjectId(projectId)
                : repository.findAll();
        return entities.stream()
                .map(e -> new BrowserSessionResponse(
                        e.getSessionId(), e.getStatus(), e.getCurrentUrl(), e.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * Enforces the max-sessions limit by closing the oldest session when the cap is
     * reached.
     */
    private Optional<String> enforceSessionLimit() {
        int max = properties.getMaxSessions();
        if (sessions.size() >= max) {
            // Evict the session with the oldest last-activity timestamp
            return lastActivity.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(e -> e.getKey());
        }
        return Optional.empty();
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
                .map(e -> e.getKey())
                .toList() // snapshot to avoid ConcurrentModificationException
                .forEach(this::closeSession);
    }

    @PreDestroy
    public void cleanup() {
        sessionLock.lock();
        try {
            List.copyOf(sessions.keySet()).forEach(this::closeSession);
            if (playwright != null) {
                playwright.close();
                playwright = null;
            }
        } finally {
            sessionLock.unlock();
        }
    }
}
