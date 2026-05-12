import { API } from '../../js/api.js';
import { state } from '../../js/state.js';

export async function initWeb() {
    // Tab Switching
    document.querySelectorAll('.web-nav-item').forEach(item => {
        item.addEventListener('click', () => {
            const tab = item.getAttribute('data-web-tab');
            switchWebTab(tab);
        });
    });

    // Web Search
    document.getElementById('btn-web-search').addEventListener('click', performWebSearch);
    document.getElementById('web-search-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') performWebSearch();
    });

    // Direct Extraction
    document.getElementById('btn-direct-extract').addEventListener('click', () => {
        const url = document.getElementById('direct-extract-url').value;
        if (url) performQuickExtraction(url);
    });
    document.getElementById('direct-extract-url').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            const url = document.getElementById('direct-extract-url').value;
            if (url) performQuickExtraction(url);
        }
    });

    // Extraction Modal
    document.getElementById('btn-close-extraction').addEventListener('click', () => {
        document.getElementById('modal-extraction').classList.remove('active');
    });

    // Close modal on outside click
    window.addEventListener('click', (e) => {
        const modal = document.getElementById('modal-extraction');
        if (e.target === modal) modal.classList.remove('active');
    });
}

function switchWebTab(tabId) {
    state.webTab = tabId;
    document.querySelectorAll('.web-nav-item').forEach(i => i.classList.remove('active'));
    document.querySelector(`[data-web-tab="${tabId}"]`).classList.add('active');

    document.querySelectorAll('.web-pane').forEach(p => p.classList.remove('active'));
    document.getElementById(`web-tab-${tabId}`).classList.add('active');
}

async function performWebSearch() {
    const query = document.getElementById('web-search-input').value;
    if (!query || !state.selectedProjectId) return;

    const site = document.getElementById('web-search-site').value;
    const limit = document.getElementById('web-search-limit').value;
    const container = document.getElementById('web-search-results');
    container.innerHTML = '<div class="empty-msg">Searching the web...</div>';

    try {
        const data = await API.web.search(state.selectedProjectId, query, site, limit);
        if (!data || data.length === 0) {
            container.innerHTML = '<div class="empty-msg">No web results found.</div>';
            return;
        }

        container.innerHTML = data.map(item => `
            <div class="search-result-item">
                <div class="search-result-header">
                    <a href="${item.url}" target="_blank" class="result-path">${item.url}</a>
                    <div class="result-actions">
                        <button class="btn-icon btn-extract" data-url="${item.url}" title="Extract Page Data">
                            <i class="codicon codicon-cloud-download"></i> Extract
                        </button>
                        <span class="badge">Score: ${item.score.toFixed(2)}</span>
                    </div>
                </div>
                <h4><a href="${item.url}" target="_blank">${item.title || 'Untitled Page'}</a></h4>
                <p>${item.snippet || 'No snippet available.'}</p>
            </div>
        `).join('');

        // Add event listeners to extract buttons
        container.querySelectorAll('.btn-extract').forEach(btn => {
            btn.addEventListener('click', () => performQuickExtraction(btn.getAttribute('data-url')));
        });

    } catch (e) {
        console.error(e);
        container.innerHTML = '<div class="empty-msg">Web search failed.</div>';
    }
}

async function performQuickExtraction(url) {
    if (!state.selectedProjectId) {
        alert("Please select a project first.");
        return;
    }

    const modal = document.getElementById('modal-extraction');
    const loading = document.getElementById('extraction-loading');
    const content = document.getElementById('extraction-content');

    modal.classList.add('active');
    loading.classList.remove('hidden');
    content.classList.add('hidden');

    try {
        const data = await API.web.quickExtract(state.selectedProjectId, url);

        loading.classList.add('hidden');
        content.classList.remove('hidden');

        if (data.error) {
            document.getElementById('extraction-text').innerHTML = `<div class="error-msg">Extraction failed: ${data.error}</div>`;
            return;
        }

        document.getElementById('extraction-title').innerText = data.title || 'No Title';
        document.getElementById('extraction-url').innerText = data.url;
        document.getElementById('extraction-text').innerText = data.content || 'No content extracted.';

        const metaContainer = document.getElementById('extraction-meta');
        if (data.metadata && Object.keys(data.metadata).length > 0) {
            metaContainer.innerHTML = Object.entries(data.metadata)
                .map(([k, v]) => `<div style="margin-bottom: 8px; border-bottom: 1px solid var(--border); padding-bottom: 4px;"><strong>${k}:</strong> ${v}</div>`)
                .join('');
        } else {
            metaContainer.innerHTML = '<div class="empty-msg">No metadata found.</div>';
        }

    } catch (e) {
        loading.classList.add('hidden');
        content.classList.remove('hidden');
        document.getElementById('extraction-text').innerHTML = `<div class="error-msg">API Error: ${e.message}</div>`;
    }
}
