import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { showNotification } from '../../js/ui.js';

export async function initGit() {
    document.getElementById('btn-refresh-git').addEventListener('click', fetchGitStatus);
    document.getElementById('btn-git-commit').addEventListener('click', commitChanges);
    document.getElementById('commit-message-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') commitChanges();
    });

    document.querySelectorAll('.close-inline-viewer[data-viewer=\"git\"]').forEach(btn => {
        btn.addEventListener('click', () => {
            document.getElementById('git-layout').classList.remove('has-viewer');
            document.getElementById('git-viewer-pane').style.display = 'none';
        });
    });

    await fetchGitStatus();
}

export async function fetchGitStatus() {
    if (!state.selectedProjectId) return;
    const branchEl = document.getElementById('git-current-branch');
    branchEl.innerText = 'loading...';

    try {
        const status = await API.projects.getGitStatus(state.selectedProjectId);
        renderGitStatus(status);
    } catch (e) {
        showNotification('Error fetching Git status', 'error');
    }
}

function renderGitStatus(status) {
    document.getElementById('git-current-branch').innerText = status.branch;

    const unstaged = [
        ...status.modified.map(f => ({ path: f, status: 'modified', staged: false })),
        ...status.untracked.map(f => ({ path: f, status: 'untracked', staged: false })),
        ...status.missing.map(f => ({ path: f, status: 'missing', staged: false }))
    ];

    const staged = [
        ...status.added.map(f => ({ path: f, status: 'added', staged: true })),
        ...status.staged.map(f => ({ path: f, status: 'staged', staged: true })),
        ...status.removed.map(f => ({ path: f, status: 'removed', staged: true }))
    ];

    renderGitFileList('git-changes-list', unstaged, 'git-changes-count');
    renderGitFileList('git-staged-list', staged, 'git-staged-count');

    const allChanges = [...unstaged, ...staged];
    if (allChanges.length > 0) {
        window.viewFileContent(allChanges[0].path);
    }
}

function renderGitFileList(containerId, files, countId) {
    const container = document.getElementById(containerId);
    document.getElementById(countId).innerText = files.length;

    if (files.length === 0) {
        container.innerHTML = '<div class=\"empty-msg\">No files</div>';
        return;
    }

    container.innerHTML = files.map(file => `
        <div class=\"git-file-item\">
            <div class=\"git-file-info\">
                <span class=\"git-file-name\">${file.path}</span>
                <span class=\"git-file-status\">${file.status}</span>
            </div>
            <div class=\"git-actions\">
                <button class=\"btn-secondary btn-small\" onclick=\"window.viewGitFile('${file.path}')\">View</button>
                ${file.staged
            ? `<button class=\"btn-secondary btn-small\" onclick=\"window.gitAction('discard', '${file.path}')\">Unstage</button>`
            : `<button class=\"btn-primary btn-small\" onclick=\"window.gitAction('stage', '${file.path}')\">Stage</button>`
        }
            </div>
        </div>
    `).join('');
}

window.gitAction = async function (action, path) {
    if (!state.selectedProjectId) return;
    try {
        if (action === 'stage') await API.git.stage(state.selectedProjectId, [path]);
        else await API.git.discard(state.selectedProjectId, [path]);
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
        await API.git.commit(state.selectedProjectId, message);
        showNotification('Committed successfully', 'success');
        document.getElementById('commit-message-input').value = '';
        fetchGitStatus();
    } catch (e) {
        showNotification('Commit failed', 'error');
    }
}

window.viewGitFile = function (path) {
    window.viewFileContent(path);
};
