package com.mcp.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mcp.dto.QuickExtractionResult;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

@Service
public class QuickExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(QuickExtractionService.class);
    private final BrowserSessionManager sessionManager;

    public QuickExtractionService(BrowserSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public QuickExtractionResult extract(Long projectId, String url) {
        logger.info("Performing quick extraction for URL: {}", url);
        String sessionId = null;
        BrowserContext context = null;
        try {
            // Create a temporary session for extraction
            sessionId = sessionManager.createSession();
            context = sessionManager.getSession(sessionId);
            Page page = context.newPage();

            page.navigate(url);
            // Wait for both load and network idle to handle SPAs like Vuetify
            page.waitForLoadState(LoadState.LOAD);
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
            } catch (Exception e) {
                logger.warn("Timeout waiting for network idle for {}, proceeding anyway", url);
            }

            String title = page.title();

            // Script to extract main content and some metadata
            String script = """
                        () => {
                            const metadata = {};
                            const metaTags = document.getElementsByTagName('meta');
                            for (let meta of metaTags) {
                                const name = meta.getAttribute('name') || meta.getAttribute('property');
                                const content = meta.getAttribute('content');
                                if (name && content) metadata[name] = content;
                            }

                            // Improved content extraction: filter noise and focus on visible text
                            const clone = document.body.cloneNode(true);
                            const noise = clone.querySelectorAll('script, style, nav, footer, iframe, noscript');
                            noise.forEach(n => n.remove());

                            const mainContent = clone.innerText
                                .split('\\n')
                                .map(line => line.trim())
                                .filter(line => line.length > 0)
                                .join('\\n');

                            return {
                                metadata: metadata,
                                content: mainContent.substring(0, 8000) // Slightly larger limit
                            };
                        }
                    """;

            @SuppressWarnings("unchecked")
            Map<String, Object> extraction = (Map<String, Object>) page.evaluate(script);

            @SuppressWarnings("unchecked")
            Map<String, String> metadata = (Map<String, String>) extraction.get("metadata");
            String contentText = (String) extraction.get("content");

            return new QuickExtractionResult(
                    url,
                    title,
                    contentText,
                    metadata,
                    null);

        } catch (Exception e) {
            logger.error("Extraction failed for URL: {}", url, e);
            return new QuickExtractionResult(url, null, null, null, e.getMessage());
        } finally {
            if (sessionId != null) {
                sessionManager.closeSession(sessionId);
            }
        }
    }
}
