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
            renderProjectsManagement();
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

    function renderProjectsManagement() {
        const container = document.getElementById('projects-manage-list');
        if (!container) return;

        if (state.projects.length === 0) {
            container.innerHTML = '<div class="empty-msg">No projects found. Add one to get started!</div>';
            return;
        }

        container.innerHTML = state.projects.map(p => `
            <div class="project-manage-card">
                <div class="project-info">
                    <h4>${p.name}</h4>
                    <p>${p.rootPath}</p>
                    <div class="project-stats-mini">
                        <span>${p.fileCount} Files</span> • 
                        <span>${p.symbolCount} Symbols</span>
                    </div>
                </div>
                <div class="project-manage-actions">
                    <button class="btn-secondary btn-small" onclick="window.switchTabToDashboard('${p.id}')">Switch To</button>
                    <button class="btn-danger btn-small" onclick="window.deleteProject('${p.id}')">Delete</button>
                </div>
            </div>
        `).join('');
    }

    window.switchTabToDashboard = function (id) {
        state.selectedProjectId = id;
        localStorage.setItem('selectedProjectId', id);
        projectSelect.value = id;
        loadDashboardData();
        switchTab('dashboard');
    };

    window.deleteProject = async function (id) {
        if (!confirm('Are you sure you want to delete this project? This will remove all indexed data (but not your source files).')) return;

        try {
            const res = await fetch(`/api/projects/${id}`, { method: 'DELETE' });
            if (res.ok) {
                showNotification('Project deleted', 'success');
                if (state.selectedProjectId == id) {
                    state.selectedProjectId = null;
                    localStorage.removeItem('selectedProjectId');
                }
                fetchProjects();
            } else {
                showNotification('Failed to delete project', 'error');
            }
        } catch (e) {
            showNotification('Error deleting project', 'error');
        }
    };

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
        // Search
        document.getElementById('btn-search').addEventListener('click', performSearch);
        document.getElementById('search-input').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') performSearch();
        });

        // Modals
        const modal = document.getElementById('modal-project');
        const addBtn = document.getElementById('btn-add-project-tab');
        if (addBtn) addBtn.addEventListener('click', () => modal.classList.add('active'));

        document.getElementById('btn-cancel-project').addEventListener('click', () => modal.classList.remove('active'));
        document.getElementById('btn-save-project').addEventListener('click', saveProject);

        // File browser filter
        document.getElementById('file-filter').addEventListener('input', (e) => {
            filterFiles(e.target.value);
        });

        // Agent View
        document.getElementById('btn-start-session').addEventListener('click', startAISession);
        document.getElementById('btn-get-context').addEventListener('click', getAgentContext);

        // Skills
        document.getElementById('btn-learn-skill').addEventListener('click', learnSkill);
        document.getElementById('skill-url-input').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') learnSkill();
        });
        document.getElementById('btn-clear-skills').addEventListener('click', clearSkills);

        // Git
        document.getElementById('btn-refresh-git').addEventListener('click', fetchGitStatus);
        document.getElementById('btn-git-commit').addEventListener('click', commitChanges);
        document.getElementById('commit-message-input').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') commitChanges();
        });

        // Close inline viewers
        document.querySelectorAll('.close-inline-viewer').forEach(btn => {
            btn.addEventListener('click', () => {
                const viewer = btn.getAttribute('data-viewer');
                document.getElementById(`${viewer}-layout`).classList.remove('has-viewer');
                document.getElementById(`${viewer}-viewer-pane`).style.display = 'none';
            });
        });
    }

    window.viewFileContent = async function (path) {
        if (!state.selectedProjectId) return;

        const tabId = state.currentTab;

        if (tabId === 'search' || tabId === 'git') {
            // Inline viewing within the current tab
            const layout = document.getElementById(`${tabId}-layout`);
            const pane = document.getElementById(`${tabId}-viewer-pane`);
            const filenameEl = document.getElementById(`${tabId}-viewer-filename`);
            const codeEl = document.querySelector(`#${tabId}-viewer-code code`);
            const symbolsEl = document.getElementById(`${tabId}-viewer-symbols`);

            if (!layout || !pane) return; // Fallback to browser if elements missing

            layout.classList.add('has-viewer');
            pane.style.display = 'flex';
            filenameEl.innerText = `Loading ${path}...`;
            codeEl.innerText = 'Loading content...';
            symbolsEl.innerHTML = '<li>Loading symbols...</li>';

            try {
                // Fetch content
                const contentRes = await fetch(`/api/index/${state.selectedProjectId}/files/read?filePath=${encodeURIComponent(path)}`);
                const contentData = await contentRes.json();
                filenameEl.innerText = path;
                codeEl.innerText = contentData.content;

                // Fetch symbols
                const symbolsRes = await fetch(`/api/ai/context?projectId=${state.selectedProjectId}&filePath=${encodeURIComponent(path)}`);
                const contextData = await symbolsRes.json();
                symbolsEl.innerHTML = contextData.symbols.map(s => `
                    <li class="symbol-item" title="${s.type}">${s.name}</li>
                `).join('') || '<li>No symbols found</li>';

            } catch (e) {
                console.error('Error loading inline viewer:', e);
                filenameEl.innerText = 'Error loading file';
                codeEl.innerText = 'Failed to fetch content.';
            }
        } else {
            // Default behavior for other tabs: Switch to Browser and view there
            switchTab('browser');
            await viewFile(path);

            // Highlight the file in the list if it exists
            const fileItems = document.querySelectorAll('.file-item');
            fileItems.forEach(item => {
                if (item.innerText.trim() === path) {
                    item.classList.add('active');
                    item.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                } else {
                    item.classList.remove('active');
                }
            });
        }
    };

    function switchTab(tabId) {
        state.currentTab = tabId;
        navItems.forEach(i => i.classList.remove('active'));
        document.querySelector(`[data-tab="${tabId}"]`).classList.add('active');

        tabPanes.forEach(p => p.classList.remove('active'));
        document.getElementById(`tab-${tabId}`).classList.add('active');

        currentTabTitle.innerText = tabId.charAt(0).toUpperCase() + tabId.slice(1);

        if (tabId === 'browser') fetchFiles();
        if (tabId === 'projects') renderProjectsManagement();
        if (tabId === 'skills') fetchSkills();
        if (tabId === 'git') fetchGitStatus();
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

        if (results.length > 0) {
            const firstResult = results[0];
            const firstPath = firstResult.filePath || firstResult.name;
            if (firstPath) viewFileContent(firstPath);
        }

        container.innerHTML = results.map(res => {
            if (type === 'content') {
                const matchesHtml = (res.matches || []).map(m => `
                    <div class="match-item">
                        <div class="match-header">
                            <span>Line <span class="match-line-num">${m.lineNumber}</span> in <code>${m.functionName}</code></span>
                        </div>
                        <div class="result-snippet">${m.lineContent.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</div>
                    </div>
                `).join('');

                return `
                    <div class="search-result-item">
                        <div class="search-result-header">
                            <span class="result-path">${res.filePath} (Score: ${res.score.toFixed(2)})</span>
                            <button class="btn-secondary btn-small" onclick="viewFileContent('${res.filePath.replace(/\\/g, '\\\\')}')">View Full File</button>
                        </div>
                        ${matchesHtml}
                    </div>
                `;
            } else if (type === 'files') {
                return `
                    <div class="search-result-item">
                        <div class="search-result-header">
                            <span class="result-path">${res.filePath}</span>
                            <button class="btn-secondary btn-small" onclick="viewFileContent('${res.filePath.replace(/\\/g, '\\\\')}')">View Full File</button>
                        </div>
                        <p>Size: ${res.fileSize} bytes | Last Modified: ${new Date(res.lastScanned).toLocaleString()}</p>
                    </div>
                `;
            } else {
                return `
                    <div class="search-result-item">
                        <div class="search-result-header">
                            <span class="result-path">${res.name} (${res.type})</span>
                            <button class="btn-secondary btn-small" onclick="viewFileContent('${res.filePath.replace(/\\/g, '\\\\')}')">View Full File</button>
                        </div>
                        <p>File: ${res.filePath} | Line: ${res.lineNumber || res.startLine || 'N/A'}</p>
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
            const res = await fetch('/health');
            const data = await res.json();
            document.getElementById('server-status').innerText = `Server: Online (${data.status})`;
        } catch (e) {
            document.getElementById('server-status').innerText = 'Server: Offline';
            document.querySelector('.status-dot').className = 'status-dot red';
        }
    }


    async function fetchSkills() {
        if (!state.selectedProjectId) return;
        const list = document.getElementById('skills-list');
        list.innerHTML = '<div class="empty-msg">Loading skills...</div>';

        try {
            const res = await fetch(`/api/ai/skills?projectId=${state.selectedProjectId}`);
            const skills = await res.json();
            renderSkills(skills);
        } catch (e) {
            list.innerHTML = '<div class="empty-msg">Error loading skills</div>';
        }
    }

    function renderSkills(skills) {
        const list = document.getElementById('skills-list');
        if (!skills || skills.length === 0) {
            list.innerHTML = '<div class="empty-msg">No skills learned yet for this project.</div>';
            return;
        }

        list.innerHTML = skills.map(skill => `
            <div class="skill-card">
                <h4>${skill.name}</h4>
                <p>${skill.description || 'No description available.'}</p>
                <div class="skill-source">Source: ${skill.source}</div>
                <button class="btn-secondary btn-view-skill" onclick="alert('Skill Content:\\n\\n' + \`${skill.name}\`)">View Full Instructions</button>
            </div>
        `).join('');
    }

    async function learnSkill() {
        const url = document.getElementById('skill-url-input').value;
        if (!url || !state.selectedProjectId) return alert('Please enter a URL');

        showNotification('Learning skill...', 'info');
        try {
            const res = await fetch(`/api/ai/skills/learn?projectId=${state.selectedProjectId}&url=${encodeURIComponent(url)}`, { method: 'POST' });
            const data = await res.json();
            if (data.status === 'success') {
                showNotification('Skill learned successfully', 'success');
                document.getElementById('skill-url-input').value = '';
                fetchSkills();
            } else {
                showNotification('Failed to learn skill', 'error');
            }
        } catch (e) {
            showNotification('Error learning skill', 'error');
        }
    }

    async function clearSkills() {
        if (!state.selectedProjectId) return;
        if (!confirm('Are you sure you want to clear all learned skills for this project?')) return;

        showNotification('Clearing skills...', 'info');
        try {
            const res = await fetch(`/api/ai/skills?projectId=${state.selectedProjectId}`, { method: 'DELETE' });
            if (res.ok) {
                showNotification('Skills cleared', 'success');
                fetchSkills();
            } else {
                showNotification('Failed to clear skills', 'error');
            }
        } catch (e) {
            showNotification('Error clearing skills', 'error');
        }
    }

    function showNotification(msg, type) {
        // Simple console log for now, but in a real app this would be a toast
        console.log(`[${type.toUpperCase()}] ${msg}`);
        // Optional: simple alert for errors
        if (type === 'error') alert(msg);
    }

    async function fetchGitStatus() {
        if (!state.selectedProjectId) return;
        const branchEl = document.getElementById('git-current-branch');
        branchEl.innerText = 'loading...';

        try {
            const res = await fetch(`/api/projects/${state.selectedProjectId}/git-status`);
            const status = await res.json();
            renderGitStatus(status);
        } catch (e) {
            showNotification('Error fetching Git status', 'error');
        }
    }

    function renderGitStatus(status) {
        document.getElementById('git-current-branch').innerText = status.branch;

        // Combine all unstaged changes
        const unstaged = [
            ...status.modified.map(f => ({ path: f, status: 'modified', staged: false })),
            ...status.untracked.map(f => ({ path: f, status: 'untracked', staged: false })),
            ...status.missing.map(f => ({ path: f, status: 'missing', staged: false }))
        ];

        // Combine staged changes
        const staged = [
            ...status.added.map(f => ({ path: f, status: 'added', staged: true })),
            ...status.staged.map(f => ({ path: f, status: 'staged', staged: true })),
            ...status.removed.map(f => ({ path: f, status: 'removed', staged: true }))
        ];

        renderGitFileList('git-changes-list', unstaged, 'git-changes-count');
        renderGitFileList('git-staged-list', staged, 'git-staged-count');

        // Auto-view the first changed file
        const allChanges = [...unstaged, ...staged];
        if (allChanges.length > 0) {
            viewFileContent(allChanges[0].path);
        }
    }

    function renderGitFileList(containerId, files, countId) {
        const container = document.getElementById(containerId);
        document.getElementById(countId).innerText = files.length;

        if (files.length === 0) {
            container.innerHTML = '<div class="empty-msg">No files</div>';
            return;
        }

        container.innerHTML = files.map(file => `
            <div class="git-file-item">
                <div class="git-file-info">
                    <span class="git-file-name">${file.path}</span>
                    <span class="git-file-status">${file.status}</span>
                </div>
                <div class="git-actions">
                    <button class="btn-secondary btn-small" onclick="viewGitFile('${file.path}')">View</button>
                    ${file.staged
                ? `<button class="btn-secondary btn-small" onclick="gitAction('discard', '${file.path}')">Unstage</button>`
                : `<button class="btn-primary btn-small" onclick="gitAction('stage', '${file.path}')">Stage</button>`
            }
                </div>
            </div>
        `).join('');
    }

    window.gitAction = async function (action, path) {
        if (!state.selectedProjectId) return;
        const endpoint = action === 'stage' ? 'stage' : 'discard';

        try {
            await fetch(`/api/projects/${state.selectedProjectId}/git/${endpoint}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify([path])
            });
            fetchGitStatus();
        } catch (e) {
            showNotification(`Failed to ${action} file`, 'error');
        }
    };

    async function commitChanges() {
        const message = document.getElementById('commit-message-input').value;
        if (!message) return alert('Please enter a commit message');

        showNotification('Committing...', 'info');
        try {
            const res = await fetch(`/api/projects/${state.selectedProjectId}/git/commit?message=${encodeURIComponent(message)}`, { method: 'POST' });
            if (res.ok) {
                showNotification('Committed successfully', 'success');
                document.getElementById('commit-message-input').value = '';
                fetchGitStatus();
            } else {
                showNotification('Commit failed', 'error');
            }
        } catch (e) {
            showNotification('Error committing', 'error');
        }
    }

    window.viewGitFile = function (path) {
        viewFileContent(path);
    };
});
