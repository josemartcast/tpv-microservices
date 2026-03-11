(function () {
  "use strict";

  const STORAGE_KEY = "tpv.pda.session.v1";
  const CACHE_KEY = "tpv.pda.cache.v1";
  const QUEUE_KEY = "tpv.pda.queue.v1";
  const CONFLICTS_KEY = "tpv.pda.conflicts.v1";
  const HEARTBEAT_MS = 10000;
  const TABLES_POLL_MS = 6000;
  const QUEUE_POLL_MS = 7000;
  const QUEUE_RETRY_LIMIT = 8;
  const TOAST_MS = 3000;
  const PRODUCT_COLORS = ["#323f5f", "#2f3f66", "#589040", "#d0822c", "#2f7f82", "#6b417e", "#4f687f"];
  const DEFAULT_BRAND = "TPV PDA";

  const state = {
    apiBase: "",
    token: "",
    username: "",
    terminalId: "",
    tables: [],
    categories: [],
    salonFilter: "ALL",
    productsByCategory: new Map(),
    activeCategoryId: null,
    currentTableNumber: null,
    currentTicket: null,
    selectedLineId: null,
    currentQtyInput: "",
    heartbeatTimer: null,
    tablesPollTimer: null,
    sendPreview: null,
    paymentSummary: null,
    lockLeaseExpiresAt: null,
    methodChoice: null,
    payLinesAmount: 0,
    actionQueue: [],
    queueConflicts: [],
    processingQueue: false,
    queueTimer: null,
    cache: {
      tables: [],
      categories: [],
      productsByCategory: {},
      ticketsById: {},
      sendPreviewByTicketId: {},
      paymentSummaryByTicketId: {}
    },
    lastLatencyMs: null,
    lastError: null,
    errors: [],
    numberPad: {
      allowDecimal: false,
      resolve: null
    }
  };

  const els = {
    loginScreen: byId("loginScreen"),
    tablesScreen: byId("tablesScreen"),
    orderScreen: byId("orderScreen"),
    loginForm: byId("loginForm"),
    brandLabel: byId("brandLabel"),
    usernameInput: byId("usernameInput"),
    passwordInput: byId("passwordInput"),
    terminalInput: byId("terminalInput"),
    apiBaseInput: byId("apiBaseInput"),
    networkBadge: byId("networkBadge"),
    sessionBadge: byId("sessionBadge"),
    conflictsBtn: byId("conflictsBtn"),
    errorsBtn: byId("errorsBtn"),
    conflictsDialog: byId("conflictsDialog"),
    conflictsList: byId("conflictsList"),
    errorsDialog: byId("errorsDialog"),
    errorsList: byId("errorsList"),
    refreshTablesBtn: byId("refreshTablesBtn"),
    logoutBtn: byId("logoutBtn"),
    tablesGrid: byId("tablesGrid"),
    tablesInfo: byId("tablesInfo"),
    salonFilter: byId("salonFilter"),
    backToTablesBtn: byId("backToTablesBtn"),
    moveTableBtn: byId("moveTableBtn"),
    orderTitle: byId("orderTitle"),
    orderMeta: byId("orderMeta"),
    ticketLines: byId("ticketLines"),
    ticketTotal: byId("ticketTotal"),
    editLineBtn: byId("editLineBtn"),
    deleteLineBtn: byId("deleteLineBtn"),
    qtyInput: byId("qtyInput"),
    qtyPadButtons: Array.from(document.querySelectorAll(".num-pad-key")),
    categoryTabs: byId("categoryTabs"),
    productsGrid: byId("productsGrid"),
    sendPreviewLabel: byId("sendPreviewLabel"),
    sendAllBtn: byId("sendAllBtn"),
    sendBarBtn: byId("sendBarBtn"),
    sendKitchenBtn: byId("sendKitchenBtn"),
    paymentPendingLabel: byId("paymentPendingLabel"),
    payFullBtn: byId("payFullBtn"),
    payPartialBtn: byId("payPartialBtn"),
    payLinesBtn: byId("payLinesBtn"),
    methodDialog: byId("methodDialog"),
    methodChoiceButtons: Array.from(document.querySelectorAll(".method-choice")),
    payLinesDialog: byId("payLinesDialog"),
    payLinesPendingLabel: byId("payLinesPendingLabel"),
    payLinesList: byId("payLinesList"),
    payLinesSelectedLabel: byId("payLinesSelectedLabel"),
    payLinesApplyBtn: byId("payLinesApplyBtn"),
    moveTableDialog: byId("moveTableDialog"),
    moveTableInfo: byId("moveTableInfo"),
    moveTableList: byId("moveTableList"),
    numberPadDialog: byId("numberPadDialog"),
    numberPadTitle: byId("numberPadTitle"),
    numberPadDisplay: byId("numberPadDisplay"),
    numberPadOkBtn: byId("numberPadOkBtn"),
    numberPadKeys: Array.from(document.querySelectorAll(".num-modal-key")),
    toast: byId("toast")
  };

  let toastTimer = null;

  boot();

  function boot() {
    initViewportTracking();
    bindEvents();
    renderQtyInput();
    els.apiBaseInput.value = window.location.origin;
    loadSession();
    loadCache();
    loadQueue();
    loadConflicts();
    updateOnlineBadge();
    window.addEventListener("online", function () {
      updateOnlineBadge();
      processQueue();
    });
    window.addEventListener("offline", updateOnlineBadge);
    window.addEventListener("pagehide", onPageHide);
    window.addEventListener("beforeunload", onPageHide);
    registerServiceWorker();
    startQueueWorker();
    recoverOrShowLogin();
  }

  function initViewportTracking() {
    const onViewportChange = function () {
      applyViewportMetrics();
    };
    applyViewportMetrics();
    window.addEventListener("resize", onViewportChange, { passive: true });
    window.addEventListener("orientationchange", onViewportChange, { passive: true });
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", onViewportChange, { passive: true });
      window.visualViewport.addEventListener("scroll", onViewportChange, { passive: true });
    }
  }

  function applyViewportMetrics() {
    const vv = window.visualViewport;
    const width = Math.max(1, Math.round((vv ? vv.width : window.innerWidth) || window.innerWidth || 1));
    const height = Math.max(1, Math.round((vv ? vv.height : window.innerHeight) || window.innerHeight || 1));
    const orientation = width >= height ? "landscape" : "portrait";
    const compact = width < 430;

    document.documentElement.style.setProperty("--app-width", width + "px");
    document.documentElement.style.setProperty("--app-height", height + "px");
    document.documentElement.style.setProperty("--vh", (height * 0.01).toFixed(4) + "px");
    document.documentElement.style.setProperty("--vw", (width * 0.01).toFixed(4) + "px");

    document.body.classList.toggle("pda-landscape", orientation === "landscape");
    document.body.classList.toggle("pda-portrait", orientation === "portrait");
    document.body.classList.toggle("pda-compact", compact);

    const updateTopbarHeight = function () {
      const topbar = document.querySelector(".topbar");
      const topbarHeight = topbar ? Math.max(50, Math.round(topbar.getBoundingClientRect().height)) : 56;
      document.documentElement.style.setProperty("--topbar-height", topbarHeight + "px");
    };
    updateTopbarHeight();
    window.requestAnimationFrame(updateTopbarHeight);
  }

  function bindEvents() {
    els.loginForm.addEventListener("submit", onLoginSubmit);
    els.refreshTablesBtn.addEventListener("click", refreshTablesSafe);
    els.salonFilter.addEventListener("change", function () {
      state.salonFilter = els.salonFilter.value || "ALL";
      renderTables();
    });
    els.logoutBtn.addEventListener("click", logout);
    els.backToTablesBtn.addEventListener("click", async function () { await leaveTableAndBack(); });
    els.moveTableBtn.addEventListener("click", onMoveTable);
    els.sendAllBtn.addEventListener("click", function () { sendComanda("ALL"); });
    els.sendBarBtn.addEventListener("click", function () { sendComanda("BAR"); });
    els.sendKitchenBtn.addEventListener("click", function () { sendComanda("COCINA"); });
    els.payFullBtn.addEventListener("click", onPayFull);
    els.payPartialBtn.addEventListener("click", onPayPartial);
    els.payLinesBtn.addEventListener("click", onPayByLines);
    els.editLineBtn.addEventListener("click", onEditSelectedLine);
    els.deleteLineBtn.addEventListener("click", onDeleteSelectedLine);
    els.qtyPadButtons.forEach(function (btn) {
      btn.addEventListener("click", function () {
        onQtyPadKey(btn.dataset.key || "");
      });
    });
    els.conflictsBtn.addEventListener("click", openConflictsDialog);
    els.errorsBtn.addEventListener("click", openErrorsDialog);
    els.methodChoiceButtons.forEach(function (btn) {
      btn.addEventListener("click", function () {
        state.methodChoice = btn.dataset.method || null;
        if (els.methodDialog.open) { els.methodDialog.close("selected"); }
      });
    });
    els.numberPadKeys.forEach(function (btn) {
      btn.addEventListener("click", function () {
        onNumberPadKey(btn.dataset.key || "");
      });
    });
    els.numberPadOkBtn.addEventListener("click", function () {
      if (els.numberPadDialog.open) {
        els.numberPadDialog.close("ok");
      }
    });
    els.numberPadDialog.addEventListener("close", function () {
      if (typeof state.numberPad.resolve === "function") {
        const resolver = state.numberPad.resolve;
        state.numberPad.resolve = null;
        resolver(els.numberPadDialog.returnValue === "ok" ? els.numberPadDisplay.value : null);
      }
    });
  }

  function recoverOrShowLogin() {
    if (!state.token || !state.username || !state.terminalId || !state.apiBase) {
      setBrand(DEFAULT_BRAND);
      showScreen("login");
      return;
    }
    els.apiBaseInput.value = state.apiBase;
    els.terminalInput.value = state.terminalId;
    showScreen("tables");
    if (state.cache.tables.length) {
      state.tables = state.cache.tables.slice();
      renderTables();
      els.tablesInfo.textContent = "Mostrando cache local de mesas";
    }
    refreshTablesSafe();
    startTablesPolling();
    processQueue();
    refreshBusinessBrand();
  }

  function loadSession() {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) { return; }
    try {
      const v = JSON.parse(raw);
      state.apiBase = normalizeBase(v.apiBase || window.location.origin);
      state.token = v.token || "";
      state.username = v.username || "";
      state.terminalId = (v.terminalId || "").trim();
      els.usernameInput.value = state.username;
      els.terminalInput.value = state.terminalId;
      els.apiBaseInput.value = state.apiBase;
      updateSessionBadge();
    } catch (_err) {
      localStorage.removeItem(STORAGE_KEY);
    }
  }

  function loadCache() {
    const raw = localStorage.getItem(CACHE_KEY);
    if (!raw) {
      return;
    }
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === "object") {
        state.cache.tables = Array.isArray(parsed.tables) ? parsed.tables : [];
        state.cache.categories = Array.isArray(parsed.categories) ? parsed.categories : [];
        state.cache.productsByCategory = parsed.productsByCategory && typeof parsed.productsByCategory === "object"
          ? parsed.productsByCategory
          : {};
        state.cache.ticketsById = parsed.ticketsById && typeof parsed.ticketsById === "object"
          ? parsed.ticketsById
          : {};
        state.cache.sendPreviewByTicketId = parsed.sendPreviewByTicketId && typeof parsed.sendPreviewByTicketId === "object"
          ? parsed.sendPreviewByTicketId
          : {};
        state.cache.paymentSummaryByTicketId = parsed.paymentSummaryByTicketId && typeof parsed.paymentSummaryByTicketId === "object"
          ? parsed.paymentSummaryByTicketId
          : {};
      }
    } catch (_err) {
      localStorage.removeItem(CACHE_KEY);
    }
  }

  function saveCache() {
    localStorage.setItem(CACHE_KEY, JSON.stringify(state.cache));
  }

  function loadQueue() {
    const raw = localStorage.getItem(QUEUE_KEY);
    if (!raw) {
      state.actionQueue = [];
      return;
    }
    try {
      const parsed = JSON.parse(raw);
      state.actionQueue = Array.isArray(parsed) ? parsed : [];
    } catch (_err) {
      state.actionQueue = [];
      localStorage.removeItem(QUEUE_KEY);
    }
  }

  function saveQueue() {
    localStorage.setItem(QUEUE_KEY, JSON.stringify(state.actionQueue));
    updateSessionBadge();
  }

  function loadConflicts() {
    const raw = localStorage.getItem(CONFLICTS_KEY);
    if (!raw) {
      state.queueConflicts = [];
      updateSessionBadge();
      return;
    }
    try {
      const parsed = JSON.parse(raw);
      state.queueConflicts = Array.isArray(parsed) ? parsed : [];
    } catch (_err) {
      state.queueConflicts = [];
      localStorage.removeItem(CONFLICTS_KEY);
    }
    updateSessionBadge();
  }

  function saveConflicts() {
    localStorage.setItem(CONFLICTS_KEY, JSON.stringify(state.queueConflicts));
    updateSessionBadge();
  }

  function startQueueWorker() {
    if (state.queueTimer) {
      return;
    }
    state.queueTimer = window.setInterval(function () {
      processQueue();
    }, QUEUE_POLL_MS);
  }

  function persistSession() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      apiBase: state.apiBase,
      token: state.token,
      username: state.username,
      terminalId: state.terminalId
    }));
  }

  function clearSession() {
    localStorage.removeItem(STORAGE_KEY);
    state.token = "";
    state.username = "";
    state.terminalId = "";
    state.currentTableNumber = null;
    state.currentTicket = null;
    state.sendPreview = null;
    state.paymentSummary = null;
    state.lockLeaseExpiresAt = null;
    stopHeartbeat();
    stopTablesPolling();
    renderPaymentSummary();
    updateSessionBadge();
    updateBackendBadge("OFFLINE");
  }

  async function onLoginSubmit(event) {
    event.preventDefault();
    const username = els.usernameInput.value.trim();
    const password = els.passwordInput.value;
    const terminalId = sanitizeTerminalId(els.terminalInput.value);
    const apiBase = normalizeBase(els.apiBaseInput.value || window.location.origin);
    if (!username || !password || !terminalId || !apiBase) {
      toast("Completa usuario, password, terminal y API URL");
      return;
    }
    try {
      disableLogin(true);
      const loginRes = await fetchJson(apiBase + "/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: username, password: password })
      });
      state.apiBase = apiBase;
      state.token = loginRes.accessToken || "";
      state.username = username;
      state.terminalId = terminalId;
      if (!state.token) { throw new Error("Login sin accessToken"); }
      persistSession();
      updateSessionBadge();
      showScreen("tables");
      await refreshTables();
      startTablesPolling();
      processQueue();
      await refreshBusinessBrand();
      toast("Sesion iniciada en " + terminalId);
    } catch (err) {
      pushError(err);
      toast("Login error: " + err.message);
    } finally {
      disableLogin(false);
    }
  }

  function disableLogin(disabled) {
    els.loginForm.querySelectorAll("input,button").forEach(function (node) { node.disabled = disabled; });
  }

  async function refreshTablesSafe() {
    try {
      await refreshTables();
    } catch (err) {
      pushError(err);
      if (state.cache.tables.length) {
        state.tables = state.cache.tables.slice();
        renderTables();
        els.tablesInfo.textContent = "Sin conexion. Mostrando cache local.";
        toast("Sin conexion: usando mesas en cache");
        return;
      }
      toast("No se pudo cargar mesas: " + err.message);
    }
  }

  async function refreshTables() {
    const list = await apiJson("/api/v1/pos/salon/tables", { method: "GET" });
    state.tables = Array.isArray(list) ? list : [];
    state.cache.tables = state.tables.slice();
    saveCache();
    renderSalonFilter();
    renderTables();
    els.tablesInfo.textContent = "Terminal " + state.terminalId + " | " + new Date().toLocaleTimeString();
  }

  function renderTables() {
    els.tablesGrid.replaceChildren();
    const filtered = state.tables.filter(function (table) {
      if (!state.salonFilter || state.salonFilter === "ALL") {
        return true;
      }
      return String(table.salonName || "").toLowerCase() === state.salonFilter.toLowerCase();
    });
    filtered.forEach(function (table) {
      const card = document.createElement("article");
      card.className = "table-card " + tableCardClass(table);

      const head = document.createElement("div");
      head.className = "table-header";
      head.innerHTML = "<span class='table-number'>Mesa " + escapeHtml(String(table.tableNumber)) + "</span>" +
        "<span class='table-elapsed'>" + elapsedText(table.elapsedMinutes) + "</span>";
      card.appendChild(head);
      if (table.tableAlias) {
        const alias = document.createElement("div");
        alias.className = "table-alias";
        alias.textContent = "Alias: " + table.tableAlias;
        card.appendChild(alias);
      }

      const status = document.createElement("div");
      status.className = "table-status " + tableStatusClass(table);
      status.textContent = tableStatusText(table);
      card.appendChild(status);

      const total = document.createElement("div");
      total.className = "table-total";
      total.textContent = table.ticketId ? centsToEur(table.totalCents) : "-";
      card.appendChild(total);

      const sub = document.createElement("div");
      sub.className = "muted";
      sub.textContent = table.pendingLines > 0 ? "Pendiente: " + table.pendingLines : "Sin pendiente";
      card.appendChild(sub);

      const foot = document.createElement("div");
      foot.className = "table-footer";
      const btn = document.createElement("button");
      btn.className = "btn btn-secondary";
      btn.type = "button";
      btn.textContent = table.ticketId ? "Entrar" : "Abrir";
      const blockedByOther = isLockedByOther(table);
      btn.disabled = blockedByOther;
      if (blockedByOther) { btn.title = "Bloqueada por " + safeTerminal(table.lockedTerminalId); }
      btn.addEventListener("click", function () { enterTable(table); });
      foot.appendChild(btn);
      const aliasBtn = document.createElement("button");
      aliasBtn.className = "btn btn-secondary";
      aliasBtn.type = "button";
      aliasBtn.textContent = "Alias";
      aliasBtn.addEventListener("click", function () {
        editTableAlias(table);
      });
      foot.appendChild(aliasBtn);
      card.appendChild(foot);
      els.tablesGrid.appendChild(card);
    });
  }

  function renderSalonFilter() {
    const previous = state.salonFilter || "ALL";
    els.salonFilter.replaceChildren();
    const all = document.createElement("option");
    all.value = "ALL";
    all.textContent = "Todos";
    els.salonFilter.appendChild(all);

    const names = Array.from(new Set(state.tables
      .map(function (t) { return String(t.salonName || "").trim(); })
      .filter(Boolean)))
      .sort(function (a, b) { return a.localeCompare(b); });
    names.forEach(function (name) {
      const option = document.createElement("option");
      option.value = name;
      option.textContent = name;
      els.salonFilter.appendChild(option);
    });

    if (names.includes(previous)) {
      state.salonFilter = previous;
    } else {
      state.salonFilter = "ALL";
    }
    els.salonFilter.value = state.salonFilter;
  }

  async function editTableAlias(table) {
    if (!table || !table.tableNumber) {
      return;
    }
    const current = table.tableAlias ? String(table.tableAlias) : "";
    const next = window.prompt("Alias para Mesa " + table.tableNumber + " (" + (table.salonName || "-") + ")", current);
    if (next === null) {
      return;
    }
    try {
      await apiJson("/api/v1/pos/salon/tables/" + table.tableNumber + "/alias", {
        method: "PUT",
        body: JSON.stringify({ alias: next })
      });
      await refreshTables();
      toast("Alias actualizado");
    } catch (err) {
      pushError(err);
      toast("No se pudo actualizar alias: " + err.message);
    }
  }

  async function enterTable(table) {
    if (isLockedByOther(table)) {
      toast("Mesa bloqueada por " + safeTerminal(table.lockedTerminalId));
      return;
    }
    try {
      await lockTable(table.tableNumber);
      state.currentTableNumber = table.tableNumber;
      state.currentTicket = table.ticketId ? await getTicket(table.ticketId) : null;
      state.selectedLineId = null;
      clearQtyInput();
      showScreen("order");
      stopTablesPolling();
      startHeartbeat(table.tableNumber);
      await ensureCatalogLoaded();
      renderOrderHeader();
      renderTicket();
      await refreshSendPreview();
      await refreshPaymentSummary();
      toast("Mesa " + table.tableNumber + " abierta");
    } catch (err) {
      pushError(err);
      toast("No se puede entrar en mesa: " + err.message);
    }
  }

  async function ensureCurrentTicket() {
    if (state.currentTicket && state.currentTicket.id) {
      return state.currentTicket;
    }
    if (!state.currentTableNumber) {
      throw new Error("No hay mesa activa");
    }
    const ticketId = await openTicket(state.currentTableNumber);
    state.currentTicket = await getTicket(ticketId);
    cacheTicket(state.currentTicket);
    return state.currentTicket;
  }

  async function openTicket(tableNumber) {
    try {
      const ticket = await apiJson("/api/v1/pos/salon/tables/" + tableNumber + "/open-ticket", { method: "POST" });
      return ticket.id;
    } catch (err) {
      if (err.status === 409) {
        await refreshTables();
        const current = state.tables.find(function (t) { return t.tableNumber === tableNumber; });
        if (current && current.ticketId) { return current.ticketId; }
      }
      throw err;
    }
  }

  function startTablesPolling() {
    stopTablesPolling();
    state.tablesPollTimer = window.setInterval(refreshTablesSafe, TABLES_POLL_MS);
  }

  function stopTablesPolling() {
    if (state.tablesPollTimer) {
      window.clearInterval(state.tablesPollTimer);
      state.tablesPollTimer = null;
    }
  }

  function startHeartbeat(tableNumber) {
    stopHeartbeat();
    state.heartbeatTimer = window.setInterval(function () {
      heartbeat(tableNumber).catch(function (err) {
        pushError(err);
        toast("Heartbeat lock error: " + err.message);
      });
    }, HEARTBEAT_MS);
  }

  function stopHeartbeat() {
    if (state.heartbeatTimer) {
      window.clearInterval(state.heartbeatTimer);
      state.heartbeatTimer = null;
    }
  }

  async function heartbeat(tableNumber) {
    const lock = await apiJson("/api/v1/pos/salon/tables/" + tableNumber + "/heartbeat", {
      method: "POST",
      body: JSON.stringify({ terminalId: state.terminalId })
    });
    setLocalLockLease(lock);
  }

  async function lockTable(tableNumber) {
    const lock = await apiJson("/api/v1/pos/salon/tables/" + tableNumber + "/lock", {
      method: "POST",
      body: JSON.stringify({ terminalId: state.terminalId })
    });
    setLocalLockLease(lock);
  }

  async function unlockTable(tableNumber) {
    await apiJson("/api/v1/pos/salon/tables/" + tableNumber + "/unlock", {
      method: "POST",
      body: JSON.stringify({ terminalId: state.terminalId })
    });
    state.lockLeaseExpiresAt = null;
  }

  async function leaveTableAndBack() {
    const tableNumber = state.currentTableNumber;
    stopHeartbeat();
    state.currentTableNumber = null;
    state.currentTicket = null;
    state.selectedLineId = null;
    clearQtyInput();
    state.sendPreview = null;
    state.paymentSummary = null;
    renderPaymentSummary();
    showScreen("tables");
    startTablesPolling();
    await refreshTablesSafe();
    if (!tableNumber) { return; }
    try {
      await unlockTable(tableNumber);
    } catch (err) {
      pushError(err);
      toast("No se pudo liberar lock: " + err.message);
    }
  }
  async function ensureCatalogLoaded() {
    if (!state.categories.length) {
      try {
        const categories = await apiJson("/api/v1/pos/categories", { method: "GET" });
        state.categories = (Array.isArray(categories) ? categories : []).filter(function (x) { return x.active !== false; });
        state.cache.categories = state.categories.slice();
        saveCache();
      } catch (_err) {
        state.categories = state.cache.categories.slice();
      }
      state.activeCategoryId = state.categories.length ? state.categories[0].id : null;
      renderCategories();
    }
    if (state.activeCategoryId !== null && !state.productsByCategory.has(state.activeCategoryId)) {
      await loadProducts(state.activeCategoryId);
    }
    renderProducts();
  }

  function renderCategories() {
    els.categoryTabs.replaceChildren();
    state.categories.forEach(function (cat) {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "tab-btn" + (cat.id === state.activeCategoryId ? " active" : "");
      btn.textContent = cat.name;
      btn.addEventListener("click", async function () {
        state.activeCategoryId = cat.id;
        if (!state.productsByCategory.has(cat.id)) { await loadProducts(cat.id); }
        renderCategories();
        renderProducts();
      });
      els.categoryTabs.appendChild(btn);
    });
  }

  async function loadProducts(categoryId) {
    try {
      const products = await apiJson("/api/v1/pos/products?categoryId=" + encodeURIComponent(String(categoryId)), { method: "GET" });
      const filtered = (Array.isArray(products) ? products : []).filter(function (x) { return x.active !== false; });
      state.productsByCategory.set(categoryId, filtered);
      state.cache.productsByCategory[String(categoryId)] = filtered;
      saveCache();
    } catch (_err) {
      state.productsByCategory.set(categoryId, state.cache.productsByCategory[String(categoryId)] || []);
    }
  }

  function renderProducts() {
    els.productsGrid.replaceChildren();
    if (state.activeCategoryId === null) { return; }
    const products = state.productsByCategory.get(state.activeCategoryId) || [];
    products.forEach(function (product, idx) {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "product-btn";
      btn.style.backgroundColor = PRODUCT_COLORS[idx % PRODUCT_COLORS.length];
      btn.textContent = product.name;
      btn.addEventListener("click", function () { addLine(product.id); });
      els.productsGrid.appendChild(btn);
    });
  }

  async function addLine(productId) {
    if (!state.currentTableNumber) { toast("No hay mesa activa"); return; }
    if (!canRunCriticalAction()) {
      toast("Sin lock valido. Reabre mesa para continuar.");
      return;
    }
    const qty = qtyFromInput();
    try {
      await ensureCurrentTicket();
      state.currentTicket = await apiJson("/api/v1/pos/tickets/" + state.currentTicket.id + "/lines", {
        method: "POST",
        body: JSON.stringify({ productId: productId, qty: qty })
      });
      cacheTicket(state.currentTicket);
      clearQtyInput();
      renderTicket();
      await refreshSendPreview();
      await refreshPaymentSummary();
    } catch (err) {
      if (shouldQueueAction(err)) {
        enqueueAction({
          type: "ADD_LINE",
          tableNumber: state.currentTableNumber,
          ticketId: state.currentTicket && state.currentTicket.id ? state.currentTicket.id : null,
          productId: productId,
          qty: qty
        });
        clearQtyInput();
        toast("Sin conexion: linea en cola para sincronizar");
        return;
      }
      pushError(err);
      toast("No se pudo anadir producto: " + err.message);
    }
  }

  function renderOrderHeader() {
    if (!state.currentTableNumber) {
      els.orderTitle.textContent = "Mesa";
      els.orderMeta.textContent = "";
      return;
    }
    const table = state.tables.find(function (t) { return t.tableNumber === state.currentTableNumber; });
    const alias = table && table.tableAlias ? (" - " + table.tableAlias) : "";
    els.orderTitle.textContent = "Mesa " + state.currentTableNumber + alias;
    const salon = table && table.salonName ? table.salonName : "";
    const elapsed = elapsedText(table ? table.elapsedMinutes : 0);
    els.orderMeta.textContent = (salon ? salon + " | " : "") + elapsed;
  }

  function renderTicket() {
    const ticket = state.currentTicket;
    els.ticketLines.replaceChildren();
    if (!ticket || !Array.isArray(ticket.lines) || !ticket.lines.length) {
      state.selectedLineId = null;
      const li = document.createElement("li");
      li.className = "muted";
      li.textContent = "Sin lineas";
      els.ticketLines.appendChild(li);
      els.ticketTotal.textContent = centsToEur(0);
      return;
    }
    const stillExists = ticket.lines.some(function (line) { return Number(line.id) === Number(state.selectedLineId); });
    if (!stillExists) {
      state.selectedLineId = null;
    }
    ticket.lines.forEach(function (line) {
      const li = document.createElement("li");
      li.className = "ticket-line" + (line.sent ? " sent" : "") +
        (Number(line.id) === Number(state.selectedLineId) ? " selected" : "");
      li.addEventListener("click", function () {
        state.selectedLineId = line.id;
        renderTicket();
      });
      li.innerHTML =
        "<div class='ticket-line-top'>" +
        "<span class='ticket-line-name'>" + escapeHtml(String(line.qty)) + "x " + escapeHtml(line.productName) + "</span>" +
        "<span class='ticket-line-price'>" + centsToEur(line.lineTotalCents) + "</span>" +
        "</div>" +
        "<div class='ticket-line-meta'>" +
        "<span class='pill pill-dest'>" + escapeHtml(line.destination || "-") + "</span>" +
        (line.sent ? "" : "<span class='pill pill-pending'>pendiente</span>") +
        "</div>";
      els.ticketLines.appendChild(li);
    });
    els.ticketTotal.textContent = centsToEur(ticket.totalCents || 0);
  }

  async function refreshSendPreview() {
    if (!state.currentTicket) {
      state.sendPreview = null;
      renderSendPreview();
      return;
    }
    try {
      state.sendPreview = await apiJson("/api/v1/pos/tickets/" + state.currentTicket.id + "/send-preview", { method: "GET" });
      state.cache.sendPreviewByTicketId[String(state.currentTicket.id)] = state.sendPreview;
      saveCache();
    } catch (_err) {
      state.sendPreview = state.cache.sendPreviewByTicketId[String(state.currentTicket.id)] || { pendingLines: [] };
    }
    renderSendPreview();
  }

  function renderSendPreview() {
    const pending = state.sendPreview && Array.isArray(state.sendPreview.pendingLines) ? state.sendPreview.pendingLines : [];
    if (!pending.length) {
      els.sendPreviewLabel.textContent = "No hay lineas pendientes";
      toggleSendButtons(false);
      return;
    }
    const grouped = pending.reduce(function (acc, line) {
      const key = line.destination || "COCINA";
      acc[key] = (acc[key] || 0) + 1;
      return acc;
    }, {});
    els.sendPreviewLabel.textContent = "Pendiente: " + Object.keys(grouped).map(function (k) { return k + " " + grouped[k]; }).join(" | ");
    toggleSendButtons(true);
  }

  function toggleSendButtons(enabled) {
    els.sendAllBtn.disabled = !enabled;
    els.sendBarBtn.disabled = !enabled;
    els.sendKitchenBtn.disabled = !enabled;
  }

  async function sendComanda(destination) {
    if (!state.currentTicket) { toast("No hay ticket activo"); return; }
    if (!canRunCriticalAction()) {
      toast("Sin lock valido. Reabre mesa para enviar.");
      return;
    }
    const idempotencyKey = buildIdempotencyKey("pda-send");
    try {
      const sendRes = await apiJson("/api/v1/pos/tickets/" + state.currentTicket.id + "/send", {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
        body: JSON.stringify({ destination: destination })
      });
      state.currentTicket = await getTicket(state.currentTicket.id);
      cacheTicket(state.currentTicket);
      renderTicket();
      await refreshSendPreview();
      toast("Comanda enviada " + sendRes.destination + " (" + sendRes.sentCount + ")");
    } catch (err) {
      if (shouldQueueAction(err)) {
        enqueueAction({
          type: "SEND",
          tableNumber: state.currentTableNumber,
          ticketId: state.currentTicket.id,
          destination: destination,
          idempotencyKey: idempotencyKey
        });
        toast("Sin conexion: envio en cola");
        return;
      }
      pushError(err);
      toast("Error enviando comanda: " + err.message);
    }
  }

  async function refreshPaymentSummary() {
    if (!state.currentTicket) {
      state.paymentSummary = null;
      renderPaymentSummary();
      return;
    }
    try {
      state.paymentSummary = await apiJson("/api/v1/pos/tickets/" + state.currentTicket.id + "/payment-summary", { method: "GET" });
      state.cache.paymentSummaryByTicketId[String(state.currentTicket.id)] = state.paymentSummary;
      saveCache();
    } catch (_err) {
      state.paymentSummary = state.cache.paymentSummaryByTicketId[String(state.currentTicket.id)] || null;
    }
    renderPaymentSummary();
  }

  function renderPaymentSummary() {
    const s = state.paymentSummary;
    if (!s) {
      els.paymentPendingLabel.textContent = "Pendiente: 0.00 EUR";
      togglePayButtons(false);
      return;
    }
    els.paymentPendingLabel.textContent = "Pendiente: " + centsToEur(s.pendingCents) + " | Pagado: " + centsToEur(s.paidCents);
    togglePayButtons(Number(s.pendingCents) > 0);
  }

  function togglePayButtons(enabled) {
    els.payFullBtn.disabled = !enabled;
    els.payPartialBtn.disabled = !enabled;
    els.payLinesBtn.disabled = !enabled;
  }

  function getSelectedLine() {
    if (!state.currentTicket || !Array.isArray(state.currentTicket.lines)) {
      return null;
    }
    return state.currentTicket.lines.find(function (line) {
      return Number(line.id) === Number(state.selectedLineId);
    }) || null;
  }

  async function onDeleteSelectedLine() {
    const line = getSelectedLine();
    if (!line) {
      toast("Selecciona una linea para borrar");
      return;
    }
    if (!canRunCriticalAction()) {
      toast("Sin lock valido. Reabre mesa para continuar.");
      return;
    }
    try {
      state.currentTicket = await apiJson("/api/v1/pos/tickets/" + state.currentTicket.id + "/lines/" + line.id, {
        method: "DELETE"
      });
      cacheTicket(state.currentTicket);
      state.selectedLineId = null;
      renderTicket();
      await refreshSendPreview();
      await refreshPaymentSummary();
      toast("Linea borrada");
    } catch (err) {
      pushError(err);
      toast("No se pudo borrar linea: " + err.message);
    }
  }

  async function onEditSelectedLine() {
    const line = getSelectedLine();
    if (!line) {
      toast("Selecciona una linea para editar");
      return;
    }
    if (!canRunCriticalAction()) {
      toast("Sin lock valido. Reabre mesa para continuar.");
      return;
    }

    const qtyRaw = await promptNumberPad("Cantidad", String(line.qty), false);
    if (qtyRaw === null) { return; }
    const qty = Math.max(1, parseInt(qtyRaw, 10) || 1);

    const priceRaw = await promptNumberPad("Precio EUR", (Number(line.unitPriceCents || 0) / 100).toFixed(2), true);
    if (priceRaw === null) { return; }
    let priceCents;
    try {
      priceCents = parseAmountToCents(priceRaw);
    } catch (err) {
      toast(err.message);
      return;
    }

    try {
      if (qty !== Number(line.qty)) {
        state.currentTicket = await apiJson("/api/v1/pos/tickets/" + state.currentTicket.id + "/lines/" + line.id, {
          method: "PATCH",
          body: JSON.stringify({ qty: qty })
        });
      }
      if (priceCents !== Number(line.unitPriceCents || 0)) {
        state.currentTicket = await apiJson("/api/v1/pos/tickets/" + state.currentTicket.id + "/lines/" + line.id + "/price", {
          method: "PATCH",
          body: JSON.stringify({ priceCents: priceCents })
        });
      }
      cacheTicket(state.currentTicket);
      renderTicket();
      await refreshSendPreview();
      await refreshPaymentSummary();
      toast("Linea actualizada");
    } catch (err) {
      pushError(err);
      toast("No se pudo editar linea: " + err.message);
    }
  }

  async function onMoveTable() {
    if (!state.currentTicket || !state.currentTableNumber) {
      toast("No hay mesa activa");
      return;
    }
    if (!canRunCriticalAction()) {
      toast("Sin lock valido. Reabre mesa para continuar.");
      return;
    }
    let targetTable;
    try {
      targetTable = await chooseMoveTargetTable(Number(state.currentTableNumber));
    } catch (err) {
      pushError(err);
      toast("No se pudo cargar mesas destino: " + err.message);
      return;
    }
    if (!targetTable || targetTable === Number(state.currentTableNumber)) {
      return;
    }

    try {
      await apiJson("/api/v1/pos/tickets/" + state.currentTicket.id + "/move-table", {
        method: "POST",
        body: JSON.stringify({ tableNumber: targetTable })
      });
      const oldTable = state.currentTableNumber;
      stopHeartbeat();
      state.currentTableNumber = targetTable;
      try {
        await lockTable(targetTable);
        startHeartbeat(targetTable);
      } catch (lockErr) {
        pushError(lockErr);
        toast("Mesa movida, pero no se pudo bloquear destino. Reabre la mesa.");
      } finally {
        try {
          await unlockTable(oldTable);
        } catch (_err) {}
      }
      state.currentTicket = await getTicket(state.currentTicket.id);
      renderOrderHeader();
      renderTicket();
      await refreshSendPreview();
      await refreshPaymentSummary();
      await refreshTablesSafe();
      toast("Mesa movida a " + targetTable);
    } catch (err) {
      pushError(err);
      toast("No se pudo mover mesa: " + err.message);
    }
  }

  async function chooseMoveTargetTable(currentTableNumber) {
    await refreshTables();
    const current = Number(currentTableNumber);
    const candidates = state.tables
      .filter(function (t) { return Number(t.tableNumber) !== current; })
      .sort(function (a, b) {
        const salonCmp = String(a.salonName || "").localeCompare(String(b.salonName || ""));
        if (salonCmp !== 0) { return salonCmp; }
        return Number(a.tableNumber) - Number(b.tableNumber);
      });

    return new Promise(function (resolve) {
      els.moveTableList.replaceChildren();
      let availableCount = 0;

      candidates.forEach(function (table) {
        const row = document.createElement("div");
        row.className = "move-table-row";

        const details = document.createElement("div");
        details.className = "move-table-details";
        const alias = table.tableAlias ? (" (" + table.tableAlias + ")") : "";
        details.textContent = (table.salonName || "Salon") + " - Mesa " + table.tableNumber + alias;

        const status = document.createElement("div");
        status.className = "move-table-status muted";
        status.textContent = tableStatusText(table);

        const action = document.createElement("button");
        action.type = "button";
        action.className = "btn btn-secondary";
        action.textContent = "Mover aqui";
        const available = isMoveTargetAvailable(table);
        action.disabled = !available;
        if (available) {
          availableCount += 1;
          action.addEventListener("click", function () {
            els.moveTableDialog.close(String(table.tableNumber));
          });
        }

        row.appendChild(details);
        row.appendChild(status);
        row.appendChild(action);
        els.moveTableList.appendChild(row);
      });

      if (!candidates.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "No hay mesas disponibles.";
        els.moveTableList.appendChild(empty);
      }

      els.moveTableInfo.textContent = availableCount > 0
        ? "Selecciona una mesa libre como destino."
        : "No hay mesas libres disponibles para mover.";

      const onClose = function () {
        els.moveTableDialog.removeEventListener("close", onClose);
        const value = parseInt(els.moveTableDialog.returnValue || "", 10);
        if (Number.isFinite(value) && value > 0) {
          resolve(value);
          return;
        }
        resolve(null);
      };

      els.moveTableDialog.addEventListener("close", onClose);
      els.moveTableDialog.showModal();
    });
  }

  async function onPayFull() {
    if (!state.paymentSummary || state.paymentSummary.pendingCents <= 0) { toast("No hay importe pendiente"); return; }
    const method = await choosePaymentMethod();
    if (!method) { return; }
    await addPayment(method, state.paymentSummary.pendingCents, "total");
  }

  async function onPayPartial() {
    if (!state.paymentSummary || state.paymentSummary.pendingCents <= 0) { toast("No hay importe pendiente"); return; }
    const method = await choosePaymentMethod();
    if (!method) { return; }
    const def = (state.paymentSummary.pendingCents / 100).toFixed(2);
    const raw = await promptNumberPad("Importe parcial EUR", def, true);
    if (raw === null) { return; }
    let amount;
    try {
      amount = parseAmountToCents(raw);
    } catch (err) {
      toast(err.message);
      return;
    }
    if (amount > state.paymentSummary.pendingCents) { toast("El importe supera el pendiente"); return; }
    await addPayment(method, amount, "parcial");
  }

  async function onPayByLines() {
    if (!state.currentTicket || !state.paymentSummary || state.paymentSummary.pendingCents <= 0) { toast("No hay importe pendiente"); return; }
    const method = await choosePaymentMethod();
    if (!method) { return; }
    const amount = await promptPartialByLines(state.paymentSummary.pendingCents);
    if (!amount) { return; }
    await addPayment(method, amount, "lineas");
  }

  function choosePaymentMethod() {
    return new Promise(function (resolve) {
      state.methodChoice = null;
      const onClose = function () {
        els.methodDialog.removeEventListener("close", onClose);
        resolve(state.methodChoice);
      };
      els.methodDialog.addEventListener("close", onClose);
      els.methodDialog.showModal();
    });
  }

  function promptPartialByLines(pendingCents) {
    return new Promise(function (resolve) {
      if (!state.currentTicket || !Array.isArray(state.currentTicket.lines) || !state.currentTicket.lines.length) {
        resolve(0);
        return;
      }
      state.payLinesAmount = 0;
      els.payLinesPendingLabel.textContent = "Pendiente: " + centsToEur(pendingCents);
      els.payLinesList.replaceChildren();

      const models = [];
      state.currentTicket.lines.forEach(function (line) {
        if (!line || line.qty <= 0) { return; }
        const row = document.createElement("div");
        row.className = "pay-line-row";

        const include = document.createElement("input");
        include.type = "checkbox";
        const name = document.createElement("div");
        name.textContent = line.productName + " (" + line.qty + "x)";
        const qty = document.createElement("input");
        qty.type = "number";
        qty.min = "0";
        qty.max = String(line.qty);
        qty.step = "1";
        qty.value = "0";
        qty.disabled = true;
        const subtotal = document.createElement("div");
        subtotal.className = "pay-line-subtotal";
        subtotal.textContent = centsToEur(0);

        const model = { line: line, include: include, qty: qty, subtotal: subtotal };
        models.push(model);

        include.addEventListener("change", function () {
          qty.disabled = !include.checked;
          if (include.checked && Number(qty.value) === 0) { qty.value = String(line.qty); }
          if (!include.checked) { qty.value = "0"; }
          refreshPayLinesSelection(models, pendingCents);
        });

        qty.addEventListener("input", function () {
          let v = Number(qty.value || 0);
          if (!Number.isFinite(v) || v < 0) { v = 0; }
          const max = Number(line.qty);
          if (v > max) { v = max; }
          qty.value = String(v);
          include.checked = v > 0;
          qty.disabled = !include.checked;
          refreshPayLinesSelection(models, pendingCents);
        });

        row.appendChild(include);
        row.appendChild(name);
        row.appendChild(qty);
        row.appendChild(subtotal);
        els.payLinesList.appendChild(row);
      });

      refreshPayLinesSelection(models, pendingCents);

      const onApply = function () {
        refreshPayLinesSelection(models, pendingCents);
        if (state.payLinesAmount <= 0 || state.payLinesAmount > pendingCents) { return; }
        els.payLinesDialog.close("apply");
      };

      const onClose = function () {
        els.payLinesApplyBtn.removeEventListener("click", onApply);
        els.payLinesDialog.removeEventListener("close", onClose);
        const amount = state.payLinesAmount || 0;
        state.payLinesAmount = 0;
        resolve(amount);
      };

      els.payLinesApplyBtn.addEventListener("click", onApply);
      els.payLinesDialog.addEventListener("close", onClose);
      els.payLinesDialog.showModal();
    });
  }

  function refreshPayLinesSelection(models, pendingCents) {
    let selected = 0;
    models.forEach(function (m) {
      const qty = m.include.checked ? Number(m.qty.value || 0) : 0;
      const cents = qty * Number(m.line.unitPriceCents || 0);
      m.subtotal.textContent = centsToEur(cents);
      selected += cents;
    });
    state.payLinesAmount = selected;
    const warning = selected > pendingCents ? " (excede pendiente)" : "";
    els.payLinesSelectedLabel.innerHTML = "Seleccionado: <strong>" + centsToEur(selected) + warning + "</strong>";
    els.payLinesApplyBtn.disabled = selected <= 0 || selected > pendingCents;
  }

  async function addPayment(method, amountCents, mode) {
    if (!state.currentTicket) { toast("No hay ticket activo"); return; }
    if (!canRunCriticalAction()) {
      toast("Sin lock valido. Reabre mesa para cobrar.");
      return;
    }
    const idempotencyKey = buildIdempotencyKey("pda-pay");
    try {
      await apiJson("/api/v1/pos/tickets/" + state.currentTicket.id + "/payments", {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
        body: JSON.stringify({ method: method, amountCents: amountCents })
      });
      state.currentTicket = await getTicket(state.currentTicket.id);
      cacheTicket(state.currentTicket);
      renderTicket();
      await refreshPaymentSummary();
      await refreshSendPreview();
      if (state.paymentSummary && state.paymentSummary.pendingCents <= 0) {
        toast("Cuenta cerrada. Mesa liberada.");
        await leaveTableAndBack();
        return;
      }
      toast("Cobro " + mode + " registrado (" + method + "): " + centsToEur(amountCents));
    } catch (err) {
      if (shouldQueueAction(err)) {
        enqueueAction({
          type: "PAYMENT",
          tableNumber: state.currentTableNumber,
          ticketId: state.currentTicket.id,
          method: method,
          amountCents: amountCents,
          idempotencyKey: idempotencyKey
        });
        toast("Sin conexion: cobro en cola");
        return;
      }
      pushError(err);
      toast("Error en cobro: " + err.message);
    }
  }

  async function getTicket(ticketId) {
    try {
      const ticket = await apiJson("/api/v1/pos/tickets/" + ticketId, { method: "GET" });
      cacheTicket(ticket);
      return ticket;
    } catch (err) {
      const cached = state.cache.ticketsById[String(ticketId)];
      if (cached) {
        return cached;
      }
      throw err;
    }
  }

  async function apiJson(path, options) {
    if (!state.apiBase) { throw new Error("API base no configurada"); }
    const headers = Object.assign({}, options && options.headers ? options.headers : {});
    if (state.token) { headers.Authorization = "Bearer " + state.token; }
    if (state.terminalId) { headers["X-Terminal-Id"] = state.terminalId; }
    headers["X-Client-App"] = "PDA";
    if (options && options.body && !headers["Content-Type"]) { headers["Content-Type"] = "application/json"; }

    const startedAt = performance.now();
    try {
      const response = await fetchJson(state.apiBase + path, {
        method: options && options.method ? options.method : "GET",
        headers: headers,
        body: options && options.body ? options.body : undefined
      });
      markBackendOk(Math.round(performance.now() - startedAt));
      return response;
    } catch (err) {
      markBackendFail(err);
      if (err.status === 401) {
        clearSession();
        showScreen("login");
      }
      throw err;
    }
  }

  function cacheTicket(ticket) {
    if (!ticket || typeof ticket.id === "undefined" || ticket.id === null) {
      return;
    }
    state.cache.ticketsById[String(ticket.id)] = ticket;
    saveCache();
  }

  function setLocalLockLease(lockResponse) {
    if (lockResponse && lockResponse.expiresAt) {
      const ts = Date.parse(lockResponse.expiresAt);
      state.lockLeaseExpiresAt = Number.isFinite(ts) ? ts : (Date.now() + 60000);
      return;
    }
    state.lockLeaseExpiresAt = Date.now() + 60000;
  }

  function hasValidLocalLock() {
    if (!state.currentTableNumber) {
      return false;
    }
    if (!state.lockLeaseExpiresAt) {
      return false;
    }
    return Date.now() < (state.lockLeaseExpiresAt - 5000);
  }

  function canRunCriticalAction() {
    if (!navigator.onLine && !hasValidLocalLock()) {
      return false;
    }
    return true;
  }

  function shouldQueueAction(err) {
    if (!navigator.onLine) {
      return true;
    }
    if (!err) {
      return false;
    }
    if (typeof err.status !== "number") {
      return true;
    }
    return err.status >= 500;
  }

  function enqueueAction(action) {
    const queued = Object.assign({
      id: buildIdempotencyKey("queue"),
      attempts: 0,
      createdAt: new Date().toISOString()
    }, action);
    state.actionQueue.push(queued);
    saveQueue();
  }

  async function processQueue() {
    if (state.processingQueue || !navigator.onLine || !state.token || !state.actionQueue.length) {
      return;
    }
    state.processingQueue = true;
    try {
      while (state.actionQueue.length) {
        const action = state.actionQueue[0];
        if ((action.attempts || 0) >= QUEUE_RETRY_LIMIT) {
          pushConflict(action, {
            status: 0,
            message: "Superado limite de reintentos"
          });
          state.actionQueue.shift();
          saveQueue();
          continue;
        }
        try {
          await replayAction(action);
          state.actionQueue.shift();
          saveQueue();
        } catch (err) {
          action.attempts = (action.attempts || 0) + 1;
          saveQueue();
          if (err && err.status === 401) {
            throw err;
          }
          if (err && (err.status === 403 || err.status === 404 || err.status === 409)) {
            pushConflict(action, err);
            state.actionQueue.shift();
            saveQueue();
            continue;
          }
          break;
        }
      }
    } finally {
      state.processingQueue = false;
    }
    if (state.currentTicket) {
      try {
        state.currentTicket = await getTicket(state.currentTicket.id);
        renderTicket();
        await refreshSendPreview();
        await refreshPaymentSummary();
      } catch (_err) {
      }
    }
    if (!state.actionQueue.length && !state.queueConflicts.length) {
      toast("Sincronizacion offline completada");
    }
  }

  async function replayAction(action) {
    if (!action || !action.type) {
      return;
    }
    if (action.tableNumber) {
      const lock = await apiJson("/api/v1/pos/salon/tables/" + action.tableNumber + "/lock", {
        method: "POST",
        body: JSON.stringify({ terminalId: state.terminalId })
      });
      setLocalLockLease(lock);
    }
    if (action.type === "ADD_LINE") {
      let ticketId = action.ticketId;
      if (!ticketId) {
        if (!action.tableNumber) {
          throw new Error("ADD_LINE sin tableNumber");
        }
        const opened = await apiJson("/api/v1/pos/salon/tables/" + action.tableNumber + "/open-ticket", {
          method: "POST"
        });
        ticketId = opened && opened.id ? opened.id : null;
        if (!ticketId) {
          throw new Error("No se pudo crear ticket en replay");
        }
        action.ticketId = ticketId;
      }
      const ticket = await apiJson("/api/v1/pos/tickets/" + ticketId + "/lines", {
        method: "POST",
        body: JSON.stringify({ productId: action.productId, qty: action.qty || 1 })
      });
      cacheTicket(ticket);
      return;
    }
    if (action.type === "SEND") {
      await apiJson("/api/v1/pos/tickets/" + action.ticketId + "/send", {
        method: "POST",
        headers: { "Idempotency-Key": action.idempotencyKey || buildIdempotencyKey("pda-send") },
        body: JSON.stringify({ destination: action.destination || "ALL" })
      });
      return;
    }
    if (action.type === "PAYMENT") {
      await apiJson("/api/v1/pos/tickets/" + action.ticketId + "/payments", {
        method: "POST",
        headers: { "Idempotency-Key": action.idempotencyKey || buildIdempotencyKey("pda-pay") },
        body: JSON.stringify({ method: action.method, amountCents: action.amountCents })
      });
    }
  }
  async function fetchJson(url, init) {
    const res = await fetch(url, init);
    const text = await res.text();
    const body = parseJsonSafe(text);
    if (!res.ok) {
      const err = new Error(errorMessageFrom(body, text, res.status));
      err.status = res.status;
      err.payload = body;
      throw err;
    }
    return body === null ? {} : body;
  }

  function markBackendOk(latencyMs) {
    state.lastLatencyMs = latencyMs;
    state.lastError = null;
    if (!navigator.onLine) { updateBackendBadge("OFFLINE"); return; }
    if (latencyMs >= 700) { updateBackendBadge("DEGRADED"); return; }
    updateBackendBadge("ONLINE");
  }

  function markBackendFail(err) {
    state.lastError = err;
    updateBackendBadge(navigator.onLine ? "DEGRADED" : "OFFLINE");
  }

  function updateOnlineBadge() {
    if (!navigator.onLine) { updateBackendBadge("OFFLINE"); return; }
    if (state.lastError) { updateBackendBadge("DEGRADED"); return; }
    if (typeof state.lastLatencyMs === "number") {
      updateBackendBadge(state.lastLatencyMs >= 700 ? "DEGRADED" : "ONLINE");
      return;
    }
    updateBackendBadge("ONLINE");
  }

  function updateBackendBadge(mode) {
    const badge = els.networkBadge;
    badge.classList.remove("badge-online", "badge-degraded", "badge-offline");
    if (mode === "ONLINE") { badge.classList.add("badge-online"); }
    else if (mode === "DEGRADED") { badge.classList.add("badge-degraded"); }
    else { badge.classList.add("badge-offline"); }
    let text = mode;
    if (typeof state.lastLatencyMs === "number") { text += " " + state.lastLatencyMs + "ms"; }
    badge.textContent = text;
    badge.title = state.lastError ? state.lastError.message : "Sin errores";
  }

  function renderQtyInput() {
    els.qtyInput.value = state.currentQtyInput;
  }

  function clearQtyInput() {
    state.currentQtyInput = "";
    renderQtyInput();
  }

  function qtyFromInput() {
    const raw = String(state.currentQtyInput || "").trim();
    if (!raw) {
      return 1;
    }
    const value = parseInt(raw, 10);
    if (!Number.isFinite(value) || value < 1) {
      return 1;
    }
    return value;
  }

  function onQtyPadKey(key) {
    if (!key) { return; }
    if (key === "C") {
      clearQtyInput();
      return;
    }
    if (key === "BACK") {
      state.currentQtyInput = state.currentQtyInput.slice(0, -1);
      renderQtyInput();
      return;
    }
    if (!/^\d$/.test(key)) {
      return;
    }
    if (state.currentQtyInput.length >= 4) {
      return;
    }
    state.currentQtyInput += key;
    renderQtyInput();
  }

  function onNumberPadKey(key) {
    if (!key) { return; }
    let value = String(els.numberPadDisplay.value || "");
    if (key === "C") {
      els.numberPadDisplay.value = "";
      return;
    }
    if (key === "BACK") {
      els.numberPadDisplay.value = value.slice(0, -1);
      return;
    }
    if (key === ".") {
      if (!state.numberPad.allowDecimal || value.includes(".")) {
        return;
      }
      if (!value) {
        value = "0";
      }
      els.numberPadDisplay.value = value + ".";
      return;
    }
    if (!/^\d$/.test(key)) {
      return;
    }
    if (value.length >= 12) {
      return;
    }
    els.numberPadDisplay.value = value + key;
  }

  function promptNumberPad(title, initialValue, allowDecimal) {
    return new Promise(function (resolve) {
      state.numberPad.allowDecimal = !!allowDecimal;
      state.numberPad.resolve = resolve;
      els.numberPadTitle.textContent = title || "Numero";
      els.numberPadDisplay.value = String(initialValue || "");
      els.numberPadDialog.showModal();
    });
  }

  function updateSessionBadge() {
    const queueSuffix = state.actionQueue.length ? (" | Q:" + state.actionQueue.length) : "";
    const conflictSuffix = state.queueConflicts.length ? (" | C:" + state.queueConflicts.length) : "";
    if (state.username && state.terminalId) {
      els.sessionBadge.textContent = state.username + " - " + state.terminalId + queueSuffix + conflictSuffix;
    } else {
      els.sessionBadge.textContent = "SIN SESION" + queueSuffix + conflictSuffix;
    }
    els.conflictsBtn.textContent = state.queueConflicts.length ? ("Conflictos (" + state.queueConflicts.length + ")") : "Conflictos";
  }

  function pushError(err) {
    const msg = new Date().toLocaleTimeString() + " - " + (err && err.message ? err.message : String(err));
    state.errors.unshift(msg);
    if (state.errors.length > 5) { state.errors = state.errors.slice(0, 5); }
  }

  function pushConflict(action, err) {
    if (!action) {
      return;
    }
    const conflict = {
      id: buildIdempotencyKey("conflict"),
      action: action,
      type: action.type || "UNKNOWN",
      ticketId: action.ticketId || null,
      tableNumber: action.tableNumber || null,
      status: err && typeof err.status === "number" ? err.status : 0,
      message: err && err.message ? err.message : "Error de sincronizacion",
      createdAt: new Date().toISOString()
    };
    state.queueConflicts.unshift(conflict);
    if (state.queueConflicts.length > 30) {
      state.queueConflicts = state.queueConflicts.slice(0, 30);
    }
    saveConflicts();
    toast("Conflicto en cola: " + conflict.type + " (revisar Conflictos)");
  }

  function openErrorsDialog() {
    els.errorsList.replaceChildren();
    if (!state.errors.length) {
      const li = document.createElement("li");
      li.textContent = "Sin errores";
      els.errorsList.appendChild(li);
    } else {
      state.errors.forEach(function (msg) {
        const li = document.createElement("li");
        li.textContent = msg;
        els.errorsList.appendChild(li);
      });
    }
    if (typeof els.errorsDialog.showModal === "function") { els.errorsDialog.showModal(); }
  }

  function openConflictsDialog() {
    els.conflictsList.replaceChildren();
    if (!state.queueConflicts.length) {
      const empty = document.createElement("div");
      empty.className = "muted";
      empty.textContent = "Sin conflictos pendientes.";
      els.conflictsList.appendChild(empty);
    } else {
      state.queueConflicts.forEach(function (c) {
        const item = document.createElement("article");
        item.className = "conflict-item";

        const title = document.createElement("div");
        title.className = "conflict-title";
        title.textContent = c.type + " | Mesa " + (c.tableNumber || "-") + " | Ticket " + (c.ticketId || "-");
        item.appendChild(title);

        const meta = document.createElement("div");
        meta.className = "conflict-meta";
        const ts = c.createdAt ? new Date(c.createdAt).toLocaleString() : "-";
        meta.textContent = "HTTP " + (c.status || "-") + " | " + ts + " | " + (c.message || "sin detalle");
        item.appendChild(meta);

        const actions = document.createElement("div");
        actions.className = "conflict-actions";

        const retryBtn = document.createElement("button");
        retryBtn.type = "button";
        retryBtn.className = "btn btn-primary";
        retryBtn.textContent = "Reintentar";
        retryBtn.addEventListener("click", async function () {
          retryConflict(c.id);
          openConflictsDialog();
          await processQueue();
        });
        actions.appendChild(retryBtn);

        const refreshBtn = document.createElement("button");
        refreshBtn.type = "button";
        refreshBtn.className = "btn btn-secondary";
        refreshBtn.textContent = "Refrescar estado";
        refreshBtn.addEventListener("click", async function () {
          await refreshConflictState(c);
        });
        actions.appendChild(refreshBtn);

        const discardBtn = document.createElement("button");
        discardBtn.type = "button";
        discardBtn.className = "btn btn-danger";
        discardBtn.textContent = "Descartar";
        discardBtn.addEventListener("click", function () {
          discardConflict(c.id);
          openConflictsDialog();
        });
        actions.appendChild(discardBtn);

        item.appendChild(actions);
        els.conflictsList.appendChild(item);
      });
    }
    if (typeof els.conflictsDialog.showModal === "function" && !els.conflictsDialog.open) {
      els.conflictsDialog.showModal();
    }
  }

  function retryConflict(conflictId) {
    const idx = state.queueConflicts.findIndex(function (c) { return c.id === conflictId; });
    if (idx < 0) {
      return;
    }
    const conflict = state.queueConflicts[idx];
    const action = Object.assign({}, conflict.action || {});
    action.attempts = 0;
    if (!action.id) {
      action.id = buildIdempotencyKey("queue");
    }
    state.actionQueue.push(action);
    state.queueConflicts.splice(idx, 1);
    saveQueue();
    saveConflicts();
    toast("Accion movida a cola para reintento");
  }

  function discardConflict(conflictId) {
    const before = state.queueConflicts.length;
    state.queueConflicts = state.queueConflicts.filter(function (c) { return c.id !== conflictId; });
    if (state.queueConflicts.length !== before) {
      saveConflicts();
      toast("Conflicto descartado");
    }
  }

  async function refreshConflictState(conflict) {
    try {
      await refreshTablesSafe();
      if (conflict && conflict.ticketId) {
        const ticket = await getTicket(conflict.ticketId);
        if (ticket && state.currentTicket && ticket.id === state.currentTicket.id) {
          state.currentTicket = ticket;
          renderTicket();
          await refreshSendPreview();
          await refreshPaymentSummary();
        }
      }
      toast("Estado actualizado");
    } catch (err) {
      pushError(err);
      toast("No se pudo refrescar estado: " + err.message);
    }
  }

  function showScreen(name) {
    els.loginScreen.classList.toggle("hidden", name !== "login");
    els.tablesScreen.classList.toggle("hidden", name !== "tables");
    els.orderScreen.classList.toggle("hidden", name !== "order");
    applyViewportMetrics();
  }

  function toast(message) {
    els.toast.textContent = message;
    els.toast.classList.remove("hidden");
    if (toastTimer) { window.clearTimeout(toastTimer); }
    toastTimer = window.setTimeout(function () { els.toast.classList.add("hidden"); }, TOAST_MS);
  }

  function logout() {
    leaveTableQuick();
    state.actionQueue = [];
    saveQueue();
    state.queueConflicts = [];
    saveConflicts();
    clearSession();
    setBrand(DEFAULT_BRAND);
    showScreen("login");
    toast("Sesion cerrada");
  }

  async function refreshBusinessBrand() {
    if (!state.token) {
      setBrand(DEFAULT_BRAND);
      return;
    }
    try {
      const profile = await apiJson("/api/v1/pos/business-profile", { method: "GET" });
      const name = profile && profile.businessName ? String(profile.businessName).trim() : "";
      setBrand(name || DEFAULT_BRAND);
    } catch (_err) {
      setBrand(DEFAULT_BRAND);
    }
  }

  function setBrand(name) {
    if (!els.brandLabel) {
      return;
    }
    const value = String(name || "").trim();
    els.brandLabel.textContent = value || DEFAULT_BRAND;
  }

  function leaveTableQuick() {
    const tableNumber = state.currentTableNumber;
    if (!tableNumber || !state.apiBase || !state.token) { return; }
    fetch(state.apiBase + "/api/v1/pos/salon/tables/" + tableNumber + "/unlock", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": "Bearer " + state.token,
        "X-Terminal-Id": state.terminalId
      },
      body: JSON.stringify({ terminalId: state.terminalId }),
      keepalive: true
    }).catch(function () {});
  }

  function onPageHide() { leaveTableQuick(); }

  function tableCardClass(table) {
    if (isLockedByOther(table)) { return "table-locked-other"; }
    if (isLockedByMe(table)) { return "table-locked-me"; }
    if (table.status === "FREE") { return "table-free"; }
    if (table.status === "PENDING_SEND") { return "table-pending"; }
    if (table.status === "BILL_REQUESTED") { return "table-bill"; }
    return "table-occupied";
  }

  function tableStatusClass(table) {
    if (isLockedByOther(table)) { return "status-lock-other"; }
    if (isLockedByMe(table)) { return "status-lock"; }
    if (table.status === "FREE") { return "status-free"; }
    if (table.status === "PENDING_SEND") { return "status-pending"; }
    if (table.status === "BILL_REQUESTED") { return "status-bill"; }
    return "status-occupied";
  }

  function tableStatusText(table) {
    if (isLockedByOther(table)) { return "Bloqueada (" + safeTerminal(table.lockedTerminalId) + ")"; }
    if (isLockedByMe(table)) { return "Bloqueada (yo)"; }
    if (table.status === "FREE") { return "Libre"; }
    if (table.status === "PENDING_SEND") { return "Pendiente enviar"; }
    if (table.status === "BILL_REQUESTED") { return "Cuenta pedida"; }
    return "Ocupada";
  }

  function isLockedByMe(table) {
    return table && table.lockedTerminalId && state.terminalId && table.lockedTerminalId.toLowerCase() === state.terminalId.toLowerCase();
  }

  function isMoveTargetAvailable(table) {
    return !!table && Number(table.tableNumber) > 0 && !table.ticketId && !table.lockedTerminalId;
  }

  function isLockedByOther(table) { return table && table.lockedTerminalId && !isLockedByMe(table); }
  function elapsedText(minutes) { return (typeof minutes !== "number" || minutes < 1) ? "-" : minutes + " min"; }
  function centsToEur(cents) { return ((Number(cents) || 0) / 100).toFixed(2) + " EUR"; }

  function parseAmountToCents(rawAmount) {
    const normalized = String(rawAmount || "").trim().replace(",", ".");
    const value = Number(normalized);
    if (!normalized || !Number.isFinite(value) || value <= 0) { throw new Error("Importe invalido"); }
    return Math.round(value * 100);
  }

  function sanitizeTerminalId(value) {
    return String(value || "").trim().replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 32);
  }

  function safeTerminal(value) { return value ? String(value) : "?"; }

  function errorMessageFrom(body, text, status) {
    if (body && typeof body === "object") {
      if (typeof body.message === "string" && body.message.trim()) { return body.message; }
      if (typeof body.error === "string" && body.error.trim()) { return body.error; }
    }
    if (text && text.trim()) { return "HTTP " + status + " - " + text.trim(); }
    return "HTTP " + status;
  }

  function parseJsonSafe(text) {
    if (!text || !text.trim()) { return {}; }
    try { return JSON.parse(text); } catch (_err) { return null; }
  }

  function normalizeBase(url) { return String(url || "").trim().replace(/\/+$/, ""); }

  function buildIdempotencyKey(prefix) {
    const rnd = typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : (Date.now() + "-" + Math.random().toString(16).slice(2));
    return prefix + "-" + rnd;
  }

  function escapeHtml(value) {
    return String(value || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/\"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function byId(id) {
    const node = document.getElementById(id);
    if (!node) { throw new Error("Missing element #" + id); }
    return node;
  }

  function registerServiceWorker() {
    if (!("serviceWorker" in navigator)) { return; }
    navigator.serviceWorker.register("./sw.js").catch(function () {});
  }
})();
