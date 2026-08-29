(function () {
  'use strict';

  const BASE = '/api';
  const TOKEN_KEY = 'dm_token';
  const ME_KEY = 'dm_me';

  // ── Token / session storage ──────────────────────────────────

  function getToken() {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  function setToken(t) {
    sessionStorage.setItem(TOKEN_KEY, t);
  }

  function clearSession() {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(ME_KEY);
  }

  function getMe() {
    try { return JSON.parse(sessionStorage.getItem(ME_KEY) || 'null'); }
    catch { return null; }
  }

  function setMe(data) {
    sessionStorage.setItem(ME_KEY, JSON.stringify(data));
  }

  function requireAuth() {
    if (!getToken()) {
      location.href = 'sign-in.html';
      throw new Error('unauthenticated');
    }
  }

  // ── Core fetch wrapper ───────────────────────────────────────

  async function request(path, options) {
    const token = getToken();
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    if (token) headers['Authorization'] = 'Bearer ' + token;

    let res;
    try {
      res = await fetch(BASE + path, Object.assign({}, options, { headers }));
    } catch (networkErr) {
      throw new Error('Network error — check your connection.');
    }

    if (res.status === 401) {
      clearSession();
      location.href = 'sign-in.html';
      throw new Error('Session expired.');
    }

    if (res.status === 403) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.message || 'Access denied.');
    }

    if (res.status === 204 || res.headers.get('content-length') === '0') {
      return null;
    }

    const payload = await res.json().catch(() => null);

    if (!res.ok) {
      throw new Error((payload && payload.message) || 'HTTP ' + res.status);
    }

    return payload;
  }

  // ── Multipart file upload ────────────────────────────────────

  async function upload(path, formData) {
    const token = getToken();
    const headers = {};
    if (token) headers['Authorization'] = 'Bearer ' + token;
    // Do NOT set Content-Type — browser must set the multipart boundary itself

    let res;
    try {
      res = await fetch(BASE + path, { method: 'POST', headers, body: formData });
    } catch (networkErr) {
      throw new Error('Network error — check your connection.');
    }

    if (res.status === 401) {
      clearSession();
      location.href = 'sign-in.html';
      throw new Error('Session expired.');
    }

    const payload = await res.json().catch(() => null);
    if (!res.ok) throw new Error((payload && payload.message) || 'Upload failed.');
    return payload;
  }

  async function downloadBlob(path) {
    const token = getToken();
    const headers = {};
    if (token) headers['Authorization'] = 'Bearer ' + token;

    let res;
    try {
      res = await fetch(BASE + path, { headers });
    } catch (networkErr) {
      throw new Error('Network error — check your connection.');
    }

    if (res.status === 401) {
      clearSession();
      location.href = 'sign-in.html';
      throw new Error('Session expired.');
    }
    if (!res.ok) throw new Error('Could not load file (HTTP ' + res.status + ').');

    return res.blob();
  }

  // ── Public surface ───────────────────────────────────────────

  window.api = {
    get:    (path)        => request(path, { method: 'GET' }),
    post:   (path, body)  => request(path, { method: 'POST',  body: JSON.stringify(body) }),
    put:    (path, body)  => request(path, { method: 'PUT',   body: JSON.stringify(body) }),
    delete: (path)        => request(path, { method: 'DELETE' }),
    upload,
    downloadBlob,
    getToken,
    setToken,
    clearSession,
    getMe,
    setMe,
    requireAuth
  };
})();