import { api } from './api.js';
import { renderWinning, setTextMessage, showSkeleton, withLoading } from './ui.js';

export async function loadLatest() {
  const out = document.getElementById('latest-result');
  try {
    const data = await api('/api/winning-numbers/latest');
    renderWinning(data, out);
  } catch (err) {
    setTextMessage(out, err.message, 'text-danger small mb-0');
  }
}

export function bindByRoundForm() {
  document.getElementById('form-by-round')?.addEventListener('submit', onByRound);
}

async function onByRound(e) {
  e.preventDefault();
  const fd = new FormData(e.currentTarget);
  const round = Number(fd.get('round'));
  const out = document.getElementById('round-result');

  if (!Number.isInteger(round) || round < 1) {
    setTextMessage(out, '1 이상의 정수 회차를 입력해 주세요.', 'text-danger small mb-0');
    return;
  }

  const btn = e.currentTarget.querySelector('[type="submit"]');
  showSkeleton(out);

  await withLoading(btn, async () => {
    try {
      const data = await api(`/api/winning-numbers/${round}`);
      renderWinning(data, out);
    } catch (err) {
      setTextMessage(out, err.message, 'text-danger small mb-0');
    }
  });
}
