package com.mcp.controller;

import com.mcp.dto.browser.*;
import com.mcp.entity.BrowserSession;
import com.mcp.service.BrowserSessionManager;
import com.mcp.service.HeadlessBrowserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.github.overrridee.annotation.ResponseEnvelope;
import io.github.overrridee.annotation.IgnoreEnvelope;

import java.util.List;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/browser")
@Validated
@ResponseEnvelope
@Tag(name = "Browser", description = "Headless browser automation for UI testing and web page interaction.")
public class BrowserController {

    public enum SessionStatus { ACTIVE, CLOSED }


    private final BrowserSessionManager sessionManager;
    private final HeadlessBrowserService browserService;

    public BrowserController(BrowserSessionManager sessionManager, HeadlessBrowserService browserService) {
        this.sessionManager = sessionManager;
        this.browserService = browserService;
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
	public BrowserSessionResponse createSession(@RequestBody @Valid BrowserSessionRequest request) {
		BrowserSession entity = sessionManager.createSessionEntity(request);
		return new BrowserSessionResponse(
				entity.getSessionId(), SessionStatus.ACTIVE.name(), entity.getCurrentUrl(), entity.getCreatedAt());
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
	public List<BrowserSessionResponse> listSessions(
			@Parameter(description = "Filter by project ID (optional)")
			@RequestParam(required = false) Long projectId) {
		return sessionManager.listSessions(projectId);
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
	@IgnoreEnvelope(reason = "204 No Content")
	public ResponseEntity<Void> closeSession(@Parameter(description = "Session ID", required = true) @PathVariable @NotBlank String sessionId) {
		try {
			sessionManager.closeSession(sessionId);
			return ResponseEntity.noContent().build();
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found", e);
		}
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
        description = "Perform a browser interaction within an active session. Select the action with the X-Action header:\\n\\n" +
            "• X-Action: navigate — Navigate to a URL. Body: {url: string}. Updates session's currentUrl.\\n\\n" +
            "• X-Action: screenshot — Capture current page screenshot. No body needed. " +
                "Returns {base64: string} (PNG image encoded as base64).\\n\\n" +
            "• X-Action: click — Click an element. Body: {selector: string} (CSS selector or XPath).\\n\\n" +
            "• X-Action: fill — Set the value of an input field instantly. Body: {selector: string, value: string}.\\n\\n" +
            "• X-Action: type — Type text keystroke-by-keystroke (simulates real typing). Body: {selector: string, text: string}.\\n\\n" +
            "• X-Action: select — Choose an option in a <select> dropdown. Body: {selector: string, value: string}.\\n\\n" +
            "• X-Action: wait — Wait until a DOM element appears. Body: {selector: string}. Blocks until visible.\\n\\n" +
            "• X-Action: evaluate — Execute arbitrary JavaScript in page context. Body: {script: string}. " +
                "Returns {result: any} with the script's return value.\\n\\n" +
            "• X-Action: extract-locators — Navigate to a URL and extract all interactive elements. " +
                "Body: {url: string}. Returns {locators: [{type, selector, label}]} for all clickable, " +
                "fillable, and selectable elements on the page.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Action completed; response body varies by X-Action"),
            @ApiResponse(responseCode = "400", description = "Missing required body field or unknown X-Action"),
            @ApiResponse(responseCode = "404", description = "Session not found")
        }
    )
	public Object browserAction(
			@Parameter(description = "Session ID", required = true) @PathVariable @NotBlank String sessionId,
			@Parameter(description = "Action: navigate | screenshot | click | fill | type | select | wait | evaluate | extract-locators", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"navigate", "screenshot", "click", "fill", "type", "select", "wait", "evaluate", "extract-locators"}))
			@RequestHeader(value = "X-Action") @NotBlank @Pattern(regexp = "^(navigate|screenshot|click|fill|type|select|wait|evaluate|extract-locators)$", message = "Invalid X-Action header value") String action,
			@RequestBody(required = false) Map<String, Object> body) {
		Object response = browserService.executeBrowserAction(sessionId, action, body);
		if (response == null) {
			return new Object();
		}
		return response;
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
        description = "Read the current state of the browser page within a session. Select the view with X-View:\\n\\n" +
            "• X-View: content (default) — retrieve current page URL, title, and full HTML content. " +
                "Returns {url, title, content (HTML string)}. " +
                "Use this after navigation or actions to inspect the rendered page before further interactions.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Page state returned"),
            @ApiResponse(responseCode = "400", description = "Unknown X-View value"),
            @ApiResponse(responseCode = "404", description = "Session not found")
        }
    )
	public Object getSessionState(
			@Parameter(description = "Session ID", required = true) @PathVariable @NotBlank String sessionId,
			@Parameter(description = "View variant: 'content' (default)", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"content"}))
			@RequestHeader(value = "X-View", required = false, defaultValue = "content") @NotBlank @Pattern(regexp = "^(content)$", message = "Invalid X-View header value") String view) {
		return browserService.getSessionState(sessionId, view);
	}
}
