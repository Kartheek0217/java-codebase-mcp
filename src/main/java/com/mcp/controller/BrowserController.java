package com.mcp.controller;

import com.mcp.dto.browser.*;
import com.mcp.entity.BrowserSession;
import com.mcp.repository.BrowserSessionRepository;
import com.mcp.service.BrowserSessionManager;
import com.mcp.service.HeadlessBrowserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/browser")
@Tag(name = "Browser", description = "Headless browser automation for UI testing and web page interaction.")
public class BrowserController {

    private final BrowserSessionManager sessionManager;
    private final HeadlessBrowserService browserService;
    private final BrowserSessionRepository repository;

    public BrowserController(BrowserSessionManager sessionManager, HeadlessBrowserService browserService,
            BrowserSessionRepository repository) {
        this.sessionManager = sessionManager;
        this.browserService = browserService;
        this.repository = repository;
    }

    // ─── Session lifecycle ────────────────────────────────────────────────────

    /**
     * {@code POST /api/browser/session} : Create a new headless browser session.
     *
     * @param request Session configuration
     * @return BrowserSessionResponse with new sessionId
     */
    @PostMapping("/session")
    @Operation(
        summary = "crt-session",
        description = "Initialize a new headless browser context. " +
            "Body: BrowserSessionRequest {projectId (Long), browserType ('chromium'|'firefox'|'webkit', default=chromium), " +
            "headless (boolean, default=true), viewportWidth (int, optional), viewportHeight (int, optional)}. " +
            "Returns BrowserSessionResponse {sessionId, status='ACTIVE', currentUrl, createdAt}. " +
            "Store the returned sessionId for all subsequent browser action calls.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Session created, returns {sessionId, status, currentUrl, createdAt}")
        }
    )
    public ResponseEntity<BrowserSessionResponse> createSession(@RequestBody BrowserSessionRequest request) {
        String sessionId = sessionManager.createSession(request);

        BrowserSession entity = new BrowserSession();
        entity.setSessionId(sessionId);
        entity.setProjectId(request.projectId());
        entity.setBrowserType(request.browserType() != null ? request.browserType() : "chromium");
        entity.setHeadless(request.headless() != null ? request.headless() : true);
        if (request.viewportWidth() != null) entity.setViewportWidth(request.viewportWidth());
        if (request.viewportHeight() != null) entity.setViewportHeight(request.viewportHeight());

        repository.save(entity);

        return ResponseEntity.ok(new BrowserSessionResponse(
                sessionId, "ACTIVE", null, entity.getCreatedAt()));
    }

    /**
     * {@code GET /api/browser/session} : List all browser sessions.
     *
     * @param projectId Optional filter by project ID
     * @return List of BrowserSessionResponse
     */
    @GetMapping("/session")
    @Operation(
        summary = "lst-sessions",
        description = "List all active or historical browser sessions. " +
            "Query param: projectId (Long, optional) — filter sessions by project. " +
            "If omitted, returns sessions across all projects. " +
            "Returns list of {sessionId, status (ACTIVE|CLOSED), currentUrl, createdAt}.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Session list returned")
        }
    )
    public ResponseEntity<List<BrowserSessionResponse>> listSessions(
            @Parameter(description = "Filter by project ID (optional)")
            @RequestParam(required = false) Long projectId) {
        List<BrowserSession> entities = projectId != null
                ? repository.findByProjectId(projectId)
                : repository.findAll();
        return ResponseEntity.ok(entities.stream()
                .map(e -> new BrowserSessionResponse(
                        e.getSessionId(), e.getStatus(), e.getCurrentUrl(), e.getCreatedAt()))
                .collect(Collectors.toList()));
    }

    /**
     * {@code DELETE /api/browser/session/{sessionId}} : Close and clean up a browser session.
     *
     * @param sessionId Session ID to close
     */
    @DeleteMapping("/session/{sessionId}")
    @Operation(
        summary = "close-session",
        description = "Terminate a browser session and release all Playwright resources. " +
            "Path param: sessionId (string). " +
            "Marks the session as CLOSED in the database. " +
            "After closing, any action calls with this sessionId will fail.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Session closed successfully"),
            @ApiResponse(responseCode = "404", description = "Session not found")
        }
    )
    public ResponseEntity<Void> closeSession(@PathVariable String sessionId) {
        sessionManager.closeSession(sessionId);
        repository.findBySessionId(sessionId).ifPresent(e -> {
            e.setStatus("CLOSED");
            repository.save(e);
        });
        return ResponseEntity.noContent().build();
    }

    // ─── Session actions (POST) ───────────────────────────────────────────────

    /**
     * {@code POST /api/browser/session/{sessionId}} : Perform a browser action.
     * Action selected via {@code X-Action} header.
     *
     * @param sessionId Session ID
     * @param action    Action name (see description)
     * @param body      Action-specific request body (see description)
     * @return Action-specific response or void
     */
    @PostMapping("/session/{sessionId}")
    @Operation(
        summary = "browser-action",
        description = "Perform a browser interaction within an active session. Select the action with the X-Action header:\n\n" +
            "• X-Action: navigate — Navigate to a URL. Body: {url: string}. Updates session's currentUrl.\n\n" +
            "• X-Action: screenshot — Capture current page screenshot. No body needed. " +
                "Returns {base64: string} (PNG image encoded as base64).\n\n" +
            "• X-Action: click — Click an element. Body: {selector: string} (CSS selector or XPath).\n\n" +
            "• X-Action: fill — Set the value of an input field instantly. Body: {selector: string, value: string}.\n\n" +
            "• X-Action: type — Type text keystroke-by-keystroke (simulates real typing). Body: {selector: string, text: string}.\n\n" +
            "• X-Action: select — Choose an option in a <select> dropdown. Body: {selector: string, value: string}.\n\n" +
            "• X-Action: wait — Wait until a DOM element appears. Body: {selector: string}. Blocks until visible.\n\n" +
            "• X-Action: evaluate — Execute arbitrary JavaScript in page context. Body: {script: string}. " +
                "Returns {result: any} with the script's return value.\n\n" +
            "• X-Action: extract-locators — Navigate to a URL and extract all interactive elements. " +
                "Body: {url: string}. Returns {locators: [{type, selector, label}]} for all clickable, " +
                "fillable, and selectable elements on the page.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Action completed; response body varies by X-Action"),
            @ApiResponse(responseCode = "400", description = "Missing required body field or unknown X-Action"),
            @ApiResponse(responseCode = "404", description = "Session not found")
        }
    )
    public ResponseEntity<Object> browserAction(
            @PathVariable String sessionId,
            @Parameter(description = "Action: navigate | screenshot | click | fill | type | select | wait | evaluate | extract-locators")
            @RequestHeader(value = "X-Action") String action,
            @RequestBody(required = false) java.util.Map<String, Object> body) {

        String selector = body != null ? (String) body.get("selector") : null;
        String value    = body != null ? (String) body.get("value")    : null;
        String url      = body != null ? (String) body.get("url")      : null;
        String text     = body != null ? (String) body.get("text")     : null;
        String script   = body != null ? (String) body.get("script")   : null;

        return switch (action.toLowerCase()) {
            case "navigate" -> {
                requireField(url, "url", "navigate");
                browserService.navigate(sessionId, url);
                updateSession(sessionId, url);
                yield ResponseEntity.ok().build();
            }
            case "screenshot" -> {
                String base64 = browserService.screenshot(sessionId);
                yield ResponseEntity.ok(new ScreenshotResponse(base64));
            }
            case "click" -> {
                requireField(selector, "selector", "click");
                browserService.click(sessionId, selector);
                yield ResponseEntity.ok().build();
            }
            case "fill" -> {
                requireField(selector, "selector", "fill");
                requireField(value, "value", "fill");
                browserService.fill(sessionId, selector, value);
                yield ResponseEntity.ok().build();
            }
            case "type" -> {
                requireField(selector, "selector", "type");
                requireField(text, "text", "type");
                browserService.type(sessionId, selector, text);
                yield ResponseEntity.ok().build();
            }
            case "select" -> {
                requireField(selector, "selector", "select");
                requireField(value, "value", "select");
                browserService.selectOption(sessionId, selector, value);
                yield ResponseEntity.ok().build();
            }
            case "wait" -> {
                requireField(selector, "selector", "wait");
                browserService.waitForSelector(sessionId, selector);
                yield ResponseEntity.ok().build();
            }
            case "evaluate" -> {
                requireField(script, "script", "evaluate");
                Object result = browserService.evaluate(sessionId, script);
                yield ResponseEntity.ok(new EvaluateResponse(result));
            }
            case "extract-locators" -> {
                requireField(url, "url", "extract-locators");
                List<LocatorInfo> locators = browserService.extractLocators(sessionId, url);
                updateSession(sessionId, url);
                yield ResponseEntity.ok(new ExtractLocatorsResponse(locators));
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown X-Action value '" + action + "'. Allowed: navigate, screenshot, click, fill, type, select, wait, evaluate, extract-locators");
        };
    }

    // ─── Session read (GET) ───────────────────────────────────────────────────

    /**
     * {@code GET /api/browser/session/{sessionId}} : Read current browser page state.
     *
     * @param sessionId Session ID
     * @param view      {@code content} — full page URL + title + HTML
     * @return PageContentResponse
     */
    @GetMapping("/session/{sessionId}")
    @Operation(
        summary = "get-session-state",
        description = "Read the current state of the browser page within a session. Select the view with X-View:\n\n" +
            "• X-View: content (default) — retrieve current page URL, title, and full HTML content. " +
                "Returns {url, title, content (HTML string)}. " +
                "Use this after navigation or actions to inspect the rendered page before further interactions.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Page state returned"),
            @ApiResponse(responseCode = "400", description = "Unknown X-View value"),
            @ApiResponse(responseCode = "404", description = "Session not found")
        }
    )
    public ResponseEntity<Object> getSessionState(
            @PathVariable String sessionId,
            @Parameter(description = "View variant: 'content' (default)")
            @RequestHeader(value = "X-View", required = false, defaultValue = "content") String view) {
        return switch (view.toLowerCase()) {
            case "content" -> {
                String url     = browserService.getUrl(sessionId);
                String title   = browserService.getTitle(sessionId);
                String content = browserService.getContent(sessionId);
                yield ResponseEntity.ok(new PageContentResponse(url, title, content));
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown X-View value '" + view + "'. Allowed: content");
        };
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void updateSession(String sessionId, String url) {
        repository.findBySessionId(sessionId).ifPresent(e -> {
            e.setCurrentUrl(url);
            e.setLastActive(LocalDateTime.now());
            repository.save(e);
        });
    }

    private void requireField(String value, String field, String action) {
        if (value == null || value.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Body field '" + field + "' is required for X-Action=" + action);
    }
}
