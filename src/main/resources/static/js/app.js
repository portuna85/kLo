(() => {
  'use strict';

  // ───────────────────────── Logger ─────────────────────────
  // 단계별 구조화 로깅.
  //   - 레벨: debug < info < warn < error  (silent 로 전체 차단)
  //   - 활성화: URL ?debug=1, localStorage['kraft-log-level'] 또는 KraftLog.setLevel(...)
  //   - 출력 형식: [HH:MM:SS.mmm] [LEVEL] [scope] message  { ...context }
  //   - 각 단계는 logger.step('scope') 로 만든 자식 로거를 사용해 흐름을 일관되게 추적.
  const Logger = (() => {
    const LEVELS = { debug: 10, info: 20, warn: 30, error: 40, silent: 99 };
    const LEVEL_KEY = 'kraft-log-level';
    const COLORS = {
      debug: 'color:#6b7280',
      info: 'color:#0d6efd',
      warn: 'color:#b45309',
      error: 'color:#dc2626;font-weight:bold'
    };

    const detectInitialLevel = () => {
      try {
        const u = new URL(window.location.href);
        if (u.searchParams.get('debug') === '1') return 'debug';
        const saved = localStorage.getItem(LEVEL_KEY);
        if (saved && saved in LEVELS) return saved;
      } catch (_) { /* noop */ }
      return 'info';
    };

    let current = detectInitialLevel();
    const ts = () => {
      const d = new Date();
      const pad = (n, w = 2) => String(n).padStart(w, '0');
      return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`;
    };

    const enabled = (lvl) => LEVELS[lvl] >= LEVELS[current];

    const emit = (lvl, scope, msg, ctx) => {
      if (!enabled(lvl)) return;
      const head = `%c[${ts()}] [${lvl.toUpperCase()}] [${scope}]%c ${msg}`;
      const fn = lvl === 'error' ? console.error
              : lvl === 'warn' ? console.warn
              : lvl === 'debug' ? console.debug
              : console.log;
      if (ctx === undefined) fn(head, COLORS[lvl], 'color:inherit');
      else fn(head, COLORS[lvl], 'color:inherit', ctx);
    };

    const make = (scope) => ({
      scope,
      debug: (m, c) => emit('debug', scope, m, c),
      info:  (m, c) => emit('info',  scope, m, c),
      warn:  (m, c) => emit('warn',  scope, m, c),
      error: (m, c) => emit('error', scope, m, c),
      step:  (sub) => make(`${scope} › ${sub}`),
      time:  (label) => {
        const t0 = performance.now();
        return {
          end: (extra) => {
            const ms = Math.round(performance.now() - t0);
            emit('debug', scope, `${label} (${ms}ms)`, extra);
            return ms;
          }
        };
      }
    });

    return {
      root: make('app'),
      step: (s) => make(s),
      setLevel: (lvl) => {
        if (!(lvl in LEVELS)) { console.warn('unknown log level:', lvl); return; }
        current = lvl;
        try { localStorage.setItem(LEVEL_KEY, lvl); } catch (_) { /* noop */ }
        emit('info', 'app', `log level → ${lvl}`);
      },
      getLevel: () => current
    };
  })();

  // 콘솔에서 수동 제어 가능하도록 노출.
  window.KraftLog = Logger;

  const log = Logger.root;
  log.info(`KraftLotto UI 시작 (level=${Logger.getLevel()})`);

  // ───────────────────────── Theme ─────────────────────────
  const THEME_KEY = 'kraft-theme';
  const setTheme = (t) => {
    document.documentElement.setAttribute('data-bs-theme', t);
    localStorage.setItem(THEME_KEY, t);
    const icon = document.querySelector('#themeToggle i');
    if (icon) icon.className = t === 'dark' ? 'bi bi-sun' : 'bi bi-moon-stars';
    Logger.step('theme').debug('theme set', { theme: t });
  };
  const initTheme = () => {
    const saved = localStorage.getItem(THEME_KEY);
    const sysDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches;
    const t = saved ?? (sysDark ? 'dark' : 'light');
    Logger.step('theme').debug('init', { saved, sysDark, applied: t });
    setTheme(t);
  };

  // ───────────────────────── fetch + ApiResponse ─────────────────────────
  const api = async (url, init) => {
    const method = (init && init.method) || 'GET';
    const flog = Logger.step(`api ${method} ${url}`);
    flog.debug('request', { headers: init?.headers, hasBody: !!(init && init.body) });
    const t = flog.time('roundtrip');
    let res;
    try {
      res = await fetch(url, { headers: { 'Accept': 'application/json' }, ...init });
    } catch (netErr) {
      flog.error('네트워크 오류', { error: netErr.message });
      throw netErr;
    }
    let body = null;
    try { body = await res.json(); } catch (_) { /* noop */ }
    t.end({ status: res.status, ok: res.ok });
    if (!body || typeof body.success !== 'boolean') {
      flog.error('응답 형식 오류', { status: res.status, body });
      throw new Error(`서버 응답 형식이 올바르지 않습니다 (HTTP ${res.status})`);
    }
    if (!body.success) {
      const err = body.error ?? { code: 'UNKNOWN', message: '알 수 없는 오류' };
      flog.warn('API 실패 응답', { status: res.status, code: err.code, message: err.message });
      const e = new Error(err.message || err.code);
      e.code = err.code;
      throw e;
    }
    flog.debug('성공', { status: res.status });
    return body.data;
  };

  // ───────────────────────── Toast ─────────────────────────
  const toast = (msg, isError = false) => {
    const el = document.getElementById('toast');
    if (!el) return;
    el.textContent = msg;
    el.classList.toggle('error', isError);
    el.classList.add('show');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => el.classList.remove('show'), 2400);
    (isError ? Logger.step('toast').warn : Logger.step('toast').debug)('표시', { msg });
  };

  // ───────────────────────── 6볼 렌더 ─────────────────────────
  const ballClass = (n) =>
    n <= 10 ? 'b1' : n <= 20 ? 'b2' : n <= 30 ? 'b3' : n <= 40 ? 'b4' : 'b5';

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

  const fmtNum = (n) => Number(n ?? 0).toLocaleString('ko-KR');
  const skeleton = () =>
    '<div class="placeholder-glow"><span class="placeholder col-7"></span></div>';

  // ───────────────────────── 추천 ─────────────────────────
  const onRecommend = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const count = Number(fd.get('count') || 5);
    const out = document.getElementById('recommend-result');
    out.innerHTML = skeleton();
    try {
      const data = await api('/api/recommend', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ count })
      });
      out.innerHTML = '';
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
      out.innerHTML = `<p class="text-danger small mb-0">${err.message}</p>`;
      toast(`추천 실패: ${err.code ?? ''} ${err.message}`, true);
    }
  };

  // ───────────────────────── 당첨번호 렌더 ─────────────────────────
  const renderWinning = (wn, container) => {
    container.innerHTML = '';
    const head = document.createElement('div');
    head.className = 'd-flex justify-content-between align-items-center mb-2';
    head.innerHTML =
      `<strong>${wn.round}회</strong>` +
      `<span class="text-muted small">${wn.drawDate}</span>`;
    container.appendChild(head);
    container.appendChild(ballsRow(wn.numbers, wn.bonusNumber));
    const dl = document.createElement('dl');
    dl.className = 'kraft-kv';
    dl.innerHTML =
      `<dt>1등 당첨금</dt><dd>${fmtNum(wn.firstPrize)} 원</dd>` +
      `<dt>1등 당첨자</dt><dd>${fmtNum(wn.firstWinners)} 명</dd>` +
      `<dt>총 판매금</dt><dd>${fmtNum(wn.totalSales)} 원</dd>`;
    container.appendChild(dl);
  };

  // ───────────────────────── 최신 회차 ─────────────────────────
  const loadLatest = async () => {
    const out = document.getElementById('latest-result');
    try {
      const data = await api('/api/winning-numbers/latest');
      renderWinning(data, out);
    } catch (err) {
      out.innerHTML = `<p class="text-danger small mb-0">${err.message}</p>`;
    }
  };

  // ───────────────────────── 회차 검색 ─────────────────────────
  const onByRound = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const round = Number(fd.get('round'));
    const out = document.getElementById('round-result');
    out.innerHTML = skeleton();
    try {
      const data = await api(`/api/winning-numbers/${round}`);
      renderWinning(data, out);
    } catch (err) {
      out.innerHTML = `<p class="text-danger small mb-0">${err.message}</p>`;
      toast(`${err.code ?? ''} ${err.message}`, true);
    }
  };

  // ───────────────────────── 회차 목록 (페이지네이션) ─────────────────────────
  const listState = { page: 0, size: 20, totalPages: 0, totalElements: 0 };

  const renderList = (pageData) => {
    const out = document.getElementById('list-result');
    out.innerHTML = '';
    if (!pageData.content || pageData.content.length === 0) {
      out.innerHTML = '<p class="text-muted small mb-0">표시할 회차가 없습니다.</p>';
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
    info.textContent = `${cur} / ${listState.totalPages} 페이지 · 총 ${fmtNum(listState.totalElements)}회차`;
    prev.disabled = listState.page <= 0;
    next.disabled = listState.totalPages === 0 || listState.page >= listState.totalPages - 1;
  };

  const loadList = async () => {
    const out = document.getElementById('list-result');
    out.innerHTML = skeleton();
    try {
      const data = await api(
        `/api/winning-numbers?page=${listState.page}&size=${listState.size}`
      );
      listState.totalPages = data.totalPages;
      listState.totalElements = data.totalElements;
      renderList(data);
      updatePager();
    } catch (err) {
      out.innerHTML = `<p class="text-danger small mb-0">${err.message}</p>`;
      updatePager();
    }
  };

  // ───────────────────────── 관리자: 수집 트리거 ─────────────────────────
  const onAdminRefresh = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const username = String(fd.get('username') || '').trim();
    const password = String(fd.get('password') || '');
    const targetRoundRaw = String(fd.get('targetRound') || '').trim();
    const targetRound = targetRoundRaw === '' ? null : Number(targetRoundRaw);
    const out = document.getElementById('admin-result');
    out.innerHTML = '<span class="text-muted">수집 요청 중…</span>';

    const auth = 'Basic ' + btoa(`${username}:${password}`);
    try {
      const res = await fetch('/api/admin/winning-numbers/refresh', {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          'Authorization': auth
        },
        body: JSON.stringify(targetRound == null ? {} : { targetRound })
      });
      let body = null;
      try { body = await res.json(); } catch (_) { /* noop */ }
      if (res.status === 401) {
        out.innerHTML = '<span class="text-danger">인증 실패: 아이디/비밀번호를 확인하세요.</span>';
        return;
      }
      if (!body || !body.success) {
        const msg = body?.error?.message || `HTTP ${res.status}`;
        out.innerHTML = `<span class="text-danger">실패: ${msg}</span>`;
        return;
      }
      const d = body.data;
      out.innerHTML =
        `<span class="text-success">수집 완료</span> · ` +
        `신규 <strong>${d.collected}</strong> · 스킵 ${d.skipped} · 실패 ${d.failed} · 최신 ${d.latestRound}회`;
      toast(`수집 완료: 신규 ${d.collected} · 최신 ${d.latestRound}회`);
      // 목록/최신 갱신
      listState.page = 0;
      loadLatest();
      loadList();
      loadFrequency();
    } catch (err) {
      out.innerHTML = `<span class="text-danger">네트워크 오류: ${err.message}</span>`;
    }
  };

  // ───────────────────────── 빈도 ─────────────────────────
  const loadFrequency = async () => {
    const out = document.getElementById('freq-result');
    out.innerHTML =
      '<div class="placeholder-glow w-100"><span class="placeholder col-12"></span></div>';
    try {
      const data = await api('/api/winning-numbers/stats/frequency');
      const max = data.reduce((m, d) => Math.max(m, d.count), 1);
      out.innerHTML = '';
      data.forEach(({ number, count }) => {
        const cell = document.createElement('div');
        cell.className = 'kraft-freq-cell';
        const pct = Math.round((count / max) * 100);
        cell.innerHTML =
          `<span class="n">${number}</span>` +
          `<div class="bar"><i style="width:${pct}%"></i></div>` +
          `<small>${count}</small>`;
        out.appendChild(cell);
      });
    } catch (err) {
      out.innerHTML = `<p class="text-danger small mb-0">${err.message}</p>`;
    }
  };

  // ───────────────────────── Bootstrap ─────────────────────────
  document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    document.getElementById('themeToggle')?.addEventListener('click', () => {
      const cur = document.documentElement.getAttribute('data-bs-theme');
      setTheme(cur === 'dark' ? 'light' : 'dark');
    });
    document.getElementById('form-recommend')?.addEventListener('submit', onRecommend);
    document.getElementById('form-by-round')?.addEventListener('submit', onByRound);
    document.getElementById('form-admin-refresh')?.addEventListener('submit', onAdminRefresh);
    document.getElementById('list-prev')?.addEventListener('click', () => {
      if (listState.page > 0) { listState.page -= 1; loadList(); }
    });
    document.getElementById('list-next')?.addEventListener('click', () => {
      if (listState.page < listState.totalPages - 1) { listState.page += 1; loadList(); }
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

