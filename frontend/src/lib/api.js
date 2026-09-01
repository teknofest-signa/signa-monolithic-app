/**
 * Transport for the producer service.
 *
 * Two credentials travel through here and they are kept apart on purpose.
 * The operator's JWT identifies a back-office user. The member bank's API key
 * identifies an institution and is the only thing that opens the OPRF endpoint.
 * They are never sent on the same request: an evaluation attributed to a person
 * rather than an institution could be neither rate limited nor audited, which
 * is what keeps the OPRF from becoming a lookup service.
 */

const BASE = import.meta.env.VITE_API_BASE_URL ?? '';

const TOKEN_KEY = 'signa.token';
/** Session storage, not local: the bank key dies with the tab, by design. */
const BANK_KEY = 'signa.bank';

export class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

export const token = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (value) => localStorage.setItem(TOKEN_KEY, value),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export const bankCredential = {
  get() {
    try {
      const raw = sessionStorage.getItem(BANK_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  },
  set(value) {
    sessionStorage.setItem(BANK_KEY, JSON.stringify(value));
  },
  clear: () => sessionStorage.removeItem(BANK_KEY),
};

/**
 * The backend answers errors with { message, status, statusCode, path } and
 * sometimes a per-field `errors` map. Flatten that into something a form can
 * show without every caller re-deriving it.
 */
function describe(body, status) {
  if (body && typeof body === 'object') {
    if (body.errors && Object.keys(body.errors).length > 0) {
      return Object.values(body.errors).join(' ');
    }
    if (body.message) return body.message;
  }
  if (status === 401) return 'Your session has expired. Sign in again.';
  if (status === 403) return 'You do not have access to that.';
  if (status === 0) return 'The producer service is not reachable.';
  return `Request failed (${status}).`;
}

async function request(path, { method = 'GET', body, headers = {}, auth = 'operator', raw = false } = {}) {
  const finalHeaders = { ...headers };

  if (auth === 'operator') {
    const jwt = token.get();
    if (jwt) finalHeaders.Authorization = `Bearer ${jwt}`;
  } else if (auth === 'bank') {
    const credential = bankCredential.get();
    if (!credential) throw new ApiError('No member bank credential in this session.', 401, null);
    finalHeaders['X-Signa-Client-Id'] = credential.clientId;
    finalHeaders['X-Signa-Api-Key'] = credential.apiKey;
  }

  let payload = body;
  if (body !== undefined && !(body instanceof FormData)) {
    finalHeaders['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(`${BASE}${path}`, { method, headers: finalHeaders, body: payload });
  } catch {
    throw new ApiError('The producer service is not reachable.', 0, null);
  }

  if (response.status === 204 || response.headers.get('content-length') === '0') {
    if (!response.ok) throw new ApiError(describe(null, response.status), response.status, null);
    return null;
  }

  const text = await response.text();
  let parsed = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }

  if (!response.ok) {
    // A rejected operator token means the session is over. Clearing it here
    // keeps every screen from having to handle that case itself.
    if (response.status === 401 && auth === 'operator') token.clear();
    throw new ApiError(describe(parsed, response.status), response.status, parsed);
  }

  return raw ? { data: parsed, response } : parsed;
}

const get = (path) => request(path);
const post = (path, body) => request(path, { method: 'POST', body });
const put = (path, body) => request(path, { method: 'PUT', body });
const del = (path) => request(path, { method: 'DELETE' });

export const api = {
  auth: {
    signIn: (email, password) => post('/api/v1/auth/login', { email, password }),
    forgotPassword: (email) => post('/api/v1/auth/forgot-password', { email }),
    resetPassword: (payload) => post('/api/v1/auth/reset-password', payload),
  },

  me: {
    profile: () => get('/api/v1/backoffice'),
    transactions: (page = 0, size = 10) => get(`/api/v1/backoffice/transactions?page=${page}&size=${size}`),
  },

  customers: {
    list: (page = 0, size = 20) => get(`/api/v1/customers?page=${page}&size=${size}`),
    enrol: (payload) => post('/api/v1/customers', payload),
    block: (id) => put(`/api/v1/customers/${id}`),
  },

  banks: {
    list: () => get('/api/v1/banks'),
    create: (name) => post('/api/v1/banks', { name }),
    remove: (id) => del(`/api/v1/banks/${id}`),
    rotateKey: (id) => post(`/api/v1/banks/${id}/rotate-api-key`),
    setOprfAccess: (id, enabled) => put(`/api/v1/banks/${id}/oprf-access?enabled=${enabled}`),
  },

  admins: {
    list: () => get('/api/v1/super-admins/admins'),
    invite: (payload) => post('/api/v1/super-admins/admins', payload),
    update: (id, payload) => put(`/api/v1/super-admins/admins/${id}`, payload),
    remove: (id) => del(`/api/v1/super-admins/admins/${id}`),
  },

  simulation: {
    run: (payload) => post('/api/v1/simulation/transactions', payload),
    history: (page = 0, size = 20) => get(`/api/v1/simulation/transactions?page=${page}&size=${size}`),
  },

  oprf: {
    /** Public. Also the fastest way to tell whether the service is up. */
    publicKey: () => request('/api/v1/oprf/public-key', { auth: 'none' }),

    /** Member-bank credential only. Never the operator's JWT. */
    evaluate: (blindedElements, keyId) => request('/api/v1/oprf/evaluate', {
      method: 'POST',
      auth: 'bank',
      body: keyId ? { blindedElements, keyId } : { blindedElements },
    }),

    /** Present only when the server has the development client switched on. */
    localDerive: (identifierType, identifier) =>
      post('/api/v1/oprf/local/derive', { identifierType, identifier }),
  },

  screening: {
    /**
     * The second call of the pair. Member-bank credential, like evaluation:
     * this returns a fact about a person, so it has to be attributable to an
     * institution and countable against that institution's quota.
     *
     * It takes the pseudonym rather than the identifier because it has to.
     * The server cannot strip the blind from an evaluation, so it never holds
     * a pseudonym of its own to match on; only the caller, after unblinding,
     * has one to send.
     */
    check: (pseudonym, oprfKeyId) => request('/api/v1/screening', {
      method: 'POST',
      auth: 'bank',
      body: { pseudonym, oprfKeyId },
    }),
  },
};
