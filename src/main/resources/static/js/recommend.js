// @ts-check

import { api } from './api.js';
import { ballsRow, setTextMessage, showSkeleton, withLoading } from './ui.js';

export function bindRecommendForm() {
  document.getElementById('form-recommend')?.addEventListener('submit', onRecommend);
}

/** @param {SubmitEvent} e */
async function onRecommend(e) {
  e.preventDefault();
  const form = /** @type {HTMLFormElement} */ (e.currentTarget);
  const fd = new FormData(form);
  const count = Number(fd.get('count') || 5);
  const out = document.getElementById('recommend-result');
  const btn = form.querySelector('[type="submit"]');
  if (!out) return;
  showSkeleton(out);

  await withLoading(btn, async () => {
    try {
      const data = await api('/api/recommend', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ count })
      });
      const fragment = document.createDocumentFragment();
      data.combinations.forEach((c, i) => {
        const row = document.createElement('div');
        row.className = 'kraft-combo';
        const idx = document.createElement('span');
        idx.className = 'idx';
        idx.textContent = `#${i + 1}`;
        row.appendChild(idx);
        row.appendChild(ballsRow(c.numbers));
        fragment.appendChild(row);
      });
      out.replaceChildren(fragment);
    } catch (err) {
      setTextMessage(out, /** @type {Error} */ (err).message, 'text-danger small mb-0');
    }
  });
}
