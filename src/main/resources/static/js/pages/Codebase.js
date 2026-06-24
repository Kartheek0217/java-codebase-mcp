export default class Codebase {
    async render() {
        const container = document.createElement('div');
        container.innerHTML = `
            <div class="card glass">
                <h2 class="card-title"><i data-lucide="code-2"></i> Codebase Explorer</h2>
                <div class="form-group" style="display:flex; gap:16px;">
                    <div style="flex:1;">
                        <label class="form-label">Project ID</label>
                        <input type="number" id="cb-project-id" class="form-control" value="1">
                    </div>
                    <div style="flex:1;">
                        <label class="form-label">Operation (X-Op)</label>
                        <select id="cb-operation" class="form-control">
                            <option value="topology">topology</option>
                            <option value="search">search</option>
                            <option value="symbols">symbols</option>
                            <option value="files">files</option>
                        </select>
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label">Query Parameter</label>
                    <input type="text" id="cb-query" class="form-control" placeholder="Search query or leave empty">
                </div>
                <button class="btn btn-primary" id="btn-fetch-cb"><i data-lucide="search"></i> Fetch Data</button>
            </div>
            <div class="card">
                <h2 class="card-title">Results</h2>
                <div id="cb-results" class="stream-output">No results yet.</div>
            </div>
        `;

        container.querySelector('#btn-fetch-cb').addEventListener('click', async () => {
            const projectId = container.querySelector('#cb-project-id').value;
            const op = container.querySelector('#cb-operation').value;
            const query = container.querySelector('#cb-query').value;
            const resEl = container.querySelector('#cb-results');

            resEl.textContent = 'Loading...';

            try {
                const url = new URL(`/api/codebase/${projectId}`, window.location.origin);
                if (query) url.searchParams.append('query', query);

                const data = await window.apiFetch(url.toString(), {
                    headers: { 'X-Op': op }
                });

                resEl.textContent = JSON.stringify(data, null, 2);
            } catch (err) {
                resEl.textContent = 'Error: ' + err.message;
            }
        });

        return container;
    }
}
