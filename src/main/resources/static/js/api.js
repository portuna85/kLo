// @ts-check

const ERROR_MESSAGES = {
  REQUEST_VALIDATION_ERROR: '요청 값을 확인해 주세요.',
  LOTTO_INVALID_COUNT: '추천 개수는 1~10 사이여야 합니다.',
  LOTTO_INVALID_TARGET_ROUND: '대상 회차 값을 확인해 주세요.',
  UNAUTHORIZED_ADMIN_API: '관리자 토큰이 유효하지 않습니다.',
  WINNING_NUMBER_NOT_FOUND: '당첨 번호를 찾을 수 없습니다.',
  TOO_MANY_REQUESTS: '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.',
  EXTERNAL_API_FAILURE: '외부 로또 API 호출에 실패했습니다.',
  INTERNAL_SERVER_ERROR: '내부 서버 오류가 발생했습니다.'
};

/** @param {number} status */
function messageFromStatus(status) {
  if (status === 400) return '요청 값을 확인해 주세요.';
  if (status === 401) return '관리자 토큰이 유효하지 않습니다.';
  if (status === 404) return '당첨 번호를 찾을 수 없습니다.';
  if (status === 429) return '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.';
  if (status === 502) return '외부 로또 API 호출에 실패했습니다.';
  return `요청에 실패했습니다. (HTTP ${status})`;
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
      throw new Error(`응답 형식이 올바르지 않습니다. (HTTP ${res.status})`);
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
      throw new Error('요청 시간이 초과되었습니다.');
    }
    if (err instanceof TypeError) {
      throw new Error('네트워크 연결을 확인해 주세요.');
    }
    throw err;
  } finally {
    clearTimeout(tid);
  }
}
