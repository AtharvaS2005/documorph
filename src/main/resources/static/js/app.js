(function () {
  'use strict';

  const app = document.getElementById('app');

  function esc(v) {
    return String(v == null ? '' : v)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function inferPage() {
    return location.pathname.split('/').pop().replace(/\.html$/i, '') || 'index';
  }
  const PAGE = inferPage();

  var LOGO_SVG =
    '<svg class="brand-mark" viewBox="0 0 32 32" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'
    + '<defs>'
    +   '<linearGradient id="dmdoc" x1="0" y1="0" x2="1" y2="1">'
    +     '<stop offset="0%" stop-color="#3C4157"/><stop offset="100%" stop-color="#262A3A"/></linearGradient>'
    +   '<linearGradient id="dmring" x1="0" y1="0" x2="1" y2="1">'
    +     '<stop offset="0%" stop-color="#2DD4BF"/><stop offset="100%" stop-color="#0D9488"/></linearGradient>'
    + '</defs>'
    + '<rect x="2.5" y="9" width="16" height="20" rx="3" fill="url(#dmdoc)"/>'
    + '<g fill="#fff">'
    +   '<rect x="5.5" y="13" width="7" height="2.2" rx="1.1" opacity=".9"/>'
    +   '<rect x="5.5" y="17.5" width="5.2" height="2.2" rx="1.1" opacity=".62"/>'
    +   '<rect x="5.5" y="22" width="6.2" height="2.2" rx="1.1" opacity=".4"/>'
    + '</g>'
    + '<path d="M24.6 11.4 L28.4 7.6" stroke="#262A3A" stroke-width="4.4" stroke-linecap="round"/>'
    + '<circle cx="29.4" cy="6.6" r="2.1" fill="#262A3A"/>'
    + '<circle cx="20" cy="16" r="7" fill="#E4F2EE"/>'
    + '<circle cx="20" cy="16" r="7" fill="none" stroke="url(#dmring)" stroke-width="2.8"/>'
    + '<g fill="#262A3A">'
    +   '<rect x="16" y="13.9" width="8" height="2.3" rx="1.15"/>'
    +   '<rect x="16" y="17.9" width="5.4" height="2.3" rx="1.15"/>'
    + '</g></svg>';

  const PAGE_TITLES = {
    index: 'DocuMorph',
    'sign-in': 'Sign In',
    'sign-up': 'Sign Up',
    setup: 'Complete Setup',
    home: 'Home',
    upload: 'New Session',
    processing: 'Processing',
    results: 'Results',
    activity: 'Activity',
    search: 'Search',
    categories: 'Categories',
    records: 'Record Sets',
    'org-forum': 'Forum',
    terms: 'Terms of Service',
    guide: 'Usage Guide'
  };
  if (PAGE_TITLES[PAGE]) document.title = PAGE_TITLES[PAGE] + ' \u2014 DocuMorph';

  /* ── Toast ───────────────────────────────────────────── */
  function showToast(msg, type) {
    type = type || 'info';
    var container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      document.body.appendChild(container);
    }
    var t = document.createElement('div');
    t.className = 'toast toast--' + type;
    t.textContent = msg;
    container.appendChild(t);
    requestAnimationFrame(function () { t.classList.add('toast--visible'); });
    setTimeout(function () {
      t.classList.remove('toast--visible');
      setTimeout(function () { t.remove(); }, 350);
    }, 3800);
  }

  /* ── Date / size helpers ─────────────────────────────── */
  function relativeDate(iso) {
    var diff = Date.now() - new Date(iso).getTime();
    var s = Math.floor(diff / 1000);
    if (s < 60) return 'just now';
    var m = Math.floor(s / 60);
    if (m < 60) return m + 'm ago';
    var h = Math.floor(m / 60);
    if (h < 24) return h + 'h ago';
    var d = Math.floor(h / 24);
    if (d < 30) return d + 'd ago';
    return formatDate(iso);
  }

  function formatDate(iso) {
    return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  function formatSize(bytes) {
    if (bytes == null) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }

  /* ── State HTML helpers ──────────────────────────────── */
  function loadingHTML(n) {
    n = n || 3;
    var html = '<div class="skeleton-list">';
    for (var i = 0; i < n; i++) html += '<div class="skeleton-item"><div class="skeleton"></div></div>';
    return html + '</div>';
  }

  function errorHTML(msg) {
    return '<div class="state-panel state-panel--error"><span class="state-icon">&#9888;</span><p class="state-msg">' + esc(msg) + '</p></div>';
  }

  function emptyHTML(icon, title, msg) {
    return '<div class="state-panel state-panel--empty"><span class="state-icon">' + icon + '</span><p class="state-title">' + esc(title) + '</p><p class="state-msg">' + esc(msg) + '</p></div>';
  }

  function statusChipHTML(status) {
    var map = { QUEUED: 'chip--queued', PROCESSING: 'chip--processing', COMPLETED: 'chip--completed', PARTIAL_FAILURE: 'chip--partial', FAILED: 'chip--failed' };
    return '<span class="chip ' + (map[status] || 'chip--default') + '">' + esc(status) + '</span>';
  }

  /* ── ensureMe ────────────────────────────────────────── */
  async function ensureMe() {
    var me = window.api.getMe();
    if (me) return me;
    try {
      me = await window.api.get('/users/me');
      window.api.setMe(me);
    } catch (e) { /* non-fatal */ }
    return me;
  }

  /* ── Password toggle ─────────────────────────────────── */
  function bindPasswordToggles(root) {
    (root || document).querySelectorAll('[data-pw-toggle]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var input = document.getElementById(btn.getAttribute('data-pw-toggle'));
        if (!input) return;
        var show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        btn.textContent = show ? 'Hide' : 'Show';
      });
    });
  }

  /* ═══════════════════════════════════════════════════════
     SHELL
  ═══════════════════════════════════════════════════════ */
  function renderShell(me) {
    var nav = [
      { href: 'home.html',       icon: '&#8962;',  label: 'Home'       },
      { href: 'upload.html',     icon: '&#8679;',  label: 'Upload'     },
      { href: 'search.html',     icon: '&#8981;',  label: 'Search'     },
      { href: 'activity.html',   icon: '&#9776;',  label: 'Activity'   },
      { href: 'categories.html', icon: '&#9965;',  label: 'Categories' },
      { href: 'records.html',    icon: '&#9783;',  label: 'Records'    },
      { href: 'org-forum.html',  icon: '&#9825;',  label: 'Forum'      }
    ];
    var navItems = nav.map(function (n) {
      var active = location.pathname.endsWith(n.href) ? ' nav-pill--active' : '';
      return '<a href="' + n.href + '" class="nav-pill' + active + '">'
        + '<span class="nav-ico">' + n.icon + '</span>'
        + '<span class="nav-label">' + esc(n.label) + '</span></a>';
    }).join('');

    var email = me ? me.email : '';
    var orgName = (me && me.organizationName) ? me.organizationName : '';

    app.innerHTML =
      '<div class="app">'
      + '<header class="appbar">'
      +   '<div class="appbar-row">'
      +     '<a href="home.html" class="brand">' + LOGO_SVG + '<span class="brand-name">DocuMorph</span></a>'
      +     '<nav class="nav-rail">' + navItems + '</nav>'
      +     '<div class="appbar-right">'
      +       '<div class="topbar-actions" id="topbar-actions"></div>'
      +       '<button class="user-pill" id="user-pill" aria-label="Account menu">'
      +         '<span class="user-pill-bars">&#9776;</span>'
      +         '<span class="user-avatar"></span>'
      +       '</button>'
      +     '</div>'
      +   '</div>'
      + '</header>'
      + '<div class="user-menu" id="user-menu" hidden>'
      +   '<div class="user-menu-head"><span class="user-email"></span><span class="user-org"></span></div>'
      +   '<a href="guide.html" class="user-menu-item">Usage Guide</a>'
      +   '<a href="terms.html" class="user-menu-item">Terms of Service</a>'
      +   '<button class="user-menu-item" id="signout-btn">Sign out</button>'
      + '</div>'
      + '<main class="app-main" id="main-content">'
      +   '<div class="page-head"><h1 class="page-title">' + esc(PAGE_TITLES[PAGE] || '') + '</h1></div>'
      +   '<div class="main-inner"></div>'
      + '</main>'
      + '</div>';

    var avatarEl = app.querySelector('.user-avatar');
    var emailEl  = app.querySelector('.user-email');
    var orgEl    = app.querySelector('.user-org');
    if (avatarEl) avatarEl.textContent = email ? email.charAt(0).toUpperCase() : '?';
    if (emailEl)  emailEl.textContent  = email;
    if (orgEl)    orgEl.textContent    = orgName || 'Personal workspace';

    var pill = document.getElementById('user-pill');
    var menu = document.getElementById('user-menu');
    if (pill && menu) {
      pill.addEventListener('click', function (e) {
        e.stopPropagation();
        menu.hidden = !menu.hidden;
      });
      document.addEventListener('click', function (e) {
        if (!menu.hidden && !menu.contains(e.target)) menu.hidden = true;
      });
      document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') menu.hidden = true;
      });
    }


    var soBtn = document.getElementById('signout-btn');
    if (soBtn) soBtn.addEventListener('click', function () {
      window.api.clearSession();
      location.href = 'sign-in.html';
    });
  }

  function mainContent() {
    return document.querySelector('.main-inner') || app;
  }

  /* ═══════════════════════════════════════════════════════
     LANDING PAGE
  ═══════════════════════════════════════════════════════ */
  function landingPage() {
    app.innerHTML =
      '<div class="landing">'

      /* ── Header ── */
      + '<header class="lp-header">'
      +   '<div class="landing-brand">' + LOGO_SVG + '<span class="brand-name">DocuMorph</span></div>'
      +   '<nav class="lp-nav">'
      +     '<a href="#how-it-works" class="lp-nav-link">How it works</a>'
      +     '<a href="#features" class="lp-nav-link">Features</a>'
      +     '<a href="sign-in.html" class="btn btn--ghost">Sign In</a>'
      +     '<a href="sign-up.html" class="btn btn--primary">Get Started Free</a>'
      +   '</nav>'
      + '</header>'

      /* ── Hero ── */
      + '<section class="lp-hero">'
      +   '<div class="lp-hero-left">'
      +     '<div class="lp-eyebrow">Intelligent Document Processing</div>'
      +     '<h1 class="lp-title">Turn any document<br>into <span class="gradient-word">structured data</span></h1>'
      +     '<p class="lp-sub">Upload PDFs, Word docs, scanned images and more. DocuMorph extracts text and structured JSON automatically — at scale, in seconds.</p>'
      +     '<div class="lp-hero-cta">'
      +       '<a href="sign-up.html" class="btn btn--primary btn--lg">Start for free</a>'
      +       '<a href="sign-in.html" class="btn btn--ghost btn--lg">Sign in</a>'
      +     '</div>'
      +     '<div class="lp-proof">'
      +       '<span>&#10003; No credit card</span>'
      +       '<span>&#10003; Batch processing</span>'
      +       '<span>&#10003; Team collaboration</span>'
      +     '</div>'
      +   '</div>'
      +   '<div class="lp-hero-right">'
      +     '<div class="hero-mockup">'
      +       '<div class="hm-bar"><div class="hm-dots"><i></i><i></i><i></i></div><span>Processing Session</span></div>'
      +       '<div class="hm-body">'
      +         '<div class="hm-progress"><div class="hmp-meta"><span>Overall Progress</span><span>2 / 4 files</span></div><div class="hmp-track"><div class="hmp-fill" style="width:52%"></div></div></div>'
      +         '<div class="hm-files">'
      +           '<div class="hm-file hm-file--done"><span class="hm-ftype">PDF</span><span class="hm-fname">invoice_jan.pdf</span><span class="hm-fchip hm-fchip--done">Done</span></div>'
      +           '<div class="hm-file hm-file--done"><span class="hm-ftype">DOC</span><span class="hm-fname">contract_v2.docx</span><span class="hm-fchip hm-fchip--done">Done</span></div>'
      +           '<div class="hm-file hm-file--active"><span class="hm-ftype">IMG</span><span class="hm-fname">scan_page1.jpg</span><span class="hm-fchip hm-fchip--proc">Processing</span></div>'
      +           '<div class="hm-file"><span class="hm-ftype">TXT</span><span class="hm-fname">notes_raw.txt</span><span class="hm-fchip hm-fchip--queue">Queued</span></div>'
      +         '</div>'
      +       '</div>'
      +     '</div>'
      +   '</div>'
      + '</section>'

      /* ── How it works ── */
      + '<section class="lp-how" id="how-it-works">'
      +   '<div class="lp-inner">'
      +     '<div class="lp-section-tag">How it works</div>'
      +     '<h2 class="lp-section-title">From upload to insight in three steps</h2>'
      +     '<div class="lp-steps">'

      /* step 1 */
      +       '<div class="lp-step">'
      +         '<div class="lp-step-n">01</div>'
      +         '<div class="lp-step-visual">'
      +           '<div class="sv-upload">'
      +             '<div class="sv-dropzone"><span class="sv-up-icon">&#8679;</span><span>Drag files here</span></div>'
      +             '<div class="sv-frow"><span class="sv-ftype">PDF</span><span class="sv-fname">annual_report.pdf</span><span class="sv-fsize">4.2 MB</span></div>'
      +             '<div class="sv-frow"><span class="sv-ftype">DOC</span><span class="sv-fname">contract_2026.docx</span><span class="sv-fsize">1.8 MB</span></div>'
      +             '<div class="sv-frow"><span class="sv-ftype">JPG</span><span class="sv-fname">scan_receipt.jpg</span><span class="sv-fsize">780 KB</span></div>'
      +           '</div>'
      +         '</div>'
      +         '<h3>Upload your documents</h3>'
      +         '<p>Drag and drop PDFs, DOCX, images, or plain text. Batch process hundreds at once or run single sessions with custom options.</p>'
      +       '</div>'

      + '<div class="lp-step-connector"><div class="lp-step-arrow"></div></div>'

      /* step 2 */
      +       '<div class="lp-step">'
      +         '<div class="lp-step-n">02</div>'
      +         '<div class="lp-step-visual">'
      +           '<div class="sv-proc">'
      +             '<div class="sv-proc-bar"><div class="sv-proc-fill"></div></div>'
      +             '<div class="sv-prow"><span class="sv-dot sv-dot--green"></span><span>invoice_jan.pdf</span><span class="sv-pill sv-pill--green">Done</span></div>'
      +             '<div class="sv-prow"><span class="sv-dot sv-dot--blue sv-dot--pulse"></span><span>contract.docx</span><span class="sv-pill sv-pill--blue">Processing</span></div>'
      +             '<div class="sv-prow"><span class="sv-dot"></span><span>scan_001.jpg</span><span class="sv-pill">Queued</span></div>'
      +           '</div>'
      +         '</div>'
      +         '<h3>Extraction runs automatically</h3>'
      +         '<p>The pipeline extracts text and key fields in parallel — with live progress tracking per file. Zero configuration required.</p>'
      +       '</div>'

      + '<div class="lp-step-connector"><div class="lp-step-arrow"></div></div>'

      /* step 3 */
      +       '<div class="lp-step">'
      +         '<div class="lp-step-n">03</div>'
      +         '<div class="lp-step-visual">'
      +           '<div class="sv-results">'
      +             '<div class="sv-rtabs"><span class="sv-rtab sv-rtab--active">Text</span><span class="sv-rtab">JSON</span></div>'
      +             '<div class="sv-rrows">'
      +               '<div class="sv-rrow"><span class="sv-rkey">Invoice #</span><span class="sv-rval">INV-2024-0891</span></div>'
      +               '<div class="sv-rrow"><span class="sv-rkey">Date</span><span class="sv-rval">15 Jan 2026</span></div>'
      +               '<div class="sv-rrow"><span class="sv-rkey">Amount</span><span class="sv-rval">$4,250.00</span></div>'
      +               '<div class="sv-rrow"><span class="sv-rkey">Vendor</span><span class="sv-rval">Acme Corp Ltd</span></div>'
      +             '</div>'
      +           '</div>'
      +         '</div>'
      +         '<h3>Review clean results</h3>'
      +         '<p>Browse full extracted text or structured JSON per file. Share findings with your team using the built-in forum.</p>'
      +       '</div>'

      +     '</div>'
      +   '</div>'
      + '</section>'

      /* ── Feature cards ── */
      + '<section class="lp-features" id="features">'
      +   '<div class="lp-inner">'
      +     '<div class="lp-section-tag">Features</div>'
      +     '<h2 class="lp-section-title">Everything your team needs</h2>'
      +     '<div class="lp-feat-grid">'

      /* feat 1 — wide */
      +       '<div class="lp-feat lp-feat--wide">'
      +         '<div class="lp-feat-text">'
      +           '<div class="lp-feat-icon">&#9889;</div>'
      +           '<h3>Batch &amp; Category Runs</h3>'
      +           '<p>Process hundreds of files in one session. Group documents into categories and re-run extractions as your templates evolve.</p>'
      +         '</div>'
      +         '<div class="lp-feat-visual">'
      +           '<div class="fv-batch">'
      +             '<div class="fv-row"><span class="fv-label">Q4 Invoices</span><div class="fv-track"><div class="fv-fill fv-fill--100"></div></div><span class="fv-pill fv-pill--green">Done</span></div>'
      +             '<div class="fv-row"><span class="fv-label">Contracts</span><div class="fv-track"><div class="fv-fill fv-fill--100"></div></div><span class="fv-pill fv-pill--green">Done</span></div>'
      +             '<div class="fv-row"><span class="fv-label">Receipts</span><div class="fv-track"><div class="fv-fill fv-fill--anim"></div></div><span class="fv-pill fv-pill--blue">Processing</span></div>'
      +             '<div class="fv-row"><span class="fv-label">Reports</span><div class="fv-track"><div class="fv-fill fv-fill--0"></div></div><span class="fv-pill">Queued</span></div>'
      +           '</div>'
      +         '</div>'
      +       '</div>'

      /* feat 2 */
      +       '<div class="lp-feat">'
      +         '<div class="lp-feat-icon">&#123; &#125;</div>'
      +         '<h3>Structured JSON Output</h3>'
      +         '<p>Get clean, schema-ready JSON alongside full text extraction — piped directly into your systems.</p>'
      +         '<div class="fv-code">'
      +           '<div class="fv-code-line"><span class="fv-key">"invoice_no":</span> <span class="fv-str">"INV-0891"</span></div>'
      +           '<div class="fv-code-line"><span class="fv-key">"amount":</span> <span class="fv-num">4250.00</span></div>'
      +           '<div class="fv-code-line"><span class="fv-key">"currency":</span> <span class="fv-str">"USD"</span></div>'
      +           '<div class="fv-code-line"><span class="fv-key">"vendor":</span> <span class="fv-str">"Acme Corp"</span></div>'
      +           '<div class="fv-code-line"><span class="fv-key">"due_date":</span> <span class="fv-str">"2026-02-15"</span></div>'
      +         '</div>'
      +       '</div>'

      /* feat 3 */
      +       '<div class="lp-feat">'
      +         '<div class="lp-feat-icon">&#128172;</div>'
      +         '<h3>Team Forum</h3>'
      +         '<p>Organisation accounts get a built-in discussion layer — attach posts to sessions and coordinate document review.</p>'
      +         '<div class="fv-forum">'
      +           '<div class="fv-post"><span class="fv-avatar">JD</span><div class="fv-post-body"><strong>Q4 batch complete</strong><p>47 invoices extracted. 3 flagged for review.</p></div></div>'
      +           '<div class="fv-post"><span class="fv-avatar fv-avatar--2">SK</span><div class="fv-post-body"><strong>Custom prompt worked</strong><p>Clause detection running cleanly now.</p></div></div>'
      +         '</div>'
      +       '</div>'

      /* feat 4 */
      +       '<div class="lp-feat">'
      +         '<div class="lp-feat-icon">&#128193;</div>'
      +         '<h3>Categories &amp; Records</h3>'
      +         '<p>Organise sessions into named categories. Build reusable record sets from extracted fields for downstream use.</p>'
      +         '<div class="fv-cats">'
      +           '<div class="fv-cat"><span class="fv-cat-dot fv-cat-dot--blue"></span><span>Invoices</span><span class="fv-cat-n">128 docs</span></div>'
      +           '<div class="fv-cat"><span class="fv-cat-dot fv-cat-dot--purple"></span><span>Contracts</span><span class="fv-cat-n">64 docs</span></div>'
      +           '<div class="fv-cat"><span class="fv-cat-dot fv-cat-dot--green"></span><span>Reports</span><span class="fv-cat-n">47 docs</span></div>'
      +           '<div class="fv-cat"><span class="fv-cat-dot fv-cat-dot--amber"></span><span>Receipts</span><span class="fv-cat-n">203 docs</span></div>'
      +         '</div>'
      +       '</div>'

      +     '</div>'
      +   '</div>'
      + '</section>'

      /* ── CTA ── */
      + '<section class="lp-cta-section">'
      +   '<div class="lp-cta-inner">'
      +     '<h2>Ready to process your first document?</h2>'
      +     '<p>Free to start. Works on PDFs, Word docs, images, and plain text.</p>'
      +     '<div class="lp-cta-btns">'
      +       '<a href="sign-up.html" class="btn btn--primary btn--lg">Create free account</a>'
      +       '<a href="sign-in.html" class="btn btn--ghost btn--lg">Sign in to existing account</a>'
      +     '</div>'
      +   '</div>'
      + '</section>'

      /* ── Footer ── */
      +   '<div class="lp-footer-inner">'
      +     '<div class="landing-brand">' + LOGO_SVG + '<span class="brand-name">DocuMorph</span></div>'
      +     '<nav class="lp-footer-nav">'
      +       '<a href="guide.html" class="lp-nav-link">Usage Guide</a>'
      +       '<a href="terms.html" class="lp-nav-link">Terms of Service</a>'
      +     '</nav>'
      +     '<p class="lp-footer-copy">&#169; 2026 DocuMorph &mdash; Intelligent Document Processing Platform</p>'
      +   '</div>'

    app.querySelectorAll('a[href^="#"]').forEach(function (a) {
      a.addEventListener('click', function (e) {
        e.preventDefault();
        var target = document.querySelector(a.getAttribute('href'));
        if (target) target.scrollIntoView({ behavior: 'smooth' });
      });
    });
  }

  /* ═══════════════════════════════════════════════════════
     SIGN-IN PAGE
  ═══════════════════════════════════════════════════════ */
  function signInPage() {
    app.innerHTML =
      '<div class="auth-layout">'
      + '<div class="auth-showcase"><div class="auth-showcase-inner">'
      +   '<span class="brand-mark-lg">' + LOGO_SVG + '</span>'
      +   '<h2>Welcome back</h2><p>Continue where you left off.</p>'
      + '</div></div>'
      + '<div class="auth-form-wrap"><div class="auth-form-inner">'
      +   '<h1 class="auth-title">Sign In</h1>'
      +   '<form id="signin-form" novalidate>'
      +     '<div class="form-field"><label for="email">Email</label>'
      +       '<input type="email" id="email" name="email" required autocomplete="email" /></div>'
      +     '<div class="form-field"><label for="password">Password</label>'
      +       '<div class="input-with-action">'
      +         '<input type="password" id="password" name="password" required autocomplete="current-password" />'
      +         '<button type="button" class="btn-link" data-pw-toggle="password">Show</button>'
      +       '</div></div>'
      +     '<div id="signin-error" class="form-error" hidden></div>'
      +     '<button type="submit" class="btn btn--primary btn--full" id="signin-submit">Sign In</button>'
      +   '</form>'
      +   '<p class="auth-switch">No account? <a href="sign-up.html">Sign Up</a></p>'
      + '</div></div>'
      + '</div>';

    bindPasswordToggles(app);

    document.getElementById('signin-form').addEventListener('submit', async function (e) {
      e.preventDefault();
      var btn = document.getElementById('signin-submit');
      var errEl = document.getElementById('signin-error');
      var email = document.getElementById('email').value.trim();
      var password = document.getElementById('password').value;
      errEl.hidden = true;
      btn.disabled = true;
      btn.textContent = 'Signing in\u2026';
      try {
        var res = await window.api.post('/auth/login', { email: email, password: password });
        window.api.setToken(res.token);
        window.api.setMe({ userId: res.userId, email: res.email, userType: res.userType, workspaceId: res.workspaceId, organizationId: res.organizationId });
        location.href = 'home.html';
      } catch (err) {
        errEl.textContent = err.message;
        errEl.hidden = false;
        btn.disabled = false;
        btn.textContent = 'Sign In';
      }
    });
  }

  /* ═══════════════════════════════════════════════════════
     SIGN-UP PAGE
  ═══════════════════════════════════════════════════════ */
  function signUpPage() {
    app.innerHTML =
      '<div class="auth-layout">'
      + '<div class="auth-showcase"><div class="auth-showcase-inner">'
      +   '<span class="brand-mark-lg">' + LOGO_SVG + '</span>'
      +   '<h2>Get started free</h2><p>Create your account in seconds.</p>'
      + '</div></div>'
      + '<div class="auth-form-wrap"><div class="auth-form-inner">'
      +   '<h1 class="auth-title">Create Account</h1>'
      +   '<form id="signup-form" novalidate>'
      +     '<div class="form-field"><label for="email">Email</label>'
      +       '<input type="email" id="email" name="email" required autocomplete="email" /></div>'
      +     '<div class="form-field"><label for="password">Password</label>'
      +       '<div class="input-with-action">'
      +         '<input type="password" id="password" name="password" required autocomplete="new-password" />'
      +         '<button type="button" class="btn-link" data-pw-toggle="password">Show</button>'
      +       '</div></div>'
      +     '<div id="signup-error" class="form-error" hidden></div>'
      +     '<button type="submit" class="btn btn--primary btn--full" id="signup-submit">Create Account</button>'
      +   '</form>'
      +   '<p class="auth-switch">Already have an account? <a href="sign-in.html">Sign In</a></p>'
      + '</div></div>'
      + '</div>';

    bindPasswordToggles(app);

    document.getElementById('signup-form').addEventListener('submit', async function (e) {
      e.preventDefault();
      var btn = document.getElementById('signup-submit');
      var errEl = document.getElementById('signup-error');
      var email = document.getElementById('email').value.trim();
      var password = document.getElementById('password').value;
      errEl.hidden = true;
      btn.disabled = true;
      btn.textContent = 'Creating account\u2026';
      try {
        var res = await window.api.post('/auth/signup', { email: email, password: password });
        sessionStorage.setItem('dm_pending_user_id', String(res.userId));
        sessionStorage.setItem('dm_pending_email', email);
        location.href = 'setup.html';
      } catch (err) {
        errEl.textContent = err.message;
        errEl.hidden = false;
        btn.disabled = false;
        btn.textContent = 'Create Account';
      }
    });
  }

  /* ═══════════════════════════════════════════════════════
     SETUP PAGE
  ═══════════════════════════════════════════════════════ */
  function setupPage() {
    app.innerHTML =
      '<div class="auth-layout">'
      + '<div class="auth-showcase"><div class="auth-showcase-inner">'
      +   '<span class="brand-mark-lg">' + LOGO_SVG + '</span>'
      +   '<h2>One more step</h2><p>Tell us how you plan to use DocuMorph.</p>'
      + '</div></div>'
      + '<div class="auth-form-wrap"><div class="auth-form-inner">'
      +   '<h1 class="auth-title">Complete Setup</h1>'
      +   '<form id="setup-form" novalidate>'
      +     '<div class="form-field"><label for="userType">Account Type</label>'
      +       '<select id="userType">'
      +         '<option value="SOLO">Solo \u2014 personal use</option>'
      +         '<option value="ORGANIZATION">Organization \u2014 team use</option>'
      +       '</select></div>'
      +     '<div id="org-fields" hidden>'
      +       '<div class="form-field"><label for="orgName">Organization Name <span class="form-optional">(creating a new org)</span></label>'
      +         '<input type="text" id="orgName" placeholder="Acme Corp" /></div>'
      +       '<div class="form-field"><label for="inviteCode">Invite Code <span class="form-optional">(joining an existing org)</span></label>'
      +         '<input type="text" id="inviteCode" placeholder="Paste invite code here" /></div>'
      +       '<p class="form-hint">Fill <em>either</em> the org name (to create) or the invite code (to join) \u2014 not both.</p>'
      +     '</div>'
      +     '<div id="setup-error" class="form-error" hidden></div>'
      +     '<button type="submit" class="btn btn--primary btn--full" id="setup-submit">Finish Setup</button>'
      +   '</form>'
      + '</div></div>'
      + '</div>';

    document.getElementById('userType').addEventListener('change', function () {
      document.getElementById('org-fields').hidden = this.value !== 'ORGANIZATION';
    });

    document.getElementById('setup-form').addEventListener('submit', async function (e) {
      e.preventDefault();
      var btn = document.getElementById('setup-submit');
      var errEl = document.getElementById('setup-error');
      errEl.hidden = true;
      btn.disabled = true;
      btn.textContent = 'Setting up\u2026';

      var pendingUserId = sessionStorage.getItem('dm_pending_user_id');
      var userType = document.getElementById('userType').value;
      var orgName = document.getElementById('orgName') ? document.getElementById('orgName').value.trim() : '';
      var inviteCode = document.getElementById('inviteCode') ? document.getElementById('inviteCode').value.trim() : '';

      if (!pendingUserId) {
        errEl.textContent = 'Session expired. Please sign up again.';
        errEl.hidden = false;
        btn.disabled = false;
        btn.textContent = 'Finish Setup';
        return;
      }

      var body = { userId: pendingUserId, userType: userType };

      if (userType === 'ORGANIZATION') {
        if (inviteCode) {
          body.inviteCode = inviteCode;
        } else if (orgName) {
          body.organizationName = orgName;
        }
      }

      try {
        var res = await window.api.post('/auth/setup', body);
        if (res.token) {
          window.api.setToken(res.token);
          window.api.setMe({ userId: res.userId, email: res.email, userType: res.userType, workspaceId: res.workspaceId, organizationId: res.organizationId });
          sessionStorage.removeItem('dm_pending_user_id');
          sessionStorage.removeItem('dm_pending_email');
          location.href = 'home.html';
        } else {
          sessionStorage.removeItem('dm_pending_user_id');
          sessionStorage.removeItem('dm_pending_email');
          showToast('Setup complete! Please sign in.', 'success');
          setTimeout(function () { location.href = 'sign-in.html'; }, 1500);
        }
      } catch (err) {
        errEl.textContent = err.message;
        errEl.hidden = false;
        btn.disabled = false;
        btn.textContent = 'Finish Setup';
      }
    });
  }

  /* ═══════════════════════════════════════════════════════
     HOME PAGE
  ═══════════════════════════════════════════════════════ */
  async function homePage() {
    window.api.requireAuth();
    var me = await ensureMe();
    renderShell(me);
    var mc = mainContent();
    mc.innerHTML = loadingHTML(4);

    var sessions = [];
    try {
      sessions = await window.api.get('/sessions');
    } catch (e) {
      mc.innerHTML = errorHTML(e.message);
      return;
    }

    var total      = sessions.length;
    var completed  = sessions.filter(function (s) { return s.status === 'COMPLETED'; }).length;
    var processing = sessions.filter(function (s) { return s.status === 'PROCESSING' || s.status === 'QUEUED'; }).length;
    var failed     = sessions.filter(function (s) { return s.status === 'FAILED' || s.status === 'PARTIAL_FAILURE'; }).length;

    var recent = sessions.slice(0, 5);
    var recentRows = recent.length
      ? recent.map(function (s) {
          return '<tr>'
            + '<td class="mono">' + esc((s.sessionId || '').substring(0, 8) + '\u2026') + '</td>'
            + '<td>' + esc(s.sessionType || '') + '</td>'
            + '<td>' + statusChipHTML(s.status) + '</td>'
            + '<td>' + relativeDate(s.createdAt) + '</td>'
            + '<td><a href="results.html?id=' + esc(s.sessionId) + '" class="btn btn--ghost btn--sm">View</a></td>'
            + '</tr>';
        }).join('')
      : '<tr><td colspan="5" class="table-empty">No sessions yet.</td></tr>';

    mc.innerHTML =
      '<div class="stats-grid">'
      + '<div class="stat-card"><span class="stat-num">' + total + '</span><span class="stat-label">Total Sessions</span></div>'
      + '<div class="stat-card stat-card--green"><span class="stat-num">' + completed + '</span><span class="stat-label">Completed</span></div>'
      + '<div class="stat-card stat-card--blue"><span class="stat-num">' + processing + '</span><span class="stat-label">In Progress</span></div>'
      + '<div class="stat-card stat-card--red"><span class="stat-num">' + failed + '</span><span class="stat-label">Failed</span></div>'
      + '</div>'
      + '<div class="section-header"><h2>Recent Sessions</h2><a href="upload.html" class="btn btn--primary btn--sm">+ New Session</a></div>'
      + '<div class="table-wrap"><table class="data-table"><thead><tr>'
      + '<th>ID</th><th>Type</th><th>Status</th><th>Created</th><th></th>'
      + '</tr></thead><tbody>' + recentRows + '</tbody></table></div>';
  }

  /* ═══════════════════════════════════════════════════════
     UPLOAD PAGE
  ═══════════════════════════════════════════════════════ */
  async function uploadPage() {
  window.api.requireAuth();
  var me = await ensureMe();
  renderShell(me);
  var mc = mainContent();

  var categories = [];
  try { categories = await window.api.get('/categories'); } catch (e) { /* non-fatal */ }

  var catOptions = categories.map(function (c) {
    return '<option value="' + esc(c.id) + '">' + esc(c.categoryName) + '</option>';
  }).join('');

  mc.innerHTML =
    '<div class="upload-layout">'
    + '<div class="upload-dropzone" id="dropzone">'
    +   '<input type="file" id="file-input" multiple accept=".pdf,.docx,.txt,.png,.jpg,.jpeg" hidden />'
    +   '<div class="dropzone-inner">'
    +     '<span class="dropzone-icon">&#8679;</span>'
    +     '<p class="dropzone-title">Drag files here or <button type="button" class="btn-link" id="browse-btn">browse</button></p>'
    +     '<p class="dropzone-hint">PDF, DOCX, TXT, PNG, JPG</p>'
    +   '</div>'
    +   '<ul class="file-list" id="file-list"></ul>'
    + '</div>'
    + '<div class="upload-options">'
    +   '<h2>Session Options</h2>'
    +   '<div class="form-field"><label for="session-type">Session Type</label>'
    +     '<select id="session-type">'
    +       '<option value="SINGLE_SESSION">Single Session</option>'
    +       '<option value="CATEGORY_RUN">Category Run</option>'
    +     '</select></div>'
    +   '<div class="form-field" id="cat-field" hidden><label for="category-id">Category</label>'
    +     '<select id="category-id"><option value="">— none —</option>' + catOptions + '</select>'
    +     '<p class="form-hint">The category\u2019s saved extraction mode, summary style, and custom prompt will be used automatically \u2014 no need to set them below.</p></div>'
    +   '<div id="manual-fields">'
    +     '<div class="form-field"><label for="extraction-mode">Extraction Mode</label>'
    +       '<select id="extraction-mode">'
    +         '<option value="FULL">Full Text</option>'
    +         '<option value="SUMMARY">Summary</option>'
    +         '<option value="KEY_INSIGHTS">Key Insights</option>'
    +         '<option value="CUSTOM_FIELDS">Custom Fields</option>'
    +       '</select></div>'
    +     '<div class="form-field"><label for="summary-style">Summary Style</label>'
    +       '<select id="summary-style">'
    +         '<option value="">None</option>'
    +         '<option value="SHORT">Short</option>'
    +         '<option value="BRIEF">Brief</option>'
    +       '</select></div>'
    +     '<div class="form-field" id="prompt-field"><label for="custom-prompt">Custom Prompt <span class="form-optional">(optional)</span></label>'
    +       '<textarea id="custom-prompt" rows="3" placeholder="Extract: invoice_number, total_amount, date"></textarea>'
    +       '<p class="form-hint">Only used when Extraction Mode is \u201cCustom Fields\u201d. Either list the fields to pull out, e.g. <span class="mono">Extract: invoice_number, total_amount, date</span> \u2014 or ask a direct question, e.g. <span class="mono">Which item has the highest price?</span></p></div>'
    +   '</div>'
    +   '<div id="upload-error" class="form-error" hidden></div>'
    +   '<button type="button" class="btn btn--primary btn--full" id="start-btn" disabled>Start Processing</button>'
    + '</div>'
    + '</div>';

  var fileInput    = document.getElementById('file-input');
  var dropzone     = document.getElementById('dropzone');
  var browseBtn    = document.getElementById('browse-btn');
  var fileList     = document.getElementById('file-list');
  var startBtn     = document.getElementById('start-btn');
  var sessionType  = document.getElementById('session-type');
  var catField     = document.getElementById('cat-field');
  var manualFields = document.getElementById('manual-fields');
  var extractionModeEl = document.getElementById('extraction-mode');
  var promptFieldEl = document.getElementById('prompt-field');
  function togglePromptField() { promptFieldEl.hidden = extractionModeEl.value !== 'CUSTOM_FIELDS'; }
  extractionModeEl.addEventListener('change', togglePromptField);
  togglePromptField();
  var errEl        = document.getElementById('upload-error');
  var selectedFiles = [];

  browseBtn.addEventListener('click', function () { fileInput.click(); });
  fileInput.addEventListener('change', function () { addFiles(Array.from(fileInput.files)); fileInput.value = ''; });

  dropzone.addEventListener('dragover', function (e) { e.preventDefault(); dropzone.classList.add('dropzone--over'); });
  dropzone.addEventListener('dragleave', function () { dropzone.classList.remove('dropzone--over'); });
  dropzone.addEventListener('drop', function (e) {
    e.preventDefault();
    dropzone.classList.remove('dropzone--over');
    addFiles(Array.from(e.dataTransfer.files));
  });

  sessionType.addEventListener('change', function () {
    var isCategoryRun = this.value === 'CATEGORY_RUN';
    catField.hidden = !isCategoryRun;
    manualFields.hidden = isCategoryRun;
  });

  function addFiles(files) {
    files.forEach(function (f) {
      if (!selectedFiles.find(function (x) { return x.name === f.name; })) selectedFiles.push(f);
    });
    renderFileList();
  }

  function renderFileList() {
    fileList.innerHTML = selectedFiles.map(function (f, i) {
      return '<li class="file-item">'
        + '<span class="file-name mono">' + esc(f.name) + '</span>'
        + '<span class="file-size">' + formatSize(f.size) + '</span>'
        + '<button type="button" class="btn-icon file-remove" data-idx="' + i + '">&#10005;</button>'
        + '</li>';
    }).join('');
    startBtn.disabled = selectedFiles.length === 0;
  }

  fileList.addEventListener('click', function (e) {
    var btn = e.target.closest('.file-remove');
    if (!btn) return;
    selectedFiles.splice(Number(btn.dataset.idx), 1);
    renderFileList();
  });

  startBtn.addEventListener('click', async function () {
    errEl.hidden = true;
    if (!selectedFiles.length) return;
    startBtn.disabled = true;
    startBtn.textContent = 'Uploading\u2026';
    var fd = new FormData();
    fd.append('sessionType', sessionType.value);
    var catId = document.getElementById('category-id').value;
    if (catId) fd.append('categoryId', catId);
    if (sessionType.value !== 'CATEGORY_RUN') {
      var exMode = document.getElementById('extraction-mode').value;
      if (exMode) fd.append('extractionMode', exMode);
      var sumStyle = document.getElementById('summary-style').value;
      if (sumStyle) fd.append('summaryStyle', sumStyle);
      var customPrompt = document.getElementById('custom-prompt').value.trim();
      if (customPrompt) fd.append('customPrompt', customPrompt);
    }
    selectedFiles.forEach(function (f) { fd.append('files', f); });
    try {
      var session = await window.api.upload('/sessions', fd);
      location.href = 'processing.html?id=' + encodeURIComponent(session.sessionId);
    } catch (err) {
      errEl.textContent = err.message;
      errEl.hidden = false;
      startBtn.disabled = false;
      startBtn.textContent = 'Start Processing';
    }
  });
}

  /* ═══════════════════════════════════════════════════════
     PROCESSING PAGE
  ═══════════════════════════════════════════════════════ */
  async function processingPage() {
    window.api.requireAuth();
    var me = await ensureMe();
    renderShell(me);
    var mc = mainContent();

    var params = new URLSearchParams(location.search);
    var sessionId = params.get('id');

    if (!sessionId) {
      mc.innerHTML = emptyHTML('&#8679;', 'No session selected', 'Go to Upload to start a new session.')
        + '<div style="margin-top:1rem;text-align:center"><a href="upload.html" class="btn btn--primary">Go to Upload</a></div>';
      return;
    }

    mc.innerHTML =
      '<div class="processing-wrap">'
      + '<div class="processing-header"><h2>Processing Session</h2>'
      +   '<span class="mono session-id-label" id="sid-label"></span></div>'
      + '<div class="progress-bar-wrap"><div class="progress-bar" id="progress-bar" style="width:0%"></div></div>'
      + '<p class="processing-status" id="proc-status">Checking status\u2026</p>'
      + '<div class="job-list" id="job-list"></div>'
      + '</div>';

    document.getElementById('sid-label').textContent = sessionId;

    var pollTimer = null;

    async function poll() {
      var status;
      try {
        status = await window.api.get('/sessions/' + encodeURIComponent(sessionId) + '/status');
      } catch (e) {
        var statusEl2 = document.getElementById('proc-status');
        if (statusEl2) statusEl2.textContent = 'Error: ' + e.message;
        return;
      }
      var jobs = status.jobs || [];
      var total = jobs.length || status.totalFiles || 0;
      var sumProgress = 0;
      jobs.forEach(function (j) {
        if (j.status === 'COMPLETED' || j.status === 'FAILED') sumProgress += 1;
        else if (j.status === 'PROCESSING') sumProgress += (j.progress || 0);
      });
      var pct = total > 0 ? Math.round((sumProgress / total) * 100) : 0;
    
      var barEl     = document.getElementById('progress-bar');
      var statusEl  = document.getElementById('proc-status');
      var jobListEl = document.getElementById('job-list');
      if (barEl)    barEl.style.width = pct + '%';
      if (statusEl) statusEl.textContent = pct + '% complete \u2014 this can take 15\u201320 minutes per document.';
      if (jobs.length && jobListEl) {
        jobListEl.innerHTML = jobs.map(function (j) {
          return '<div class="job-row"><span class="job-name mono">' + esc(j.fileName || j.fileId || '') + '</span>' + statusChipHTML(j.status) + '</div>';
        }).join('');
      }
    
      var allTerminal = jobs.length > 0 && jobs.every(function (j) { return j.status === 'COMPLETED' || j.status === 'FAILED'; });
      if (allTerminal) {
        clearInterval(pollTimer);
        if (statusEl) statusEl.textContent = 'Done! Redirecting to results\u2026';
        setTimeout(function () { location.href = 'results.html?id=' + encodeURIComponent(sessionId); }, 1500);
      }
    }

    poll();
    pollTimer = setInterval(poll, 4000);
  }


  function prettyJson(jsonStr, fallback) {
    if (!jsonStr) return fallback;
    try { return JSON.stringify(JSON.parse(jsonStr), null, 2); }
    catch (e) { return jsonStr; }
  }
  
  function renderTablesHTML(tablesJson) {
    if (!tablesJson) return '<p class="no-replies">No table data extracted.</p>';
    var rows;
    try { rows = JSON.parse(tablesJson); } catch (e) { return '<pre class="result-pre">' + esc(tablesJson) + '</pre>'; }
    if (!Array.isArray(rows) || !rows.length) return '<p class="no-replies">No table data extracted.</p>';
  
    var byPage = {};
    rows.forEach(function (row) {
      var page = String(row.page || '1');
      if (!byPage[page]) byPage[page] = [];
      byPage[page].push(row);
    });
  
    return Object.keys(byPage).sort().map(function (page) {
      var pageRows = byPage[page];
      var cols = Object.keys(pageRows[0]).filter(function (k) { return k !== 'page'; });
      var thead = '<tr>' + cols.map(function (c) { return '<th>' + esc(c) + '</th>'; }).join('') + '</tr>';
      var tbody = pageRows.map(function (row) {
        return '<tr>' + cols.map(function (c) { return '<td>' + esc(row[c] || '') + '</td>'; }).join('') + '</tr>';
      }).join('');
      return '<h4 style="margin:1rem 0 0.5rem;font-size:0.85rem;color:var(--muted)">Page ' + esc(page) + '</h4>'
        + '<div class="table-wrap"><table class="data-table"><thead>' + thead + '</thead><tbody>' + tbody + '</tbody></table></div>';
    }).join('');
  }

  
  /* ═══════════════════════════════════════════════════════
     RESULTS PAGE
  ═══════════════════════════════════════════════════════ */
  async function resultsPage() {
    window.api.requireAuth();
    var me = await ensureMe();
    renderShell(me);
    var mc = mainContent();

    var params = new URLSearchParams(location.search);
    var sessionId = params.get('id');

    if (!sessionId) {
      mc.innerHTML = emptyHTML('&#9783;', 'No session selected', 'Open a session from Activity or Home.');
      return;
    }

    mc.innerHTML = loadingHTML(3);

    var results;
    try {
      results = await window.api.get('/sessions/' + encodeURIComponent(sessionId) + '/results');
    } catch (e) {
      mc.innerHTML = errorHTML(e.message);
      return;
    }

    if (!results || !results.results || !results.results.length) {
      mc.innerHTML = emptyHTML('&#9783;', 'No results yet', 'Processing may still be running.');
      return;
    }

    var fileItems = results.results.map(function (r, i) {
      return '<li class="result-file-item' + (i === 0 ? ' result-file-item--active' : '') + '" data-idx="' + i + '">'
        + '<span class="result-file-name mono">' + esc(r.fileName) + '</span>'
        + statusChipHTML(r.jobStatus)
        + '</li>';
    }).join('');

    mc.innerHTML =
      '<div style="display:flex;justify-content:flex-end;gap:0.5rem;flex-wrap:wrap;margin-bottom:1rem;">'
      +   '<button type="button" class="btn btn--ghost btn--sm" id="export-json-btn">Export JSON</button>'
      +   '<button type="button" class="btn btn--ghost btn--sm" id="export-csv-btn">Export CSV</button>'
      + '</div>'
      + '<div class="results-layout">'
      + '<aside class="results-sidebar"><ul class="result-file-list" id="result-file-list">' + fileItems + '</ul></aside>'
      + '<div class="results-main" id="results-main"></div>'
      + '</div>';

    function bindExport(btnId, fmt, ext) {
      document.getElementById(btnId).addEventListener('click', async function () {
        try {
          var blob = await window.api.downloadBlob(
            '/sessions/' + encodeURIComponent(sessionId) + '/export?format=' + fmt);
          var url = URL.createObjectURL(blob);
          var a = document.createElement('a');
          a.href = url;
          a.download = 'session-' + sessionId + '.' + ext;
          document.body.appendChild(a);
          a.click();
          a.remove();
          URL.revokeObjectURL(url);
        } catch (e) { showToast(e.message, 'error'); }
      });
    }
    bindExport('export-json-btn', 'json', 'json');
    bindExport('export-csv-btn', 'csv', 'csv');

    function renderResult(idx) {
      var r = results.results[idx];
      var panel = document.getElementById('results-main');
      if (!r || !panel) return;
      panel.innerHTML =
        '<div class="result-header">'
        + '<h3 class="result-filename mono"></h3>'
        + statusChipHTML(r.jobStatus)
        + (r.processedAt ? '<span class="result-date">' + formatDate(r.processedAt) + '</span>' : '')
        + (r.fileId
            ? '<button class="btn btn--ghost btn--sm" id="view-original-btn">View original</button>'
              + '<button class="btn btn--ghost btn--sm" id="download-original-btn">Download</button>'
            : '')
        + (r.jobStatus === 'FAILED' && r.jobId
            ? '<button class="btn btn--primary btn--sm" id="retry-job-btn">Retry</button>'
            : '')
        + '</div>'
        + '<div class="result-tabs">'
        +   '<button class="result-tab result-tab--active" data-tab="text">Extracted Text</button>'
        +   '<button class="result-tab" data-tab="json">Structured JSON</button>'
        +   '<button class="result-tab" data-tab="tables">Tables</button>'
        + '</div>'
        + '<div class="result-pane" id="pane-text"><pre class="result-pre">' + esc(r.extractedText || 'No text output.') + '</pre></div>'
        + '<div class="result-pane" id="pane-json" hidden><pre class="result-pre">' + esc(prettyJson(r.structuredJson, 'No JSON output.')) + '</pre></div>'
        + '<div class="result-pane" id="pane-tables" hidden>' + renderTablesHTML(r.tables) + '</div>';
        panel.querySelector('.result-filename').textContent = r.fileName;
      panel.querySelectorAll('.result-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
          panel.querySelectorAll('.result-tab').forEach(function (t) { t.classList.remove('result-tab--active'); });
          tab.classList.add('result-tab--active');
          document.getElementById('pane-text').hidden = tab.dataset.tab !== 'text';
          document.getElementById('pane-json').hidden = tab.dataset.tab !== 'json';
          document.getElementById('pane-tables').hidden = tab.dataset.tab !== 'tables';
        });
      });
      var viewBtn = document.getElementById('view-original-btn');
      if (viewBtn) viewBtn.addEventListener('click', async function () {
        try {
          var blob = await window.api.downloadBlob('/files/' + encodeURIComponent(r.fileId));
          var url = URL.createObjectURL(blob);
          window.open(url, '_blank');
          setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
        } catch (e) { showToast(e.message, 'error'); }
      });

      var dlBtn = document.getElementById('download-original-btn');
      if (dlBtn) dlBtn.addEventListener('click', async function () {
        try {
          var blob = await window.api.downloadBlob('/files/' + encodeURIComponent(r.fileId) + '?download=true');
          var url = URL.createObjectURL(blob);
          var a = document.createElement('a');
          a.href = url;
          a.download = r.fileName || 'document';
          document.body.appendChild(a);
          a.click();
          a.remove();
          URL.revokeObjectURL(url);
        } catch (e) { showToast(e.message, 'error'); }
      });

      var retryBtn = document.getElementById('retry-job-btn');
      if (retryBtn) retryBtn.addEventListener('click', async function () {
        retryBtn.disabled = true;
        retryBtn.textContent = 'Submitting\u2026';
        try {
          await window.api.post('/sessions/jobs/' + encodeURIComponent(r.jobId) + '/retry', {});
          showToast('Job resubmitted for processing.', 'success');
          location.href = 'processing.html?id=' + encodeURIComponent(sessionId);
        } catch (e) {
          showToast(e.message, 'error');
          retryBtn.disabled = false;
          retryBtn.textContent = 'Retry';
        }
      });
    }

    renderResult(0);

    document.getElementById('result-file-list').addEventListener('click', function (e) {
      var item = e.target.closest('.result-file-item');
      if (!item) return;
      document.querySelectorAll('.result-file-item').forEach(function (el) { el.classList.remove('result-file-item--active'); });
      item.classList.add('result-file-item--active');
      renderResult(Number(item.dataset.idx));
    });

  }

  /* ═══════════════════════════════════════════════════════
   SEARCH PAGE
  ════════════════════════════════════════════════════════ */
  async function searchPage() {
    window.api.requireAuth();
    var me = await ensureMe();
    renderShell(me);
    var mc = mainContent();

    mc.innerHTML =
      '<div class="search-bar-wrap" style="display:flex;gap:0.5rem;flex-wrap:wrap;margin-bottom:0.75rem;">'
      +   '<input type="text" id="search-input" placeholder="Search across your documents\u2026" style="flex:1;padding:0.65rem 0.875rem;border:1.5px solid var(--border-2);border-radius:var(--radius-sm);font-size:0.9rem;font-family:inherit;" />'
      +   '<button type="button" class="btn btn--primary" id="search-btn">Search</button>'
      + '</div>'
      + '<div style="display:flex;justify-content:flex-end;margin-bottom:1.25rem;">'
      +   '<button type="button" class="btn btn--ghost btn--sm" id="reindex-btn">Rebuild search index</button>'
      + '</div>'
      + '<div id="search-results"></div>';

    var input = document.getElementById('search-input');
    var btn = document.getElementById('search-btn');
    var resultsEl = document.getElementById('search-results');

    async function runSearch() {
      var query = input.value.trim();
      if (!query) return;
      resultsEl.innerHTML = loadingHTML(3);
      var results;
      try {
        results = await window.api.get('/search?query=' + encodeURIComponent(query) + '&topK=5');
      } catch (e) {
        resultsEl.innerHTML = errorHTML(e.message);
        return;
      }
      if (!results || !results.length) {
        resultsEl.innerHTML = emptyHTML('&#8981;', 'No matches found', 'Try a different search term, or make sure documents have finished processing.');
        return;
      }
      resultsEl.innerHTML = results.map(function () {
        return '<div class="reply-item" style="margin-bottom:0.75rem;">'
          + '<div class="reply-meta"><span class="reply-author"></span><span class="reply-time"></span></div>'
          + '<p class="reply-message"></p>'
          + '<a href="#" class="btn-link source-link" style="font-size:0.8rem;">View source document</a>'
          + '</div>';
      }).join('');

      var items = resultsEl.querySelectorAll('.reply-item');
      results.forEach(function (r, i) {
        if (!items[i]) return;
        items[i].querySelector('.reply-author').textContent = r.fileName || 'Unknown file';
        items[i].querySelector('.reply-time').textContent = r.score != null ? Math.round(r.score * 100) + '% match' : '';
        items[i].querySelector('.reply-message').textContent = r.text;
        var link = items[i].querySelector('.source-link');
        if (r.sessionId) {
          link.href = 'results.html?id=' + encodeURIComponent(r.sessionId);
        } else {
          link.style.display = 'none';
        }
      });
    }

    btn.addEventListener('click', runSearch);
    input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') runSearch();
    });

    document.getElementById('reindex-btn').addEventListener('click', async function () {
      var rb = document.getElementById('reindex-btn');
      if (!confirm('Rebuild the search index from stored extractions? This clears stale entries and may take a minute.')) return;
      rb.disabled = true;
      rb.textContent = 'Rebuilding\u2026';
      try {
        var res = await window.api.post('/search/reindex', {});
        showToast('Reindexed ' + (res.indexed || 0) + ' documents.', 'success');
      } catch (e) {
        showToast(e.message, 'error');
      } finally {
        rb.disabled = false;
        rb.textContent = 'Rebuild search index';
      }
    });
  }


  /* ═══════════════════════════════════════════════════════
     ACTIVITY PAGE
  ═══════════════════════════════════════════════════════ */
    async function activityPage() {
    window.api.requireAuth();
    var me = await ensureMe();
    renderShell(me);
    var mc = mainContent();
    mc.innerHTML = loadingHTML(6);

    var sessions = [];
    try {
      sessions = await window.api.get('/sessions');
    } catch (e) {
      mc.innerHTML = errorHTML(e.message);
      return;
    }

    var MODE_LABELS = {
      FULL: 'Full Text', SUMMARY: 'Summary',
      KEY_INSIGHTS: 'Key Insights', CUSTOM_FIELDS: 'Custom Fields'
    };

    function fileNames(s) {
      return (s.jobs || []).map(function (j) { return j.fileName || ''; })
        .filter(function (n) { return n; });
    }

    function describeType(s) {
      if (s.sessionType === 'CATEGORY_RUN') {
        return s.categoryName ? esc(s.categoryName) : 'Category Run';
      }
      var mode = (s.jobs && s.jobs[0]) ? s.jobs[0].extractionMode : null;
      return MODE_LABELS[mode] || 'Single Session';
    }

    function renderTable(list) {
      if (!list.length) {
        return emptyHTML('&#9776;', 'No sessions', 'Start a new session from the Upload page.');
      }
      return '<div class="table-wrap"><table class="data-table"><thead><tr>'
        + '<th>Documents</th><th>Extraction</th><th>Status</th><th>Created</th><th></th><th></th>'
        + '</tr></thead><tbody>'
        + list.map(function (s) {
            var names = fileNames(s);
            var primary = names.length ? names[0] : '(no files)';
            var extra = names.length > 1 ? ' <span class="form-optional">+' + (names.length - 1) + ' more</span>' : '';
            return '<tr>'
              + '<td><span class="mono" title="' + esc(names.join(', ')) + '">' + esc(primary) + '</span>' + extra + '</td>'
              + '<td>' + describeType(s)
              +   (s.sessionType === 'CATEGORY_RUN' ? ' <span class="form-optional">(category)</span>' : '')
              + '</td>'
              + '<td>' + statusChipHTML(s.status) + '</td>'
              + '<td><span title="' + esc(formatDate(s.createdAt)) + '">' + relativeDate(s.createdAt) + '</span></td>'
              + '<td><a href="results.html?id=' + esc(s.sessionId) + '" class="btn btn--ghost btn--sm">View</a></td>'
              + '<td><button class="btn btn--danger btn--sm session-del-btn" data-id="' + esc(s.sessionId) + '">Delete</button></td>'
              + '</tr>';
          }).join('')
        + '</tbody></table></div>';
    }

    mc.innerHTML =
      '<div class="section-header">'
      + '<h2>All Sessions</h2>'
      + '<div class="filter-row">'
      +   '<input type="text" id="filter-name" placeholder="Filter by document name\u2026" style="padding:0.4rem 0.75rem;border:1.5px solid var(--border-2);border-radius:var(--radius-sm);font-size:0.82rem;font-family:inherit;min-width:200px;" />'
      +   '<select id="filter-status"><option value="">All Statuses</option>'
      +     '<option value="QUEUED">Queued</option>'
      +     '<option value="PROCESSING">Processing</option>'
      +     '<option value="COMPLETED">Completed</option>'
      +     '<option value="PARTIAL_FAILURE">Partial Failure</option>'
      +     '<option value="FAILED">Failed</option>'
      +   '</select>'
      +   '<select id="filter-type"><option value="">All Types</option>'
      +     '<option value="SINGLE_SESSION">Single Session</option>'
      +     '<option value="CATEGORY_RUN">Category Run</option>'
      +   '</select>'
      + '</div>'
      + '</div>'
      + '<div id="activity-table">' + renderTable(sessions) + '</div>';

    function applyFilters() {
      var status = document.getElementById('filter-status').value;
      var type   = document.getElementById('filter-type').value;
      var name   = document.getElementById('filter-name').value.trim().toLowerCase();

      var filtered = sessions.filter(function (s) {
        if (status && s.status !== status) return false;
        if (type && s.sessionType !== type) return false;
        if (name && !fileNames(s).some(function (n) { return n.toLowerCase().indexOf(name) !== -1; })) return false;
        return true;
      });
      document.getElementById('activity-table').innerHTML = renderTable(filtered);
    }

    document.getElementById('filter-status').addEventListener('change', applyFilters);
    document.getElementById('filter-type').addEventListener('change', applyFilters);
    document.getElementById('filter-name').addEventListener('input', applyFilters);

    // delegated so it survives re-renders from filtering
    document.getElementById('activity-table').addEventListener('click', async function (e) {
      var btn = e.target.closest('.session-del-btn');
      if (!btn) return;
      if (!confirm('Delete this session and all its extracted results? This cannot be undone.')) return;
      btn.disabled = true;
      try {
        await window.api.delete('/sessions/' + encodeURIComponent(btn.dataset.id));
        sessions = sessions.filter(function (s) { return s.sessionId !== btn.dataset.id; });
        showToast('Session deleted.', 'success');
        applyFilters();
      } catch (err) {
        showToast(err.message, 'error');
        btn.disabled = false;
      }
    });
  }

  /* ═══════════════════════════════════════════════════════
     CATEGORIES PAGE
  ═══════════════════════════════════════════════════════ */
  async function categoriesPage() {
  window.api.requireAuth();
  var me = await ensureMe();
  renderShell(me);
  var mc = mainContent();

  var taEl = document.getElementById('topbar-actions');
  if (taEl) taEl.innerHTML = '<button class="btn btn--primary btn--sm" id="new-cat-btn">+ New Category</button>';

  async function loadCategories() {
    mc.innerHTML = loadingHTML(4);
    var cats = [];
    try { cats = await window.api.get('/categories'); } catch (e) { mc.innerHTML = errorHTML(e.message); return; }

    if (!cats.length) {
      mc.innerHTML = emptyHTML('&#9965;', 'No categories', 'Create a category to group your document sessions.');
      return;
    }

    mc.innerHTML =
      '<div class="card-grid">'
      + cats.map(function (c, i) {
          return '<div class="cat-card" data-idx="' + i + '">'
            + '<div class="cat-card-body"><h3 class="cat-card-name"></h3>'
            + (c.description ? '<p class="cat-card-desc"></p>' : '')
            + '</div>'
            + '<div class="cat-card-actions">'
            +   '<button class="btn btn--ghost btn--sm cat-edit-btn" data-idx="' + i + '">Edit</button>'
            +   '<button class="btn btn--danger btn--sm cat-del-btn" data-id="' + esc(c.id) + '">Delete</button>'
            + '</div>'
            + '</div>';
        }).join('')
      + '</div>';

    cats.forEach(function (c, i) {
      var card = mc.querySelectorAll('.cat-card')[i];
      if (!card) return;
      card.querySelector('.cat-card-name').textContent = c.categoryName;
      if (c.description) card.querySelector('.cat-card-desc').textContent = c.description;
    });

    mc.querySelectorAll('.cat-edit-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var c = cats[Number(btn.dataset.idx)];
        openCatModal(c.id, c);
      });
    });

    mc.querySelectorAll('.cat-del-btn').forEach(function (btn) {
      btn.addEventListener('click', async function () {
        if (!confirm('Delete this category?')) return;
        try {
          await window.api.delete('/categories/' + encodeURIComponent(btn.dataset.id));
          showToast('Category deleted.', 'success');
          loadCategories();
        } catch (e) { showToast(e.message, 'error'); }
      });
    });
  }

  function openCatModal(id, existing) {
    existing = existing || {};
    var isEdit = !!id;
    var modal = document.getElementById('cat-modal');
    if (modal) modal.remove();
    modal = document.createElement('div');
    modal.id = 'cat-modal';
    modal.className = 'modal-overlay';
    modal.innerHTML =
      '<div class="modal">'
      + '<div class="modal-header"><h2>' + (isEdit ? 'Edit Category' : 'New Category') + '</h2>'
      +   '<button class="btn-icon" id="modal-close">&#10005;</button></div>'
      + '<div class="modal-body">'
      +   '<div class="form-field"><label for="cat-name">Name</label><input type="text" id="cat-name" /></div>'
      +   '<div class="form-field"><label for="cat-type">Category Type</label>'
      +     '<select id="cat-type"><option value="DOCUMENT">Document</option><option value="RECORD">Record</option></select></div>'
      +   '<div class="form-field"><label for="cat-mode">Default Extraction Mode</label>'
      +     '<select id="cat-mode">'
      +       '<option value="FULL">Full Text</option>'
      +       '<option value="SUMMARY">Summary</option>'
      +       '<option value="KEY_INSIGHTS">Key Insights</option>'
      +       '<option value="CUSTOM_FIELDS">Custom Fields</option>'
      +     '</select></div>'
      +   '<div class="form-field"><label for="cat-summary-style">Summary Style <span class="form-optional">(optional)</span></label>'
      +     '<select id="cat-summary-style"><option value="">None</option><option value="SHORT">Short</option><option value="BRIEF">Brief</option></select></div>'
      +   '<div class="form-field" id="cat-storage-field" hidden><label for="cat-storage">Storage Format</label>'
      +     '<select id="cat-storage"><option value="DOCUMENT_WISE">Document-wise</option><option value="CONSOLIDATED">Consolidated</option></select></div>'
      +   '<div class="form-field" id="cat-prompt-field"><label for="cat-prompt">Custom Prompt <span class="form-optional">(optional)</span></label>'
      +      '<textarea id="cat-prompt" rows="2" placeholder="Extract: invoice_number, total_amount, date"></textarea>'
      +     '<p class="form-hint">Only used when Default Extraction Mode is \u201cCustom Fields\u201d. Either list the fields to pull out, e.g. <span class="mono">Extract: invoice_number, total_amount, date</span> \u2014 or ask a direct question, e.g. <span class="mono">Which item has the highest price?</span></p></div>'
      +   '<div class="form-field"><label for="cat-desc">Description <span class="form-optional">(optional)</span></label>'
      +     '<textarea id="cat-desc" rows="2"></textarea></div>'
      +   '<div id="cat-modal-error" class="form-error" hidden></div>'
      + '</div>'
      + '<div class="modal-footer">'
      +   '<button class="btn btn--ghost" id="modal-cancel">Cancel</button>'
      +   '<button class="btn btn--primary" id="modal-save">Save</button>'
      + '</div>'
      + '</div>';
    document.body.appendChild(modal);

    var typeEl = document.getElementById('cat-type');
    var modeEl = document.getElementById('cat-mode');
    var storageFieldEl = document.getElementById('cat-storage-field');
    var promptFieldEl = document.getElementById('cat-prompt-field');
    function toggleStorage() { storageFieldEl.hidden = typeEl.value !== 'RECORD'; }
    function togglePrompt() { promptFieldEl.hidden = modeEl.value !== 'CUSTOM_FIELDS'; }
    typeEl.addEventListener('change', toggleStorage);
    modeEl.addEventListener('change', togglePrompt);

    if (existing.categoryName) document.getElementById('cat-name').value = existing.categoryName;
    if (existing.categoryType) typeEl.value = existing.categoryType;
    if (existing.defaultExtractionMode) document.getElementById('cat-mode').value = existing.defaultExtractionMode;
    if (existing.summaryStyle) document.getElementById('cat-summary-style').value = existing.summaryStyle;
    if (existing.storageFormat) document.getElementById('cat-storage').value = existing.storageFormat;
    if (existing.customPrompt) document.getElementById('cat-prompt').value = existing.customPrompt;
    if (existing.description) document.getElementById('cat-desc').value = existing.description;
    toggleStorage();
    togglePrompt();

    function closeModal() { modal.remove(); }
    document.getElementById('modal-close').addEventListener('click', closeModal);
    document.getElementById('modal-cancel').addEventListener('click', closeModal);
    modal.addEventListener('click', function (e) { if (e.target === modal) closeModal(); });

    document.getElementById('modal-save').addEventListener('click', async function () {
      var errEl = document.getElementById('cat-modal-error');
      var body = {
        categoryName: document.getElementById('cat-name').value.trim(),
        categoryType: typeEl.value,
        defaultExtractionMode: document.getElementById('cat-mode').value,
        summaryStyle: document.getElementById('cat-summary-style').value || null,
        storageFormat: typeEl.value === 'RECORD' ? document.getElementById('cat-storage').value : null,
        customPrompt: document.getElementById('cat-prompt').value.trim() || null,
        description: document.getElementById('cat-desc').value.trim() || null
      };
      if (!body.categoryName) { errEl.textContent = 'Name is required.'; errEl.hidden = false; return; }
      errEl.hidden = true;
      try {
        if (isEdit) {
          await window.api.put('/categories/' + encodeURIComponent(id), body);
        } else {
          await window.api.post('/categories', body);
        }
        closeModal();
        showToast('Category saved.', 'success');
        loadCategories();
      } catch (e) { errEl.textContent = e.message; errEl.hidden = false; }
    });
  }

  loadCategories();

  var newBtn = document.getElementById('new-cat-btn');
  if (newBtn) newBtn.addEventListener('click', function () { openCatModal(null, null); });
}

  /* ═══════════════════════════════════════════════════════
     RECORDS PAGE
  ═══════════════════════════════════════════════════════ */
  async function recordsPage() {
  window.api.requireAuth();
  var me = await ensureMe();
  renderShell(me);
  var mc = mainContent();

  var taEl = document.getElementById('topbar-actions');
  if (taEl) taEl.innerHTML = '<button class="btn btn--primary btn--sm" id="new-rec-btn">+ New Record Set</button>';

  async function loadRecords() {
    mc.innerHTML = loadingHTML(4);
    var records = [];
    try { records = await window.api.get('/record-sets'); } catch (e) { mc.innerHTML = errorHTML(e.message); return; }

    if (!records.length) {
      mc.innerHTML = emptyHTML('&#9783;', 'No record sets', 'Create a record set from an existing Record-type category.');
      return;
    }

    mc.innerHTML =
      '<div class="table-wrap"><table class="data-table"><thead><tr>'
      + '<th>Category</th><th>Storage Format</th><th>Documents</th><th>Version</th><th>Created</th><th></th><th></th><th></th>'
      + '</tr></thead><tbody>'
      + records.map(function (r, i) {
          var count = 0;
          try {
            var parsed = r.mergedContent ? JSON.parse(r.mergedContent) : null;
            count = parsed && parsed.documentCount ? parsed.documentCount : 0;
          } catch (e) { /* leave 0 */ }
          return '<tr>'
            + '<td class="rec-name"></td>'
            + '<td>' + esc(r.storageFormat || '\u2014') + '</td>'
            + '<td>' + count + '</td>'
            + '<td>' + esc(r.currentVersion) + '</td>'
            + '<td>' + formatDate(r.createdAt) + '</td>'
            + '<td><button class="btn btn--ghost btn--sm rec-view-btn" data-idx="' + i + '">View</button></td>'
            + '<td><button class="btn btn--ghost btn--sm rec-csv-btn" data-idx="' + i + '">Export CSV</button></td>'
            + '<td><button class="btn btn--danger btn--sm rec-del-btn" data-id="' + esc(r.id) + '">Delete</button></td>'
            + '</tr>';
        }).join('')
      + '</tbody></table></div>';

    var rows = mc.querySelectorAll('tbody tr');
    records.forEach(function (r, i) {
      if (!rows[i]) return;
      rows[i].querySelector('.rec-name').textContent = r.categoryName || '';
    });

    mc.querySelectorAll('.rec-view-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        openRecViewModal(records[Number(btn.dataset.idx)]);
      });
    });

    mc.querySelectorAll('.rec-csv-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        exportRecordCsv(records[Number(btn.dataset.idx)]);
      });
    });

    mc.querySelectorAll('.rec-del-btn').forEach(function (btn) {
      btn.addEventListener('click', async function () {
        if (!confirm('Delete this record set? Accumulated data will be lost.')) return;
        try {
          await window.api.delete('/record-sets/' + encodeURIComponent(btn.dataset.id));
          showToast('Record set deleted.', 'success');
          loadRecords();
        } catch (e) { showToast(e.message, 'error'); }
      });
    });
  }

  loadRecords();

  var newRecBtn = document.getElementById('new-rec-btn');
  if (newRecBtn) newRecBtn.addEventListener('click', function () { openRecModal(); });

  async function openRecModal() {
    var existing = document.getElementById('rec-modal');
    if (existing) existing.remove();

    var categories = [];
    try { categories = await window.api.get('/categories'); } catch (e) { /* non-fatal */ }
    var recordCats = categories.filter(function (c) { return c.categoryType === 'RECORD'; });

    var modal = document.createElement('div');
    modal.id = 'rec-modal';
    modal.className = 'modal-overlay';

    if (!recordCats.length) {
      modal.innerHTML =
        '<div class="modal">'
        + '<div class="modal-header"><h2>New Record Set</h2>'
        +   '<button class="btn-icon" id="rec-modal-close">&#10005;</button></div>'
        + '<div class="modal-body">'
        +   '<p class="state-msg">You need a Record-type category before creating a record set. '
        +   '<a href="categories.html">Create one here</a>, then come back.</p>'
        + '</div>'
        + '<div class="modal-footer"><button class="btn btn--ghost" id="rec-modal-cancel">Close</button></div>'
        + '</div>';
      document.body.appendChild(modal);
      function closeEmptyModal() { modal.remove(); }
      document.getElementById('rec-modal-close').addEventListener('click', closeEmptyModal);
      document.getElementById('rec-modal-cancel').addEventListener('click', closeEmptyModal);
      modal.addEventListener('click', function (e) { if (e.target === modal) closeEmptyModal(); });
      return;
    }

    var catOptions = recordCats.map(function (c) {
      return '<option value="' + esc(c.id) + '">' + esc(c.categoryName) + '</option>';
    }).join('');

    modal.innerHTML =
      '<div class="modal">'
      + '<div class="modal-header"><h2>New Record Set</h2>'
      +   '<button class="btn-icon" id="rec-modal-close">&#10005;</button></div>'
      + '<div class="modal-body">'
      +   '<div class="form-field"><label for="rec-category">Category</label>'
      +     '<select id="rec-category">' + catOptions + '</select>'
      +     '<p class="form-hint">Only Record-type categories are shown \u2014 the record set inherits its name and storage format from the category.</p></div>'
      +   '<div id="rec-modal-error" class="form-error" hidden></div>'
      + '</div>'
      + '<div class="modal-footer">'
      +   '<button class="btn btn--ghost" id="rec-modal-cancel">Cancel</button>'
      +   '<button class="btn btn--primary" id="rec-modal-save">Create</button>'
      + '</div>'
      + '</div>';
    document.body.appendChild(modal);

    function closeModal() { modal.remove(); }
    document.getElementById('rec-modal-close').addEventListener('click', closeModal);
    document.getElementById('rec-modal-cancel').addEventListener('click', closeModal);
    modal.addEventListener('click', function (e) { if (e.target === modal) closeModal(); });

    document.getElementById('rec-modal-save').addEventListener('click', async function () {
      var categoryId = document.getElementById('rec-category').value;
      var errEl = document.getElementById('rec-modal-error');
      try {
        await window.api.post('/record-sets', { categoryId: categoryId });
        closeModal();
        showToast('Record set created.', 'success');
        loadRecords();
      } catch (e) { errEl.textContent = e.message; errEl.hidden = false; }
    });
  }
  
  // Builds the CSV client-side from mergedContent: one row per document for
  // PER_DOCUMENT sets, one row per value position for CONSOLIDATED sets.
  function recordToCsv(rec) {
    var parsed = null;
    try { parsed = rec.mergedContent ? JSON.parse(rec.mergedContent) : null; } catch (e) { /* ignore */ }
    if (!parsed) return null;

    function cell(v) {
      if (v == null) return '""';
      if (typeof v === 'object') v = JSON.stringify(v);
      return '"' + String(v).replace(/"/g, '""') + '"';
    }

    var header, rows = [];
    if (parsed.storageFormat === 'CONSOLIDATED') {
      var fields = parsed.fields || {};
      header = Object.keys(fields);
      if (!header.length) return null;
      var cols = header.map(function (k) { return Array.isArray(fields[k]) ? fields[k] : [fields[k]]; });
      var n = Math.max.apply(null, cols.map(function (c) { return c.length; }));
      for (var r = 0; r < n; r++) rows.push(cols.map(function (c) { return c[r]; }));
    } else {
      var docs = parsed.documents || [];
      if (!docs.length) return null;
      var keys = [];
      docs.forEach(function (d) {
        Object.keys(d.fields || {}).forEach(function (k) { if (keys.indexOf(k) < 0) keys.push(k); });
      });
      header = ['fileName', 'processedAt'].concat(keys);
      docs.forEach(function (d) {
        var f = d.fields || {};
        rows.push([d.fileName, d.processedAt].concat(keys.map(function (k) { return f[k]; })));
      });
    }
    return [header].concat(rows).map(function (row) { return row.map(cell).join(','); }).join('\r\n');
  }

  function exportRecordCsv(rec) {
    var csv = recordToCsv(rec);
    if (!csv) { showToast('Nothing to export yet.', 'error'); return; }
    var blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = (rec.categoryName || 'record-set').replace(/[^\w\-]+/g, '_') + '-v' + rec.currentVersion + '.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  function openRecViewModal(rec) {
    var existing = document.getElementById('rec-view-modal');
    if (existing) existing.remove();

    var parsed = null;
    try { parsed = rec.mergedContent ? JSON.parse(rec.mergedContent) : null; } catch (e) { /* ignore */ }

    var bodyHTML;
    if (!parsed) {
      bodyHTML = '<p class="no-replies">Nothing accumulated yet. Run a Category Run session on this category to populate it.</p>';
    } else if (parsed.storageFormat === 'CONSOLIDATED') {
      var fields = parsed.fields || {};
      var keys = Object.keys(fields);
      bodyHTML = keys.length
        ? '<div class="table-wrap"><table class="data-table"><thead><tr><th>Field</th><th>Values across documents</th></tr></thead><tbody>'
          + keys.map(function (k) {
              var vals = Array.isArray(fields[k]) ? fields[k] : [fields[k]];
              return '<tr><td class="mono">' + esc(k) + '</td><td>'
                + vals.map(function (v) { return esc(v == null ? '\u2014' : v); }).join('<br>') + '</td></tr>';
            }).join('')
          + '</tbody></table></div>'
        : '<p class="no-replies">No fields recorded yet.</p>';
    } else {
      var docs = parsed.documents || [];
      bodyHTML = docs.length
        ? '<div class="table-wrap"><table class="data-table"><thead><tr><th>File</th><th>Processed</th><th>Fields</th></tr></thead><tbody>'
          + docs.map(function (d) {
              return '<tr><td class="mono">' + esc(d.fileName || '') + '</td>'
                + '<td>' + (d.processedAt ? formatDate(d.processedAt) : '\u2014') + '</td>'
                + '<td><pre class="result-pre" style="max-height:160px">' + esc(JSON.stringify(d.fields || {}, null, 2)) + '</pre></td></tr>';
            }).join('')
          + '</tbody></table></div>'
        : '<p class="no-replies">No documents recorded yet.</p>';
    }

    var modal = document.createElement('div');
    modal.id = 'rec-view-modal';
    modal.className = 'modal-overlay';
    modal.innerHTML =
      '<div class="modal" style="max-width:760px">'
      + '<div class="modal-header"><h2 class="rec-view-title"></h2>'
      +   '<button class="btn-icon" id="rec-view-close">&#10005;</button></div>'
      + '<div class="modal-body">'
      +   '<p class="form-hint">Storage format: <span class="mono">' + esc(rec.storageFormat || '\u2014')
      +   '</span> \u00b7 Version ' + esc(rec.currentVersion) + '</p>'
      +   bodyHTML
      + '</div>'
      + '<div class="modal-footer"><button class="btn btn--ghost" id="rec-view-csv">Export CSV</button>'
      +   '<button class="btn btn--ghost" id="rec-view-cancel">Close</button></div>'
      + '</div>';
    document.body.appendChild(modal);
    modal.querySelector('.rec-view-title').textContent = rec.categoryName || 'Record Set';

    function closeModal() { modal.remove(); }
    document.getElementById('rec-view-close').addEventListener('click', closeModal);
    document.getElementById('rec-view-cancel').addEventListener('click', closeModal);
    document.getElementById('rec-view-csv').addEventListener('click', function () { exportRecordCsv(rec); });
    modal.addEventListener('click', function (e) { if (e.target === modal) closeModal(); });
  }
}

  /* ═══════════════════════════════════════════════════════
     FORUM PAGE
  ═══════════════════════════════════════════════════════ */
  async function forumPage() {
    window.api.requireAuth();

    // Always fetch fresh so inviteCode is populated after backend change
    var me;
    try {
      me = await window.api.get('/users/me');
      window.api.setMe(me);
    } catch (e) {
      me = window.api.getMe();
    }

    renderShell(me);
    var mc = mainContent();

    if (!me || me.userType !== 'ORGANIZATION') {
      mc.innerHTML =
        '<div class="state-panel state-panel--locked">'
        + '<span class="state-icon">&#128274;</span>'
        + '<p class="state-title">Organization Feature</p>'
        + '<p class="state-msg">The forum is available to Organization accounts only. Upgrade your account to collaborate with your team.</p>'
        + '</div>';
      return;
    }

    var taEl = document.getElementById('topbar-actions');
    if (taEl) taEl.innerHTML = '<button class="btn btn--primary btn--sm" id="new-post-btn">+ New Post</button>';

    // Invite code banner — only shown when inviteCode is present
    if (me.inviteCode) {
      var banner = document.createElement('div');
      banner.className = 'invite-banner';
      banner.innerHTML =
        '<span class="invite-label">Org Invite Code</span>'
        + '<code class="invite-code" id="invite-code-val">' + esc(me.inviteCode) + '</code>'
        + '<button class="btn btn--ghost btn--sm" id="copy-invite-btn">Copy</button>';
      mc.parentNode.insertBefore(banner, mc);

      document.getElementById('copy-invite-btn').addEventListener('click', function () {
        var btn = document.getElementById('copy-invite-btn');
        var code = document.getElementById('invite-code-val').textContent;
        navigator.clipboard.writeText(code).then(function () {
          btn.textContent = 'Copied!';
          setTimeout(function () { btn.textContent = 'Copy'; }, 2000);
        }).catch(function () {
          showToast('Could not copy — select and copy manually.', 'error');
        });
      });
    }

    var activePostId = null;

    async function loadPosts() {
      mc.innerHTML = loadingHTML(4);
      var posts = [];
      try { posts = await window.api.get('/forum/posts'); } catch (e) { mc.innerHTML = errorHTML(e.message); return; }

      if (!posts.length) {
        mc.innerHTML = emptyHTML('&#9825;', 'No posts yet', 'Start a discussion with your team.');
        return;
      }

      mc.innerHTML =
        '<div class="forum-layout">'
        + '<div class="forum-list" id="forum-list">'
        + posts.map(function (p) {
            return '<div class="forum-post-item" data-id="' + esc(p.id) + '">'
              + '<div class="post-item-title"></div>'
              + '<div class="post-item-meta"><span class="post-author"></span><span class="post-time">' + relativeDate(p.createdAt) + '</span>'
              +   '<span class="post-replies">' + (p.replyCount || 0) + ' replies</span></div>'
              + '</div>';
          }).join('')
        + '</div>'
        + '<div class="forum-detail" id="forum-detail">'
        +   emptyHTML('&#9825;', 'Select a post', 'Click a post on the left to read and reply.')
        + '</div>'
        + '</div>';

      posts.forEach(function (p, i) {
        var items = mc.querySelectorAll('.forum-post-item');
        if (!items[i]) return;
        items[i].querySelector('.post-item-title').textContent = p.title;
        items[i].querySelector('.post-author').textContent = p.authorEmail;
      });

      mc.querySelectorAll('.forum-post-item').forEach(function (item) {
        item.addEventListener('click', function () {
          mc.querySelectorAll('.forum-post-item').forEach(function (el) { el.classList.remove('forum-post-item--active'); });
          item.classList.add('forum-post-item--active');
          loadPostDetail(item.dataset.id, posts.find(function (p) { return String(p.id) === item.dataset.id; }));
        });
      });
    }

    async function loadPostDetail(postId, post) {
      activePostId = postId;
      var detail = document.getElementById('forum-detail');
      if (!detail || !post) return;
      detail.innerHTML = loadingHTML(2);

      var replies = [];
      try { replies = await window.api.get('/forum/posts/' + encodeURIComponent(postId) + '/replies'); } catch (e) { /* non-fatal */ }

      detail.innerHTML =
        '<div class="post-detail">'
        + '<div class="post-detail-header">'
        +   '<h2 class="post-detail-title"></h2>'
        +   '<div class="post-detail-meta"><span class="post-author"></span><span class="post-time">' + relativeDate(post.createdAt) + '</span></div>'
        + '</div>'
        + '<div class="post-detail-body"><p class="post-message"></p></div>'
        + '<div class="reply-list" id="reply-list">'
        + (replies.length
            ? replies.map(function (r) {
                return '<div class="reply-item">'
                  + '<div class="reply-meta"><span class="reply-author"></span><span class="reply-time">' + relativeDate(r.createdAt) + '</span></div>'
                  + '<p class="reply-message"></p>'
                  + '</div>';
              }).join('')
            : '<p class="no-replies">No replies yet. Be the first to reply.</p>')
        + '</div>'
        + '<div class="reply-compose">'
        +   '<textarea id="reply-text" rows="3" placeholder="Write a reply\u2026"></textarea>'
        +   '<button class="btn btn--primary btn--sm" id="reply-submit">Reply</button>'
        + '</div>'
        + '</div>';

      detail.querySelector('.post-detail-title').textContent = post.title;
      detail.querySelector('.post-detail .post-author').textContent = post.authorEmail;
      detail.querySelector('.post-message').textContent = post.message;

      var replyEls = detail.querySelectorAll('.reply-item');
      replies.forEach(function (r, i) {
        if (!replyEls[i]) return;
        replyEls[i].querySelector('.reply-author').textContent = r.authorEmail;
        replyEls[i].querySelector('.reply-message').textContent = r.message;
      });

      document.getElementById('reply-submit').addEventListener('click', async function () {
        var text = document.getElementById('reply-text').value.trim();
        if (!text) return;
        var btn = document.getElementById('reply-submit');
        btn.disabled = true;
        btn.textContent = 'Sending\u2026';
        try {
          await window.api.post('/forum/posts/' + encodeURIComponent(postId) + '/replies', { message: text });
          document.getElementById('reply-text').value = '';
          showToast('Reply posted.', 'success');
          loadPostDetail(postId, post);
        } catch (e) {
          showToast(e.message, 'error');
          btn.disabled = false;
          btn.textContent = 'Reply';
        }
      });
    }

    function openNewPostModal() {
      var existing = document.getElementById('post-modal');
      if (existing) existing.remove();
      var modal = document.createElement('div');
      modal.id = 'post-modal';
      modal.className = 'modal-overlay';
      modal.innerHTML =
        '<div class="modal">'
        + '<div class="modal-header"><h2>New Post</h2>'
        +   '<button class="btn-icon" id="post-modal-close">&#10005;</button></div>'
        + '<div class="modal-body">'
        +   '<div class="form-field"><label for="post-title">Title</label><input type="text" id="post-title" /></div>'
        +   '<div class="form-field"><label for="post-message">Message</label><textarea id="post-message" rows="5"></textarea></div>'
        +   '<div id="post-modal-error" class="form-error" hidden></div>'
        + '</div>'
        + '<div class="modal-footer">'
        +   '<button class="btn btn--ghost" id="post-modal-cancel">Cancel</button>'
        +   '<button class="btn btn--primary" id="post-modal-save">Post</button>'
        + '</div>'
        + '</div>';
      document.body.appendChild(modal);

      function closeModal() { modal.remove(); }
      document.getElementById('post-modal-close').addEventListener('click', closeModal);
      document.getElementById('post-modal-cancel').addEventListener('click', closeModal);
      modal.addEventListener('click', function (e) { if (e.target === modal) closeModal(); });

      document.getElementById('post-modal-save').addEventListener('click', async function () {
        var title = document.getElementById('post-title').value.trim();
        var message = document.getElementById('post-message').value.trim();
        var errEl = document.getElementById('post-modal-error');
        if (!title || !message) { errEl.textContent = 'Title and message are required.'; errEl.hidden = false; return; }
        errEl.hidden = true;
        try {
          await window.api.post('/forum/posts', { title: title, message: message });
          closeModal();
          showToast('Post created.', 'success');
          loadPosts();
        } catch (e) { errEl.textContent = e.message; errEl.hidden = false; }
      });
    }

    loadPosts();

    var newPostBtn = document.getElementById('new-post-btn');
    if (newPostBtn) newPostBtn.addEventListener('click', openNewPostModal);
  }


  /* ═══════════════════════════════════════════════════════
     STATIC DOCUMENT PAGES
  ═══════════════════════════════════════════════════════ */
  async function renderStaticPage(bodyHTML) {
    // readable signed-out (terms must be viewable before signup)
    if (window.api.getToken()) {
      var me = await ensureMe();
      renderShell(me);
      mainContent().innerHTML = bodyHTML;
      return;
    }
    app.innerHTML =
      '<div class="doc-public">'
      + '<header class="lp-header">'
      +   '<a href="index.html" class="landing-brand">' + LOGO_SVG + '<span class="brand-name">DocuMorph</span></a>'
      +   '<nav class="lp-nav">'
      +     '<a href="terms.html" class="lp-nav-link">Terms</a>'
      +     '<a href="guide.html" class="lp-nav-link">Guide</a>'
      +     '<a href="sign-in.html" class="btn btn--ghost">Sign In</a>'
      +   '</nav>'
      + '</header>'
      + '<div class="doc-public-inner">'
      +   '<h1 class="page-title" style="margin-bottom:1.5rem">' + esc(PAGE_TITLES[PAGE] || '') + '</h1>'
      +   bodyHTML
      + '</div>'
      + '</div>';
  }

  function termsPage() {
    renderStaticPage(
      '<div class="doc-prose">'
      + '<p class="doc-updated">Last updated: 30 August 2026</p>'

      + '<div class="doc-callout"><strong>Plain summary.</strong> DocuMorph reads your documents and pulls out '
      + 'structured data. Your documents stay on the server running this software and are processed by local '
      + 'models — nothing is sent to an external AI service. Extraction is automated and <em>will</em> sometimes '
      + 'be wrong, so check anything that matters. You can delete your data at any time.</div>'

      + '<h2>1. Acceptance</h2>'
      + '<p>By creating an account or using DocuMorph ("the Service"), you agree to these terms. If you do not '
      + 'agree, do not use the Service.</p>'

      + '<h2>2. What the Service does</h2>'
      + '<p>DocuMorph accepts documents you upload, performs optical character recognition and table detection on '
      + 'them, and uses a language model to produce summaries or extract named fields. Extracted text is indexed '
      + 'so you can search across your documents.</p>'

      + '<h2>3. Accounts and workspaces</h2>'
      + '<p>You are responsible for your account credentials and for activity under your account. Each account '
      + 'receives a private workspace; documents and extractions are visible only within that workspace.</p>'
      + '<p>Organization accounts can invite others using an invite code. <strong>Anyone holding a valid invite '
      + 'code can join that organization</strong> and gain access to its shared forum. Treat invite codes as '
      + 'sensitive and regenerate or rotate them if one is exposed.</p>'

      + '<h2>4. Your content</h2>'
      + '<p>You retain all rights to the documents you upload and to the data extracted from them. You grant the '
      + 'Service only the permission technically required to store, process, index and display that content back '
      + 'to you.</p>'
      + '<p>You confirm that you have the right to upload each document and that doing so does not breach any '
      + 'confidentiality obligation, contract, or law that applies to you.</p>'

      + '<h2>5. How your documents are handled</h2>'
      + '<ul>'
      +   '<li>Uploaded files are written to disk on the server running the Service and referenced by your session.</li>'
      +   '<li>Text extraction and language-model inference run <strong>locally</strong>. Document contents are not '
      +   'transmitted to any third-party AI provider.</li>'
      +   '<li>Extracted text is split into chunks, converted into numerical embeddings, and stored in a vector '
      +   'database scoped to your workspace so that search works.</li>'
      +   '<li>Deleting a session removes its database records, its files from disk, and its entries from the '
      +   'search index.</li>'
      + '</ul>'

      + '<h2>6. Accuracy — please read</h2>'
      + '<p>Extraction is automated and <strong>is not guaranteed to be accurate or complete</strong>. Known '
      + 'limitations include:</p>'
      + '<ul>'
      +   '<li>Optical character recognition can misread poor scans, unusual fonts, or handwriting.</li>'
      +   '<li>Table structure may be reconstructed incorrectly, particularly where one logical row spans several '
      +   'printed lines.</li>'
      +   '<li>The language model may return an empty value for a field that is present, or occasionally produce a '
      +   'value not supported by the document.</li>'
      + '</ul>'
      + '<p><strong>Do not rely on extracted output for financial, legal, medical, or other consequential '
      + 'decisions without verifying it against the original document.</strong> The Service is a drafting and '
      + 'review aid, not a system of record.</p>'

      + '<h2>7. Acceptable use</h2>'
      + '<p>You may not use the Service to:</p>'
      + '<ul>'
      +   '<li>upload material you have no right to process, or that is unlawful;</li>'
      +   '<li>attempt to access another user\u2019s workspace, documents, or extractions;</li>'
      +   '<li>probe, scan, or disrupt the Service or the infrastructure it runs on;</li>'
      +   '<li>submit volumes intended to exhaust processing capacity or storage.</li>'
      + '</ul>'

      + '<h2>8. Availability and limits</h2>'
      + '<p>The Service is provided on an "as is" and "as available" basis with no uptime commitment. Documents '
      + 'are processed one at a time and a single document commonly takes 15\u201320 minutes; concurrent uploads '
      + 'queue rather than run in parallel. File type and size limits apply and may change without notice.</p>'

      + '<h2>9. Deletion and retention</h2>'
      + '<p>You may delete any session at any time, which removes the stored file, its extraction records, and its '
      + 'search index entries. Backups, where taken, may retain deleted content for a limited period before being '
      + 'overwritten.</p>'

      + '<h2>10. Third-party components</h2>'
      + '<p>The Service is built on open-source components including PaddleOCR, DocTR, Table Transformer, '
      + 'DeepSeek-OCR, BAAI/bge-m3, Qdrant, and models served through Ollama. These are governed by their own '
      + 'licences. Their inclusion does not imply endorsement by their authors.</p>'

      + '<h2>11. Limitation of liability</h2>'
      + '<p>To the fullest extent permitted by law, the operators of the Service are not liable for any indirect, '
      + 'incidental, or consequential loss, nor for any loss of data, profit, or opportunity arising from your use '
      + 'of the Service or from reliance on its output.</p>'

      + '<h2>12. Changes</h2>'
      + '<p>These terms may be updated. Continued use after a change constitutes acceptance of the revised terms. '
      + 'The date at the top reflects the most recent revision.</p>'

      + '<h2>13. Contact</h2>'
      + '<p>Questions about these terms should be directed to the operator of this deployment.</p>'

      + '<div class="doc-callout doc-callout--warn"><strong>Note.</strong> This document is a template written for '
      + 'a project deployment. It is not legal advice. Have it reviewed by a qualified professional before relying '
      + 'on it commercially or in a jurisdiction with specific data-protection requirements.</div>'
      + '</div>'
    );
  }

  function guidePage() {
    renderStaticPage(
      '<div class="doc-prose">'
      + '<p class="doc-updated">A practical guide to getting good results.</p>'

      + '<h2>1. What you can upload</h2>'
      + '<p>Supported types: <span class="mono">PDF, DOCX, TXT, PNG, JPG, JPEG, TIF, TIFF</span>. Maximum 50 MB '
      + 'per file. Anything else is rejected before processing starts.</p>'
      + '<p>Three kinds of document are handled differently, automatically:</p>'
      + '<ul>'
      +   '<li><strong>Digital PDFs</strong> \u2014 text is read directly. Fastest and most accurate.</li>'
      +   '<li><strong>Printer scans</strong> \u2014 deskewed, then read with OCR.</li>'
      +   '<li><strong>Phone photos</strong> \u2014 perspective-corrected and shadow-reduced before OCR. Hardest '
      +   'case; results improve markedly with a flat, evenly lit, in-focus photo.</li>'
      + '</ul>'

      + '<h2>2. Choosing an extraction mode</h2>'
      + '<table class="doc-table"><thead><tr><th>Mode</th><th>Use it when</th></tr></thead><tbody>'
      + '<tr><td><strong>Full Text</strong></td><td>You want the whole document cleaned up and readable.</td></tr>'
      + '<tr><td><strong>Summary</strong></td><td>You want three to five sentences describing the document.</td></tr>'
      + '<tr><td><strong>Key Insights</strong></td><td>You want the main facts and figures as bullet points.</td></tr>'
      + '<tr><td><strong>Custom Fields</strong></td><td>You want specific named values, or an answer to a question.</td></tr>'
      + '</tbody></table>'
      + '<p>The Custom Prompt box only appears \u2014 and is only used \u2014 when the mode is <strong>Custom '
      + 'Fields</strong>. Filling it in under any other mode has no effect.</p>'

      + '<h2>3. Writing a good custom prompt</h2>'
      + '<p>There are exactly two shapes, and the wording decides which one you get.</p>'

      + '<h3>A list of fields</h3>'
      + '<p><span class="mono">Extract: invoice_number, invoice_date, total_amount</span></p>'
      + '<p>Returns one key per field. Rules that decide whether your text is read as a list:</p>'
      + '<ul>'
      +   '<li>Separate fields with commas.</li>'
      +   '<li>Keep each field to <strong>four words or fewer</strong>.</li>'
      +   '<li>Use no <span class="mono">. ? ! ; :</span> inside a field name \u2014 a single full stop pushes the '
      +   'whole prompt into question mode.</li>'
      + '</ul>'
      + '<p>Field names are treated as <em>concepts</em>, not literal search strings. Asking for '
      + '<span class="mono">total_amount</span> will find a line labelled "Grand Total" or "Net Amount". You can '
      + 'also ask for something the document implies rather than states \u2014 '
      + '<span class="mono">area_of_expertise</span> on a CV \u2014 and it will be inferred from the content.</p>'

      + '<h3>A direct question</h3>'
      + '<p><span class="mono">Which line item has the highest value?</span></p>'
      + '<p>Returns a single <span class="mono">answer</span>. Use this for comparisons, totals, and anything '
      + 'needing reasoning across rows. You cannot mix a field list and a question in one prompt \u2014 the whole '
      + 'prompt is treated as one or the other.</p>'

      + '<h2>4. Categories</h2>'
      + '<p>A category is a saved template: extraction mode, summary style, and custom prompt stored together. '
      + 'Choose <strong>Category Run</strong> when uploading and every file inherits those settings, so you are not '
      + 're-typing the same prompt for every invoice.</p>'
      + '<ul>'
      +   '<li><strong>Document</strong> type \u2014 each upload is independent.</li>'
      +   '<li><strong>Record</strong> type \u2014 extractions accumulate into a record set over time.</li>'
      + '</ul>'

      + '<h2>5. Record sets</h2>'
      + '<p>Create one from a Record-type category, then run documents against that category. Each completed '
      + 'extraction is appended automatically. One record set per category.</p>'
      + '<ul>'
      +   '<li><strong>Document-wise</strong> \u2014 one entry per file, kept separate.</li>'
      +   '<li><strong>Consolidated</strong> \u2014 field values merged across every file, so you can see all '
      +   'invoice numbers together.</li>'
      + '</ul>'

      + '<h2>6. Search</h2>'
      + '<p>Search combines meaning-based matching with exact-term matching, so both "seller address" and a '
      + 'specific invoice number work. Only documents that finished processing are searchable.</p>'
      + '<p>Results below a relevance threshold are hidden rather than padding the list. If a document you deleted '
      + 'still appears, use <strong>Rebuild search index</strong> on the Search page.</p>'

      + '<h2>7. How long it takes</h2>'
      + '<p>Roughly <strong>15\u201320 minutes per document</strong>, dominated by OCR and table detection. '
      + 'Documents are processed <strong>one at a time</strong> \u2014 uploading five means the fifth starts after '
      + 'the first four finish. The progress bar reflects real per-page progress; if it appears stuck, the page is '
      + 'genuinely still being processed.</p>'
      + '<p>Uploading the same document again because it "seems stuck" just adds another job to the back of the '
      + 'queue and makes everything slower.</p>'

      + '<h2>8. When something fails</h2>'
      + '<ul>'
      +   '<li>A failed job shows a <strong>Retry</strong> button on the results page \u2014 no need to re-upload.</li>'
      +   '<li>"No text or tables could be extracted" usually means the file is corrupt, empty, or an image with no '
      +   'legible text.</li>'
      +   '<li>If a field returns empty, open the <strong>Extracted Text</strong> tab first. If the value is not '
      +   'there, OCR missed it and no prompt will recover it.</li>'
      + '</ul>'

      + '<h2>9. Known limitations</h2>'
      + '<ul>'
      +   '<li>A table row printed across several lines can be split into multiple rows. Values are captured but '
      +   'may be grouped oddly.</li>'
      +   '<li>The language model occasionally returns nothing for a field that is present, or a value the document '
      +   'does not support.</li>'
      +   '<li>Handwriting and low-resolution scans are unreliable.</li>'
      + '</ul>'
      + '<p><strong>Always check extracted values against the original before acting on them.</strong> Use '
      + '<em>View original</em> on the results page to compare side by side.</p>'

      + '<h2>10. Working as a team</h2>'
      + '<p>Organization accounts share a forum. The invite code is shown on the Forum page \u2014 anyone with it '
      + 'can join your organization, so share it deliberately.</p>'
      + '</div>'
    );
  }



  /* ═══════════════════════════════════════════════════════
     ROUTER / BOOT
  ═══════════════════════════════════════════════════════ */
  var ROUTES = {
    index:       landingPage,
    'sign-in':   signInPage,
    'sign-up':   signUpPage,
    setup:       setupPage,
    home:        homePage,
    upload:      uploadPage,
    processing:  processingPage,
    results:     resultsPage,
    activity:    activityPage,
    search: searchPage,
    categories:  categoriesPage,
    records:     recordsPage,
    'org-forum': forumPage,
    terms:       termsPage,
    guide:       guidePage,
  };

  document.addEventListener('DOMContentLoaded', function () {
    if (!app) return;
    var handler = ROUTES[PAGE];
    if (handler) {
      handler();
    } else {
      app.innerHTML = errorHTML('Page not found.');
    }
  });

})();