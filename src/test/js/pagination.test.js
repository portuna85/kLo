import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMock = vi.fn();
const setTextMessageMock = vi.fn();
const showSkeletonMock = vi.fn();
const ballsRowMock = vi.fn(() => document.createElement('div'));

vi.mock('../../main/resources/static/js/api.js', () => ({
  api: apiMock
}));

vi.mock('../../main/resources/static/js/ui.js', () => ({
  ballsRow: ballsRowMock,
  fmtNum: (n) => String(n),
  setTextMessage: setTextMessageMock,
  showSkeleton: showSkeletonMock
}));

describe('pagination', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="list-result"></div>
      <div id="list-pageinfo"></div>
      <button id="list-prev"></button>
      <button id="list-next"></button>
      <select id="list-size"><option value="20">20</option><option value="50">50</option></select>
    `;
    vi.clearAllMocks();
  });

  it('loads and renders list page info', async () => {
    apiMock.mockResolvedValue({
      content: [{ round: 1, drawDate: '2026-05-01', numbers: [1, 2, 3, 4, 5, 6], bonusNumber: 7 }],
      totalPages: 3,
      totalElements: 41
    });

    const mod = await import('../../main/resources/static/js/pagination.js');
    await mod.loadList();

    expect(apiMock).toHaveBeenCalled();
    expect(document.getElementById('list-pageinfo').textContent).toContain('3');
    expect(showSkeletonMock).toHaveBeenCalled();
  });

  it('shows error text when api fails', async () => {
    apiMock.mockRejectedValue(new Error('boom'));

    const mod = await import('../../main/resources/static/js/pagination.js');
    await mod.loadList();

    expect(setTextMessageMock).toHaveBeenCalled();
  });
});
