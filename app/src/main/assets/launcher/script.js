// ===== Navigation =====
let currentPage = 'home';

function showPage(page) {
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));

  const pageEl = document.getElementById('page-' + page);
  const navEl = document.getElementById('nav-' + page);
  if (pageEl) pageEl.classList.add('active');
  if (navEl) navEl.classList.add('active');

  currentPage = page;

  const topbarLeft = document.querySelector('.topbar-left');
  const topbarCenter = document.querySelector('.topbar-center');
  const bottombarLeft = document.querySelector('.bottombar-left');

  const isHome = page === 'home';
  if (topbarLeft) topbarLeft.style.visibility = isHome ? 'visible' : 'hidden';
  if (topbarCenter) topbarCenter.style.visibility = isHome ? 'visible' : 'hidden';
  if (bottombarLeft) bottombarLeft.style.visibility = isHome ? 'visible' : 'hidden';

  if (page === 'servers') loadServers();
}

// ===== Bridge Helper =====
function callAndroid(method, ...args) {
  try {
    if (typeof Android !== 'undefined' && Android[method]) {
      return Android[method](...args);
    }
  } catch (e) {
    console.error('Bridge error:', method, e);
  }
  return null;
}

// ===== Settings =====
let currentSettings = {};

function loadSettings() {
  try {
    const raw = callAndroid('getSettings');
    if (!raw) return;
    currentSettings = JSON.parse(raw);

    const nickEl = document.getElementById('nickname-input');
    if (nickEl) nickEl.value = currentSettings.nickname || '';

    const chatEl = document.getElementById('chat-lines-select');
    if (chatEl) chatEl.value = currentSettings.chatLines || '5';

    const keyboardEl = document.getElementById('keyboard-toggle');
    if (keyboardEl) keyboardEl.checked = !!currentSettings.keyboard;

    const voiceEl = document.getElementById('voicechat-toggle');
    if (voiceEl) voiceEl.checked = currentSettings.voiceChat !== false;

    const verEl = document.getElementById('launcher-version');
    if (verEl) verEl.textContent = currentSettings.version || 'v1.0';

    const topVerEl = document.getElementById('topbar-version');
    if (topVerEl) topVerEl.textContent = currentSettings.version || 'v1.0';

    const topUser = document.getElementById('topbar-username');
    if (topUser && currentSettings.nickname) topUser.textContent = currentSettings.nickname;

  } catch (e) {
    console.error('loadSettings error:', e);
  }
}

function saveSettings() {
  const nickname = document.getElementById('nickname-input')?.value?.trim() || '';
  const chatLines = document.getElementById('chat-lines-select')?.value || '5';
  const keyboard = document.getElementById('keyboard-toggle')?.checked || false;
  const voiceChat = document.getElementById('voicechat-toggle')?.checked || false;

  if (!nickname) {
    showDialog('Atenção', 'Digite um nome de jogador antes de salvar.', 'warning');
    return;
  }

  const result = callAndroid('saveSettings', nickname, chatLines, keyboard, voiceChat);
  if (result === 'ok') {
    currentSettings.nickname = nickname;
    currentSettings.chatLines = chatLines;
    currentSettings.keyboard = keyboard;
    currentSettings.voiceChat = voiceChat;

    const topUser = document.getElementById('topbar-username');
    if (topUser) topUser.textContent = nickname;

    showToast('Configurações salvas!');
  } else {
    showDialog('Erro', result || 'Não foi possível salvar.', 'error');
  }
}

