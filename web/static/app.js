/* ============================================================================
   CERBERUS SYSTEM — web/static/app.js
   Fetch client for the local FastAPI engine. Same endpoints as the TUI,
   so both interfaces display identical data.

   Endpoints served by server/main.py (default http://127.0.0.1:8005):
     GET /api/health             -> engine status
     GET /api/stats              -> headline metrics
     GET /api/papers?q=&limit=&fuzzy= -> ranked papers / search (fuzzy toggle)
     GET /api/search?q=&fuzzy=   -> explicit Wagner-Fischer fuzzy search
     GET /api/traverse?source=&mode=bfs|dfs -> visit order from a paper
     GET /api/reports/authors    -> top authors by total citations
     GET /api/reports/trends     -> per-year citation trends
     GET /api/papers/:id         -> paper detail
     GET /api/path               -> BFS shortest citation path
     GET /api/paths              -> all simple paths (+ Hamiltonian flags)
     GET /api/optimal-route      -> Held-Karp bitmask DP route
     GET /api/lineage            -> BFS reachability by level
     POST /api/reload            -> re-read citation_data.csv
     POST /api/papers            -> add a paper (in-memory mutation)
     POST /api/citations         -> add a citation edge (in-memory mutation)
   ========================================================================== */

const API_BASE = localStorage.getItem('cerberusApiBase') || 'http://127.0.0.1:8005';

/* ------------------------------------------------------------------ core --
 * fetchJSON: the ONLY IO primitive the dashboard needs.
 * - always returns plain JSON
 * - throws Error with a readable message on network failure or {ok:false,...}
 * - callers may pass method/headers/body; headers merge with the Accept default
 * ------------------------------------------------------------------------ */
