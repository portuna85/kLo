import { bindListControls, loadList } from './pagination.js';
import { bindRecommendForm } from './recommend.js';
import { bindThemeToggle, initTheme } from './theme.js';
import { loadFrequency } from './frequency.js';
import { bindByRoundForm, loadLatest } from './winning.js';

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  bindThemeToggle();
  bindRecommendForm();
  bindByRoundForm();
  bindListControls();
  loadLatest();
  loadFrequency();
  loadList();
});
