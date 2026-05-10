import { API } from './js/api.js';
import { state, setSelectedProjectId } from './js/state.js';
import { loadComponent, showNotification } from './js/ui.js';

// Feature initializers
import { initDashboard, loadDashboardData } from './features/dashboard/dashboard.js';
import { initSearch } from './features/search/search.js';
import { initBrowser, fetchFiles, viewFile } from './features/browser/browser.js';
import { initWeb } from './features/web/web.js';
import { initAgent } from './features/agent/agent.js';
import { initSkills, fetchSkills } from './features/skills/skills.js';
import { initGit, fetchGitStatus } from './features/git/git.js';
import { initProjects, renderProjectsManagement } from './features/projects/projects.js';

document.addEventListener('DOMContentLoaded', () => {
    init();
});

async function init() {
    setupGlobalEventListeners();
    await fetchProjects();
    checkServerStatus();
    
    // Load initial tab (Dashboard)
    await switchTab('dashboard');
}

async function fetchProjects() {
    const projectSelect = document.getElementById('project-select');
    try {
        state.projects = await API.projects.listSummary();
        renderProjectSelect();
        
        if (state.projects.length > 0) {
            if (!state.selectedProjectId || !state.projects.find(p => p.id == state.selectedProjectId)) {
                state.selectedProjectId = state.projects[0].id;
                setSelectedProjectId(state.selectedProjectId);
            }
            projectSelect.value = state.selectedProjectId;
        }
    } catch (error) {
        projectSelect.innerHTML = '<option value=\"\">Error loading projects</option>';
    }
}

function renderProjectSelect() {
    const projectSelect = document.getElementById('project-select');
    projectSelect.innerHTML = state.projects.map(p =>
        `<option value=\"${p.id}\" ${p.id == state.selectedProjectId ? 'selected' : ''}>${p.name}</option>`
    ).join('');
}

function setupGlobalEventListeners() {
    // Tab switching
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', () => {
            const tab = item.getAttribute('data-tab');
            switchTab(tab);
        });
    });

    // Project selection
    document.getElementById('project-select').addEventListener('change', (e) => {
        setSelectedProjectId(e.target.value);
        refreshCurrentTab();
    });

    // Global event for project additions
    window.addEventListener('project-added', fetchProjects);
}

async function switchTab(tabId) {
    state.currentTab = tabId;
    
    // Update UI
    document.querySelectorAll('.nav-item').forEach(i => i.classList.remove('active'));
    document.querySelector(`[data-tab=\"${tabId}\"]`).classList.add('active');

    const tabPane = document.getElementById(`tab-${tabId}`);
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
    tabPane.classList.add('active');

    document.getElementById('current-tab-title').innerText = tabId.charAt(0).toUpperCase() + tabId.slice(1);

    // Load content if empty
    if (tabPane.innerHTML === '') {
        tabPane.innerHTML = await loadComponent(tabId);
        await initializeFeature(tabId);
    } else {
        await refreshFeature(tabId);
    }
}

async function initializeFeature(tabId) {
    switch (tabId) {
        case 'dashboard': await initDashboard(); break;
        case 'search': initSearch(); break;
        case 'browser': await initBrowser(); break;
        case 'web': await initWeb(); break;
        case 'agent': initAgent(); break;
        case 'skills': await initSkills(); break;
        case 'git': await initGit(); break;
        case 'projects': initProjects(onProjectSwitch); break;
    }
}

async function refreshFeature(tabId) {
    switch (tabId) {
        case 'dashboard': await loadDashboardData(); break;
        case 'browser': await fetchFiles(); break;
        case 'skills': await fetchSkills(); break;
        case 'git': await fetchGitStatus(); break;
        case 'projects': renderProjectsManagement(); break;
    }
}

function onProjectSwitch(id) {
    document.getElementById('project-select').value = id;
    switchTab('dashboard');
}

async function refreshCurrentTab() {
    await refreshFeature(state.currentTab);
}

async function checkServerStatus() {
    try {
        const data = await API.health();
        document.getElementById('server-status').innerText = `Server: Online (${data.status})`;
    } catch (e) {
        document.getElementById('server-status').innerText = 'Server: Offline';
        document.querySelector('.status-dot').className = 'status-dot red';
    }
}

// Exposed globally for inline handlers in features
window.viewFileContent = async function (path) {
    if (!state.selectedProjectId) return;

    const tabId = state.currentTab;

    if (tabId === 'search' || tabId === 'git') {
        const layout = document.getElementById(`${tabId}-layout`);
        const pane = document.getElementById(`${tabId}-viewer-pane`);
        const filenameEl = document.getElementById(`${tabId}-viewer-filename`);
        const codeEl = document.querySelector(`#${tabId}-viewer-code code`);
        const symbolsEl = document.getElementById(`${tabId}-viewer-symbols`);

        if (!layout || !pane) return;

        layout.classList.add('has-viewer');
        pane.style.display = 'flex';
        filenameEl.innerText = `Loading ${path}...`;
        codeEl.innerText = 'Loading content...';
        symbolsEl.innerHTML = '<li>Loading symbols...</li>';

        try {
            const contentData = await API.index.readFile(state.selectedProjectId, path);
            filenameEl.innerText = path;
            codeEl.innerText = contentData.content;

            const contextData = await API.ai.getContext(state.selectedProjectId, path);
            symbolsEl.innerHTML = contextData.symbols.map(s => `
                <li class=\"symbol-item\" title=\"${s.type}\">${s.name}</li>
            `).join('') || '<li>No symbols found</li>';

        } catch (e) {
            filenameEl.innerText = 'Error loading file';
            codeEl.innerText = 'Failed to fetch content.';
        }
    } else {
        await switchTab('browser');
        await viewFile(path);
        
        // Highlight in list
        const fileItems = document.querySelectorAll('.file-item');
        fileItems.forEach(item => {
            if (item.getAttribute('data-path') === path) {
                item.classList.add('active');
                item.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            } else {
                item.classList.remove('active');
            }
        });
    }
};
