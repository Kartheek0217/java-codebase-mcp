package com.mcp.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.browser.LocatorInfo;
import com.mcp.dto.browser.ScreenshotResponse;
import com.mcp.dto.browser.EvaluateResponse;
import com.mcp.dto.browser.ExtractLocatorsResponse;
import com.mcp.dto.browser.PageContentResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

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

    public Object executeBrowserAction(String sessionId, String action, Map<String, Object> body) {
        if (action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Action header is required");
        }
        String selector = body != null ? (String) body.get("selector") : null;
        String value    = body != null ? (String) body.get("value")    : null;
        String url      = body != null ? (String) body.get("url")      : null;
        String text     = body != null ? (String) body.get("text")     : null;
        String script   = body != null ? (String) body.get("script")   : null;

        return switch (action.toLowerCase()) {
            case "navigate" -> {
                requireField(url, "url", "navigate");
                navigate(sessionId, url);
                sessionManager.updateSession(sessionId, url);
                yield null;
            }
            case "screenshot" -> {
                String base64 = screenshot(sessionId);
                yield new ScreenshotResponse(base64);
            }
            case "click" -> {
                requireField(selector, "selector", "click");
                click(sessionId, selector);
                yield null;
            }
            case "fill" -> {
                requireField(selector, "selector", "fill");
                requireField(value, "value", "fill");
                fill(sessionId, selector, value);
                yield null;
            }
            case "type" -> {
                requireField(selector, "selector", "type");
                requireField(text, "text", "type");
                type(sessionId, selector, text);
                yield null;
            }
            case "select" -> {
                requireField(selector, "selector", "select");
                requireField(value, "value", "select");
                selectOption(sessionId, selector, value);
                yield null;
            }
            case "wait" -> {
                requireField(selector, "selector", "wait");
                waitForSelector(sessionId, selector);
                yield null;
            }
            case "evaluate" -> {
                requireField(script, "script", "evaluate");
                Object result = evaluate(sessionId, script);
                yield new EvaluateResponse(result);
            }
            case "extract-locators" -> {
                requireField(url, "url", "extract-locators");
                List<LocatorInfo> locators = extractLocators(sessionId, url);
                sessionManager.updateSession(sessionId, url);
                yield new ExtractLocatorsResponse(locators);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown X-Action value '" + action + "'. Allowed: navigate, screenshot, click, fill, type, select, wait, evaluate, extract-locators");
        };
    }

    public PageContentResponse getSessionState(String sessionId, String view) {
        if (view == null) {
            view = "content";
        }
        return switch (view.toLowerCase()) {
            case "content" -> {
                String url     = getUrl(sessionId);
                String title   = getTitle(sessionId);
                String content = getContent(sessionId);
                yield new PageContentResponse(url, title, content);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown X-View value '" + view + "'. Allowed: content");
        };
    }

    private void requireField(String value, String field, String action) {
        if (value == null || value.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Body field '" + field + "' is required for X-Action=" + action);
    }

    @SuppressWarnings("unchecked")
    public List<LocatorInfo> extractLocators(String sessionId, String url) {
        Page page = getOrCreatePage(sessionId);
        if (url != null && !url.isEmpty()) {
            page.navigate(url);
        }

        // Wait for stability
        try {
            page.waitForLoadState(LoadState.LOAD);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(60000); // stableTime as requested by user
        } catch (Exception e) {
            // Log or ignore timeout errors if some resources fail to load
        }

        String script = """
                () => {
                    const getXPath = (el) => {
                        if (el.id) return `//*[@id="${el.id}"]`;
                        const parts = [];
                        while (el && el.nodeType === 1) {
                            let index = 0;
                            for (let sibling = el.previousSibling; sibling; sibling = sibling.previousSibling) {
                                if (sibling.nodeType === el.nodeType && sibling.tagName === el.tagName) index++;
                            }
                            const tagName = el.tagName.toLowerCase();
                            const pathIndex = index > 0 || (el.nextSibling && [...el.parentNode.children].filter(c => c.tagName === el.tagName).length > 1) ? `[${index + 1}]` : '';
                            parts.unshift(`${tagName}${pathIndex}`);
                            el = el.parentNode;
                        }
                        return parts.length ? '/' + parts.join('/') : null;
                    };

                    const isVisible = (el) => {
                        if (!el) return false;
                        const style = window.getComputedStyle(el);
                        return style.display !== 'none' &&
                               style.visibility !== 'hidden' &&
                               style.opacity !== '0' &&
                               el.offsetWidth > 0 &&
                               el.offsetHeight > 0;
                    };

                    const getAllElements = (root = document) => {
                        let elements = Array.from(root.querySelectorAll('*'));
                        const all = [...elements];
                        for (const el of elements) {
                            if (el.shadowRoot) {
                                all.push(...getAllElements(el.shadowRoot));
                            }
                        }
                        return all;
                    };

                    const interactiveSelectors = 'input, select, textarea, button, a, [role="button"], [role="link"], [data-test], [data-testid], [data-qa], [data-cy]';
                    const allElements = getAllElements();
                    const elements = allElements.filter(el => {
                        try {
                            return el.matches && el.matches(interactiveSelectors) && isVisible(el);
                        } catch (e) {
                            return false;
                        }
                    });

                    return Array.from(new Set(elements)).map(el => {
                        let label = '';

                        // 1. Try finding explicit label
                        if (el.id) {
                            const labelEl = document.querySelector(`label[for="${el.id}"]`);
                            if (labelEl) label = labelEl.innerText.trim();
                        }

                        // 2. Try closest label parent
                        if (!label) {
                            const parentLabel = el.closest('label');
                            if (parentLabel) label = parentLabel.innerText.trim();
                        }

                        // 3. Try aria-label, placeholder, or title
                        if (!label) label = el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.getAttribute('title') || '';

                        // 4. Try innerText or sibling text
                        if (!label) {
                            label = el.innerText.trim();
                            if (!label && el.nextElementSibling) {
                                label = el.nextElementSibling.innerText.trim();
                            }
                        }

                        // Remove emojis/icons
                        if (label) {
                             label = label.replace(/[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]/g, '').trim();
                             label = label.replace(/[\\u2700-\\u27BF]|\\u2B50|\\uD83C[\\uDF00-\\uDFFF]|\\uD83D[\\uDC00-\\uDE4F]/g, '').trim();
                        }

                        // Cleanup label
                        if (label) {
                            label = label.replace(/^btn-/, '').replace(/^stat-/, '').replace(/-/g, ' ');
                            label = label.split(/\\s+/).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ');
                        }

                        // Locator Strategy Priority
                        let locator = '';

                        // 1. data-* attributes (best for stability)
                        const dataAttr = Array.from(el.attributes).find(a =>
                            ['data-test', 'data-testid', 'data-qa', 'data-cy'].includes(a.name) ||
                            (a.name.startsWith('data-') && !['data-v-'].some(p => a.name.startsWith(p)))
                        );

                        if (dataAttr) {
                            locator = `[${dataAttr.name}="${dataAttr.value}"]`;
                        }

                        // 2. Role + Name (accessible roles / ARIA)
                        if (!locator) {
                            const role = el.getAttribute('role') ||
                                         (el.tagName === 'BUTTON' ? 'button' :
                                          el.tagName === 'A' ? 'link' :
                                          el.tagName === 'INPUT' ? (['checkbox', 'radio'].includes(el.type) ? el.type : 'textbox') :
                                          el.tagName === 'SELECT' ? 'combobox' :
                                          el.tagName === 'TEXTAREA' ? 'textbox' : '');

                            const ariaName = (el.getAttribute('aria-label') || el.innerText.trim() || el.getAttribute('placeholder') || '').replace(/\\s+/g, ' ').trim();

                            if (role && ariaName && ariaName.length > 0 && ariaName.length < 60) {
                                locator = `role=${role}[name="${ariaName.replace(/"/g, '\\\\\"')}"]`;
                            }
                        }

                        // 3. Unique Text (for buttons and links)
                        if (!locator && (el.tagName === 'BUTTON' || el.tagName === 'A')) {
                            const text = el.innerText.trim().replace(/\\s+/g, ' ');
                            if (text && text.length > 0 && text.length < 40) {
                                locator = `${el.tagName.toLowerCase()}:has-text("${text.replace(/"/g, '\\\\\"')}")`;
                            }
                        }

                        // 4. ID or Name (if stable looking)
                        if (!locator) {
                            if (el.id && !/\\d{4,}/.test(el.id) && !el.id.includes('v-')) { // avoid IDs with 4+ digits or vue-like IDs
                                locator = `#${el.id}`;
                            } else if (el.name) {
                                locator = `[name="${el.name}"]`;
                            }
                        }

                        // 5. CSS Selectors (Fallback)
                        if (!locator) {
                            locator = el.tagName.toLowerCase();
                            if (el.className && typeof el.className === 'string') {
                                const classes = el.className.split(/\\s+/).filter(c => c && !c.includes(':') && !/\\d/.test(c));
                                if (classes.length > 0) {
                                    locator += `.${classes[0]}`;
                                }
                            }
                        }

                        // 6. XPath (Last Resort)
                        if (!locator) {
                            locator = getXPath(el);
                        }

                        // Section Detection
                        const sectionEl = el.closest('aside, section, header, nav, .sidebar, .main-content');
                        let section = 'Page';
                        if (sectionEl) {
                            if (sectionEl.tagName === 'ASIDE' || sectionEl.classList.contains('sidebar')) section = 'Sidebar';
                            else if (sectionEl.tagName === 'HEADER') section = 'Header';
                            else if (sectionEl.id) section = sectionEl.id.replace('tab-', '').toUpperCase();
                            else if (sectionEl.classList.contains('main-content')) section = 'Main';
                        }

                        return {
                            locator: locator,
                            type: el.tagName.toLowerCase() === 'input' ? el.type : el.tagName.toLowerCase(),
                            label: label || locator,
                            name: el.name || '',
                            section: section,
                            xpath: getXPath(el)
                        };
                    });
                }
                """;

        List<Map<String, String>> result = (List<Map<String, String>>) page.evaluate(script);
        List<LocatorInfo> locators = new ArrayList<>();
        for (Map<String, String> map : result) {
            locators.add(new LocatorInfo(
                    map.get("locator"),
                    map.get("type"),
                    map.get("label"),
                    map.get("name"),
                    map.get("section"),
                    map.get("xpath")));
        }
        return locators;
    }
}
