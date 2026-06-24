export default class Dashboard {
    async render() {
        const container = document.createElement('div');

        container.innerHTML = `
            <div class="card glass">
                <h2 class="card-title"><i data-lucide="server"></i> System Health</h2>
                <div id="system-info">Loading...</div>
            </div>
            <div class="card">
                <h2 class="card-title"><i data-lucide="activity"></i> Metrics</h2>
                <p class="text-muted">More dashboard metrics will appear here.</p>
            </div>
        `;

        this.fetchSystemStatus(container.querySelector('#system-info'));

        return container;
    }

    async fetchSystemStatus(element) {
        const badge = document.getElementById('system-status-badge');
        try {
            const data = await window.apiFetch('/api/system');
            const isUp = data.status === 'UP' || data.status === 'OK' || data.database === 'connected';

            badge.textContent = isUp ? 'Online' : 'Degraded';
            badge.className = 'badge ' + (isUp ? 'badge-success' : 'badge-warning');

            element.innerHTML = `
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
                    <div>
                        <div class="form-label">Status</div>
                        <div style="font-weight: 500;">${data.status || 'N/A'}</div>
                    </div>
                    <div>
                        <div class="form-label">Database</div>
                        <div style="font-weight: 500;">${data.database || 'N/A'}</div>
                    </div>
                </div>
            `;
        } catch (err) {
            badge.textContent = 'Offline';
            badge.className = 'badge badge-error';
            element.innerHTML = `<div style="color: var(--error-color)">Failed to fetch system status: ${err.message}</div>`;
        }
    }
}
