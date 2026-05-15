(function () {
  try {
    var t = localStorage.getItem('kraft-theme');
    var theme = t || (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    document.documentElement.setAttribute('data-bs-theme', theme);
  } catch (_) {
    document.documentElement.setAttribute('data-bs-theme', 'light');
  }
})();
