// @ts-check

import { mountPagination } from './pagination.js';
import { mountRecommend } from './recommend.js';
import { bindThemeToggle, initTheme } from './theme.js';
import { mountFrequency } from './frequency.js';
import { mountWinning } from './winning.js';

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  bindThemeToggle();
  mountRecommend(document);
  mountWinning(document);
  mountFrequency();
  mountPagination(document);
});
