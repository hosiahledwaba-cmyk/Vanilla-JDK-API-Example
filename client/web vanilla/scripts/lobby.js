// scripts/lobby.js

export const LobbyModule = {
  render(container) {
    const token = localStorage.getItem('token');

    container.innerHTML = `
      <div>
        <h2 class="text-xl font-bold mb-4">Game Lobby</h2>

        <div class="mb-4">
          <label class="block font-semibold">Select Game:</label>
          <select id="gameSelect" class="w-full border p-2 rounded">
            <option value="snl">🎲 Snakes & Ladders</option>
            <!-- Add more games here -->
          </select>
        </div>

        <div class="mb-4">
          <label class="block font-semibold">Bet Amount (tokens):</label>
          <input id="betAmount" type="number" class="w-full border p-2 rounded" placeholder="e.g. 50" />
        </div>

        <button id="joinQueueBtn" class="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          Join Queue
        </button>

        <div id="queueStatus" class="mt-6 text-sm text-gray-700"></div>

        <div class="mt-6 space-x-2">
          <button class="text-sm underline text-gray-500" id="goBack">← Wallet</button>
        </div>
      </div>
    `;

    const queueStatus = document.getElementById('queueStatus');
    const joinQueueBtn = document.getElementById('joinQueueBtn');

    document.getElementById('goBack').onclick = () => {
      import('./wallet.js').then(mod => loadModule(mod.WalletModule));
    };

    joinQueueBtn.onclick = async () => {
      const gameId = document.getElementById('gameSelect').value;
      const bet = parseInt(document.getElementById('betAmount').value);
      if (!bet || bet <= 0) return alert('Enter a valid bet amount.');

      queueStatus.textContent = 'Searching for available games...';

      const joinRes = await fetch(`http://localhost:8080/games/queue/join`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ game: gameId, bet })
      });

      const { status, message } = await joinRes.json();
      if (status !== 'queued') {
        queueStatus.textContent = message || 'Unable to join queue';
        return;
      }

      checkQueue(gameId, bet);
    };

    async function checkQueue(gameId, bet) {
      let attempts = 0;

      const interval = setInterval(async () => {
        attempts++;
        const statusRes = await fetch(`http://localhost:8080/games/queue/status`, {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ game: gameId, bet })
        });

        const data = await statusRes.json();

        if (data.status === 'matched') {
          clearInterval(interval);
          queueStatus.innerHTML = `✅ Match found! Starting game...`;
          setTimeout(() => {
            import('./game.js').then(mod => mod.GameModule.render(container, data.sessionId));
          }, 1000);
        } else if (data.status === 'suggest') {
          queueStatus.innerHTML = `
            ❓ No match at ${bet} tokens yet.<br/>
            Suggested open games: ${data.suggestions.map(s => s.bet + ' tokens').join(', ')}<br/>
            You can adjust your bet and try again.
          `;
        } else {
          queueStatus.textContent = `Waiting for more players... (${attempts * 3}s)`;
        }

        if (attempts > 20) {
          clearInterval(interval);
          queueStatus.textContent = '❌ Timeout. Try again later.';
        }
      }, 3000);
    }
  }
};
