import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { showNotification } from '../../js/ui.js';

let currentSessionId = null;
let refreshInterval = null;
let lastExtractedLocators = [];

async function fetchSessions() {
    if (!state.selectedProjectId) return;
    try {
        const sessions = await API.browser.listSessions(state.selectedProjectId);
        renderSessionList(sessions);
    } catch (e) { console.error(e); }
}

// Compatibility exports for app.js
export async function fetchFiles() {
    await fetchSessions();
}

export async function viewFile(path) {
    // No-op for headless browser, but could be used to navigate to a file:// URL if needed
    console.log('viewFile called for headless browser with path:', path);
}

export async function initBrowser() {
    // Navigation
    document.getElementById('btn-navigate').addEventListener('click', navigate);
    document.getElementById('browser-url').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') navigate();
    });

    // Sessions
    document.getElementById('btn-start-session').addEventListener('click', createSession);
    document.getElementById('btn-manage-sessions').addEventListener('click', fetchSessions);

    // Actions
    document.getElementById('btn-screenshot').addEventListener('click', takeScreenshot);
    document.getElementById('btn-reload').addEventListener('click', takeScreenshot);
    document.getElementById('btn-click-selector').addEventListener('click', clickSelector);
    document.getElementById('btn-fill-selector').addEventListener('click', fillSelector);
    document.getElementById('btn-type-selector').addEventListener('click', typeSelector);
    document.getElementById('btn-select-selector').addEventListener('click', selectSelector);
    document.getElementById('btn-wait-selector').addEventListener('click', waitForSelector);
    document.getElementById('btn-extract-locators').addEventListener('click', extractLocators);
    document.getElementById('btn-download-locators').addEventListener('click', downloadLocators);

    await fetchSessions();
}

function renderSessionList(sessions) {
    const list = document.getElementById('session-list');
    list.innerHTML = sessions.map(s => `
        <li class="session-item ${s.sessionId === currentSessionId ? 'active' : ''}" data-id="${s.sessionId}">
            <div class="session-info">
                <span class="session-id">${s.sessionId.substring(0, 8)}...</span>
                <span class="session-status ${s.status.toLowerCase()}">${s.status}</span>
            </div>
            <button class="btn-close-session" data-id="${s.sessionId}">&times;</button>
        </li>
    `).join('') || '<li>No active sessions</li>';

    list.querySelectorAll('.session-item').forEach(item => {
        item.addEventListener('click', (e) => {
            if (e.target.classList.contains('btn-close-session')) return;
            selectSession(item.getAttribute('data-id'));
        });
    });

    list.querySelectorAll('.btn-close-session').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = btn.getAttribute('data-id');
            await API.browser.closeSession(id);
            if (currentSessionId === id) {
                currentSessionId = null;
                updateView();
            }
            fetchSessions();
        });
    });
}

async function createSession() {
    if (!state.selectedProjectId) return;
    try {
        showNotification('Launching browser...', 'info');
        const session = await API.browser.createSession(state.selectedProjectId);
        currentSessionId = session.sessionId;
        await fetchSessions();
        updateView();
        showNotification('Browser session started', 'success');
    } catch (e) {
        showNotification('Failed to start browser', 'error');
    }
}

function selectSession(id) {
    currentSessionId = id;
    fetchSessions();
    updateView();
    takeScreenshot();
}

async function updateView() {
    const placeholder = document.getElementById('browser-placeholder');
    const preview = document.getElementById('browser-preview');

    if (currentSessionId) {
        placeholder.classList.add('hidden');
        preview.classList.remove('hidden');
        updatePageInfo();
    } else {
        placeholder.classList.remove('hidden');
        preview.classList.add('hidden');
    }
}

async function navigate() {
    if (!currentSessionId) {
        await createSession();
    }
    const url = document.getElementById('browser-url').value;
    if (!url) return;

    try {
        showNotification('Navigating...', 'info');
        await API.browser.navigate(currentSessionId, url);
        await takeScreenshot();
        updatePageInfo();
    } catch (e) {
        showNotification('Navigation failed', 'error');
    }
}

async function takeScreenshot() {
    if (!currentSessionId) return;
    try {
        const data = await API.browser.screenshot(currentSessionId);
        const img = document.getElementById('screenshot-display');
        img.src = `data:image/png;base64,${data.base64Image}`;
    } catch (e) { console.error('Screenshot failed', e); }
}

async function updatePageInfo() {
    if (!currentSessionId) return;
    try {
        const data = await API.browser.getContent(currentSessionId);
        document.getElementById('info-title').innerText = data.title || 'No Title';
        document.getElementById('info-url').innerText = data.url;
        document.getElementById('browser-url').value = data.url;
    } catch (e) { console.error('Failed to get page info', e); }
}