// ===== Servers =====
function loadServers() {
  const listEl = document.getElementById('servers-list');
  if (!listEl) return;

  try {
    const raw = callAndroid('getServers');
    const servers = raw ? JSON.parse(raw) : [];

    if (servers.length === 0) {
      listEl.innerHTML = '<div class="servers-empty">Nenhum servidor salvo. Toque em Adicionar.</div>';
      return;
    }

    listEl.innerHTML = servers.map(s => `
      <div class="server-card ${s.selected ? 'selected-card' : ''}" data-host="${s.host}" data-port="${s.port}">
        <div class="server-card-header">
          ${s.selected ? '<div class="selected-badge"><span class="material-symbols-rounded" style="font-size:12px;">star</span> ATIVO</div>' : ''}
          <div class="server-card-status-badge">
            <span class="status-dot"></span>
            Salvo
          </div>
          <div class="server-card-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
              <path d="M2 20h20v-8H2v8zm2-6h2v4H4v-4zm-2-6h20V2H2v6zm2 4h16v-2H4v2z"/>
            </svg>
          </div>
        </div>
        <div class="server-card-body">
          <div class="server-card-info">
            <div class="server-card-name">${s.name || s.host + ':' + s.port}</div>
            <div class="server-card-address">
              <span class="material-symbols-rounded" style="font-size:13px;">router</span>
              ${s.host}:${s.port}
            </div>
          </div>
          <div class="server-card-actions">
            ${!s.selected ? `<button class="btn-server-connect secondary" onclick="selectServer('${s.host}',${s.port})">
              <span class="material-symbols-rounded" style="font-size:14px;">check_circle</span>
              Usar
            </button>` : ''}
            <button class="btn-server-connect" onclick="connectServer('${s.host}',${s.port})">
              <span class="material-symbols-rounded" style="font-size:14px;">play_arrow</span>
              JOGAR
            </button>
            <button class="btn-server-delete" onclick="deleteServer('${s.host}',${s.port})" title="Remover">
              <span class="material-symbols-rounded" style="font-size:16px;">delete</span>
            </button>
          </div>
        </div>
      </div>
    `).join('');

    updateSelectedServerDisplay();
  } catch (e) {
    console.error('loadServers error:', e);
    listEl.innerHTML = '<div class="servers-empty">Erro ao carregar servidores.</div>';
  }
}

function selectServer(host, port) {
  const result = callAndroid('selectServer', host, port);
  if (result === 'ok') {
    loadServers();
    updateSelectedServerDisplay();
    showToast('Servidor selecionado!');
  } else {
    showDialog('Erro', result || 'Não foi possível selecionar.', 'error');
  }
}

function deleteServer(host, port) {
  const result = callAndroid('removeServer', host, parseInt(port));
  if (result === 'ok') {
    loadServers();
    updateSelectedServerDisplay();
    showToast('Servidor removido.');
  } else {
    showDialog('Erro', result || 'Não foi possível remover.', 'error');
  }
}

function connectServer(host, port) {
  const result = callAndroid('selectServer', host, port);
  if (result === 'ok') {
    callAndroid('playGame');
  }
}

function updateSelectedServerDisplay() {
  try {
    const raw = callAndroid('getSelectedServer');
    const serverEl = document.getElementById('selected-server-display');
    const srvAddressEl = document.getElementById('srv-address');
    const footerEl = document.getElementById('footer-server');

    if (raw) {
      const s = JSON.parse(raw);
      const label = s.name || (s.host + ':' + s.port);
      const addr = s.host + ':' + s.port;
      if (serverEl) serverEl.textContent = addr;
      if (srvAddressEl) srvAddressEl.textContent = addr;
      if (footerEl) footerEl.innerHTML = `
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="2" y="2" width="20" height="8" rx="2"/>
          <rect x="2" y="14" width="20" height="8" rx="2"/>
          <circle cx="6" cy="6" r="1"/><circle cx="6" cy="18" r="1"/>
        </svg>
        ${addr}`;
    } else {
      if (serverEl) serverEl.textContent = 'Sem servidor';
      if (srvAddressEl) srvAddressEl.textContent = 'Adicione um servidor';
    }
  } catch (e) {
    console.error('updateSelectedServerDisplay error:', e);
  }
}

