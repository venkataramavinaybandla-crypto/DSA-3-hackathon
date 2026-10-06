/* ============================================================================
   CERBERUS SYSTEM — web/static/app.js
   Fetch client for the local FastAPI engine. Same endpoints as the TUI,
   so both interfaces display identical data.

   Endspoints served by server/main.py (default http://127.0.0.1:8005):
     GET /api/health            -> engine status
     GET /api/stats             -> headline metrics
     GET /api/papers?q=&limit=  -> ranked papers / substring search
     GET /api/papers/:id        -> paper detail
     GET /api/path              -> BFS shortest citation path
     GET /api/paths             -> all simple paths (+ Hamiltonian flags)
     GET /api/optimal-route     -> Held-Karp bitmask DP route
     GET /api/lineage           -> BFS reachability by level
     POST /api/reload           -> re-read citation_data.csv
   ========================================================================== */

const API_BASE = localStorage.getItem('cerberusApiBase') || 'http://127.0.0.1:8005';

/* ------------------------------------------------------------------ core --
 * fetchJSON: the ONLY IO primitive the dashboard needs.
 * - always returns plain JSON
 * - throws Error with a readable message on network failure or {ok:false,...}
 * ------------------------------------------------------------------------ */
async function fetchJSON(path, options = {}) {
  let response;
  try {
    response = await fetch(API_BASE + path, {
      headers: { Accept: 'application/json' },
      ...options,
    });
  } catch (networkError) {
    throw new Error(`Cannot reach the CERBERUS API at ${API_BASE}. Is the server running?`);
  }
  if (!response.ok) {
    let message;
    try {
      const payload = await response.json();
      message = payload.error || payload.detail || response.statusText;
    } catch (_) {
      message = response.statusText;
    }
    throw new Error(message);
  }
  return response.json();
}

/* --------------------------------------------------------------- helpers --
 */
const $ = (selector, root = document) => root.querySelector(selector);

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (ch) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]
  ));
}

function nodeChain(ids, classList = '') {
  if (!ids || !ids.length) return '<span class="flow__empty">—</span>';
  return ids
    .map((id, i) => (i
      ? '<span class="arrow">&rarr;</span>'
      + `<span class="node">${escapeHtml(id)}</span>`
      : `<span class="node node--hot">${escapeHtml(id)}</span>`))
    .join('');
}

function statusFlash(message, isError = false) {
  const foot = $('.window__foot .ok');
  if (!foot) return;
  foot.textContent = `${isError ? '✕' : '✓'} ${message}`;
  foot.classList.toggle('is-error', Boolean(isError));
}

/* ------------------------------------------------------------- mini-router *
 * Tiny hash router so the four dashboard tabs become real views — everything
 * renders from API state, no page reloads.
 * ------------------------------------------------------------------------ */
const TABS = ['graph', 'search', 'traversal', 'reports'];
let activeTab = TABS[0];
let datasets = { papers: [], stats: { papers: 0, edges: 0, most_cited: null, most_cited_count: 0 } };

function syncTabHighlight() {
  document.querySelectorAll('.tabs > span').forEach((tab, index) => {
    tab.classList.toggle('is-active', TABS[index] === activeTab);
  });
}

function showTab(next) {
  if (!TABS.includes(next)) return;
  activeTab = next;
  syncTabHighlight();
  render();
}

document.querySelectorAll('.tabs > span').forEach((tab, index) => {
  tab.addEventListener('click', () => showTab(TABS[index]));
});

/* -------------------------------------------------------------- renderers *
 * Every view is a pure function of the API state in `datasets`; render()
 * paints the active tab. Call render() after any fetch resolves.
 * ------------------------------------------------------------------------ */
