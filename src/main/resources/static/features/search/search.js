import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { escapeHtml } from '../../js/ui.js';

export function initSearch() {
    document.getElementById('btn-search').addEventListener('click', performSearch);
    document.getElementById('search-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') performSearch();
    });

    document.querySelectorAll('.close-inline-viewer[data-viewer=\"search\"]').forEach(btn => {
        btn.addEventListener('click', () => {
            document.getElementById('search-layout').classList.remove('has-viewer');
            document.getElementById('search-viewer-pane').style.display = 'none';
        });
    });
}

async function performSearch() {
    const query = document.getElementById('search-input').value;
    if (!query || !state.selectedProjectId) return;

    const type = document.querySelector('input[name=\"search-type\"]:checked').value;
    const resultsContainer = document.getElementById('search-results');
    resultsContainer.innerHTML = '<div class=\"empty-msg\">Searching...</div>';

    try {
        let data;
        if (type === 'content') data = await API.index.searchContent(state.selectedProjectId, query);
        else if (type === 'files') data = await API.index.searchFiles(state.selectedProjectId, query);
        else if (type === 'symbols') data = await API.ai.getSymbols(state.selectedProjectId, query);

        renderSearchResults(data, type);
    } catch (e) {
        resultsContainer.innerHTML = '<div class=\"empty-msg\">Search failed</div>';
    }
}

function renderSearchResults(results, type) {
    const container = document.getElementById('search-results');
    if (!results || results.length === 0) {
        container.innerHTML = '<div class=\"empty-msg\">No results found</div>';
        return;
    }

    if (results.length > 0) {
        const firstResult = results[0];
        const firstPath = firstResult.filePath || firstResult.name;
        if (firstPath) window.viewFileContent(firstPath);
    }

    container.innerHTML = results.map(res => {
        if (type === 'content') {
            const matchesHtml = (res.matches || []).map(m => `
                <div class=\"match-item\">
                    <div class=\"match-header\">
                        <span>Line <span class=\"match-line-num\">${m.lineNumber}</span> in <code>${m.functionName}</code></span>
                    </div>
                    <div class=\"result-snippet\">${escapeHtml(m.lineContent)}</div>
                </div>
            `).join('');

            return `
                <div class=\"search-result-item\">
                    <div class=\"search-result-header\">
                        <span class=\"result-path\">${res.filePath} (Score: ${res.score.toFixed(2)})</span>
                        <button class=\"btn-secondary btn-small\" onclick=\"window.viewFileContent('${res.filePath.replace(/\\/g, '\\\\')}')\">View Full File</button>
                    </div>
                    ${matchesHtml}
                </div>
            `;
        } else if (type === 'files') {
            return `
                <div class=\"search-result-item\">
                    <div class=\"search-result-header\">
                        <span class=\"result-path\">${res.filePath}</span>
                        <button class=\"btn-secondary btn-small\" onclick=\"window.viewFileContent('${res.filePath.replace(/\\/g, '\\\\')}')\">View Full File</button>
                    </div>
                    <p>Size: ${res.fileSize} bytes | Last Modified: ${new Date(res.lastScanned).toLocaleString()}</p>
                </div>
            `;
        } else {
            return `
                <div class=\"search-result-item\">
                    <div class=\"search-result-header\">
                        <span class=\"result-path\">${res.name} (${res.type})</span>
                        <button class=\"btn-secondary btn-small\" onclick=\"window.viewFileContent('${res.filePath.replace(/\\/g, '\\\\')}')\">View Full File</button>
                    </div>
                    <p>File: ${res.filePath} | Line: ${res.lineNumber || res.startLine || 'N/A'}</p>
                </div>
            `;
        }
    }).join('');
}
