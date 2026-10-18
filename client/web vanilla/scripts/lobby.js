// client/web vanilla/scripts/lobby.js

import { renderNavTabs, bindNavTabs, loadModule } from './main.js';
import { getIcon } from './icons.js';

export const LobbyModule = {
  render(container) {
    const token = localStorage.getItem('token');
    const user = localStorage.getItem('user') || 'Player';
    let selectedGame = 'snl';
    let selectedBet = 50;
    let queuePollInterval = null;

    container.innerHTML = `
      <div class="lobby-viewport">
        ${renderNavTabs('lobby')}

        <!-- Lobby Hero Header -->
        <div class="lobby-hero">
          <div>
            <h1 class="lobby-hero-title">Games & Arenas</h1>
            <p class="lobby-hero-desc">
              Discover authentic card classics, board races, and strategic arenas. Compete against intelligent AI or jump into live multiplayer rooms.
            </p>
          </div>
          <div class="balance-chip" style="background: var(--surface-elevated); border-color: var(--border-medium);">
            <span style="display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: var(--status-success); box-shadow: 0 0 8px var(--status-success);"></span>
            <span style="font-size: 0.8rem; color: var(--text-secondary);">Synchronous Engine Online</span>
          </div>
        </div>

        <div class="lobby-grid">
          <!-- Main Game Selection & Match Control -->
          <div class="card">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.25rem;">
              <h2 style="font-size: 1.25rem; font-weight: 800; letter-spacing: -0.02em;">
                Select Game Title
              </h2>
              <span style="font-size: 0.78rem; color: var(--text-muted); font-weight: 600;">5 Modes Available</span>
            </div>
            <p style="color: var(--text-secondary); font-size: 0.88rem; margin-bottom: 1.25rem;">
              Choose your favorite game mode to configure match wagers and find opponents.
            </p>

            <div class="game-options-grid" id="gameOptions">
              <!-- South African Khasino -->
              <div class="game-option-card" data-game="khasino">
                <div class="game-card-top">
                  <span class="game-badge" style="border-color: rgba(66, 217, 208, 0.4); background: rgba(66, 217, 208, 0.12); color: var(--accent-primary);">
                    Authentic SA
                  </span>
                  <span class="game-selection-pill">${getIcon('check', 12)} Selected</span>
                </div>
                <div class="game-icon-box">${getIcon('cards', 28)}</div>
                <div>
                  <div class="game-title">Khasino (Kasi Kasino)</div>
                  <div class="game-desc">Authentic 40-card South African fishing game with Chow, Builds, Steals, Cupha, and 2v2 Partnerships.</div>
                </div>
              </div>

              <!-- Snakes & Ladders -->
              <div class="game-option-card active" data-game="snl">
                <div class="game-card-top">
                  <span class="game-badge">Classic Board</span>
                  <span class="game-selection-pill">${getIcon('check', 12)} Selected</span>
                </div>
                <div class="game-icon-box">${getIcon('dice', 28)}</div>
                <div>
                  <div class="game-title">Snakes & Ladders</div>
                  <div class="game-desc">100-cell zigzag race, dice rolling, snakes and ladders with tactical CPU auto-play.</div>
                </div>
              </div>

              <!-- Crazy Eights -->
              <div class="game-option-card" data-game="crazy8">
                <div class="game-card-top">
                  <span class="game-badge">Card Classic</span>
                  <span class="game-selection-pill">${getIcon('check', 12)} Selected</span>
                </div>
                <div class="game-icon-box">${getIcon('cards', 28)}</div>
                <div>
                  <div class="game-title">Crazy Eights</div>
                  <div class="game-desc">Match rank or suit, 8s wild with strategic suit calling and intelligent bot decision logic.</div>
                </div>
              </div>

              <!-- Uno Arena -->
              <div class="game-option-card" data-game="uno">
                <div class="game-card-top">
                  <span class="game-badge">Action Arena</span>
                  <span class="game-selection-pill">${getIcon('check', 12)} Selected</span>
                </div>
                <div class="game-icon-box">${getIcon('sparkles', 28)}</div>
                <div>
                  <div class="game-title">Uno Arena</div>
                  <div class="game-desc">Colors, Skips, Reverses, +2, Wild, and +4 tactical action cards with smart AI bots.</div>
                </div>
              </div>

              <!-- Ludo Master Arena -->
              <div class="game-option-card" data-game="ludo">
                <div class="game-card-top">
                  <span class="game-badge" style="border-color: rgba(245, 158, 11, 0.4); background: rgba(245, 158, 11, 0.12); color: #F59E0B;">
                    4-Colour Race
                  </span>
                  <span class="game-selection-pill">${getIcon('check', 12)} Selected</span>
                </div>
                <div class="game-icon-box">${getIcon('ludo', 28)}</div>
                <div>
                  <div class="game-title">Ludo Master</div>
                  <div class="game-desc">Strategic 4-colour race for 2-4 players. Exact finish rolls, bonus sixes, captures, and safe star sanctuaries.</div>
                </div>
              </div>
            </div>

            <!-- Khasino Setup & Rules Panel -->
            <div id="khasinoConfigPanel" class="hidden" style="margin-top: 1.5rem; padding: 1.35rem; background: var(--surface-secondary); border: 1px solid var(--border-medium); border-radius: var(--radius-md);">
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; flex-wrap: wrap; gap: 0.5rem;">
                <div style="font-weight: 700; color: var(--accent-primary); font-size: 0.95rem; display: flex; align-items: center; gap: 0.5rem;">
                  <span>${getIcon('cards', 18)}</span> South African Khasino Configuration
                </div>
                <button type="button" id="lobbyRulesGuideBtn" class="btn btn-secondary btn-sm" style="font-size: 0.8rem;">
                  ${getIcon('scroll', 14)} View Official Rules
                </button>
              </div>

              <div class="form-group" style="margin-bottom: 1rem;">
                <label class="form-label">Game Mode & Player Setup</label>
                <div class="chip-select-group" id="khasinoModeChips">
                  <button type="button" class="chip-btn active" data-mode="TWO_PLAYER" data-players="2">
                    ${getIcon('users', 14)} 2 Players (1v1, Two 10-Card Deals, 11-Pt)
                  </button>
                  <button type="button" class="chip-btn" data-mode="THREE_PLAYER" data-players="3">
                    ${getIcon('users', 14)} 3 Players (13 Cards + 1 Floor, 7-Pt)
                  </button>
                  <button type="button" class="chip-btn" data-mode="FOUR_PLAYER_SINGLES" data-players="4">
                    ${getIcon('users', 14)} 4 Players Singles (Solo 4-Way, 7-Pt)
                  </button>
                  <button type="button" class="chip-btn" data-mode="FOUR_PLAYER_PARTNERSHIPS" data-players="4">
                    ${getIcon('users', 14)} 4 Players 2v2 Partnerships (11-Pt)
                  </button>
                </div>
              </div>

              <div class="form-group" style="margin-bottom: 0;">
                <label class="form-label">AI Difficulty</label>
                <div class="chip-select-group" id="khasinoDiffChips">
                  <button type="button" class="chip-btn" data-diff="EASY">
                    <span style="color: #34d399; font-size: 0.75rem;">●</span> Easy (Casual)
                  </button>
                  <button type="button" class="chip-btn active" data-diff="MEDIUM">
                    <span style="color: #fbbf24; font-size: 0.75rem;">●</span> Medium (Strategic)
                  </button>
                  <button type="button" class="chip-btn" data-diff="HARD">
                    <span style="color: #f87171; font-size: 0.75rem;">●</span> Hard (Competitive Cupha Master)
                  </button>
                </div>
              </div>
            </div>

            <!-- Ludo Setup & Rules Panel -->
            <div id="ludoConfigPanel" class="hidden" style="margin-top: 1.5rem; padding: 1.35rem; background: var(--surface-secondary); border: 1px solid var(--border-medium); border-radius: var(--radius-md);">
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; flex-wrap: wrap; gap: 0.5rem;">
                <div style="font-weight: 700; color: #F59E0B; font-size: 0.95rem; display: flex; align-items: center; gap: 0.5rem;">
                  <span>${getIcon('ludo', 18)}</span> Ludo Match Setup & Player Count
                </div>
                <button type="button" id="lobbyLudoRulesBtn" class="btn btn-secondary btn-sm" style="font-size: 0.8rem;">
                  ${getIcon('scroll', 14)} View Official Rules
                </button>
              </div>

              <div class="form-group" style="margin-bottom: 0;">
                <label class="form-label">Supported Player Count</label>
                <div class="chip-select-group" id="ludoPlayerChips">
                  <button type="button" class="chip-btn active" data-players="2">
                    ${getIcon('users', 14)} 2 Players (Red vs Yellow)
                  </button>
                  <button type="button" class="chip-btn" data-players="3">
                    ${getIcon('users', 14)} 3 Players (Red, Green, Yellow)
                  </button>
                  <button type="button" class="chip-btn" data-players="4">
                    ${getIcon('users', 14)} 4 Players (Red, Green, Yellow, Blue)
                  </button>
                </div>
              </div>
            </div>

            <!-- Match Wager Selection -->
            <div class="form-group" style="margin-top: 1.75rem;">
              <label class="form-label">Wager Amount (Tokens)</label>
              <div class="chip-select-group" id="wagerChips">
                <button type="button" class="chip-btn" data-bet="25">${getIcon('coin', 13)} 25 Tokens</button>
                <button type="button" class="chip-btn active" data-bet="50">${getIcon('coin', 13)} 50 Tokens</button>
                <button type="button" class="chip-btn" data-bet="100">${getIcon('coin', 13)} 100 Tokens</button>
                <button type="button" class="chip-btn" data-bet="250">${getIcon('coin', 13)} 250 Tokens</button>
              </div>
            </div>

            <!-- Primary Action Buttons -->
            <div style="display: flex; gap: 1rem; margin-top: 2rem; flex-wrap: wrap;">
              <button id="joinQueueBtn" class="btn btn-primary" style="flex: 2; min-width: 220px; padding: 0.85rem 1.5rem; font-size: 1rem;">
                ${getIcon('zap', 16)} Quick Match (Queue)
              </button>
              <button id="playBotBtn" class="btn btn-secondary" style="flex: 1; min-width: 200px; padding: 0.85rem 1.25rem; font-size: 1rem;">
                ${getIcon('bot', 16)} Practice vs RobinBot
              </button>
            </div>

            <!-- Quick Match Searching Radar Box -->
            <div id="queueStatusBox" class="hidden" style="margin-top: 2rem; padding: 1.75rem; background: var(--surface-secondary); border: 1px solid var(--border-medium); border-radius: var(--radius-lg); text-align: center;">
              <div class="radar-scanner">
                <div class="radar-beam"></div>
                <div style="z-index: 2; color: var(--accent-primary); display: flex; align-items: center; justify-content: center;">
                  ${getIcon('radio', 32)}
                </div>
              </div>
              <div id="queueStatusText" style="font-weight: 700; color: var(--accent-primary); font-size: 1.1rem;">
                Searching for opponents...
              </div>
              <div style="color: var(--text-secondary); font-size: 0.88rem; margin-top: 0.35rem;">
                Game: <span id="queueGameDisplay" style="color: var(--text-primary); font-weight: 700;">Snakes & Ladders</span> • Bet: <span id="queueBetDisplay" style="color: #FBBF24; font-weight: 700;">50</span> tokens
              </div>
              <button id="cancelQueueBtn" class="btn btn-secondary btn-sm" style="margin-top: 1.25rem;">
                Cancel Matchmaking
              </button>
            </div>
          </div>

          <!-- Custom Multiplayer Rooms Panel -->
          <div class="card" style="display: flex; flex-direction: column; justify-content: space-between;">
            <div>
              <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.25rem;">
                <span style="color: var(--accent-primary); display: inline-flex;">${getIcon('room', 20)}</span>
                <h3 style="font-size: 1.15rem; font-weight: 800; color: var(--text-primary);">
                  Private Game Rooms
                </h3>
              </div>
              <p style="color: var(--text-secondary); font-size: 0.85rem; margin-bottom: 1.5rem; line-height: 1.45;">
                Create a custom room to invite friends, or enter an active room code to join their lobby.
              </p>

              <div class="form-group">
                <button id="createRoomBtn" class="btn btn-secondary" style="width: 100%; padding: 0.75rem 1rem;">
                  ${getIcon('plus', 16)} Create Custom Room
                </button>
              </div>

              <div style="display: flex; align-items: center; gap: 0.75rem; margin: 1.5rem 0;">
                <div style="flex: 1; height: 1px; background: var(--border-subtle);"></div>
                <span style="font-size: 0.72rem; text-transform: uppercase; color: var(--text-muted); font-weight: 700; letter-spacing: 0.05em;">
                  or join by code
                </span>
                <div style="flex: 1; height: 1px; background: var(--border-subtle);"></div>
              </div>

              <div class="form-group">
                <input type="text" id="joinRoomId" class="form-input" placeholder="Paste 6-Character Room Code..." style="font-family: var(--font-mono); text-transform: uppercase; letter-spacing: 0.1em; text-align: center;" />
              </div>

              <button id="joinRoomBtn" class="btn btn-primary" style="width: 100%; padding: 0.75rem 1rem;">
                Enter Room
              </button>
            </div>

            <div id="proTipBox" style="margin-top: 2rem; padding: 1rem; background: var(--surface-secondary); border-radius: var(--radius-md); border: 1px solid var(--border-subtle); font-size: 0.82rem; color: var(--text-secondary); line-height: 1.45;">
              <span style="color: var(--accent-primary); display: inline-flex; vertical-align: middle; margin-right: 4px;">${getIcon('sparkles', 15)}</span> <strong>Pro-Tip:</strong> Win up to 2x your wager! Land on ladders to zoom ahead and dodge tricky snakes.
            </div>
          </div>
        </div>

        <!-- South African Khasino Rules Guide Modal -->
        <div id="lobbyKhasinoRulesModal" class="modal-overlay hidden" style="z-index: 100;">
          <div class="modal-content" style="max-width: 780px; max-height: 85vh; overflow-y: auto; text-align: left; padding: 2rem;">
            <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-glass); padding-bottom: 1rem; margin-bottom: 1.25rem;">
              <h2 style="font-size: 1.4rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem; color: #34d399;">
                <span>${getIcon('cards', 20)}</span> South African Khasino (Kasi Kasino) Rules Guide
              </h2>
              <button type="button" id="closeLobbyRulesBtn" class="btn btn-secondary" style="font-size: 0.85rem; padding: 0.35rem 0.75rem;">${getIcon('x', 14)} Close</button>
            </div>

            <div style="font-size: 0.9rem; color: var(--text-secondary); line-height: 1.6; display: flex; flex-direction: column; gap: 1.25rem;">
              <div style="background: rgba(16, 185, 129, 0.08); border-left: 3px solid #10b981; padding: 0.85rem 1rem; border-radius: 4px;">
                <strong>Authentic South African Rules (KASA):</strong> Khasino is South Africa's premier indigenous card game played strictly with a stripped 40-card deck (Aces to 10s; no picture cards).
              </div>

              <div>
                <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">1. Deck & Player Setup</h4>
                <p>• <strong>Deck:</strong> Exactly 40 cards (Spades, Hearts, Diamonds, Clubs; Ranks: A, 2, 3, 4, 5, 6, 7, 8, 9, 10). Ace = 1.<br>
                   • <strong>2-Player:</strong> 10 cards each in Deal 1 (0 floor cards). Once played out, a second 10-card deal is dealt. (11-pt mode).<br>
                   • <strong>3-Player:</strong> 13 cards each, 1 face-up card on floor. (7-pt mode).<br>
                   • <strong>4-Player Singles:</strong> 10 cards each, 0 floor cards. (7-pt mode).<br>
                   • <strong>4-Player 2v2 Partnerships:</strong> 10 cards each, Team 1 (Seats 0 & 2) vs Team 2 (Seats 1 & 3). (11-pt mode).</p>
              </div>

              <div>
                <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">2. Capturing (Chow) & Sweeps</h4>
                <p>• Play 1 card from hand to capture matching floor cards, or combinations of cards that sum to your card's value.<br>
                   • You can capture multiple groups simultaneously with one played card (e.g. played 7 captures a 7 AND a 3+4).<br>
                   • <strong>Sweep:</strong> If your capture clears all floor cards and builds from the table, you score +1 Sweep point!</p>
              </div>

              <div>
                <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">3. Simple Builds vs Compound Builds</h4>
                <p>• <strong>Simple Build:</strong> Combine floor cards with 1 card from hand into a declared value (&le; 10). <em>Crucial rule:</em> You must retain another card in your hand matching the declared value! Small card rests on top.<br>
                   • <strong>Compound (Augmented) Build:</strong> Add another valid group independently summing to the same capture value. <em>Compound builds lock their capture value permanently</em>—opponents cannot alter a compound build's value!</p>
              </div>

              <div>
                <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">4. Changing Opponent's Build Value</h4>
                <p>• You may steal an opponent's simple build by adding 1 card from your hand to increase its value (up to 10), provided you hold the card matching the new total in your hand. Ownership immediately transfers to you!</p>
              </div>

              <div>
                <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">5. Drifting, Stealing & Cupha</h4>
                <p>• <strong>Drift (Trail):</strong> Place 1 card onto the floor without capturing. In 2-player Deal 1, a player who owns an uncaptured build on the floor cannot drift; in Deal 2, drifting is allowed.<br>
                   • <strong>Stealing Exposed Captured Cards:</strong> You may steal the top exposed card from an opponent's captured pile to augment an eligible build.<br>
                   • <strong>Cupha (Entrapment):</strong> You are not forced to capture immediately; strategically declining captures to lure opponents into traps is fully supported.</p>
              </div>

              <div>
                <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">6. Scoring Systems</h4>
                <div style="background: var(--surface-glass); border: 1px solid var(--border-glass); border-radius: var(--radius-sm); padding: 0.75rem; margin-top: 0.5rem;">
                  <table style="width: 100%; border-collapse: collapse; font-size: 0.85rem;">
                    <tr style="border-bottom: 1px solid var(--border-glass); color: #34d399; font-weight: 700;">
                      <th style="padding: 4px 8px; text-align: left;">Scoring Item</th>
                      <th style="padding: 4px 8px; text-align: center;">Points</th>
                      <th style="padding: 4px 8px; text-align: left;">Notes</th>
                    </tr>
                    <tr><td style="padding: 4px 8px;">10 of Diamonds (Mummy)</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">2</td><td>Decides match tiebreakers!</td></tr>
                    <tr><td style="padding: 4px 8px;">2 of Spades (Spy Two)</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">1</td><td>The little casino spade</td></tr>
                    <tr><td style="padding: 4px 8px;">Each Ace</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">1 ea</td><td>4 points total across all aces</td></tr>
                    <tr><td style="padding: 4px 8px;">Most Cards (21+)</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">2</td><td>11-pt modes only (1-1 on 20-20 tie)</td></tr>
                    <tr><td style="padding: 4px 8px;">Most Spades (6+)</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">2</td><td>11-pt modes only (1-1 on 5-5 tie)</td></tr>
                    <tr><td style="padding: 4px 8px;">Each Sweep</td><td style="padding: 4px 8px; text-align: center; color: #fbbf24; font-weight: 700;">1 ea</td><td>Awarded on board clears</td></tr>
                  </table>
                </div>
              </div>
            </div>

            <div style="margin-top: 1.5rem; text-align: right;">
              <button type="button" id="closeLobbyRulesBottomBtn" class="btn btn-primary" style="padding: 0.5rem 1.5rem;">
                Got it, Let's Play!
              </button>
            </div>
          </div>
        </div>

        <!-- Ludo Rules Guide Modal -->
        <div id="lobbyLudoRulesModal" class="modal-overlay hidden" style="z-index: 100;">
          <div class="modal-content" style="max-width: 780px; max-height: 85vh; overflow-y: auto; text-align: left; padding: 2rem;">
            <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-glass); padding-bottom: 1rem; margin-bottom: 1.25rem;">
              <h2 style="font-size: 1.4rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem; color: #F59E0B;">
                <span>${getIcon('ludo', 20)}</span> Ludo Master Arena Rules Guide
              </h2>
              <button type="button" id="closeLobbyLudoRulesBtn" class="btn btn-secondary" style="font-size: 0.85rem; padding: 0.35rem 0.75rem;">${getIcon('x', 14)} Close</button>
            </div>

            <div style="font-size: 0.9rem; color: var(--text-secondary); line-height: 1.6; display: flex; flex-direction: column; gap: 1.25rem;">
              <div style="background: rgba(245, 158, 11, 0.08); border-left: 3px solid #F59E0B; padding: 0.85rem 1rem; border-radius: 4px;">
                <strong>Conventional Four-Colour Ludo:</strong> Authoritative backend movement along a 52-square outer circuit with safe stars, colored private home columns, bonus sixes, and exact-roll home finish.
              </div>

              <div>
                <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">1. Starting Tokens & Exiting the Yard</h4>
                <p>• Every player starts with 4 tokens inside their colored yard.<br>
                   • Rolling a <strong>6</strong> allows you to enter a token onto your color's starting square (or advance a token already in play).<br>
                   • Rolls of 1 to 5 cannot move tokens that are in the yard.</p>
              </div>

              <div>
                <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">2. Rolling, Bonus Turns & 3 Consecutive Sixes</h4>
                <p>• Rolling a <strong>6 grants an extra roll</strong> on the same turn.<br>
                   • If you roll <strong>three consecutive sixes</strong> in a single turn, the third six is penalized and your turn immediately ends without making a move.<br>
                   • When a roll offers no legal moves, the turn cleanly advances to the next player.</p>
              </div>

              <div>
                <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">3. Captures & Safe Squares</h4>
                <p>• Landing on an opponent's token on any standard track square <strong>captures it</strong>, sending it back to their yard.<br>
                   • <strong>Safe Squares:</strong> The 4 starting squares and the 4 star squares (marked with sanctuary stars) are safe. Tokens cannot be captured on safe squares.<br>
                   • Friendly tokens never capture each other.</p>
              </div>

              <div>
                <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.35rem;">4. Home Columns & Victory Condition</h4>
                <p>• After navigating the 52-square track, tokens turn into their color's private 5-cell home column.<br>
                   • Opponents cannot enter your home column.<br>
                   • Reaching the central Home requires an <strong>exact dice roll</strong>.<br>
                   • The first player to bring all <strong>4 tokens</strong> into the Home center wins the match!</p>
              </div>
            </div>

            <div style="margin-top: 1.75rem; text-align: right; border-top: 1px solid var(--border-glass); padding-top: 1rem;">
              <button type="button" id="closeLobbyLudoRulesBottomBtn" class="btn btn-primary" style="padding: 0.6rem 1.25rem;">
                Got It, Let's Play!
              </button>
            </div>
          </div>
        </div>
      </div>
    `;

    bindNavTabs(container);

    const gameNames = {
      snl: 'Snakes & Ladders',
      crazy8: 'Crazy Eights',
      uno: 'Uno Arena',
      khasino: 'South African Khasino (Kasi Kasino)',
      ludo: 'Ludo Master Arena'
    };

    const proTips = {
      snl: `${getIcon('sparkles', 15)} <strong>Pro-Tip:</strong> Win up to 2x your wager! Land on ladders to zoom ahead and dodge tricky snakes.`,
      crazy8: `${getIcon('sparkles', 15)} <strong>Crazy 8s Tip:</strong> Save your 8s for when you have no valid suit or rank to call the dominant suit in your hand!`,
      uno: `${getIcon('sparkles', 15)} <strong>Uno Tip:</strong> Disrupt opponent turns using Skip and Reverse cards. Chain +2 and +4 cards to force heavy card draws!`,
      khasino: `${getIcon('sparkles', 15)} <strong>Khasino Pro-Tip:</strong> 10 of Diamonds (Mummy) is worth 2 points, 2 of Spades (Spy Two) is worth 1 point! Hold matching cards to Chow builds or combine floor cards.`,
      ludo: `${getIcon('sparkles', 15)} <strong>Ludo Tip:</strong> Roll a 6 to bring tokens out of your yard! Land on opponent pieces to send them back, and seek refuge on safe star squares.`
    };

    let selectedKhasinoMode = 'TWO_PLAYER';
    let selectedKhasinoPlayers = 2;
    let selectedKhasinoDiff = 'MEDIUM';
    let selectedLudoPlayers = 2;

    // Game card selection
    document.querySelectorAll('#gameOptions .game-option-card').forEach(card => {
      card.onclick = () => {
        document.querySelectorAll('#gameOptions .game-option-card').forEach(c => c.classList.remove('active'));
        card.classList.add('active');
        selectedGame = card.getAttribute('data-game');
        const tipEl = document.getElementById('proTipBox');
        if (tipEl && proTips[selectedGame]) {
          tipEl.innerHTML = proTips[selectedGame];
        }

        const khasinoPanel = document.getElementById('khasinoConfigPanel');
        if (khasinoPanel) {
          if (selectedGame === 'khasino') {
            khasinoPanel.classList.remove('hidden');
          } else {
            khasinoPanel.classList.add('hidden');
          }
        }

        const ludoPanel = document.getElementById('ludoConfigPanel');
        if (ludoPanel) {
          if (selectedGame === 'ludo') {
            ludoPanel.classList.remove('hidden');
          } else {
            ludoPanel.classList.add('hidden');
          }
        }
      };
    });

    // Khasino mode selection
    document.querySelectorAll('#khasinoModeChips .chip-btn').forEach(btn => {
      btn.onclick = () => {
        document.querySelectorAll('#khasinoModeChips .chip-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        selectedKhasinoMode = btn.getAttribute('data-mode');
        selectedKhasinoPlayers = parseInt(btn.getAttribute('data-players'), 10);
      };
    });

    // Khasino difficulty selection
    document.querySelectorAll('#khasinoDiffChips .chip-btn').forEach(btn => {
      btn.onclick = () => {
        document.querySelectorAll('#khasinoDiffChips .chip-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        selectedKhasinoDiff = btn.getAttribute('data-diff');
      };
    });

    // Rules modal triggers
    const rulesModal = document.getElementById('lobbyKhasinoRulesModal');
    const openRulesBtn = document.getElementById('lobbyRulesGuideBtn');
    const closeRulesBtn = document.getElementById('closeLobbyRulesBtn');
    const closeRulesBottomBtn = document.getElementById('closeLobbyRulesBottomBtn');

    if (openRulesBtn && rulesModal) {
      openRulesBtn.onclick = () => rulesModal.classList.remove('hidden');
    }
    if (closeRulesBtn && rulesModal) {
      closeRulesBtn.onclick = () => rulesModal.classList.add('hidden');
    }
    if (closeRulesBottomBtn && rulesModal) {
      closeRulesBottomBtn.onclick = () => rulesModal.classList.add('hidden');
    }

    // Ludo Player count selection
    document.querySelectorAll('#ludoPlayerChips .chip-btn').forEach(btn => {
      btn.onclick = () => {
        document.querySelectorAll('#ludoPlayerChips .chip-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        selectedLudoPlayers = parseInt(btn.getAttribute('data-players'), 10);
      };
    });

    // Ludo Rules modal triggers
    const ludoRulesModal = document.getElementById('lobbyLudoRulesModal');
    const openLudoRulesBtn = document.getElementById('lobbyLudoRulesBtn');
    const closeLudoRulesBtn = document.getElementById('closeLobbyLudoRulesBtn');
    const closeLudoRulesBottomBtn = document.getElementById('closeLobbyLudoRulesBottomBtn');

    if (openLudoRulesBtn && ludoRulesModal) {
      openLudoRulesBtn.onclick = () => ludoRulesModal.classList.remove('hidden');
    }
    if (closeLudoRulesBtn && ludoRulesModal) {
      closeLudoRulesBtn.onclick = () => ludoRulesModal.classList.add('hidden');
    }
    if (closeLudoRulesBottomBtn && ludoRulesModal) {
      closeLudoRulesBottomBtn.onclick = () => ludoRulesModal.classList.add('hidden');
    }

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
    const queueGameDisplay = document.getElementById('queueGameDisplay');
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
      window.showToast(`Match found! Starting ${gameNames[selectedGame] || 'game'}...`, 'success');
      const { loadGameUI } = await import('./game.js');
      loadGameUI(sessionId, token, user);
    }

    // Quick Match Queue
    joinQueueBtn.onclick = async () => {
      joinQueueBtn.disabled = true;
      playBotBtn.disabled = true;
      queueBox.classList.remove('hidden');
      if (queueBetDisplay) queueBetDisplay.textContent = selectedBet;
      if (queueGameDisplay) queueGameDisplay.textContent = gameNames[selectedGame] || selectedGame;
      queueStatusText.textContent = `Entering ${gameNames[selectedGame] || ''} matchmaking queue...`;

      try {
        const joinRes = await fetch('/games/queue/join', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ game: selectedGame, bet: selectedBet })
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

        queueStatusText.textContent = 'Searching for opponents or pairing with RobinBot...';
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
              body: JSON.stringify({ game: selectedGame, bet: selectedBet })
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
        }, 1000);

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
        // Ensure user has sufficient balance for practice
        try {
          const balRes = await fetch('/wallet/balance', { headers: { Authorization: `Bearer ${token}` } });
          if (balRes.ok) {
            const balData = await balRes.json();
            if (balData.balance < selectedBet) {
              await fetch('/deposit', {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: '200' })
              });
              if (window.updateHeaderBalance) window.updateHeaderBalance();
            }
          }
        } catch (ignored) {}

        const payload = {
          game: selectedGame,
          maxPlayers: String(selectedGame === 'khasino' ? selectedKhasinoPlayers : (selectedGame === 'ludo' ? selectedLudoPlayers : 2)),
          bet: String(selectedBet)
        };
        if (selectedGame === 'khasino') {
          payload.khasinoMode = selectedKhasinoMode;
          payload.difficulty = selectedKhasinoDiff;
        }

        const res = await fetch('/create-game', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (data.sessionId) {
          // Start match
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

    // --- Waiting Room Lobby Implementation ---
    let currentRoomSse = null;

    function showWaitingRoomModal(initialRoom) {
      let room = initialRoom;

      const existing = document.getElementById('waitingRoomModal');
      if (existing) existing.remove();

      const modal = document.createElement('div');
      modal.id = 'waitingRoomModal';
      modal.className = 'modal-overlay';
      modal.style.zIndex = '150';

      const gameDisplayName = gameNames[room.gameType.toLowerCase()] || room.gameType;

      function renderWaitingRoomContent() {
        const isHost = (room.hostId === user);
        const myMember = room.members.find(m => m.playerId === user);
        const isReady = myMember ? myMember.isReady : false;
        const allReady = room.members.length >= 2 && room.members.every(m => m.isHost || m.isReady);

        let memberRowsHtml = '';
        for (let i = 0; i < room.maxPlayers; i++) {
          const m = room.members.find(mem => mem.seatIndex === i) || room.members[i];
          if (m) {
            const teamBadge = (room.khasinoMode === 'FOUR_PLAYER_PARTNERSHIPS') ?
              `<span class="team-pill ${m.teamId === 1 ? 'team-1-pill' : 'team-2-pill'}">Team ${m.teamId}</span>` : '';
            const hostBadge = m.isHost ? `<span style="color: #fbbf24; display: inline-flex;" title="Room Host">${getIcon('crown', 14)}</span>` : '';
            const readyBadge = m.isHost ?
              `<span class="status-tag ready">${getIcon('crown', 12)} Host</span>` :
              (m.isReady ? `<span class="status-tag ready"><span style="color: #34d399; margin-right: 4px;">●</span> Ready</span>` : `<span class="status-tag not-ready"><span style="color: #fbbf24; margin-right: 4px;">●</span> Waiting</span>`);

            memberRowsHtml += `
              <div class="participant-item ${m.isHost ? 'is-host' : ''}">
                <div style="display: flex; align-items: center; gap: 0.75rem;">
                  <div class="user-avatar" style="width: 34px; height: 34px; font-size: 0.9rem;">
                    ${m.playerId.substring(0, 1).toUpperCase()}
                  </div>
                  <div>
                    <div style="font-weight: 700; font-size: 0.9rem; color: #ffffff; display: flex; align-items: center; gap: 0.35rem;">
                      ${m.playerId} ${hostBadge}
                    </div>
                    <div style="font-size: 0.75rem; color: var(--text-muted);">
                      Seat ${i + 1} ${teamBadge}
                    </div>
                  </div>
                </div>
                <div>${readyBadge}</div>
              </div>
            `;
          } else {
            const teamBadge = (room.khasinoMode === 'FOUR_PLAYER_PARTNERSHIPS') ?
              `<span class="team-pill ${(i % 2 === 0) ? 'team-1-pill' : 'team-2-pill'}">Team ${(i % 2 === 0) ? 1 : 2}</span>` : '';
            memberRowsHtml += `
              <div class="participant-item empty-slot">
                <div style="display: flex; align-items: center; gap: 0.75rem;">
                  <div class="user-avatar" style="width: 34px; height: 34px; font-size: 0.85rem; background: rgba(255,255,255,0.05); color: var(--text-muted);">
                    ${i + 1}
                  </div>
                  <div>
                    <div style="font-style: italic; font-size: 0.85rem; color: var(--text-muted);">
                      Waiting for player to join...
                    </div>
                    <div style="font-size: 0.75rem; color: var(--text-muted);">
                      Seat ${i + 1} ${teamBadge}
                    </div>
                  </div>
                </div>
                <div style="font-size: 0.75rem; color: var(--text-muted);">Open Slot</div>
              </div>
            `;
          }
        }

        modal.innerHTML = `
          <div class="waiting-room-card">
            <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
              <div>
                <span class="game-badge" style="background: rgba(16, 185, 129, 0.15); color: #34d399; margin-bottom: 0.35rem;">
                  ${getIcon('gamepad', 14)} Live Waiting Lobby
                </span>
                <h2 style="font-size: 1.5rem; font-weight: 800; color: #ffffff;">
                  ${gameDisplayName}
                </h2>
                <div style="font-size: 0.85rem; color: var(--text-secondary); margin-top: 0.25rem;">
                  Wager: <strong style="color: #fbbf24;">${room.wagerAmount} ${getIcon('coin', 12)}</strong> • Max Players: <strong>${room.maxPlayers}</strong>
                  ${room.khasinoMode ? ' • Mode: <strong style="color: #34d399;">' + room.khasinoMode + '</strong>' : ''}
                </div>
              </div>
              <button id="closeWaitingRoomBtn" class="btn btn-secondary" style="font-size: 0.8rem; padding: 0.3rem 0.65rem;">
                ${getIcon('x', 14)} Close
              </button>
            </div>

            <!-- Room Code with 1-click Copy -->
            <div style="text-align: center; margin: 1rem 0;">
              <div style="font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); margin-bottom: 0.25rem;">
                Share this Room Code with friends
              </div>
              <div class="room-code-container">
                <span class="room-code-badge" id="lobbyRoomCodeVal">${room.roomCode}</span>
                <button type="button" id="copyCodeBtn" class="btn btn-secondary" style="font-size: 0.8rem; padding: 0.4rem 0.85rem;">
                  ${getIcon('scroll', 14)} Copy Code
                </button>
              </div>
            </div>

            <!-- Participant List -->
            <div style="margin: 1.25rem 0;">
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
                <span style="font-size: 0.8rem; font-weight: 700; text-transform: uppercase; color: var(--text-secondary);">
                  Participants (${room.members.length} / ${room.maxPlayers})
                </span>
                <span style="font-size: 0.75rem; color: ${allReady ? '#34d399' : '#fbbf24'}; font-weight: 700; display: inline-flex; align-items: center; gap: 4px;">
                  ${allReady ? getIcon('check', 14) + ' All Players Ready' : 'Waiting for ready status...'}
                </span>
              </div>
              <div class="participant-list">
                ${memberRowsHtml}
              </div>
            </div>

            <!-- Actions Footer -->
            <div style="display: flex; gap: 0.75rem; margin-top: 1.5rem; flex-wrap: wrap;">
              ${isHost ? `
                <button id="hostStartMatchBtn" class="btn btn-primary" style="flex: 2; min-width: 180px;" ${allReady ? '' : 'disabled'}>
                  Start Match (${room.members.length}/${room.maxPlayers})
                </button>
                <button id="hostBotFillBtn" class="btn btn-violet" style="flex: 1; min-width: 150px;">
                  ${getIcon('bot', 16)} Fill Bots & Start
                </button>
                <button id="hostDisbandBtn" class="btn btn-secondary" style="padding: 0.6rem 1rem;">
                  Disband
                </button>
              ` : `
                <button id="guestToggleReadyBtn" class="btn ${isReady ? 'btn-secondary' : 'btn-primary'}" style="flex: 2; min-width: 180px;">
                  ${isReady ? getIcon('check', 14) + ' Ready (Click to cancel)' : 'Ready Up!'}
                </button>
                <button id="guestLeaveBtn" class="btn btn-secondary" style="flex: 1; min-width: 120px;">
                  Leave Room
                </button>
              `}
            </div>
          </div>
        `;

        const copyBtn = modal.querySelector('#copyCodeBtn');
        if (copyBtn) {
          copyBtn.onclick = () => {
            navigator.clipboard.writeText(room.roomCode).then(() => {
              window.showToast('Room Code copied to clipboard: ' + room.roomCode, 'success');
            }).catch(() => {
              window.showToast('Room Code: ' + room.roomCode, 'info');
            });
          };
        }

        const closeBtn = modal.querySelector('#closeWaitingRoomBtn');
        if (closeBtn) {
          closeBtn.onclick = () => {
            leaveAndCloseRoom();
          };
        }

        if (isHost) {
          const startBtn = modal.querySelector('#hostStartMatchBtn');
          if (startBtn) {
            startBtn.onclick = async () => {
              startBtn.disabled = true;
              startBtn.textContent = 'Launching Match...';
              try {
                const res = await fetch(`/rooms/${room.id}/start`, {
                  method: 'POST',
                  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                  body: JSON.stringify({ allowBotFill: false })
                });
                const data = await res.json();
                if (data.sessionId) {
                  cleanupRoomSSE();
                  modal.remove();
                  launchGame(data.sessionId);
                } else {
                  window.showToast(data.error || 'Failed to start match', 'error');
                  startBtn.disabled = false;
                  startBtn.textContent = `▶️ Start Match (${room.members.length}/${room.maxPlayers})`;
                }
              } catch (err) {
                window.showToast('Network error starting match', 'error');
                startBtn.disabled = false;
              }
            };
          }

          const botFillBtn = modal.querySelector('#hostBotFillBtn');
          if (botFillBtn) {
            botFillBtn.onclick = async () => {
              botFillBtn.disabled = true;
              botFillBtn.textContent = 'Filling Bots...';
              try {
                const res = await fetch(`/rooms/${room.id}/start`, {
                  method: 'POST',
                  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                  body: JSON.stringify({ allowBotFill: true })
                });
                const data = await res.json();
                if (data.sessionId) {
                  cleanupRoomSSE();
                  modal.remove();
                  launchGame(data.sessionId);
                } else {
                  window.showToast(data.error || 'Failed to start with bots', 'error');
                  botFillBtn.disabled = false;
                }
              } catch (err) {
                window.showToast('Network error starting match', 'error');
                botFillBtn.disabled = false;
              }
            };
          }

          const disbandBtn = modal.querySelector('#hostDisbandBtn');
          if (disbandBtn) {
            disbandBtn.onclick = () => {
              leaveAndCloseRoom();
            };
          }
        } else {
          const toggleReadyBtn = modal.querySelector('#guestToggleReadyBtn');
          if (toggleReadyBtn) {
            toggleReadyBtn.onclick = async () => {
              toggleReadyBtn.disabled = true;
              try {
                const res = await fetch(`/rooms/${room.id}/ready`, {
                  method: 'POST',
                  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                  body: JSON.stringify({ ready: !isReady })
                });
                const data = await res.json();
                if (data.room) {
                  room = data.room;
                  renderWaitingRoomContent();
                }
              } catch (err) {
                window.showToast('Network error updating ready status', 'error');
              }
            };
          }

          const leaveBtn = modal.querySelector('#guestLeaveBtn');
          if (leaveBtn) {
            leaveBtn.onclick = () => {
              leaveAndCloseRoom();
            };
          }
        }
      }

      function cleanupRoomSSE() {
        if (currentRoomSse) {
          currentRoomSse.close();
          currentRoomSse = null;
        }
      }

      function leaveAndCloseRoom() {
        cleanupRoomSSE();
        fetch(`/rooms/${room.id}/leave`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
        }).catch(console.warn);
        modal.remove();
        window.showToast('Left waiting room', 'info');
      }

      cleanupRoomSSE();
      currentRoomSse = new EventSource(`/events?roomId=${room.id}&token=${token}`);
      currentRoomSse.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'room_update' && data.room) {
            room = data.room;
            renderWaitingRoomContent();
          } else if (data.type === 'match_start') {
            cleanupRoomSSE();
            modal.remove();
            launchGame(data.sessionId);
          }
        } catch (e) {
          console.warn('Error parsing room SSE event:', e);
        }
      };

      renderWaitingRoomContent();
      document.body.appendChild(modal);
    }

    // Create custom room
    document.getElementById('createRoomBtn').onclick = async () => {
      try {
        const payload = {
          game: selectedGame,
          maxPlayers: String(selectedGame === 'khasino' ? selectedKhasinoPlayers : (selectedGame === 'ludo' ? selectedLudoPlayers : 2)),
          wager: String(selectedBet),
          isBotMatch: false
        };
        if (selectedGame === 'khasino') {
          payload.khasinoMode = selectedKhasinoMode;
          payload.aiDifficulty = selectedKhasinoDiff;
        }

        const res = await fetch('/rooms/create', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (data.room) {
          showWaitingRoomModal(data.room);
        } else {
          window.showToast(data.error || 'Failed to create room', 'error');
        }
      } catch (e) {
        window.showToast('Failed to create room', 'error');
      }
    };

    // Join room
    document.getElementById('joinRoomBtn').onclick = async () => {
      const code = document.getElementById('joinRoomId').value.trim();
      if (!code) return window.showToast('Please enter a 6-character room code or ID', 'error');

      try {
        const res = await fetch('/rooms/join', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ roomCode: code })
        });
        const data = await res.json();
        if (data.room) {
          showWaitingRoomModal(data.room);
        } else {
          window.showToast(data.error || 'Cannot join room', 'error');
        }
      } catch (e) {
        window.showToast('Failed to join room', 'error');
      }
    };
  }
};
