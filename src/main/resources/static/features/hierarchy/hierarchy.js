import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { showNotification } from '../../js/ui.js';

export function initHierarchy() {
    const searchBtn = document.getElementById('hierarchy-search-btn');
    const searchInput = document.getElementById('hierarchy-symbol-search');

    searchBtn.addEventListener('click', () => searchSymbols());
    searchInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') searchSymbols();
    });
}

async function searchSymbols() {
    const query = document.getElementById('hierarchy-symbol-search').value.trim();
    if (!query || !state.selectedProjectId) return;

    const resultsContainer = document.getElementById('hierarchy-search-results');
    resultsContainer.innerHTML = '<p class="loading">Searching symbols...</p>';

    try {
        const symbols = await API.ai.getSymbols(state.selectedProjectId, query);
        const methods = symbols.filter(s => s.type === 'METHOD');

        if (methods.length === 0) {
            resultsContainer.innerHTML = '<p class="error">No methods found matching that query.</p>';
            return;
        }

        resultsContainer.innerHTML = methods.map(s => `
            <div class="symbol-result-item" data-id="${s.id}">
                <div class="symbol-info">
                    <span class="symbol-name">${s.name}</span>
                    <span class="symbol-file">${s.filePath.split('/').pop()}</span>
                </div>
                <button class="btn-secondary btn-small analyze-btn" data-id="${s.id}">Analyze</button>
            </div>
        `).join('');

        document.querySelectorAll('.analyze-btn').forEach(btn => {
            btn.addEventListener('click', () => loadHierarchy(btn.getAttribute('data-id')));
        });

    } catch (error) {
        resultsContainer.innerHTML = '<p class="error">Error searching symbols.</p>';
    }
}

async function loadHierarchy(symbolId) {
    const view = document.getElementById('hierarchy-main-view');
    const incomingList = document.getElementById('incoming-calls');
    const outgoingList = document.getElementById('outgoing-calls');

    view.style.display = 'block';
    incomingList.innerHTML = '<p class="loading">Loading...</p>';
    outgoingList.innerHTML = '<p class="loading">Loading...</p>';

    try {
        const data = await API.ui.getCallHierarchy(symbolId);
        
        document.getElementById('selected-symbol-name').innerText = data.symbol.name;
        document.getElementById('selected-symbol-path').innerText = data.symbol.filePath;

        // Render Incoming
        if (data.incoming && data.incoming.length > 0) {
            incomingList.innerHTML = data.incoming.map(item => `
                <div class="call-item" onclick="window.viewFileContent('${item.caller.filePath}')">
                    <span class="caller-name">${item.caller.name}</span>
                    <span class="caller-file">${item.caller.filePath.split('/').pop()}</span>
                </div>
            `).join('');
        } else {
            incomingList.innerHTML = '<p class="empty-text">No incoming calls found.</p>';
        }

        // Render Outgoing
        if (data.outgoing && data.outgoing.length > 0) {
            outgoingList.innerHTML = data.outgoing.map(call => `
                <div class="call-item">
                    <span class="callee-name">${call.calleeName}</span>
                    <span class="call-action" onclick="searchSpecificSymbol('${call.calleeName}')">🔍 Find</span>
                </div>
            `).join('');
        } else {
            outgoingList.innerHTML = '<p class="empty-text">No outgoing calls found.</p>';
        }

    } catch (error) {
        showNotification('Error loading hierarchy', 'error');
    }
}

window.searchSpecificSymbol = function(name) {
    document.getElementById('hierarchy-symbol-search').value = name;
    searchSymbols();
};
