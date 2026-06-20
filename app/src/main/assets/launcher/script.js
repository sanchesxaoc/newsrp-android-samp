// ===== Bridge helper =====
function callAndroid(method) {
  var args = Array.prototype.slice.call(arguments, 1);
  try {
    if (typeof Android !== 'undefined' && typeof Android[method] === 'function') {
      return Android[method].apply(Android, args);
    }
  } catch (e) {
    console.error('Bridge error:', method, e);
  }
  return null;
}

// ===== UI Elements (Global) =====
var dialogOverlay, dialogTitle, dialogMessage, btnDialogOk;
var navHome, navServers, navSettings, navChangelogs;
var pageHome, pageServers, pageSettings, pageChangelogs;
var lastSavedSettings = { nickname: '', chatLines: '5', keyboard: false, voiceChat: true };

// ===== Sidebar Navigation =====
function showPage(page) {
  if (!pageHome) return;

  pageHome.classList.remove('active');
  pageServers.classList.remove('active');
  pageSettings.classList.remove('active');
  pageChangelogs && pageChangelogs.classList.remove('active');
  navHome.classList.remove('active');
  navServers.classList.remove('active');
  navSettings.classList.remove('active');
  navChangelogs && navChangelogs.classList.remove('active');

  var topbarLeft  = document.querySelector('.topbar-left');
  var topbarCenter = document.querySelector('.topbar-center');
  var bottombarLeft = document.querySelector('.bottombar-left');
  if (topbarLeft)   topbarLeft.style.visibility   = page === 'home' ? 'visible' : 'hidden';
  if (topbarCenter) topbarCenter.style.visibility = page === 'home' ? 'visible' : 'hidden';
  if (bottombarLeft) bottombarLeft.style.visibility = page === 'home' ? 'visible' : 'hidden';

  if (page === 'home') {
    pageHome.classList.add('active');
    navHome.classList.add('active');
  } else if (page === 'servers') {
    pageServers.classList.add('active');
    navServers.classList.add('active');
    loadServers();
  } else if (page === 'settings') {
    pageSettings.classList.add('active');
    navSettings.classList.add('active');
  } else if (page === 'changelogs') {
    pageChangelogs && pageChangelogs.classList.add('active');
    navChangelogs && navChangelogs.classList.add('active');
    loadChangelogs();
  }
}

