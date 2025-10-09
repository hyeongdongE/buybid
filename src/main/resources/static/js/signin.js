import { setAccessToken, getAccessToken } from "./auth.js";

document.getElementById("loginForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;

    const res = await fetch("/api/signin", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ email, password })
    });

    if (!res.ok) {
        alert("로그인 실패");
        return;
    }

    const data = await res.json();
    setAccessToken(data.accessToken);
    window.location.href = "/main";

    alert("로그인 성공")
});
//
//async function fetchWithAuth(url, options = {}) {
//    let token = getAccessToken();
//
//    let res = await fetch(url, {
//        ...options,
//        headers: {
//            ...(options.headers || {}),
//            Authorization: `Bearer ${token}`
//        },
//        credentials: "include"
//    });
//
//    // Access Token 만료 → Refresh 시도
//    if (res.status === 401) {
//        console.log("Access Token 만료 → Refresh 시도");
//        const success = await refreshToken();
//
//        if (success) {
//            token = getAccessToken();
//            res = await fetch(url, {
//                ...options,
//                headers: {
//                    ...(options.headers || {}),
//                    Authorization: `Bearer ${token}`
//                },
//                credentials: "include"
//            });
//        } else {
//            alert("세션이 만료되었습니다. 다시 로그인해주세요.");
//            return null;
//        }
//    }
//
//    return res;
//}
//
//async function refreshToken() {
//    const res = await fetch("/api/refresh", {
//        method: "POST",
//        credentials: "include"
//    });
//
//    if (!res.ok) {
//        return false;
//    }
//
//    const data = await res.json();
//    setAccessToken(data.accessToken); // 새 Access Token 갱신
//    console.log("토큰 갱신 완료:", data.accessToken);
//    return true;
//}
//
//document.addEventListener("DOMContentLoaded", async () => {
//    await refreshToken();
//});