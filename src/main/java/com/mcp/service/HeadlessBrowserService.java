package com.mcp.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mcp.dto.browser.LocatorInfo;

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
    public List<LocatorInfo> extractLocators(String sessionId, String url) {
        if (url != null && !url.isEmpty()) {
            navigate(sessionId, url);
        }
        Page page = getOrCreatePage(sessionId);

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

                    const elements = document.querySelectorAll('input, select, textarea, button, a.btn, a.btn-secondary, [id^="stat-"], [id^="btn-"]');
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
                        
                        // 4. Try innerText or sibling text (for status/buttons)
                        if (!label) {
                            if (el.tagName === 'BUTTON' || el.tagName === 'A' || el.tagName === 'H3' || el.tagName === 'SPAN') {
                                label = el.innerText.trim();
                                if (!label && el.nextElementSibling) {
                                    label = el.nextElementSibling.innerText.trim();
                                } else if (!label && el.parentElement) {
                                     const p = el.closest('.stat-info') || el.closest('.stat-card');
                                     if (p) label = p.innerText.replace(el.innerText, '').trim();
                                }
                            }
                        }

                        // Remove emojis/icons
                        if (label) {
                             label = label.replace(/[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]/g, '').trim();
                             label = label.replace(/[\\u2700-\\u27BF]|\\u2B50|\\uD83C[\\uDF00-\\uDFFF]|\\uD83D[\\uDC00-\\uDE4F]/g, '').trim();
                        }

                        // 5. Special case for tabs
                        if (el.hasAttribute('data-tab')) {
                            const tabName = el.getAttribute('data-tab');
                            if (!label || label.length < 3) label = tabName;
                            if (!label.toLowerCase().includes('tab')) label += ' Tab';
                        }
                        
                        // 6. Cleanup label (capitalize first letters)
                        if (label) {
                            label = label.replace(/^btn-/, '').replace(/^stat-/, '').replace(/-/g, ' ');
                            label = label.split(/\\s+/).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ');
                        }

                        // Locator Strategy
                        let locator = '';
                        if (el.hasAttribute('data-tab')) {
                            locator = `${el.tagName.toLowerCase()}[data-tab="${el.getAttribute('data-tab')}"]`;
                        } else if (el.id) {
                            locator = `#${el.id}`;
                        } else if (el.name) {
                            locator = `[name="${el.name}"]`;
                        } else {
                            locator = el.tagName.toLowerCase();
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
