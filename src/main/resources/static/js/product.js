// ===== 기본 참조 =====
const grid   = document.getElementById('productGrid');
let   cards  = [];
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
  wrap.dataset.category = p.category || '';
  wrap.dataset.price    = p.price ?? p.startPrice ?? 0;
  wrap.dataset.endat    = p.endAt || '';
  wrap.dataset.pop      = p.popularity ?? p.pop ?? 0;
  wrap.dataset.recent   = p.recent ?? p.updatedAt ?? p.createdAt ?? p.endAt ?? '';

  wrap.innerHTML = `
    <div class="card product-card h-100">
      <img src="${p.imageUrl || 'https://via.placeholder.com/400'}" class="card-img-top" alt="상품" />
      <div class="card-body p-2">
        <p class="mb-1 text-truncate">${p.title ?? ''}</p>
        <div class="d-flex justify-content-between">
          <span class="price">₩${KR.format(p.price ?? p.startPrice ?? 0)}</span>
          <span class="muted">${p.ddayText ?? `D-${daysUntil(p.endAt)}`}</span>
        </div>
      </div>
    </div>
  `;
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

// ===== 3) 데이터 로드 & 렌더 =====
async function loadProduct() {
  const res = await fetch('/api/products', { credentials: 'include' });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const data = await res.json();

  const items = Array.isArray(data) ? data : (data?.content ?? []);
  grid.innerHTML = '';

  const frag = document.createDocumentFragment();
  items.forEach(p => frag.appendChild(createProductCard(p)));
  grid.appendChild(frag);
  cards = Array.from(grid.children);

  applyFilterSort();
}

// ===== 4) 정렬/필터 =====
function applyFilterSort(){
  const cat = catSel?.value || '';

  cards.forEach(c => {
    const ok = !cat || c.dataset.category === cat;
    c.style.display = ok ? '' : 'none';
  });

  const visible = cards.filter(c => c.style.display !== 'none');

  const by = sortSel?.value || 'recent';
  visible.sort((a,b)=>{
    const num = v => Number(v||0);
    const date = v => new Date(v||0).getTime();
    switch(by){
      case 'priceAsc':  return num(a.dataset.price) - num(b.dataset.price);
      case 'priceDesc': return num(b.dataset.price) - num(a.dataset.price);
      case 'endSoon':   return date(a.dataset.endat) - date(b.dataset.endat);
      case 'popular':   return num(b.dataset.pop) - num(a.dataset.pop);
      default:          return date(b.dataset.recent) - date(a.dataset.recent);
    }
  });

  visible.forEach(el => grid.appendChild(el));
}

// ===== 5) 이벤트 바인딩 =====
sortSel?.addEventListener('change', applyFilterSort);
catSel?.addEventListener('change', applyFilterSort);

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
  document.addEventListener('DOMContentLoaded', loadProduct);
} else {
  loadProduct();
}