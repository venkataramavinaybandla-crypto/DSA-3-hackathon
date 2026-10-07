/* ============================================================================
   CERBERUS SYSTEM — web/static/app.js
   Fetch client + renderer for the citation-analysis dashboard.

   The dashboard is a *client* of the CERBERUS REST API; no graph logic lives
   here. Endpoints consumed (server/main.py):
     GET  /api/health             -> engine status
     GET  /api/stats              -> headline metrics
     GET  /api/papers?q=&limit=&fuzzy= -> ranked papers / search
     GET  /api/traverse?source=&mode=bfs|dfs -> visit order from a paper
     GET  /api/reports/authors    -> top authors by total citations
     GET  /api/reports/trends     -> per-year citation trends
     GET  /api/papers/:id         -> paper detail (+ document availability)
     GET  /api/papers/:id/document?format=pdf|text -> the paper's own file
     GET  /api/path               -> BFS shortest citation path
     GET  /api/optimal-route      -> Held-Karp bitmask DP route
     POST /api/reload             -> re-read citation_data.csv
     POST /api/papers             -> add a paper (in-memory mutation)
     POST /api/citations          -> add a citation edge (in-memory mutation)
   ========================================================================== */

/* --------------------------------------------------------------- API base --
 * Deployment-safe resolution. A deployed dashboard must never assume the
 * developer's machine. Order: ?api= -> localStorage -> config.js -> this
 * page's own origin -> local fallback (only when opened from file://).
 * ------------------------------------------------------------------------ */
function stripTrailingSlash(url) {
  return String(url).replace(/\/+$/, '');
}

function resolveApiBase() {
  const fromQuery = new URLSearchParams(window.location.search).get('api');
  if (fromQuery && fromQuery.trim()) return stripTrailingSlash(fromQuery.trim());

  const stored = (localStorage.getItem('cerberusApiBase') || '').trim();
  if (stored) return stripTrailingSlash(stored);

  const configured = (window.CERBERUS_API_URL || '').trim();
  if (configured) return stripTrailingSlash(configured);

  // Served by the API (local dev or single-service deploy): same origin.
  if (window.location.protocol === 'http:' || window.location.protocol === 'https:') {
    return stripTrailingSlash(window.location.origin);
  }
  // Opened straight off disk — assume a local backend.
  return 'http://127.0.0.1:8005';
}

const API_BASE = resolveApiBase();

/* ------------------------------------------------------------------ core -- */
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

/* --------------------------------------------------------------- helpers -- */
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (ch) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]
  ));
}

/* A paper entry is a real link: clickable with a mouse, focusable and
 * actionable with the keyboard, and tappable on touch. */
function paperLink(id, label, extraClass = '') {
  const safeId = escapeHtml(id);
  const classes = `paper-link${extraClass ? ` ${extraClass}` : ''}`;
  return `<a class="${classes}" href="#/paper/${encodeURIComponent(id)}" data-paper-id="${safeId}">${escapeHtml(label)}</a>`;
}

function metricValue(value) {
  const text = String(value ?? '');
  return text.includes('<') ? text : escapeHtml(text);
}

function nodeChain(ids) {
  if (!ids || !ids.length) return '<span class="flow__empty">No chain to show.</span>';
  return ids.map((id, i) => (
    `${i ? '<span class="arrow" aria-hidden="true">&rarr;</span>' : ''}`
    + `<a class="node${i === 0 ? ' node--hot' : ''}" href="#/paper/${encodeURIComponent(id)}" `
    + `data-paper-id="${escapeHtml(id)}">${escapeHtml(id)}</a>`
  )).join('');
}

function statusFlash(message, isError = false) {
  const foot = $('.window__foot .ok');
  if (!foot) return;
  foot.textContent = `${isError ? '\u2715' : '\u2713'} ${message}`;
  foot.classList.toggle('is-error', Boolean(isError));
}

function panelHeading(title, note) {
  return `<div class="panel__head">
      <h3 class="panel__heading">${title}</h3>
      ${note ? `<p class="panel__note">${note}</p>` : ''}
    </div>`;
}

/* ------------------------------------------------------------- mini-router */
const TABS = ['graph', 'search', 'traversal', 'reports', 'mutate'];
let activeTab = TABS[0];
const datasets = {
  papers: [],
  stats: { papers: 0, edges: 0, most_cited: null, most_cited_count: 0 },
  mutations: [],
};

function syncTabHighlight() {
  $$('.tabs > span').forEach((tab, index) => {
    const active = TABS[index] === activeTab;
    tab.classList.toggle('is-active', active);
    tab.setAttribute('aria-selected', String(active));
    tab.setAttribute('tabindex', active ? '0' : '-1');
  });
}

