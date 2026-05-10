import { API } from '../../js/api.js';
import { state } from '../../js/state.js';
import { showNotification } from '../../js/ui.js';

export async function initWeb() {
    // Web Tools Tab Switching
    document.querySelectorAll('.web-nav-item').forEach(item => {
        item.addEventListener('click', () => {
            const tab = item.getAttribute('data-web-tab');
            switchWebTab(tab);
        });
    });

    // Web Search
    document.getElementById('btn-web-search').addEventListener('click', performWebSearch);
    document.getElementById('web-search-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') performWebSearch();
    });

    // Crawler
    document.getElementById('btn-new-crawl').addEventListener('click', () => document.getElementById('modal-crawl').classList.add('active'));
    document.getElementById('btn-cancel-crawl').addEventListener('click', () => document.getElementById('modal-crawl').classList.remove('active'));
    document.getElementById('btn-start-crawl-exec').addEventListener('click', startCrawl);

    // Extraction
    document.getElementById('btn-extract-meta').addEventListener('click', fetchMetadata);
    document.getElementById('btn-extract-data').addEventListener('click', performExtraction);

    await fetchCrawlJobs();
}

function switchWebTab(tabId) {
    state.webTab = tabId;
    document.querySelectorAll('.web-nav-item').forEach(i => i.classList.remove('active'));
    document.querySelector(`[data-web-tab=\"${tabId}\"]`).classList.add('active');

    document.querySelectorAll('.web-pane').forEach(p => p.classList.remove('active'));
    document.getElementById(`web-tab-${tabId}`).classList.add('active');

    if (tabId === 'web-crawler') fetchCrawlJobs();
}

async function performWebSearch() {
    const query = document.getElementById('web-search-input').value;
    if (!query || !state.selectedProjectId) return;

    const site = document.getElementById('web-search-site').value;
    const limit = document.getElementById('web-search-limit').value;
    const container = document.getElementById('web-search-results');
    container.innerHTML = '<div class=\"empty-msg\">Searching web index...</div>';

    try {
        const data = await API.web.search(state.selectedProjectId, query, site, limit);
        if (!data || data.length === 0) {
            container.innerHTML = '<div class=\"empty-msg\">No web results found. Try crawling some sites first!</div>';
            return;
        }

        container.innerHTML = data.map(item => `
            <div class=\"search-result-item\">
                <div class=\"search-result-header\">
                    <span class=\"result-path\">${item.url}</span>
                    <span class=\"badge\">Score: ${item.score.toFixed(2)}</span>
                </div>
                <h4>${item.title || 'Untitled Page'}</h4>
                <p>${item.snippet || 'No snippet available.'}</p>
            </div>
        `).join('');
    } catch (e) { container.innerHTML = '<div class=\"empty-msg\">Web search failed.</div>'; }
}

async function fetchCrawlJobs() {
    const container = document.getElementById('crawler-jobs-list');
    try {
        const jobs = await API.web.listCrawlJobs();
        if (!jobs || jobs.length === 0) {
            container.innerHTML = '<div class=\"empty-msg\">No crawl jobs found.</div>';
            return;
        }
        container.innerHTML = jobs.map(job => `
            <div class=\"crawl-job-card\">
                <div class=\"job-info\">
                    <h4>${job.startUrl}</h4>
                    <div class=\"job-meta\">
                        ID: ${job.id} | Pages: ${job.pagesCrawled} | Created: ${new Date(job.createdAt).toLocaleString()}
                    </div>
                </div>
                <div class=\"job-actions\">
                    <span class=\"status-tag ${job.status.toLowerCase()}\">${job.status}</span>
                    ${job.status === 'RUNNING' ? `<button class=\"btn-secondary btn-small\" onclick=\"window.stopCrawl('${job.id}')\">Stop</button>` : ''}
                    <button class=\"btn-danger btn-small\" onclick=\"window.deleteCrawlJob('${job.id}')\">Delete</button>
                </div>
            </div>
        `).join('');
    } catch (e) { container.innerHTML = '<div class=\"empty-msg\">Failed to load jobs.</div>'; }
}

async function startCrawl() {
    const url = document.getElementById('crawl-url').value;
    if (!url || !state.selectedProjectId) return alert('Start URL is required');

    const request = {
        projectId: state.selectedProjectId,
        startUrl: url,
        maxDepth: parseInt(document.getElementById('crawl-depth').value) || 2,
        maxPages: parseInt(document.getElementById('crawl-pages').value) || 100,
        delayMs: 0,
        respectRobotsTxt: true,
        includePatterns: document.getElementById('crawl-include').value.split('\n').filter(p => p.trim())
    };

    try {
        await API.web.startCrawl(request);
        document.getElementById('modal-crawl').classList.remove('active');
        showNotification('Crawl job started', 'success');
        fetchCrawlJobs();
    } catch (e) { showNotification('Failed to start crawl', 'error'); }
}

window.stopCrawl = async function (id) {
    try {
        await API.web.stopCrawl(id);
        fetchCrawlJobs();
    } catch (e) { showNotification('Failed to stop crawl', 'error'); }
};

window.deleteCrawlJob = async function (id) {
    if (!confirm('Delete this crawl job and all its results?')) return;
    try {
        await API.web.deleteCrawl(id);
        fetchCrawlJobs();
    } catch (e) { showNotification('Failed to delete job', 'error'); }
};

async function fetchMetadata() {
    const url = document.getElementById('extract-meta-url').value;
    if (!url) return;
    const output = document.getElementById('extract-meta-output');
    output.innerText = 'Fetching...';
    try {
        const data = await API.web.extractMetadata(url);
        output.innerText = JSON.stringify(data, null, 2);
    } catch (e) { output.innerText = 'Failed to fetch metadata.'; }
}

async function performExtraction() {
    const url = document.getElementById('extract-data-url').value;
    const selectorsStr = document.getElementById('extract-selectors').value;
    if (!url || !selectorsStr) return alert('URL and Selectors are required');

    const output = document.getElementById('extract-data-output');
    output.innerText = 'Extracting...';

    try {
        const selectors = JSON.parse(selectorsStr);
        const data = await API.web.extractData(url, selectors);
        output.innerText = JSON.stringify(data, null, 2);
    } catch (e) {
        output.innerText = 'Extraction failed. Ensure selectors are valid JSON.';
        console.error(e);
    }
}
