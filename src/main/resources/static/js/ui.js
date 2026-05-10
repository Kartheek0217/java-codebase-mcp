export function showNotification(msg, type) {
    console.log(`[${type.toUpperCase()}] ${msg}`);
    if (type === 'error') alert(msg);
    // In a real app, this would be a toast notification
}

export function escapeHtml(unsafe) {
    if (unsafe === null || unsafe === undefined) return '';
    return String(unsafe)
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;")
         .replace(/"/g, "&quot;")
         .replace(/'/g, "&#039;");
}

export async function loadComponent(featureName) {
    const htmlUrl = `features/${featureName}/${featureName}.html`;
    try {
        const response = await fetch(htmlUrl);
        if (!response.ok) throw new Error(`Failed to load ${htmlUrl}`);
        return await response.text();
    } catch (error) {
        console.error(`Error loading component ${featureName}:`, error);
        return `<div class="error">Error loading component ${featureName}</div>`;
    }
}
