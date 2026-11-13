// Cliente REST mínimo para Camunda 7 (Node 18+, sin dependencias).
const base = (process.env.CAMUNDA_URL || 'http://localhost:8080/engine-rest').replace(/\/$/, '');
const user = process.env.CAMUNDA_USER, password = process.env.CAMUNDA_PASSWORD;
const auth = user ? { Authorization: 'Basic ' + Buffer.from(user + ':' + password).toString('base64') } : {};
async function http(method, path, body, raw) {
  const init = { method, headers: { ...auth }, signal: AbortSignal.timeout(20000) };
  if (raw) init.body = raw;
  else if (body !== undefined) { init.headers['Content-Type'] = 'application/json'; init.body = JSON.stringify(body); }
  const r = await fetch(base + path, init);
  if (!r.ok) throw new Error(`HTTP ${r.status} ${path}: ${await r.text()}`);
  return r.status === 204 ? null : r.json();
}
module.exports = { base, http, get: p => http('GET', p), post: (p, b) => http('POST', p, b) };
