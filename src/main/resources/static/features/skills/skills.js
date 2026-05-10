import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { showNotification } from '../../js/ui.js';

export async function initSkills() {
    document.getElementById('btn-learn-skill').addEventListener('click', learnSkill);
    document.getElementById('skill-url-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') learnSkill();
    });
    document.getElementById('btn-clear-skills').addEventListener('click', clearSkills);
    await fetchSkills();
}

export async function fetchSkills() {
    if (!state.selectedProjectId) return;
    const list = document.getElementById('skills-list');
    list.innerHTML = '<div class=\"empty-msg\">Loading skills...</div>';

    try {
        const skills = await API.ai.getSkills(state.selectedProjectId);
        renderSkills(skills);
    } catch (e) {
        list.innerHTML = '<div class=\"empty-msg\">Error loading skills</div>';
    }
}

function renderSkills(skills) {
    const list = document.getElementById('skills-list');
    if (!skills || skills.length === 0) {
        list.innerHTML = '<div class=\"empty-msg\">No skills learned yet for this project.</div>';
        return;
    }

    list.innerHTML = skills.map(skill => `
        <div class=\"skill-card\">
            <h4>${skill.name}</h4>
            <p>${skill.description || 'No description available.'}</p>
            <div class=\"skill-source\">Source: ${skill.source}</div>
            <button class=\"btn-secondary btn-view-skill\" onclick=\"alert('Skill Content:\\n\\n' + \`${skill.name}\`)\">View Full Instructions</button>
        </div>
    `).join('');
}

async function learnSkill() {
    const url = document.getElementById('skill-url-input').value;
    if (!url || !state.selectedProjectId) return alert('Please enter a URL');

    showNotification('Learning skill...', 'info');
    try {
        const data = await API.ai.learnSkill(state.selectedProjectId, url);
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
        await API.ai.clearSkills(state.selectedProjectId);
        showNotification('Skills cleared', 'success');
        fetchSkills();
    } catch (e) {
        showNotification('Error clearing skills', 'error');
    }
}
