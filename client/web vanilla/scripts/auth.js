// client/web vanilla/scripts/auth.js

export const AuthModule = {
  render(container) {
    let mode = 'login'; // 'login' or 'signup'

    function updateView() {
      container.innerHTML = `
        <div class="auth-container">
          <div class="card" style="box-shadow: var(--shadow-pop); border-color: var(--border-medium); padding: 2.25rem;">
            <div class="auth-header">
              <div style="width: 48px; height: 48px; border-radius: var(--radius-md); background: var(--surface-secondary); border: 1px solid var(--border-medium); display: flex; align-items: center; justify-content: center; margin: 0 auto 1.25rem; font-size: 1.5rem; color: var(--accent-primary);">
                🏹
              </div>
              <h2 style="font-size: 1.75rem; font-weight: 800; letter-spacing: -0.03em; margin-bottom: 0.35rem;">
                ${mode === 'login' ? 'Welcome Back' : 'Join RobinHood'}
              </h2>
              <p style="color: var(--text-secondary); font-size: 0.9rem; line-height: 1.45;">
                ${mode === 'login' ? 'Sign in to access your multiplayer rooms, records, and token wallet.' : 'Create an account to claim 100 free tokens and challenge live opponents or tactical bots.'}
              </p>
            </div>

            <div class="auth-toggle">
              <button type="button" id="toggleLogin" class="auth-toggle-btn ${mode === 'login' ? 'active' : ''}">Sign In</button>
              <button type="button" id="toggleSignup" class="auth-toggle-btn ${mode === 'signup' ? 'active' : ''}">Create Account</button>
            </div>

            <form id="authForm">
              <div class="form-group">
                <label class="form-label" for="authUsername">Username or Email</label>
                <input type="text" id="authUsername" class="form-input" placeholder="e.g. player1@robinhood.io" required autocomplete="username" />
              </div>

              <div class="form-group">
                <label class="form-label" for="authPassword">Password</label>
                <input type="password" id="authPassword" class="form-input" placeholder="••••••••" required autocomplete="current-password" />
              </div>

              <button type="submit" id="authSubmitBtn" class="btn btn-primary" style="width: 100%; margin-top: 0.75rem; padding: 0.85rem; font-size: 1rem;">
                ${mode === 'login' ? 'Sign In to Platform' : 'Claim 100 Tokens & Register'}
              </button>
            </form>

            <div style="margin-top: 1.75rem; padding-top: 1.25rem; border-top: 1px solid var(--border-subtle); font-size: 0.78rem; color: var(--text-muted); text-align: center; display: flex; align-items: center; justify-content: center; gap: 0.5rem;">
              <span>🔒</span> Pure Vanilla JDK Backend • PostgreSQL 16 Persistence
            </div>
          </div>
        </div>
      `;

      document.getElementById('toggleLogin').onclick = () => {
        mode = 'login';
        updateView();
      };

      document.getElementById('toggleSignup').onclick = () => {
        mode = 'signup';
        updateView();
      };

      const form = document.getElementById('authForm');
      const submitBtn = document.getElementById('authSubmitBtn');

      form.onsubmit = async (e) => {
        e.preventDefault();
        const username = document.getElementById('authUsername').value.trim();
        const password = document.getElementById('authPassword').value.trim();

        if (!username || !password) {
          window.showToast('Please fill in both fields.', 'error');
          return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = 'Processing...';

        try {
          const endpoint = mode === 'login' ? '/auth/login' : '/auth/signup';
          const res = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
          });

          const data = await res.json();

          if (mode === 'signup') {
            if (data.success) {
              window.showToast('Account created! Logging you in...', 'success');
              // Automatically log in after registration
              const loginRes = await fetch('/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
              });
              const loginData = await loginRes.json();
              if (loginData.token) {
                localStorage.setItem('token', loginData.token);
                localStorage.setItem('user', loginData.user || username);
                localStorage.setItem('email', loginData.email || username);
                window.checkAuth();
              } else {
                mode = 'login';
                updateView();
              }
            } else {
              window.showToast(data.error || 'Signup failed', 'error');
            }
          } else {
            if (data.token) {
              localStorage.setItem('token', data.token);
              localStorage.setItem('user', data.user || username);
              localStorage.setItem('email', data.email || username);
              window.showToast(`Welcome back, ${username}!`, 'success');
              window.checkAuth();
            } else {
              window.showToast(data.error || 'Invalid username or password', 'error');
            }
          }
        } catch (err) {
          window.showToast('Server connection error. Is the server running?', 'error');
        } finally {
          submitBtn.disabled = false;
          submitBtn.textContent = mode === 'login' ? 'Sign In' : 'Claim Tokens & Register';
        }
      };
    }

    updateView();
  }
};