// client/web vanilla/scripts/lobby.js

import { renderNavTabs, bindNavTabs, loadModule } from './main.js';

export const LobbyModule = {
  render(container) {
    const token = localStorage.getItem('token');
    const user = localStorage.getItem('user') || 'Player';
    let selectedBet = 50;
    let queuePollInterval = null;

    container.innerHTML = `
      <div>
        ${renderNavTabs('lobby')}

        <div class="lobby-grid">
          <!-- Main Lobby Settings -->
          <div class="glass-card">
            <h2 style="font-size: 1.6rem; font-weight: 800; letter-spacing: -0.02em; margin-bottom: 0.25rem;">
              Game Lobby
            </h2>
            <p style="color: var(--text-secondary); font-size: 0.95rem; margin-bottom: 1.5rem;">
              Select your wager and challenge players or practice against RobinBot
            </p>

            <div class="form-group">
              <label class="form-label">Game Title</label>
              <div class="game-card-select">
                <div style="font-size: 2.2rem;">🎲</div>
                <div>
                  <div style="font-weight: 700; font-size: 1.1rem; color: #ffffff;">Snakes & Ladders (10x10)</div>
                  <div style="color: var(--text-secondary); font-size: 0.85rem;">
                    Classic 100-cell zigzag board, live dice rolling, instant token rewards
                  </div>
                </div>
              </div>
            </div>

            <div class="form-group" style="margin-top: 1.5rem;">
              <label class="form-label">Wager Amount (Tokens)</label>
              <div class="chip-select-group" id="wagerChips">
                <button type="button" class="chip-btn" data-bet="25">🪙 25</button>
                <button type="button" class="chip-btn active" data-bet="50">🪙 50</button>
                <button type="button" class="chip-btn" data-bet="100">🪙 100</button>
                <button type="button" class="chip-btn" data-bet="250">🪙 250</button>
              </div>
            </div>

            <div style="display: flex; gap: 1rem; margin-top: 2rem; flex-wrap: wrap;">
              <button id="joinQueueBtn" class="btn btn-primary" style="flex: 1; min-width: 200px;">
                ⚡ Quick Match (Queue)
              </button>
              <button id="playBotBtn" class="btn btn-violet" style="flex: 1; min-width: 200px;">
                🤖 Practice vs RobinBot
              </button>
            </div>

            <!-- Queue Status / Radar Box -->
            <div id="queueStatusBox" class="hidden" style="margin-top: 2rem; padding: 1.5rem; background: var(--surface-elevated); border: 1px solid var(--border-glass); border-radius: var(--radius-md); text-align: center;">
              <div class="radar-scanner">
                <div class="radar-beam"></div>
                <div style="font-size: 1.8rem; z-index: 2;">📡</div>
              </div>
              <div id="queueStatusText" style="font-weight: 700; color: #34d399; font-size: 1.05rem;">
                Searching for opponents...
              </div>
              <div style="color: var(--text-muted); font-size: 0.85rem; margin-top: 0.25rem;">
                Bet: <span id="queueBetDisplay" style="color: #fbbf24; font-weight: 700;">50</span> tokens • Auto-pairing enabled
              </div>
              <button id="cancelQueueBtn" class="btn btn-secondary" style="margin-top: 1rem; font-size: 0.85rem; padding: 0.4rem 0.9rem;">
                Cancel Matchmaking
              </button>
            </div>
          </div>

          <!-- Custom Room Management -->
          <div class="glass-card" style="display: flex; flex-direction: column; justify-content: space-between;">
            <div>
              <h3 style="font-size: 1.15rem; font-weight: 700; margin-bottom: 0.5rem; display: flex; align-items: center; gap: 0.5rem;">
                <span>🏠</span> Private Game Rooms
              </h3>
              <p style="color: var(--text-secondary); font-size: 0.85rem; margin-bottom: 1.5rem;">
                Host a private match or join an existing session code
              </p>

              <div class="form-group">
                <button id="createRoomBtn" class="btn btn-secondary" style="width: 100%;">
                  ➕ Create Custom Room
                </button>
              </div>

              <div style="position: relative; text-align: center; margin: 1.25rem 0;">
                <span style="background: var(--surface-glass); padding: 0 0.5rem; color: var(--text-muted); font-size: 0.75rem; text-transform: uppercase;">
                  or join by code
                </span>
              </div>

              <div class="form-group">
                <input type="text" id="joinRoomId" class="form-input" placeholder="Paste Session ID..." />
              </div>

              <button id="joinRoomBtn" class="btn btn-primary" style="width: 100%;">
                Enter Room
              </button>
            </div>

            <div style="margin-top: 1.5rem; padding: 0.85rem; background: rgba(8, 12, 22, 0.6); border-radius: var(--radius-sm); border: 1px solid var(--border-glass); font-size: 0.8rem; color: var(--text-muted);">
              💡 <strong>Pro-Tip:</strong> Win up to 2x your wager! Land on ladders to zoom ahead and dodge tricky snakes.
            </div>
          </div>
        </div>
      </div>
    `;

    bindNavTabs(container);

    // Wager chip selection
    document.querySelectorAll('#wagerChips .chip-btn').forEach(btn => {
      btn.onclick = () => {
        document.querySelectorAll('#wagerChips .chip-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        selectedBet = parseInt(btn.getAttribute('data-bet'), 10);
      };
    });

    const queueBox = document.getElementById('queueStatusBox');
    const queueStatusText = document.getElementById('queueStatusText');
    const queueBetDisplay = document.getElementById('queueBetDisplay');
    const joinQueueBtn = document.getElementById('joinQueueBtn');
    const playBotBtn = document.getElementById('playBotBtn');
    const cancelQueueBtn = document.getElementById('cancelQueueBtn');

    function stopQueue() {
      if (queuePollInterval) {
        clearInterval(queuePollInterval);
        queuePollInterval = null;
      }
      queueBox.classList.add('hidden');
      joinQueueBtn.disabled = false;
      playBotBtn.disabled = false;
    }

    cancelQueueBtn.onclick = () => {
      stopQueue();
      window.showToast('Matchmaking cancelled.', 'info');
    };

    async function launchGame(sessionId) {
      stopQueue();
      localStorage.setItem('sessionId', sessionId);
      window.showToast('Match found! Starting game...', 'success');
      const { loadGameUI } = await import('./game.js');
      loadGameUI(sessionId, token, user);
    }

    // Quick Match Queue
    joinQueueBtn.onclick = async () => {
      joinQueueBtn.disabled = true;
      playBotBtn.disabled = true;
      queueBox.classList.remove('hidden');
      queueBetDisplay.textContent = selectedBet;
      queueStatusText.textContent = 'Entering matchmaking queue...';

      try {
        const joinRes = await fetch('/games/queue/join', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ game: 'snl', bet: selectedBet })
        });

        const data = await joinRes.json();
        if (data.status === 'matched') {
          return launchGame(data.sessionId);
        }

        if (data.status === 'error') {
          window.showToast(data.message || 'Unable to join queue', 'error');
          stopQueue();
          return;
        }

        queueStatusText.textContent = 'Searching for opponents...';
        let polls = 0;

        queuePollInterval = setInterval(async () => {
          polls++;
          try {
            const statusRes = await fetch('/games/queue/status', {
              method: 'POST',
              headers: {
                Authorization: `Bearer ${token}`,
                'Content-Type': 'application/json'
              },
              body: JSON.stringify({ game: 'snl', bet: selectedBet })
            });
            const statusData = await statusRes.json();
            if (statusData.status === 'matched') {
              clearInterval(queuePollInterval);
              return launchGame(statusData.sessionId);
            }
          } catch (e) {
            console.warn('Queue poll error:', e);
          }

          if (polls > 15) {
            stopQueue();
            window.showToast('Matchmaking timed out. Please try again.', 'error');
          }
        }, 1200);

      } catch (err) {
        window.showToast('Connection error joining queue.', 'error');
        stopQueue();
      }
    };

    // Practice vs RobinBot
    playBotBtn.onclick = async () => {
      playBotBtn.disabled = true;
      joinQueueBtn.disabled = true;

      try {
        // Create 2-player game
        const res = await fetch('/create-game', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ maxPlayers: '2', bet: String(selectedBet) })
        });
        const data = await res.json();
        if (data.sessionId) {
          // Trigger queue/bot match or start directly
          await fetch('/start-game', {
            method: 'POST',
            headers: {
              Authorization: `Bearer ${token}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ sessionId: data.sessionId })
          });
          launchGame(data.sessionId);
        } else {
          window.showToast(data.error || 'Unable to start match.', 'error');
          playBotBtn.disabled = false;
          joinQueueBtn.disabled = false;
        }
      } catch (err) {
        window.showToast('Connection error starting game.', 'error');
        playBotBtn.disabled = false;
        joinQueueBtn.disabled = false;
      }
    };

    // Create custom room
    document.getElementById('createRoomBtn').onclick = async () => {
      try {
        const res = await fetch('/create-game', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ maxPlayers: '2', bet: String(selectedBet) })
        });
        const data = await res.json();
        if (data.sessionId) {
          prompt('Share this Session ID with your opponent:', data.sessionId);
          await fetch('/start-game', {
            method: 'POST',
            headers: {
              Authorization: `Bearer ${token}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ sessionId: data.sessionId })
          });
          launchGame(data.sessionId);
        }
      } catch (e) {
        window.showToast('Failed to create room', 'error');
      }
    };

    // Join room
    document.getElementById('joinRoomBtn').onclick = async () => {
      const code = document.getElementById('joinRoomId').value.trim();
      if (!code) return window.showToast('Please enter a session code', 'error');

      try {
        const res = await fetch('/join-game', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ sessionId: code })
        });
        const data = await res.json();
        if (data.joined) {
          launchGame(code);
        } else {
          window.showToast(data.error || 'Cannot join this session', 'error');
        }
      } catch (e) {
        window.showToast('Failed to join session', 'error');
      }
    };
  }
};
