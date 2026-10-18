// client/web vanilla/scripts/game.js

let activeEventSource = null;

const DICE_FACES = ['⚀', '⚁', '⚂', '⚃', '⚄', '⚅'];
const TOKEN_COLORS = ['token-0', 'token-1', 'token-2', 'token-3'];

export function setupForfeitLeaveButton(sessionId, token, isOverFn) {
  const btn = document.getElementById('leaveMatchBtn');
  if (!btn) return;
  btn.onclick = () => {
    if (isOverFn && isOverFn()) {
      localStorage.removeItem('sessionId');
      if (activeEventSource) {
        activeEventSource.close();
        activeEventSource = null;
      }
      import('./lobby.js').then(m => window.loadModule(m.LobbyModule));
      return;
    }

    const existing = document.getElementById('forfeitModal');
    if (existing) existing.remove();

    const modal = document.createElement('div');
    modal.id = 'forfeitModal';
    modal.className = 'modal-overlay';
    modal.style.zIndex = '200';
    modal.innerHTML = `
      <div class="forfeit-modal-box">
        <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">⚠️</div>
        <h3 style="font-size: 1.35rem; font-weight: 800; color: #f87171; margin-bottom: 0.5rem;">
          Confirm Forfeit & Exit
        </h3>
        <p style="color: var(--text-secondary); font-size: 0.9rem; line-height: 1.5; margin-bottom: 1.5rem;">
          Leaving an active game means <strong>forfeiting the match</strong>. Your wager will be awarded to your opponent!
        </p>
        <div style="display: flex; gap: 0.75rem; justify-content: center;">
          <button id="cancelForfeitBtn" class="btn btn-secondary" style="flex: 1;">
            Keep Playing
          </button>
          <button id="confirmForfeitBtn" class="btn btn-danger" style="flex: 1; background: #ef4444; color: white;">
            Yes, Forfeit Match
          </button>
        </div>
      </div>
    `;

    modal.querySelector('#cancelForfeitBtn').onclick = () => modal.remove();
    modal.querySelector('#confirmForfeitBtn').onclick = async () => {
      try {
        await fetch('/games/forfeit', {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({ sessionId })
        });
      } catch (e) {
        console.warn('Forfeit request error:', e);
      }
      localStorage.removeItem('sessionId');
      modal.remove();
      if (activeEventSource) {
        activeEventSource.close();
        activeEventSource = null;
      }
      window.showToast('You forfeited the match.', 'info');
      import('./lobby.js').then(m => window.loadModule(m.LobbyModule));
    };

    document.body.appendChild(modal);
  };
}

