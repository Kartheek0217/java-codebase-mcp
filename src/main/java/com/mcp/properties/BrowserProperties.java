package com.mcp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mcp.browser")
public class BrowserProperties {

    private boolean headless = true;
    private String browserType = "chromium";
    private int viewportWidth = 1280;
    private int viewportHeight = 720;
    private int timeoutMs = 120000;
    private String screenshotDir = "data/screenshots";
    private int maxSessions = 10;

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public String getBrowserType() {
        return browserType;
    }

    public void setBrowserType(String browserType) {
        this.browserType = browserType;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public void setViewportWidth(int viewportWidth) {
        this.viewportWidth = viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public void setViewportHeight(int viewportHeight) {
        this.viewportHeight = viewportHeight;
    }

    public String getScreenshotDir() {
        return screenshotDir;
    }

    public void setScreenshotDir(String screenshotDir) {
        this.screenshotDir = screenshotDir;
    }

    /** Alias used by BrowserSessionManager to read the idle-session timeout. */
    public long getDefaultTimeoutMs() {
        return timeoutMs;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }
}
