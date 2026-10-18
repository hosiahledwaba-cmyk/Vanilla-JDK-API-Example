// client/web vanilla/scripts/docs.js

let activeToken = localStorage.getItem('token') || '';
let activeUser = localStorage.getItem('user') || '';
let streamEventSource = null;
let monitorIntervalId = null;

// OpenAPI Catalog of Robinhood Endpoints
const API_SPEC = [
  {
    group: '🔐 Authentication & Profiles',
    endpoints: [
      {
        id: 'auth_signup',
        method: 'POST',
        path: '/signup',
        summary: 'Register a new player account',
        desc: 'Creates a new user profile in PostgreSQL with 100 welcome tokens for instant play.',
        requiresAuth: false,
        params: [],
        sampleBody: {
          email: "player_" + Math.floor(Math.random() * 1000) + "@robinhood.io",
          password: "password123"
        }
      },
      {
        id: 'auth_login',
        method: 'POST',
        path: '/login',
        summary: 'Authenticate and receive JWT Bearer token',
        desc: 'Verifies email/username and password, returning a signed JWT token.',
        requiresAuth: false,
        params: [],
        sampleBody: {
          email: "p1@robinhood.io",
          password: "password123"
        }
      }
    ]
  },
  {
    group: '🏠 Custom Multiplayer Rooms',
    endpoints: [
      {
        id: 'rooms_create',
        method: 'POST',
        path: '/rooms/create',
        summary: 'Create custom multiplayer room',
        desc: 'Hosts a new waiting room with a 6-character room code. Supports Khasino (2-player, 3-player, 4-player singles, 2v2 partnerships), Crazy 8s, Uno, or Snakes & Ladders.',
        requiresAuth: true,
        params: [],
        sampleBody: {
          game: "KHASINO",
          khasinoMode: "TWO_PLAYER",
          maxPlayers: 2,
          wager: 50,
          aiDifficulty: "MEDIUM",
          isBotMatch: false
        }
      },
      {
        id: 'rooms_join',
        method: 'POST',
        path: '/rooms/join',
        summary: 'Join waiting room via 6-character code or ID',
        desc: 'Joins an existing waiting room lobby. Automatically assigns available seat index and team ID.',
        requiresAuth: true,
        params: [],
        sampleBody: {
          code: "FR2L37"
        }
      },
      {
        id: 'rooms_get',
        method: 'GET',
        path: '/rooms/{id}',
        summary: 'Fetch waiting room metadata and participant list',
        desc: 'Retrieves room host, participant readiness status, seats, and game configuration.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Room ID or 6-char Room Code', default: 'FR2L37' }
        ],
        sampleBody: null
      },
      {
        id: 'rooms_ready',
        method: 'POST',
        path: '/rooms/{id}/ready',
        summary: 'Toggle readiness status in waiting room',
        desc: 'Marks participant as ready or unready. Broadcasts live update over room SSE.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Room ID or Code', default: '' }
        ],
        sampleBody: {
          ready: true
        }
      },
      {
        id: 'rooms_start',
        method: 'POST',
        path: '/rooms/{id}/start',
        summary: 'Host launches the match',
        desc: 'Starts the game session once all human players are ready. Dispatches match_start SSE broadcast.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Room ID', default: '' }
        ],
        sampleBody: {}
      },
      {
        id: 'rooms_leave',
        method: 'POST',
        path: '/rooms/{id}/leave',
        summary: 'Leave waiting room lobby',
        desc: 'Removes member from room. If host leaves, host role is automatically transferred or room is cancelled.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Room ID', default: '' }
        ],
        sampleBody: {}
      }
    ]
  },
  {
    group: '⚡ Atomic Matchmaking Queue',
    endpoints: [
      {
        id: 'queue_join',
        method: 'POST',
        path: '/games/queue/join',
        summary: 'Enqueue for Quick Match (PostgreSQL row-locked)',
        desc: 'Enters queue atomically in PostgreSQL. If another player with matching game and wager is waiting, instantly pairs them.',
        requiresAuth: true,
        params: [],
        sampleBody: {
          game: "KHASINO",
          khasinoMode: "TWO_PLAYER",
          bet: 50
        }
      },
      {
        id: 'queue_status',
        method: 'POST',
        path: '/games/queue/status',
        summary: 'Poll matchmaking ticket status',
        desc: 'Checks if queued player has been paired with an opponent into an active session.',
        requiresAuth: true,
        params: [],
        sampleBody: {}
      },
      {
        id: 'queue_cancel',
        method: 'POST',
        path: '/games/queue/cancel',
        summary: 'Cancel queued matchmaking ticket',
        desc: 'Removes player from PostgreSQL matchmaking_queue table.',
        requiresAuth: true,
        params: [],
        sampleBody: {}
      }
    ]
  },
  {
    group: '🎮 Active Matches & Forfeits',
    endpoints: [
      {
        id: 'game_session_get',
        method: 'GET',
        path: '/games/session/{id}',
        summary: 'Get match session metadata and turn state',
        desc: 'Retrieves current game type, participants, active turn, started flag, and isOver / winner state.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Game Session ID', default: '' }
        ],
        sampleBody: null
      },
      {
        id: 'game_forfeit',
        method: 'POST',
        path: '/games/forfeit',
        summary: 'Forfeit active match',
        desc: 'Enforces the strict forfeit policy. Immediately ends match, awards victory & wager to remaining player, logs to DB, and broadcasts SSE forfeit notification.',
        requiresAuth: true,
        params: [],
        sampleBody: {
          sessionId: ""
        }
      },
      {
        id: 'game_history',
        method: 'GET',
        path: '/games/history',
        summary: 'Get player match history from PostgreSQL',
        desc: 'Returns chronological history of all completed and forfeited matches with outcomes, scores, and XP.',
        requiresAuth: true,
        params: [],
        sampleBody: null
      }
    ]
  },
  {
    group: '🇿🇦 South African Khasino (Kasi Kasino)',
    endpoints: [
      {
        id: 'khasino_state',
        method: 'GET',
        path: '/games/khasino/state/{id}',
        summary: 'Fetch full South African Khasino state',
        desc: 'Returns current 40-card South African Khasino state, floor cards, simple & compound builds, player hands, captured piles, and tallies.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Game Session ID', default: '' }
        ],
        sampleBody: null
      },
      {
        id: 'khasino_legal_moves',
        method: 'GET',
        path: '/games/khasino/legal-moves/{id}',
        summary: 'Compute all legal moves for current turn',
        desc: 'Calculates every legal Chow, Simple Build, Compound Build, Build Value Change, Drift, and Steal.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Game Session ID', default: '' }
        ],
        sampleBody: null
      },
      {
        id: 'khasino_action',
        method: 'POST',
        path: '/games/khasino/action',
        summary: 'Execute a South African Khasino card move',
        desc: 'Plays a card action. Advances anti-clockwise turn, checks sweep/deal completion, and persists snapshot to PostgreSQL.',
        requiresAuth: true,
        params: [],
        sampleBody: {
          sessionId: "",
          action: {
            type: "DRIFT",
            playedCardId: "H_5"
          }
        }
      }
    ]
  },
  {
    group: '♟️ Ludo Master Arena',
    endpoints: [
      {
        id: 'ludo_state',
        method: 'GET',
        path: '/games/ludo/state/{id}',
        summary: 'Fetch authoritative Ludo game state',
        desc: 'Returns complete 4-colour Ludo state including player colors, 4 tokens per player, step positions, track coordinates, safe status, and legal choices.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Game Session ID', default: '' }
        ],
        sampleBody: null
      },
      {
        id: 'ludo_legal_moves',
        method: 'GET',
        path: '/games/ludo/legal-moves/{id}',
        summary: 'Query legal token moves for active player',
        desc: 'Calculates eligible tokens that can move with the current dice roll without overshooting finish step 56.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Game Session ID', default: '' }
        ],
        sampleBody: null
      },
      {
        id: 'ludo_roll',
        method: 'POST',
        path: '/games/ludo/roll/{id}',
        summary: 'Roll authoritative die for active turn',
        desc: 'Generates a 1-6 dice roll. Evaluates consecutive sixes (3rd six penalizes and ends turn), legal moves, and bonus turn state.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Game Session ID', default: '' }
        ],
        sampleBody: {}
      },
      {
        id: 'ludo_move',
        method: 'POST',
        path: '/games/ludo/move/{id}',
        summary: 'Move chosen token on Ludo circuit',
        desc: 'Advances the selected token, applies captures on shared track (unless safe), enters home path, detects exact finish win, and awards bonus rolls on 6.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Game Session ID', default: '' }
        ],
        sampleBody: {
          tokenId: 0
        }
      }
    ]
  },
  {
    group: '🃏 Card Games (Crazy 8s & Uno)',
    endpoints: [
      {
        id: 'card_state',
        method: 'GET',
        path: '/games/card/state/{id}',
        summary: 'Get Crazy 8s / Uno game state',
        desc: 'Retrieves top discard card, active suit/color, player cards, turn order, and bot status.',
        requiresAuth: true,
        params: [
          { name: 'id', in: 'path', required: true, desc: 'Session ID', default: '' }
        ],
        sampleBody: null
      },
      {
        id: 'card_action',
        method: 'POST',
        path: '/games/card/action',
        summary: 'Play card or draw in Crazy 8s / Uno',
        desc: 'Executes card action (play, draw, call suit/color, Uno declaration).',
        requiresAuth: true,
        params: [],
        sampleBody: {
          sessionId: "",
          action: "play",
          cardId: "H_8",
          declaredSuit: "SPADES"
        }
      }
    ]
  },
  {
    group: '🎲 Snakes & Ladders',
    endpoints: [
      {
        id: 'snl_roll',
        method: 'POST',
        path: '/games/snl/roll',
        summary: 'Roll dice in Snakes & Ladders match',
        desc: 'Rolls 6-sided dice, navigates 100-cell zigzag board, executes ladder zooms or snake bites, and checks 100-tile victory.',
        requiresAuth: true,
        params: [],
        sampleBody: {
          sessionId: ""
        }
      }
    ]
  },
  {
    group: '🪙 Wallet & Tokens',
    endpoints: [
      {
        id: 'wallet_balance',
        method: 'GET',
        path: '/wallet/balance',
        summary: 'Fetch player token balance',
        desc: 'Returns current token balance.',
        requiresAuth: true,
        params: [],
        sampleBody: null
      },
      {
        id: 'wallet_deposit',
        method: 'POST',
        path: '/wallet/deposit',
        summary: 'Credit tokens to wallet',
        desc: 'Credits tokens for testing or simulated deposit.',
        requiresAuth: true,
        params: [],
        sampleBody: {
          amount: 100
        }
      }
    ]
  },
  {
    group: '📈 System Health & Telemetry',
    endpoints: [
      {
        id: 'sys_health',
        method: 'GET',
        path: '/health',
        summary: 'System health probe',
        desc: 'Returns 200 OK if NIO server loop is operational.',
        requiresAuth: false,
        params: [],
        sampleBody: null
      },
      {
        id: 'sys_stats',
        method: 'GET',
        path: '/api/system/stats',
        summary: 'Live system stats and metrics (JSON)',
        desc: 'Returns real-time JVM telemetry, memory utilization, PostgreSQL status, and active engine counts.',
        requiresAuth: false,
        params: [],
        sampleBody: null
      },
      {
        id: 'sys_metrics',
        method: 'GET',
        path: '/metrics',
        summary: 'Prometheus scraper metrics endpoint',
        desc: 'Returns Prometheus exposition format metrics (requests, errors, active sessions, uptime).',
        requiresAuth: false,
        params: [],
        sampleBody: null
      }
    ]
  }
];

