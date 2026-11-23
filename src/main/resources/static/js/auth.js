let refreshInProgress;

export function csrfFetch(url, options = {}) {
    const headers = new Headers(options.headers);
    const csrf = document.querySelector('meta[name="csrf-token"]')?.content;
    if (csrf && !/^(GET|HEAD|OPTIONS)$/i.test(options.method ?? "GET")) {
        headers.set("X-XSRF-TOKEN", csrf);
    }
    return fetch(url, { ...options, credentials: "same-origin", headers });
}

export function refreshSession() {
    if (!refreshInProgress) {
        refreshInProgress = csrfFetch("/api/refresh", { method: "POST" })
            .then(response => response.ok)
            .finally(() => { refreshInProgress = undefined; });
    }
    return refreshInProgress;
}

export async function authenticatedFetch(url, options) {
    const response = await csrfFetch(url, options);
    if (response.status === 401 && await refreshSession()) {
        return csrfFetch(url, options);
    }
    return response;
}

export async function errorMessage(response, fallback) {
    const body = await response.json().catch(() => ({}));
    return body.detail || fallback;
}
