export const state = {
    currentTab: 'dashboard',
    projects: [],
    selectedProjectId: localStorage.getItem('selectedProjectId') || null,
    files: [],
    currentSessionId: null,
    webTab: 'web-search'
};

export function setSelectedProjectId(id) {
    state.selectedProjectId = id;
    localStorage.setItem('selectedProjectId', id);
}

export function getState() {
    return state;
}
