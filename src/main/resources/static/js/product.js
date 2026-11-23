// ===== 기본 참조 =====
const grid   = document.getElementById('productGrid');
const sortSel = document.getElementById('sortSelect');
const catSel  = document.getElementById('categorySelect');
const gridBtn = document.getElementById('gridBtn');
const listBtn = document.getElementById('listBtn');

const KR = new Intl.NumberFormat('ko-KR');
function daysUntil(iso){
  if (!iso) return 0;
  const end = new Date(iso), now = new Date();
  return Math.max(0, Math.ceil((end - now)/86400000));
}
function formatKDateTimeParts(iso){
  if (!iso) return { date:'', time:'' };
  const d = new Date(iso);
  const m = String(d.getMonth()+1).padStart(2,'0');
  const day = String(d.getDate()).padStart(2,'0');
  let h = d.getHours();
  const ap = h >= 12 ? '오후' : '오전';
  h = h % 12 || 12;
  const hh = String(h).padStart(2,'0');
  const mm = String(d.getMinutes()).padStart(2,'0');
  return { date: `${m}월 ${day}일`, time: `${ap} ${hh}시 ${mm}분` };
}

// ===== 1) RUNNING 카드 =====
function createRunningCard(p) {
  const wrap = document.createElement('div');
  wrap.className = 'col-6 col-md-4 col-lg-3 col-card';
  wrap.dataset.id       = p.id;

  wrap.innerHTML = `
    <div class="card product-card h-100">
      <img class="card-img-top" alt="상품" />
      <div class="card-body p-2">
        <p class="mb-1 text-truncate"></p>
        <div class="d-flex justify-content-between">
          <span class="price">₩${KR.format(p.price ?? p.startPrice ?? 0)}</span>
          <span class="muted">${p.ddayText ?? `D-${daysUntil(p.endAt)}`}</span>
        </div>
      </div>
    </div>
  `;
  const image = wrap.querySelector("img");
  image.src = /^\/files\/products\/[0-9]+_400\/[0-9]+\.jpg$/.test(p.imageUrl ?? "")
    ? p.imageUrl : "/image/default_product.png";
  wrap.querySelector("p").textContent = p.title ?? "";
  return wrap;
}

// ===== 2) 상태별 카드 분기 =====
function createProductCard(p) {
  if (p.status === 'RUNNING') return createRunningCard(p);

  if (p.status === 'SCHEDULED') {
    const el = createRunningCard(p);
    const card = el.querySelector('.product-card');
    card.classList.add('scheduled');

    const { date, time } = formatKDateTimeParts(p.startAt);
    card.insertAdjacentHTML(
      'afterbegin',
      `<div class="overlay overlay-scheduled">
         <div class="overlay-text">
           <div>${date}</div>
           <div>${time}</div>
           <div>오픈 예정</div>
         </div>
       </div>`
    );

    card.addEventListener('click', e => e.preventDefault());
    return el;
  }

  // ENDED
  const el = createRunningCard(p);
  const card = el.querySelector('.product-card');
  card.classList.add('ended');
  el.querySelector('.d-flex .muted').textContent = '종료';
  card.insertAdjacentHTML('beforeend',
    `<span class="badge text-bg-secondary" style="position:absolute;top:8px;right:8px">종료</span>`
  );

  card.addEventListener('click', e => {
    e.preventDefault();
    e.stopPropagation();
  });
  return el;
}

const previous = document.getElementById("previousProducts");
const next = document.getElementById("nextProducts");
const summary = document.getElementById("productPageSummary");
const message = document.getElementById("productListMessage");
let page = 0;
let request;

async function loadProduct(target = 0) {
  request?.abort();
  const controller = new AbortController();
  request = controller;
  previous.disabled = true;
  next.disabled = true;
  message.textContent = "";
  const params = new URLSearchParams({ page: target, sort: sortSel.value, category: catSel.value });
  try {
    const response = await fetch(`/api/products?${params}`, { signal: controller.signal });
    if (!response.ok) throw new Error("Unable to load products");
    const result = await response.json();
    if (controller.signal.aborted) return;
    page = result.page;
    grid.replaceChildren(...result.content.map(createProductCard));
    message.textContent = result.content.length ? "" : "등록된 상품이 없습니다.";
    summary.textContent = result.totalPages ? `${page + 1} / ${result.totalPages}` : "";
    previous.disabled = page === 0;
    next.disabled = page + 1 >= result.totalPages;
  } catch (error) {
    if (error.name !== "AbortError") message.textContent = "상품을 불러오지 못했습니다.";
  }
}

sortSel.addEventListener("change", () => loadProduct(0));
catSel.addEventListener("change", () => loadProduct(0));
previous.addEventListener("click", () => loadProduct(page - 1));
next.addEventListener("click", () => loadProduct(page + 1));

// 보기 전환
gridBtn?.addEventListener('click', (e)=>{
  e.preventDefault();
  grid.classList.remove('list-view');
  gridBtn.classList.add('active');
  listBtn?.classList.remove('active');
});
listBtn?.addEventListener('click', (e)=>{
  e.preventDefault();
  grid.classList.add('list-view');
  listBtn.classList.add('active');
  gridBtn?.classList.remove('active');
});

grid.addEventListener('click', (e)=>{
  const card = e.target.closest('.product-card');
  if (!card) return;
  const wrap = card.closest('.col-card');
  const id = wrap?.dataset.id;

  // 예약은 클릭 막기
  if (card.classList.contains('scheduled')) return;

  if (id) location.href = `/productdetail?id=${id}`;
});

// ===== 6) 페이지 로드 시 실행 =====
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => loadProduct());
} else {
  loadProduct();
}
