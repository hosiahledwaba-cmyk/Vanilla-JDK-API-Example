// scripts/wallet.js

export const WalletModule = {
  render(container) {
    const username = localStorage.getItem('user') || 'Unknown';
    const token = localStorage.getItem('token');

    container.innerHTML = `
      <div>
        <h2 class="text-xl font-bold mb-4">Welcome, ${username}</h2>

        <div class="mb-4">
          <p class="text-sm text-gray-500">Your Token Balance:</p>
          <p id="balance" class="text-3xl font-bold text-green-600">...</p>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <form id="depositForm" class="border p-4 rounded shadow">
            <h3 class="font-semibold mb-2">Deposit</h3>
            <input id="depositAmount" type="number" min="1" class="w-full border p-2 rounded mb-2" placeholder="Amount" />
            <button type="submit" class="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700">Deposit</button>
          </form>

          <form id="withdrawForm" class="border p-4 rounded shadow">
            <h3 class="font-semibold mb-2">Withdraw</h3>
            <input id="withdrawAmount" type="number" min="1" class="w-full border p-2 rounded mb-2" placeholder="Amount" />
            <button type="submit" class="w-full bg-red-600 text-white py-2 rounded hover:bg-red-700">Withdraw</button>
          </form>
        </div>

        <div class="mt-6 space-x-2">
          <button id="toLobbyBtn" class="bg-purple-600 text-white px-4 py-2 rounded hover:bg-purple-700">Enter Game Lobby</button>
          <button id="toLeaderboardBtn" class="bg-gray-600 text-white px-4 py-2 rounded hover:bg-gray-700">View Leaderboard</button>
          <button id="toHistoryBtn" class="bg-gray-600 text-white px-4 py-2 rounded hover:bg-gray-700">Game History</button>
        </div>
        <p id="walletMsg" class="mt-4 text-sm text-gray-500"></p>
      </div>
    `;

    const msg = document.getElementById('walletMsg');

    async function updateBalance() {
      const res = await fetch('http://localhost:8080/wallet/balance', {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}` }
      });
      const data = await res.json();
      document.getElementById('balance').textContent = data.balance ?? '0';
    }

    document.getElementById('depositForm').onsubmit = async (e) => {
      e.preventDefault();
      const amount = parseInt(document.getElementById('depositAmount').value);
      const res = await fetch('http://localhost:8080/wallet/deposit', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ amount })
      });
      const data = await res.json();
      msg.textContent = data.message || 'Deposited';
      updateBalance();
    };

    document.getElementById('withdrawForm').onsubmit = async (e) => {
      e.preventDefault();
      const amount = parseInt(document.getElementById('withdrawAmount').value);
      const res = await fetch('http://localhost:8080/wallet/withdraw', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ amount })
      });
      const data = await res.json();
      msg.textContent = data.message || 'Withdrawn';
      updateBalance();
    };

    // Navigation buttons
    document.getElementById('toLobbyBtn').onclick = () => {
      import('./lobby.js').then(mod => loadModule(mod.LobbyModule));
    };
    document.getElementById('toLeaderboardBtn').onclick = () => {
      import('./leaderboard.js').then(mod => loadModule(mod.LeaderboardModule));
    };
    document.getElementById('toHistoryBtn').onclick = () => {
      import('./history.js').then(mod => loadModule(mod.HistoryModule));
    };

    updateBalance();
  }
};