function showTab(next) {
  if (!TABS.includes(next)) return;
  activeTab = next;
  syncTabHighlight();
  render();
}

function wireTabs() {
  const tabs = $$('.tabs > span');
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => showTab(TABS[index]));
    tab.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        showTab(TABS[index]);
      } else if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
        event.preventDefault();
        const step = event.key === 'ArrowRight' ? 1 : tabs.length - 1;
        const nextIndex = (index + step) % tabs.length;
        tabs[nextIndex].focus();
        showTab(TABS[nextIndex]);
      }
    });
  });
}

/* ---------------------------------------------------- dynamic panel utils -- */
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
  $$('[data-dynamic-tab]').forEach((el) => {
    el.hidden = el.getAttribute('data-dynamic-tab') !== activeTab;
  });
  // The graph-only panels (most cited, lineage) step aside on other views so
  // each tab reads as one focused task instead of a wall of data.
  $$('[data-view="graph"]').forEach((el) => {
    el.hidden = activeTab !== 'graph';
  });
}

/* -------------------------------------------------------------- renderers -- */
function renderGraphTab() {
  const { stats, papers } = datasets;
  const values = [
    stats.papers,
    stats.edges,
    `${stats.most_cited_count}${stats.most_cited ? ` <small>${escapeHtml(stats.most_cited)}</small>` : ''}`,
    stats.papers ? (stats.edges / stats.papers).toFixed(1) : '0.0',
  ];
  $$('.metric__value').forEach((el, index) => {
    if (values[index] === undefined) return;
    el.innerHTML = metricValue(values[index]);
  });

  const status = $('.window__status');
  if (status) {
    status.innerHTML = `<i aria-hidden="true">&#9679;</i> ${stats.papers} papers &middot; ${stats.edges} edges`;
  }
  setRailFacts({ papers: stats.papers, edges: stats.edges });

  const tbody = $('.panels .panel .table tbody');
  if (tbody && papers.length) {
    const topCount = Math.max(...papers.map((p) => p.citationCount), 1);
    tbody.innerHTML = papers.slice(0, 5).map((paper, index) => `
      <tr data-paper-id="${escapeHtml(paper.id)}">
        <td class="rank" data-label="#">${index + 1}</td>
        <td class="id" data-label="ID">${paperLink(paper.id, paper.id)}</td>
        <td class="title" data-label="Title">${paperLink(paper.id, paper.title, 'paper-link--title')}</td>
        <td class="author" data-label="Author">${escapeHtml(paper.author)}</td>
        <td data-label="Year">${escapeHtml(paper.year)}</td>
        <td class="count" data-label="Citations">${paper.citationCount}</td>
        <td data-label="Share"><span class="share"><span class="share__fill" style="width:${Math.round((paper.citationCount / topCount) * 100)}%"></span></span></td>
      </tr>`).join('');
  }

  const lineage = $('.panels .panel .flow');
  if (lineage && datasets.lineageChain) {
    const heading = lineage.closest('.panel')?.querySelector('.panel__heading');
    if (heading) heading.textContent = 'Citation lineage';
    const note = $('#lineage-note');
    if (note) {
      note.textContent = datasets.lineageHops != null
        ? `BFS shortest chain \u00b7 ${datasets.lineageHops} hops`
        : 'BFS shortest chain';
    }
    lineage.innerHTML = nodeChain(datasets.lineageChain);
  }
}

function renderSearchTab() {
  if (activeTab !== 'search' || !datasets.searchResults) return;
  const panel = dynamicPanel('.panel--search-results', 'search');
  if (!panel) return;
  const results = datasets.searchResults;
  const rows = results.map((paper) => `
    <tr data-paper-id="${escapeHtml(paper.id)}">
      <td class="id" data-label="ID">${paperLink(paper.id, paper.id)}</td>
      <td class="title" data-label="Title">${paperLink(paper.id, paper.title, 'paper-link--title')}</td>
      <td class="author" data-label="Author">${escapeHtml(paper.author)}</td>
      <td data-label="Year">${escapeHtml(paper.year)}</td>
      <td class="count" data-label="Citations">${paper.citationCount}</td>
    </tr>`);
  // "fuzzy" only appears when fuzzy matching was actually used — the label
  // stays contextual instead of being part of the permanent chrome.
  const matchNote = datasets.searchFuzzy
    ? 'fuzzy match \u00b7 Wagner\u2013Fischer edit distance'
    : 'exact substring match \u00b7 KMP';
  panel.innerHTML = [
    panelHeading(
      `Search \u2014 "${escapeHtml(datasets.searchQuery)}"`,
      `${matchNote} \u00b7 ${results.length} hits`,
    ),
    '<div class="table-wrap"><table class="table"><thead><tr>',
    '<th scope="col">ID</th><th scope="col">Title</th><th class="col-author" scope="col">Author</th>',
    '<th scope="col">Year</th><th class="col-count" scope="col">Citations</th>',
    '</tr></thead><tbody>',
    rows.length ? rows.join('') : '<tr><td colspan="5">no hits</td></tr>',
    '</tbody></table></div>',
  ].join('');
}

