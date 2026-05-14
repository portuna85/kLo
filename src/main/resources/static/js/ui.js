const numberFormatter = new Intl.NumberFormat('ko-KR');

export const fmtNum = (n) => numberFormatter.format(Number(n ?? 0));

export function createSkeleton(widthClass = 'col-7') {
  const wrap = document.createElement('div');
  wrap.className = 'placeholder-glow';
  const placeholder = document.createElement('span');
  placeholder.className = `placeholder ${widthClass}`;
  wrap.appendChild(placeholder);
  return wrap;
}

export function showSkeleton(container, widthClass = 'col-7') {
  container.replaceChildren(createSkeleton(widthClass));
}

export function setTextMessage(container, text, className = 'small mb-0') {
  container.replaceChildren();
  const p = document.createElement('p');
  p.className = className;
  p.textContent = text;
  container.appendChild(p);
}

export async function withLoading(btn, fn) {
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
}

const ballClass = (n) => (n <= 10 ? 'b1' : n <= 20 ? 'b2' : n <= 30 ? 'b3' : n <= 40 ? 'b4' : 'b5');

function ball(n, bonus = false) {
  const span = document.createElement('span');
  span.className = `kraft-ball ${ballClass(n)}${bonus ? ' bonus' : ''}`;
  span.textContent = String(n);
  return span;
}

export function ballsRow(numbers, bonus) {
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
}

export function renderWinning(wn, container) {
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
}
