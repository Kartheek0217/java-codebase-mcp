document.addEventListener('DOMContentLoaded', () => {
    const state = {
        currentTab: 'dashboard',
        projects: [],
        selectedProjectId: localStorage.getItem('selectedProjectId') || null,
        files: [],
        currentSessionId: null
    };

    // DOM Elements
    const navItems = document.querySelectorAll('.nav-item');
    const tabPanes = document.querySelectorAll('.tab-pane');
    const projectSelect = document.getElementById('project-select');
    const currentTabTitle = document.getElementById('current-tab-title');

    // Initialize
    init();

    function init() {
        fetchProjects();
        setupEventListeners();
        checkServerStatus();
    }

    async function fetchProjects() {
        try {
            const response = await fetch('/api/ui/projects-summary');
            state.projects = await response.json();
            renderProjectSelect();
            if (state.projects.length > 0) {
                if (!state.selectedProjectId || !state.projects.find(p => p.id == state.selectedProjectId)) {
                    state.selectedProjectId = state.projects[0].id;
                }
                projectSelect.value = state.selectedProjectId;
                loadDashboardData();
            }
        } catch (error) {
            console.error('Error fetching projects:', error);
            projectSelect.innerHTML = '<option value="">Error loading projects</option>';
        }
    }

    function renderProjectSelect() {
        projectSelect.innerHTML = state.projects.map(p =>
            `<option value="${p.id}" ${p.id == state.selectedProjectId ? 'selected' : ''}>${p.name}</option>`
        ).join('');
    }

    function setupEventListeners() {
        // Tab switching
        navItems.forEach(item => {
            item.addEventListener('click', () => {
                const tab = item.getAttribute('data-tab');
                switchTab(tab);
            });
        });

        // Project selection
        projectSelect.addEventListener('change', (e) => {
            state.selectedProjectId = e.target.value;
            localStorage.setItem('selectedProjectId', state.selectedProjectId);
            loadDashboardData();
            if (state.currentTab === 'browser') fetchFiles();
        });

        // Dashboard actions
        document.getElementById('btn-trigger-scan').addEventListener('click', triggerScan);
        document.getElementById('btn-reconcile').addEventListener('click', triggerReconcile);
        document.getElementById('btn-refresh').addEventListener('click', () => {
            loadDashboardData();
            if (state.currentTab === 'browser') fetchFiles();
        });

        // Search
        document.getElementById('btn-search').addEventListener('click', performSearch);
        document.getElementById('search-input').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') performSearch();
        });

        // Modals
        const modal = document.getElementById('modal-project');
        document.getElementById('btn-add-project').addEventListener('click', () => modal.classList.add('active'));
        document.getElementById('btn-cancel-project').addEventListener('click', () => modal.classList.remove('active'));
        document.getElementById('btn-save-project').addEventListener('click', saveProject);

        // File browser filter
        document.getElementById('file-filter').addEventListener('input', (e) => {
            filterFiles(e.target.value);
        });

        // Agent View
        document.getElementById('btn-start-session').addEventListener('click', startAISession);
        document.getElementById('btn-get-context').addEventListener('click', getAgentContext);
    }

    function switchTab(tabId) {
        state.currentTab = tabId;
        navItems.forEach(i => i.classList.remove('active'));
        document.querySelector(`[data-tab="${tabId}"]`).classList.add('active');

        tabPanes.forEach(p => p.classList.remove('active'));
        document.getElementById(`tab-${tabId}`).classList.add('active');

        currentTabTitle.innerText = tabId.charAt(0).toUpperCase() + tabId.slice(1);

        if (tabId === 'browser') fetchFiles();
        if (tabId === 'settings') loadSettingsData();
    }

    async function loadDashboardData() {
        if (!state.selectedProjectId) return;

        try {
            // Stats
            const statsRes = await fetch(`/api/index/${state.selectedProjectId}/status`);
            const stats = await statsRes.json();
            document.getElementById('stat-files').innerText = stats.totalFilesIndexed || 0;
            document.getElementById('stat-symbols').innerText = stats.totalSymbols || 0;

            // Git Info (from Project Controller status)
            const gitRes = await fetch(`/api/projects/${state.selectedProjectId}/git-status`);
            const git = await gitRes.json();
            document.getElementById('stat-branch').innerText = git.branch || 'N/A';

            // History
            const historyRes = await fetch(`/api/ai/history?projectId=${state.selectedProjectId}`);
            const history = await historyRes.json();
            renderHistory(history);

        } catch (error) {
            console.error('Error loading dashboard:', error);
        }
    }

    function renderHistory(history) {
        const list = document.getElementById('history-list');
        if (!history || history.length === 0) {
            list.innerHTML = '<li class="empty-msg">No recent activity</li>';
            return;
        }

        list.innerHTML = history.map(item => `
            <li class="activity-item">
                <span class="activity-type">${item.type}</span>
                <span class="activity-val">${item.value}</span>
                <span class="activity-time">${new Date(item.timestamp).toLocaleTimeString()}</span>
            </li>
        `).join('');
    }

    async function triggerScan() {
        if (!state.selectedProjectId) return;
        showNotification('Scanning...', 'info');
        try {
            await fetch(`/api/index/${state.selectedProjectId}/trigger-scan`, { method: 'POST' });
            showNotification('Scan completed', 'success');
            loadDashboardData();
        } catch (e) { showNotification('Scan failed', 'error'); }
    }

    async function triggerReconcile() {
        if (!state.selectedProjectId) return;
        showNotification('Reconciling...', 'info');
        try {
            await fetch(`/api/index/${state.selectedProjectId}/reconcile`, { method: 'POST' });
            showNotification('Reconciliation completed', 'success');
            loadDashboardData();
        } catch (e) { showNotification('Reconciliation failed', 'error'); }
    }

    async function saveProject() {
        const name = document.getElementById('new-project-name').value;
        const path = document.getElementById('new-project-path').value;
        if (!name || !path) return alert('Please fill all fields');

        try {
            const response = await fetch(`/api/projects?name=${encodeURIComponent(name)}&rootPath=${encodeURIComponent(path)}`, { method: 'POST' });
            if (response.ok) {
                document.getElementById('modal-project').classList.remove('active');
                fetchProjects();
            } else {
                alert('Failed to create project. Check if path exists.');
            }
        } catch (e) { console.error(e); }
    }

    async function performSearch() {
        const query = document.getElementById('search-input').value;
        if (!query) return;

        const type = document.querySelector('input[name="search-type"]:checked').value;
        const resultsContainer = document.getElementById('search-results');
        resultsContainer.innerHTML = '<div class="empty-msg">Searching...</div>';

        try {
            let url = '';
            if (type === 'content') url = `/api/index/${state.selectedProjectId}/search-content?query=${encodeURIComponent(query)}`;
            else if (type === 'files') url = `/api/index/${state.selectedProjectId}/files/search?query=${encodeURIComponent(query)}`;
            else if (type === 'symbols') url = `/api/ai/symbols?projectId=${state.selectedProjectId}&query=${encodeURIComponent(query)}`;

            const response = await fetch(url);
            const data = await response.json();
            renderSearchResults(data, type);
        } catch (e) {
            resultsContainer.innerHTML = '<div class="empty-msg">Search failed</div>';
        }
    }

    function renderSearchResults(results, type) {
        const container = document.getElementById('search-results');
        if (!results || results.length === 0) {
            container.innerHTML = '<div class="empty-msg">No results found</div>';
            return;
        }

        container.innerHTML = results.map(res => {
            if (type === 'content') {
                return `
                    <div class="search-result-item">
                        <span class="result-path">${res.filePath} (Score: ${res.score.toFixed(2)})</span>
                        <div class="result-snippet">${res.snippet.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</div>
                    </div>
                `;
            } else if (type === 'files') {
                return `
                    <div class="search-result-item">
                        <span class="result-path">${res.filePath}</span>
                        <p>Size: ${res.sizeBytes} bytes | Last Modified: ${new Date(res.lastModified).toLocaleString()}</p>
                    </div>
                `;
            } else {
                return `
                    <div class="search-result-item">
                        <span class="result-path">${res.name} (${res.type})</span>
                        <p>File: ${res.filePath} | Line: ${res.startLine}</p>
                    </div>
                `;
            }
        }).join('');
    }

    async function fetchFiles() {
        if (!state.selectedProjectId) return;
        try {
            const response = await fetch(`/api/index/${state.selectedProjectId}/files/search?query=`);
            state.files = await response.json();
            renderFileList(state.files);
        } catch (e) { console.error(e); }
    }

    function renderFileList(files) {
        const list = document.getElementById('file-list');
        list.innerHTML = files.map(f => `
            <li class="file-item" data-path="${f.filePath}">${f.filePath.split(/[\\\/]/).pop()}</li>
        `).join('');

        list.querySelectorAll('.file-item').forEach(item => {
            item.addEventListener('click', () => {
                list.querySelectorAll('.file-item').forEach(i => i.classList.remove('active'));
                item.classList.add('active');
                viewFile(item.getAttribute('data-path'));
            });
        });
    }

    function filterFiles(query) {
        const filtered = state.files.filter(f => f.filePath.toLowerCase().includes(query.toLowerCase()));
        renderFileList(filtered);
    }

    async function viewFile(path) {
        const header = document.getElementById('file-viewer-header');
        const code = document.getElementById('code-viewer');
        const symbolsList = document.getElementById('file-symbols');

        header.innerText = `Loading ${path}...`;

        try {
            // Read content
            const contentRes = await fetch(`/api/index/${state.selectedProjectId}/files/read?filePath=${encodeURIComponent(path)}`);
            const contentData = await contentRes.json();
            header.innerText = path;
            code.innerHTML = `<code>${contentData.content.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</code>`;

            // Symbols
            const symbolsRes = await fetch(`/api/ai/context?projectId=${state.selectedProjectId}&filePath=${encodeURIComponent(path)}`);
            const contextData = await symbolsRes.json();
            symbolsList.innerHTML = contextData.symbols.map(s => `
                <li class="symbol-item" title="${s.type}">${s.name}</li>
            `).join('') || '<li>No symbols</li>';

        } catch (e) {
            header.innerText = 'Error loading file';
            code.innerText = 'Failed to fetch content';
        }
    }

    async function startAISession() {
        if (!state.selectedProjectId) return;
        try {
            const res = await fetch(`/api/ai/session/start?projectId=${state.selectedProjectId}`, { method: 'POST' });
            const data = await res.json();
            state.currentSessionId = data.sessionId;
            document.getElementById('session-id').innerText = data.sessionId;
            document.getElementById('session-info').classList.remove('hidden');
            showNotification('Session started', 'success');
        } catch (e) { showNotification('Failed to start session', 'error'); }
    }

    async function getAgentContext() {
        const path = document.getElementById('agent-file-path').value;
        if (!path || !state.selectedProjectId) return;

        const output = document.getElementById('agent-context-output');
        output.innerHTML = 'Fetching context...';

        try {
            const res = await fetch(`/api/ai/context?projectId=${state.selectedProjectId}&filePath=${encodeURIComponent(path)}`);
            const data = await res.json();
            output.innerHTML = `<pre>${JSON.stringify(data, null, 2).replace(/</g, '&lt;').replace(/>/g, '&gt;')}</pre>`;
        } catch (e) {
            output.innerHTML = 'Error fetching context';
        }
    }

    async function checkServerStatus() {
        try {
            const res = await fetch('/api/status');
            const data = await res.json();
            document.getElementById('server-status').innerText = `Server: Online (${data.version})`;
        } catch (e) {
            document.getElementById('server-status').innerText = 'Server: Offline';
            document.querySelector('.status-dot').className = 'status-dot red';
        }
    }

    async function loadSettingsData() {
        try {
            const healthRes = await fetch('/api/health');
            const health = await healthRes.json();
            document.getElementById('health-details').innerHTML = `<pre>${JSON.stringify(health, null, 2)}</pre>`;

            const gitRes = await fetch('/api/git-info');
            const git = await gitRes.json();
            document.getElementById('git-details').innerHTML = `<pre>${JSON.stringify(git, null, 2)}</pre>`;
        } catch (e) { }
    }

    function showNotification(msg, type) {
        // Simple alert for now, could be improved
        console.log(`[${type}] ${msg}`);
    }
});
