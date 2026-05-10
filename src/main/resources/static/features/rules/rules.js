import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { showNotification } from '../../js/ui.js';

export async function initRules() {
    setupEventListeners();
    await fetchRules();
}

function setupEventListeners() {
    const addBtn = document.getElementById('add-rule-btn');
    const modal = document.getElementById('rule-modal');
    const closeBtns = document.querySelectorAll('.close-modal, .close-modal-btn');
    const form = document.getElementById('rule-form');

    if (addBtn) {
        addBtn.addEventListener('click', () => {
            document.getElementById('rule-modal-title').innerText = 'Add New Rule';
            form.reset();
            document.getElementById('rule-id').value = '';
            modal.classList.add('active');
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

            const id = document.getElementById('rule-id').value;
            const ruleData = {
                projectId: state.selectedProjectId,
                name: document.getElementById('rule-name').value,
                value: document.getElementById('rule-value').value,
                category: document.getElementById('rule-category').value,
                description: document.getElementById('rule-description').value
            };

            try {
                if (id) {
                    await API.ai.rules.update(id, ruleData);
                    showNotification('Rule updated successfully');
                } else {
                    await API.ai.rules.create(ruleData);
                    showNotification('Rule created successfully');
                }
                modal.classList.remove('active');
                await fetchRules();
            } catch (error) {
                showNotification('Error saving rule: ' + error.message, 'error');
            }
        });
    }
}

export async function fetchRules() {
    const rulesList = document.getElementById('rules-list');
    if (!rulesList || !state.selectedProjectId) return;

    try {
        const rules = await API.ai.rules.list(state.selectedProjectId);
        renderRules(rules);
    } catch (error) {
        rulesList.innerHTML = `<div class=\"error-message\">Error loading rules: ${error.message}</div>`;
    }
}

function renderRules(rules) {
    const rulesList = document.getElementById('rules-list');
    if (rules.length === 0) {
        rulesList.innerHTML = '<div class=\"empty-placeholder\">No rules defined for this project yet.</div>';
        return;
    }

    rulesList.innerHTML = rules.map(rule => `
        <div class=\"rule-card\">
            <div class=\"rule-header\">
                <span class=\"rule-name\">${rule.name}</span>
                <span class=\"rule-category\">${rule.category}</span>
            </div>
            <div class=\"rule-value\">${rule.value}</div>
            <p class=\"rule-description\">${rule.description || 'No description provided.'}</p>
            <div class=\"rule-actions\">
                <button class=\"btn-icon edit-rule\" data-id=\"${rule.id}\" title=\"Edit\">✏️</button>
                <button class=\"btn-icon delete-rule\" data-id=\"${rule.id}\" title=\"Delete\">🗑️</button>
            </div>
        </div>
    `).join('');

    // Attach actions
    rulesList.querySelectorAll('.edit-rule').forEach(btn => {
        btn.addEventListener('click', () => {
            const id = btn.getAttribute('data-id');
            const rule = rules.find(r => r.id == id);
            if (rule) {
                document.getElementById('rule-modal-title').innerText = 'Edit Rule';
                document.getElementById('rule-id').value = rule.id;
                document.getElementById('rule-name').value = rule.name;
                document.getElementById('rule-value').value = rule.value;
                document.getElementById('rule-category').value = rule.category;
                document.getElementById('rule-description').value = rule.description || '';
                document.getElementById('rule-modal').classList.add('active');
            }
        });
    });

    rulesList.querySelectorAll('.delete-rule').forEach(btn => {
        btn.addEventListener('click', async () => {
            if (confirm('Are you sure you want to delete this rule?')) {
                const id = btn.getAttribute('data-id');
                try {
                    await API.ai.rules.delete(id);
                    showNotification('Rule deleted');
                    await fetchRules();
                } catch (error) {
                    showNotification('Error deleting rule', 'error');
                }
            }
        });
    });
}
