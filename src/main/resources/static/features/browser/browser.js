import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { escapeHtml, showNotification } from '../../js/ui.js';

export async function initBrowser() {
    document.getElementById('file-filter').addEventListener('input', (e) => {
        filterFiles(e.target.value);
    });
    document.getElementById('btn-summarize-file').addEventListener('click', summarizeActiveFile);
    await fetchFiles();
}

export async function fetchFiles() {
    if (!state.selectedProjectId) return;
    try {
        state.files = await API.index.searchFiles(state.selectedProjectId, '');
        renderFileList(state.files);
    } catch (e) { console.error(e); }
}

function renderFileList(files) {
    const list = document.getElementById('file-list');
    list.innerHTML = files.map(f => `
        <li class=\"file-item\" data-path=\"${f.filePath}\">${f.filePath.split(/[\\\\\\/]/).pop()}</li>
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

export async function viewFile(path) {
    const header = document.getElementById('file-viewer-header');
    const code = document.getElementById('code-viewer');
    const symbolsList = document.getElementById('file-symbols');

    header.innerText = `Loading ${path}...`;

    try {
        const contentData = await API.index.readFile(state.selectedProjectId, path);
        header.innerText = path;
        code.innerHTML = `<code>${escapeHtml(contentData.content)}</code>`;

        const contextData = await API.ai.getContext(state.selectedProjectId, path);
        symbolsList.innerHTML = contextData.symbols.map(s => `
            <li class=\"symbol-item\" title=\"${s.type}\">${s.name}</li>
        `).join('') || '<li>No symbols</li>';

        const summarizeBtn = document.getElementById('btn-summarize-file');
        if (summarizeBtn) {
            summarizeBtn.classList.remove('hidden');
            summarizeBtn.setAttribute('data-path', path);
        }

    } catch (e) {
        header.innerText = 'Error loading file';
        code.innerText = 'Failed to fetch content';
    }
}

async function summarizeActiveFile() {
    const path = document.getElementById('btn-summarize-file').getAttribute('data-path');
    if (!path || !state.selectedProjectId) return;

    showNotification('Summarizing...', 'info');
    try {
        const data = await API.ai.summarize(state.selectedProjectId, path);
        const summaryText = `[FILE SUMMARY]\n\nLines: ${data.lines}\nSymbols: ${data.symbols?.length || 0}\n\nContent Preview:\n${data.content}`;
        alert(summaryText);
    } catch (e) { showNotification('Failed to summarize', 'error'); }
}
