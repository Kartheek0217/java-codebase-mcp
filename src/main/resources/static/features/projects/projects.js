import { API } from '../../js/api.js';
import { state, setSelectedProjectId } from '../../js/state.js';
import { showNotification } from '../../js/ui.js';

export function initProjects(onProjectSwitch) {
    const modal = document.getElementById('modal-project');
    const addBtn = document.getElementById('btn-add-project-tab');
    if (addBtn) addBtn.addEventListener('click', () => modal.classList.add('active'));

    document.getElementById('btn-cancel-project').addEventListener('click', () => modal.classList.remove('active'));
    document.getElementById('btn-save-project').addEventListener('click', saveProject);

    window.switchTabToDashboard = (id) => {
        setSelectedProjectId(id);
        onProjectSwitch(id);
    };

    window.deleteProject = async (id) => {
        if (!confirm('Are you sure you want to delete this project? This will remove all indexed data.')) return;
        try {
            await API.projects.delete(id);
            showNotification('Project deleted', 'success');
            if (state.selectedProjectId == id) {
                setSelectedProjectId(null);
            }
            renderProjectsManagement();
        } catch (e) { showNotification('Failed to delete project', 'error'); }
    };

    renderProjectsManagement();
}

export function renderProjectsManagement() {
    const container = document.getElementById('projects-manage-list');
    if (!container) return;

    if (state.projects.length === 0) {
        container.innerHTML = '<div class=\"empty-msg\">No projects found. Add one to get started!</div>';
        return;
    }

    container.innerHTML = state.projects.map(p => `
        <div class=\"project-manage-card\">
            <div class=\"project-info\">
                <h4>${p.name}</h4>
                <p>${p.rootPath}</p>
                <div class=\"project-stats-mini\">
                    <span>${p.fileCount} Files</span> • 
                    <span>${p.symbolCount} Symbols</span>
                </div>
            </div>
            <div class=\"project-manage-actions\">
                <button class=\"btn-secondary btn-small\" onclick=\"window.switchTabToDashboard('${p.id}')\">Switch To</button>
                <button class=\"btn-danger btn-small\" onclick=\"window.deleteProject('${p.id}')\">Delete</button>
            </div>
        </div>
    `).join('');
}

async function saveProject() {
    const name = document.getElementById('new-project-name').value;
    const path = document.getElementById('new-project-path').value;
    if (!name || !path) return alert('Please fill all fields');

    try {
        await API.projects.create(name, path);
        document.getElementById('modal-project').classList.remove('active');
        // This should trigger a refresh in the main app
        window.dispatchEvent(new CustomEvent('project-added'));
    } catch (e) { alert('Failed to create project. Check if path exists.'); }
}