// ===== Init on DOMContentLoaded =====
document.addEventListener('DOMContentLoaded', function () {
  navHome      = document.getElementById('nav-home');
  navServers   = document.getElementById('nav-servers');
  navSettings  = document.getElementById('nav-settings');
  navChangelogs = document.getElementById('nav-changelogs');
  pageHome      = document.getElementById('page-home');
  pageServers   = document.getElementById('page-servers');
  pageSettings  = document.getElementById('page-settings');
  pageChangelogs = document.getElementById('page-changelogs');

  dialogOverlay = document.getElementById('custom-dialog');
  dialogTitle   = document.getElementById('dialog-title');
  dialogMessage = document.getElementById('dialog-message');
  btnDialogOk   = document.getElementById('btn-dialog-ok');

  btnDialogOk && btnDialogOk.addEventListener('click', function () {
    dialogOverlay && dialogOverlay.classList.add('hidden');
  });

  // ===== Unsaved changes guard =====
  function hasUnsavedChanges() {
    var nick = (document.getElementById('nickname-input') || {}).value || '';
    var chat = (document.getElementById('chat-lines-select') || {}).value || '5';
    var kbd  = ((document.getElementById('keyboard-toggle') || {}).checked) || false;
    var vc   = ((document.getElementById('voicechat-toggle') || {}).checked !== false);
    return nick !== lastSavedSettings.nickname
        || chat !== lastSavedSettings.chatLines
        || kbd  !== lastSavedSettings.keyboard
        || vc   !== lastSavedSettings.voiceChat;
  }

  var pendingPage = null;
  function attemptNavigation(page) {
    if (pageSettings.classList.contains('active') && hasUnsavedChanges()) {
      pendingPage = page;
      showConfirmDialog();
    } else {
      showPage(page);
    }
  }

  // Navigation
  navHome      && navHome.addEventListener('click',      function () { attemptNavigation('home'); });
  navServers   && navServers.addEventListener('click',   function () { attemptNavigation('servers'); });
  navSettings  && navSettings.addEventListener('click',  function () { attemptNavigation('settings'); });
  navChangelogs && navChangelogs.addEventListener('click', function () { attemptNavigation('changelogs'); });
  document.getElementById('open-settings-btn') && document.getElementById('open-settings-btn').addEventListener('click', function () { attemptNavigation('settings'); });

  // ===== Settings =====
  function doSaveSettings() {
    var nickname  = (document.getElementById('nickname-input').value || '').trim();
    var chatLines = document.getElementById('chat-lines-select').value || '5';
    var keyboard  = document.getElementById('keyboard-toggle').checked || false;
    var voiceChat = document.getElementById('voicechat-toggle').checked !== false;

    if (!nickname) {
      showDialog('Atenção', 'Digite um nome de jogador antes de salvar.', false);
      return false;
    }

    var result = callAndroid('saveSettings', nickname, chatLines, keyboard, voiceChat);
    if (result === 'ok') {
      lastSavedSettings = { nickname: nickname, chatLines: chatLines, keyboard: keyboard, voiceChat: voiceChat };
      var topUser = document.getElementById('topbar-username');
      if (topUser) topUser.innerText = nickname;
      showToast('Configurações salvas!');
      return true;
    } else {
      showDialog('Erro', result || 'Não foi possível salvar.', true);
      return false;
    }
  }

  document.getElementById('save-settings') && document.getElementById('save-settings').addEventListener('click', function () {
    if (doSaveSettings() && pendingPage) {
      showPage(pendingPage);
      pendingPage = null;
    }
  });

  // Confirm Dialog
  var confirmDialog   = document.getElementById('confirm-dialog');
  var btnConfirmDiscard = document.getElementById('btn-confirm-discard');
  var btnConfirmSave    = document.getElementById('btn-confirm-save');

  function showConfirmDialog() {
    confirmDialog && confirmDialog.classList.remove('hidden');
  }

  btnConfirmDiscard && btnConfirmDiscard.addEventListener('click', function () {
    document.getElementById('nickname-input').value  = lastSavedSettings.nickname;
    document.getElementById('chat-lines-select').value = lastSavedSettings.chatLines;
    document.getElementById('keyboard-toggle').checked  = lastSavedSettings.keyboard;
    document.getElementById('voicechat-toggle').checked = lastSavedSettings.voiceChat;
    confirmDialog.classList.add('hidden');
    if (pendingPage) { showPage(pendingPage); pendingPage = null; }
  });

  btnConfirmSave && btnConfirmSave.addEventListener('click', function () {
    confirmDialog.classList.add('hidden');
    if (doSaveSettings() && pendingPage) { showPage(pendingPage); pendingPage = null; }
  });

  // ===== Play button =====
  document.getElementById('srv-connect') && document.getElementById('srv-connect').addEventListener('click', connectToServer);

  // ===== Reinstall =====
  document.getElementById('btn-reinstall') && document.getElementById('btn-reinstall').addEventListener('click', function () {
    callAndroid('reinstallData');
  });

  // ===== Add server dialog =====
  document.getElementById('btn-add-server') && document.getElementById('btn-add-server').addEventListener('click', function () {
    var inp = document.getElementById('server-addr-input');
    var err = document.getElementById('add-server-error');
    if (inp) inp.value = '';
    if (err) err.textContent = '';
    document.getElementById('add-server-dialog').classList.remove('hidden');
  });

  document.getElementById('btn-add-server-cancel') && document.getElementById('btn-add-server-cancel').addEventListener('click', function () {
    document.getElementById('add-server-dialog').classList.add('hidden');
  });

  document.getElementById('btn-add-server-save') && document.getElementById('btn-add-server-save').addEventListener('click', saveNewServer);

  document.getElementById('server-addr-input') && document.getElementById('server-addr-input').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') saveNewServer();
  });

  // ===== Initial Load =====
  loadConfig();
  loadServerBadge();

  // Ready
  document.body.classList.add('ready');
});

