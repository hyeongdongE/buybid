// /js/product-upload.js
// 🌟 모듈로 실행되므로 위 script 태그에 type="module" 필수
import { authenticatedFetch, errorMessage } from '/js/auth.js';

(function () {
    const $ = (sel) => document.querySelector(sel);
    const pad = (n) => String(n).padStart(2, "0");
    const toLocal = (d) =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;

    const alertBox = $("#alertBox");
    const showAlert = (msg) => {
        alertBox.textContent = msg;
        alertBox.classList.remove("d-none");
        alertBox.scrollIntoView({ behavior: "smooth", block: "center" });
    };
    const hideAlert = () => alertBox.classList.add("d-none");

    const sEl = $("#startAt");
    const eEl = $("#endAt");
    if (sEl && !sEl.value) sEl.value = toLocal(new Date(Date.now() + 10 * 60 * 1000));
    if (eEl && !eEl.value) eEl.value = toLocal(new Date(Date.now() + 3 * 24 * 60 * 60 * 1000));

    const MAX = 10;
    const pickBox = $("#pickBox");
    const input = $("#images");
    const thumbs = $("#thumbs");
    const counter = $("#imgCount");
    let files = [];
    let urls = [];

    const clearURLs = () => { urls.forEach((u) => URL.revokeObjectURL(u)); urls = []; };

    function render() {
        clearURLs();
        thumbs.innerHTML = "";
        files.slice(0, MAX).forEach((f, i) => {
            const url = URL.createObjectURL(f);
            urls.push(url);
            const wrap = document.createElement("div");
            wrap.className = "thumb";
            wrap.style.position = "relative";
            wrap.innerHTML = `
        <img src="${url}" alt="thumb" style="width:100%;height:100%;object-fit:cover;">
        ${i === 0 ? '<span class="badge text-bg-dark" style="position:absolute;left:0;right:0;bottom:0;border-radius:0 0 10px 10px;background:rgba(0,0,0,.65);text-align:center;padding:2px 0;">대표 사진</span>' : ""}
        <button type="button" class="btn btn-sm btn-dark rounded-circle remove" data-i="${i}" style="position:absolute;top:-8px;right:-8px;height:24px;width:24px;line-height:1;padding:0">
          <i class="bi bi-x" style="font-size:12px"></i>
        </button>
      `;
            wrap.addEventListener("click", (ev) => {
                const isRemove = ev.target.closest(".remove");
                if (isRemove) return;
                if (i !== 0) {
                    const picked = files.splice(i, 1)[0];
                    files.unshift(picked);
                    render();
                }
            });
            thumbs.appendChild(wrap);
        });
        if (counter) counter.textContent = `${files.length}/${MAX}`;
    }

    function fileKey(f) { return `${f.name}::${f.size}::${f.lastModified}`; }

    function add(list) {
        const existing = new Set(files.map(fileKey));
        for (const f of Array.from(list)) {
            if (!f.type.startsWith("image/")) continue;
            if (f.size > 10 * 1024 * 1024) continue;
            if (files.length >= MAX) break;
            const key = fileKey(f);
            if (existing.has(key)) continue;
            existing.add(key);
            files.push(f);
        }
        render();
    }

    pickBox?.addEventListener("click", () => input?.click());
    input?.addEventListener("change", (e) => add(e.target.files));

    thumbs?.addEventListener("click", (e) => {
        const btn = e.target.closest(".remove");
        if (!btn) return;
        const i = +btn.dataset.i;
        files.splice(i, 1);
        render();
    });

    // ---------- 서버 전송 ----------
    const submitBtn = $("#submitBtn");

    async function submitForm() {
        hideAlert();

        const title = $("#title").value.trim();
        const description = $("#description").value.trim();
        const category = $("#category").value;
        const startAt = sEl.value;
        const endAt = eEl.value;
        const startPrice = Number($("#startPrice").value || 0);
        const minIncrement = Number($("#minIncrement").value || 0);
        const buyNow = Number($("#buyNowPrice").value || 0);

        // ✅ 이미지 최소 1장 검증 (서버 검증과 일치)
        if (files.length === 0) return showAlert("최소 1장 이상의 이미지를 선택하세요.");

        if (!title) return showAlert("제목을 입력하세요.");
        if (!description) return showAlert("자세한 설명을 입력하세요.");
        if (!category) return showAlert("카테고리를 선택하세요.");
        if (!startAt || !endAt) return showAlert("경매 시작/마감일을 입력하세요.");
        if (new Date(endAt) <= new Date(startAt)) return showAlert("마감일은 시작일 이후여야 합니다.");
        if (minIncrement <= 0) return showAlert("최소 입찰단위는 0보다 커야 합니다.");
        if (buyNow && buyNow < startPrice + minIncrement)
        return showAlert("즉시구매가는 시작가 + 최소입찰단위 이상이어야 합니다.");

        const fd = new FormData();
        fd.append("title", title);
        fd.append("description", description);
        fd.append("category", category);
        fd.append("startAt", startAt);
        fd.append("endAt", endAt);
        fd.append("startPrice", String(startPrice));
        fd.append("minIncrement", String(minIncrement));
        if (buyNow) fd.append("buyNow", String(buyNow));
        const ordered = files.slice(0, 10);
        ordered.forEach((f, idx) => {
            fd.append("images", f, f.name);
            fd.append("orders", String(idx + 1)); // 서버 DTO의 List<Integer> orders와 매칭
        });

        submitBtn.disabled = true;
        submitBtn.textContent = "등록 중...";

        try {

            const res = await authenticatedFetch("/api/upload", {
                method: "POST",
                body: fd
            });

            if (res.status === 401) {
                window.location.href = "/signin?next=%2Fproductupload";
                return;
            }
            if (!res.ok) {
                showAlert(await errorMessage(res, "등록에 실패했습니다. 입력값을 확인해주세요."));
                return;
            }

            const next = res.headers.get("Location") || "/main";
            window.location.replace(next);
        } catch (err) {
            console.error(err);
            showAlert("네트워크 오류가 발생했습니다.");
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = "작성 완료";
        }
    }

    submitBtn?.addEventListener("click", (e) => {
        e.preventDefault();
        submitForm();
    });
})();
