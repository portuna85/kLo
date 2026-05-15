// @ts-check

import { api } from './api.js';
import { ballsRow, setBusy, setTextMessage, showSkeleton, withLoading } from './ui.js';

export function bindRecommendForm(root = document) {
  const form = root.getElementById('form-recommend');
  if (!form) return;
  form.addEventListener('submit', onRecommend);
  const countInput = form.querySelector('#rec-count');
  countInput?.addEventListener('input', () => validateRecommendCount(form));
}

export function mountRecommend(root = document) {
  bindRecommendForm(root);
}

/** @param {SubmitEvent} e */
async function onRecommend(e) {
  e.preventDefault();
  const form = /** @type {HTMLFormElement} */ (e.currentTarget);
  if (!validateRecommendCount(form)) return;
  const fd = new FormData(form);
  const count = Number(fd.get('count') || 5);
  const out = document.getElementById('recommend-result');
  const btn = form.querySelector('[type="submit"]');
  if (!out) return;
  showSkeleton(out);

  await withLoading(btn, async () => {
    try {
      const data = await api('/api/v1/recommend', {
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
      setBusy(out, false);
    } catch (err) {
      setTextMessage(out, /** @type {Error} */ (err).message, 'text-danger small mb-0');
    }
  });
}

/** @param {HTMLFormElement} form */
function validateRecommendCount(form) {
  const input = /** @type {HTMLInputElement|null} */ (form.querySelector('#rec-count'));
  const feedback = form.querySelector('#rec-count-feedback');
  if (!input || !feedback) return true;

  const raw = input.value?.trim() ?? '';
  const count = Number(raw);
  const valid = Number.isInteger(count) && count >= 1 && count <= 10;

  input.setAttribute('aria-invalid', valid ? 'false' : 'true');
  input.classList.toggle('is-invalid', !valid);
  feedback.textContent = valid ? '' : '1~10 사이 정수를 입력해 주세요.';
  return valid;
}
