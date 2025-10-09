let accessToken = null;

export function setAccessToken(token) {
    accessToken = token;
}

export function getAccessToken() {
    return accessToken;
}

export async function refreshToken() {
    const res = await fetch("/api/refresh", {
        method: "POST",
        credentials: "include"
    });

    if (!res.ok) {
        return false;
    }

    const data = await res.json();
    setAccessToken(data.accessToken);
    return true;
}