// ===== Add Server Dialog =====
function showAddServerDialog() {
  const input = document.getElementById('server-input');
  const errEl = document.getElementById('server-error');
  if (input) input.value = '';
  if (errEl) errEl.textContent = '';
  document.getElementById('add-server-dialog')?.classList.remove('hidden');
}

function saveNewServer() {
  const input = document.getElementById('server-input');
  const errEl = document.getElementById('server-error');
  const rawAddress = input?.value?.trim() || '';

  if (!rawAddress) {
    if (errEl) errEl.textContent = 'Digite o endereço do servidor.';
    return;
  }

  const result = callAndroid('addServer', rawAddress);
  if (result === 'ok') {
    document.getElementById('add-server-dialog')?.classList.add('hidden');
    loadServers();
    updateSelectedServerDisplay();
    showToast('Servidor adicionado!');
  } else {
    if (errEl) errEl.textContent = result || 'Endereço inválido.';
  }
}

// ===== Play Game =====
function playGame() {
  callAndroid('playGame');
}

// ===== Dialog =====
function showDialog(title, message, type) {
  const overlay = document.getElementById('custom-dialog');
  const titleEl = document.getElementById('dialog-title');
  const msgEl = document.getElementById('dialog-message');
  const iconEl = document.getElementById('dialog-icon-symbol');
  const boxEl = document.getElementById('custom-dialog-box');

  if (titleEl) titleEl.textContent = title;
  if (msgEl) msgEl.textContent = message;

  if (iconEl) {
    if (type === 'error') { iconEl.textContent = 'error'; iconEl.style.color = '#e53935'; }
    else if (type === 'warning') { iconEl.textContent = 'warning'; iconEl.style.color = '#ff9800'; }
    else { iconEl.textContent = 'info'; iconEl.style.color = 'var(--accent)'; }
  }

  overlay?.classList.remove('hidden');
}

// ===== Toast =====
let toastTimer = null;
function showToast(msg, type) {
  const toast = document.getElementById('toast');
  const msgEl = document.getElementById('toast-msg');
  if (!toast) return;
  if (msgEl) msgEl.textContent = msg || 'Salvo!';
  toast.className = 'toast' + (type === 'error' ? ' error' : '');
  toast.classList.add('show');
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 2500);
}

// ===== DOMContentLoaded =====
document.addEventListener('DOMContentLoaded', () => {
  // Nav buttons
  document.getElementById('nav-home')?.addEventListener('click', () => showPage('home'));
  document.getElementById('nav-servers')?.addEventListener('click', () => showPage('servers'));
  document.getElementById('nav-settings')?.addEventListener('click', () => showPage('settings'));
  document.getElementById('nav-changelogs')?.addEventListener('click', () => showPage('changelogs'));

  // Home buttons
  document.getElementById('srv-connect')?.addEventListener('click', playGame);
  document.getElementById('open-settings-btn')?.addEventListener('click', () => showPage('settings'));

  // Settings
  document.getElementById('save-settings')?.addEventListener('click', saveSettings);
  document.getElementById('btn-reinstall')?.addEventListener('click', () => {
    if (typeof Android !== 'undefined') Android.reinstallData?.();
  });

  // Servers
  document.getElementById('btn-add-server')?.addEventListener('click', showAddServerDialog);
  document.getElementById('btn-server-cancel')?.addEventListener('click', () => {
    document.getElementById('add-server-dialog')?.classList.add('hidden');
  });
  document.getElementById('btn-server-save')?.addEventListener('click', saveNewServer);

  // Server input enter key
  document.getElementById('server-input')?.addEventListener('keydown', e => {
    if (e.key === 'Enter') saveNewServer();
  });

  // Dialog close
  document.getElementById('btn-dialog-ok')?.addEventListener('click', () => {
    document.getElementById('custom-dialog')?.classList.add('hidden');
  });

  // Load data
  loadSettings();
  updateSelectedServerDisplay();

  // Ready
  document.body.classList.add('ready');
});