function renderGraphTab() {
  const { stats, papers } = datasets;
  const metricEls = document.querySelectorAll('.metric__value, .metric > span:last-child');
  const values = [stats.papers, stats.edges, `${stats.most_cited_count} ${stats.most_cited ? `<small>${escapeHtml(stats.most_cited)}</small>` : ''}`, 'O(V+E)'];
  metricEls.forEach((el, index) => {
    if (values[index] === undefined) return;
    el.innerHTML = values[index].includes('<') ? values[index] : escapeHtml(String(values[index]));
  });

  const status = $('.window__status');
  if (status) {
    status.innerHTML = `<i>&#9679;</i> ${stats.papers} papers &middot; ${stats.edges} edges`;
  }

  const tbody = $('.panel .table tbody');
  if (tbody && papers.length) {
    const topCount = Math.max(...papers.map((p) => p.citationCount), 1);
    const rows = papers.slice(0, 5).map((paper, index) => `
      <tr>
        <td class="rank">${index + 1}</td>
        <td class="id">${escapeHtml(paper.id)}</td>
        <td>${escapeHtml(paper.title)}</td>
        <td class="author">${escapeHtml(paper.author)}</td>
        <td>${paper.year}</td>
        <td class="count">${paper.citationCount}</td>
        <td><span class="share"><span class="share__fill" style="width:${Math.round((paper.citationCount / topCount) * 100)}%"></span></span></td>
      </tr>`);
    tbody.innerHTML = rows.join('');
  }

  const lineage = $('.panel .flow');
  if (lineage && datasets.lineageChain) {
    const lineagePanel = lineage.closest('.panel');
    const heading = lineagePanel && lineagePanel.querySelector('.panel__heading');
    if (heading) heading.innerHTML = `Citation Lineage &mdash; ${escapeHtml(datasets.lineageChain.join(' &rarr; '))}`;
    lineage.innerHTML = nodeChain(datasets.lineageChain);
  }
}

function renderSearchTab() {
  const results = datasets.searchResults;
  const show = activeTab === 'search' && results;
  if (!show) return;
  const rail = $('.rail');
  if (!rail) return;
  let panel = $('.panel--search-results');
  if (!panel) {
    panel = document.createElement('section');
    panel.className = 'panel panel--search-results';
    panel.setAttribute('aria-label', 'Search results');
    rail.insertAdjacentElement('afterend', panel);
  }
  const rows = results.map((paper) => `
    <tr><td class="id">${escapeHtml(paper.id)}</td><td>${escapeHtml(paper.title)}</td>
        <td class="author">${escapeHtml(paper.author)}</td><td>${paper.year}</td>
        <td class="count">${paper.citationCount}</td></tr>`);
  panel.innerHTML = `
    <h3 class="panel__heading">Search &mdash; "${escapeHtml(datasets.searchQuery)}" &middot; ${results.length} hits</h3>
    <table class="table"><thead><tr><th>ID</th><th>Title</th><th class="col-author">Author</th><th>Year</th><th style="text-align:right;">Citations</th></tr></thead>
    <tbody>${rows.length ? rows.join('') : '<tr><td colspan="5">no hits</td></tr>'}</tbody></table>`;
}

function renderTraversalTab() {
  if (activeTab !== 'traversal' || !datasets.lastRun) return;
  const run = datasets.lastRun;
  const rail = $('.rail');
  if (!rail) return;
  let panel = $('.panel--traversal');
  if (!panel) {
    panel = document.createElement('section');
    panel.className = 'panel panel--traversal';
    rail.insertAdjacentElement('afterend', panel);
  }
  const body = run.html || '<p class="flow__empty">run a traversal from the console</p>';
  panel.innerHTML = `<h3 class="panel__heading">${escapeHtml(run.title)}</h3>${body}`;
}

/* ------------------------------------------------------------- boot load --
 */
async function render() {
  if (activeTab === 'graph') renderGraphTab();
  if (activeTab === 'search') renderSearchTab();
  if (activeTab === 'traversal') renderTraversalTab();
}

async function loadOverview() {
  const [stats, papers] = await Promise.all([
    fetchJSON('/api/stats'),
    fetchJSON('/api/papers?limit=5'),
  ]);
  datasets.stats = stats;
  datasets.papers = papers.results || [];
  render();
}

async function loadLineageFromShortestChain() {
  const shortest = await fetchJSON('/api/path?source=P103&target=P107');
  datasets.lineageChain = shortest.path || [];
  if (shortest.found) {
    datasets.lastRun = {
      title: `BFS Shortest Path &mdash; ${shortest.hops} hops`,
      html: `<div class="flow">${nodeChain(shortest.path)}</div>`,
    };
  }
  render();
}

