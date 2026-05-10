import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { showNotification, escapeHtml } from '../../js/ui.js';

export function initAgent() {
    document.getElementById('btn-start-session').addEventListener('click', startAISession);
    document.getElementById('btn-get-context').addEventListener('click', getAgentContext);
    document.getElementById('btn-get-topology').addEventListener('click', fetchTopology);
    document.getElementById('btn-get-suggestions').addEventListener('click', fetchSuggestions);
}

async function startAISession() {
    if (!state.selectedProjectId) return;
    try {
        const data = await API.ai.startSession(state.selectedProjectId);
        state.currentSessionId = data.sessionId;
        document.getElementById('session-id').innerText = data.sessionId;
        document.getElementById('session-info').classList.remove('hidden');
        showNotification('Session started', 'success');
    } catch (e) { showNotification('Failed to start session', 'error'); }
}

async function getAgentContext() {
    const pathsInput = document.getElementById('agent-file-path').value;
    if (!pathsInput || !state.selectedProjectId) return;

    const paths = pathsInput.split(',').map(p => p.trim());
    const output = document.getElementById('agent-context-output');
    output.innerHTML = 'Fetching context...';

    try {
        let data;
        if (paths.length > 1) {
            data = await API.ai.getBatchContext(state.selectedProjectId, paths);
        } else {
            data = await API.ai.getContext(state.selectedProjectId, paths[0]);
        }
        output.innerHTML = `<pre>${escapeHtml(JSON.stringify(data, null, 2))}</pre>`;
    } catch (e) {
        output.innerHTML = 'Error fetching context';
    }
}

async function fetchTopology() {
    if (!state.selectedProjectId) return;
    const output = document.getElementById('topology-output');
    output.innerHTML = 'Fetching topology...';
    try {
        const data = await API.ai.getTopology(state.selectedProjectId);
        output.innerHTML = `<pre>${escapeHtml(JSON.stringify(data, null, 2))}</pre>`;
    } catch (e) { output.innerHTML = 'Error loading topology'; }
}

async function fetchSuggestions() {
    const query = document.getElementById('suggest-query').value;
    if (!query || !state.selectedProjectId) return;
    const output = document.getElementById('suggestions-output');
    output.innerHTML = 'Analyzing...';
    try {
        const data = await API.ai.getSuggestions(state.selectedProjectId, query);
        if (!data || data.length === 0) {
            output.innerHTML = 'No suggestions found.';
            return;
        }
        output.innerHTML = data.map(item => `
            <div class=\"suggestion-item\">
                <span class=\"suggestion-path\">${item.filePath} (Score: ${item.score.toFixed(2)})</span>
                <div class=\"result-snippet\">${escapeHtml(item.matches?.[0]?.lineContent || 'No snippet available')}</div>
            </div>
        `).join('');
    } catch (e) { output.innerHTML = 'Error loading suggestions'; }
}