function renderTraversalTab() {
  if (activeTab !== 'traversal' || !datasets.lastRun) return;
  const panel = dynamicPanel('.panel--traversal', 'traversal');
  if (!panel) return;
  const run = datasets.lastRun;
  panel.innerHTML = panelHeading(run.title, run.note || '') + (run.html
    || '<p class="flow__empty">Run a traversal from the toolbar above.</p>');
}

async function renderReportsTab() {
  if (activeTab !== 'reports') return;
  const panel = dynamicPanel('.panel--reports', 'reports');
  if (!panel) return;
  if (!datasets.reports) {
    panel.innerHTML = panelHeading('Reports')
      + '<p class="flow__empty">Loading reports &hellip;</p>';
    try {
      const [authors, trends] = await Promise.all([
        fetchJSON('/api/reports/authors?limit=10')
          .catch(() => fetchJSON('/api/report?type=top-authors&limit=10')),
        fetchJSON('/api/reports/trends')
          .catch(() => fetchJSON('/api/report?type=trends')),
      ]);
      datasets.reports = { authors: authors.authors || [], trends: trends.trends || [] };
    } catch (error) {
      statusFlash(error.message, true);
      panel.innerHTML = panelHeading('Reports')
        + `<p class="flow__empty">${escapeHtml(error.message)}</p>`;
      return;
    }
  }
  if (activeTab !== 'reports') return;

  const { authors, trends } = datasets.reports;
  const maxAuthor = Math.max(...authors.map((a) => a.totalCitations), 1);
  const authorRows = authors.map((row, index) => `
    <tr>
      <td class="rank" data-label="#">${index + 1}</td>
      <td class="author" data-label="Author">${escapeHtml(row.author)}</td>
      <td data-label="Papers">${row.papers}</td>
      <td class="count" data-label="Citations">${row.totalCitations}</td>
      <td data-label="Share"><span class="share"><span class="share__fill" style="width:${Math.round((row.totalCitations / maxAuthor) * 100)}%"></span></span></td>
    </tr>`);

  const maxYear = Math.max(...trends.map((t) => t.totalCitations), 1);
  const trendRows = trends.map((row) => `
    <tr>
      <td data-label="Year">${row.year}</td>
      <td data-label="Papers">${row.papers}</td>
      <td class="count" data-label="Citations">${row.totalCitations}</td>
      <td data-label="Load"><span class="share"><span class="share__fill" style="width:${Math.round((row.totalCitations / maxYear) * 100)}%"></span></span></td>
    </tr>`);

  panel.innerHTML = [
    panelHeading('Reports', 'ranked by citations \u00b7 merge sort'),
    '<div class="reports-grid">',
    '<div>',
    `<h4 class="report__sub">Top authors <span class="report__meta">${authors.length} shown</span></h4>`,
    '<div class="table-wrap"><table class="table"><thead><tr>',
    '<th class="rank" scope="col">#</th><th scope="col">Author</th><th scope="col">Papers</th>',
    '<th class="col-count" scope="col">Citations</th><th class="col-share" scope="col">Share</th>',
    '</tr></thead><tbody>',
    authorRows.length ? authorRows.join('') : '<tr><td colspan="5">no data</td></tr>',
    '</tbody></table></div>',
    '</div>',
    '<div>',
    `<h4 class="report__sub">Citation trends <span class="report__meta">${trends.length} years</span></h4>`,
    '<div class="table-wrap"><table class="table"><thead><tr>',
    '<th scope="col">Year</th><th scope="col">Papers</th>',
    '<th class="col-count" scope="col">Citations</th><th class="col-share" scope="col">Load</th>',
    '</tr></thead><tbody>',
    trendRows.length ? trendRows.join('') : '<tr><td colspan="4">no data</td></tr>',
    '</tbody></table></div>',
    '</div>',
    '</div>',
  ].join('');
}

function renderMutationLog() {
  const log = $('#mutate-log');
  if (!log) return;
  const entries = datasets.mutations;
  log.innerHTML = entries.length
    ? entries.map((entry) => `<div class="mutate-log__line${entry.error ? ' is-error' : ''}">${escapeHtml(entry.text)}</div>`).join('')
    : '<div class="mutate-log__line">No changes in this session.</div>';
}