// Helper: Toast Notification
window.showToast = function(msg, type = 'info') {
  const c = document.getElementById('toast-container');
  if (!c) return;
  const t = document.createElement('div');
  t.className = `toast ${type}`;
  t.textContent = msg;
  c.appendChild(t);
  setTimeout(() => t.remove(), 4000);
};

// Tab Switching
window.switchTab = function(tabName) {
  document.querySelectorAll('.nav-tab-btn').forEach(b => b.classList.remove('active'));
  document.getElementById('viewDocs').style.display = 'none';
  document.getElementById('viewMonitor').style.display = 'none';
  document.getElementById('viewStream').style.display = 'none';

  if (tabName === 'docs') {
    document.getElementById('tabBtnDocs').classList.add('active');
    document.getElementById('viewDocs').style.display = 'block';
  } else if (tabName === 'monitor') {
    document.getElementById('tabBtnMonitor').classList.add('active');
    document.getElementById('viewMonitor').style.display = 'block';
    fetchSystemStats(false);
  } else if (tabName === 'stream') {
    document.getElementById('tabBtnStream').classList.add('active');
    document.getElementById('viewStream').style.display = 'block';
  }
};

// Render Swagger Endpoints
function renderEndpoints() {
  const container = document.getElementById('endpointsContainer');
  if (!container) return;

  let html = '';
  API_SPEC.forEach(group => {
    html += `
      <div class="api-group" data-group="${group.group.toLowerCase()}">
        <div class="api-group-title">
          <span>${group.group}</span>
          <span style="font-size: 0.75rem; color: #64748b; font-family: 'JetBrains Mono'; font-weight: normal;">
            (${group.endpoints.length} endpoints)
          </span>
        </div>
    `;

    group.endpoints.forEach(ep => {
      const methodLower = ep.method.toLowerCase();
      html += `
        <div class="endpoint-card ${methodLower}" id="card_${ep.id}" data-search="${ep.path} ${ep.method} ${ep.summary} ${group.group}">
          <div class="endpoint-header" onclick="toggleEndpoint('${ep.id}')">
            <div class="endpoint-identity">
              <span class="method-badge ${methodLower}">${ep.method}</span>
              <span class="endpoint-path">${ep.path}</span>
              <span class="endpoint-summary">• ${ep.summary}</span>
            </div>
            <div style="display: flex; align-items: center; gap: 0.75rem;">
              ${ep.requiresAuth ? '<span style="font-size: 0.72rem; color: #fbbf24; font-family: \'JetBrains Mono\';">🔒 Protected</span>' : '<span style="font-size: 0.72rem; color: #94a3b8; font-family: \'JetBrains Mono\';">🌐 Public</span>'}
              <span id="chevron_${ep.id}" style="color: #64748b; font-size: 0.85rem;">▼</span>
            </div>
          </div>

          <div class="endpoint-body" id="body_${ep.id}">
            <p style="font-size: 0.85rem; color: #94a3b8; line-height: 1.5; margin-bottom: 1.25rem;">
              ${ep.desc}
            </p>

            ${ep.params.length > 0 ? `
              <div style="font-size: 0.8rem; font-weight: 700; color: #cbd5e1; margin-bottom: 0.5rem; text-transform: uppercase;">
                Parameters
              </div>
              <table class="param-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>In</th>
                    <th>Required</th>
                    <th>Description</th>
                    <th>Value</th>
                  </tr>
                </thead>
                <tbody>
                  ${ep.params.map(p => `
                    <tr>
                      <td><code style="color: #38bdf8;">${p.name}</code></td>
                      <td>${p.in}</td>
                      <td><span style="color: ${p.required ? '#f87171' : '#94a3b8'};">${p.required ? 'Yes' : 'No'}</span></td>
                      <td>${p.desc}</td>
                      <td>
                        <input type="text" id="param_${ep.id}_${p.name}" class="form-input" style="padding: 0.35rem 0.65rem; font-size: 0.82rem; font-family: 'JetBrains Mono';" value="${p.default || ''}" placeholder="value" />
                      </td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            ` : ''}

            ${ep.sampleBody !== null ? `
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
                <span style="font-size: 0.8rem; font-weight: 700; color: #cbd5e1; text-transform: uppercase;">
                  Request Body (application/json)
                </span>
                <button class="btn btn-secondary" onclick="resetBody('${ep.id}')" style="font-size: 0.72rem; padding: 0.2rem 0.55rem;">
                  Reset Sample
                </button>
              </div>
              <textarea id="body_input_${ep.id}" class="code-box">${JSON.stringify(ep.sampleBody, null, 2)}</textarea>
            ` : ''}

            <div style="margin-top: 1.25rem; display: flex; align-items: center; gap: 0.75rem;">
              <button class="btn-execute" onclick="executeEndpoint('${ep.id}')">
                ▶️ Execute Request
              </button>
              <span id="loader_${ep.id}" style="display: none; font-size: 0.85rem; color: #38bdf8;">
                ⏳ Executing...
              </span>
            </div>

            <!-- Response Panel -->
            <div id="resp_panel_${ep.id}" style="display: none; margin-top: 1.25rem;">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <div style="display: flex; align-items: center; gap: 0.75rem;">
                  <span style="font-size: 0.8rem; font-weight: 700; color: #cbd5e1; text-transform: uppercase;">
                    Response
                  </span>
                  <span id="resp_status_${ep.id}" class="status-pill success">200 OK</span>
                  <span id="resp_time_${ep.id}" style="font-size: 0.75rem; color: #94a3b8; font-family: 'JetBrains Mono';">-- ms</span>
                </div>
                <button class="btn btn-secondary" onclick="copyResponse('${ep.id}')" style="font-size: 0.72rem; padding: 0.2rem 0.55rem;">
                  📋 Copy JSON
                </button>
              </div>
              <div id="resp_viewer_${ep.id}" class="response-box"></div>
            </div>
          </div>
        </div>
      `;
    });

    html += `</div>`;
  });

  container.innerHTML = html;
}

window.toggleEndpoint = function(id) {
  const b = document.getElementById(`body_${id}`);
  const chevron = document.getElementById(`chevron_${id}`);
  if (!b) return;
  const isOpen = b.classList.contains('open');
  if (isOpen) {
    b.classList.remove('open');
    if (chevron) chevron.textContent = '▼';
  } else {
    b.classList.add('open');
    if (chevron) chevron.textContent = '▲';
  }
};

window.expandAllEndpoints = function(expand) {
  document.querySelectorAll('.endpoint-body').forEach(b => {
    if (expand) b.classList.add('open');
    else b.classList.remove('open');
  });
  document.querySelectorAll('[id^="chevron_"]').forEach(c => {
    c.textContent = expand ? '▲' : '▼';
  });
};

window.filterEndpoints = function() {
  const q = document.getElementById('endpointSearch').value.toLowerCase().trim();
  document.querySelectorAll('.endpoint-card').forEach(card => {
    const hay = card.getAttribute('data-search').toLowerCase();
    if (!q || hay.includes(q)) {
      card.style.display = 'block';
    } else {
      card.style.display = 'none';
    }
  });
};

window.resetBody = function(id) {
  for (const group of API_SPEC) {
    for (const ep of group.endpoints) {
      if (ep.id === id && ep.sampleBody !== null) {
        document.getElementById(`body_input_${id}`).value = JSON.stringify(ep.sampleBody, null, 2);
        return;
      }
    }
  }
};

window.copyResponse = function(id) {
  const el = document.getElementById(`resp_viewer_${id}`);
  if (el) {
    navigator.clipboard.writeText(el.textContent);
    window.showToast('Response copied to clipboard', 'success');
  }
};

// Execute Interactive Endpoint
window.executeEndpoint = async function(id) {
  let targetEp = null;
  for (const group of API_SPEC) {
    for (const ep of group.endpoints) {
      if (ep.id === id) {
        targetEp = ep;
        break;
      }
    }
    if (targetEp) break;
  }
  if (!targetEp) return;

  const loader = document.getElementById(`loader_${id}`);
  const panel = document.getElementById(`resp_panel_${id}`);
  const statusEl = document.getElementById(`resp_status_${id}`);
  const timeEl = document.getElementById(`resp_time_${id}`);
  const viewer = document.getElementById(`resp_viewer_${id}`);

  let path = targetEp.path;
  targetEp.params.forEach(p => {
    if (p.in === 'path') {
      const val = document.getElementById(`param_${id}_${p.name}`).value.trim();
      path = path.replace(`{${p.name}}`, encodeURIComponent(val));
    }
  });

  const headers = {};
  if (activeToken) {
    headers['Authorization'] = `Bearer ${activeToken}`;
  }

  let body = null;
  if (targetEp.method !== 'GET') {
    headers['Content-Type'] = 'application/json';
    const bodyInput = document.getElementById(`body_input_${id}`);
    if (bodyInput) {
      body = bodyInput.value.trim();
    }
  }

  if (loader) loader.style.display = 'inline';

  const startTime = performance.now();
  try {
    const res = await fetch(path, {
      method: targetEp.method,
      headers,
      body: targetEp.method !== 'GET' ? body : undefined
    });
    const duration = Math.round(performance.now() - startTime);

    const statusText = `${res.status} ${res.statusText || (res.status === 200 ? 'OK' : res.status === 201 ? 'Created' : '')}`;
    statusEl.textContent = statusText;
    statusEl.className = 'status-pill ' + (res.status < 300 ? 'success' : res.status < 500 ? 'client-err' : 'server-err');
    timeEl.textContent = `${duration} ms`;

    const raw = await res.text();
    let pretty = raw;
    try {
      const parsed = JSON.parse(raw);
      pretty = JSON.stringify(parsed, null, 2);

      // If this was a login or signup, automatically capture token!
      if (id === 'auth_login' && parsed.token) {
        activeToken = parsed.token;
        activeUser = parsed.email || parsed.user || 'User';
        localStorage.setItem('token', activeToken);
        localStorage.setItem('user', activeUser);
        updateAuthBadge();
        window.showToast(`Authenticated as ${activeUser}! Token active.`, 'success');
      }
    } catch {}

    viewer.textContent = pretty;
    panel.style.display = 'block';
  } catch (err) {
    const duration = Math.round(performance.now() - startTime);
    statusEl.textContent = 'Network Error';
    statusEl.className = 'status-pill server-err';
    timeEl.textContent = `${duration} ms`;
    viewer.textContent = `Fetch Exception: ${err.message}`;
    panel.style.display = 'block';
  } finally {
    if (loader) loader.style.display = 'none';
  }
};

// System Stats Monitoring
async function fetchSystemStats(showToastNotif = false) {
  const pingStart = performance.now();
  try {
    const res = await fetch('/api/system/stats');
    const latency = Math.round(performance.now() - pingStart);
    document.getElementById('headerLatencyText').textContent = `${latency} ms`;

    if (res.ok) {
      const data = await res.json();
      document.getElementById('rawStatsViewer').textContent = JSON.stringify(data, null, 2);

      // Server Card
      document.getElementById('metricServerStatus').textContent = data.status || 'UP';
      const upSec = Math.floor((data.timestamp - (data.timestamp - (data.server ? data.server.threads * 1000 : 0))) / 1000);
      document.getElementById('metricServerThreads').textContent = `Threads: ${data.server ? data.server.threads : '--'} active (${data.server ? data.server.availableProcessors : 4} cores)`;
      document.getElementById('metricServerUptime').textContent = `Uptime: Running • Port 8080`;

      // Database Card
      if (data.database) {
        document.getElementById('metricDbStatus').textContent = data.database.connected ? 'CONNECTED' : 'DISCONNECTED';
        document.getElementById('metricDbStatus').style.color = data.database.connected ? '#34d399' : '#f87171';
        document.getElementById('metricDbDetails').textContent = `${data.database.type} • Port ${data.database.port}`;
        document.getElementById('metricDbStats').textContent = `Users: ${data.database.totalUsers} • Matches: ${data.database.totalMatches}`;
      }

      // Memory Card
      if (data.memory) {
        const usedMb = Math.round(data.memory.usedBytes / (1024 * 1024));
        const maxMb = Math.round(data.memory.maxBytes / (1024 * 1024));
        const pct = Math.min(100, Math.round((usedMb / maxMb) * 100));
        document.getElementById('metricMemUsed').textContent = `${usedMb} MB`;
        document.getElementById('metricMemMax').textContent = `Max Allocated: ${maxMb} MB`;
        document.getElementById('metricMemPercent').textContent = `${pct}%`;
        document.getElementById('metricMemBar').style.width = `${pct}%`;
      }

      // Game Engine Card
      if (data.engine) {
        document.getElementById('metricActiveSessions').textContent = data.engine.activeGameSessions;
        document.getElementById('metricWaitingRooms').textContent = `Waiting Lobbies: ${data.engine.openWaitingRooms}`;
        document.getElementById('metricQueueCount').textContent = `Matchmaking Queue: ${data.engine.queuedPlayers} players`;
      }

      if (showToastNotif) window.showToast('Telemetry refreshed!', 'info');
    }
  } catch (e) {
    document.getElementById('headerStatusText').textContent = 'Server Offline';
    document.getElementById('headerStatusText').style.color = '#f87171';
    console.warn('Stats fetch error:', e);
  }
}

window.fetchSystemStats = fetchSystemStats;

window.updateRefreshInterval = function() {
  if (monitorIntervalId) clearInterval(monitorIntervalId);
  const val = parseInt(document.getElementById('monitorRefreshRate').value, 10);
  if (val > 0) {
    monitorIntervalId = setInterval(() => fetchSystemStats(false), val);
  }
};

// SSE Stream Monitor
window.toggleSseStream = function() {
  const btn = document.getElementById('streamConnectBtn');
  const input = document.getElementById('streamTopicInput');
  const topic = input.value.trim();

  if (streamEventSource) {
    streamEventSource.close();
    streamEventSource = null;
    btn.textContent = 'Connect to Stream';
    btn.className = 'btn btn-primary';
    appendStreamLog('[DISCONNECTED] SSE stream closed.');
    return;
  }

  if (!topic) {
    return window.showToast('Please enter a Session ID or Room ID to attach.', 'error');
  }

  appendStreamLog(`[CONNECTING] Connecting to SSE for topic: ${topic}...`);
  btn.textContent = 'Disconnect';
  btn.className = 'btn btn-secondary';

  const isSession = (topic.length > 20 && !topic.startsWith('room-'));
  const url = isSession ? `/ws/${topic}?token=${activeToken}` : `/events?roomId=${topic}&token=${activeToken}`;

  streamEventSource = new EventSource(url);

  streamEventSource.onopen = () => {
    appendStreamLog(`[OPEN] Connection established to ${url}`);
  };

  streamEventSource.onmessage = (event) => {
    const time = new Date().toLocaleTimeString();
    appendStreamLog(`[FRAME ${time}] ${event.data}`);
  };

  streamEventSource.onerror = (err) => {
    appendStreamLog(`[ERROR] SSE connection error or stream closed.`);
  };
};

function appendStreamLog(msg) {
  const box = document.getElementById('streamEventContainer');
  if (!box) return;
  const line = document.createElement('div');
  line.style.padding = '4px 0';
  line.style.borderBottom = '1px solid rgba(255,255,255,0.03)';
  line.textContent = msg;
  box.appendChild(line);
  box.scrollTop = box.scrollHeight;
}

window.clearStreamLog = function() {
  const box = document.getElementById('streamEventContainer');
  if (box) box.innerHTML = '<div style="color: #64748b;">[Cleared] Stream log cleared.</div>';
};

// Auth Modal
window.openAuthModal = function() {
  document.getElementById('modalTokenInput').value = activeToken;
  document.getElementById('authModal').classList.remove('hidden');
};

window.closeAuthModal = function() {
  document.getElementById('authModal').classList.add('hidden');
};

window.saveActiveToken = function() {
  activeToken = document.getElementById('modalTokenInput').value.trim();
  localStorage.setItem('token', activeToken);
  updateAuthBadge();
  closeAuthModal();
  window.showToast('Active token updated!', 'success');
};

window.clearActiveToken = function() {
  activeToken = '';
  activeUser = '';
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  document.getElementById('modalTokenInput').value = '';
  updateAuthBadge();
  closeAuthModal();
  window.showToast('Logged out from API console.', 'info');
};

window.quickAuth = async function(email, password) {
  try {
    const res = await fetch('/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    if (res.ok) {
      const data = await res.json();
      activeToken = data.token;
      activeUser = email;
      localStorage.setItem('token', activeToken);
      localStorage.setItem('user', activeUser);
      updateAuthBadge();
      window.showToast(`Authorized as ${email}!`, 'success');
    } else {
      window.showToast('Login failed. Ensure user is signed up.', 'error');
    }
  } catch (e) {
    window.showToast('Error authenticating', 'error');
  }
};

window.setAdminToken = function() {
  activeToken = 'super-secret-admin-token';
  activeUser = 'SuperAdmin';
  localStorage.setItem('token', activeToken);
  localStorage.setItem('user', activeUser);
  updateAuthBadge();
  window.showToast('SuperAdmin token activated!', 'success');
};

function updateAuthBadge() {
  const badge = document.getElementById('activeAuthBadge');
  const btnIcon = document.getElementById('authBtnIcon');
  const btnText = document.getElementById('authBtnText');
  if (activeToken) {
    badge.textContent = `Authorized (${activeUser || activeToken.substring(0, 10) + '...'})`;
    badge.style.color = '#34d399';
    if (btnIcon) btnIcon.textContent = '🔒';
    if (btnText) btnText.textContent = 'Authorized';
  } else {
    badge.textContent = 'Anonymous (No Token)';
    badge.style.color = '#fbbf24';
    if (btnIcon) btnIcon.textContent = '🔓';
    if (btnText) btnText.textContent = 'Authorize';
  }
}

// Initial Setup
renderEndpoints();
updateAuthBadge();
fetchSystemStats(false);
updateRefreshInterval();
