// @ts-check

const THEME_KEY = 'kraft-theme';

/** @param {'light'|'dark'} theme */
export function setTheme(theme) {
  document.documentElement.setAttribute('data-bs-theme', theme);
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch (_) {
    // no-op
  }
  const icon = document.querySelector('#themeToggle i');
  if (icon) {
    icon.className = theme === 'dark' ? 'bi bi-sun' : 'bi bi-moon-stars';
  }
}

export function initTheme() {
  let saved = null;
  try {
    saved = localStorage.getItem(THEME_KEY);
  } catch (_) {
    // no-op
  }
  const systemDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches;
  setTheme(/** @type {'light'|'dark'} */ (saved ?? (systemDark ? 'dark' : 'light')));
}

export function bindThemeToggle() {
  document.getElementById('themeToggle')?.addEventListener('click', () => {
    const cur = document.documentElement.getAttribute('data-bs-theme');
    setTheme(cur === 'dark' ? 'light' : 'dark');
  });
}
