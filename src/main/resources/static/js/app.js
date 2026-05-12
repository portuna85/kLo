(() => {
  'use strict';

  // ????????????????????????? Logger ?????????????????????????
  // ?④퀎蹂?援ъ“??濡쒓퉭.
  //   - ?덈꺼: debug < info < warn < error  (silent 濡??꾩껜 李⑤떒)
  //   - ?쒖꽦?? URL ?debug=1, localStorage['kraft-log-level'] ?먮뒗 KraftLog.setLevel(...)
  //   - 異쒕젰 ?뺤떇: [HH:MM:SS.mmm] [LEVEL] [scope] message  { ...context }
  //   - 媛??④퀎??logger.step('scope') 濡?留뚮뱺 ?먯떇 濡쒓굅瑜??ъ슜???먮쫫???쇨??섍쾶 異붿쟻.
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
      step:  (sub) => make(`${scope} ??${sub}`),
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
        emit('info', 'app', `log level ??${lvl}`);
      },
      getLevel: () => current
    };
  })();

  // 肄섏넄?먯꽌 ?섎룞 ?쒖뼱 媛?ν븯?꾨줉 ?몄텧.
  window.KraftLog = Logger;

  const log = Logger.root;
  log.info(`KraftLotto UI ?쒖옉 (level=${Logger.getLevel()})`);

  // ????????????????????????? Theme ?????????????????????????
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

  // ????????????????????????? fetch + ApiResponse ?????????????????????????
  const api = async (url, init) => {
    const method = (init && init.method) || 'GET';
    const flog = Logger.step(`api ${method} ${url}`);
    // 誘쇨컧 header 留덉뒪???좏떥 ?곸슜
    const maskHeaders = (headers) => {
      if (!headers) return headers;
      const SENSITIVE = [
        'authorization', 'token', 'admin-token', 'x-kraft-admin-token'
      ];
      const masked = {};
      for (const k in headers) {
        if (SENSITIVE.includes(k.toLowerCase())) masked[k] = '[REDACTED]';
        else masked[k] = headers[k];
      }
      return masked;
    };
    flog.debug('request', { headers: maskHeaders(init?.headers), hasBody: !!(init && init.body) });
    const t = flog.time('roundtrip');
    let res;
    try {
      res = await fetch(url, { headers: { 'Accept': 'application/json' }, ...init });
    } catch (netErr) {
      flog.error('?ㅽ듃?뚰겕 ?ㅻ쪟', { error: netErr.message });
      throw netErr;
    }
    let body = null;
    try { body = await res.json(); } catch (_) { /* noop */ }
    t.end({ status: res.status, ok: res.ok });
    if (!body || typeof body.success !== 'boolean') {
      flog.error('?묐떟 ?뺤떇 ?ㅻ쪟', { status: res.status, body });
      throw new Error(`?쒕쾭 ?묐떟 ?뺤떇???щ컮瑜댁? ?딆뒿?덈떎 (HTTP ${res.status})`);
    }
    if (!body.success) {
      const err = body.error ?? { code: 'UNKNOWN', message: '?????녿뒗 ?ㅻ쪟' };
      flog.warn('API ?ㅽ뙣 ?묐떟', { status: res.status, code: err.code, message: err.message });
      const e = new Error(err.message || err.code);
      e.code = err.code;
      throw e;
    }
    flog.debug('?깃났', { status: res.status });
    return body.data;
  };

  // ????????????????????????? Toast ?????????????????????????
  let toastTimer = null;
  const toast = (msg, isError = false) => {
    const el = document.getElementById('toast');
    if (!el) return;
    el.textContent = msg;
    el.classList.toggle('error', isError);
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 2400);
    (isError ? Logger.step('toast').warn : Logger.step('toast').debug)('?쒖떆', { msg });
  };

  // ????????????????????????? ?좏떥: 踰꾪듉 濡쒕뵫 ?곹깭 ?????????????????????????
  const withLoading = async (btn, fn) => {
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

  // ????????????????????????? 6蹂??뚮뜑 ?????????????????????????
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
  const setTextMessage = (container, text, className = 'small mb-0') => {
    container.replaceChildren();
    const p = document.createElement('p');
    p.className = className;
    p.textContent = text;
    container.appendChild(p);
  };

  // ????????????????????????? 異붿쿇 ?????????????????????????
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
        toast(`異붿쿇 ?ㅽ뙣: ${err.code ?? ''} ${err.message}`, true);
      }
    });
  };

  // ????????????????????????? ?뱀꺼踰덊샇 ?뚮뜑 ?????????????????????????
  const renderWinning = (wn, container) => {
    container.replaceChildren();
    const head = document.createElement('div');
    head.className = 'd-flex justify-content-between align-items-center mb-2';
    const roundStrong = document.createElement('strong');
    roundStrong.textContent = `${wn.round}??;
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
      ['1???뱀꺼湲?, `${fmtNum(wn.firstPrize)} ??],
      ['1???뱀꺼??, `${fmtNum(wn.firstWinners)} 紐?],
      ['珥??먮ℓ湲?, `${fmtNum(wn.totalSales)} ??]
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

  // ????????????????????????? 理쒖떊 ?뚯감 ?????????????????????????
  const loadLatest = async () => {
    const out = document.getElementById('latest-result');
    try {
      const data = await api('/api/winning-numbers/latest');
      renderWinning(data, out);
    } catch (err) {
      setTextMessage(out, err.message, 'text-danger small mb-0');
      toast(`理쒖떊 ?뚯감 濡쒕뱶 ?ㅽ뙣: ${err.message}`, true);
    }
  };

  // ????????????????????????? ?뚯감 寃???????????????????????????
  const onByRound = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const round = Number(fd.get('round'));
    const out = document.getElementById('round-result');

    if (!Number.isInteger(round) || round < 1) {
      setTextMessage(out, '1 ?댁긽???щ컮瑜??뚯감瑜??낅젰??二쇱꽭??', 'text-danger small mb-0');
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
        toast(`${err.code ?? ''} ${err.message}`, true);
      }
    });
  };

  // ????????????????????????? ?뚯감 紐⑸줉 (?섏씠吏?ㅼ씠?? ?????????????????????????
  const listState = { page: 0, size: 20, totalPages: 0, totalElements: 0, abortCtrl: null };

  const renderList = (pageData) => {
    const out = document.getElementById('list-result');
    out.replaceChildren();
    if (!pageData.content || pageData.content.length === 0) {
      setTextMessage(out, '?쒖떆???뚯감媛 ?놁뒿?덈떎.', 'text-muted small mb-0');
      return;
    }
    pageData.content.forEach((wn) => {
      const row = document.createElement('div');
      row.className = 'kraft-list-row';
      const r = document.createElement('span');
      r.className = 'round';
      r.textContent = `${wn.round}??;
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
    info.textContent = `${cur} / ${listState.totalPages} ?섏씠吏 쨌 珥?${fmtNum(listState.totalElements)}?뚯감`;
    prev.disabled = listState.page <= 0;
    next.disabled = listState.totalPages === 0 || listState.page >= listState.totalPages - 1;
  };

  const loadList = async () => {
    // ?댁쟾 吏꾪뻾 以묒씤 ?붿껌???덉쑝硫?痍⑥냼 (鍮좊Ⅸ ?섏씠吏 ?꾪솚 寃쎌웳 議곌굔 諛⑹?)
    if (listState.abortCtrl) listState.abortCtrl.abort();
    listState.abortCtrl = new AbortController();
    const { signal } = listState.abortCtrl;

    const out = document.getElementById('list-result');
    out.innerHTML = skeleton();
    try {
      const data = await api(
        `/api/winning-numbers?page=${listState.page}&size=${listState.size}`,
        { signal }
      );
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

  // ????????????????????????? ?뱀꺼踰덊샇 ?섏쭛 ?몃━嫄??????????????????????????
  const onCollectRefresh = async (e) => {
    e.preventDefault();
    const clog = Logger.step('collect-refresh');
    const fd = new FormData(e.currentTarget);
    const targetRoundRaw = String(fd.get('targetRound') || '').trim();
    const targetRound = targetRoundRaw === '' ? null : targetRoundRaw;
    const adminToken = String(fd.get('adminToken') || '').trim();
    const out = document.getElementById('collect-result');
    const btn = e.currentTarget.querySelector('[type="submit"]');

    if (!adminToken) {
      out.textContent = '愿由ъ옄 ?좏겙???낅젰??二쇱꽭??';
      out.className = 'small mt-2 text-danger';
      return;
    }

    out.textContent = '?섏쭛 ?붿껌 以묅?;
    out.className = 'small mt-2 text-muted';
    clog.info('?섏쭛 ?붿껌', { targetRound });

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
        out.textContent = `?섏쭛 ?꾨즺 쨌 ?좉퇋 ${data.collected} 쨌 ?ㅽ궢 ${data.skipped} 쨌 ?ㅽ뙣 ${data.failed} 쨌 理쒖떊 ${data.latestRound}??;
        out.className = 'small mt-2 text-success';
        toast(`?섏쭛 ?꾨즺: ?좉퇋 ${data.collected} 쨌 理쒖떊 ${data.latestRound}??);
        clog.info('?섏쭛 ?깃났', { collected: data.collected, latestRound: data.latestRound });
        listState.page = 0;
        loadLatest();
        loadList();
        loadFrequency();
      } catch (err) {
        if (err.name === 'TypeError') {
          out.textContent = `?ㅽ듃?뚰겕 ?ㅻ쪟: ${err.message}`;
        } else {
          out.textContent = `?ㅽ뙣: ${err.message}`;
        }
        out.className = 'small mt-2 text-danger';
        clog.warn('?섏쭛 ?ㅽ뙣', { code: err.code, message: err.message });
        toast(`?섏쭛 ?ㅽ뙣: ${err.message}`, true);
      }
    });
  };

  // ????????????????????????? 鍮덈룄 ?????????????????????????
  const loadFrequency = async () => {
    const out = document.getElementById('freq-result');
    const lowOut = document.getElementById('freq-low6-result');
    out.innerHTML =
      '<div class="placeholder-glow w-100"><span class="placeholder col-12"></span></div>';
    if (lowOut) {
      lowOut.innerHTML =
        '<div class="placeholder-glow w-100"><span class="placeholder col-8"></span></div>';
    }
    try {
      const data = await api('/api/winning-numbers/stats/frequency');
      const max = data.reduce((m, d) => Math.max(m, d.count), 1);
      const lowSixList = [...data]
        .sort((a, b) => a.count - b.count || a.number - b.number)
        .slice(0, 6);
      const lowSix = new Set(
        lowSixList.map((d) => d.number)
      );
      const lowSixNumbers = lowSixList.map((d) => d.number).sort((a, b) => a - b);
      out.replaceChildren();
      data.forEach(({ number, count }) => {
        const cell = document.createElement('div');
        cell.className = 'kraft-freq-cell';
        if (lowSix.has(number)) {
          cell.classList.add('low-freq');
        }
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
        const params = new URLSearchParams();
        lowSixNumbers.forEach((n) => params.append('numbers', String(n)));
        const history = await api(`/api/winning-numbers/stats/combination-prize-history?${params.toString()}`);
        const summary = document.createElement('div');
        summary.className = 'small';
        const combo = history.numbers.join(', ');
        const firstRounds = history.firstPrizeHits.map((h) => `${h.round}회`).join(', ') || '없음';
        const secondRounds = history.secondPrizeHits.map((h) => `${h.round}회`).join(', ') || '없음';
        summary.innerHTML =
          `<strong>조합 ${combo}</strong> · 1등 ${history.firstPrizeCount}회 (${firstRounds}) · 2등 ${history.secondPrizeCount}회 (${secondRounds})`;
        lowOut.appendChild(summary);
      }
    } catch (err) {
      setTextMessage(out, err.message, 'text-danger small mb-0');
      if (lowOut) {
        setTextMessage(lowOut, err.message, 'text-danger small mb-0');
      }
    }
  };

  // ????????????????????????? Bootstrap ?????????????????????????
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