export async function loadGameUI(sessionId, token, userEmail) {
  const app = document.getElementById('app');
  if (!app) return;

  if (activeEventSource) {
    activeEventSource.close();
    activeEventSource = null;
  }

  // 1. Fetch Session Info to determine Game Type
  let sessionMeta = null;
  try {
    const res = await fetch(`/games/session/${sessionId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (res.ok) {
      sessionMeta = await res.json();
    }
  } catch (e) {
    console.warn('Failed to query session metadata:', e);
  }

  const gameType = sessionMeta ? sessionMeta.gameType : 'SNAKES_AND_LADDERS';

  if (gameType === 'KHASINO') {
    renderKhasinoGame(sessionId, token, userEmail, sessionMeta);
  } else if (gameType === 'CRAZY_EIGHT' || gameType === 'UNO') {
    renderCardGame(sessionId, token, userEmail, sessionMeta, gameType === 'UNO');
  } else if (gameType === 'LUDO') {
    renderLudoGame(sessionId, token, userEmail, sessionMeta);
  } else {
    renderSnakesAndLadders(sessionId, token, userEmail, sessionMeta);
  }
}

/* ==========================================================================
   1. Snakes & Ladders Implementation
   ========================================================================== */
async function renderSnakesAndLadders(sessionId, token, userEmail, sessionMeta) {
  const app = document.getElementById('app');

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
    let leftToRight = false;

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
    document.querySelectorAll('.tokens-holder').forEach(el => el.innerHTML = '');

    const players = Object.entries(gameState.positions || {});
    playerListEl.innerHTML = '';

    players.forEach(([email, pos], idx) => {
      const colorClass = TOKEN_COLORS[idx % TOKEN_COLORS.length];
      const isYou = (email === userEmail);
      const isTurn = (gameState.currentTurn === email);

      const holder = document.getElementById(`tokens-${pos}`);
      if (holder) {
        const tokenEl = document.createElement('div');
        tokenEl.className = `player-token ${colorClass}`;
        tokenEl.title = `${email} (Cell ${pos})`;
        holder.appendChild(tokenEl);
      }

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

  diceBtn.onclick = async () => {
    if (diceBtn.disabled) return;
    diceBtn.disabled = true;

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
        } else if (data.type === 'forfeit') {
          const isYou = (data.player === userEmail);
          appendFeed(`⚠️ ${data.player} forfeited the match.`);
          if (isYou) {
            window.showToast('You forfeited the match.', 'error');
          } else {
            window.showToast(`Opponent ${data.player} forfeited! Victory awarded to you!`, 'success');
          }
          handleGameOver(data.winner || (isYou ? 'Opponent' : userEmail));
        } else if (data.type === 'end') {
          appendFeed('Match concluded.', 'win');
          gameState.isOver = true;
          updateTurnUI();
        }
      } catch (err) {
        console.warn('Error parsing SSE event:', err);
      }
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
      modalDesc.textContent = `${winner} won the match. Good game!`;
      window.showToast(`Game over. ${winner} won the match.`, 'info');
    }

    modal.classList.remove('hidden');
    window.updateHeaderBalance();
  }

  setupForfeitLeaveButton(sessionId, token, () => gameState.isOver);
  initSSE();
}

/* ==========================================================================
   2. Card Games (Crazy Eights & Uno) Implementation
   ========================================================================== */
async function renderCardGame(sessionId, token, userEmail, sessionMeta, isUno) {
  const app = document.getElementById('app');
  const gameTitle = isUno ? 'Uno Arena' : 'Crazy Eights';
  const gameIcon = isUno ? '🌈' : '🎴';

  app.innerHTML = `
    <div>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
        <div>
          <h2 style="font-size: 1.5rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
            <span>${gameIcon}</span> ${gameTitle}
          </h2>
          <div style="font-size: 0.85rem; color: var(--text-secondary); font-family: var(--font-mono);">
            Match ID: <span style="color: var(--accent-cyan);">${sessionId.substring(0, 8)}...</span> •
            Wager: <span style="color: #fbbf24; font-weight: 700;">${sessionMeta ? sessionMeta.wager : 50} 🪙</span>
          </div>
        </div>
        <button id="leaveMatchBtn" class="btn btn-secondary" style="font-size: 0.85rem; padding: 0.45rem 0.85rem;">
          ← Exit to Lobby
        </button>
      </div>

      <div class="game-layout">
        <!-- Main Card Table Area -->
        <div class="card-table">
          <!-- Opponents Row -->
          <div id="opponentsRow" class="opponents-row"></div>

          <!-- Table Center: Discard Pile & Draw Deck -->
          <div class="table-center">
            <!-- Discard Pile Slot -->
            <div class="deck-slot">
              <span class="deck-label">Discard Pile</span>
              <div id="discardTopContainer" style="display: flex; align-items: center; justify-content: center; min-width: 80px; min-height: 110px;">
                <!-- Current top card rendered here -->
              </div>
              <div id="activeIndicator" style="margin-top: 0.25rem; font-size: 0.85rem; text-align: center;"></div>
            </div>

            <!-- Draw Deck Slot -->
            <div class="deck-slot">
              <span class="deck-label">Draw Pile</span>
              <div id="drawDeckBtn" class="card-unit ${isUno ? 'uno-back' : 'c8-back'}" title="Click to draw a card">
                ${isUno ? 'UNO' : '🂠'}
              </div>
              <span id="drawDeckCountText" style="font-size: 0.75rem; color: var(--text-muted); font-family: var(--font-mono);">
                0 cards
              </span>
            </div>
          </div>

          <!-- Player's Hand Container -->
          <div class="player-hand-container">
            <div style="display: flex; justify-content: space-between; align-items: center; padding: 0 0.5rem;">
              <div style="font-weight: 700; font-size: 0.95rem; color: #ffffff; display: flex; align-items: center; gap: 0.5rem;">
                <span>👤 Your Hand</span>
                <span id="handCountBadge" class="opponent-card-count">0</span>
              </div>

              <div style="display: flex; gap: 0.5rem;">
                <button id="drawActionBtn" class="btn btn-secondary" style="padding: 0.35rem 0.75rem; font-size: 0.8rem;">
                  ➕ Draw Card
                </button>
                <button id="passActionBtn" class="btn btn-secondary" style="padding: 0.35rem 0.75rem; font-size: 0.8rem;">
                  ⏭ Pass Turn
                </button>
              </div>
            </div>

            <!-- Fan of player's cards -->
            <div id="playerHandFan" class="cards-fan"></div>
          </div>
        </div>

        <!-- Sidebar -->
        <div class="game-sidebar">
          <!-- Turn Banner -->
          <div id="turnBanner" class="turn-banner opponent-turn">
            Connecting match...
          </div>

          <!-- Quick Game Rules Card -->
          <div style="background: var(--surface-glass); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 1rem;">
            <div style="font-size: 0.8rem; font-weight: 700; text-transform: uppercase; color: var(--text-secondary); margin-bottom: 0.5rem;">
              Game Rules
            </div>
            <div style="font-size: 0.82rem; color: var(--text-muted); line-height: 1.45;">
              ${isUno ?
                '• Match by <strong>Color</strong> or <strong>Symbol</strong>.<br>• <strong>★ Wild</strong> & <strong>+4</strong> can change colors.<br>• <strong>⊘ Skip</strong> & <strong>⇄ Reverse</strong> disrupt turns.<br>• Empty your hand to win the prize pool!' :
                '• Match by <strong>Suit</strong> or <strong>Rank</strong>.<br>• <strong>8s are Wild!</strong> Play an 8 anytime to call a new suit.<br>• First player to discard all cards wins!'
              }
            </div>
          </div>

          <!-- Live Match Feed -->
          <div style="background: var(--surface-glass); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 1rem;">
            <div style="font-size: 0.8rem; font-weight: 700; text-transform: uppercase; color: var(--text-secondary); margin-bottom: 0.5rem;">
              Live Match Feed
            </div>
            <div id="matchFeed" class="match-feed">
              <div class="feed-item">${gameTitle} loaded. Ready to play!</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Suit Picker Modal (For Crazy Eights) -->
      <div id="suitPickerModal" class="modal-overlay hidden">
        <div class="modal-content" style="max-width: 380px;">
          <h3 style="font-size: 1.35rem; font-weight: 800; margin-bottom: 0.5rem;">Declare New Suit</h3>
          <p style="color: var(--text-secondary); font-size: 0.85rem;">
            You played a Wild 8! Choose the suit players must match next:
          </p>
          <div class="picker-grid">
            <button class="picker-btn" data-suit="HEARTS" style="background: rgba(239, 68, 68, 0.15); color: #ef4444; border-color: rgba(239, 68, 68, 0.4);">
              <span>♥</span> Hearts
            </button>
            <button class="picker-btn" data-suit="DIAMONDS" style="background: rgba(239, 68, 68, 0.15); color: #ef4444; border-color: rgba(239, 68, 68, 0.4);">
              <span>♦</span> Diamonds
            </button>
            <button class="picker-btn" data-suit="CLUBS" style="background: rgba(148, 163, 184, 0.15); color: #f8fafc; border-color: rgba(148, 163, 184, 0.4);">
              <span>♣</span> Clubs
            </button>
            <button class="picker-btn" data-suit="SPADES" style="background: rgba(148, 163, 184, 0.15); color: #f8fafc; border-color: rgba(148, 163, 184, 0.4);">
              <span>♠</span> Spades
            </button>
          </div>
        </div>
      </div>

      <!-- Color Picker Modal (For Uno Wilds) -->
      <div id="colorPickerModal" class="modal-overlay hidden">
        <div class="modal-content" style="max-width: 380px;">
          <h3 style="font-size: 1.35rem; font-weight: 800; margin-bottom: 0.5rem;">Choose Wild Color</h3>
          <p style="color: var(--text-secondary); font-size: 0.85rem;">
            You played a Wild Card! Pick the color to continue play:
          </p>
          <div class="picker-grid">
            <button class="picker-btn" data-color="RED" style="background: #ef4444; color: #ffffff;">
              <span>🔴</span> RED
            </button>
            <button class="picker-btn" data-color="BLUE" style="background: #2563eb; color: #ffffff;">
              <span>🔵</span> BLUE
            </button>
            <button class="picker-btn" data-color="GREEN" style="background: #10b981; color: #ffffff;">
              <span>🟢</span> GREEN
            </button>
            <button class="picker-btn" data-color="YELLOW" style="background: #f59e0b; color: #ffffff;">
              <span>🟡</span> YELLOW
            </button>
          </div>
        </div>
      </div>

      <!-- Victory / Game Over Modal -->
      <div id="gameOverModal" class="modal-overlay hidden">
        <div class="modal-content">
          <div id="modalIcon" style="font-size: 3.5rem; margin-bottom: 0.5rem;">🏆</div>
          <h2 id="modalTitle" style="font-size: 1.8rem; font-weight: 800; margin-bottom: 0.5rem;">VICTORY!</h2>
          <p id="modalDesc" style="color: var(--text-secondary); margin-bottom: 1.5rem; font-size: 1rem;">
            Congratulations! You emptied your hand first!
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

  const opponentsRow = document.getElementById('opponentsRow');
  const discardTopContainer = document.getElementById('discardTopContainer');
  const activeIndicator = document.getElementById('activeIndicator');
  const drawDeckBtn = document.getElementById('drawDeckBtn');
  const drawDeckCountText = document.getElementById('drawDeckCountText');
  const playerHandFan = document.getElementById('playerHandFan');
  const handCountBadge = document.getElementById('handCountBadge');
  const turnBanner = document.getElementById('turnBanner');
  const feedEl = document.getElementById('matchFeed');
  const drawActionBtn = document.getElementById('drawActionBtn');
  const passActionBtn = document.getElementById('passActionBtn');

  const suitPickerModal = document.getElementById('suitPickerModal');
  const colorPickerModal = document.getElementById('colorPickerModal');

  let currentGameState = null;
  let pendingWildCardId = null;

  function appendFeed(text, type = '') {
    const item = document.createElement('div');
    item.className = `feed-item ${type}`;
    item.textContent = text;
    feedEl.appendChild(item);
    feedEl.scrollTop = feedEl.scrollHeight;
  }

  const SUIT_SYMBOLS = {
    HEARTS: '♥',
    DIAMONDS: '♦',
    CLUBS: '♣',
    SPADES: '♠'
  };

  const UNO_ACTION_SYMBOLS = {
    SKIP: '⊘',
    REVERSE: '⇄',
    DRAW_TWO: '+2',
    WILD: '★',
    WILD_DRAW_FOUR: '+4'
  };

  // --- Card UI Renderers ---
  function createC8CardElement(card, isPlayable, onClick) {
    const el = document.createElement('div');
    const isRed = (card.suit === 'HEARTS' || card.suit === 'DIAMONDS');
    const sym = SUIT_SYMBOLS[card.suit] || card.symbol || '';
    const disp = card.display || (card.rank === 'EIGHT' ? '8' : card.rank);
    el.className = `card-unit c8-card ${isRed ? 'red' : 'black'} ${isPlayable ? 'playable' : ''}`;
    el.innerHTML = `
      <div class="corner-top">
        <span>${disp}</span>
        <span>${sym}</span>
      </div>
      <div class="center-symbol">${card.isEight ? '★' : sym}</div>
      <div class="corner-bottom">
        <span>${disp}</span>
        <span>${sym}</span>
      </div>
    `;
    if (isPlayable && onClick) el.onclick = onClick;
    return el;
  }

  function createUnoCardElement(card, isPlayable, onClick) {
    const el = document.createElement('div');
    const sym = (card.type && UNO_ACTION_SYMBOLS[card.type]) ? UNO_ACTION_SYMBOLS[card.type] : (card.symbol || (card.number !== undefined ? card.number : ''));
    el.className = `card-unit uno-card uno-${card.color} ${isPlayable ? 'playable' : ''}`;
    el.innerHTML = `
      <div class="uno-oval">
        <div class="uno-symbol">${sym}</div>
      </div>
    `;
    if (isPlayable && onClick) el.onclick = onClick;
    return el;
  }

  function updateTableUI(state) {
    if (!state) return;
    currentGameState = state;

    const isYourTurn = (state.currentTurn === userEmail);

    // 1. Turn Banner
    if (state.gameOver) {
      turnBanner.className = 'turn-banner opponent-turn';
      turnBanner.innerHTML = `🏁 Game Over! Winner: <strong>${state.winnerId}</strong>`;
    } else if (isYourTurn) {
      turnBanner.className = 'turn-banner your-turn';
      turnBanner.innerHTML = '👉 YOUR TURN! Play a card or draw';
    } else {
      turnBanner.className = 'turn-banner opponent-turn';
      turnBanner.innerHTML = `⏳ <strong>${state.currentTurn}</strong> is thinking...`;
    }

    // 2. Action buttons
    drawActionBtn.disabled = !isYourTurn || state.gameOver;
    passActionBtn.disabled = !isYourTurn || state.gameOver;

    // 3. Opponents Row
    opponentsRow.innerHTML = '';
    const cardCounts = state.cardCounts || {};
    Object.entries(cardCounts).forEach(([email, count]) => {
      if (email === userEmail) return; // don't show self in opponents row
      const isOppTurn = (state.currentTurn === email);
      const oppCard = document.createElement('div');
      oppCard.className = `opponent-card ${isOppTurn ? 'active-turn' : ''}`;
      oppCard.innerHTML = `
        <div class="user-avatar" style="width: 32px; height: 32px; font-size: 0.9rem;">
          ${email.substring(0, 1).toUpperCase()}
        </div>
        <div>
          <div style="font-weight: 700; font-size: 0.85rem; color: #ffffff;">
            ${email}
          </div>
          <div style="font-size: 0.75rem; color: var(--text-muted);">
            ${isOppTurn ? '⏳ Playing move...' : 'Waiting'}
          </div>
        </div>
        <span class="opponent-card-count" title="${count} cards in hand">
          🂠 ${count}
        </span>
      `;
      opponentsRow.appendChild(oppCard);
    });

    // 4. Discard Pile Top
    discardTopContainer.innerHTML = '';
    if (state.discardTop) {
      const topCardEl = isUno ?
        createUnoCardElement(state.discardTop, false) :
        createC8CardElement(state.discardTop, false);
      discardTopContainer.appendChild(topCardEl);
    } else {
      discardTopContainer.innerHTML = '<span style="color: var(--text-muted); font-size: 0.8rem;">Empty</span>';
    }

    // 5. Active Declaration Indicator
    if (isUno) {
      const dirText = state.direction === 1 ? '↻ Clockwise' : '↺ Reversed';
      activeIndicator.innerHTML = `
        <div style="display: flex; flex-direction: column; align-items: center; gap: 0.25rem;">
          <span class="active-status-pill" style="border-color: ${state.activeHex || '#ffffff'}; color: ${state.activeHex || '#ffffff'};">
            ● ${state.activeColor}
          </span>
          <span style="font-size: 0.72rem; color: var(--text-muted);">${dirText}</span>
        </div>
      `;
    } else {
      const suitColors = {
        HEARTS: '#ef4444',
        DIAMONDS: '#ef4444',
        CLUBS: '#94a3b8',
        SPADES: '#94a3b8'
      };
      const suitColor = suitColors[state.activeSuit] || '#34d399';
      activeIndicator.innerHTML = `
        <div style="font-size: 0.8rem; font-weight: 700;">
          Active Suit: <span style="color: ${suitColor};">${state.activeSuit || 'Any'}</span>
        </div>
      `;
    }

    // 6. Draw Deck
    drawDeckCountText.textContent = `${state.drawDeckCount || 0} cards`;

    // 7. Player's Hand
    playerHandFan.innerHTML = '';
    const myHand = (state.hands && state.hands[userEmail]) ? state.hands[userEmail] : [];
    handCountBadge.textContent = myHand.length;

    myHand.forEach(card => {
      // Check playability
      let isPlayable = false;
      if (isYourTurn && !state.gameOver) {
        if (isUno) {
          isPlayable = card.isWild || (card.color === state.activeColor) || (card.symbol === state.activeSymbol);
        } else {
          isPlayable = card.isEight || (card.suit === state.activeSuit) || (card.rank === state.activeRank);
        }
      }

      const cardEl = isUno ?
        createUnoCardElement(card, isPlayable, () => onCardClicked(card)) :
        createC8CardElement(card, isPlayable, () => onCardClicked(card));

      playerHandFan.appendChild(cardEl);
    });
  }

  // Handle Card Click
  function onCardClicked(card) {
    if (!currentGameState || currentGameState.currentTurn !== userEmail) {
      window.showToast("It's not your turn!", 'info');
      return;
    }

    if (isUno) {
      if (card.isWild) {
        pendingWildCardId = card.id;
        colorPickerModal.classList.remove('hidden');
        return;
      }
      submitCardPlay(card.id, null);
    } else {
      if (card.isEight) {
        pendingWildCardId = card.id;
        suitPickerModal.classList.remove('hidden');
        return;
      }
      submitCardPlay(card.id, null);
    }
  }

  // Submit Play Card Request
  async function submitCardPlay(cardId, colorOrSuit) {
    try {
      const payload = { cardId };
      if (colorOrSuit) {
        if (isUno) payload.color = colorOrSuit;
        else payload.suit = colorOrSuit;
      }

      const res = await fetch(`/games/card/play?sessionId=${sessionId}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      const data = await res.json();
      if (!res.ok) {
        window.showToast(data.error || 'Invalid move', 'error');
      }
    } catch (e) {
      window.showToast('Network error playing card', 'error');
    }
  }

  // Draw Card
  async function performDraw() {
    if (!currentGameState || currentGameState.currentTurn !== userEmail) {
      window.showToast("Wait for your turn to draw!", 'info');
      return;
    }
    try {
      const res = await fetch(`/games/card/draw?sessionId=${sessionId}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });
      const data = await res.json();
      if (data.success) {
        window.showToast('Drew a card from deck!', 'info');
      } else {
        window.showToast(data.error || 'Cannot draw card', 'error');
      }
    } catch (e) {
      window.showToast('Network error drawing card', 'error');
    }
  }

  // Pass Turn
  async function performPass() {
    if (!currentGameState || currentGameState.currentTurn !== userEmail) {
      window.showToast("Wait for your turn to pass!", 'info');
      return;
    }
    try {
      const res = await fetch(`/games/card/pass?sessionId=${sessionId}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });
      const data = await res.json();
      if (data.success) {
        window.showToast('Turn passed.', 'info');
      } else {
        window.showToast(data.error || 'Cannot pass right now', 'error');
      }
    } catch (e) {
      window.showToast('Network error passing turn', 'error');
    }
  }

  drawDeckBtn.onclick = performDraw;
  drawActionBtn.onclick = performDraw;
  passActionBtn.onclick = performPass;

  // Wild Suit Picker buttons
  document.querySelectorAll('#suitPickerModal .picker-btn').forEach(btn => {
    btn.onclick = () => {
      const suit = btn.getAttribute('data-suit');
      suitPickerModal.classList.add('hidden');
      if (pendingWildCardId) {
        submitCardPlay(pendingWildCardId, suit);
        pendingWildCardId = null;
      }
    };
  });

  // Wild Color Picker buttons
  document.querySelectorAll('#colorPickerModal .picker-btn').forEach(btn => {
    btn.onclick = () => {
      const color = btn.getAttribute('data-color');
      colorPickerModal.classList.add('hidden');
      if (pendingWildCardId) {
        submitCardPlay(pendingWildCardId, color);
        pendingWildCardId = null;
      }
    };
  });

  // Fetch initial card state
  try {
    const res = await fetch(`/games/card/state?sessionId=${sessionId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json();
      if (data.state) {
        updateTableUI(data.state);
      }
    }
  } catch (e) {
    console.warn('Failed to fetch initial card state:', e);
  }

  // Connect SSE for Real-Time Card Events
  function initCardSSE() {
    activeEventSource = new EventSource(`/ws/${sessionId}?token=${token}`);

    activeEventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);

        if (data.type === 'card_move') {
          if (data.state) {
            updateTableUI(data.state);
            if (data.state.lastAction) {
              appendFeed(`🎴 ${data.state.lastAction}`);
            }
          }

          if (data.won || (data.state && data.state.gameOver)) {
            const winner = data.player || (data.state && data.state.winnerId);
            handleGameOver(winner);
          }
        } else if (data.type === 'forfeit') {
          const isYou = (data.player === userEmail);
          appendFeed(`⚠️ ${data.player} forfeited the match.`);
          if (isYou) {
            window.showToast('You forfeited the match.', 'error');
          } else {
            window.showToast(`Opponent ${data.player} forfeited! Victory awarded to you!`, 'success');
          }
          handleGameOver(data.winner || (isYou ? 'Opponent' : userEmail));
        } else if (data.type === 'start') {
          if (data.state) {
            updateTableUI(data.state);
          }
          appendFeed('Game started! Cards dealt.');
        } else if (data.type === 'end') {
          appendFeed('Match concluded.', 'win');
          if (data.winner) handleGameOver(data.winner);
        }
      } catch (err) {
        console.warn('Error parsing card SSE event:', err);
      }
    };

    activeEventSource.onerror = (err) => {
      console.warn('SSE warning on card game:', err);
    };
  }

  function handleGameOver(winner) {
    if (currentGameState) currentGameState.gameOver = true;
    updateTableUI(currentGameState);

    const isWinner = (winner === userEmail);
    const modal = document.getElementById('gameOverModal');
    const modalIcon = document.getElementById('modalIcon');
    const modalTitle = document.getElementById('modalTitle');
    const modalDesc = document.getElementById('modalDesc');

    if (isWinner) {
      modalIcon.textContent = '🎉';
      modalTitle.textContent = 'MATCH WON!';
      modalTitle.style.color = '#34d399';
      modalDesc.textContent = `Superb strategy! Your winnings have been deposited into your Token Wallet.`;
      window.showToast('Congratulations! You won the game!', 'success');
    } else {
      modalIcon.textContent = '🥈';
      modalTitle.textContent = 'MATCH FINISHED';
      modalTitle.style.color = '#fbbf24';
      modalDesc.textContent = `${winner} won the match. Better luck next hand!`;
      window.showToast(`Game over. ${winner} won the match.`, 'info');
    }

    modal.classList.remove('hidden');
    window.updateHeaderBalance();
  }

  setupForfeitLeaveButton(sessionId, token, () => currentGameState && currentGameState.gameOver);
  initCardSSE();
}

