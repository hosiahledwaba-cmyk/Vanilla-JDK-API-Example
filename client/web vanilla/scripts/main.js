// scripts/main.js

window.addEventListener('DOMContentLoaded', () => {
  const app = document.getElementById('app');
  const logoutBtn = document.getElementById('logoutBtn');

  function loadModule(module) {
    app.innerHTML = '';
    module.render(app);
  }

  function checkAuth() {
    const token = localStorage.getItem('token');
    if (token) {
      logoutBtn.classList.remove('hidden');

      const inGame = localStorage.getItem('sessionId');
      if (inGame) {
        loadGame();
      } else {
        import('./wallet.js').then(mod => loadModule(mod.WalletModule));
      }

    } else {
      logoutBtn.classList.add('hidden');
      import('./auth.js').then(mod => loadModule(mod.AuthModule));
    }
  }

  async function loadGame() {
    const sessionId = localStorage.getItem('sessionId');
    const token = localStorage.getItem('token');
    const email = localStorage.getItem('email');

    if (!sessionId || !token || !email) {
      console.warn('Missing session or user data');
      return checkAuth(); // fallback to auth
    }

    const { loadGameUI } = await import('./game.js');
    loadGameUI(sessionId, token, email);
  }

  logoutBtn.addEventListener('click', () => {
    localStorage.clear();
    checkAuth();
  });

  window.loadModule = loadModule;
  window.checkAuth = checkAuth;
  window.loadGame = loadGame; // make it globally accessible if needed elsewhere

  checkAuth();
});
