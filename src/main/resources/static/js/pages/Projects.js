export default class Projects {
    async render() {
        const container = document.createElement('div');
        container.innerHTML = `
            <div class="card glass">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
                    <h2 class="card-title" style="margin: 0;"><i data-lucide="folder-kanban"></i> Projects</h2>
                    <button class="btn btn-primary" id="btn-add-project"><i data-lucide="plus"></i> Add Project</button>
                </div>
                <div id="projects-grid"></div>
            </div>
        `;

        this.initGrid(container.querySelector('#projects-grid'));

        container.querySelector('#btn-add-project').addEventListener('click', () => {
            alert('Add project functionality would go here (POST /api/projects).');
        });

        return container;
    }

    initGrid(element) {
        new gridjs.Grid({
            columns: [
                { id: 'id', name: 'ID', width: '80px' },
                { id: 'name', name: 'Name' },
                { id: 'rootPath', name: 'Root Path' },
                { id: 'status', name: 'Status', formatter: (cell) => gridjs.html(`<span class="badge ${cell === 'COMPLETED' ? 'badge-success' : 'badge-warning'}">${cell}</span>`) },
                {
                    id: 'actions',
                    name: 'Actions',
                    formatter: (_, row) => gridjs.html(`
                        <div class="grid-actions">
                            <button class="btn btn-secondary" onclick="alert('View Project ${row.cells[0].data}')"><i data-lucide="eye"></i> View</button>
                        </div>
                    `)
                }
            ],
            server: {
                url: '/api/projects',
                then: data => data.map(project => [
                    project.id,
                    project.name,
                    project.rootPath,
                    project.status || 'UNKNOWN',
                    null
                ])
            },
            search: true,
            sort: true,
            pagination: { enabled: true, limit: 10 }
        }).render(element);
    }
}