/* ==========================================================================
   3. South African Khasino (Kasi Kasino) Implementation
   ========================================================================== */
async function renderKhasinoGame(sessionId, token, userEmail, sessionMeta) {
  const app = document.getElementById('app');

  let currentGameState = null;
  let legalActionsList = [];
  let stagedPlayedCardId = null;
  let stagedFloorCardIds = new Set();
  let stagedTargetBuildId = null;
  let stagedStolenCardId = null;
  let stagedStolenOpponentId = null;

  const SUIT_SYMBOLS = {
    HEARTS: '♥',
    DIAMONDS: '♦',
    CLUBS: '♣',
    SPADES: '♠'
  };

  const RANK_DISPLAYS = {
    ACE: 'A',
    TWO: '2',
    THREE: '3',
    FOUR: '4',
    FIVE: '5',
    SIX: '6',
    SEVEN: '7',
    EIGHT: '8',
    NINE: '9',
    TEN: '10'
  };

  const modeDisplayName = {
    TWO_PLAYER: '2 Players (1v1, Two 10-Card Deals, 11-Pt)',
    THREE_PLAYER: '3 Players (13 Cards + 1 Floor, 7-Pt)',
    FOUR_PLAYER_SINGLES: '4 Players Singles (Solo 4-Way, 7-Pt)',
    FOUR_PLAYER_PARTNERSHIPS: '4 Players 2v2 Partnerships (11-Pt)'
  };

  app.innerHTML = `
    <div>
      <!-- Header -->
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; gap: 0.75rem;">
        <div>
          <h2 style="font-size: 1.5rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem; color: #34d399;">
            <span>🇿🇦</span> South African Khasino (Kasi Kasino)
          </h2>
          <div style="font-size: 0.85rem; color: var(--text-secondary); font-family: var(--font-mono); display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;">
            <span>Match: <strong style="color: var(--accent-cyan);">${sessionId.substring(0, 8)}...</strong></span> •
            <span id="khasinoModeDisplay" style="color: #60a5fa; font-weight: 700;">Loading setup...</span> •
            <span>Wager: <strong style="color: #fbbf24;">${sessionMeta ? sessionMeta.wager : 50} 🪙</strong></span>
          </div>
        </div>

        <div style="display: flex; gap: 0.75rem;">
          <button id="khasinoRulesBtn" class="btn btn-secondary" style="font-size: 0.85rem; padding: 0.45rem 0.85rem; border-color: rgba(16, 185, 129, 0.4); color: #34d399;">
            📖 Rules Guide
          </button>
          <button id="leaveMatchBtn" class="btn btn-secondary" style="font-size: 0.85rem; padding: 0.45rem 0.85rem;">
            ← Exit to Lobby
          </button>
        </div>
      </div>

      <!-- Main Game Grid Layout -->
      <div class="game-layout">
        <!-- Table Area -->
        <div class="khasino-table" id="khasinoTable">
          <!-- Opponents Bar -->
          <div id="khasinoOpponentsRow" class="khasino-opponents-row"></div>

          <!-- Middle Felt: Loose Cards and Active Builds -->
          <div class="khasino-middle-felt" id="khasinoMiddleFelt">
            <!-- Active Builds Sub-Section -->
            <div>
              <div class="khasino-section-header">
                <span>🎯 Active Builds on Table (<span id="buildCount">0</span>)</span>
                <span style="font-size: 0.7rem; color: var(--text-muted); font-weight: normal;">Click a build to target for chow, augment, or changing value</span>
              </div>
              <div id="khasinoBuildsGrid" class="khasino-builds-grid" style="margin-top: 0.5rem;">
                <div style="color: var(--text-muted); font-size: 0.85rem; padding: 1rem; text-align: center; width: 100%;">
                  No active builds on the table.
                </div>
              </div>
            </div>

            <div style="height: 1px; background: rgba(16, 185, 129, 0.2); margin: 0.25rem 0;"></div>

            <!-- Loose Cards Sub-Section -->
            <div>
              <div class="khasino-section-header">
                <span>🃏 Loose Floor Cards (<span id="floorCount">0</span>)</span>
                <span id="floorSelectionSummary" style="font-size: 0.7rem; color: #fbbf24; font-weight: 700;">0 selected (Sum: 0)</span>
              </div>
              <div id="khasinoFloorGrid" class="khasino-floor-grid" style="margin-top: 0.5rem;">
                <div style="color: var(--text-muted); font-size: 0.85rem; padding: 1rem; text-align: center; width: 100%;">
                  Floor is currently empty.
                </div>
              </div>
            </div>
          </div>

          <!-- Interactive Action Staging Bar -->
          <div class="khasino-stage-bar" id="khasinoStageBar">
            <div class="khasino-stage-info">
              <div class="khasino-stage-pill">
                <span style="color: var(--text-muted);">Played Card:</span>
                <strong id="stagePlayedCard" style="color: #34d399;">None selected</strong>
              </div>
              <div class="khasino-stage-pill">
                <span style="color: var(--text-muted);">Target Cards:</span>
                <strong id="stageTargetFloor" style="color: #fbbf24;">None</strong>
              </div>
              <div class="khasino-stage-pill">
                <span style="color: var(--text-muted);">Target Build:</span>
                <strong id="stageTargetBuild" style="color: #60a5fa;">None</strong>
              </div>
              <div id="stageStolenCardPill" class="khasino-stage-pill hidden">
                <span style="color: var(--text-muted);">Stolen Card:</span>
                <strong id="stageStolenCard" style="color: #f59e0b;">None</strong>
              </div>
            </div>

            <div class="khasino-stage-actions">
              <button id="actionChowBtn" class="btn-khasino-chow" disabled title="Capture matching cards or sum combinations">
                ⚡ Chow (Capture)
              </button>
              <button id="actionBuildBtn" class="btn-khasino-build" disabled title="Build floor cards + played card into a declared capture value">
                🏗️ Simple Build
              </button>
              <button id="actionAugmentBtn" class="btn-khasino-augment" disabled title="Add an independent group to an existing owned build">
                ➕ Augment Build
              </button>
              <button id="actionChangeBtn" class="btn-khasino-change" disabled title="Increase an opponent's build value and seize ownership">
                🔄 Change Value
              </button>
              <button id="actionDriftBtn" class="btn-khasino-drift" disabled title="Place played card onto the floor without capturing">
                🍃 Drift (Trail)
              </button>
              <button id="actionClearBtn" class="btn btn-secondary" style="font-size: 0.8rem; padding: 0.45rem 0.75rem;">
                ✕ Clear
              </button>
            </div>
          </div>

          <!-- Player Hand Container -->
          <div style="background: rgba(8, 16, 12, 0.8); border: 1px solid rgba(16, 185, 129, 0.25); border-radius: var(--radius-md); padding: 0.85rem 1rem;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem; flex-wrap: wrap; gap: 0.5rem;">
              <div style="display: flex; align-items: center; gap: 0.5rem;">
                <span style="font-weight: 800; font-size: 0.95rem; color: #ffffff;">Your Hand</span>
                <span id="myTeamBadge" class="hidden" style="background: rgba(139, 92, 246, 0.25); border: 1px solid rgba(139, 92, 246, 0.5); color: #c4b5fd; font-size: 0.72rem; padding: 1px 7px; border-radius: 999px; font-weight: 700;">Team 1</span>
                <span id="myHandCountBadge" style="font-size: 0.75rem; color: var(--text-muted); font-family: var(--font-mono);">(0 cards)</span>
              </div>
              <div style="font-size: 0.8rem; color: var(--text-secondary); display: flex; gap: 1rem; flex-wrap: wrap;">
                <span>Captured: <strong id="myCapturedCount" style="color: #34d399;">0</strong> cards</span>
                <span>Spades: <strong id="mySpadesCount" style="color: #60a5fa;">0</strong> ♠</span>
                <span>Sweeps: <strong id="mySweepCount" style="color: #fbbf24;">0</strong> ★</span>
              </div>
            </div>

            <div id="khasinoPlayerHand" class="cards-fan" style="min-height: 120px;"></div>
          </div>
        </div>

        <!-- Sidebar / Match Controls / Legal Actions Helper / Live Feed -->
        <div class="game-sidebar">
          <!-- Turn Banner -->
          <div id="turnBanner" class="turn-banner opponent-turn">
            Connecting match...
          </div>

          <!-- Smart Legal Actions Helper -->
          <div style="background: var(--surface-glass); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 1rem;">
            <div style="font-size: 0.8rem; font-weight: 800; text-transform: uppercase; color: #34d399; margin-bottom: 0.5rem; display: flex; justify-content: space-between; align-items: center;">
              <span>💡 Recommended Legal Moves</span>
              <span id="helperModeText" style="font-size: 0.7rem; color: var(--text-muted); font-weight: normal;">(All available)</span>
            </div>
            <p style="font-size: 0.78rem; color: var(--text-secondary); margin-bottom: 0.75rem;">
              Click any move below to automatically stage and execute it:
            </p>
            <div id="khasinoLegalActions" class="khasino-helper-container">
              <div style="color: var(--text-muted); font-size: 0.8rem; font-style: italic;">
                Calculating legal moves...
              </div>
            </div>
          </div>

          <!-- Scores & Card Majority Tracker -->
          <div style="background: var(--surface-glass); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 1rem;">
            <div style="font-size: 0.8rem; font-weight: 800; text-transform: uppercase; color: var(--text-secondary); margin-bottom: 0.75rem;">
              📊 Match Statistics & Tallies
            </div>
            <div id="khasinoScoresTracker" style="display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.82rem;"></div>
          </div>

          <!-- Live Match Feed -->
          <div style="background: var(--surface-glass); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 1rem;">
            <div style="font-size: 0.8rem; font-weight: 800; text-transform: uppercase; color: var(--text-secondary); margin-bottom: 0.5rem;">
              Live Match Feed
            </div>
            <div id="matchFeed" class="match-feed">
              <div class="feed-item">Khasino table loaded. Dealing cards...</div>
            </div>
          </div>
        </div>
      </div>

      <!-- In-Game Rules Guide Modal -->
      <div id="khasinoRulesModal" class="modal-overlay hidden" style="z-index: 100;">
        <div class="modal-content" style="max-width: 780px; max-height: 85vh; overflow-y: auto; text-align: left; padding: 2rem;">
          <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-glass); padding-bottom: 1rem; margin-bottom: 1.25rem;">
            <h2 style="font-size: 1.4rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem; color: #34d399;">
              <span>🇿🇦</span> South African Khasino (Kasi Kasino) Rules Guide
            </h2>
            <button type="button" id="closeRulesBtn" class="btn btn-secondary" style="font-size: 0.85rem; padding: 0.35rem 0.75rem;">✕ Close</button>
          </div>

          <div style="font-size: 0.9rem; color: var(--text-secondary); line-height: 1.6; display: flex; flex-direction: column; gap: 1.25rem;">
            <div style="background: rgba(16, 185, 129, 0.08); border-left: 3px solid #10b981; padding: 0.85rem 1rem; border-radius: 4px;">
              <strong>Official South African KASA Rules:</strong> 40-card deck (A-10; no picture cards). Anti-clockwise play.
            </div>

            <div>
              <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">1. Deck & Setups</h4>
              <p>• <strong>Deck:</strong> 40 cards across 4 suits (Aces = 1, 2-10). No Jacks, Queens, Kings.<br>
                 • <strong>2-Player:</strong> 10 cards each in Deal 1. A second 10-card deal follows. (11-pt mode).<br>
                 • <strong>3-Player:</strong> 13 cards each, 1 floor card. (7-pt mode).<br>
                 • <strong>4-Player Singles:</strong> 10 cards each, 0 floor cards. (7-pt mode).<br>
                 • <strong>4-Player 2v2 Partnerships:</strong> 10 cards each, Team 1 (Seats 0&2) vs Team 2 (Seats 1&3). (11-pt mode).</p>
            </div>

            <div>
              <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">2. Capturing (Chow)</h4>
              <p>• Match single floor cards of equal value, or combinations of loose cards that sum to your card's value.<br>
                 • Multiple matching groups can be captured simultaneously.<br>
                 • <strong>Sweep (★):</strong> Clearing every single card and build from the board scores +1 Sweep point!</p>
            </div>

            <div>
              <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">3. Simple vs Compound Builds</h4>
              <p>• <strong>Simple Build:</strong> Combine loose cards with 1 card from hand into a declared value (≤ 10). You must hold a capturing card of that value in your hand. Small card sits on top.<br>
                 • <strong>Compound (Augmented) Build:</strong> Adding another independent group summing to the same capture value converts it to a compound build. Compound builds lock their capture value permanently!</p>
            </div>

            <div>
              <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">4. Changing Opponent's Build Value</h4>
              <p>• You may add 1 card from hand to an opponent's simple build to increase its value (up to 10), provided you hold the card matching the new total. Ownership immediately transfers to you!</p>
            </div>

            <div>
              <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">5. Drifting, Stealing & Cupha</h4>
              <p>• <strong>Drift:</strong> Trailing a card to the floor. In 2-player Deal 1, a player who owns an uncaptured build on the floor cannot drift; in Deal 2, drifting is allowed.<br>
                 • <strong>Stealing Captured Cards:</strong> You may steal the top exposed card from an opponent's captured pile to augment an eligible build.<br>
                 • <strong>Cupha (Entrapment):</strong> Players can strategically decline an available capture to set a trap for an opponent.</p>
            </div>

            <div>
              <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">6. Scoring Breakdown</h4>
              <table style="width: 100%; border-collapse: collapse; font-size: 0.85rem; background: var(--surface-glass); border: 1px solid var(--border-glass); border-radius: 6px;">
                <tr style="border-bottom: 1px solid var(--border-glass); color: #34d399; font-weight: 700;">
                  <th style="padding: 4px 8px; text-align: left;">Item</th>
                  <th style="padding: 4px 8px; text-align: center;">Points</th>
                  <th style="padding: 4px 8px; text-align: left;">Rule</th>
                </tr>
                <tr><td style="padding: 4px 8px;">10♦ (Mummy)</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">2</td><td>Decides match tiebreakers!</td></tr>
                <tr><td style="padding: 4px 8px;">2♠ (Spy Two)</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">1</td><td>The little casino spade</td></tr>
                <tr><td style="padding: 4px 8px;">Each Ace</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">1 ea</td><td>4 points total across all aces</td></tr>
                <tr><td style="padding: 4px 8px;">Most Cards</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">2</td><td>11-pt modes only (1-1 on tie)</td></tr>
                <tr><td style="padding: 4px 8px;">Most Spades</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">2</td><td>11-pt modes only (1-1 on tie)</td></tr>
                <tr><td style="padding: 4px 8px;">Each Sweep (★)</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">1 ea</td><td>Awarded on board clears</td></tr>
              </table>
            </div>
          </div>

          <div style="margin-top: 1.5rem; text-align: right;">
            <button type="button" id="closeRulesBottomBtn" class="btn btn-primary" style="padding: 0.5rem 1.5rem;">
              Back to Game
            </button>
          </div>
        </div>
      </div>

      <!-- End of Match Final Scorecard Modal -->
      <div id="khasinoScoreModal" class="modal-overlay hidden" style="z-index: 110;">
        <div class="modal-content" style="max-width: 640px; text-align: center; padding: 2rem;">
          <div id="scoreModalIcon" style="font-size: 3.5rem; margin-bottom: 0.5rem;">🏆</div>
          <h2 id="scoreModalTitle" style="font-size: 1.8rem; font-weight: 800; margin-bottom: 0.25rem;">MATCH FINISHED</h2>
          <p id="scoreModalWinnerDesc" style="color: #34d399; font-weight: 700; margin-bottom: 1.25rem; font-size: 1.05rem;">
            Winner: Determining...
          </p>

          <div style="background: var(--surface-glass); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 1rem; margin-bottom: 1.5rem;">
            <div style="font-size: 0.8rem; font-weight: 800; text-transform: uppercase; color: var(--text-muted); margin-bottom: 0.75rem; text-align: left;">
              Official Khasino Scorecard Breakdown
            </div>
            <div id="scorecardTableContainer" style="overflow-x: auto;"></div>
          </div>

          <button id="scoreModalReturnBtn" class="btn btn-primary" style="width: 100%; font-size: 1.05rem; padding: 0.75rem;">
            Return to Game Lobby
          </button>
        </div>
      </div>
    </div>
  `;

  // Attach nav and modal handlers
  setupForfeitLeaveButton(sessionId, token, () => currentGameState && currentGameState.isOver);

  document.getElementById('scoreModalReturnBtn').onclick = () => {
    if (activeEventSource) activeEventSource.close();
    localStorage.removeItem('sessionId');
    window.checkAuth();
  };

  const rulesModal = document.getElementById('khasinoRulesModal');
  document.getElementById('khasinoRulesBtn').onclick = () => rulesModal.classList.remove('hidden');
  document.getElementById('closeRulesBtn').onclick = () => rulesModal.classList.add('hidden');
  document.getElementById('closeRulesBottomBtn').onclick = () => rulesModal.classList.add('hidden');

  const feedEl = document.getElementById('matchFeed');
  function appendFeed(text, type = '') {
    const item = document.createElement('div');
    item.className = `feed-item ${type}`;
    item.textContent = text;
    feedEl.appendChild(item);
    feedEl.scrollTop = feedEl.scrollHeight;
  }

  function triggerSweepCelebration() {
    const banner = document.createElement('div');
    banner.className = 'sweep-celebration';
    banner.innerHTML = `<span>★</span> SWEEP! +1 POINT <span>★</span>`;
    document.getElementById('khasinoTable').appendChild(banner);
    setTimeout(() => banner.remove(), 1800);
    window.showToast('★ SWEEP! Cleared the entire board for +1 point!', 'success');
  }

  // --- Card UI Renderer ---
  function createCardUnit(card, options = {}) {
    const el = document.createElement('div');
    const isRed = (card.suit === 'HEARTS' || card.suit === 'DIAMONDS');
    const sym = SUIT_SYMBOLS[card.suit] || '';
    const disp = RANK_DISPLAYS[card.rank] || card.rank;
    const isMummy = (card.rank === 'TEN' && card.suit === 'DIAMONDS');
    const isSpyTwo = (card.rank === 'TWO' && card.suit === 'SPADES');

    let extraClasses = options.extraClasses || '';
    if (isMummy) extraClasses += ' mummy-card';
    if (isSpyTwo) extraClasses += ' spy-card';
    if (options.isSelected) extraClasses += ' selected-hand';
    if (options.isTargetSelected) extraClasses += ' selected-target';
    if (options.isPlayable) extraClasses += ' playable';

    el.className = `card-unit khasino-card ${isRed ? 'red' : 'black'} ${extraClasses}`;
    el.setAttribute('data-card-id', card.id);

    let badgeHtml = '';
    if (isMummy) {
      badgeHtml = `<span class="khasino-card-special-badge badge-mummy">MUMMY 2★</span>`;
    } else if (isSpyTwo) {
      badgeHtml = `<span class="khasino-card-special-badge badge-spy">SPY TWO 1★</span>`;
    }

    el.innerHTML = `
      ${badgeHtml}
      <div class="corner-top">
        <span>${disp}</span>
        <span>${sym}</span>
      </div>
      <div class="center-symbol">${sym}</div>
      <div class="corner-bottom">
        <span>${disp}</span>
        <span>${sym}</span>
      </div>
    `;

    if (options.onClick) {
      el.onclick = options.onClick;
    }
    return el;
  }

  function createMiniCard(card) {
    const el = document.createElement('div');
    const isRed = (card.suit === 'HEARTS' || card.suit === 'DIAMONDS');
    const sym = SUIT_SYMBOLS[card.suit] || '';
    const disp = RANK_DISPLAYS[card.rank] || card.rank;
    el.className = `khasino-mini-card ${isRed ? 'red' : 'black'}`;
    el.title = `${card.name || (disp + sym)}`;
    el.innerHTML = `
      <div style="display: flex; justify-content: space-between;"><span>${disp}</span><span>${sym}</span></div>
      <div style="text-align: center; font-size: 0.85rem;">${sym}</div>
    `;
    return el;
  }

  function clearStaged() {
    stagedPlayedCardId = null;
    stagedFloorCardIds.clear();
    stagedTargetBuildId = null;
    stagedStolenCardId = null;
    stagedStolenOpponentId = null;
    updateStagingBar();
    if (currentGameState) renderKhasinoUI(currentGameState);
  }

  document.getElementById('actionClearBtn').onclick = clearStaged;

  function updateStagingBar() {
    const playedEl = document.getElementById('stagePlayedCard');
    const floorEl = document.getElementById('stageTargetFloor');
    const buildEl = document.getElementById('stageTargetBuild');
    const stolenPill = document.getElementById('stageStolenCardPill');
    const stolenEl = document.getElementById('stageStolenCard');

    if (stagedPlayedCardId && currentGameState) {
      const c = currentGameState.hand.find(card => card.id === stagedPlayedCardId);
      playedEl.textContent = c ? `${RANK_DISPLAYS[c.rank] || c.rank}${SUIT_SYMBOLS[c.suit]} (${c.value})` : stagedPlayedCardId;
    } else {
      playedEl.textContent = 'None selected';
    }

    if (stagedFloorCardIds.size > 0 && currentGameState) {
      let sum = 0;
      const names = [];
      stagedFloorCardIds.forEach(id => {
        const fc = currentGameState.floorCards.find(c => c.id === id);
        if (fc) {
          sum += fc.value;
          names.push(`${RANK_DISPLAYS[fc.rank] || fc.rank}${SUIT_SYMBOLS[fc.suit]}`);
        }
      });
      floorEl.textContent = `${names.join(', ')} (Sum: ${sum})`;
      document.getElementById('floorSelectionSummary').textContent = `${stagedFloorCardIds.size} selected (Sum: ${sum})`;
    } else {
      floorEl.textContent = 'None';
      document.getElementById('floorSelectionSummary').textContent = `0 selected (Sum: 0)`;
    }

    if (stagedTargetBuildId && currentGameState) {
      const allBuilds = Array.isArray(currentGameState.builds) ? currentGameState.builds : Object.values(currentGameState.builds || {});
      const b = allBuilds.find(b => b.id === stagedTargetBuildId);
      buildEl.textContent = b ? `Build ${b.captureValue} (${b.ownerId === userEmail ? 'You' : b.ownerId})` : stagedTargetBuildId;
    } else {
      buildEl.textContent = 'None';
    }

    if (stagedStolenCardId) {
      stolenPill.classList.remove('hidden');
      stolenEl.textContent = `${stagedStolenCardId} from ${stagedStolenOpponentId}`;
    } else {
      stolenPill.classList.add('hidden');
    }

    // Evaluate button readiness based on legal moves
    const isMyTurn = (currentGameState && currentGameState.currentTurn === userEmail && !currentGameState.isOver);
    const hasPlayed = Boolean(stagedPlayedCardId);

    const chowBtn = document.getElementById('actionChowBtn');
    const buildBtn = document.getElementById('actionBuildBtn');
    const augmentBtn = document.getElementById('actionAugmentBtn');
    const changeBtn = document.getElementById('actionChangeBtn');
    const driftBtn = document.getElementById('actionDriftBtn');

    if (!isMyTurn || !hasPlayed) {
      chowBtn.disabled = true;
      buildBtn.disabled = true;
      augmentBtn.disabled = true;
      changeBtn.disabled = true;
      driftBtn.disabled = !isMyTurn || !hasPlayed;
      return;
    }

    // Check if staged configuration matches any legal move
    const isLegalChow = legalActionsList.some(a =>
      a.type === 'CHOW' && a.playedCardId === stagedPlayedCardId &&
      (stagedFloorCardIds.size > 0 || stagedTargetBuildId)
    );
    chowBtn.disabled = !isLegalChow;

    const isLegalBuild = legalActionsList.some(a =>
      a.type === 'BUILD_SIMPLE' && a.playedCardId === stagedPlayedCardId &&
      stagedFloorCardIds.size > 0
    );
    buildBtn.disabled = !isLegalBuild;

    const isLegalAugment = legalActionsList.some(a =>
      a.type === 'BUILD_COMPOUND' && a.playedCardId === stagedPlayedCardId &&
      stagedTargetBuildId
    );
    augmentBtn.disabled = !isLegalAugment;

    const isLegalChange = legalActionsList.some(a =>
      a.type === 'CHANGE_BUILD_VALUE' && a.playedCardId === stagedPlayedCardId &&
      stagedTargetBuildId
    );
    changeBtn.disabled = !isLegalChange;

    const isLegalDrift = legalActionsList.some(a =>
      a.type === 'DRIFT' && a.playedCardId === stagedPlayedCardId
    );
    driftBtn.disabled = !isLegalDrift;
  }

  // Submit Action to Backend
  async function submitKhasinoAction(payload) {
    try {
      const res = await fetch(`/games/khasino/action?sessionId=${sessionId}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        window.showToast(data.error || 'Invalid move', 'error');
      } else {
        clearStaged();
        if (data.isSweep) {
          triggerSweepCelebration();
        }
        await fetchKhasinoState();
      }
    } catch (e) {
      window.showToast('Network error executing move', 'error');
    }
  }

  // Button Click Handlers
  document.getElementById('actionChowBtn').onclick = () => {
    if (!stagedPlayedCardId) return window.showToast('Select a card from hand to Chow with', 'info');
    // Find matching legal chow action or submit staged
    const matchingChow = legalActionsList.find(a =>
      a.type === 'CHOW' && a.playedCardId === stagedPlayedCardId &&
      (!stagedTargetBuildId || (a.targetBuildIds && a.targetBuildIds.includes(stagedTargetBuildId)))
    );
    if (matchingChow) {
      submitKhasinoAction(matchingChow);
    } else {
      submitKhasinoAction({
        type: 'CHOW',
        playedCardId: stagedPlayedCardId,
        targetFloorCardIds: Array.from(stagedFloorCardIds),
        targetBuildIds: stagedTargetBuildId ? [stagedTargetBuildId] : []
      });
    }
  };

  document.getElementById('actionBuildBtn').onclick = () => {
    if (!stagedPlayedCardId) return window.showToast('Select a card from hand to build with', 'info');
    // Find matching legal build action
    const matchingBuild = legalActionsList.find(a =>
      a.type === 'BUILD_SIMPLE' && a.playedCardId === stagedPlayedCardId
    );
    if (matchingBuild) {
      submitKhasinoAction(matchingBuild);
    } else {
      const playedCard = currentGameState.hand.find(c => c.id === stagedPlayedCardId);
      let floorSum = 0;
      stagedFloorCardIds.forEach(id => {
        const fc = currentGameState.floorCards.find(c => c.id === id);
        if (fc) floorSum += fc.value;
      });
      const declVal = (playedCard ? playedCard.value : 0) + floorSum;
      submitKhasinoAction({
        type: 'BUILD_SIMPLE',
        playedCardId: stagedPlayedCardId,
        targetFloorCardIds: Array.from(stagedFloorCardIds),
        declaredValue: declVal
      });
    }
  };

  document.getElementById('actionAugmentBtn').onclick = () => {
    if (!stagedPlayedCardId) return window.showToast('Select a card from hand to augment', 'info');
    const matchingAug = legalActionsList.find(a =>
      a.type === 'BUILD_COMPOUND' && a.playedCardId === stagedPlayedCardId &&
      (!stagedTargetBuildId || a.targetBuildId === stagedTargetBuildId)
    );
    if (matchingAug) {
      submitKhasinoAction(matchingAug);
    } else {
      submitKhasinoAction({
        type: 'BUILD_COMPOUND',
        targetBuildId: stagedTargetBuildId,
        playedCardId: stagedPlayedCardId,
        targetFloorCardIds: Array.from(stagedFloorCardIds),
        stolenCardId: stagedStolenCardId,
        stolenOpponentId: stagedStolenOpponentId
      });
    }
  };

  document.getElementById('actionChangeBtn').onclick = () => {
    if (!stagedPlayedCardId) return window.showToast('Select a card from hand to change build value', 'info');
    const matchingChg = legalActionsList.find(a =>
      a.type === 'CHANGE_BUILD_VALUE' && a.playedCardId === stagedPlayedCardId &&
      (!stagedTargetBuildId || a.targetBuildId === stagedTargetBuildId)
    );
    if (matchingChg) {
      submitKhasinoAction(matchingChg);
      const allBuilds = Array.isArray(currentGameState.builds) ? currentGameState.builds : Object.values(currentGameState.builds || {});
      const targetBuild = allBuilds.find(b => b.id === stagedTargetBuildId);
      const playedCard = currentGameState.hand.find(c => c.id === stagedPlayedCardId);
      const newVal = (targetBuild ? targetBuild.captureValue : 0) + (playedCard ? playedCard.value : 0);
      submitKhasinoAction({
        type: 'CHANGE_BUILD_VALUE',
        targetBuildId: stagedTargetBuildId,
        playedCardId: stagedPlayedCardId,
        declaredValue: newVal
      });
    }
  };

  document.getElementById('actionDriftBtn').onclick = () => {
    if (!stagedPlayedCardId) return window.showToast('Select a card from hand to drift onto floor', 'info');
    submitKhasinoAction({
      type: 'DRIFT',
      playedCardId: stagedPlayedCardId
    });
  };

  // Render Khasino UI from state
  function renderKhasinoUI(state) {
    currentGameState = state;

    // Header Mode display
    const modeStr = modeDisplayName[state.mode] || state.mode;
    const dealStr = (state.mode === 'TWO_PLAYER') ? ` • Deal ${state.dealNumber} of 2` : '';
    document.getElementById('khasinoModeDisplay').textContent = `${modeStr}${dealStr}`;

    // Turn Banner
    const isMyTurn = (state.currentTurn === userEmail && !state.isOver);
    const turnBanner = document.getElementById('turnBanner');
    if (state.isOver) {
      turnBanner.className = 'turn-banner my-turn';
      turnBanner.textContent = '🏁 Match Finished!';
    } else if (isMyTurn) {
      turnBanner.className = 'turn-banner my-turn';
      turnBanner.innerHTML = '👉 <strong>YOUR TURN TO PLAY</strong>';
    } else {
      turnBanner.className = 'turn-banner opponent-turn';
      turnBanner.innerHTML = `⏳ <span>${state.currentTurn || 'Opponent'} is thinking...</span>`;
    }

    // 1. Opponents Row
    const oppRow = document.getElementById('khasinoOpponentsRow');
    oppRow.innerHTML = '';
    const oppCounts = state.opponentCounts || {};
    const teams = state.playerTeams || {};
    const capturedPiles = state.capturedPiles || {};
    const sweepCounts = state.sweepCounts || {};

    const myTeam = teams[userEmail] || 0;
    const teamBadge = document.getElementById('myTeamBadge');
    if (myTeam > 0) {
      teamBadge.classList.remove('hidden');
      teamBadge.textContent = `Team ${myTeam}`;
    } else {
      teamBadge.classList.add('hidden');
    }

    // Self statistics
    const myKey = (myTeam > 0) ? `Team ${myTeam}` : userEmail;
    const myCaptured = capturedPiles[myKey] || capturedPiles[userEmail] || [];
    const mySweeps = sweepCounts[myKey] || sweepCounts[userEmail] || 0;
    document.getElementById('myCapturedCount').textContent = myCaptured.length;
    document.getElementById('mySpadesCount').textContent = myCaptured.filter(c => c.suit === 'SPADES').length;
    document.getElementById('mySweepCount').textContent = mySweeps;
    document.getElementById('myHandCountBadge').textContent = `(${state.hand ? state.hand.length : 0} cards)`;

    for (const [oppId, count] of Object.entries(oppCounts)) {
      if (oppId === userEmail) continue;
      const oppTeam = teams[oppId] || 0;
      const oppKey = (oppTeam > 0) ? `Team ${oppTeam}` : oppId;
      const oppCap = capturedPiles[oppKey] || capturedPiles[oppId] || [];
      const oppSw = sweepCounts[oppKey] || sweepCounts[oppId] || 0;
      const topExposed = (oppCap.length > 0) ? oppCap[oppCap.length - 1] : null;

      const oppBox = document.createElement('div');
      oppBox.className = `khasino-opponent-box ${state.currentTurn === oppId ? 'active-turn' : ''}`;

      let teamHtml = '';
      if (oppTeam > 0) {
        const isPartner = (oppTeam === myTeam);
        teamHtml = `<span style="font-size: 0.68rem; padding: 1px 6px; border-radius: 999px; font-weight: 700; ${isPartner ? 'background: rgba(16, 185, 129, 0.25); color: #34d399;' : 'background: rgba(239, 68, 68, 0.25); color: #f87171;'}">${isPartner ? '🤝 Partner' : '⚔️ Opponent'} (Team ${oppTeam})</span>`;
      }

      let exposedCardHtml = '';
      if (topExposed) {
        const isSelectedStolen = (stagedStolenCardId === topExposed.id);
        const isRed = (topExposed.suit === 'HEARTS' || topExposed.suit === 'DIAMONDS');
        const sym = SUIT_SYMBOLS[topExposed.suit] || '';
        const disp = RANK_DISPLAYS[topExposed.rank] || topExposed.rank;

        exposedCardHtml = `
          <div class="khasino-exposed-card-slot ${isSelectedStolen ? 'stolen-selected' : ''}" id="exposed_${oppId}" title="Top exposed card of ${oppId}'s captured pile. Click to steal for building!">
            <div class="card-unit khasino-card ${isRed ? 'red' : 'black'}" style="width: 52px; height: 75px; padding: 4px;">
              <div class="corner-top" style="font-size: 0.72rem;"><span>${disp}</span><span>${sym}</span></div>
              <div class="center-symbol" style="font-size: 1.1rem;">${sym}</div>
            </div>
            <span style="font-size: 0.65rem; color: #fbbf24; font-weight: 700;">★ TOP PILE</span>
          </div>
        `;
      } else {
        exposedCardHtml = `
          <div style="width: 52px; height: 75px; border: 1px dashed rgba(255,255,255,0.15); border-radius: 6px; display: flex; align-items: center; justify-content: center; font-size: 0.65rem; color: var(--text-muted); text-align: center;">
            No cards
          </div>
        `;
      }

      oppBox.innerHTML = `
        <div style="display: flex; flex-direction: column; gap: 0.25rem;">
          <div style="display: flex; align-items: center; gap: 0.4rem;">
            <strong style="color: #ffffff; font-size: 0.9rem;">${oppId}</strong>
            ${teamHtml}
          </div>
          <div style="font-size: 0.78rem; color: var(--text-secondary); display: flex; gap: 0.6rem;">
            <span>Hand: <strong style="color: #fbbf24;">${count}</strong></span>
            <span>Captured: <strong style="color: #34d399;">${oppCap.length}</strong></span>
            <span>Sweeps: <strong style="color: #fbbf24;">${oppSw}</strong> ★</span>
          </div>
        </div>
        ${exposedCardHtml}
      `;

      // Click exposed card to steal
      if (topExposed) {
        const slotEl = oppBox.querySelector(`#exposed_${oppId}`);
        if (slotEl) {
          slotEl.onclick = () => {
            if (stagedStolenCardId === topExposed.id) {
              stagedStolenCardId = null;
              stagedStolenOpponentId = null;
            } else {
              stagedStolenCardId = topExposed.id;
              stagedStolenOpponentId = oppId;
              window.showToast(`Selected exposed ${topExposed.name || topExposed.id} from ${oppId} to steal!`, 'info');
            }
            updateStagingBar();
            renderKhasinoUI(currentGameState);
          };
        }
      }

      oppRow.appendChild(oppBox);
    }

    // 2. Active Builds Grid
    const buildsGrid = document.getElementById('khasinoBuildsGrid');
    buildsGrid.innerHTML = '';
    const builds = Array.isArray(state.builds) ? state.builds : Object.values(state.builds || {});
    document.getElementById('buildCount').textContent = builds.length;

    if (builds.length === 0) {
      buildsGrid.innerHTML = `<div style="color: var(--text-muted); font-size: 0.85rem; padding: 0.75rem; text-align: center; width: 100%;">No active builds on the table.</div>`;
    } else {
      builds.forEach(b => {
        const bCard = document.createElement('div');
        const isSelected = (stagedTargetBuildId === b.id);
        bCard.className = `khasino-build-card ${isSelected ? 'selected-build' : ''}`;

        const isMine = (b.ownerId === userEmail);
        const ownerLabel = isMine ? '👤 You' : `👤 ${b.ownerId}`;

        const badgeHtml = b.isCompound ?
          `<span class="khasino-build-compound-badge">👑 Compound ${b.captureValue}</span>` :
          `<span class="khasino-build-val-badge">🎯 Build ${b.captureValue}</span>`;

        bCard.innerHTML = `
          <div class="khasino-build-header">
            ${badgeHtml}
            <span class="khasino-build-owner">${ownerLabel}</span>
          </div>
          <div class="khasino-build-groups" id="groups_${b.id}"></div>
        `;

        const groupsContainer = bCard.querySelector(`#groups_${b.id}`);
        if (b.groups && b.groups.length > 0) {
          b.groups.forEach((grp, gIdx) => {
            const grpBox = document.createElement('div');
            grpBox.className = 'khasino-compound-group-box';
            grp.forEach(c => {
              grpBox.appendChild(createMiniCard(c));
            });
            groupsContainer.appendChild(grpBox);
          });
        }

        bCard.onclick = () => {
          if (stagedTargetBuildId === b.id) {
            stagedTargetBuildId = null;
          } else {
            stagedTargetBuildId = b.id;
          }
          updateStagingBar();
          renderKhasinoUI(currentGameState);
        };

        buildsGrid.appendChild(bCard);
      });
    }

    // 3. Loose Floor Cards Grid
    const floorGrid = document.getElementById('khasinoFloorGrid');
    floorGrid.innerHTML = '';
    const floorCards = state.floorCards || [];
    document.getElementById('floorCount').textContent = floorCards.length;

    if (floorCards.length === 0) {
      floorGrid.innerHTML = `<div style="color: var(--text-muted); font-size: 0.85rem; padding: 0.75rem; text-align: center; width: 100%;">Floor is currently empty.</div>`;
    } else {
      floorCards.forEach(c => {
        const isTarget = stagedFloorCardIds.has(c.id);
        const cEl = createCardUnit(c, {
          isTargetSelected: isTarget,
          onClick: () => {
            if (stagedFloorCardIds.has(c.id)) {
              stagedFloorCardIds.delete(c.id);
            } else {
              stagedFloorCardIds.add(c.id);
            }
            updateStagingBar();
            renderKhasinoUI(currentGameState);
          }
        });
        floorGrid.appendChild(cEl);
      });
    }

    // 4. Player Hand Fan
    const handFan = document.getElementById('khasinoPlayerHand');
    handFan.innerHTML = '';
    const myHand = state.hand || [];

    myHand.forEach(c => {
      const isSelected = (stagedPlayedCardId === c.id);
      const hasMoves = legalActionsList.some(a => a.playedCardId === c.id);

      const cEl = createCardUnit(c, {
        isSelected: isSelected,
        isPlayable: hasMoves && isMyTurn,
        onClick: () => {
          if (stagedPlayedCardId === c.id) {
            stagedPlayedCardId = null;
          } else {
            stagedPlayedCardId = c.id;
          }
          updateStagingBar();
          updateLegalActionsHelper();
          renderKhasinoUI(currentGameState);
        }
      });
      handFan.appendChild(cEl);
    });

    // 5. Update Statistics / Scores Tracker in Sidebar
    updateScoresTracker(state);

    // 6. Check Game Over
    if (state.isOver) {
      showScorecardModal(state);
    }

    updateStagingBar();
  }

  // Update Smart Legal Moves Helper in Sidebar
  function updateLegalActionsHelper() {
    const container = document.getElementById('khasinoLegalActions');
    const modeText = document.getElementById('helperModeText');
    container.innerHTML = '';

    const isMyTurn = (currentGameState && currentGameState.currentTurn === userEmail && !currentGameState.isOver);
    if (!isMyTurn) {
      container.innerHTML = `<div style="color: var(--text-muted); font-size: 0.8rem; font-style: italic;">Wait for your turn to see legal moves.</div>`;
      modeText.textContent = '';
      return;
    }

    let relevantMoves = legalActionsList;
    if (stagedPlayedCardId) {
      relevantMoves = legalActionsList.filter(a => a.playedCardId === stagedPlayedCardId);
      modeText.textContent = `(for selected card)`;
    } else {
      modeText.textContent = `(All available)`;
    }

    if (relevantMoves.length === 0) {
      container.innerHTML = `<div style="color: var(--text-muted); font-size: 0.8rem; font-style: italic;">No moves found for this selection.</div>`;
      return;
    }

    relevantMoves.slice(0, 15).forEach(action => {
      const chip = document.createElement('div');
      chip.className = 'khasino-helper-chip';

      let icon = '⚡';
      let badgeBg = 'background: rgba(16, 185, 129, 0.2); color: #34d399;';
      if (action.type === 'BUILD_SIMPLE') {
        icon = '🏗️';
        badgeBg = 'background: rgba(59, 130, 246, 0.2); color: #60a5fa;';
      } else if (action.type === 'BUILD_COMPOUND') {
        icon = '➕';
        badgeBg = 'background: rgba(139, 92, 246, 0.2); color: #c4b5fd;';
      } else if (action.type === 'CHANGE_BUILD_VALUE') {
        icon = '🔄';
        badgeBg = 'background: rgba(245, 158, 11, 0.2); color: #fbbf24;';
      } else if (action.type === 'DRIFT') {
        icon = '🍃';
        badgeBg = 'background: rgba(148, 163, 184, 0.2); color: #94a3b8;';
      }

      let actionTitle = action.description;
      if (!actionTitle) {
        const c = currentGameState && currentGameState.hand ? currentGameState.hand.find(card => card.id === action.playedCardId) : null;
        const cName = c ? `${RANK_DISPLAYS[c.rank] || c.rank}${SUIT_SYMBOLS[c.suit]}` : (action.playedCardId || 'card');
        if (action.type === 'DRIFT') actionTitle = `Drift ${cName} to floor`;
        else if (action.type === 'CHOW') actionTitle = `Chow with ${cName}`;
        else if (action.type === 'BUILD_SIMPLE') actionTitle = `Build ${action.declaredValue || ''} with ${cName}`;
        else if (action.type === 'BUILD_COMPOUND') actionTitle = `Augment with ${cName}`;
        else if (action.type === 'CHANGE_BUILD_VALUE') actionTitle = `Increase to ${action.declaredValue || ''} with ${cName}`;
        else actionTitle = action.type;
      }

      chip.innerHTML = `
        <div style="display: flex; align-items: center; gap: 0.5rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
          <span>${icon}</span>
          <span style="font-weight: 600; font-size: 0.8rem; color: #ffffff;">${actionTitle}</span>
        </div>
        <span class="khasino-helper-badge" style="${badgeBg}; flex-shrink: 0;">${action.type.replace('_', ' ')}</span>
      `;

      chip.onclick = () => {
        submitKhasinoAction(action);
      };

      container.appendChild(chip);
    });
  }

  // Update Match Statistics in Sidebar
  function updateScoresTracker(state) {
    const tracker = document.getElementById('khasinoScoresTracker');
    tracker.innerHTML = '';

    const capturedPiles = state.capturedPiles || {};
    const sweepCounts = state.sweepCounts || {};

    for (const [entityId, pile] of Object.entries(capturedPiles)) {
      const sweeps = sweepCounts[entityId] || 0;
      const spades = pile.filter(c => c.suit === 'SPADES').length;
      const aces = pile.filter(c => c.rank === 'ACE').length;
      const hasMummy = pile.some(c => c.rank === 'TEN' && c.suit === 'DIAMONDS');
      const hasSpy = pile.some(c => c.rank === 'TWO' && c.suit === 'SPADES');

      const row = document.createElement('div');
      row.style.cssText = 'background: rgba(0, 0, 0, 0.3); border: 1px solid var(--border-glass); border-radius: var(--radius-sm); padding: 0.5rem 0.75rem;';
      row.innerHTML = `
        <div style="display: flex; justify-content: space-between; font-weight: 700; color: #ffffff; margin-bottom: 0.25rem;">
          <span>${entityId === userEmail ? 'You' : entityId}</span>
          <span style="color: #34d399;">${pile.length} cards (${spades} ♠)</span>
        </div>
        <div style="font-size: 0.75rem; color: var(--text-secondary); display: flex; gap: 0.5rem; flex-wrap: wrap;">
          <span>Aces: <strong style="color: #ffffff;">${aces}</strong></span> •
          <span>Mummy 10♦: <strong style="color: ${hasMummy ? '#fbbf24' : 'var(--text-muted)'};">${hasMummy ? '✓ YES' : '—'}</strong></span> •
          <span>Spy 2♠: <strong style="color: ${hasSpy ? '#60a5fa' : 'var(--text-muted)'};">${hasSpy ? '✓ YES' : '—'}</strong></span> •
          <span>Sweeps: <strong style="color: #fbbf24;">${sweeps} ★</strong></span>
        </div>
      `;
      tracker.appendChild(row);
    }
  }

  // Final Itemized Scorecard Modal
  function showScorecardModal(state) {
    const modal = document.getElementById('khasinoScoreModal');
    const titleEl = document.getElementById('scoreModalTitle');
    const winnerDesc = document.getElementById('scoreModalWinnerDesc');
    const iconEl = document.getElementById('scoreModalIcon');
    const tableContainer = document.getElementById('scorecardTableContainer');

    const winner = state.winnerId;
    const isWinner = (winner === userEmail || (winner && winner.startsWith('Team ') && state.playerTeams && state.playerTeams[userEmail] === (winner === 'Team 1' ? 1 : 2)));

    if (isWinner) {
      iconEl.textContent = '🎉';
      titleEl.textContent = 'MATCH WON!';
      titleEl.style.color = '#34d399';
      winnerDesc.textContent = `Superb strategy! You won the South African Khasino match!`;
      window.showToast('Congratulations! You won the Khasino match!', 'success');
    } else {
      iconEl.textContent = '🥈';
      titleEl.textContent = 'MATCH CONCLUDED';
      titleEl.style.color = '#fbbf24';
      winnerDesc.textContent = `Winner: ${winner || 'Draw'}. Well played!`;
      window.showToast(`Game over. Winner: ${winner}`, 'info');
    }

    const finalScores = state.finalScores || {};
    let tableHtml = `
      <table style="width: 100%; border-collapse: collapse; font-size: 0.85rem; text-align: left;">
        <thead>
          <tr style="border-bottom: 2px solid rgba(16, 185, 129, 0.4); color: #34d399; font-weight: 800;">
            <th style="padding: 6px 10px;">Scoring Category</th>
    `;

    const entityKeys = Object.keys(finalScores);
    entityKeys.forEach(k => {
      tableHtml += `<th style="padding: 6px 10px; text-align: center;">${k === userEmail ? 'You' : k}</th>`;
    });
    tableHtml += `</tr></thead><tbody>`;

    const rows = [
      { label: '10♦ (Mummy) (+2)', key: 'mummyPoints' },
      { label: '2♠ (Spy Two) (+1)', key: 'spyTwoPoints' },
      { label: 'Aces (+1 ea)', key: 'acePoints' },
      { label: 'Most Cards (+2)', key: 'cardsPoints' },
      { label: 'Most Spades (+2)', key: 'spadesPoints' },
      { label: 'Sweeps (+1 ea)', key: 'sweepPoints' },
      { label: 'TOTAL POINTS', key: 'totalPoints', isTotal: true }
    ];

    rows.forEach(r => {
      const isTotal = r.isTotal;
      tableHtml += `
        <tr style="border-bottom: 1px solid var(--border-glass); ${isTotal ? 'font-weight: 800; font-size: 1rem; color: #34d399; background: rgba(16, 185, 129, 0.1);' : ''}">
          <td style="padding: 6px 10px;">${r.label}</td>
      `;
      entityKeys.forEach(k => {
        const val = finalScores[k] ? finalScores[k][r.key] : 0;
        tableHtml += `<td style="padding: 6px 10px; text-align: center; ${isTotal ? 'color: #fbbf24;' : ''}">${val}</td>`;
      });
      tableHtml += `</tr>`;
    });

    tableHtml += `</tbody></table>`;
    tableContainer.innerHTML = tableHtml;

    modal.classList.remove('hidden');
    window.updateHeaderBalance();
  }

  // Fetch Full State from Server
  async function fetchKhasinoState() {
    try {
      const res = await fetch(`/games/khasino/state?sessionId=${sessionId}`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        if (data.state) {
          renderKhasinoUI(data.state);
        }
      }

      // Also refresh legal moves
      const movesRes = await fetch(`/games/khasino/legal-moves?sessionId=${sessionId}`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (movesRes.ok) {
        const movesData = await movesRes.json();
        legalActionsList = movesData.actions || [];
        updateLegalActionsHelper();
      }
    } catch (e) {
      console.warn('Failed to fetch Khasino state:', e);
    }
  }

  // Real-Time SSE Listener
  function initKhasinoSSE() {
    activeEventSource = new EventSource(`/ws/${sessionId}?token=${token}`);

    activeEventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'forfeit') {
          const forfeiter = data.player || data.forfeitedBy || 'An opponent';
          const isYou = (forfeiter === userEmail);
          appendFeed(`⚠️ ${forfeiter} forfeited the match.`);
          if (isYou) {
            window.showToast('You forfeited the match.', 'error');
          } else {
            window.showToast(`Opponent ${forfeiter} forfeited! Victory awarded to you!`, 'success');
          }
          if (currentGameState) currentGameState.isOver = true;
          const modal = document.getElementById('khasinoScoreModal');
          if (modal) {
            const icon = document.getElementById('scoreModalIcon');
            const title = document.getElementById('scoreModalTitle');
            const desc = document.getElementById('scoreModalWinnerDesc');
            if (icon) icon.textContent = isYou ? '🥈' : '🎉';
            if (title) {
              title.textContent = isYou ? 'FORFEITED MATCH' : 'MATCH WON (FORFEIT)!';
              title.style.color = isYou ? '#f87171' : '#34d399';
            }
            if (desc) desc.textContent = isYou ? 'You forfeited the match.' : `${forfeiter} forfeited. You have been awarded the win and wager!`;
            modal.classList.remove('hidden');
          }
          fetchKhasinoState();
        } else if (data.type === 'khasino_move' || data.type === 'start') {
          if (data.state) {
            renderKhasinoUI(data.state);
            if (data.state.lastAction) {
              appendFeed(`🇿🇦 ${data.state.lastAction}`);
            }
          }
          fetchKhasinoState();
        } else if (data.type === 'end') {
          appendFeed('Match concluded.', 'win');
          fetchKhasinoState();
        }
      } catch (e) {
        console.warn('Error parsing Khasino SSE:', e);
      }
    };

    activeEventSource.onerror = (err) => {
      console.warn('SSE warning on Khasino:', err);
    };
  }

  // Initial Load
  await fetchKhasinoState();
  initKhasinoSSE();
}

