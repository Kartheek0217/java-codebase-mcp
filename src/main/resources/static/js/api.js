export async function apiFetch(url, options = {}) {
    try {
        const response = await fetch(url, options);
        if (!response.ok) {
            const errorText = await response.text().catch(() => 'No error body');
            throw new Error(`HTTP error! status: ${response.status} - ${errorText}`);
        }

        const contentType = response.headers.get("content-type");
        if (contentType && contentType.includes("application/json")) {
            return await response.json();
        }

        const text = await response.text();
        if (!text) return null;

        try {
            return JSON.parse(text);
        } catch (e) {
            return text;
        }
    } catch (error) {
        console.error(`API Fetch Error (${url}):`, error);
        throw error;
    }
}

export const API = {
    projects: {
        listSummary: () => apiFetch('/api/projects/summary'),
        delete: (id) => apiFetch(`/api/projects/${id}`, { method: 'DELETE' }),
        create: (name, path) => apiFetch(`/api/projects?name=${encodeURIComponent(name)}&rootPath=${encodeURIComponent(path)}`, { method: 'POST' }),
        getStatus: (id) => apiFetch(`/api/projects/${id}/stats`),
        getGitStatus: (id) => apiFetch(`/api/projects/${id}/git-status`)
    },
    index: {
        triggerScan: (id) => apiFetch(`/api/codebase/${id}/scan`, { method: 'POST' }),
        reconcile: (id) => apiFetch(`/api/codebase/${id}/reconcile`, { method: 'POST' }),
        readFile: (id, path) => apiFetch(`/api/codebase/${id}/file?filePath=${encodeURIComponent(path)}`),
        searchContent: (id, query) => apiFetch(`/api/codebase/${id}/search?query=${encodeURIComponent(query)}`),
        searchFiles: (id, query) => apiFetch(`/api/codebase/${id}/files?query=${encodeURIComponent(query)}`)
    },
    ai: {
        getHistory: (projectId, sessionId) => apiFetch(`/api/codebase/${projectId}/history?sessionId=${sessionId}`),
        getSymbols: (projectId, query) => apiFetch(`/api/codebase/${projectId}/symbols?query=${encodeURIComponent(query)}`),
        getContext: (projectId, path, sessionId) => apiFetch(`/api/codebase/${projectId}/file?filePath=${encodeURIComponent(path)}${sessionId ? '&sessionId=' + sessionId : ''}`),
        getBatchContext: (projectId, paths) => apiFetch(`/api/codebase/${projectId}/context/batch`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(paths)
        }),
        summarize: (projectId, path) => apiFetch(`/api/codebase/${projectId}/summarize?filePath=${encodeURIComponent(path)}`),
        startSession: (projectId) => apiFetch(`/api/mcp/sessions?projectId=${projectId}`, { method: 'POST' }),
        getTopology: (projectId) => apiFetch(`/api/codebase/${projectId}/topology`),
        getSuggestions: (projectId, query) => apiFetch(`/api/codebase/${projectId}/suggest?query=${encodeURIComponent(query)}`),
        getSkills: (projectId) => apiFetch(`/api/mcp/skills?projectId=${projectId}`),
        learnSkill: (projectId, url) => apiFetch(`/api/mcp/skills/learn?projectId=${projectId}&url=${encodeURIComponent(url)}`, { method: 'POST' }),
        clearSkills: (projectId) => apiFetch(`/api/mcp/skills?projectId=${projectId}`, { method: 'DELETE' }),
        rules: {
            list: (projectId) => apiFetch(`/api/mcp/rules?projectId=${projectId}`),
            create: (rule) => apiFetch('/api/mcp/rules', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(rule)
            }),
            update: (id, rule) => apiFetch(`/api/mcp/rules/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(rule)
            }),
            delete: (id) => apiFetch(`/api/mcp/rules/${id}`, { method: 'DELETE' }),
            clearAll: (projectId) => apiFetch(`/api/mcp/rules?projectId=${projectId}`, { method: 'DELETE' })
        },
        tasks: {
            list: (projectId) => apiFetch(`/api/mcp/tasks?projectId=${projectId}`),
            create: (request) => apiFetch('/api/mcp/tasks', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            }),
            update: (id, task) => apiFetch(`/api/mcp/tasks/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(task)
            }),
            updateStep: (taskId, stepId, status) => apiFetch(`/api/mcp/tasks/${taskId}/steps/${stepId}?status=${status}`, { method: 'PUT' }),
            delete: (id) => apiFetch(`/api/mcp/tasks/${id}`, { method: 'DELETE' })
        }

    },
    git: {
        stage: (projectId, paths) => apiFetch(`/api/projects/${projectId}/git/stage`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(paths)
        }),
        discard: (projectId, paths) => apiFetch(`/api/projects/${projectId}/git/discard`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(paths)
        }),
        commit: (projectId, message) => apiFetch(`/api/projects/${projectId}/git/commit?message=${encodeURIComponent(message)}`, { method: 'POST' })
    },
    browser: {
        createSession: (projectId, options = {}) => apiFetch('/api/browser/session', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ projectId, ...options })
        }),
        listSessions: (projectId) => apiFetch(`/api/browser/session?projectId=${projectId}`),
        closeSession: (id) => apiFetch(`/api/browser/session/${id}`, { method: 'DELETE' }),
        navigate: (id, url) => apiFetch(`/api/browser/session/${id}/navigate`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url })
        }),
        screenshot: (id) => apiFetch(`/api/browser/session/${id}/screenshot`, { method: 'POST' }),
        click: (id, selector) => apiFetch(`/api/browser/session/${id}/click`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ selector })
        }),
        fill: (id, selector, value) => apiFetch(`/api/browser/session/${id}/fill`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ selector, value })
        }),
        evaluate: (id, script) => apiFetch(`/api/browser/session/${id}/evaluate`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ script })
        }),
        getContent: (id) => apiFetch(`/api/browser/session/${id}/content`),
        type: (id, selector, text) => apiFetch(`/api/browser/session/${id}/type`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ selector, text })
        }),
        selectOption: (id, selector, value) => apiFetch(`/api/browser/session/${id}/select`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ selector, value })
        }),
        waitForSelector: (id, selector) => apiFetch(`/api/browser/session/${id}/wait`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ selector })
        }),
        extractLocators: (id, url) => apiFetch(`/api/browser/session/${id}/extract-locators`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url })
        })
    },
    system: {
        health: () => apiFetch('/api/system/health'),
        info: () => apiFetch('/api/system/info')
    },
    health: () => apiFetch('/api/system/health'),
    ui: {
        getProjectsSummary: () => apiFetch('/api/projects/summary'),
        getProjectStats: (projectId) => apiFetch(`/api/projects/${projectId}/stats`),
        getSymbolById: (id) => apiFetch(`/api/codebase/symbols/${id}`),
        getCallHierarchy: (id) => apiFetch(`/api/codebase/symbols/${id}/hierarchy`)
    }
};

