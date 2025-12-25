// scripts/auth.js

const AuthModule = {
  render(container) {
    container.innerHTML = `
      <div>
        <h2 class="text-xl font-semibold mb-4">Login / Signup</h2>
        <form id="authForm" class="space-y-4">
          <input type="text" id="username" placeholder="Username" class="w-full border p-2 rounded" required />
          <input type="password" id="password" placeholder="Password" class="w-full border p-2 rounded" required />
          <div class="flex space-x-4">
            <button type="submit" class="bg-blue-600 text-white px-4 py-2 rounded">Login</button>
            <button type="button" id="signupBtn" class="bg-green-600 text-white px-4 py-2 rounded">Sign Up</button>
          </div>
        </form>
        <p id="authMsg" class="mt-2 text-sm text-red-500"></p>
      </div>
    `;

    const form = document.getElementById('authForm');
    const signupBtn = document.getElementById('signupBtn');
    const msg = document.getElementById('authMsg');

    form.onsubmit = async e => {
      e.preventDefault();
      const username = document.getElementById('username').value.trim();
      const password = document.getElementById('password').value.trim();
      const res = await fetch('http://localhost:8080/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const data = await res.json();
      if (data.token) {
        localStorage.setItem('token', data.token);
        localStorage.setItem('user', username);
        checkAuth();
      } else {
        msg.textContent = data.error || 'Login failed';
      }
    };

    signupBtn.onclick = async () => {
      const username = document.getElementById('username').value.trim();
      const password = document.getElementById('password').value.trim();
      const res = await fetch('http://localhost:8080/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const data = await res.json();
      if (data.success) {
        msg.textContent = 'Signup successful. You can now log in.';
      } else {
        msg.textContent = data.error || 'Signup failed';
      }
    };
  }
};

window.AuthModule = AuthModule;