/* ==========================================================================
   4. Ludo Master Arena Implementation
   ========================================================================== */
const LUDO_TRACK_COORDS = [
  [6, 1], [6, 2], [6, 3], [6, 4], [6, 5],
  [5, 6], [4, 6], [3, 6], [2, 6], [1, 6], [0, 6],
  [0, 7],
  [0, 8], [1, 8], [2, 8], [3, 8], [4, 8], [5, 8],
  [6, 9], [6, 10], [6, 11], [6, 12], [6, 13], [6, 14],
  [7, 14],
  [8, 14], [8, 13], [8, 12], [8, 11], [8, 10], [8, 9],
  [9, 8], [10, 8], [11, 8], [12, 8], [13, 8], [14, 8],
  [14, 7],
  [14, 6], [13, 6], [12, 6], [11, 6], [10, 6], [9, 6],
  [8, 5], [8, 4], [8, 3], [8, 2], [8, 1], [8, 0],
  [7, 0],
  [6, 0]
];

const LUDO_START_CELLS = {
  RED: [6, 1],
  GREEN: [1, 8],
  YELLOW: [8, 13],
  BLUE: [13, 6]
};

const LUDO_SAFE_TRACK_INDICES = [0, 8, 13, 21, 26, 34, 39, 47];

async function renderLudoGame(sessionId, token, userEmail, sessionMeta) {
  const app = document.getElementById('app');
  let ludoState = null;
  let isRolling = false;

  app.innerHTML = `
    <div class="ludo-viewport">
      <div class="ludo-top-bar">
        <div style="display: flex; align-items: center; gap: 0.75rem;">
          <a href="#" id="ludoBackBtn" class="btn btn-ghost btn-sm" title="Return to lobby">
            ← Lobby
          </a>
          <div>
            <div style="font-weight: 800; font-size: 1.15rem; color: #FFFFFF; display: flex; align-items: center; gap: 0.5rem;">
              <span>♟️</span> Ludo Master Arena
            </div>
            <div style="font-size: 0.78rem; color: var(--text-muted);">
              Session: <span style="font-family: var(--font-mono);">${sessionId.substring(0, 8)}...</span>
              • Wager: <span style="color: #FBBF24; font-weight: 700;">${sessionMeta ? sessionMeta.wager : 50} 🪙</span>
            </div>
          </div>
        </div>

        <div class="ludo-turn-banner" id="ludoTurnBanner">
          <span class="ludo-turn-dot" id="ludoTurnDot" style="color: #EF4444; background: #EF4444;"></span>
          <span id="ludoTurnText" style="font-weight: 700; font-size: 0.92rem; color: var(--text-primary);">
            Initializing match...
          </span>
        </div>

        <div style="display: flex; gap: 0.5rem;">
          <button id="ludoRulesBtn" class="btn btn-secondary btn-sm" title="View Ludo rules">
            📖 Rules
          </button>
          <button id="leaveMatchBtn" class="btn btn-danger btn-sm" title="Forfeit or leave match">
            🚪 Leave
          </button>
        </div>
      </div>

      <div class="ludo-game-layout">
        <!-- Main Board Card -->
        <div class="ludo-board-card">
          <div class="ludo-board-grid" id="ludoBoardGrid">
            <!-- 15x15 cells generated dynamically -->
          </div>
        </div>

        <!-- Sidebar: Dice Controls, Players & Activity Feed -->
        <div style="display: flex; flex-direction: column; gap: 1.25rem;">
          <!-- Interactive Dice Roller Card -->
          <div class="card ludo-dice-box">
            <div style="font-size: 0.8rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted);">
              Authoritative Dice
            </div>
            <button id="ludoDiceBtn" class="ludo-dice-btn" title="Roll Dice">
              ⚅
            </button>
            <div id="ludoDiceInstruction" style="font-size: 0.88rem; font-weight: 600; text-align: center; color: var(--text-secondary);">
              Waiting to roll...
            </div>
            <div id="ludoSixBonusBadge" class="hidden" style="font-size: 0.75rem; font-weight: 700; color: #F59E0B; background: rgba(245, 158, 11, 0.15); padding: 3px 10px; border-radius: 12px; border: 1px solid rgba(245, 158, 11, 0.4);">
              🔥 6 ROLLED! Bonus roll awarded!
            </div>
          </div>

          <!-- Participating Players Card -->
          <div class="card" style="padding: 1.25rem;">
            <div style="font-size: 0.82rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); margin-bottom: 0.75rem;">
              Players & Token Race
            </div>
            <div id="ludoPlayersList"></div>
          </div>

          <!-- Chronological Activity Log Card -->
          <div class="card" style="padding: 1.25rem;">
            <div style="font-size: 0.82rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); margin-bottom: 0.5rem; display: flex; justify-content: space-between;">
              <span>Live Action Log</span>
              <span style="color: var(--accent-primary); font-size: 0.75rem;">⚡ Real-Time</span>
            </div>
            <div class="ludo-event-log" id="ludoEventLog">
              <div class="ludo-log-item">Ludo match loaded.</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Rules Modal -->
    <div id="ludoGameRulesModal" class="modal-overlay hidden" style="z-index: 210;">
      <div class="modal-content" style="max-width: 680px; max-height: 85vh; overflow-y: auto; text-align: left; padding: 2rem;">
        <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-medium); padding-bottom: 1rem; margin-bottom: 1.25rem;">
          <h3 style="font-size: 1.3rem; font-weight: 800; color: #F59E0B; display: flex; align-items: center; gap: 0.5rem;">
            <span>♟️</span> Ludo Arena Rules
          </h3>
          <button id="closeLudoGameRulesBtn" class="btn btn-secondary btn-sm">✕ Close</button>
        </div>
        <div style="font-size: 0.88rem; color: var(--text-secondary); line-height: 1.6; display: flex; flex-direction: column; gap: 1rem;">
          <p>• <strong>Yard Exit:</strong> You must roll a <strong>6</strong> to bring a token out of your starting yard onto the track.</p>
          <p>• <strong>Extra Roll:</strong> Rolling a 6 grants a bonus roll on the same turn. If you roll <strong>3 consecutive sixes</strong>, your turn ends immediately without a move!</p>
          <p>• <strong>Captures:</strong> Landing on an opponent token on any standard track square captures it, sending it back to their yard.</p>
          <p>• <strong>Safe Squares:</strong> The 4 starting squares and the 4 star squares (marked with ★) are safe from captures.</p>
          <p>• <strong>Home Finish:</strong> After completing the circuit, tokens enter your color's private home path. Reaching the center requires an <strong>exact roll</strong>. First player to bring all 4 tokens home wins!</p>
        </div>
      </div>
    </div>
  `;

  // Leave / Forfeit button
  setupForfeitLeaveButton(sessionId, token, () => ludoState && ludoState.gameOver);

  // Return to lobby button
  const backBtn = document.getElementById('ludoBackBtn');
  if (backBtn) {
    backBtn.onclick = (e) => {
      e.preventDefault();
      if (activeEventSource) {
        activeEventSource.close();
        activeEventSource = null;
      }
      import('./lobby.js').then(m => window.loadModule(m.LobbyModule));
    };
  }

  // Rules modal
  const rulesModal = document.getElementById('ludoGameRulesModal');
  const rulesBtn = document.getElementById('ludoRulesBtn');
  const closeRulesBtn = document.getElementById('closeLudoGameRulesBtn');
  if (rulesBtn && rulesModal) rulesBtn.onclick = () => rulesModal.classList.remove('hidden');
  if (closeRulesBtn && rulesModal) closeRulesBtn.onclick = () => rulesModal.classList.add('hidden');

  function getYardHex(color) {
    switch (color) {
      case 'RED': return '#EF4444';
      case 'GREEN': return '#10B981';
      case 'YELLOW': return '#F59E0B';
      case 'BLUE': return '#3B82F6';
      default: return '#EF4444';
    }
  }

  function createYardElement(color, startRow, startCol) {
    const yard = document.createElement('div');
    yard.className = `ludo-yard-quadrant ludo-yard-${color}`;
    yard.style.gridRow = `${startRow} / span 6`;
    yard.style.gridColumn = `${startCol} / span 6`;

    yard.innerHTML = `
      <div class="ludo-yard-header" style="color: ${getYardHex(color)};">
        <span>${color} YARD</span>
        <span id="yardCount_${color}" style="font-weight: 800;">4/4</span>
      </div>
      <div class="ludo-yard-docks">
        <div class="ludo-dock-circle" id="dock_${color}_0"></div>
        <div class="ludo-dock-circle" id="dock_${color}_1"></div>
        <div class="ludo-dock-circle" id="dock_${color}_2"></div>
        <div class="ludo-dock-circle" id="dock_${color}_3"></div>
      </div>
    `;
    return yard;
  }

  function buildLudoGrid() {
    const grid = document.getElementById('ludoBoardGrid');
    if (!grid) return;
    grid.innerHTML = '';

    // Create 4 Corner Yards
    grid.appendChild(createYardElement('RED', 1, 1));
    grid.appendChild(createYardElement('GREEN', 1, 10));
    grid.appendChild(createYardElement('BLUE', 10, 1));
    grid.appendChild(createYardElement('YELLOW', 10, 10));

    // Create Center Home Area (row 7..9, col 7..9)
    const centerHome = document.createElement('div');
    centerHome.className = 'ludo-center-home';
    centerHome.id = 'ludoCenterHome';
    centerHome.innerHTML = `
      <svg class="ludo-center-triangles" viewBox="0 0 100 100" preserveAspectRatio="none">
        <polygon points="0,0 50,50 0,100" fill="rgba(239, 68, 68, 0.45)" stroke="#EF4444" stroke-width="1.5" />
        <polygon points="0,0 50,50 100,0" fill="rgba(16, 185, 129, 0.45)" stroke="#10B981" stroke-width="1.5" />
        <polygon points="100,0 50,50 100,100" fill="rgba(245, 158, 11, 0.45)" stroke="#F59E0B" stroke-width="1.5" />
        <polygon points="0,100 50,50 100,100" fill="rgba(59, 130, 246, 0.45)" stroke="#3B82F6" stroke-width="1.5" />
      </svg>
      <div class="ludo-center-trophy">
        <span>🏆</span>
        <span class="ludo-center-finished-count" id="ludoCenterFinishCount">0/4 Home</span>
      </div>
    `;
    grid.appendChild(centerHome);

    // Create Single Cells for Track and Home Columns
    for (let r = 0; r < 15; r++) {
      for (let c = 0; c < 15; c++) {
        // Skip corner yards and center home
        if ((r < 6 && c < 6) || (r < 6 && c >= 9) || (r >= 9 && c < 6) || (r >= 9 && c >= 9)) continue;
        if (r >= 6 && r <= 8 && c >= 6 && c <= 8) continue;

        const cell = document.createElement('div');
        cell.className = 'ludo-cell';
        cell.id = `ludoCell_${r}_${c}`;
        cell.style.gridRow = String(r + 1);
        cell.style.gridColumn = String(c + 1);

        // Check if starting square
        if (r === LUDO_START_CELLS.RED[0] && c === LUDO_START_CELLS.RED[1]) cell.classList.add('start-RED');
        if (r === LUDO_START_CELLS.GREEN[0] && c === LUDO_START_CELLS.GREEN[1]) cell.classList.add('start-GREEN');
        if (r === LUDO_START_CELLS.YELLOW[0] && c === LUDO_START_CELLS.YELLOW[1]) cell.classList.add('start-YELLOW');
        if (r === LUDO_START_CELLS.BLUE[0] && c === LUDO_START_CELLS.BLUE[1]) cell.classList.add('start-BLUE');

        // Check if home column
        if (c === 7 && r >= 1 && r <= 5) cell.classList.add('home-GREEN');
        if (c === 7 && r >= 9 && r <= 13) cell.classList.add('home-BLUE');
        if (r === 7 && c >= 1 && c <= 5) cell.classList.add('home-RED');
        if (r === 7 && c >= 9 && c <= 13) cell.classList.add('home-YELLOW');

        // Check if safe track square (Track 0, 8, 13, 21, 26, 34, 39, 47)
        for (let i = 0; i < LUDO_SAFE_TRACK_INDICES.length; i++) {
          const trackIdx = LUDO_SAFE_TRACK_INDICES[i];
          const coord = LUDO_TRACK_COORDS[trackIdx];
          if (coord[0] === r && coord[1] === c) {
            cell.classList.add('is-safe');
            break;
          }
        }

        grid.appendChild(cell);
      }
    }
  }

  function appendFeed(msg, type = 'normal') {
    const feed = document.getElementById('ludoEventLog');
    if (!feed) return;
    const item = document.createElement('div');
    item.className = `ludo-log-item ${type}`;
    item.textContent = msg;
    feed.insertBefore(item, feed.firstChild);
    while (feed.children.length > 25) {
      feed.removeChild(feed.lastChild);
    }
  }

  function renderLudoUI(state) {
    if (!state) return;

    // 1. Update Turn Banner
    const isMyTurn = (state.currentTurnPlayer === userEmail);
    const activePlayerObj = (state.players || []).find(p => p.id === state.currentTurnPlayer);
    const activeColorHex = activePlayerObj ? activePlayerObj.colorHex : '#42D9D0';
    const activeColorName = activePlayerObj ? activePlayerObj.colorName : 'Player';

    const turnDot = document.getElementById('ludoTurnDot');
    if (turnDot) {
      turnDot.style.color = activeColorHex;
      turnDot.style.background = activeColorHex;
    }

    const turnText = document.getElementById('ludoTurnText');
    if (turnText) {
      if (state.gameOver) {
        turnText.innerHTML = `🏆 <strong style="color: #FBBF24;">${state.winnerId || 'Winner'}</strong> won the match!`;
      } else if (isMyTurn) {
        if (state.phase === 'WAITING_FOR_ROLL') {
          turnText.innerHTML = `🎲 <strong style="color: #42D9D0;">Your Turn!</strong> Roll the dice.`;
        } else {
          turnText.innerHTML = `🎯 <strong style="color: #42D9D0;">Rolled a ${state.currentRoll}!</strong> Click a highlighted token.`;
        }
      } else {
        turnText.innerHTML = `⏳ <strong style="color: ${activeColorHex};">${state.currentTurnPlayer}</strong> (${activeColorName}) is playing...`;
      }
    }

    // 2. Update Dice Box
    const diceBtn = document.getElementById('ludoDiceBtn');
    const diceInst = document.getElementById('ludoDiceInstruction');
    const sixBadge = document.getElementById('ludoSixBonusBadge');

    if (diceBtn && !isRolling) {
      const rollVal = state.currentRoll || 1;
      diceBtn.textContent = DICE_FACES[Math.max(0, Math.min(5, rollVal - 1))];

      if (isMyTurn && state.phase === 'WAITING_FOR_ROLL' && !state.gameOver) {
        diceBtn.disabled = false;
        diceBtn.classList.add('can-roll');
      } else {
        diceBtn.disabled = true;
        diceBtn.classList.remove('can-roll');
      }
    }

    if (diceInst) {
      if (state.gameOver) {
        diceInst.textContent = 'Match Completed!';
      } else if (isMyTurn) {
        if (state.phase === 'WAITING_FOR_ROLL') {
          diceInst.textContent = 'Click dice above to roll!';
        } else {
          diceInst.textContent = `Rolled a ${state.currentRoll}. Select an active token!`;
        }
      } else {
        diceInst.textContent = `Waiting for ${state.currentTurnPlayer}...`;
      }
    }

    if (sixBadge) {
      if (state.currentRoll === 6 && !state.gameOver) {
        sixBadge.classList.remove('hidden');
      } else {
        sixBadge.classList.add('hidden');
      }
    }

    // 3. Clear existing tokens on the board
    document.querySelectorAll('.ludo-token-piece').forEach(t => t.remove());

    // 4. Group & Render Tokens
    const allTokens = state.tokens || {};
    let totalFinishedCount = 0;

    for (const [playerId, tokensList] of Object.entries(allTokens)) {
      let yardCount = 0;
      let playerObj = (state.players || []).find(p => p.id === playerId);
      let color = playerObj ? playerObj.color : 'RED';

      tokensList.forEach(t => {
        const piece = document.createElement('div');
        piece.className = `ludo-token-piece color-${t.color}`;
        piece.textContent = t.id + 1;
        piece.title = `${playerId} (Token ${t.id + 1}, Step: ${t.step})`;

        // Legal move highlight
        if (isMyTurn && state.phase === 'WAITING_FOR_MOVE' && playerId === userEmail) {
          if (Array.isArray(state.legalMoves) && state.legalMoves.includes(t.id)) {
            piece.classList.add('is-legal');
            piece.onclick = (e) => {
              e.stopPropagation();
              submitTokenMove(t.id);
            };
          }
        }

        if (t.step < 0) {
          // Token in Yard Dock
          yardCount++;
          const dock = document.getElementById(`dock_${t.color}_${t.id}`);
          if (dock) dock.appendChild(piece);
        } else if (t.step >= 56) {
          // Finished in Center Home
          totalFinishedCount++;
          const centerHome = document.getElementById('ludoCenterHome');
          if (centerHome) {
            piece.style.transform = 'scale(0.85)';
            centerHome.appendChild(piece);
          }
        } else {
          // On Track or in Home Column
          const targetCell = document.getElementById(`ludoCell_${t.row}_${t.col}`);
          if (targetCell) {
            targetCell.appendChild(piece);
          }
        }
      });

      const yardLabel = document.getElementById(`yardCount_${color}`);
      if (yardLabel) {
        yardLabel.textContent = `${yardCount}/4`;
      }
    }

    const finishCountEl = document.getElementById('ludoCenterFinishCount');
    if (finishCountEl) {
      finishCountEl.textContent = `${totalFinishedCount}/4 Home`;
    }

    // 5. Render Players HUD List
    const playersListEl = document.getElementById('ludoPlayersList');
    if (playersListEl && state.players) {
      playersListEl.innerHTML = '';
      state.players.forEach(p => {
        const isCurrent = (p.id === state.currentTurnPlayer);
        const pTokens = (allTokens[p.id] || []);
        const inYard = pTokens.filter(t => t.step < 0).length;
        const inHome = pTokens.filter(t => t.step >= 56).length;
        const onTrack = pTokens.length - inYard - inHome;

        const row = document.createElement('div');
        row.className = `ludo-player-card ${isCurrent ? 'active-turn' : ''}`;
        row.innerHTML = `
          <div>
            <div style="font-weight: 700; font-size: 0.9rem; color: #FFFFFF; display: flex; align-items: center; gap: 0.4rem;">
              <span>${p.id === userEmail ? '👤 You' : (p.id.includes('Bot') || p.id.includes('CPU') ? '🤖 ' + p.name : p.name)}</span>
              ${isCurrent ? '<span style="font-size: 0.72rem; color: #42D9D0; background: rgba(66,217,208,0.15); padding: 1px 6px; border-radius: 8px;">Active</span>' : ''}
            </div>
            <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.2rem;">
              Yard: <strong>${inYard}</strong> • Track: <strong>${onTrack}</strong> • Finished: <strong style="color: #FBBF24;">${inHome}/4</strong>
            </div>
          </div>
          <span class="ludo-color-badge" style="background: ${p.colorHex}22; color: ${p.colorHex}; border: 1px solid ${p.colorHex}55;">
            ${p.colorName}
          </span>
        `;
        playersListEl.appendChild(row);
      });
    }

    // 6. Action Feed Sync
    if (state.lastMessage) {
      appendFeed(state.lastMessage);
    }

    // 7. Victory Screen on Game Over
    if (state.gameOver) {
      showVictoryModal(state.winnerId);
    }
  }

  function showVictoryModal(winner) {
    if (document.getElementById('ludoVictoryModal')) return;
    const isWinner = (winner === userEmail);
    const modal = document.createElement('div');
    modal.id = 'ludoVictoryModal';
    modal.className = 'modal-overlay';
    modal.style.zIndex = '250';
    modal.innerHTML = `
      <div class="forfeit-modal-box" style="border-color: ${isWinner ? 'var(--accent-primary)' : 'var(--border-medium)'}; text-align: center;">
        <div style="font-size: 3.5rem; margin-bottom: 0.5rem;">${isWinner ? '🏆' : '♟️'}</div>
        <h2 style="font-size: 1.6rem; font-weight: 800; color: #FFFFFF; margin-bottom: 0.5rem;">
          ${isWinner ? 'Match Victory!' : 'Match Finished!'}
        </h2>
        <p style="color: var(--text-secondary); font-size: 0.92rem; margin-bottom: 1.5rem; line-height: 1.5;">
          ${isWinner ? 'Congratulations! You brought all 4 tokens safely into the Home center!' : `${winner} brought all 4 tokens home and claimed the victory.`}
        </p>
        <div style="display: flex; gap: 0.75rem; justify-content: center;">
          <button id="ludoPlayAgainBtn" class="btn btn-primary" style="flex: 1;">
            ⚡ Play Again
          </button>
          <button id="ludoReturnLobbyBtn" class="btn btn-secondary" style="flex: 1;">
            🏠 Game Lobby
          </button>
        </div>
      </div>
    `;

    modal.querySelector('#ludoPlayAgainBtn').onclick = () => {
      modal.remove();
      if (activeEventSource) {
        activeEventSource.close();
        activeEventSource = null;
      }
      import('./lobby.js').then(m => window.loadModule(m.LobbyModule));
    };

    modal.querySelector('#ludoReturnLobbyBtn').onclick = () => {
      modal.remove();
      if (activeEventSource) {
        activeEventSource.close();
        activeEventSource = null;
      }
      import('./lobby.js').then(m => window.loadModule(m.LobbyModule));
    };

    document.body.appendChild(modal);
  }

  // Interactive Dice Roll
  const diceBtn = document.getElementById('ludoDiceBtn');
  if (diceBtn) {
    diceBtn.onclick = async () => {
      if (isRolling || !ludoState) return;
      if (ludoState.currentTurnPlayer !== userEmail) return;
      if (ludoState.phase !== 'WAITING_FOR_ROLL') return;

      isRolling = true;
      diceBtn.classList.add('ludo-dice-rolling');
      diceBtn.disabled = true;

      let tumble = setInterval(() => {
        diceBtn.textContent = DICE_FACES[Math.floor(Math.random() * 6)];
      }, 60);

      try {
        const res = await fetch(`/games/ludo/roll/${sessionId}`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({})
        });
        const data = await res.json();
        clearInterval(tumble);
        diceBtn.classList.remove('ludo-dice-rolling');

        if (data.success) {
          diceBtn.textContent = DICE_FACES[(data.roll || 1) - 1];
          if (data.consecutiveSixPenalty) {
            window.showToast('⚠️ 3 consecutive sixes! Turn forfeited.', 'error');
          } else if (data.noLegalMoves) {
            window.showToast(`Rolled a ${data.roll} with no legal moves. Passing turn...`, 'info');
          } else if (data.roll === 6) {
            window.showToast('🔥 Rolled a 6! Select a token to bring out or advance.', 'success');
          }
        } else {
          window.showToast(data.error || 'Failed to roll', 'error');
        }
        await fetchLudoState();
      } catch (e) {
        clearInterval(tumble);
        diceBtn.classList.remove('ludo-dice-rolling');
        console.warn('Roll error:', e);
      } finally {
        isRolling = false;
      }
    };
  }

  async function submitTokenMove(tokenId) {
    try {
      const res = await fetch(`/games/ludo/move/${sessionId}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ tokenId: tokenId })
      });
      const data = await res.json();
      if (data.success) {
        if (data.captured && data.captured.length > 0) {
          window.showToast(`⚔️ Captured opponent token!`, 'success');
        }
        if (data.bonusRoll) {
          window.showToast(`🎲 Bonus roll granted! Roll again.`, 'success');
        }
      } else {
        window.showToast(data.error || 'Illegal move', 'error');
      }
      await fetchLudoState();
    } catch (e) {
      console.warn('Move error:', e);
    }
  }

  async function fetchLudoState() {
    try {
      const res = await fetch(`/games/ludo/state/${sessionId}`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        ludoState = data.state;
        renderLudoUI(ludoState);
      }
    } catch (e) {
      console.warn('Error fetching Ludo state:', e);
    }
  }

  function initLudoSSE() {
    if (activeEventSource) {
      activeEventSource.close();
      activeEventSource = null;
    }

    activeEventSource = new EventSource(`/events?sessionId=${sessionId}&token=${token}`);
    activeEventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'ludo_roll' || data.type === 'ludo_move') {
          if (data.state) {
            ludoState = data.state;
            renderLudoUI(data.state);
          } else {
            fetchLudoState();
          }
        } else if (data.type === 'forfeit') {
          window.showToast(`Player ${data.player} forfeited match.`, 'info');
          fetchLudoState();
        } else if (data.type === 'end') {
          fetchLudoState();
        }
      } catch (e) {
        console.warn('Error parsing Ludo SSE event:', e);
      }
    };
  }

  buildLudoGrid();
  await fetchLudoState();
  initLudoSSE();
}

export const GameModule = {
  render(container, sessionId) {
    const token = localStorage.getItem('token');
    const user = localStorage.getItem('user') || 'Player';
    loadGameUI(sessionId, token, user);
  }
};
