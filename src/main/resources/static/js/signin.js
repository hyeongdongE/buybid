import { csrfFetch, refreshSession } from "./auth.js";

const requestedPage = new URLSearchParams(location.search).get("next") ?? "";
const validNext = requestedPage === "/chatlist" || requestedPage === "/productupload"
    || /^\/chat\/\d+$/.test(requestedPage);
const next = validNext ? requestedPage : "/main";

if (validNext) {
    refreshSession().then(ok => { if (ok) location.replace(next); }).catch(() => {});
}

document.getElementById("loginForm").addEventListener("submit", async event => {
    event.preventDefault();
    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;
    try {
        const response = await csrfFetch("/api/signin", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, password })
        });
        if (response.ok) location.replace(next);
        else alert("이메일 또는 비밀번호를 확인하세요.");
    } catch {
        alert("로그인 요청에 실패했습니다. 잠시 후 다시 시도하세요.");
    }
});