async function clickSelector() {
    const selector = document.getElementById('action-selector').value;
    if (!currentSessionId || !selector) return;
    try {
        await API.browser.click(currentSessionId, selector);
        showNotification('Clicked element', 'success');
        setTimeout(takeScreenshot, 500);
    } catch (e) { showNotification('Click failed', 'error'); }
}

async function fillSelector() {
    const selector = document.getElementById('action-selector').value;
    const value = document.getElementById('action-value').value;
    if (!currentSessionId || !selector) return;
    try {
        await API.browser.fill(currentSessionId, selector, value);
        showNotification('Filled element', 'success');
        setTimeout(takeScreenshot, 500);
    } catch (e) { showNotification('Fill failed', 'error'); }
}

async function typeSelector() {
    const selector = document.getElementById('action-selector').value;
    const text = document.getElementById('action-value').value;
    if (!currentSessionId || !selector) return;
    try {
        await API.browser.type(currentSessionId, selector, text);
        showNotification('Typed text into element', 'success');
        setTimeout(takeScreenshot, 500);
    } catch (e) { showNotification('Type failed', 'error'); }
}

async function selectSelector() {
    const selector = document.getElementById('action-selector').value;
    const value = document.getElementById('action-value').value;
    if (!currentSessionId || !selector) return;
    try {
        await API.browser.selectOption(currentSessionId, selector, value);
        showNotification('Selected option', 'success');
        setTimeout(takeScreenshot, 500);
    } catch (e) { showNotification('Select failed', 'error'); }
}

async function waitForSelector() {
    const selector = document.getElementById('action-selector').value;
    if (!currentSessionId || !selector) return;
    try {
        showNotification('Waiting for element...', 'info');
        await API.browser.waitForSelector(currentSessionId, selector);
        showNotification('Element found!', 'success');
        setTimeout(takeScreenshot, 300);
    } catch (e) { showNotification('Element not found', 'error'); }
}

async function extractLocators() {
    if (!currentSessionId) return;
    const url = document.getElementById('browser-url').value;

    try {
        showNotification('Extracting locators...', 'info');
        const data = await API.browser.extractLocators(currentSessionId, url || null);
        lastExtractedLocators = data.locators;
        renderLocatorResults(data.locators);
        showNotification(`Found ${data.locators.length} locators`, 'success');
    } catch (e) {
        showNotification('Failed to extract locators', 'error');
    }
}

function renderLocatorResults(locators) {
    const section = document.getElementById('locator-results-section');
    const container = document.getElementById('locator-results');

    section.classList.remove('hidden');

    if (!locators || locators.length === 0) {
        container.innerHTML = '<div class="empty-msg">No locators found</div>';
        return;
    }

    // Group by section
    const grouped = locators.reduce((acc, loc) => {
        const s = loc.section || 'General';
        if (!acc[s]) acc[s] = [];
        acc[s].push(loc);
        return acc;
    }, {});

    container.innerHTML = Object.entries(grouped).map(([sectionName, items]) => `
        <div class="locator-group">
            <h6 class="locator-section-title">${sectionName}</h6>
            <div class="locator-group-items">
                ${items.map(loc => `
                    <div class="locator-item" onclick="document.getElementById('action-selector').value = '${loc.locator}'" title="Click to use CSS selector">
                        <div class="locator-header">
                            <span class="locator-tag">${loc.locator}</span>
                            <span class="locator-type">${loc.type}</span>
                        </div>
                        <div class="locator-label">${loc.label}</div>
                        <div class="locator-xpath-row" onclick="event.stopPropagation(); document.getElementById('action-selector').value = '${loc.xpath}'" title="Click to use XPath">
                             <code>${loc.xpath}</code>
                        </div>
                        ${loc.name ? `<div class="locator-name">name: ${loc.name}</div>` : ''}
                    </div>
                `).join('')}
            </div>
        </div>
    `).join('');
}

function downloadLocators() {
    if (!lastExtractedLocators || lastExtractedLocators.length === 0) {
        showNotification('No locators to download', 'warning');
        return;
    }
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(lastExtractedLocators, null, 2));
    const downloadAnchorNode = document.createElement('a');
    downloadAnchorNode.setAttribute("href", dataStr);
    downloadAnchorNode.setAttribute("download", `locators_${new Date().getTime()}.json`);
    document.body.appendChild(downloadAnchorNode);
    downloadAnchorNode.click();
    downloadAnchorNode.remove();
}
