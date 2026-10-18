// client/web vanilla/scripts/main.js

export function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  const icon = type === 'success' ? '✅' : type === 'error' ? '❌' : 'ℹ️';
  toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;

  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(30px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

window.showToast = showToast;

export async function updateHeaderBalance() {
  const token = localStorage.getItem('token');
  const chip = document.getElementById('balanceChip');
  const balanceEl = document.getElementById('headerBalance');
  if (!token || !chip || !balanceEl) return;

  try {
    const res = await fetch('/wallet/balance', {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json();
      balanceEl.textContent = data.balance ?? '0';
    }
  } catch (e) {
    console.warn('Failed to update balance:', e);
  }
}

window.updateHeaderBalance = updateHeaderBalance;

export function renderNavTabs(activeTab = 'lobby') {
  return `
    <div class="tabs-bar">
      <button class="tab-btn ${activeTab === 'lobby' ? 'active' : ''}" data-nav="lobby">
        🎲 Game Lobby
      </button>
      <button class="tab-btn ${activeTab === 'wallet' ? 'active' : ''}" data-nav="wallet">
        💼 Token Wallet
      </button>
      <button class="tab-btn ${activeTab === 'leaderboard' ? 'active' : ''}" data-nav="leaderboard">
        🏆 Leaderboard
      </button>
      <button class="tab-btn ${activeTab === 'history' ? 'active' : ''}" data-nav="history">
        📜 History
      </button>
    </div>
  `;
}

export function bindNavTabs(container) {
  container.querySelectorAll('[data-nav]').forEach(btn => {
    btn.onclick = async () => {
      const tab = btn.getAttribute('data-nav');
      if (tab === 'lobby') {
        const { LobbyModule } = await import('./lobby.js');
        loadModule(LobbyModule);
      } else if (tab === 'wallet') {
        const { WalletModule } = await import('./wallet.js');
        loadModule(WalletModule);
      } else if (tab === 'leaderboard') {
        const { LeaderboardModule } = await import('./leaderboard.js');
        loadModule(LeaderboardModule);
      } else if (tab === 'history') {
        const { HistoryModule } = await import('./history.js');
        loadModule(HistoryModule);
      }
    };
  });
}

export function loadModule(module) {
  const app = document.getElementById('app');
  if (!app) return;
  app.innerHTML = '';
  module.render(app);
}

window.loadModule = loadModule;

export async function checkAuth() {
  const token = localStorage.getItem('token');
  const user = localStorage.getItem('user') || 'Player';
  const logoutBtn = document.getElementById('logoutBtn');
  const userBadge = document.getElementById('userBadge');
  const balanceChip = document.getElementById('balanceChip');
  const headerUsername = document.getElementById('headerUsername');
  const headerAvatar = document.getElementById('headerAvatar');

  if (token) {
    if (logoutBtn) logoutBtn.classList.remove('hidden');
    if (userBadge) userBadge.classList.remove('hidden');
    if (balanceChip) balanceChip.classList.remove('hidden');
    if (headerUsername) headerUsername.textContent = user;
    if (headerAvatar) headerAvatar.textContent = user.charAt(0).toUpperCase();

    updateHeaderBalance();

    const activeSession = localStorage.getItem('sessionId');
    if (activeSession) {
      const { loadGameUI } = await import('./game.js');
      loadGameUI(activeSession, token, user);
    } else {
      const { LobbyModule } = await import('./lobby.js');
      loadModule(LobbyModule);
    }
  } else {
    if (logoutBtn) logoutBtn.classList.add('hidden');
    if (userBadge) userBadge.classList.add('hidden');
    if (balanceChip) balanceChip.classList.add('hidden');

    const { AuthModule } = await import('./auth.js');
    loadModule(AuthModule);
  }
}

window.checkAuth = checkAuth;

window.addEventListener('DOMContentLoaded', () => {
  const logoutBtn = document.getElementById('logoutBtn');
  const brandLogo = document.getElementById('brandLogo');

  if (logoutBtn) {
    logoutBtn.onclick = () => {
      localStorage.clear();
      showToast('You have been logged out.', 'info');
      checkAuth();
    };
  }

  if (brandLogo) {
    brandLogo.onclick = (e) => {
      e.preventDefault();
      if (localStorage.getItem('token')) {
        localStorage.removeItem('sessionId');
        checkAuth();
      }
    };
  }

  checkAuth();
});