// ===== Connect to server =====
function connectToServer() {
  callAndroid('playGame');
}

// ===== Load Config (Settings) =====
function loadConfig() {
  try {
    var raw = callAndroid('getSettings');
    if (!raw) return;
    var cfg = JSON.parse(raw);

    var nickEl = document.getElementById('nickname-input');
    if (nickEl) nickEl.value = cfg.nickname || '';

    var chatEl = document.getElementById('chat-lines-select');
    if (chatEl) chatEl.value = cfg.chatLines || '5';

    var kbEl = document.getElementById('keyboard-toggle');
    if (kbEl) kbEl.checked = !!cfg.keyboard;

    var vcEl = document.getElementById('voicechat-toggle');
    if (vcEl) vcEl.checked = cfg.voiceChat !== false;

    var verEl = document.getElementById('launcher-version');
    if (verEl) verEl.innerText = cfg.version || 'v1.0';

    var topUser = document.getElementById('topbar-username');
    if (topUser && cfg.nickname) topUser.innerText = cfg.nickname;

    lastSavedSettings = {
      nickname:  cfg.nickname  || '',
      chatLines: cfg.chatLines || '5',
      keyboard:  !!cfg.keyboard,
      voiceChat: cfg.voiceChat !== false
    };
  } catch (e) {
    console.error('loadConfig error:', e);
  }
}

// ===== Server badge in topbar =====
function loadServerBadge() {
  try {
    var raw = callAndroid('getSelectedServer');
    var vLabel  = document.querySelector('#topbar-server-badge .v-label');
    var vValue  = document.getElementById('topbar-server-name');
    var countEl = document.getElementById('player-count-num');
    var addrEl  = document.getElementById('srv-address-home');
    var footerEl = document.getElementById('footer-server-addr');

    if (raw) {
      var s = JSON.parse(raw);
      var addr = s.host + ':' + s.port;
      if (vLabel)  vLabel.innerText  = 'SA-MP Identificado';
      if (vValue)  vValue.innerText  = addr;
      if (countEl) countEl.innerText = addr;
      if (addrEl)  addrEl.innerText  = addr;
      if (footerEl) footerEl.innerText = addr;
    } else {
      if (vLabel)  vLabel.innerText  = 'Sem servidor';
      if (vValue)  vValue.innerText  = 'Adicione um servidor';
      if (countEl) countEl.innerText = '—';
      if (addrEl)  addrEl.innerText  = 'Adicione um servidor';
    }
  } catch (e) {
    console.error('loadServerBadge error:', e);
  }
}

// ===== Load & render servers =====
function loadServers() {
  var listEl   = document.getElementById('servers-list');
  var countBadge = document.getElementById('servers-count');
  if (!listEl) return;

  try {
    var raw = callAndroid('getServers');
    var servers = raw ? JSON.parse(raw) : [];

    if (countBadge) countBadge.innerText = servers.length + (servers.length === 1 ? ' Disponível' : ' Disponíveis');

    if (servers.length === 0) {
      listEl.innerHTML = '<div class="servers-empty">Nenhum servidor salvo. Toque em Adicionar.</div>';
      return;
    }

    listEl.innerHTML = servers.map(function (s) {
      var addr = s.host + ':' + s.port;
      return '<div class="server-card' + (s.selected ? ' is-selected' : '') + '" data-host="' + s.host + '" data-port="' + s.port + '">'
        + '<div class="server-card-header">'
        + (s.selected ? '<div class="badge-selected"><span class="material-symbols-rounded" style="font-size:12px">star</span> ATIVO</div>' : '')
        + '<div class="server-card-status-badge"><span class="status-dot online"></span>Salvo</div>'
        + '<div class="server-card-icon"><svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M2 20h20v-8H2v8zm2-6h2v4H4v-4zm-2-6h20V2H2v6zm2 4h16v-2H4v2z"/></svg></div>'
        + '</div>'
        + '<div class="server-card-body">'
        + '<div class="server-card-info">'
        + '<h3 class="server-card-name">' + (s.name || addr) + '</h3>'
        + '<div class="server-card-address"><span class="material-symbols-rounded" style="font-size:14px;margin-right:4px">router</span>' + addr + '</div>'
        + '</div>'
        + '<div class="server-actions-row">'
        + (!s.selected ? '<button class="btn-use-server" onclick="selectServer(\'' + s.host + '\',' + s.port + ')"><span class="material-symbols-rounded" style="font-size:14px">check_circle</span>USAR</button>' : '')
        + '<button class="btn-server-connect" onclick="connectFrom(\'' + s.host + '\',' + s.port + ')" style="flex:1"><span class="material-symbols-rounded" style="font-size:16px;margin-right:4px">play_arrow</span>JOGAR AGORA</button>'
        + '<button class="btn-del-server" onclick="deleteServer(\'' + s.host + '\',' + s.port + ')"><span class="material-symbols-rounded" style="font-size:16px">delete</span></button>'
        + '</div>'
        + '</div>'
        + '</div>';
    }).join('');

  } catch (e) {
    console.error('loadServers error:', e);
    listEl.innerHTML = '<div class="servers-empty">Erro ao carregar servidores.</div>';
  }
}

