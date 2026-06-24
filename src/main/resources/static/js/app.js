import Dashboard from './pages/Dashboard.js';
import Projects from './pages/Projects.js';
import Codebase from './pages/Codebase.js';
import MCP from './pages/MCP.js';
import LLM from './pages/LLM.js';
import Browser from './pages/Browser.js';

const routes = {
    '/': { title: 'System Status', component: Dashboard },
    '/projects': { title: 'Projects', component: Projects },
    '/codebase': { title: 'Codebase Explorer', component: Codebase },
    '/mcp': { title: 'MCP Management', component: MCP },
    '/llm': { title: 'LLM Review', component: LLM },
    '/browser': { title: 'Browser Session', component: Browser }
};

class App {
    constructor() {
        this.root = document.getElementById('root');
        this.pageTitle = document.getElementById('page-title');
        this.navItems = document.querySelectorAll('.nav-item');

        window.addEventListener('hashchange', () => this.handleRoute());
        this.handleRoute(); // Initial load

        // Initialize Lucide icons
        lucide.createIcons();
    }

    async handleRoute() {
        const hash = window.location.hash || '#/';
        const path = hash.replace('#', '');

        const route = routes[path] || routes['/'];

        this.updateNav(path);
        this.pageTitle.textContent = route.title;

        // Clear current content
        this.root.innerHTML = '';

        // Render new component
        const componentInstance = new route.component();
        const element = await componentInstance.render();
        this.root.appendChild(element);

        // Initialize Lucide icons for new content
        lucide.createIcons({ root: this.root });
    }

    updateNav(currentPath) {
        this.navItems.forEach(item => {
            if (item.dataset.path === currentPath) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });
    }
}

// Global API helper
window.apiFetch = async (url, options = {}) => {
    try {
        const response = await fetch(url, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            }
        });
        if (!response.ok) {
            throw new Error(`API Error: ${response.statusText}`);
        }
        return await response.json();
    } catch (error) {
        console.error('API call failed:', error);
        throw error;
    }
};

document.addEventListener('DOMContentLoaded', () => {
    new App();
});