async function fetchJSON(path, options = {}) {
  let response;
  try {
    response = await fetch(API_BASE + path, {
      ...options,
      headers: { Accept: 'application/json', ...(options.headers || {}) },
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
 * Tiny router so the five dashboard tabs become real views — everything
 * renders from API state, no page reloads.
 * ------------------------------------------------------------------------ */
const TABS = ['graph', 'search', 'traversal', 'reports', 'mutate'];
let activeTab = TABS[0];
let datasets = {
  papers: [],
  stats: { papers: 0, edges: 0, most_cited: null, most_cited_count: 0 },
  mutations: [],
};

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

/* ---------------------------------------------------- dynamic panel utils *
 * Tabs other than "graph" inject their own <section class="panel"> into the
 * main .panels column. Only the active tab's panel is visible.
 * ------------------------------------------------------------------------ */
function dynamicPanel(selector, tabName) {
  const host = $('.panels');
  if (!host) return null;
  let panel = host.querySelector(selector);
  if (!panel) {
    panel = document.createElement('section');
    panel.className = selector.replace(/^\./, '');
    panel.setAttribute('data-dynamic-tab', tabName);
    panel.setAttribute('aria-label', tabName);
    host.appendChild(panel);
  }
  return panel;
}

function syncDynamicPanels() {
  document.querySelectorAll('[data-dynamic-tab]').forEach((el) => {
    el.hidden = el.getAttribute('data-dynamic-tab') !== activeTab;
  });
}

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
    const html = String(values[index]);
    el.innerHTML = html.includes('<') ? html : escapeHtml(html);
  });

  const status = $('.window__status');
  if (status) {
    status.innerHTML = `<i>&#9679;</i> ${stats.papers} papers &middot; ${stats.edges} edges`;
  }

  const tbody = $('.panels .panel .table tbody');
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

  const lineage = $('.panels .panel .flow');
  if (lineage && datasets.lineageChain) {
    const lineagePanel = lineage.closest('.panel');
    const heading = lineagePanel && lineagePanel.querySelector('.panel__heading');
    if (heading) heading.innerHTML = `Citation Lineage &mdash; ${escapeHtml(datasets.lineageChain.join(' &rarr; '))}`;
    lineage.innerHTML = nodeChain(datasets.lineageChain);
  }
}

function renderSearchTab() {
  const results = datasets.searchResults;
  if (activeTab !== 'search' || !results) return;
  const panel = dynamicPanel('.panel--search-results', 'search');
  if (!panel) return;
  const rows = results.map((paper) => `
    <tr><td class="id">${escapeHtml(paper.id)}</td><td>${escapeHtml(paper.title)}</td>
        <td class="author">${escapeHtml(paper.author)}</td><td>${paper.year}</td>
        <td class="count">${paper.citationCount}</td></tr>`);
  const mode = datasets.searchFuzzy ? ' · fuzzy' : '';
  panel.innerHTML = `
    <h3 class="panel__heading">Search &mdash; "${escapeHtml(datasets.searchQuery)}"${mode} &middot; ${results.length} hits</h3>
    <table class="table"><thead><tr><th>ID</th><th>Title</th><th class="col-author">Author</th><th>Year</th><th style="text-align:right;">Citations</th></tr></thead>
    <tbody>${rows.length ? rows.join('') : '<tr><td colspan="5">no hits</td></tr>'}</tbody></table>`;
}

function renderTraversalTab() {
  if (activeTab !== 'traversal' || !datasets.lastRun) return;
  const panel = dynamicPanel('.panel--traversal', 'traversal');
  if (!panel) return;
  const run = datasets.lastRun;
  const body = run.html || '<p class="flow__empty">run a traversal from the console</p>';
  panel.innerHTML = `<h3 class="panel__heading">${run.title}</h3>${body}`;
}

async function renderReportsTab() {
  if (activeTab !== 'reports') return;
  const panel = dynamicPanel('.panel--reports', 'reports');
  if (!panel) return;
  if (!datasets.reports) {
    panel.innerHTML = '<h3 class="panel__heading">Reports</h3><p class="flow__empty">loading reports &hellip;</p>';
    try {
      // Python backend: /api/reports/*; pure-Java ApiServer: /api/report?type=
      const [authors, trends] = await Promise.all([
        fetchJSON('/api/reports/authors?limit=10')
          .catch(() => fetchJSON('/api/report?type=top-authors&limit=10')),
        fetchJSON('/api/reports/trends')
          .catch(() => fetchJSON('/api/report?type=trends')),
      ]);
      datasets.reports = {
        authors: authors.authors || [],
        trends: trends.trends || [],
      };
    } catch (error) {
      statusFlash(error.message, true);
      panel.innerHTML = `<h3 class="panel__heading">Reports</h3><p class="flow__empty">${escapeHtml(error.message)}</p>`;
      return;
    }
  }
  if (activeTab !== 'reports') return; // user navigated away while fetching
  const { authors, trends } = datasets.reports;

  const maxAuthor = Math.max(...authors.map((a) => a.totalCitations), 1);
  const authorRows = authors.map((row, index) => `
    <tr><td class="rank">${index + 1}</td><td>${escapeHtml(row.author)}</td>
        <td>${row.papers}</td><td class="count">${row.totalCitations}</td>
        <td><span class="share"><span class="share__fill" style="width:${Math.round((row.totalCitations / maxAuthor) * 100)}%"></span></span></td></tr>`);

  const maxYear = Math.max(...trends.map((t) => t.totalCitations), 1);
  const trendRows = trends.map((row) => `
    <tr><td>${row.year}</td><td>${row.papers}</td><td class="count">${row.totalCitations}</td>
        <td><span class="share"><span class="share__fill" style="width:${Math.round((row.totalCitations / maxYear) * 100)}%"></span></span></td></tr>`);

  panel.innerHTML = `
    <h3 class="panel__heading">Reports &mdash; top authors &amp; yearly trends</h3>
    <div class="reports-grid">
      <div>
        <h4 class="report__sub">Top Authors &middot; ${authors.length} shown</h4>
        <table class="table"><thead><tr><th class="rank">#</th><th>Author</th><th>Papers</th><th style="text-align:right;">Citations</th><th>Share</th></tr></thead>
        <tbody>${authorRows.length ? authorRows.join('') : '<tr><td colspan="5">no data</td></tr>'}</tbody></table>
      </div>
      <div>
        <h4 class="report__sub">Citation Trends &middot; ${trends.length} years</h4>
        <table class="table"><thead><tr><th>Year</th><th>Papers</th><th style="text-align:right;">Citations</th><th>Load</th></tr></thead>
        <tbody>${trendRows.length ? trendRows.join('') : '<tr><td colspan="4">no data</td></tr>'}</tbody></table>
      </div>
    </div>`;
}

function renderMutationLog() {
  const log = $('#mutate-log');
  if (!log) return;
  const entries = datasets.mutations;
  log.innerHTML = entries.length
    ? entries.map((entry) => `<div class="mutate-log__line${entry.error ? ' is-error' : ''}">${escapeHtml(entry.text)}</div>`).join('')
    : '<div class="mutate-log__line">no mutations this session</div>';
}

function renderMutateTab() {
  if (activeTab !== 'mutate') return;
  const panel = dynamicPanel('.panel--mutate', 'mutate');
  if (!panel) return;
  if (!panel.dataset.ready) {
    panel.dataset.ready = '1';
    panel.innerHTML = `
      <h3 class="panel__heading">Graph Mutations &mdash; live in-memory engine</h3>
      <div class="mutate-grid">
        <form class="mutate-form" id="add-paper-form">
          <h4 class="mutate-form__title">add paper</h4>
          <input name="id" placeholder="id e.g. P900" autocomplete="off" />
          <input name="title" placeholder="title" autocomplete="off" />
          <input name="author" placeholder="author" autocomplete="off" />
          <input name="year" placeholder="year e.g. 2024" inputmode="numeric" autocomplete="off" />
          <button type="submit" class="console__btn">add paper</button>
        </form>
        <form class="mutate-form" id="add-citation-form">
          <h4 class="mutate-form__title">add citation edge</h4>
          <input name="citing" placeholder="citing id e.g. P900" autocomplete="off" />
          <span class="arrow">&rarr;</span>
          <input name="cited" placeholder="cited id e.g. P101" autocomplete="off" />
          <button type="submit" class="console__btn">add edge</button>
        </form>
      </div>
      <div class="mutate-log" id="mutate-log"></div>`;
    panel.querySelector('#add-paper-form').addEventListener('submit', handleAddPaper);
    panel.querySelector('#add-citation-form').addEventListener('submit', handleAddCitation);
  }
  renderMutationLog();
}

function logMutation(text, isError = false) {
  datasets.mutations.unshift({ text, error: isError });
  if (datasets.mutations.length > 12) datasets.mutations.pop();
  renderMutationLog();
}

/* ------------------------------------------------------------- boot load -- */
async function render() {
  renderGraphTab(); // header metrics + static panels are visible on every tab
  syncDynamicPanels();
  if (activeTab === 'search') renderSearchTab();
  if (activeTab === 'traversal') renderTraversalTab();
  if (activeTab === 'reports') await renderReportsTab();
  if (activeTab === 'mutate') renderMutateTab();
}

/* fetchStats: GET /api/stats on the Python backend; the pure-Java ApiServer
 * has no stats route, so derive the same headline metrics from the paper
 * list (citationCount is the in-degree, and summing in-degrees = edges). */
async function fetchStats() {
  try {
    return await fetchJSON('/api/stats');
  } catch (_) {
    const payload = await fetchJSON('/api/papers?limit=200');
    const papers = payload.results || [];
    let most = null;
    for (const p of papers) {
      if (!most || p.citationCount > most.citationCount) most = p;
    }
    return {
      papers: papers.length,
      edges: papers.reduce((sum, p) => sum + (p.citationCount || 0), 0),
      most_cited: most ? most.id : null,
      most_cited_count: most ? most.citationCount : 0,
    };
  }
}

async function loadOverview() {
  const [stats, papers] = await Promise.all([
    fetchStats(),
    fetchJSON('/api/papers?limit=5'),
  ]);
  datasets.stats = stats;
  datasets.papers = papers.results || [];
  render();
}

async function loadLineageFromShortestChain() {
  try {
    const shortest = await fetchJSON('/api/path?source=P103&target=P107');
    datasets.lineageChain = shortest.path || [];
    if (shortest.found) {
      datasets.lastRun = {
        title: `BFS Shortest Path &mdash; ${shortest.hops} hops`,
        html: `<div class="flow">${nodeChain(shortest.path)}</div>`,
      };
    }
  } catch (_) {
    return; // ApiServer without /api/path — headline overview still works
  }
  render();
}

/* --------------------------------------------------- user-triggered forms */
function buildQueryConsole() {
  const foot = $('.window__foot');
  if (!foot || foot.querySelector('.query-console')) return;
  const console_ = document.createElement('form');
  console_.className = 'query-console';
  console_.innerHTML = `
    <input name="source" placeholder="source e.g. P103" autocomplete="off" />
    <span class="arrow">&rarr;</span>
    <input name="target" placeholder="target e.g. P107" autocomplete="off" />
    <button type="submit" class="console__btn">shortest path</button>
    <input name="route" placeholder="bitmask route: P101,P104,P107" size="26" autocomplete="off" />
    <button type="submit" class="console__btn" data-action="route">optimal route</button>
    <select name="mode" class="console__select" title="traversal strategy">
      <option value="bfs">BFS</option>
      <option value="dfs">DFS</option>
    </select>
    <button type="submit" class="console__btn" data-action="traverse">traverse</button>
    <input name="query" placeholder="search: attention" size="16" autocomplete="off" />
    <label class="console__check" title="Wagner–Fischer typo-tolerant matching"><input type="checkbox" name="fuzzy" /> fuzzy</label>
    <button type="submit" class="console__btn" data-action="search">search</button>`;
  foot.before(console_);
  console_.addEventListener('submit', onSubmit);
}

function onSubmit(event) {
  event.preventDefault();
  const action = event.submitter?.dataset?.action || 'path';
  const form = event.target;
  if (action === 'search') {
    const query = form.elements.query.value.trim();
    if (!query) return statusFlash('type a query to search for', true);
    handleSearch(query, form.elements.fuzzy.checked);
  } else if (action === 'route') {
    const ids = form.elements.route.value.split(',').map((s) => s.trim()).filter(Boolean);
    if (ids.length >= 2) handleOptimalRoute(ids);
    else statusFlash('list at least two paper ids for the bitmask route', true);
  } else if (action === 'traverse') {
    const source = form.elements.source.value.trim().toUpperCase();
    if (!source) return statusFlash('enter a source paper id to traverse', true);
    handleTraverse(source, form.elements.mode.value);
  } else {
    const source = form.elements.source.value.trim().toUpperCase();
    const target = form.elements.target.value.trim().toUpperCase();
    if (source && target) handleShortestPath(source, target);
    else statusFlash('source and target required for shortest path', true);
  }
}

async function handleShortestPath(source, target) {
  statusFlash(`resolving ${source} → ${target} …`);
  try {
    const result = await fetchJSON(`/api/path?source=${encodeURIComponent(source)}&target=${encodeURIComponent(target)}`);
    datasets.lineageChain = result.path;
    datasets.lastRun = result.found
      ? { title: `BFS Shortest Path &mdash; ${result.hops} hops · ${escapeHtml(source)} → ${escapeHtml(target)}`, html: `<div class="flow">${nodeChain(result.path)}</div>` }
      : { title: `No path — ${escapeHtml(source)} → ${escapeHtml(target)}`, html: '<p class="flow__empty">no directed citation chain links these papers</p>' };
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

async function handleTraverse(source, mode) {
  statusFlash(`${mode.toUpperCase()} traversal from ${source} …`);
  try {
    const params = new URLSearchParams({ source, mode });
    const result = await fetchJSON(`/api/traverse?${params}`);
    if (!result.found || !result.order.length) {
      datasets.lastRun = {
        title: `Traversal &middot; ${escapeHtml(mode.toUpperCase())} &middot; FROM ${escapeHtml(source)}`,
        html: `<p class="flow__empty">paper ${escapeHtml(source)} is not in the graph</p>`,
      };
      showTab('traversal');
      statusFlash(`no papers reached from ${source}`, true);
      return;
    }
    const rows = result.order.map((node, index) => `
      <tr><td class="rank">${index + 1}</td><td class="id">${escapeHtml(node.id)}</td>
          <td>${escapeHtml(node.title)}</td><td>${node.year}</td>
          <td class="count">${node.citationCount}</td></tr>`);
    datasets.lastRun = {
      title: `Traversal &middot; ${escapeHtml(result.mode.toUpperCase())} &middot; FROM ${escapeHtml(result.source)}`,
      html: `<p class="flow__empty">${result.reached} papers reached &middot; visit order below</p>
        <table class="table"><thead><tr><th class="rank">Hop</th><th>ID</th><th>Title</th><th>Year</th><th style="text-align:right;">Citations</th></tr></thead>
        <tbody>${rows.join('')}</tbody></table>`,
    };
    showTab('traversal');
    statusFlash(`${result.mode.toUpperCase()} from ${source} · ${result.reached} papers reached`);
  } catch (error) {
    statusFlash(error.message, true);
  }
}

async function handleSearch(query, fuzzy = false) {
  statusFlash(`searching "${query}"${fuzzy ? ' (fuzzy)' : ''} …`);
  try {
    const params = new URLSearchParams({ q: query, limit: 25 });
    if (fuzzy) params.set('fuzzy', 'true');
    const payload = await fetchJSON(`/api/papers?${params}`);
    datasets.searchQuery = query;
    datasets.searchFuzzy = fuzzy;
    datasets.searchResults = payload.results || [];
    showTab('search');
    statusFlash(`search "${query}"${fuzzy ? ' · fuzzy' : ''} · ${datasets.searchResults.length} hits`);
  } catch (error) {
    statusFlash(error.message, true);
  }
}

/* ----------------------------------------------------------- mutations -- */
async function refreshAfterMutation() {
  datasets.reports = null; // invalidate the reports cache
  await loadOverview();    // refresh metric tiles + top-cited table
}

async function handleAddPaper(event) {
  event.preventDefault();
  const form = event.target;
  const body = {
    id: form.elements.id.value.trim().toUpperCase(),
    title: form.elements.title.value.trim(),
    author: form.elements.author.value.trim(),
    year: Number(form.elements.year.value),
  };
  if (!body.id || !body.title || !body.author || !Number.isInteger(body.year)) {
    statusFlash('id, title, author and a numeric year are required', true);
    return;
  }
  statusFlash(`adding paper ${body.id} …`);
  try {
    const paper = await fetchJSON('/api/papers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    logMutation(`+ paper ${paper.id} — ${paper.title} (${paper.year}) · ${paper.author}`);
    statusFlash(`paper ${paper.id} added · ${datasets.stats.papers + 1} papers in graph`);
    form.reset();
    await refreshAfterMutation();
  } catch (error) {
    logMutation(`! add paper ${body.id || '?'} failed — ${error.message}`, true);
    statusFlash(error.message, true);
  }
}

async function handleAddCitation(event) {
  event.preventDefault();
  const form = event.target;
  const citing = form.elements.citing.value.trim().toUpperCase();
  const cited = form.elements.cited.value.trim().toUpperCase();
  if (!citing || !cited) {
    statusFlash('both citing and cited ids are required', true);
    return;
  }
  statusFlash(`linking ${citing} → ${cited} …`);
  try {
    const result = await fetchJSON('/api/citations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ citing, cited }),
    });
    if (result.added) {
      logMutation(`+ edge ${result.citing} → ${result.cited} · ${result.edges} edges total`);
      statusFlash(`edge ${result.citing} → ${result.cited} added · ${result.edges} edges`);
      form.reset();
    } else {
      logMutation(`· edge ${result.citing} → ${result.cited} already existed`);
      statusFlash(`edge ${result.citing} → ${result.cited} already existed`);
    }
    await refreshAfterMutation();
  } catch (error) {
    logMutation(`! edge ${citing} → ${cited} failed — ${error.message}`, true);
    statusFlash(error.message, true);
  }
}

/* ------------------------------------------------------------------ boot -- */
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
