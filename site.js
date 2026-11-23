const screens = {
  products: ["상품 목록", "/main", "등록한 상품의 썸네일과 현재가가 보입니다. 카테고리와 가격순 정렬은 서버에서 적용합니다."],
  signup: ["회원가입", "/signup", "이름과 이메일, 비밀번호를 입력해 거래에 참여할 계정을 만듭니다."],
  signin: ["로그인", "/signin", "로그인 뒤 등록·입찰·채팅 화면으로 이동합니다. 브라우저에서는 HttpOnly 쿠키를 사용합니다."],
  upload: ["상품 등록", "/productupload", "상품 사진과 입찰 기간, 시작가와 증가액, 즉시 구매가를 설정합니다."],
  auction: ["자동입찰", "/productdetail", "경쟁 입찰 후 현재가가 20,000원으로 바뀌었고, 새로고침한 뒤에도 같은 가격이 표시됩니다."],
  "chat-list": ["채팅 목록", "/chatlist", "거래가 확정된 채팅방과 최근 메시지를 확인합니다."],
  chat: ["거래 채팅", "/chat/{id}", "25,000원에 거래를 확정한 두 계정이 메시지를 주고받았습니다."]
};

const tabs = [...document.querySelectorAll(".screen-tabs [role=tab]")];
const panel = document.getElementById("screen-panel");
const screenTitle = document.getElementById("screen-title");
const screenCaption = document.getElementById("screen-caption");
const screenPath = document.getElementById("screen-path");
const screenImage = document.getElementById("screen-image");
const dialog = document.getElementById("screen-dialog");
const zoom = document.getElementById("zoom-screen");

function selectScreen(tab) {
  const key = tab.dataset.key;
  const [title, path, caption] = screens[key];
  for (const candidate of tabs) {
    candidate.setAttribute("aria-selected", String(candidate === tab));
    candidate.tabIndex = candidate === tab ? 0 : -1;
  }
  panel.setAttribute("aria-labelledby", tab.id);
  screenTitle.textContent = title;
  screenCaption.textContent = caption;
  screenPath.textContent = path;
  screenImage.src = "./assets/" + key + ".webp";
  screenImage.alt = "BuyBid " + title + " 실행 화면";
}

for (const tab of tabs) {
  tab.addEventListener("click", () => selectScreen(tab));
  tab.addEventListener("keydown", event => {
    const current = tabs.indexOf(tab);
    const next = {
      ArrowLeft: (current - 1 + tabs.length) % tabs.length,
      ArrowRight: (current + 1) % tabs.length,
      Home: 0,
      End: tabs.length - 1
    }[event.key];
    if (next === undefined) return;
    event.preventDefault();
    tabs[next].focus();
    selectScreen(tabs[next]);
  });
}

zoom.addEventListener("click", () => {
  const image = document.getElementById("dialog-image");
  image.src = screenImage.currentSrc || screenImage.src;
  image.alt = screenImage.alt;
  document.getElementById("dialog-title").textContent = screenTitle.textContent;
  dialog.showModal();
});
dialog.addEventListener("close", () => zoom.focus());
