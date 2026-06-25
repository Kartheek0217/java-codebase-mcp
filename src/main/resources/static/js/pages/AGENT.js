export default class LLM {
    async render() {
        const container = document.createElement('div');
        container.innerHTML = `
            <div class="card glass">
                <h2 class="card-title"><i data-lucide="bot"></i> LLM Review & Generation</h2>
                <div class="form-group" style="display:flex; gap:16px;">
                    <div style="flex:1;">
                        <label class="form-label">Project ID</label>
                        <input type="number" id="llm-project-id" class="form-control" value="1">
                    </div>
                    <div style="flex:1;">
                        <label class="form-label">Action (X-Action)</label>
                        <select id="llm-action" class="form-control">
                            <option value="code-review">code-review</option>
                            <option value="explain-file">explain-file</option>
                            <option value="ask">ask</option>
                            <option value="code-refactor">code-refactor</option>
                        </select>
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label">File Path / Question (JSON Body)</label>
                    <textarea id="llm-body" class="form-control" placeholder='{"filePath": "src/main/java/App.java"}'></textarea>
                </div>
                <button class="btn btn-primary" id="btn-stream"><i data-lucide="play"></i> Start Stream</button>
            </div>
            <div class="card">
                <h2 class="card-title">Live Response</h2>
                <div id="llm-output" class="stream-output markdown-body">Waiting to start...</div>
            </div>
        `;

        container.querySelector('#btn-stream').addEventListener('click', () => {
            const projectId = container.querySelector('#llm-project-id').value;
            const action = container.querySelector('#llm-action').value;
            const bodyText = container.querySelector('#llm-body').value;
            const output = container.querySelector('#llm-output');

            output.innerHTML = '';

            let reqBody = {};
            try {
                if (bodyText) reqBody = JSON.parse(bodyText);
            } catch (e) {
                alert("Invalid JSON body");
                return;
            }

            fetch(`/api/llm/${projectId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Action': action
                },
                body: JSON.stringify(reqBody)
            })
                .then(response => {
                    const reader = response.body.getReader();
                    const decoder = new TextDecoder();

                    let markdownContent = '';

                    function read() {
                        reader.read().then(({ done, value }) => {
                            if (done) {
                                console.log('Stream complete');
                                return;
                            }
                            const chunk = decoder.decode(value);
                            // Very naive SSE parsing for display purposes
                            const lines = chunk.split('\\n');
                            lines.forEach(line => {
                                if (line.startsWith('data:')) {
                                    const data = line.replace('data:', '').trim();
                                    if (data !== '[DONE]') {
                                        markdownContent += data + '\\n';
                                        output.innerHTML = marked.parse(markdownContent);
                                        output.scrollTop = output.scrollHeight;
                                    }
                                }
                            });
                            read();
                        });
                    }
                    read();
                })
                .catch(err => {
                    output.textContent = "Error: " + err.message;
                });
        });

        return container;
    }
}
