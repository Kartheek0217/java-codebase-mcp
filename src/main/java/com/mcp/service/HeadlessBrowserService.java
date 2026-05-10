package com.mcp.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

@Service
public class HeadlessBrowserService {

    private final BrowserSessionManager sessionManager;

    public HeadlessBrowserService(BrowserSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "file");

    private Page getOrCreatePage(String sessionId) {
        BrowserContext context = sessionManager.getSession(sessionId);
        if (context == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }
        if (context.pages().isEmpty()) {
            return context.newPage();
        }
        return context.pages().get(0);
    }

    private void validateUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
                throw new IllegalArgumentException(
                    "Invalid URL scheme '" + scheme + "'. Only http, https, and file are allowed.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed URL: " + url, e);
        }
    }

    public void navigate(String sessionId, String url) {
        validateUrl(url);
        getOrCreatePage(sessionId).navigate(url);
    }

    public String screenshot(String sessionId) {
        byte[] screenshot = getOrCreatePage(sessionId).screenshot();
        return Base64.getEncoder().encodeToString(screenshot);
    }

    public void click(String sessionId, String selector) {
        getOrCreatePage(sessionId).click(selector);
    }

    public void fill(String sessionId, String selector, String value) {
        getOrCreatePage(sessionId).fill(selector, value);
    }

    public void type(String sessionId, String selector, String text) {
        getOrCreatePage(sessionId).locator(selector).pressSequentially(text);
    }

    public void selectOption(String sessionId, String selector, String value) {
        getOrCreatePage(sessionId).selectOption(selector, value);
    }

    public Object evaluate(String sessionId, String script) {
        return getOrCreatePage(sessionId).evaluate(script);
    }

    public String getContent(String sessionId) {
        return getOrCreatePage(sessionId).content();
    }

    public String getTitle(String sessionId) {
        return getOrCreatePage(sessionId).title();
    }

    public String getUrl(String sessionId) {
        return getOrCreatePage(sessionId).url();
    }

    public void waitForSelector(String sessionId, String selector) {
        getOrCreatePage(sessionId).waitForSelector(selector);
    }

    public String extractText(String sessionId, String selector) {
        return (String) getOrCreatePage(sessionId).evalOnSelector(selector, "el => el.innerText");
    }

    @SuppressWarnings("unchecked")
    public List<String> extractAllText(String sessionId, String selector) {
        return (List<String>) getOrCreatePage(sessionId).evalOnSelectorAll(selector,
                "elements => elements.map(el => el.innerText)");
    }
}
