import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { showNotification } from '../../js/ui.js';

export async function initTasks() {
    setupEventListeners();
    await fetchTasks();
}

function setupEventListeners() {
    const addBtn = document.getElementById('add-task-btn');
    const modal = document.getElementById('task-modal');
    const closeBtns = document.querySelectorAll('.close-modal, .close-modal-btn');
    const form = document.getElementById('task-form');
    const addStepBtn = document.getElementById('add-step-input-btn');
    const stepInputs = document.getElementById('step-inputs');

    if (addBtn) {
        addBtn.addEventListener('click', () => {
            form.reset();
            stepInputs.innerHTML = `
                <div class=\"step-input-row\">
                    <input type=\"text\" class=\"form-control step-input\" placeholder=\"Step 1 description\" required>
                </div>
            `;
            modal.classList.add('active');
        });
    }

    if (addStepBtn) {
        addStepBtn.addEventListener('click', () => {
            const row = document.createElement('div');
            row.className = 'step-input-row';
            row.innerHTML = `
                <input type=\"text\" class=\"form-control step-input\" placeholder=\"Next step description\" required>
                <button type=\"button\" class=\"btn-icon remove-step-input\">✕</button>
            `;
            stepInputs.appendChild(row);

            row.querySelector('.remove-step-input').addEventListener('click', () => {
                row.remove();
            });
        });
    }

    closeBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            modal.classList.remove('active');
        });
    });

    if (form) {
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            if (!state.selectedProjectId) return;

            const steps = Array.from(document.querySelectorAll('.step-input'))
                .map(input => input.value.trim())
                .filter(v => v !== '');

            const request = {
                projectId: state.selectedProjectId,
                title: document.getElementById('task-title').value,
                description: document.getElementById('task-description').value,
                priority: document.getElementById('task-priority').value,
                steps: steps
            };

            try {
                await API.ai.tasks.create(request);
                showNotification('Task created successfully');
                modal.classList.remove('active');
                await fetchTasks();
            } catch (error) {
                showNotification('Error creating task: ' + error.message, 'error');
            }
        });
    }
}

export async function fetchTasks() {
    const tasksList = document.getElementById('tasks-list');
    if (!tasksList || !state.selectedProjectId) return;

    try {
        const tasks = await API.ai.tasks.list(state.selectedProjectId);
        renderTasks(tasks);
    } catch (error) {
        tasksList.innerHTML = `<div class=\"error-message\">Error loading tasks: ${error.message}</div>`;
    }
}

function renderTasks(tasks) {
    const tasksList = document.getElementById('tasks-list');
    if (tasks.length === 0) {
        tasksList.innerHTML = '<div class=\"empty-placeholder\">No tasks created for this project.</div>';
        return;
    }

    tasksList.innerHTML = tasks.map(task => `
        <div class=\"task-card priority-${task.priority.toLowerCase()}\">
            <div class=\"task-header\">
                <span class=\"task-title\">${task.title}</span>
                <span class=\"task-status status-${task.status.toLowerCase().replace('_', '-')}\">${task.status}</span>
            </div>
            <p class=\"task-description\">${task.description || 'No description'}</p>
            
            <ul class=\"task-steps\">
                ${task.steps.map(step => `
                    <li class=\"step-item\">
                        <input type=\"checkbox\" class=\"step-checkbox\" 
                               data-task-id=\"${task.id}\" 
                               data-step-id=\"${step.id}\"
                               ${step.status === 'COMPLETED' ? 'checked' : ''}>
                        <span class=\"${step.status === 'COMPLETED' ? 'step-completed' : ''}\">${step.description}</span>
                    </li>
                `).join('')}
            </ul>

            <div class=\"task-actions\">
                <button class=\"btn-icon delete-task\" data-id=\"${task.id}\" title=\"Delete\">🗑️</button>
            </div>
        </div>
    `).join('');

    // Attach checkbox handlers
    tasksList.querySelectorAll('.step-checkbox').forEach(checkbox => {
        checkbox.addEventListener('change', async (e) => {
            const taskId = checkbox.getAttribute('data-task-id');
            const stepId = checkbox.getAttribute('data-step-id');
            const newStatus = checkbox.checked ? 'COMPLETED' : 'TODO';

            try {
                await API.ai.tasks.updateStep(taskId, stepId, newStatus);
                await fetchTasks(); // Refresh to update task status
            } catch (error) {
                showNotification('Error updating step', 'error');
                checkbox.checked = !checkbox.checked; // Revert
            }
        });
    });

    // Attach delete handler
    tasksList.querySelectorAll('.delete-task').forEach(btn => {
        btn.addEventListener('click', async () => {
            if (confirm('Delete this task?')) {
                const id = btn.getAttribute('data-id');
                try {
                    await API.ai.tasks.delete(id);
                    showNotification('Task deleted');
                    await fetchTasks();
                } catch (error) {
                    showNotification('Error deleting task', 'error');
                }
            }
        });
    });
}