/* --------------------------------------------------- user-triggered forms *
 */
function buildQueryConsole() {
  const foot = $('.window__foot');
  if (!foot || foot.querySelector('.query-console')) return;
  const console_ = document.createElement('form');
  console_.className = 'query-console';
  console_.innerHTML = `
    <input name="source" placeholder="source e.g. P103" required minlength="2" />
    <span class="arrow">&rarr;</span>
    <input name="target" placeholder="target e.g. P107" required minlength="2" />
    <button type="submit" class="console__btn">shortest path</button>
    <input name="route" placeholder="bitmask route: P101,P104,P107" size="26" />
    <button type="submit" class="console__btn" data-action="route">optimal route</button>
    <input name="query" placeholder="search: attention" size="16" />
    <button type="submit" class="console__btn" data-action="search">search</button>`;
  foot.before(console_);
}

function onSubmit(event) {
  event.preventDefault();
  const action = event.submitter?.dataset?.action || 'path';
  if (action === 'search') {
    const query = event.target.elements.query.value.trim();
    if (!query) return;
    handleSearch(query);
  } else if (action === 'route') {
    const ids = event.target.elements.route.value.split(',').map((s) => s.trim()).filter(Boolean);
    if (ids.length >= 2) handleOptimalRoute(ids);
  } else {
    const source = event.target.elements.source.value.trim().toUpperCase();
    const target = event.target.elements.target.value.trim().toUpperCase();
    if (source && target) handleShortestPath(source, target);
  }
}

async function handleShortestPath(source, target) {
  statusFlash(`resolving ${source} → ${target} …`);
  try {
    const result = await fetchJSON(`/api/path?source=${encodeURIComponent(source)}&target=${encodeURIComponent(target)}`);
    datasets.lineageChain = result.path;
    datasets.lastRun = result.found
      ? { title: `BFS Shortest Path &mdash; ${result.hops} hops · ${source} → ${target}`, html: `<div class="flow">${nodeChain(result.path)}</div>` }
      : { title: `No path — ${source} → ${target}`, html: '<p class="flow__empty">no directed citation chain links these papers</p>' };
    showTab(result.found ? 'traversal' : 'graph');
    statusFlash(`path ${source} → ${target} · ${result.hops} hops`);
  } catch (error) {
    statusFlash(error.message, true);
  }
}

async function handleOptimalRoute(ids) {
  statusFlash('solving Held-Karp bitmask route …');
  try {
    const params = new URLSearchParams({ papers: ids.join(',') });
    const result = await fetchJSON(`/api/optimal-route?${params}`);
    datasets.lastRun = result.walk && result.walk.length
      ? { title: `Optimal Citation Route &mdash; ${result.hops} hops total`, html: `<div class="flow">${nodeChain(result.walk)}</div>` }
      : { title: 'No valid ordering', html: '<p class="flow__empty">no directed ordering visits every requested paper</p>' };
    showTab('traversal');
    statusFlash(`optimal route · ${result.hops} hops`);
  } catch (error) {
    statusFlash(error.message, true);
  }
}

async function handleSearch(query) {
  statusFlash(`searching "${query}" …`);
  try {
    const params = new URLSearchParams({ q: query, limit: 25 });
    const payload = await fetchJSON(`/api/papers?${params}`);
    datasets.searchQuery = query;
    datasets.searchResults = payload.results || [];
    showTab('search');
    statusFlash(`search "${query}" · ${datasets.searchResults.length} hits`);
  } catch (error) {
    statusFlash(error.message, true);
  }
}

/* ------------------------------------------------------------------ boot --
 */
async function boot() {
  buildQueryConsole();
  syncTabHighlight();
  try {
    await loadOverview();
    await loadLineageFromShortestChain();
    statusFlash('engine ready — zero java.util in core logic');
  } catch (error) {
    statusFlash(error.message, true);
    renderGraphTab(); // paint cached/zeroed state so the dashboard still looks alive
  }
}

document.addEventListener('DOMContentLoaded', boot);
