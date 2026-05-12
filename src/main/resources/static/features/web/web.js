import { API } from '../../js/api.js';
import { state } from '../../js/state.js';

export async function initWeb() {
    // Web Tools Tab Switching (Keeping for future extensibility)
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
                    <span class="badge">Score: ${item.score.toFixed(2)}</span>
                </div>
                <h4><a href="${item.url}" target="_blank">${item.title || 'Untitled Page'}</a></h4>
                <p>${item.snippet || 'No snippet available.'}</p>
            </div>
        `).join('');
    } catch (e) { container.innerHTML = '<div class="empty-msg">Web search failed.</div>'; }
}
