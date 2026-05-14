import { api } from './api.js';
import { setTextMessage, showSkeleton } from './ui.js';

export async function loadFrequency() {
  const out = document.getElementById('freq-result');
  const lowOut = document.getElementById('freq-low6-result');
  showSkeleton(out, 'col-12');
  if (lowOut) showSkeleton(lowOut, 'col-8');
  try {
    const summaryData = await api('/api/winning-numbers/stats/frequency-summary');
    const data = summaryData.frequencies;
    const max = data.reduce((m, d) => Math.max(m, d.count), 1);
    const lowSixList = [...data].sort((a, b) => a.count - b.count || a.number - b.number).slice(0, 6);
    const lowSix = new Set(lowSixList.map((d) => d.number));

    const fragment = document.createDocumentFragment();
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
      fragment.appendChild(cell);
    });
    out.replaceChildren(fragment);

    if (lowOut) {
      lowOut.replaceChildren();
      const history = summaryData.lowSixCombinationHistory;
      const summary = document.createElement('div');
      summary.className = 'small';
      const strong = document.createElement('strong');
      strong.textContent = `조합 ${history.numbers.join(', ')}`;
      const firstRounds = history.firstPrizeHits.map((h) => `${h.round}회`).join(', ') || '없음';
      const secondRounds = history.secondPrizeHits.map((h) => `${h.round}회`).join(', ') || '없음';
      summary.append(strong, ` · 1등 ${history.firstPrizeCount}회 (${firstRounds}) · 2등 ${history.secondPrizeCount}회 (${secondRounds})`);
      lowOut.appendChild(summary);
    }
  } catch (err) {
    setTextMessage(out, err.message, 'text-danger small mb-0');
    if (lowOut) setTextMessage(lowOut, err.message, 'text-danger small mb-0');
  }
}
