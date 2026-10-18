// client/web vanilla/scripts/game.js

let activeEventSource = null;

const DICE_FACES = ['⚀', '⚁', '⚂', '⚃', '⚄', '⚅'];
const TOKEN_COLORS = ['token-0', 'token-1', 'token-2', 'token-3'];

export async function loadGameUI(sessionId, token, userEmail) {
  const app = document.getElementById('app');
  if (!app) return;

  if (activeEventSource) {
    activeEventSource.close();
    activeEventSource = null;
  }

  app.innerHTML = `
    <div>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
        <div>
          <h2 style="font-size: 1.5rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
            <span>🎲</span> Snakes & Ladders
          </h2>
          <div style="font-size: 0.85rem; color: var(--text-secondary); font-family: var(--font-mono);">
            Match ID: <span style="color: var(--accent-cyan);">${sessionId.substring(0, 8)}...</span>
          </div>
        </div>
        <button id="leaveMatchBtn" class="btn btn-secondary" style="font-size: 0.85rem; padding: 0.45rem 0.85rem;">
          ← Exit to Lobby
        </button>
      </div>

      <div class="game-layout">
        <!-- 10x10 Board Grid -->
        <div class="board-container">
          <div id="boardGrid" class="board-grid"></div>
        </div>

        <!-- Sidebar / Dice Roller / Match Feed -->
        <div class="game-sidebar">
          <!-- Turn Banner -->
          <div id="turnBanner" class="turn-banner opponent-turn">
            Connecting match...
          </div>

          <!-- 3D Animated Dice Stage -->
          <div class="dice-stage">
            <div id="diceBox" class="dice-box">🎲</div>
            <button id="rollDiceBtn" class="btn btn-primary" style="width: 100%; font-size: 1.1rem; padding: 0.85rem;" disabled>
              🎲 Roll Dice
            </button>
          </div>

          <!-- Scoreboard Card -->
          <div style="background: var(--surface-glass); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 1rem;">
            <div style="font-size: 0.8rem; font-weight: 700; text-transform: uppercase; color: var(--text-secondary); margin-bottom: 0.75rem;">
              Players in Match
            </div>
            <div id="playerList" style="display: flex; flex-direction: column; gap: 0.5rem;"></div>
          </div>

          <!-- Match Event Feed -->
          <div style="background: var(--surface-glass); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 1rem;">
            <div style="font-size: 0.8rem; font-weight: 700; text-transform: uppercase; color: var(--text-secondary); margin-bottom: 0.5rem;">
              Live Match Feed
            </div>
            <div id="matchFeed" class="match-feed">
              <div class="feed-item">Match initialized. Board loaded.</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Victory / Game Over Modal -->
      <div id="gameOverModal" class="modal-overlay hidden">
        <div class="modal-content">
          <div id="modalIcon" style="font-size: 3.5rem; margin-bottom: 0.5rem;">🏆</div>
          <h2 id="modalTitle" style="font-size: 1.8rem; font-weight: 800; margin-bottom: 0.5rem;">VICTORY!</h2>
          <p id="modalDesc" style="color: var(--text-secondary); margin-bottom: 1.5rem; font-size: 1rem;">
            Congratulations! You reached cell 100 first!
          </p>
          <button id="modalActionBtn" class="btn btn-primary" style="width: 100%;">
            Return to Game Lobby
          </button>
        </div>
      </div>
    </div>
  `;

  document.getElementById('leaveMatchBtn').onclick = () => {
    if (activeEventSource) activeEventSource.close();
    localStorage.removeItem('sessionId');
    window.checkAuth();
  };

  document.getElementById('modalActionBtn').onclick = () => {
    if (activeEventSource) activeEventSource.close();
    localStorage.removeItem('sessionId');
    window.checkAuth();
  };

  const boardEl = document.getElementById('boardGrid');
  const turnBanner = document.getElementById('turnBanner');
  const diceBtn = document.getElementById('rollDiceBtn');
  const diceBox = document.getElementById('diceBox');
  const feedEl = document.getElementById('matchFeed');
  const playerListEl = document.getElementById('playerList');

  let boardMeta = { snakes: {}, ladders: {} };
  let gameState = { positions: {}, currentTurn: '', isOver: false };

  function appendFeed(text, type = '') {
    const item = document.createElement('div');
    item.className = `feed-item ${type}`;
    item.textContent = text;
    feedEl.appendChild(item);
    feedEl.scrollTop = feedEl.scrollHeight;
  }

  // Fetch Board Configuration
  try {
    const res = await fetch(`/games/snl/board/${sessionId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (res.ok) {
      boardMeta = await res.json();
    }
  } catch (e) {
    console.warn('Failed to fetch board meta:', e);
  }

  // Draw 10x10 Board Grid (Rows 10 down to 1)
  function renderBoardGrid() {
    boardEl.innerHTML = '';
    let leftToRight = false; // Row 10 is 100 down to 91 (right to left)

    for (let row = 10; row >= 1; row--) {
      const rowCells = [];
      for (let col = 1; col <= 10; col++) {
        let cellNum;
        if (leftToRight) {
          cellNum = (row - 1) * 10 + col;
        } else {
          cellNum = row * 10 - col + 1;
        }
        rowCells.push(cellNum);
      }
      leftToRight = !leftToRight;

      for (const num of rowCells) {
        const cell = document.createElement('div');
        cell.id = `cell-${num}`;
        cell.className = `board-cell ${(num % 2 === 0) ? 'alt' : ''}`;

        if (num === 1) cell.classList.add('cell-1');
        if (num === 100) cell.classList.add('cell-100');

        let portalHtml = '';
        if (boardMeta.snakes && boardMeta.snakes[num]) {
          cell.classList.add('has-snake');
          portalHtml = `<span class="portal-tag portal-snake">🐍 ${boardMeta.snakes[num]}</span>`;
        } else if (boardMeta.ladders && boardMeta.ladders[num]) {
          cell.classList.add('has-ladder');
          portalHtml = `<span class="portal-tag portal-ladder">🪜 ${boardMeta.ladders[num]}</span>`;
        } else if (num === 1) {
          portalHtml = `<span style="font-size: 0.65rem; color: #34d399; font-weight: 700;">🏁 START</span>`;
        } else if (num === 100) {
          portalHtml = `<span style="font-size: 0.65rem; color: #fbbf24; font-weight: 700;">🏆 GOAL</span>`;
        }

        cell.innerHTML = `
          <div style="display: flex; justify-content: space-between; align-items: flex-start;">
            <span class="cell-num">${num}</span>
            ${portalHtml}
          </div>
          <div class="tokens-holder" id="tokens-${num}"></div>
        `;

        boardEl.appendChild(cell);
      }
    }
  }

  renderBoardGrid();

  // Draw Player Tokens
  function updatePlayerTokens() {
    // Clear all token holders
    document.querySelectorAll('.tokens-holder').forEach(el => el.innerHTML = '');

    const players = Object.entries(gameState.positions || {});
    playerListEl.innerHTML = '';

    players.forEach(([email, pos], idx) => {
      const colorClass = TOKEN_COLORS[idx % TOKEN_COLORS.length];
      const isYou = (email === userEmail);
      const isTurn = (gameState.currentTurn === email);

      // Add token to board cell
      const holder = document.getElementById(`tokens-${pos}`);
      if (holder) {
        const tokenEl = document.createElement('div');
        tokenEl.className = `player-token ${colorClass}`;
        tokenEl.title = `${email} (Cell ${pos})`;
        holder.appendChild(tokenEl);
      }

      // Add to sidebar scoreboard
      const row = document.createElement('div');
      row.style.cssText = `
        display: flex; justify-content: space-between; align-items: center;
        padding: 0.5rem 0.65rem; border-radius: 8px;
        background: ${isTurn ? 'rgba(16, 185, 129, 0.12)' : 'rgba(255, 255, 255, 0.03)'};
        border: 1px solid ${isTurn ? 'rgba(16, 185, 129, 0.4)' : 'transparent'};
      `;
      row.innerHTML = `
        <div style="display: flex; align-items: center; gap: 0.5rem;">
          <div class="player-token ${colorClass}" style="position: relative; animation: none;"></div>
          <div style="font-weight: 600; font-size: 0.85rem; color: ${isYou ? '#34d399' : '#ffffff'};">
            ${email} ${isYou ? '<span style="font-size: 0.75rem; color: var(--text-muted);">(You)</span>' : ''}
          </div>
        </div>
        <div style="font-family: var(--font-mono); font-weight: 700; font-size: 0.9rem; color: #fbbf24;">
          Cell ${pos}
        </div>
      `;
      playerListEl.appendChild(row);
    });
  }

  function updateTurnUI() {
    const isYourTurn = (gameState.currentTurn === userEmail);

    if (gameState.isOver) {
      turnBanner.className = 'turn-banner opponent-turn';
      turnBanner.innerHTML = '🏁 Match Finished';
      diceBtn.disabled = true;
      return;
    }

    if (isYourTurn) {
      turnBanner.className = 'turn-banner your-turn';
      turnBanner.innerHTML = '👉 YOUR TURN! Roll the dice';
      diceBtn.disabled = false;
    } else {
      turnBanner.className = 'turn-banner opponent-turn';
      const opponentName = gameState.currentTurn || 'Opponent';
      turnBanner.innerHTML = `⏳ ${opponentName} is rolling...`;
      diceBtn.disabled = true;
    }
  }

  // Handle Dice Roll
  diceBtn.onclick = async () => {
    if (diceBtn.disabled) return;
    diceBtn.disabled = true;

    // Trigger visual roll animation
    diceBox.classList.add('rolling');
    diceBox.textContent = '🎲';

    try {
      const res = await fetch(`/games/snl/roll/${sessionId}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });
      const data = await res.json();

      setTimeout(() => {
        diceBox.classList.remove('rolling');
        if (data.roll) {
          diceBox.textContent = DICE_FACES[data.roll - 1] || data.roll;
        }
      }, 500);

      if (!res.ok) {
        window.showToast(data.error || 'Failed to roll dice', 'error');
        updateTurnUI();
      }
    } catch (e) {
      diceBox.classList.remove('rolling');
      window.showToast('Connection error rolling dice', 'error');
      updateTurnUI();
    }
  };

  // Fetch initial game state
  try {
    const res = await fetch(`/games/snl/state/${sessionId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json();
      gameState.positions = data.positions || {};
      gameState.currentTurn = data.currentTurn;
      gameState.isOver = data.isOver || false;
      updatePlayerTokens();
      updateTurnUI();
    }
  } catch (e) {
    console.warn('Failed to fetch game state:', e);
  }

  // Connect SSE Stream
  function initSSE() {
    activeEventSource = new EventSource(`/ws/${sessionId}?token=${token}`);

    activeEventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);

        if (data.type === 'move') {
          const { player, roll, from, to, won, nextPlayer } = data;
          gameState.positions[player] = to;
          gameState.currentTurn = nextPlayer;

          if (player !== userEmail && roll) {
            diceBox.classList.add('rolling');
            setTimeout(() => {
              diceBox.classList.remove('rolling');
              diceBox.textContent = DICE_FACES[roll - 1] || roll;
            }, 500);
          }

          let extra = '';
          if (boardMeta.snakes && boardMeta.snakes[from + roll] === to) {
            extra = ` — 🐍 Slid down a snake to ${to}!`;
            window.showToast(`${player} slid down a snake!`, 'error');
          } else if (boardMeta.ladders && boardMeta.ladders[from + roll] === to) {
            extra = ` — 🪜 Climbed a ladder to ${to}!`;
            window.showToast(`${player} climbed a ladder!`, 'success');
          }

          appendFeed(`🎲 ${player} rolled ${roll} (Cell ${from} → ${to})${extra}`);

          updatePlayerTokens();
          updateTurnUI();

          if (won || to >= 100) {
            handleGameOver(player);
          }
        } else if (data.type === 'end') {
          appendFeed('Match concluded.', 'win');
          gameState.isOver = true;
          updateTurnUI();
        }
      } catch (err) {
        console.warn('Error parsing SSE event:', err);
      }
    };

    activeEventSource.onerror = (err) => {
      console.warn('SSE connection warning:', err);
    };
  }

  function handleGameOver(winner) {
    gameState.isOver = true;
    updateTurnUI();

    const isWinner = (winner === userEmail);
    const modal = document.getElementById('gameOverModal');
    const modalIcon = document.getElementById('modalIcon');
    const modalTitle = document.getElementById('modalTitle');
    const modalDesc = document.getElementById('modalDesc');

    if (isWinner) {
      modalIcon.textContent = '🎉';
      modalTitle.textContent = 'MATCH WON!';
      modalTitle.style.color = '#34d399';
      modalDesc.textContent = 'Superb strategy! Your wager winnings have been deposited into your Token Wallet.';
      window.showToast('Congratulations! You won the game!', 'success');
    } else {
      modalIcon.textContent = '🥈';
      modalTitle.textContent = 'MATCH FINISHED';
      modalTitle.style.color = '#fbbf24';
      modalDesc.textContent = `${winner} reached cell 100 first. Good game!`;
      window.showToast(`Game over. ${winner} won the match.`, 'info');
    }

    modal.classList.remove('hidden');
    window.updateHeaderBalance();
  }

  initSSE();
}

export const GameModule = {
  render(container, sessionId) {
    const token = localStorage.getItem('token');
    const user = localStorage.getItem('user') || 'Player';
    loadGameUI(sessionId, token, user);
  }
};
