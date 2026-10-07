/* Browser E2E for the CERBERUS dashboard — drives web/static/app.js in
   headless Chrome over the DevTools protocol (no puppeteer dependency).
   Run: node e2e_dashboard.mjs
   Env: API_BASE=http://127.0.0.1:8006 (defaults to Python backend :8005)
   29 checks: boot, fuzzy search, BFS/DFS traverse, reports, mutations,
   panel visibility, paper-viewer open/back, API-base resolution, error
   surfacing, zero uncaught page exceptions. */
import { spawn } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const CHROME = process.env.CHROME || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const API = process.env.API_BASE || 'http://127.0.0.1:8005';
const DASH = API.endsWith('/') ? API : API + '/';
const CUSTOM_BASE = Boolean(process.env.API_BASE);
const PORT = 9333;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let passed = 0;
let failed = 0;
function check(name, ok, detail = '') {
  if (ok) { passed++; console.log(`  PASS  ${name}`); }
  else { failed++; console.log(`  FAIL  ${name} ${detail}`); }
}

/* ---------------- chrome + CDP plumbing ---------------- */
const profile = mkdtempSync(join(tmpdir(), 'cerberus-e2e-'));
const chrome = spawn(CHROME, [
  '--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  '--no-first-run', '--no-default-browser-check', '--disable-gpu', '--window-size=1400,1000',
  'about:blank',
], { stdio: 'ignore', windowsHide: true });

let ws;
let nextId = 0;
const pending = new Map();
const pageErrors = [];

function connect(url) {
  return new Promise((resolve, reject) => {
    ws = new WebSocket(url);
    ws.onopen = () => resolve();
    ws.onerror = (e) => reject(new Error('ws error: ' + e.message));
    ws.onmessage = (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.id && pending.has(msg.id)) {
        const { resolve: res, reject: rej } = pending.get(msg.id);
        pending.delete(msg.id);
        msg.error ? rej(new Error(JSON.stringify(msg.error))) : res(msg.result);
      } else if (msg.method === 'Runtime.exceptionThrown') {
        pageErrors.push(msg.params.exceptionDetails.exception?.description || msg.params.exceptionDetails.text);
      } else if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
        pageErrors.push(msg.params.args.map((a) => a.value || a.description || '').join(' '));
      }
    };
  });
}
function send(method, params = {}, sessionId) {
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    setTimeout(() => { if (pending.has(id)) { pending.delete(id); reject(new Error('timeout: ' + method)); } }, 15000);
  });
}
async function evalJs(expr) {
  const r = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true }, sessionId);
  if (r.exceptionDetails) throw new Error('eval exception: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text));
  return r.result.value;
}
async function waitFor(expr, label, timeout = 10000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    try { if (await evalJs(`Boolean(${expr})`)) return; } catch (_) { /* page still loading */ }
    await sleep(150);
  }
  throw new Error(`timeout waiting for: ${label}`);
}
let sessionId;

