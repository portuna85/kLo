// @ts-check

const ERROR_MESSAGES = {
  REQUEST_VALIDATION_ERROR: 'Please check your request input.',
  LOTTO_INVALID_COUNT: 'Recommendation count must be between 1 and 10.',
  LOTTO_INVALID_TARGET_ROUND: 'Please check the target round input.',
  UNAUTHORIZED_ADMIN_API: 'Admin token is invalid.',
  WINNING_NUMBER_NOT_FOUND: 'Winning number not found.',
  TOO_MANY_REQUESTS: 'Too many requests. Please try again later.',
  EXTERNAL_API_FAILURE: 'External lotto API call failed.',
  INTERNAL_SERVER_ERROR: 'Internal server error occurred.'
};

/** @param {number} status */
function messageFromStatus(status) {
  if (status === 400) return 'Please check your request input.';
  if (status === 401) return 'Admin token is invalid.';
  if (status === 404) return 'Winning number not found.';
  if (status === 429) return 'Too many requests. Please try again later.';
  if (status === 502) return 'External lotto API call failed.';
  return `Request failed (HTTP ${status})`;
}

/**
 * @template T
 * @param {string} url
 * @param {RequestInit} [init]
 * @returns {Promise<T>}
 */
export async function api(url, init = {}) {
  const ctrl = new AbortController();
  const tid = setTimeout(() => ctrl.abort(), 10_000);
  try {
    const res = await fetch(url, {
      ...init,
      signal: init.signal ?? ctrl.signal,
      headers: { Accept: 'application/json', ...(init.headers ?? {}) }
    });
    let body = null;
    try {
      body = await res.json();
    } catch (_) {
      body = null;
    }
    if (!body || typeof body.success !== 'boolean') {
      throw new Error(`Invalid response body (HTTP ${res.status})`);
    }
    if (!body.success) {
      const err = body.error ?? { code: 'UNKNOWN', message: '' };
      // @ts-ignore
      const mapped = ERROR_MESSAGES[err.code] || err.message || messageFromStatus(res.status);
      const e = new Error(mapped);
      // @ts-ignore
      e.code = err.code;
      // @ts-ignore
      e.status = res.status;
      throw e;
    }
    return body.data;
  } catch (err) {
    if (err && typeof err === 'object' && 'name' in err && err.name === 'AbortError') {
      throw new Error('Request timed out.');
    }
    if (err instanceof TypeError) {
      throw new Error('Please check your network connection.');
    }
    throw err;
  } finally {
    clearTimeout(tid);
  }
}
