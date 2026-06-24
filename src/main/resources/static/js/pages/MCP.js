export default class MCP {
    async render() {
        const container = document.createElement('div');
        container.innerHTML = `
            <div class="card glass">
                <h2 class="card-title"><i data-lucide="terminal-square"></i> MCP Management</h2>
                <div style="display:flex; gap:16px; margin-bottom:24px;">
                    <button class="btn btn-secondary" id="btn-tasks">Load Tasks</button>
                    <button class="btn btn-secondary" id="btn-rules">Load Rules</button>
                    <button class="btn btn-secondary" id="btn-skills">Load Skills</button>
                </div>
                <div id="mcp-grid"></div>
            </div>
        `;

        const gridContainer = container.querySelector('#mcp-grid');
        let currentGrid = null;

        const loadGrid = (url, columns) => {
            if (currentGrid) {
                currentGrid.destroy();
                gridContainer.innerHTML = '';
            }
            currentGrid = new gridjs.Grid({
                columns,
                server: {
                    url,
                    then: data => data.map(item => Object.values(item))
                },
                search: true,
                pagination: { enabled: true, limit: 10 }
            });
            currentGrid.render(gridContainer);
        };

        container.querySelector('#btn-tasks').addEventListener('click', () => {
            loadGrid('/api/mcp/tasks', ['ID', 'Title', 'Status', 'Project ID']);
        });

        container.querySelector('#btn-rules').addEventListener('click', () => {
            loadGrid('/api/mcp/rules', ['ID', 'Name', 'Description']);
        });

        container.querySelector('#btn-skills').addEventListener('click', () => {
            loadGrid('/api/mcp/skills', ['ID', 'Name', 'Type']);
        });

        // Initially load tasks
        setTimeout(() => container.querySelector('#btn-tasks').click(), 100);

        return container;
    }
}
