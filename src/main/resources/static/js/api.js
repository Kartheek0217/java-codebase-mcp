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
        listSummary: () => apiFetch('/api/ui/projects-summary'),
        delete: (id) => apiFetch(`/api/projects/${id}`, { method: 'DELETE' }),
        create: (name, path) => apiFetch(`/api/projects?name=${encodeURIComponent(name)}&rootPath=${encodeURIComponent(path)}`, { method: 'POST' }),
        getStatus: (id) => apiFetch(`/api/index/${id}/status`),
        getGitStatus: (id) => apiFetch(`/api/projects/${id}/git-status`)
    },
    index: {
        triggerScan: (id) => apiFetch(`/api/index/${id}/trigger-scan`, { method: 'POST' }),
        reconcile: (id) => apiFetch(`/api/index/${id}/reconcile`, { method: 'POST' }),
        readFile: (id, path) => apiFetch(`/api/index/${id}/files/read?filePath=${encodeURIComponent(path)}`),
        searchContent: (id, query) => apiFetch(`/api/index/${id}/search-content?query=${encodeURIComponent(query)}`),
        searchFiles: (id, query) => apiFetch(`/api/index/${id}/files/search?query=${encodeURIComponent(query)}`)
    },
    ai: {
        getHistory: (projectId) => apiFetch(`/api/ai/history?projectId=${projectId}`),
        getSymbols: (projectId, query) => apiFetch(`/api/ai/symbols?projectId=${projectId}&query=${encodeURIComponent(query)}`),
        getContext: (projectId, path) => apiFetch(`/api/ai/context?projectId=${projectId}&filePath=${encodeURIComponent(path)}`),
        getBatchContext: (projectId, paths) => apiFetch(`/api/ai/context/batch?projectId=${projectId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(paths)
        }),
        summarize: (projectId, path) => apiFetch(`/api/ai/summarize?projectId=${projectId}&filePath=${encodeURIComponent(path)}`),
        startSession: (projectId) => apiFetch(`/api/ai/session/start?projectId=${projectId}`, { method: 'POST' }),
        getTopology: (projectId) => apiFetch(`/api/ai/topology?projectId=${projectId}`),
        getSuggestions: (projectId, query) => apiFetch(`/api/ai/suggest?projectId=${projectId}&query=${encodeURIComponent(query)}`),
        getSkills: (projectId) => apiFetch(`/api/ai/skills?projectId=${projectId}`),
        learnSkill: (projectId, url) => apiFetch(`/api/ai/skills/learn?projectId=${projectId}&url=${encodeURIComponent(url)}`, { method: 'POST' }),
        clearSkills: (projectId) => apiFetch(`/api/ai/skills?projectId=${projectId}`, { method: 'DELETE' }),
        rules: {
            list: (projectId) => apiFetch(`/api/ai/rules?projectId=${projectId}`),
            create: (rule) => apiFetch('/api/ai/rules', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(rule)
            }),
            update: (id, rule) => apiFetch(`/api/ai/rules/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(rule)
            }),
            delete: (id) => apiFetch(`/api/ai/rules/${id}`, { method: 'DELETE' }),
            clearAll: (projectId) => apiFetch(`/api/ai/rules?projectId=${projectId}`, { method: 'DELETE' })
        },
        tasks: {
            list: (projectId) => apiFetch(`/api/ai/tasks?projectId=${projectId}`),
            create: (request) => apiFetch('/api/ai/tasks', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            }),
            update: (id, task) => apiFetch(`/api/ai/tasks/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(task)
            }),
            updateStep: (taskId, stepId, status) => apiFetch(`/api/ai/tasks/${taskId}/steps/${stepId}?status=${status}`, { method: 'PUT' }),
            delete: (id) => apiFetch(`/api/ai/tasks/${id}`, { method: 'DELETE' })
        }
    },
    web: {
        search: (projectId, q, site, limit) => apiFetch(`/api/web/search?projectId=${projectId}&q=${encodeURIComponent(q)}&site=${encodeURIComponent(site)}&limit=${limit}`),
        listCrawlJobs: () => apiFetch('/api/web/crawl'),
        startCrawl: (request) => apiFetch('/api/web/crawl', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request)
        }),
        stopCrawl: (id) => apiFetch(`/api/web/crawl/${id}/stop`, { method: 'POST' }),
        deleteCrawl: (id) => apiFetch(`/api/web/crawl/${id}`, { method: 'DELETE' }),
        extractMetadata: (url) => apiFetch(`/api/web/extract/metadata?url=${encodeURIComponent(url)}`),
        extractData: (url, selectors) => apiFetch('/api/web/extract', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url, selectors })
        })
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
        })
    },
    health: () => apiFetch('/health'),
    ui: {
        getProjectsSummary: () => apiFetch('/api/ui/projects-summary'),
        getProjectStats: (projectId) => apiFetch(`/api/ui/project-stats?projectId=${projectId}`),
        getSymbolById: (id) => apiFetch(`/api/ui/symbols/${id}`),
        getCallHierarchy: (id) => apiFetch(`/api/ui/symbols/${id}/hierarchy`)
    }
};