async function main() {
  /* wait for the devtools endpoint */
  let version;
  for (let i = 0; i < 40; i++) {
    try { version = await (await fetch(`http://127.0.0.1:${PORT}/json/version`)).json(); break; } catch (_) { await sleep(250); }
  }
  if (!version) throw new Error('chrome devtools never came up');
  await connect(version.webSocketDebuggerUrl);

  const { targetId } = await send('Target.createTarget', { url: DASH });
  const { sessionId: sid } = await send('Target.attachToTarget', { targetId, flatten: true });
  sessionId = sid;
  await send('Runtime.enable', {}, sessionId);
  await send('Page.enable', {}, sessionId);

  /* point the dashboard at the requested backend, then boot */
  if (CUSTOM_BASE) {
    await waitFor(`document.readyState === 'complete'`, 'first page load');
    await evalJs(`(() => { localStorage.setItem('cerberusApiBase', ${JSON.stringify(API)}); location.reload(); return true; })()`);
  }

  /* backend contract probe: Python has /api/stats, pure-Java ApiServer does not */
  const statsProbe = await fetch(API + '/api/stats').then((r) => r.ok).catch(() => false);
  const isJava = !statsProbe;
  // The mutation checks add papers and edges in memory, so reset the dataset
  // first: reruns stay deterministic against a warm backend. The pure-Java
  // surface has no reload route and already starts from a fresh graph.
  await fetch(API + '/api/reload', { method: 'POST' }).catch(() => {});
  const listPayload = await (await fetch(API + '/api/papers?limit=200')).json();
  const expectedPapers = (listPayload.results || []).length;
  console.log(`backend: ${API} (${isJava ? 'pure-Java ApiServer' : 'Python FastAPI'}) · ${expectedPapers} papers\n`);

  /* ---- 1. boot ---- */
  await waitFor(`document.readyState === 'complete' && document.querySelector('.query-console')`, 'dashboard boot + query console');
  const metricPapers = await evalJs(`document.querySelector('.metric__value').textContent.trim()`);
  check('boot: metrics show live paper count', metricPapers === String(expectedPapers), `(dom=${metricPapers} api=${expectedPapers})`);
  check('boot: status line reports papers/edges', (await evalJs(`document.querySelector('.window__status').textContent`)).includes(`${expectedPapers} papers`));
  await waitFor(`document.querySelector('.window__foot .ok').textContent.includes('engine ready')`, 'boot status flash');
  check('console: fuzzy checkbox present', await evalJs(`!!document.querySelector('.query-console input[name=fuzzy]')`));
  check('console: bfs/dfs mode select present', await evalJs(`[...document.querySelectorAll('.query-console select[name=mode] option')].map(o=>o.value).join(',') === 'bfs,dfs'`));

  /* ---- 2. BFS traversal runner ---- */
  await evalJs(`(() => {
    const f = document.querySelector('.query-console');
    f.elements.source.value = 'P101';
    f.elements.mode.value = 'bfs';
    f.requestSubmit(f.querySelector('[data-action=traverse]'));
    return true;
  })()`);
  await waitFor(`document.querySelector('.panel--traversal') && document.querySelector('.panel--traversal').textContent.includes('from P101')`, 'traversal panel rendered');
  const travText = await evalJs(`document.querySelector('.panel--traversal').textContent`);
  check('traverse: title shows BFS + source', travText.includes('Traversal') && travText.includes('BFS') && travText.includes('from P101'));
  check('traverse: visit order table has hop numbers', /1/.test(travText) && travText.includes('Attention Is All You Need'));
  check('traverse: status flash shows reached count', (await evalJs(`document.querySelector('.window__foot .ok').textContent`)).includes('papers reached'));

  /* ---- 3. DFS traversal ---- */
  await evalJs(`(() => {
    const f = document.querySelector('.query-console');
    f.elements.source.value = 'P101';
    f.elements.mode.value = 'dfs';
    f.requestSubmit(f.querySelector('[data-action=traverse]'));
    return true;
  })()`);
  await waitFor(`document.querySelector('.panel--traversal').textContent.includes('DFS')`, 'dfs traversal rendered');
  check('traverse: mode select switches to DFS', true);

  /* ---- 4. reports tab ---- */
  await evalJs(`document.querySelectorAll('.tabs > span')[3].click()`);
  await waitFor(`document.querySelector('.panel--reports') && document.querySelector('.panel--reports').textContent.includes('Top authors')`, 'reports panel rendered');
  const repText = await evalJs(`document.querySelector('.panel--reports').textContent`);
  check('reports: top authors table populated', repText.includes('Ashish Vaswani') || repText.includes('Vaswani'));
  check('reports: yearly trends table populated', /\d{4}/.test(repText) && repText.includes('Citation trends'));
  check('reports: traversal panel hidden on reports tab', await evalJs(`document.querySelector('.panel--traversal').hidden === true`));

  /* ---- 5. mutate tab: add paper ---- */
  await evalJs(`document.querySelectorAll('.tabs > span')[4].click()`);
  await waitFor(`!!document.querySelector('#add-paper-form') && !!document.querySelector('#add-citation-form')`, 'mutate forms rendered');
  await evalJs(`(() => {
    const f = document.querySelector('#add-paper-form');
    f.elements.id.value = 'E2E900';
    f.elements.title.value = 'End-to-End Verification Paper';
    f.elements.author.value = 'E2E Bot';
    f.elements.year.value = '2025';
    f.requestSubmit(f.querySelector('button'));
    return true;
  })()`);
  try {
    await waitFor(`document.querySelector('#mutate-log').textContent.includes('+ paper E2E900')`, 'add paper success log');
  } catch (e) {
    console.log('  DEBUG mutate-log:', await evalJs(`document.querySelector('#mutate-log')?.textContent || '(missing)'`).catch(() => '(n/a)'));
    console.log('  DEBUG status:', await evalJs(`document.querySelector('.window__foot .ok')?.textContent || '(missing)'`).catch(() => '(n/a)'));
    console.log('  DEBUG form values:', await evalJs(`JSON.stringify([...document.querySelector('#add-paper-form').elements].map(el=>[el.name,el.value]))`).catch(() => '(n/a)'));
    throw e;
  }
  check('mutate: paper added via POST /api/papers', true);
  check('mutate: graph tab picks up new paper count', String(expectedPapers + 1) === await evalJs(`document.querySelector('.metric__value').textContent.trim()`), `(expected ${expectedPapers + 1}, dom=${await evalJs(`document.querySelector('.metric__value').textContent.trim()`)})`);

  /* duplicate → 409 surfaced, not swallowed */
  await evalJs(`(() => {
    const f = document.querySelector('#add-paper-form');
    f.elements.id.value = 'E2E900';
    f.elements.title.value = 'Duplicate';
    f.elements.author.value = 'Bot';
    f.elements.year.value = '2025';
    f.requestSubmit(f.querySelector('button'));
    return true;
  })()`);
  await waitFor(`document.querySelector('#mutate-log').textContent.includes('! add paper E2E900 failed')`, 'duplicate paper error log');
  const errLine = await evalJs(`[...document.querySelectorAll('#mutate-log .mutate-log__line')].find(l=>l.textContent.includes('! add paper'))?.textContent || ''`);
  check('mutate: 409 duplicate error is visible', /409|already|exists/i.test(errLine), `(log: ${errLine})`);

  /* ---- 6. mutate tab: add citation edge ---- */
  await evalJs(`(() => {
    const f = document.querySelector('#add-citation-form');
    f.elements.citing.value = 'E2E900';
    f.elements.cited.value = 'P101';
    f.requestSubmit(f.querySelector('button'));
    return true;
  })()`);
  await waitFor(`document.querySelector('#mutate-log').textContent.includes('+ edge E2E900')`, 'add edge success log');
  check('mutate: edge added via POST /api/citations', true);

  /* duplicate edge → added:false path */
  await evalJs(`(() => {
    const f = document.querySelector('#add-citation-form');
    f.elements.citing.value = 'E2E900';
    f.elements.cited.value = 'P101';
    f.requestSubmit(f.querySelector('button'));
    return true;
  })()`);
  await waitFor(`document.querySelector('#mutate-log').textContent.includes('already existed')`, 'duplicate edge log');
  check('mutate: duplicate edge reported as already existed', true);

  /* unknown citing paper → 404 surfaced */
  await evalJs(`(() => {
    const f = document.querySelector('#add-citation-form');
    f.elements.citing.value = 'NOPE999';
    f.elements.cited.value = 'P101';
    f.requestSubmit(f.querySelector('button'));
    return true;
  })()`);
  await waitFor(`document.querySelector('#mutate-log').textContent.includes('! edge NOPE999')`, '404 edge error log');
  check('mutate: unknown paper error is visible', true);

  /* ---- 7. fuzzy search from the console ---- */
  await evalJs(`(() => {
    const f = document.querySelector('.query-console');
    f.elements.query.value = 'atention';
    f.elements.fuzzy.checked = true;
    f.requestSubmit(f.querySelector('[data-action=search]'));
    return true;
  })()`);
  await waitFor(`document.querySelector('.panel--search-results') && document.querySelector('.panel--search-results').textContent.includes('hits')`, 'search results rendered');
  const searchText = await evalJs(`document.querySelector('.panel--search-results').textContent`);
  check('fuzzy search: results panel labelled fuzzy', searchText.includes('fuzzy'));
  check('fuzzy search: typo query found hits', !searchText.includes('no hits'), `(panel: ${searchText.slice(0, 120)})`);
  check('search: other dynamic panels hidden', await evalJs(`document.querySelector('.panel--traversal').hidden === true && document.querySelector('.panel--reports').hidden === true`));

  /* non-fuzzy same query → should miss (control) */
  await evalJs(`(() => {
    const f = document.querySelector('.query-console');
    f.elements.query.value = 'atention';
    f.elements.fuzzy.checked = false;
    f.requestSubmit(f.querySelector('[data-action=search]'));
    return true;
  })()`);
  await waitFor(`document.querySelector('.panel--search-results').textContent.includes('"atention"') && !document.querySelector('.panel--search-results').textContent.includes('fuzzy')`, 'exact (non-fuzzy) search re-rendered');
  const exactText = await evalJs(`document.querySelector('.panel--search-results').textContent`);
  check('exact search: same typo without fuzzy gives no hits', exactText.includes('no hits'), `(panel: ${exactText.slice(0, 120)})`);

  /* ---- 8. shortest path (submit listener wired) ---- */
  await evalJs(`(() => {
    const f = document.querySelector('.query-console');
    f.elements.source.value = 'P103';
    f.elements.target.value = 'P107';
    f.requestSubmit(f.querySelector('button')); // first button = shortest path
    return true;
  })()`);
  if (isJava) {
    // pure-Java ApiServer has no /api/path — the dashboard must degrade gracefully
    await waitFor(`document.querySelector('.window__foot .ok').classList.contains('is-error')`, 'graceful error flash for missing /api/path');
    check('missing endpoint: error surfaced in status line, page alive', true);
  } else {
    await waitFor(`document.querySelector('.panel--traversal').textContent.includes('Shortest path')`, 'shortest path rendered');
    check('shortest path: submit listener wired end-to-end', true);
  }

  /* ---- 9. paper viewer: rows open the paper's own document ---- */
  // The pure-Java ApiServer has no /api/papers/{id} detail route, so this
  // surface degrades to a clear error state instead — checked separately.
  await evalJs(`window.location.hash = ''; document.querySelectorAll('.tabs > span')[0].click();`);
  await waitFor(`!!document.querySelector('.panels .panel a.paper-link[href="#/paper/P101"]')`, 'paper links rendered');
  check('papers: entries are real links', await evalJs(`document.querySelectorAll('.panels .panel a.paper-link[href^="#/paper/"]').length`) > 0);
  await evalJs(`document.querySelector('.panels .panel a.paper-link[href="#/paper/P101"]').click()`);
  if (isJava) {
    await waitFor(`!document.getElementById('paper-view').hidden && document.getElementById('paper-view-title').textContent.includes('could not be opened')`, 'viewer degrades on the Java surface');
    check('papers: missing detail route degrades to a clear error', true);
  } else {
    await waitFor(`!document.getElementById('paper-view').hidden && document.getElementById('paper-view-title').textContent.includes('Attention')`, 'paper viewer opened');
    check('papers: viewer opens the clicked paper', await evalJs(`document.getElementById('paper-view-id').textContent`) === 'P101');
    check('papers: dashboard hidden while viewing', await evalJs(`document.getElementById('dashboard-view').hidden === true`));
    check('papers: PDF embedded for a paper that ships one', await evalJs(`!!document.querySelector('.doc-frame')`));
    await evalJs(`document.getElementById('paper-back').click()`);
    await waitFor(`document.getElementById('dashboard-view').hidden === false`, 'back to dashboard');
    check('papers: Back returns to the dashboard', true);
  }
  await evalJs(`window.location.hash = ''`);
  await sleep(200);

  /* ---- 10. deployment-safe API base ---- */
  // The dashboard must resolve to the backend it is actually served by (or was
  // explicitly pointed at), never to a hardcoded developer machine.
  const resolvedBase = await evalJs(`document.getElementById('api-base').textContent`);
  check('api: base resolved to the active backend, not a hardcoded host',
    resolvedBase === API, `(base=${resolvedBase} api=${API})`);

  /* ---- 11. paper detail flow unchanged ---- */
  check('no uncaught page exceptions', pageErrors.length === 0, pageErrors.slice(0, 3).join(' | '));
}

try {
  await main();
} catch (error) {
  failed++;
  console.log('  FAIL  harness error:', error.message);
  if (pageErrors.length) console.log('  page errors:', pageErrors.slice(0, 5));
} finally {
  try { ws && ws.close(); } catch (_) {}
  try { spawn('taskkill', ['/F', '/T', '/PID', String(chrome.pid)], { stdio: 'ignore', windowsHide: true }); } catch (_) {}
  await sleep(500);
  try { rmSync(profile, { recursive: true, force: true }); } catch (_) {}
}
console.log(`\nRESULT: ${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
