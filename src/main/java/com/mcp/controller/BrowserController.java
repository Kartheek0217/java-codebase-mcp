package com.mcp.controller;

import com.mcp.dto.browser.*;
import com.mcp.entity.BrowserSession;
import com.mcp.repository.BrowserSessionRepository;
import com.mcp.service.BrowserSessionManager;
import com.mcp.service.HeadlessBrowserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/browser")
@Tag(name = "Browser", description = "Endpoints for headless browser operations and UI testing.")
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

    @PostMapping("/session")
    @Operation(summary = "crt-session", description = "Initializes a new headless browser context.")
    public ResponseEntity<BrowserSessionResponse> createSession(@RequestBody BrowserSessionRequest request) {
        String sessionId = sessionManager.createSession();

        BrowserSession entity = new BrowserSession();
        entity.setSessionId(sessionId);
        entity.setProjectId(request.projectId());
        entity.setBrowserType(request.browserType() != null ? request.browserType() : "chromium");
        entity.setHeadless(request.headless() != null ? request.headless() : true);
        if (request.viewportWidth() != null)
            entity.setViewportWidth(request.viewportWidth());
        if (request.viewportHeight() != null)
            entity.setViewportHeight(request.viewportHeight());

        repository.save(entity);

        return ResponseEntity.ok(new BrowserSessionResponse(
                sessionId, "ACTIVE", null, entity.getCreatedAt()));
    }

    @GetMapping("/session")
    @Operation(summary = "lst-sessions", description = "Returns a list of all active or historical browser sessions.")
    public ResponseEntity<List<BrowserSessionResponse>> listSessions(
            @Parameter(description = "Filter by project ID") @RequestParam(required = false) Long projectId) {
        List<BrowserSession> entities = projectId != null ? repository.findByProjectId(projectId)
                : repository.findAll();

        return ResponseEntity.ok(entities.stream()
                .map(e -> new BrowserSessionResponse(
                        e.getSessionId(), e.getStatus(), e.getCurrentUrl(), e.getCreatedAt()))
                .collect(Collectors.toList()));
    }

    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "close-session", description = "Terminates the browser context and cleans up resources.")
    public ResponseEntity<Void> closeSession(@PathVariable String sessionId) {
        sessionManager.closeSession(sessionId);
        repository.findBySessionId(sessionId).ifPresent(e -> {
            e.setStatus("CLOSED");
            repository.save(e);
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/session/{sessionId}/navigate")
    @Operation(summary = "navigate", description = "Navigates the browser to the specified web address.")
    public ResponseEntity<Void> navigate(@PathVariable String sessionId, @RequestBody NavigateRequest request) {
        browserService.navigate(sessionId, request.url());
        updateSession(sessionId, request.url());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/session/{sessionId}/screenshot")
    @Operation(summary = "screenshot", description = "Captures a screenshot of the current page as a base64 encoded string.")
    public ResponseEntity<ScreenshotResponse> screenshot(@PathVariable String sessionId) {
        String base64 = browserService.screenshot(sessionId);
        return ResponseEntity.ok(new ScreenshotResponse(base64));
    }

    @PostMapping("/session/{sessionId}/click")
    @Operation(summary = "click", description = "Performs a mouse click on the element matching the selector.")
    public ResponseEntity<Void> click(@PathVariable String sessionId, @RequestBody ClickRequest request) {
        browserService.click(sessionId, request.selector());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/session/{sessionId}/fill")
    @Operation(summary = "fill", description = "Sets the value of an input field matching the selector.")
    public ResponseEntity<Void> fill(@PathVariable String sessionId, @RequestBody FillRequest request) {
        browserService.fill(sessionId, request.selector(), request.value());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/session/{sessionId}/type")
    @Operation(summary = "type", description = "Types text character-by-character into the element matching the selector, simulating real keystrokes.")
    public ResponseEntity<Void> type(@PathVariable String sessionId, @RequestBody TypeRequest request) {
        browserService.type(sessionId, request.selector(), request.text());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/session/{sessionId}/select")
    @Operation(summary = "select-option", description = "Selects an option by value from a <select> element matching the selector.")
    public ResponseEntity<Void> selectOption(@PathVariable String sessionId, @RequestBody SelectOptionRequest request) {
        browserService.selectOption(sessionId, request.selector(), request.value());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/session/{sessionId}/wait")
    @Operation(summary = "wait-selector", description = "Waits until an element matching the selector appears in the DOM.")
    public ResponseEntity<Void> waitForSelector(@PathVariable String sessionId,
            @RequestBody WaitForSelectorRequest request) {
        browserService.waitForSelector(sessionId, request.selector());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/session/{sessionId}/evaluate")
    @Operation(summary = "evaluate", description = "Runs a script within the context of the page.")
    public ResponseEntity<EvaluateResponse> evaluate(@PathVariable String sessionId,
            @RequestBody EvaluateRequest request) {
        Object result = browserService.evaluate(sessionId, request.script());
        return ResponseEntity.ok(new EvaluateResponse(result));
    }

    @GetMapping("/session/{sessionId}/content")
    @Operation(summary = "get-content", description = "Retrieves the current page URL, title, and HTML content.")
    public ResponseEntity<PageContentResponse> getContent(@PathVariable String sessionId) {
        String url = browserService.getUrl(sessionId);
        String title = browserService.getTitle(sessionId);
        String content = browserService.getContent(sessionId);
        return ResponseEntity.ok(new PageContentResponse(url, title, content));
    }

    @PostMapping("/session/{sessionId}/extract-locators")
    @Operation(summary = "extract-locators", description = "Navigates to a page and extracts all interactive locators with their types and labels.")
    public ResponseEntity<ExtractLocatorsResponse> extractLocators(
            @PathVariable String sessionId,
            @RequestBody ExtractLocatorsRequest request) {
        List<LocatorInfo> locators = browserService.extractLocators(sessionId, request.url());
        updateSession(sessionId, request.url());
        return ResponseEntity.ok(new ExtractLocatorsResponse(locators));
    }

    private void updateSession(String sessionId, String url) {
        repository.findBySessionId(sessionId).ifPresent(e -> {
            e.setCurrentUrl(url);
            e.setLastActive(LocalDateTime.now());
            repository.save(e);
        });
    }
}
