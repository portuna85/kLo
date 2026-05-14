// @ts-check

import { api } from './api.js';
import { renderWinning, setBusy, setTextMessage, showSkeleton, withLoading } from './ui.js';

export async function loadLatest() {
  const out = document.getElementById('latest-result');
  if (!out) return;
  setBusy(out, true);
  try {
    const data = await api('/api/v1/winning-numbers/latest');
    renderWinning(data, out);
  } catch (err) {
    setTextMessage(out, /** @type {Error} */ (err).message, 'text-danger small mb-0');
  }
}

export function bindByRoundForm(root = document) {
  root.getElementById('form-by-round')?.addEventListener('submit', onByRound);
}

export function mountWinning(root = document) {
  bindByRoundForm(root);
  void loadLatest();
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
    setTextMessage(out, '유효한 회차 번호를 입력해 주세요. (1 이상)', 'text-danger small mb-0');
    return;
  }

  const btn = form.querySelector('[type="submit"]');
  showSkeleton(out);

  await withLoading(btn, async () => {
    try {
      const data = await api(`/api/v1/winning-numbers/${round}`);
      renderWinning(data, out);
      setBusy(out, false);
    } catch (err) {
      setTextMessage(out, /** @type {Error} */ (err).message, 'text-danger small mb-0');
    }
  });
}
