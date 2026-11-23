import { authenticatedFetch, csrfFetch } from "./auth.js";

async function updateNavigation() {
    const groups = [document.getElementById("nav-buttons"), document.getElementById("mnav-buttons")];
    if (!groups.some(Boolean)) return;

    const loggedIn = await authenticatedFetch("/api/session").then(response => response.ok).catch(() => false);
    for (const group of groups) {
        if (!group) continue;
        if (loggedIn) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "btn btn-buybid";
            button.textContent = "로그아웃";
            button.addEventListener("click", async () => {
                try {
                    const response = await csrfFetch("/api/logout", { method: "POST" });
                    if (!response.ok) throw new Error();
                    location.assign("/main");
                } catch {
                    alert("로그아웃 요청에 실패했습니다.");
                }
            });
            group.replaceChildren(button);
        } else {
            const signIn = document.createElement("a");
            signIn.href = "/signin";
            signIn.className = "btn btn-outline-dark me-2";
            signIn.textContent = "로그인";
            const signUp = document.createElement("a");
            signUp.href = "/signup";
            signUp.className = "btn btn-buybid";
            signUp.textContent = "회원가입";
            group.replaceChildren(signIn, signUp);
        }
    }
}

updateNavigation();
