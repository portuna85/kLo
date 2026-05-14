// @ts-check

import { api } from './api.js';
import { renderWinning, setTextMessage, showSkeleton, withLoading } from './ui.js';

export async function loadLatest() {
  const out = document.getElementById('latest-result');
  if (!out) return;
  try {
    const data = await api('/api/winning-numbers/latest');
    renderWinning(data, out);
  } catch (err) {
    setTextMessage(out, /** @type {Error} */ (err).message, 'text-danger small mb-0');
  }
}

export function bindByRoundForm() {
  document.getElementById('form-by-round')?.addEventListener('submit', onByRound);
}

/** @param {SubmitEvent} e */
async function onByRound(e) {
  e.preventDefault();
  const form = /** @type {HTMLFormElement} */ (e.currentTarget);
  const fd = new FormData(form);
  const round = Number(fd.get('round'));
  const out = document.getElementById('round-result');
  if (!out) return;

  if (!Number.isInteger(round) || round < 1) {
    setTextMessage(out, 'Please enter a valid round number (>= 1).', 'text-danger small mb-0');
    return;
  }

  const btn = form.querySelector('[type="submit"]');
  showSkeleton(out);

  await withLoading(btn, async () => {
    try {
      const data = await api(`/api/winning-numbers/${round}`);
      renderWinning(data, out);
    } catch (err) {
      setTextMessage(out, /** @type {Error} */ (err).message, 'text-danger small mb-0');
    }
  });
}
