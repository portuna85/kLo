import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { api } from '../../main/resources/static/js/api.js';

describe('api', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('returns body.data on successful response', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 200,
      json: async () => ({ success: true, data: { value: 1 } })
    });

    const result = await api('/ok');

    expect(result).toEqual({ value: 1 });
  });

  it('maps known API error code message', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 400,
      json: async () => ({ success: false, error: { code: 'LOTTO_INVALID_COUNT', message: 'x' } })
    });

    await expect(api('/bad')).rejects.toThrow('between 1 and 10');
  });

  it('throws network message on TypeError', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('network'));

    await expect(api('/net')).rejects.toThrow();
  });
});
