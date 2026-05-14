import { api } from './api.js';
import { ballsRow, fmtNum, setTextMessage, showSkeleton } from './ui.js';

const listState = { page: 0, size: 20, totalPages: 0, totalElements: 0, abortCtrl: null };

function renderList(pageData) {
  const out = document.getElementById('list-result');
  if (!pageData.content || pageData.content.length === 0) {
    setTextMessage(out, '조회된 회차가 없습니다.', 'text-muted small mb-0');
    return;
  }
  const fragment = document.createDocumentFragment();
  pageData.content.forEach((wn) => {
    const row = document.createElement('div');
    row.className = 'kraft-list-row';
    const r = document.createElement('span');
    r.className = 'round';
    r.textContent = `${wn.round}회`;
    const d = document.createElement('span');
    d.className = 'date';
    d.textContent = wn.drawDate;
    row.appendChild(r);
    row.appendChild(d);
    row.appendChild(ballsRow(wn.numbers, wn.bonusNumber));
    fragment.appendChild(row);
  });
  out.replaceChildren(fragment);
}

function updatePager() {
  const info = document.getElementById('list-pageinfo');
  const prev = document.getElementById('list-prev');
  const next = document.getElementById('list-next');
  const cur = listState.totalPages === 0 ? 0 : listState.page + 1;
  info.textContent = `${cur} / ${listState.totalPages}페이지 · 총 ${fmtNum(listState.totalElements)}건`;
  prev.disabled = listState.page <= 0;
  next.disabled = listState.totalPages === 0 || listState.page >= listState.totalPages - 1;
}

export async function loadList() {
  if (listState.abortCtrl) listState.abortCtrl.abort();
  listState.abortCtrl = new AbortController();
  const { signal } = listState.abortCtrl;
  const out = document.getElementById('list-result');
  showSkeleton(out, 'col-12');
  try {
    const data = await api(`/api/winning-numbers?page=${listState.page}&size=${listState.size}`, { signal });
    listState.abortCtrl = null;
    listState.totalPages = data.totalPages;
    listState.totalElements = data.totalElements;
    renderList(data);
    updatePager();
  } catch (err) {
    if (err.name === 'AbortError') return;
    setTextMessage(out, err.message, 'text-danger small mb-0');
    updatePager();
  }
}

export function bindListControls() {
  document.getElementById('list-prev')?.addEventListener('click', () => {
    if (listState.page > 0) {
      listState.page -= 1;
      loadList();
    }
  });
  document.getElementById('list-next')?.addEventListener('click', () => {
    if (listState.page < listState.totalPages - 1) {
      listState.page += 1;
      loadList();
    }
  });
  document.getElementById('list-size')?.addEventListener('change', (e) => {
    listState.size = Number(e.target.value) || 20;
    listState.page = 0;
    loadList();
  });
}
