let boardMeta = null;
let gameState = {};
const colors = ['bg-blue-500', 'bg-green-500', 'bg-pink-500', 'bg-purple-500'];

export async function loadGameUI(sessionId, token, userEmail) {
  const app = document.getElementById('app');
  app.innerHTML = `
    <div class="text-center mb-4">
      <h2 class="text-xl font-semibold">Snakes & Ladders</h2>
      <p class="text-sm text-gray-500">Session: ${sessionId}</p>
    </div>
    <div id="board" class="grid grid-cols-10 gap-0 border border-gray-400"></div>
    <div class="flex justify-center mt-4">
      <button id="dice-btn" class="px-6 py-2 text-white bg-gray-400 rounded">🎲 Roll Dice</button>
    </div>
  `;

  const board = document.getElementById('board');
  const diceButton = document.getElementById('dice-btn');

  diceButton.addEventListener('click', () => rollDice(sessionId, token, userEmail, diceButton, board));

  await loadBoard(sessionId, token, board);
  await initGame(sessionId, token, userEmail, board, diceButton);
}

async function loadBoard(sessionId, token, board) {
  const res = await fetch(`http://localhost:8080/games/snl/board/${sessionId}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  boardMeta = await res.json();
  drawBoard(board);
}

function drawBoard(board) {
  board.innerHTML = '';
  let leftToRight = true;

  for (let row = 10; row > 0; row--) {
    for (let col = 1; col <= 10; col++) {
      let i = (row - 1) * 10 + col;
      if (!leftToRight) i = row * 10 - col + 1;

      const cell = document.createElement('div');
      cell.className = 'w-8 h-8 text-[10px] flex items-center justify-center border border-gray-300 relative';
      cell.textContent = i;
      cell.id = `cell-${i}`;

      if (boardMeta.snakes[i]) {
        cell.classList.add('bg-red-200');
        cell.title = `🐍 Snake to ${boardMeta.snakes[i]}`;
      }
      if (boardMeta.ladders[i]) {
        cell.classList.add('bg-yellow-200');
        cell.title = `🪜 Ladder to ${boardMeta.ladders[i]}`;
      }

      board.appendChild(cell);
    }
    leftToRight = !leftToRight;
  }
}

function drawPlayers(board) {
  document.querySelectorAll('.player-token').forEach(el => el.remove());

  Object.entries(gameState.positions || {}).forEach(([email, pos], idx) => {
    const token = document.createElement('div');
    token.className = `player-token absolute w-3 h-3 rounded-full ${colors[idx % colors.length]}`;
    token.title = email;

    const cell = document.getElementById(`cell-${pos}`);
    if (cell) {
      cell.appendChild(token);
    }
  });
}

function highlightTurn(diceButton, userEmail) {
  const isYourTurn = gameState.currentTurn === userEmail;
  gameState.isYourTurn = isYourTurn;

  diceButton.disabled = !isYourTurn;
  diceButton.classList.toggle('bg-green-500', isYourTurn);
  diceButton.classList.toggle('bg-gray-400', !isYourTurn);
}

async function rollDice(sessionId, token, userEmail, diceButton, board) {
  if (!gameState.isYourTurn) return;

  const res = await fetch(`http://localhost:8080/games/snl/roll/${sessionId}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` }
  });
  const result = await res.json();

  showDice(diceButton, result.roll);
}

function showDice(button, roll) {
  const face = ['⚀', '⚁', '⚂', '⚃', '⚄', '⚅'][roll - 1];
  button.textContent = face;
}

async function initGame(sessionId, token, userEmail, board, diceButton) {
  const res = await fetch(`http://localhost:8080/games/snl/state/${sessionId}`, {
    headers: { Authorization: `Bearer ${token}` }
  });

  gameState = await res.json();
  gameState.isYourTurn = gameState.currentTurn === userEmail;

  drawPlayers(board);
  highlightTurn(diceButton, userEmail);
  connectWebSocket(sessionId, token, board, diceButton, userEmail);
}

function connectWebSocket(sessionId, token, board, diceButton, userEmail) {
  const ws = new EventSource(`http://localhost:8080/ws/${sessionId}?token=${token}`);
  ws.onmessage = (evt) => {
    const data = JSON.parse(evt.data);
    if (data.type === 'move') {
      gameState.positions[data.player] = data.to;
      gameState.currentTurn = data.nextPlayer;
      drawPlayers(board);
      highlightTurn(diceButton, userEmail);
    }
  };
}
