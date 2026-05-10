import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { showNotification } from '../../js/ui.js';

export async function initDashboard() {
    document.getElementById('btn-trigger-scan').addEventListener('click', triggerScan);
    document.getElementById('btn-reconcile').addEventListener('click', triggerReconcile);
    await loadDashboardData();
}

export async function loadDashboardData() {
    if (!state.selectedProjectId) return;

    try {
        const stats = await API.projects.getStatus(state.selectedProjectId);
        document.getElementById('stat-files').innerText = stats.totalFilesIndexed || 0;
        document.getElementById('stat-symbols').innerText = stats.totalSymbols || 0;

        const git = await API.projects.getGitStatus(state.selectedProjectId);
        document.getElementById('stat-branch').innerText = git.branch || 'N/A';

        const history = await API.ai.getHistory(state.selectedProjectId);
        renderHistory(history);

    } catch (error) {
        console.error('Error loading dashboard:', error);
    }
}

function renderHistory(history) {
    const list = document.getElementById('history-list');
    if (!history || history.length === 0) {
        list.innerHTML = '<li class=\"empty-msg\">No recent activity</li>';
        return;
    }

    list.innerHTML = history.map(item => `
        <li class=\"activity-item\">
            <span class=\"activity-type\">${item.type}</span>
            <span class=\"activity-val\">${item.value}</span>
            <span class=\"activity-time\">${new Date(item.timestamp).toLocaleTimeString()}</span>
        </li>
    `).join('');
}

async function triggerScan() {
    if (!state.selectedProjectId) return;
    showNotification('Scanning...', 'info');
    try {
        await API.index.triggerScan(state.selectedProjectId);
        showNotification('Scan completed', 'success');
        loadDashboardData();
    } catch (e) { showNotification('Scan failed', 'error'); }
}

async function triggerReconcile() {
    if (!state.selectedProjectId) return;
    showNotification('Reconciling...', 'info');
    try {
        await API.index.reconcile(state.selectedProjectId);
        showNotification('Reconciliation completed', 'success');
        loadDashboardData();
    } catch (e) { showNotification('Reconciliation failed', 'error'); }
}
