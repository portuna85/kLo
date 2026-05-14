// @ts-check

const numberFormatter = new Intl.NumberFormat('ko-KR');

/** @param {number | string | null | undefined} n */
export const fmtNum = (n) => numberFormatter.format(Number(n ?? 0));

export function createSkeleton(widthClass = 'col-7') {
  const wrap = document.createElement('div');
  wrap.className = 'placeholder-glow';
  const placeholder = document.createElement('span');
  placeholder.className = `placeholder ${widthClass}`;
  wrap.appendChild(placeholder);
  return wrap;
}

/** @param {Element} container */
export function showSkeleton(container, widthClass = 'col-7') {
  container.setAttribute('aria-busy', 'true');
  container.replaceChildren(createSkeleton(widthClass));
}

/** @param {Element} container */
export function setTextMessage(container, text, className = 'small mb-0') {
  container.setAttribute('aria-busy', 'false');
  container.replaceChildren();
  const p = document.createElement('p');
  p.className = className;
  p.textContent = text;
  container.appendChild(p);
}

/** @param {Element | null} container */
export function setBusy(container, busy) {
  if (!container) return;
  container.setAttribute('aria-busy', busy ? 'true' : 'false');
}

/** @template T @param {Element | null} btn @param {() => Promise<T>} fn */
export async function withLoading(btn, fn) {
  if (!(btn instanceof HTMLElement)) return fn();
  const prev = btn.innerHTML;
  btn.setAttribute('aria-busy', 'true');
  btn.setAttribute('disabled', 'true');
  btn.setAttribute('aria-disabled', 'true');
  try {
    return await fn();
  } finally {
    btn.removeAttribute('disabled');
    btn.removeAttribute('aria-busy');
    btn.setAttribute('aria-disabled', 'false');
    btn.innerHTML = prev;
  }
}

/** @param {number} n */
const ballClass = (n) => (n <= 10 ? 'b1' : n <= 20 ? 'b2' : n <= 30 ? 'b3' : n <= 40 ? 'b4' : 'b5');

/** @param {number} n */
function ball(n, bonus = false) {
  const span = document.createElement('span');
  span.className = `kraft-ball ${ballClass(n)}${bonus ? ' bonus' : ''}`;
  span.textContent = String(n);
  span.setAttribute('aria-label', bonus ? `보너스 번호 ${n}` : `번호 ${n}`);
  return span;
}

/** @param {number[]} numbers */
export function ballsRow(numbers, bonus) {
  const wrap = document.createElement('div');
  wrap.className = 'kraft-balls';
  const label = bonus == null
    ? `번호 조합 ${numbers.join(', ')}`
    : `번호 조합 ${numbers.join(', ')}, 보너스 ${bonus}`;
  wrap.setAttribute('aria-label', label);
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

/** @param {{round:number,drawDate:string,numbers:number[],bonusNumber:number,firstPrize:number,firstWinners:number,totalSales:number}} wn */
export function renderWinning(wn, container) {
  container.setAttribute('aria-busy', 'false');
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