function selectServer(host, port) {
  var result = callAndroid('selectServer', host, port);
  if (result === 'ok') { loadServers(); loadServerBadge(); showToast('Servidor selecionado!'); }
  else showDialog('Erro', result || 'Não foi possível selecionar.', true);
}

function deleteServer(host, port) {
  var result = callAndroid('removeServer', host, port);
  if (result === 'ok') { loadServers(); loadServerBadge(); showToast('Servidor removido.'); }
  else showDialog('Erro', result || 'Não foi possível remover.', true);
}

function connectFrom(host, port) {
  var result = callAndroid('selectServer', host, port);
  if (result === 'ok') callAndroid('playGame');
}

function saveNewServer() {
  var inp = document.getElementById('server-addr-input');
  var errEl = document.getElementById('add-server-error');
  var raw = (inp ? inp.value : '').trim();
  if (!raw) { if (errEl) errEl.textContent = 'Digite o endereço do servidor.'; return; }

  var result = callAndroid('addServer', raw);
  if (result === 'ok') {
    document.getElementById('add-server-dialog').classList.add('hidden');
    loadServers();
    loadServerBadge();
    showToast('Servidor adicionado!');
  } else {
    if (errEl) errEl.textContent = result || 'Endereço inválido.';
  }
}

// ===== Changelogs (static for now) =====
function loadChangelogs() {
  var el = document.getElementById('changelog-list');
  if (!el || el.dataset.loaded) return;
  el.dataset.loaded = '1';
  el.innerHTML = ''
    + '<div class="changelog-item">'
    + '<div class="changelog-version"><h3>v1.0.0</h3><span class="changelog-date">2025</span></div>'
    + '<div class="changelog-body"><ul>'
    + '<li>Launcher com interface renovada (design Gotham)</li>'
    + '<li>Suporte a múltiplos servidores SA-MP</li>'
    + '<li>Configurações de apelido e linhas do chat</li>'
    + '<li>Teclado Android e Voice Chat configuráveis</li>'
    + '</ul></div>'
    + '</div>';
}

// ===== Dialog =====
function showDialog(title, message, isError) {
  var overlay = document.getElementById('custom-dialog');
  var tEl = document.getElementById('dialog-title');
  var mEl = document.getElementById('dialog-message');
  var iEl = document.getElementById('dialog-icon-sym');
  if (tEl) tEl.innerText = title;
  if (mEl) mEl.innerText = message;
  if (iEl) {
    iEl.innerText = isError ? 'error' : 'info';
    iEl.style.color = isError ? '#e53935' : 'var(--accent)';
  }
  overlay && overlay.classList.remove('hidden');
}

// ===== Toast =====
var _toastTimer = null;
function showToast(msg) {
  var toast = document.getElementById('settings-toast');
  var msgEl = document.getElementById('toast-message');
  if (!toast) return;
  if (msgEl) msgEl.innerText = msg || 'Configuração salva com sucesso!';
  toast.classList.add('show');
  if (_toastTimer) clearTimeout(_toastTimer);
  _toastTimer = setTimeout(function () { toast.classList.remove('show'); }, 2500);
}
