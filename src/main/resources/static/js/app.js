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

  const api = async (url, init) => {
    const res = await fetch(url, { headers: { Accept: 'application/json' }, ...init });
    let body = null;
    try { body = await res.json(); } catch (_) {}
    if (!body || typeof body.success !== 'boolean') {
      throw new Error(`유효하지 않은 응답입니다. (HTTP ${res.status})`);
    }
    if (!body.success) {
      const err = body.error ?? { code: 'UNKNOWN', message: '요청 처리 실패' };
      const e = new Error(err.message || err.code);
      e.code = err.code;
      throw e;
    }
    return body.data;
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
      ['1등 당첨금', `${fmtNum(wn.firstPrize)} 원`],
      ['1등 당첨자', `${fmtNum(wn.firstWinners)} 명`],
      ['총 판매금', `${fmtNum(wn.totalSales)} 원`]
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
    info.textContent = `${cur} / ${listState.totalPages} 페이지 · 총 ${fmtNum(listState.totalElements)}건`;
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

  const onCollectRefresh = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const targetRoundRaw = String(fd.get('targetRound') || '').trim();
    const targetRound = targetRoundRaw === '' ? null : targetRoundRaw;
    const adminToken = String(fd.get('adminToken') || '').trim();
    const out = document.getElementById('collect-result');
    const btn = e.currentTarget.querySelector('[type="submit"]');

    if (!adminToken) {
      if (out) {
        out.textContent = '관리자 토큰을 입력하세요.';
        out.className = 'small mt-2 text-danger';
      }
      return;
    }

    if (out) {
      out.textContent = '수집 요청 중...';
      out.className = 'small mt-2 text-muted';
    }

    await withLoading(btn, async () => {
      try {
        const data = await api('/api/winning-numbers/refresh', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Kraft-Admin-Token': adminToken
          },
          body: JSON.stringify(targetRound == null ? {} : { targetRound })
        });

        if (out) {
          out.textContent = `수집 완료: 저장 ${data.collected}, 건너뜀 ${data.skipped}, 실패 ${data.failed}, 최신 ${data.latestRound}회`;
          out.className = 'small mt-2 text-success';
        }

        listState.page = 0;
        loadLatest();
        loadList();
        loadFrequency();
      } catch (err) {
        if (out) {
          out.textContent = `수집 실패: ${err.message}`;
          out.className = 'small mt-2 text-danger';
        }
      }
    });
  };

  document.addEventListener('DOMContentLoaded', () => {
    initTheme();

    document.getElementById('themeToggle')?.addEventListener('click', () => {
      const cur = document.documentElement.getAttribute('data-bs-theme');
      setTheme(cur === 'dark' ? 'light' : 'dark');
    });

    document.getElementById('form-recommend')?.addEventListener('submit', onRecommend);
    document.getElementById('form-by-round')?.addEventListener('submit', onByRound);
    document.getElementById('form-collect-refresh')?.addEventListener('submit', onCollectRefresh);

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