function renderMutateTab() {
  if (activeTab !== 'mutate') return;
  const panel = dynamicPanel('.panel--mutate', 'mutate');
  if (!panel) return;
  if (!panel.dataset.ready) {
    panel.dataset.ready = '1';
    panel.innerHTML = [
      panelHeading('Add data', 'in-memory \u00b7 discarded by reload'),
      '<div class="mutate-grid">',
      '<form class="mutate-form" id="add-paper-form">',
      '<h4 class="mutate-form__title">Paper</h4>',
      '<label class="field"><span class="field__label">ID</span>',
      '<input name="id" placeholder="P900" autocomplete="off"></label>',
      '<label class="field"><span class="field__label">Title</span>',
      '<input name="title" placeholder="Paper title" autocomplete="off"></label>',
      '<label class="field"><span class="field__label">Author</span>',
      '<input name="author" placeholder="First author" autocomplete="off"></label>',
      '<label class="field"><span class="field__label">Year</span>',
      '<input name="year" placeholder="2024" inputmode="numeric" autocomplete="off"></label>',
      '<button type="submit" class="console__btn">Add paper</button>',
      '</form>',

      '<form class="mutate-form" id="add-citation-form">',
      '<h4 class="mutate-form__title">Citation edge</h4>',
      '<label class="field"><span class="field__label">Citing paper</span>',
      '<input name="citing" placeholder="P900" autocomplete="off"></label>',
      '<label class="field"><span class="field__label">Cited paper</span>',
      '<input name="cited" placeholder="P101" autocomplete="off"></label>',
      '<button type="submit" class="console__btn">Add edge</button>',
      '</form>',
      '</div>',
      '<div class="mutate-log" id="mutate-log" aria-live="polite"></div>',
    ].join('');
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

/* ----------------------------------------------------------------- render -- */
async function render() {
  renderGraphTab();
  syncDynamicPanels();
  if (activeTab === 'search') renderSearchTab();
  if (activeTab === 'traversal') renderTraversalTab();
  if (activeTab === 'reports') await renderReportsTab();
  if (activeTab === 'mutate') renderMutateTab();
}

/* ------------------------------------------------------------- boot load -- */
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

function setRailFacts(facts) {
  const set = (id, value) => {
    const el = document.getElementById(id);
    if (el && value != null) el.textContent = value;
  };
  set('rail-papers', facts.papers);
  set('rail-edges', facts.edges);
  set('rail-docs', facts.documents);
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

/* Non-critical context for the sidebar: the dataset file and how many papers
 * actually ship a document. Failures here must never break the dashboard. */
async function loadDatasetSummary() {
  try {
    const health = await fetchJSON('/api/health');
    const csv = typeof health.csv === 'string' ? health.csv.split(/[\\/]/).pop() : null;
    if (csv) {
      const el = $('#rail-source');
      if (el) el.textContent = csv;
      const title = $('.window__source');
      if (title) title.textContent = csv;
    }
  } catch (_) { /* health is optional */ }

  try {
    const payload = await fetchJSON('/api/papers?limit=200');
    const results = payload.results || [];
    const withDocs = results.filter((p) => p.document && p.document.available).length;
    setRailFacts({ documents: `${withDocs} of ${results.length}` });
  } catch (_) { /* document counts are optional */ }
}

async function loadLineageFromShortestChain() {
  try {
    const shortest = await fetchJSON('/api/path?source=P103&target=P107');
    datasets.lineageChain = shortest.path || [];
    datasets.lineageHops = shortest.found ? shortest.hops : null;
    if (shortest.found) {
      datasets.lastRun = {
        title: `Shortest path \u00b7 P103 \u2192 P107 \u00b7 ${shortest.hops} hops`,
        note: 'BFS parent tracking \u00b7 O(V+E)',
        html: `<div class="flow">${nodeChain(shortest.path)}</div>`,
      };
    }
  } catch (_) {
    return; // the pure-Java ApiServer has no /api/path — overview still works
  }
  render();
}

/* ------------------------------------------------- user-triggered controls */
function buildQueryConsole() {
  const host = $('#query-toolbar');
  if (!host || host.querySelector('.query-console')) return;

  const form = document.createElement('form');
  form.className = 'query-console';
  form.setAttribute('aria-label', 'Citation network queries');
  form.innerHTML = `
    <fieldset class="qc-group">
      <legend class="qc-legend">Path &amp; traversal</legend>
      <input name="source" placeholder="from P103" size="9" autocomplete="off" aria-label="Source paper id">
      <span class="arrow" aria-hidden="true">&rarr;</span>
      <input name="target" placeholder="to P107" size="9" autocomplete="off" aria-label="Target paper id">
      <button type="submit" class="console__btn">Find path</button>
      <span class="qc-sep" aria-hidden="true"></span>
      <label class="qc-inline">
        <span class="qc-inline__label">Order</span>
        <select name="mode" class="console__select" title="Traversal order">
          <option value="bfs">Breadth-first</option>
          <option value="dfs">Depth-first</option>
        </select>
      </label>
      <button type="submit" class="console__btn" data-action="traverse">Traverse</button>
    </fieldset>

    <fieldset class="qc-group">
      <legend class="qc-legend">Optimal route</legend>
      <input name="route" placeholder="P101,P104,P107" size="16" autocomplete="off" aria-label="Papers to visit">
      <button type="submit" class="console__btn" data-action="route">Solve route</button>
    </fieldset>

    <fieldset class="qc-group">
      <legend class="qc-legend">Search</legend>
      <input name="query" placeholder="title or author" size="14" autocomplete="off" aria-label="Search query">
      <label class="console__check">
        <input type="checkbox" name="fuzzy">
        <span>allow typos</span>
      </label>
      <button type="submit" class="console__btn" data-action="search">Search</button>
    </fieldset>`;

  host.appendChild(form);
  form.addEventListener('submit', onSubmit);
}

function onSubmit(event) {
  event.preventDefault();
  const action = event.submitter?.dataset?.action || 'path';
  const form = event.target;
  if (action === 'search') {
    const query = form.elements.query.value.trim();
    if (!query) return statusFlash('Type a query to search for.', true);
    handleSearch(query, form.elements.fuzzy.checked);
  } else if (action === 'route') {
    const ids = form.elements.route.value.split(',').map((s) => s.trim()).filter(Boolean);
    if (ids.length >= 2) handleOptimalRoute(ids);
    else statusFlash('List at least two paper ids for the optimal route.', true);
  } else if (action === 'traverse') {
    const source = form.elements.source.value.trim().toUpperCase();
    if (!source) return statusFlash('Enter a source paper id to traverse.', true);
    handleTraverse(source, form.elements.mode.value);
  } else {
    const source = form.elements.source.value.trim().toUpperCase();
    const target = form.elements.target.value.trim().toUpperCase();
    if (source && target) handleShortestPath(source, target);
    else statusFlash('Source and target are both required.', true);
  }
}

async function handleShortestPath(source, target) {
  statusFlash(`Resolving ${source} \u2192 ${target} \u2026`);
  try {
    const result = await fetchJSON(`/api/path?source=${encodeURIComponent(source)}&target=${encodeURIComponent(target)}`);
    datasets.lineageChain = result.path;
    datasets.lineageHops = result.found ? result.hops : null;
    datasets.lastRun = result.found
      ? {
        title: `Shortest path \u00b7 ${escapeHtml(source)} \u2192 ${escapeHtml(target)} \u00b7 ${result.hops} hops`,
        note: 'BFS parent tracking \u00b7 O(V+E)',
        html: `<div class="flow">${nodeChain(result.path)}</div>`,
      }
      : {
        title: `No path \u00b7 ${escapeHtml(source)} \u2192 ${escapeHtml(target)}`,
        note: 'reachability',
        html: '<p class="flow__empty">No directed citation chain links these papers.</p>',
      };
    showTab(result.found ? 'traversal' : 'graph');
    statusFlash(`Path ${source} \u2192 ${target} \u00b7 ${result.hops} hops`);
  } catch (error) {
    statusFlash(error.message, true);
  }
}

async function handleOptimalRoute(ids) {
  statusFlash('Solving the bitmask route \u2026');
  try {
    const params = new URLSearchParams({ papers: ids.join(',') });
    const result = await fetchJSON(`/api/optimal-route?${params}`);
    datasets.lastRun = result.walk && result.walk.length
      ? {
        title: `Optimal route \u00b7 ${ids.length} papers \u00b7 ${result.hops} hops total`,
        note: 'Held\u2013Karp bitmask DP over BFS distances',
        html: `<div class="flow">${nodeChain(result.walk)}</div>`,
      }
      : {
        title: 'No valid ordering',
        note: 'Held\u2013Karp bitmask DP',
        html: '<p class="flow__empty">No directed ordering visits every requested paper.</p>',
      };
    showTab('traversal');
    statusFlash(`Optimal route \u00b7 ${result.hops} hops`);
  } catch (error) {
    statusFlash(error.message, true);
  }
}

async function handleTraverse(source, mode) {
  const label = mode === 'dfs' ? 'Depth-first' : 'Breadth-first';
  statusFlash(`${label} traversal from ${source} \u2026`);
  try {
    const params = new URLSearchParams({ source, mode });
    const result = await fetchJSON(`/api/traverse?${params}`);
    if (!result.found || !result.order.length) {
      datasets.lastRun = {
        title: `Traversal \u00b7 ${label} \u00b7 from ${escapeHtml(source)}`,
        note: 'reachability',
        html: `<p class="flow__empty">Paper ${escapeHtml(source)} is not in the graph.</p>`,
      };
      showTab('traversal');
      statusFlash(`No papers reached from ${source}.`, true);
      return;
    }
    const rows = result.order.map((node, index) => `
      <tr data-paper-id="${escapeHtml(node.id)}">
        <td class="rank" data-label="Step">${index + 1}</td>
        <td class="id" data-label="ID">${paperLink(node.id, node.id)}</td>
        <td class="title" data-label="Title">${paperLink(node.id, node.title, 'paper-link--title')}</td>
        <td data-label="Year">${escapeHtml(node.year)}</td>
        <td class="count" data-label="Citations">${node.citationCount}</td>
      </tr>`);
    datasets.lastRun = {
      title: `Traversal \u00b7 ${label} \u00b7 from ${escapeHtml(source)}`,
      note: `${result.reached} papers reached \u00b7 ${mode.toUpperCase()} visit order \u00b7 O(V+E)`,
      html: `<div class="table-wrap"><table class="table"><thead><tr>
          <th class="rank" scope="col">Step</th><th scope="col">ID</th><th scope="col">Title</th>
          <th scope="col">Year</th><th class="col-count" scope="col">Citations</th>
        </tr></thead><tbody>${rows.join('')}</tbody></table></div>`,
    };
    showTab('traversal');
    statusFlash(`${label} from ${source} \u00b7 ${result.reached} papers reached`);
  } catch (error) {
    statusFlash(error.message, true);
  }
}

async function handleSearch(query, fuzzy = false) {
  statusFlash(`Searching \u201c${query}\u201d${fuzzy ? ' (typo-tolerant)' : ''} \u2026`);
  try {
    const params = new URLSearchParams({ q: query, limit: 25 });
    if (fuzzy) params.set('fuzzy', 'true');
    const payload = await fetchJSON(`/api/papers?${params}`);
    datasets.searchQuery = query;
    datasets.searchFuzzy = fuzzy;
    datasets.searchResults = payload.results || [];
    showTab('search');
    statusFlash(`Search \u201c${query}\u201d \u00b7 ${datasets.searchResults.length} hits`);
  } catch (error) {
    statusFlash(error.message, true);
  }
}

/* ----------------------------------------------------------- mutations -- */
async function refreshAfterMutation() {
  datasets.reports = null;
  await loadOverview();
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
    statusFlash('ID, title, author and a numeric year are all required.', true);
    return;
  }
  statusFlash(`Adding paper ${body.id} \u2026`);
  try {
    const paper = await fetchJSON('/api/papers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    logMutation(`+ paper ${paper.id} \u2014 ${paper.title} (${paper.year}) \u00b7 ${paper.author}`);
    statusFlash(`Added ${paper.id} \u00b7 ${datasets.stats.papers + 1} papers in graph`);
    form.reset();
    await refreshAfterMutation();
  } catch (error) {
    logMutation(`! add paper ${body.id || '?'} failed \u2014 ${error.message}`, true);
    statusFlash(error.message, true);
  }
}

async function handleAddCitation(event) {
  event.preventDefault();
  const form = event.target;
  const citing = form.elements.citing.value.trim().toUpperCase();
  const cited = form.elements.cited.value.trim().toUpperCase();
  if (!citing || !cited) {
    statusFlash('Both citing and cited paper ids are required.', true);
    return;
  }
  statusFlash(`Linking ${citing} \u2192 ${cited} \u2026`);
  try {
    const result = await fetchJSON('/api/citations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ citing, cited }),
    });
    if (result.added) {
      logMutation(`+ edge ${result.citing} \u2192 ${result.cited} \u00b7 ${result.edges} edges total`);
      statusFlash(`Added edge ${result.citing} \u2192 ${result.cited} \u00b7 ${result.edges} edges`);
      form.reset();
    } else {
      logMutation(`\u00b7 edge ${result.citing} \u2192 ${result.cited} already existed`);
      statusFlash(`Edge ${result.citing} \u2192 ${result.cited} already existed`);
    }
    await refreshAfterMutation();
  } catch (error) {
    logMutation(`! edge ${citing} \u2192 ${cited} failed \u2014 ${error.message}`, true);
    statusFlash(error.message, true);
  }
}

/* ==========================================================================
   PAPER VIEWER — #/paper/<id>
   The document is the focus; the surrounding chrome stays deliberately quiet.
   ======================================================================== */
const PAPER_ROUTE = /^#\/paper\/([^/?#]+)/;
let paperRequestId = 0;

function parseRoute() {
  const match = PAPER_ROUTE.exec(window.location.hash || '');
  if (!match) return { name: 'dashboard' };
  return { name: 'paper', id: decodeURIComponent(match[1]).trim().toUpperCase() };
}

function parseRecordText(text) {
  const match = /(?:^|\n)\s*Abstract\s*:?\s*\n?/i.exec(text);
  if (!match) return { abstract: '' };
  return { abstract: text.slice(match.index + match[0].length).trim() };
}

function renderPaperDetail(paper) {
  $('#paper-view-id').textContent = paper.id;
  $('#paper-view-title').textContent = paper.title || '(untitled)';
  $('#paper-view-authors').textContent = paper.author || '';

  $('#paper-view-facts').innerHTML = [
    `<div><dt>Year</dt><dd>${escapeHtml(paper.year)}</dd></div>`,
    `<div><dt>Citations</dt><dd>${paper.citationCount}</dd></div>`,
    `<div><dt>Cites</dt><dd>${(paper.cites || []).length}</dd></div>`,
    `<div><dt>Cited by</dt><dd>${(paper.citedBy || []).length}</dd></div>`,
  ].join('');

  const doc = paper.document || {};
  const actions = [];
  if (doc.pdfUrl) {
    actions.push(`<a class="btn btn--primary" href="${API_BASE}${doc.pdfUrl}" target="_blank" rel="noopener">Open PDF in new tab</a>`);
  }
  if (doc.textUrl) {
    actions.push(`<a class="btn btn--ghost" href="${API_BASE}${doc.textUrl}" target="_blank" rel="noopener">Open text record</a>`);
  }
  $('#paper-view-actions').innerHTML = actions.join('');

  const relations = [];
  const chipRow = (label, ids) => {
    if (!ids || !ids.length) return '';
    return `<div class="rel-row">
        <span class="rel-row__label">${label}</span>
        <span class="rel-row__chips">${ids.slice(0, 30).map((id) => paperLink(id, id, 'chip')).join('')}</span>
      </div>`;
  };
  relations.push(chipRow('Cites', paper.cites));
  relations.push(chipRow('Cited by', paper.citedBy));
  $('#paper-view-abstract').innerHTML = relations.join('');
}

async function loadPaperText(paper, requestId) {
  const doc = paper.document || {};
  // An abstract only adds information when the PDF is the main document; for
  // text-only papers the record itself already contains it.
  if (!doc.textUrl || !doc.pdfUrl) return;
  try {
    const response = await fetch(API_BASE + doc.textUrl);
    if (!response.ok) return;
    const text = await response.text();
    if (requestId !== paperRequestId) return;
    const { abstract } = parseRecordText(text);
    if (!abstract) return;
    const host = $('#paper-view-abstract');
    if (host) {
      host.insertAdjacentHTML('afterbegin', `
        <details class="abstract" open>
          <summary>Abstract</summary>
          <p>${escapeHtml(abstract)}</p>
        </details>`);
    }
  } catch (_) { /* the abstract is a bonus; the document still renders */ }
}

async function loadPaperDocument(paper, requestId) {
  const host = $('#paper-view-doc');
  const doc = paper.document || {};
  const title = escapeHtml(paper.title || paper.id);

  if (doc.pdfUrl) {
    host.innerHTML = `
      <iframe class="doc-frame" src="${API_BASE}${doc.pdfUrl}" title="PDF \u2014 ${title}"></iframe>
      <p class="doc-state doc-state--hint">
        If the PDF does not display, <a href="${API_BASE}${doc.pdfUrl}" target="_blank" rel="noopener">open it in a new tab</a>.
      </p>`;
    return;
  }

  if (doc.textUrl) {
    try {
      const response = await fetch(API_BASE + doc.textUrl);
      if (!response.ok) throw new Error(`The text record returned ${response.status}.`);
      const text = await response.text();
      if (requestId !== paperRequestId) return;
      host.innerHTML = `<pre class="doc-text">${escapeHtml(text)}</pre>`;
    } catch (error) {
      if (requestId !== paperRequestId) return;
      host.innerHTML = `<p class="doc-state doc-state--error">${escapeHtml(error.message)}</p>`;
    }
    return;
  }

  host.innerHTML = `
    <p class="doc-state">
      No document file is on disk for this paper. The citation graph still holds
      its metadata and edges &mdash; only the paper file is missing from
      <code>research_papers/</code>.
    </p>`;
}

async function openPaper(id) {
  const requestId = ++paperRequestId;
  const status = $('#paper-view-status');
  if (status) status.textContent = '';

  $('#paper-view-id').textContent = id;
  $('#paper-view-title').textContent = 'Loading \u2026';
  $('#paper-view-authors').textContent = '';
  $('#paper-view-facts').innerHTML = '';
  $('#paper-view-actions').innerHTML = '';
  $('#paper-view-abstract').innerHTML = '';
  $('#paper-view-doc').innerHTML = '<p class="doc-state">Loading document \u2026</p>';

  try {
    const paper = await fetchJSON(`/api/papers/${encodeURIComponent(id)}`);
    if (requestId !== paperRequestId) return;
    renderPaperDetail(paper);
    if (status) status.textContent = `${paper.citationCount} citations`;
    await loadPaperText(paper, requestId);
    await loadPaperDocument(paper, requestId);
  } catch (error) {
    if (requestId !== paperRequestId) return;
    $('#paper-view-title').textContent = `Paper ${id} could not be opened`;
    $('#paper-view-actions').innerHTML = '';
    $('#paper-view-doc').innerHTML = `<p class="doc-state doc-state--error">${escapeHtml(error.message)}</p>`;
    statusFlash(error.message, true);
  }
}

function applyRoute() {
  const route = parseRoute();
  const isPaper = route.name === 'paper';
  const dashboard = $('#dashboard-view');
  const viewer = $('#paper-view');
  if (dashboard) dashboard.hidden = isPaper;
  if (viewer) viewer.hidden = !isPaper;
  window.scrollTo(0, 0);

  if (isPaper) {
    openPaper(route.id);
  } else {
    paperRequestId += 1; // cancel any in-flight paper load
    const title = $('#paper-view-title');
    if (title) title.textContent = '';
  }
}

/* Any element carrying data-paper-id opens that paper. Real <a> elements keep
 * their native keyboard/context-menu behaviour; table rows get the same
 * destination for pointer users without hijacking the link. */
function wirePaperClickDelegation() {
  document.addEventListener('click', (event) => {
    if (event.target.closest('a')) return; // the anchor already navigates
    const target = event.target.closest('[data-paper-id]');
    if (!target) return;
    window.location.hash = `#/paper/${encodeURIComponent(target.getAttribute('data-paper-id'))}`;
  });
}

/* ------------------------------------------------------- connection help -- */
function buildConnectionHelp(applied, actual) {
  const meta = $('.window__foot-meta');
  if (!meta || $('#api-help-panel')) return;

  const details = document.createElement('details');
  details.className = 'connection-help';
  details.id = 'api-help-panel';
  details.innerHTML = `
    <summary class="link-quiet">connection help</summary>
    <div class="connection-help__body">
      <p>
        This dashboard is using the API at <code>${escapeHtml(API_BASE)}</code>${actual ? ` (<span class="connection-help__tier">${escapeHtml(actual)}</span>)` : ''}.
      </p>
      <p>
        Point it somewhere else without editing the source: set
        <code>window.CERBERUS_API_URL</code> in <code>config.js</code> at deploy
        time, or save a URL below for this browser only.
      </p>
      <form class="connection-help__form">
        <input name="api" type="url" placeholder="https://cerberus-api.example.edu" aria-label="API base URL"
               value="${escapeHtml(localStorage.getItem('cerberusApiBase') || '')}">
        <button type="submit" class="console__btn">Save</button>
        <button type="button" class="console__btn console__btn--ghost" data-action="reset">Reset</button>
      </form>
      <p class="connection-help__note">Saving reloads the dashboard.</p>
    </div>`;

  details.querySelector('form').addEventListener('submit', (event) => {
    event.preventDefault();
    const url = event.target.elements.api.value.trim();
    if (url) localStorage.setItem('cerberusApiBase', url.replace(/\/+$/, ''));
    else localStorage.removeItem('cerberusApiBase');
    window.location.reload();
  });
  details.querySelector('[data-action="reset"]').addEventListener('click', () => {
    localStorage.removeItem('cerberusApiBase');
    window.location.reload();
  });

  meta.appendChild(details);
}

/* ------------------------------------------------------------- boot ---- */
function describeApiSource() {
  if (new URLSearchParams(window.location.search).get('api')) return 'from ?api= parameter';
  if ((localStorage.getItem('cerberusApiBase') || '').trim()) return 'saved in this browser';
  if ((window.CERBERUS_API_URL || '').trim()) return 'from config.js';
  if (window.location.protocol === 'http:' || window.location.protocol === 'https:') return 'same origin as this page';
  return 'local development default';
}

async function boot() {
  const apiEl = $('#api-base');
  if (apiEl) apiEl.textContent = API_BASE;

  buildQueryConsole();
  wireTabs();
  syncTabHighlight();
  wirePaperClickDelegation();
  buildConnectionHelp(API_BASE, describeApiSource());
  window.addEventListener('hashchange', applyRoute);

  try {
    await loadOverview();
    await loadDatasetSummary();
    await loadLineageFromShortestChain();
    statusFlash('engine ready');
  } catch (error) {
    statusFlash(error.message, true);
    renderGraphTab(); // paint the cached/zeroed state so the page still reads
  }
  applyRoute();
}

document.addEventListener('DOMContentLoaded', boot);
