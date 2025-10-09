import { getAccessToken, setAccessToken } from "./auth.js";

function renderNavbar() {
    const token = getAccessToken();
    const navButtons = document.getElementById("nav-buttons");
    const mnavButtons = document.getElementById("mnav-buttons");

    if (token) {
        // 로그인 상태
        navButtons.innerHTML = `
      <a class="btn btn-buybid" href="#" id="logoutBtn">로그아웃</a>
    `;
        mnavButtons.innerHTML = `
      <a class="btn btn-buybid" href="#" id="logoutBtnMobile">로그아웃</a>
    `;

        document.getElementById("logoutBtn")?.addEventListener("click", logout);
        document.getElementById("logoutBtnMobile")?.addEventListener("click", logout);
    } else {
        // 비로그인 상태
        navButtons.innerHTML = `
      <a class="btn btn-outline-dark me-2" href="/signin">로그인</a>
      <a class="btn btn-buybid" href="/signup">회원가입</a>
    `;
        mnavButtons.innerHTML = `
      <a class="btn btn-outline-dark" href="/signin">로그인</a>
      <a class="btn btn-buybid" href="/signup">회원가입</a>
    `;
    }
}

async function logout() {
    await fetch("/api/logout", {
        method: "POST",
        credentials: "include"
    });
    setAccessToken(null);
    location.href = "/signin";
}

async function fetchWithAuth(url, options = {}) {
    let token = getAccessToken();

    let res = await fetch(url, {
        ...options,
        headers: {
            ...(options.headers || {}),
            Authorization: `Bearer ${token}`
        },
        credentials: "include"
    });

    // Access Token 만료 → Refresh 시도
    if (res.status === 401) {
        console.log("Access Token 만료 → Refresh 시도");
        const success = await refreshToken();

        if (success) {
            token = getAccessToken();
            res = await fetch(url, {
                ...options,
                headers: {
                    ...(options.headers || {}),
                    Authorization: `Bearer ${token}`
                },
                credentials: "include"
            });
        } else {
            alert("세션이 만료되었습니다. 다시 로그인해주세요.");
            return null;
        }
    }

    return res;
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

// 페이지 로드 시 실행
document.addEventListener("DOMContentLoaded", async () => {
    const path = window.location.pathname; // 현재 URL 경로 (/main, /about 등)
    document.querySelectorAll(".nav-link").forEach(link => {
        if (link.getAttribute("href") === path) {
            link.classList.add("active");
        } else {
            link.classList.remove("active");
        }
    });

    await refreshToken();
    renderNavbar();
});