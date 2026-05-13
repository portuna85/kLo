(() => {
  'use strict';

  const THEME_KEY = 'kraft-theme';

  const setTheme = (theme) => {
    document.documentElement.setAttribute('data-bs-theme', theme);
    try { localStorage.setItem(THEME_KEY, theme); } catch (_) {}
    const icon = document.querySelector('#themeToggle i');
    if (icon) icon.className = theme === 'dark' ? 'bi bi-sun' : 'bi bi-moon-stars';
  };

  const initTheme = () => {
    let saved = null;
    try { saved = localStorage.getItem(THEME_KEY); } catch (_) {}
    const systemDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches;
    setTheme(saved ?? (systemDark ? 'dark' : 'light'));
  };

  const ERROR_MESSAGES = {
    REQUEST_VALIDATION_ERROR: '입력값을 확인하세요.',
    LOTTO_INVALID_COUNT: '추천 개수는 1~10개여야 합니다.',
    LOTTO_INVALID_TARGET_ROUND: '회차 입력값을 확인하세요.',
    UNAUTHORIZED_ADMIN_API: '관리자 토큰이 올바르지 않습니다.',
    WINNING_NUMBER_NOT_FOUND: '해당 데이터를 찾을 수 없습니다.',
    TOO_MANY_REQUESTS: '요청이 많습니다. 잠시 후 다시 시도하세요.',
    EXTERNAL_API_FAILURE: '외부 로또 API 연결에 실패했습니다.',
    INTERNAL_SERVER_ERROR: '서버 오류가 발생했습니다.'
  };

  const messageFromStatus = (status) => {
    if (status === 400) return '입력값을 확인하세요.';
    if (status === 401) return '관리자 토큰이 올바르지 않습니다.';
    if (status === 404) return '해당 데이터를 찾을 수 없습니다.';
    if (status === 429) return '요청이 많습니다. 잠시 후 다시 시도하세요.';
    if (status === 502) return '외부 로또 API 연결에 실패했습니다.';
    return `요청 처리 실패 (HTTP ${status})`;
  };

  const api = async (url, init = {}) => {
    const ctrl = new AbortController();
    const tid = setTimeout(() => ctrl.abort(), 10_000);
    try {
      const res = await fetch(url, {
        ...init,
        signal: init.signal ?? ctrl.signal,
        headers: { Accept: 'application/json', ...(init.headers ?? {}) }
      });
      let body = null;
      try { body = await res.json(); } catch (_) {}
      if (!body || typeof body.success !== 'boolean') {
        throw new Error(`유효하지 않은 응답입니다. (HTTP ${res.status})`);
      }
      if (!body.success) {
        const err = body.error ?? { code: 'UNKNOWN', message: '' };
        const mapped = ERROR_MESSAGES[err.code] || err.message || messageFromStatus(res.status);
        const e = new Error(mapped);
        e.code = err.code;
        e.status = res.status;
        throw e;
      }
      return body.data;
    } catch (err) {
      if (err.name === 'AbortError') throw new Error('요청 시간이 초과되었습니다.');
      if (err instanceof TypeError) throw new Error('네트워크 연결을 확인하세요.');
      throw err;
    } finally {
      clearTimeout(tid);
    }
  };

  const skeleton = () => '<div class="placeholder-glow"><span class="placeholder col-7"></span></div>';
  const fmtNum = (n) => Number(n ?? 0).toLocaleString('ko-KR');

  const setTextMessage = (container, text, className = 'small mb-0') => {
    container.replaceChildren();
    const p = document.createElement('p');
    p.className = className;
    p.textContent = text;
    container.appendChild(p);
  };

  const withLoading = async (btn, fn) => {
    if (!btn) return fn();
    const prev = btn.innerHTML;
    btn.disabled = true;
    btn.setAttribute('aria-busy', 'true');
    try {
      return await fn();
    } finally {
      btn.disabled = false;
      btn.removeAttribute('aria-busy');
      btn.innerHTML = prev;
    }
  };

  const ballClass = (n) => (n <= 10 ? 'b1' : n <= 20 ? 'b2' : n <= 30 ? 'b3' : n <= 40 ? 'b4' : 'b5');

  const ball = (n, bonus = false) => {
    const span = document.createElement('span');
    span.className = `kraft-ball ${ballClass(n)}${bonus ? ' bonus' : ''}`;
    span.textContent = n;
    return span;
  };

  const ballsRow = (numbers, bonus) => {
    const wrap = document.createElement('div');
    wrap.className = 'kraft-balls';
    numbers.forEach((n) => wrap.appendChild(ball(n)));
    if (bonus != null) {
      const plus = document.createElement('span');
      plus.className = 'kraft-ball-plus';
      plus.textContent = '+';
      wrap.appendChild(plus);
      wrap.appendChild(ball(bonus, true));
    }
    return wrap;
  };

  const renderWinning = (wn, container) => {
    container.replaceChildren();
    const head = document.createElement('div');
    head.className = 'd-flex justify-content-between align-items-center mb-2';

    const roundStrong = document.createElement('strong');
    roundStrong.textContent = `${wn.round}회`;
    const dateSpan = document.createElement('span');
    dateSpan.className = 'text-muted small';
    dateSpan.textContent = wn.drawDate;

    head.appendChild(roundStrong);
    head.appendChild(dateSpan);
    container.appendChild(head);
    container.appendChild(ballsRow(wn.numbers, wn.bonusNumber));

    const dl = document.createElement('dl');
    dl.className = 'kraft-kv';
    const kv = [
      ['1등 당첨금', `${fmtNum(wn.firstPrize)}원`],
      ['1등 당첨자', `${fmtNum(wn.firstWinners)}명`],
      ['총 판매금', `${fmtNum(wn.totalSales)}원`]
    ];

    kv.forEach(([k, v]) => {
      const dt = document.createElement('dt');
      dt.textContent = k;
      const dd = document.createElement('dd');
      dd.textContent = v;
      dl.appendChild(dt);
      dl.appendChild(dd);
    });

    container.appendChild(dl);
  };

  const onRecommend = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const count = Number(fd.get('count') || 5);
    const out = document.getElementById('recommend-result');
    const btn = e.currentTarget.querySelector('[type="submit"]');
    out.innerHTML = skeleton();

    await withLoading(btn, async () => {
      try {
        const data = await api('/api/recommend', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ count })
        });
        out.replaceChildren();
        data.combinations.forEach((c, i) => {
          const row = document.createElement('div');
          row.className = 'kraft-combo';
          const idx = document.createElement('span');
          idx.className = 'idx';
          idx.textContent = `#${i + 1}`;
          row.appendChild(idx);
          row.appendChild(ballsRow(c.numbers));
          out.appendChild(row);
        });
      } catch (err) {
        setTextMessage(out, err.message, 'text-danger small mb-0');
      }
    });
  };

  const loadLatest = async () => {
    const out = document.getElementById('latest-result');
    try {
      const data = await api('/api/winning-numbers/latest');
      renderWinning(data, out);
    } catch (err) {
      setTextMessage(out, err.message, 'text-danger small mb-0');
    }
  };

  const onByRound = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const round = Number(fd.get('round'));
    const out = document.getElementById('round-result');

    if (!Number.isInteger(round) || round < 1) {
      setTextMessage(out, '1 이상의 정수 회차를 입력하세요.', 'text-danger small mb-0');
      return;
    }

    const btn = e.currentTarget.querySelector('[type="submit"]');
    out.innerHTML = skeleton();

    await withLoading(btn, async () => {
      try {
        const data = await api(`/api/winning-numbers/${round}`);
        renderWinning(data, out);
      } catch (err) {
        setTextMessage(out, err.message, 'text-danger small mb-0');
      }
    });
  };

  const listState = { page: 0, size: 20, totalPages: 0, totalElements: 0, abortCtrl: null };

  const renderList = (pageData) => {
    const out = document.getElementById('list-result');
    out.replaceChildren();
    if (!pageData.content || pageData.content.length === 0) {
      setTextMessage(out, '조회된 회차가 없습니다.', 'text-muted small mb-0');
      return;
    }

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
      out.appendChild(row);
    });
  };

  const updatePager = () => {
    const info = document.getElementById('list-pageinfo');
    const prev = document.getElementById('list-prev');
    const next = document.getElementById('list-next');
    const cur = listState.totalPages === 0 ? 0 : listState.page + 1;
    info.textContent = `${cur} / ${listState.totalPages}페이지 · 총 ${fmtNum(listState.totalElements)}건`;
    prev.disabled = listState.page <= 0;
    next.disabled = listState.totalPages === 0 || listState.page >= listState.totalPages - 1;
  };

  const loadList = async () => {
    if (listState.abortCtrl) listState.abortCtrl.abort();
    listState.abortCtrl = new AbortController();
    const { signal } = listState.abortCtrl;

    const out = document.getElementById('list-result');
    out.innerHTML = skeleton();
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
  };

  const loadFrequency = async () => {
    const out = document.getElementById('freq-result');
    const lowOut = document.getElementById('freq-low6-result');
    out.innerHTML = '<div class="placeholder-glow w-100"><span class="placeholder col-12"></span></div>';
    if (lowOut) {
      lowOut.innerHTML = '<div class="placeholder-glow w-100"><span class="placeholder col-8"></span></div>';
    }

    try {
      const summaryData = await api('/api/winning-numbers/stats/frequency-summary');
      const data = summaryData.frequencies;
      const max = data.reduce((m, d) => Math.max(m, d.count), 1);
      const lowSixList = [...data].sort((a, b) => a.count - b.count || a.number - b.number).slice(0, 6);
      const lowSix = new Set(lowSixList.map((d) => d.number));

      out.replaceChildren();
      data.forEach(({ number, count }) => {
        const cell = document.createElement('div');
        cell.className = 'kraft-freq-cell';
        if (lowSix.has(number)) cell.classList.add('low-freq');

        const pct = Math.round((count / max) * 100);
        const n = document.createElement('span');
        n.className = 'n';
        n.textContent = number;

        const bar = document.createElement('div');
        bar.className = 'bar';
        const i = document.createElement('i');
        i.style.width = `${pct}%`;
        bar.appendChild(i);

        const small = document.createElement('small');
        small.textContent = count;

        cell.appendChild(n);
        cell.appendChild(bar);
        cell.appendChild(small);
        out.appendChild(cell);
      });

      if (lowOut) {
        lowOut.replaceChildren();
        const history = summaryData.lowSixCombinationHistory;
        const summary = document.createElement('div');
        summary.className = 'small';
        const combo = history.numbers.join(', ');
        const firstRounds = history.firstPrizeHits.map((h) => `${h.round}회`).join(', ') || '없음';
        const secondRounds = history.secondPrizeHits.map((h) => `${h.round}회`).join(', ') || '없음';
        summary.innerHTML = `<strong>조합 ${combo}</strong> · 1등 ${history.firstPrizeCount}회 (${firstRounds}) · 2등 ${history.secondPrizeCount}회 (${secondRounds})`;
        lowOut.appendChild(summary);
      }
    } catch (err) {
      setTextMessage(out, err.message, 'text-danger small mb-0');
      if (lowOut) setTextMessage(lowOut, err.message, 'text-danger small mb-0');
    }
  };

  document.addEventListener('DOMContentLoaded', () => {
    initTheme();

    document.getElementById('themeToggle')?.addEventListener('click', () => {
      const cur = document.documentElement.getAttribute('data-bs-theme');
      setTheme(cur === 'dark' ? 'light' : 'dark');
    });

    document.getElementById('form-recommend')?.addEventListener('submit', onRecommend);
    document.getElementById('form-by-round')?.addEventListener('submit', onByRound);

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

    loadLatest();
    loadFrequency();
    loadList();
  });
})();
