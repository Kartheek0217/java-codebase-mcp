export default class Browser {
    async render() {
        const container = document.createElement('div');
        container.innerHTML = `
            <div class="card glass">
                <h2 class="card-title"><i data-lucide="chrome"></i> Browser Sessions</h2>
                <div style="display:flex; gap:16px; margin-bottom:24px;">
                    <button class="btn btn-primary" id="btn-create-session"><i data-lucide="plus"></i> New Browser Session</button>
                    <button class="btn btn-secondary" id="btn-list-sessions"><i data-lucide="refresh-cw"></i> Refresh List</button>
                </div>
                <div id="browser-sessions"></div>
            </div>
        `;

        const sessionsDiv = container.querySelector('#browser-sessions');

        const loadSessions = async () => {
            sessionsDiv.innerHTML = 'Loading...';
            try {
                const data = await window.apiFetch('/api/browser/session');
                if (!data || data.length === 0) {
                    sessionsDiv.innerHTML = '<p class="text-muted">No active browser sessions.</p>';
                    return;
                }

                let html = '<ul style="list-style:none; padding:0; display:flex; flex-direction:column; gap:12px;">';
                data.forEach(session => {
                    html += `
                        <li style="background:var(--bg-base); padding:16px; border-radius:var(--radius-md); border:1px solid var(--border-color); display:flex; justify-content:space-between; align-items:center;">
                            <div>
                                <div style="font-weight:600;">Session ID: ${session.sessionId || session}</div>
                            </div>
                            <button class="btn btn-secondary text-error" onclick="alert('Delete session ${session.sessionId || session}')"><i data-lucide="trash"></i> Close</button>
                        </li>
                    `;
                });
                html += '</ul>';
                sessionsDiv.innerHTML = html;
                lucide.createIcons({ root: sessionsDiv });
            } catch (err) {
                sessionsDiv.innerHTML = `<p class="text-error">Error: ${err.message}</p>`;
            }
        };

        container.querySelector('#btn-list-sessions').addEventListener('click', loadSessions);
        container.querySelector('#btn-create-session').addEventListener('click', async () => {
            try {
                await window.apiFetch('/api/browser/session', { method: 'POST' });
                loadSessions();
            } catch (err) {
                alert('Failed to create session: ' + err.message);
            }
        });

        // Initial load
        setTimeout(loadSessions, 100);

        return container;
    }
}
