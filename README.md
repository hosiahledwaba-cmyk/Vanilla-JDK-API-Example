# RobinHood. Vanilla JDK Real-Time Multiplayer Gaming Platform

[![Java](https://img.shields.io/badge/Java-21%2B%20(Pure%20JDK)-orange?logo=openjdk)](https://openjdk.org/)
[![Database](https://img.shields.io/badge/Database-PostgreSQL%2016-blue?logo=postgresql)](https://www.postgresql.org/)
[![Frontend](https://img.shields.io/badge/Frontend-Vanilla%20HTML5%20%2F%20CSS3%20%2F%20ES6-yellow?logo=javascript)](https://developer.mozilla.org/en-US/docs/Web/JavaScript)
[![Architecture](https://img.shields.io/badge/Architecture-Zero%20Frameworks-green)](#architecture-philosophy)
[![Icons](https://img.shields.io/badge/Iconography-Pure%20Vector%20SVG-darkblue)](#universal-vector-svg-icon-system)

RobinHood is a high-performance, real-time multiplayer gaming platform engineered with **zero external web frameworks**, running entirely on standard **Vanilla JDK 21+** (`com.sun.net.httpserver`, virtual threads, raw JDBC connection pooling) and a contemporary, glassmorphic **Vanilla HTML5/CSS3/JavaScript (ES6+)** single-page web client with an integrated vector SVG design system.

---

## Supported Game Modes

RobinHood features five fully authoritative game modes, complete with custom state-machine engines, deterministic AI opponents, real-time Server-Sent Events (SSE) synchronization, and PostgreSQL persistence:

| Game Mode | Players | Engine Mechanics & Rules | Match Types |
| :--- | :---: | :--- | :--- |
| **Ludo Master Arena** | 2, 3, or 4 | 15×15 canonical grid, 52-tile outer loop, safe sanctuary squares (`★`), yard exit gating (requires rolling a 6), bonus turns on 6, 3-consecutive-six penalty forfeiture, friendly stacking, opponent captures back to yard, 5-tile color home stretch, exact finish roll (tile 56). | Practice vs AI Bot, Quick Match, Custom Room |
| **South African Khasino (Kasi Kasino)** | 2 or 4 | Traditional fishing card game: single and 2v2 partnership play, building numerical sets, augmentations, trailing/drifting, value changes, sweeps, and itemized scoring (Big Casino 10♦, Little Casino 2♠, Most Cards, Most Spades, Aces). | Practice vs AI Bot, Quick Match, Custom Room |
| **Crazy Eights** | 2 to 4 | Classic shedding card game: rank/suit matching, 8s wild with suit declaration, draw accumulation, and hand penalty scoring. | Practice vs AI Bot, Quick Match, Custom Room |
| **Uno** | 2 to 4 | Modern color-shedding: color and number matching, Skip, Reverse, Draw Two, Wild, Wild Draw Four, and penalty accumulation. | Practice vs AI Bot, Quick Match, Custom Room |
| **Snakes & Ladders** | 2 to 4 | 100-cell board race with sliding snakes, climbing ladders, authoritative rolling dice, and exact finish requirements. | Practice vs AI Bot, Quick Match, Custom Room |

---

## Universal Vector SVG Icon System

To ensure a refined, high-end aesthetic across high-DPI displays, operating systems, and dark-mode themes, the platform contains **zero platform-dependent unicode emojis or third-party icon fonts**.

All UI elements utilize an integrated, dependency-free vector icon module ([`client/web vanilla/scripts/icons.js`](client/web%20vanilla/scripts/icons.js)):
- **SVG Vector Icons (`getIcon`)**: 24×24 stroke-based scalable graphics styled with `stroke="currentColor"`, `stroke-width="2"`, and smooth line joins. Icons automatically inherit CSS colors from theme tokens.
- **Dynamic Vector Dice (`getSvgDice`)**: Algorithmic vector dice renderer generating geometric rounded borders and precise pip coordinate layouts (1 through 6) for Ludo and Snakes & Ladders, replacing unreliable unicode dice characters (`['⚀', '⚁', '⚂', '⚃', '⚄', '⚅']`).
- **Physical Playing Card Faces**: Card faces maintain standard single-character playing card suits (`♥, ♦, ♣, ♠`) inside dedicated CSS playing card containers, while all UI buttons, suit pickers, status badges, and table trackers render crisp vector SVG icons (`heart`, `diamondSuit`, `club`, `spade`).

---

## Architecture Philosophy

```
┌─────────────────────────────────────────────────────────────┐
│                 Vanilla ES6+ Web Client                     │
│    (Glassmorphic CSS3, Responsive Canvas/Grid, SSE Client)  │
│    - Universal Vector SVG System (scripts/icons.js)         │
│    - Swagger / OpenAPI Telemetry Explorer (docs.html)       │
└──────────────────────────────▲──────────────────────────────┘
                               │ HTTP / SSE
┌──────────────────────────────▼──────────────────────────────┐
│                    RobinHood HTTP Server                    │
│     Built on JDK com.sun.net.httpserver & Virtual Threads   │
├──────────────────────────────┬──────────────────────────────┤
│  REST Router & Controllers   │   RealTimeDispatcher (SSE)   │
│  - /auth, /wallet, /games    │   - State Broadcasts         │
│  - Matchmaker & RoomManager  │   - Connection Heartbeats    │
│  - Ozow Payment Gateway      │   - Match Event Streams      │
├──────────────────────────────┴──────────────────────────────┤
│                 Core Game Engine Library                    │
│  - Authoritative State Machines (Ludo, Khasino, Uno, etc.)  │
│  - Deterministic AI Decision Engines (RobinBot)             │
├─────────────────────────────────────────────────────────────┤
│                  Data Persistence Layer                     │
│  - Raw JDBC Connection Pooling (DBManager)                  │
│  - PostgreSQL 16 (Users, Sessions, Rooms, History, Wallet)  │
└─────────────────────────────────────────────────────────────┘
```

1. **Pure Vanilla JDK (Backend)**: No Spring Boot, Quarkus, Micronaut, or Jakarta EE. Every route, JSON serializer, JWT verifier, and HTTP handler runs directly on Java standard libraries.
2. **Authoritative Rules Engines**: Clients never dictate dice values, card draws, legal moves, or win conditions. Actions are submitted as intent requests, validated by backend state machines, and broadcasted to players.
3. **Deterministic AI (RobinBot)**: Every game mode includes built-in bot intelligence executing the exact same authoritative API flows as human players.
4. **Real-Time Multiplayer (SSE)**: Built-in `RealTimeDispatcher` streams state transitions using lightweight Server-Sent Events (SSE).
5. **No Frontend Frameworks**: No React, Vue, Angular, or Tailwind. Pure CSS Custom Properties, flexbox/grid layouts, responsive breakpoints, and modular ES6 imports.

---

## Detailed Game Engine Rules

### 1. Ludo Master Arena (`com.robinhood.engine.ludo`)
- **Grid Layout**: 15×15 canonical grid with four colored yard quadrants, a 52-tile outer loop, home stretches (5 tiles per color), and a central finish triangle.
- **Color Assignments**:
  - 2 Players: Red vs. Yellow (diametrically opposed).
  - 3 Players: Red, Green, Yellow.
  - 4 Players: Red, Green, Yellow, Blue.
- **Yard Mobilization**: Tokens remain in the yard until a **6** is rolled, which mobilizes a token to that color's starting tile (`Track 0` for Red, `13` for Green, `26` for Yellow, `39` for Blue).
- **Consecutive Sixes Rule**: Rolling a 6 grants a bonus turn. If a player rolls **three consecutive sixes**, the turn is immediately forfeited and passes to the next player.
- **Sanctuaries (Safe Squares)**: Track squares `0, 8, 13, 21, 26, 34, 39, 47` are marked with `★`. Tokens on safe squares are immune to capture. Multiple tokens (including opposing tokens) may co-exist on safe squares.
- **Captures**: Landing on an opponent's token on any standard track tile captures it, immediately returning it to its owner's yard (`step = -1`).
- **Exact Finish Roll**: Moving into the home column requires the exact roll to land on square 56. Overshooting rolls are invalid.
- **Automatic Turn Advancement**: If no legal moves can be made with the rolled value, the turn advances automatically.

### 2. South African Khasino (`com.robinhood.engine.khasino`)
- **Card Distribution**: Standard 52-card deck dealt to 2 or 4 players (singles or 2v2 partnerships) with 4 loose floor cards.
- **Player Actions**:
  - **Chow (Capture)**: Take floor cards matching the played card's face value or sums matching the card (e.g., played 8 captures a 5 and 3).
  - **Build**: Combine a hand card with a floor card to create a declared numeric value for a future capture.
  - **Augment Build**: Add hand cards to an existing build to increase its declared value (if permitted by rules).
  - **Change Build Value**: Modify an existing single build if holding the matching capture card.
  - **Drift (Trail)**: Place a card on the floor when unable or choosing not to build or capture.
  - **Sweep**: Clearing all cards from the table awards a Sweep point bonus.
- **Itemized End-Game Scoring**:
  - Most Cards (3 points)
  - Most Spades (1 point)
  - Big Casino / Mummy (10 of Diamonds — 2 points)
  - Little Casino / Spy (2 of Spades — 1 point)
  - Aces (1 point each — up to 4 points)
  - Sweeps (1 point each)

### 3. Crazy Eights & Uno (`com.robinhood.engine.crazyeight`, `com.robinhood.engine.uno`)
- **Turn Flow**: Discard matching current top card by rank/suit (Crazy Eights) or color/value (Uno).
- **Special Actions**:
  - Eights / Wild Cards: Change active playing suit/color via the vector suit modal.
  - Uno Actions: Skip turn, Reverse player order, Draw Two, Wild Draw Four.
- **Draw Penalty**: Automatic draw accumulation when no legal card can be played.

### 4. Snakes & Ladders (`com.robinhood.engine.snakesandladders`)
- **100-Tile Board**: Ascending zigzag grid from Tile 1 to Tile 100.
- **Ladders**: Transport tokens upward to higher tiles upon landing on the base.
- **Snakes**: Slide tokens downward upon landing on the snake's head.
- **Exact Finish**: Reaching Tile 100 requires an exact roll; overshooting bounces back.

---

## Payments & Wallet Integration

The platform includes a dedicated virtual wallet system with real-time balance tracking and payment gateway integration:

### 1. In-App Token Ledger
- Practice token deposits and withdrawals.
- Escrow wagering: Wagers are deducted at match start and automatically paid out to the winner upon match completion.
- Complete transaction ledger and match history stored in PostgreSQL.

### 2. Ozow Instant EFT Gateway (`com.robinhood.service.OzowService`)
- Integrated support for South African Instant EFT payments via Ozow.
- Secure request construction with SHA-512 cryptographic checksum hashing:
  ```java
  String data = MERCHANT + "|" + ref + "|" + amount + "|" + returnUrl + "|" + timestamp + "|" + KEY;
  String hash = Util.sha512(data);
  ```
- Asynchronous webhook notification handler (`/wallet/ozow/notify`) verifying transaction status and crediting player wallets.

---

## OpenAPI 3.0 Telemetry & Live Monitor Console

The platform provides an integrated Swagger / OpenAPI 3.0 explorer and system monitor accessible directly in the browser at `/docs`:

- **Interactive API Testing**: Test authentication, wallet operations, room creation, matchmaking, and authoritative game actions directly from the browser without third-party tools.
- **Real-Time Telemetry Dashboard**:
  - **JVM Health**: Real-time heap memory usage (Used / Free / Total / Max bytes).
  - **Thread Monitor**: Active virtual threads and available processor cores.
  - **PostgreSQL Diagnostics**: Live database connectivity status, registered users, and recorded matches.
  - **Engine Metrics**: Active game sessions, waiting rooms, and queued matchmaking players.
- **OpenAPI Schema**: Machine-readable specification served at `/api/openapi.json`.

---

## Repository Structure

```
Vanilla-JDK-API-Example/
├── api/                                # Backend HTTP Server & REST API
│   └── src/com/robinhood/
│       ├── Main.java                   # Server entrypoint & route registration
│       ├── core/
│       │   ├── HttpServer.java         # JDK HTTP server wrapper & virtual thread pool
│       │   ├── Router.java             # Request routing, static files, and CORS
│       │   └── ConfigLoader.java       # Configuration loader
│       ├── controller/                 # HTTP controllers (Wallet, Auth, Games, Docs)
│       ├── db/                         # PostgreSQL JDBC DAOs & schema definitions
│       │   ├── DBManager.java          # JDBC connection manager
│       │   ├── PostgresSchema.java     # Table creation & schema migrations
│       │   ├── MatchSessionDao.java    # Active match state persistence
│       │   └── RoomDao.java            # Custom room persistence
│       ├── game/                       # Matchmaking & room orchestration
│       │   ├── GameManager.java        # Session lifecycle manager
│       │   ├── GameSession.java        # Authoritative room session & bot worker loop
│       │   ├── Matchmaker.java         # Quick Match queue manager
│       │   └── RoomManager.java        # 6-character room code manager
│       ├── realtime/                   # SSE Real-time dispatcher
│       ├── service/                    # External services (OzowService.java)
│       ├── security/                   # Session & JWT token verification
│       └── util/                       # JSON parser, SHA-512 hashing, HTTP utilities
├── engine/                             # Authoritative Game Rules Modules
│   └── src/com/robinhood/engine/
│       ├── Game.java                   # Core game interface
│       ├── GameEngine.java             # Game factory
│       ├── GameType.java               # Enum: KHASINO, CRAZY_EIGHT, UNO, SNAKES_AND_LADDERS, LUDO
│       ├── ludo/                       # Ludo Game Engine
│       │   ├── LudoBoard.java          # 15x15 grid coordinates & track mapping
│       │   ├── LudoColor.java          # Player colors & track offsets
│       │   ├── LudoToken.java          # Token state (-1 to 56) & coordinates
│       │   ├── LudoGame.java           # Complete rules engine & state machine
│       │   ├── LudoAi.java             # Heuristic bot decision logic
│       │   └── LudoRulesEngineTest.java# Automated unit test suite
│       ├── khasino/                    # Khasino card engine & AI
│       ├── crazyeight/                 # Crazy Eights card engine & AI
│       ├── uno/                        # Uno card engine & AI
│       └── snakesandladders/           # Snakes & Ladders engine & AI
├── client/web vanilla/                 # Frontend Web Application
│   ├── index.html                      # Single-page web app entrypoint
│   ├── docs.html                       # Built-in OpenAPI / Swagger explorer & telemetry console
│   ├── css/
│   │   └── style.css                   # Glassmorphic design system & layout styles
│   └── scripts/
│       ├── icons.js                    # Universal Vector SVG Icon & Dice Module
│       ├── main.js                     # App router & authentication orchestrator
│       ├── auth.js                     # Register / Login modal view
│       ├── lobby.js                    # Game selection grid, room badges, rules modal
│       ├── game.js                     # Dynamic game board renderers (Ludo, Cards, Board)
│       ├── wallet.js                   # Token balance, deposit, and withdraw dialogs
│       ├── leaderboard.js              # Global high-scores & rankings
│       ├── history.js                  # Past match history & ledger
│       └── docs.js                     # Interactive API documentation runner & monitor
├── lib/
│   └── postgresql-42.7.4.jar           # Official PostgreSQL JDBC Driver
├── docker-compose.yml                  # PostgreSQL container service
├── build.bat / build.ps1               # Incremental Java compiler scripts
├── run.bat / run.ps1                   # Background server runner with toggle & PID tracking
├── test_card_games.ps1                 # Automated card game API integration test suite
├── test_khasino_api.ps1                # Automated Khasino API integration test suite
└── test_ludo_integration.ps1           # End-to-end Ludo API integration test suite
```

---

## Getting Started

### Prerequisites
- **Java JDK 21 or higher** (`java -version` and `javac -version`).
- **PostgreSQL 16** running locally, OR **Docker** for containerized database execution.

---

### Step 1: Start PostgreSQL
If using Docker, start the pre-configured database container:
```bash
docker-compose up -d
```
The database will be available at `localhost:5433` with user `postgres` and password `postgres`.

---

### Step 2: Build the Application
Compile all engine and API source files:

**PowerShell:**
```powershell
.\build.ps1
```
*Optional debug build:* `.\build.ps1 -Debug`

**Command Prompt / Batch:**
```cmd
build.bat
```
*Optional debug build:* `build.bat debug`

This compiles all engine, API, and test classes into `bin/` with zero warnings.

---

### Step 3: Run the Server
Launch the HTTP server:

**PowerShell:**
```powershell
.\run.ps1
```
*Tip: Running `.\run.ps1` again will cleanly stop the running background server.*

**Command Prompt / Batch:**
```cmd
run.bat
```
*Tip: Running `run.bat` again will cleanly stop the running background server.*

The server will automatically initialize database tables and listen on:
- **Web App**: [http://localhost:8080/](http://localhost:8080/)
- **Interactive API Docs & Live Monitor**: [http://localhost:8080/docs](http://localhost:8080/docs)
- **OpenAPI 3.0 Schema**: [http://localhost:8080/api/openapi.json](http://localhost:8080/api/openapi.json)

---

## REST API Reference

All protected endpoints require an `Authorization: Bearer <token>` header obtained from `/auth/login`.

### Authentication
- `POST /auth/register` — Create a new player account (`{ "email": "...", "password": "..." }`)
- `POST /auth/login` — Authenticate and receive a session token
- `GET /auth/user` — Fetch current user profile and balance
- `GET /auth/status` — Check token validity

### Wallet & Payments
- `GET /wallet/balance` — View current token balance
- `POST /wallet/deposit` — Deposit practice tokens (`{ "amount": 500 }`)
- `POST /wallet/withdraw` — Withdraw practice tokens (`{ "amount": 100 }`)
- `POST /wallet/ozow/initiate` — Generate Ozow payment link with SHA-512 checksum
- `POST /wallet/ozow/notify` — Webhook for Ozow payment confirmation

### Matchmaking & Rooms
- `POST /games/rooms/create` — Create a custom room (`{ "gameType": "LUDO", "maxPlayers": 4 }`)
- `POST /games/rooms/join` — Join via 6-character room code (`{ "roomCode": "ABC123" }`)
- `POST /games/queue/join` — Enter Quick Match queue (`{ "gameType": "LUDO", "maxPlayers": 2 }`)
- `POST /games/forfeit` — Concede an active match and award pot to opponents

### Ludo Authoritative Actions
- `GET /games/ludo/state/{sessionId}` — Retrieve board coordinates, active turn, and dice roll
- `GET /games/ludo/legal-moves/{sessionId}` — Retrieve movable token IDs for the rolled value
- `POST /games/ludo/roll/{sessionId}` — Authoritatively roll the dice (1–6)
- `POST /games/ludo/move/{sessionId}` — Move a token (`{ "tokenId": 1 }`)

### System & Telemetry
- `GET /health` — Service health and database ping
- `GET /metrics` — Memory usage, active threads, and running match sessions
- `GET /api/system/stats` — Live system metrics for the monitor console
- `GET /docs` — Swagger-style interactive API documentation

---

## Testing & Quality Assurance

### 1. Ludo Rules Engine Unit Tests
Run the standalone Java test suite verifying all 14 rule specifications:
```powershell
java -cp "bin;lib/*" com.robinhood.engine.ludo.LudoRulesEngineTest
```
**Results:** `14 / 14 Unit Tests PASSED`
- Board initialization (2, 3, 4 players)
- Yard exit gating (requires rolling a 6)
- Bonus roll on rolling a 6
- Three consecutive sixes forfeiture
- Canonical path progression & boundary validation
- Ownership enforcement (cannot move opponent tokens)
- Sanctuary/Safe square immunity
- Capturing opponent tokens back to their yard
- Colored home column entry
- Exact-roll finish requirements
- Complete win detection (all 4 tokens finished)
- Automatic turn advancement on no legal moves

### 2. End-to-End Integration Tests
Run automated API integration suites:
```powershell
# Test Ludo matchmaking, room creation, dice rolls, and state synchronization
powershell -ExecutionPolicy Bypass -File .\test_ludo_integration.ps1

# Test Khasino card engine and transactions
powershell -ExecutionPolicy Bypass -File .\test_khasino_api.ps1

# Test Crazy Eights, Uno, and Snakes & Ladders engines
powershell -ExecutionPolicy Bypass -File .\test_card_games.ps1
```

---

## Security & Resilience

- **Authoritative Server Validation**: Clients cannot spoof moves, fake dice rolls, advance out of turn, or access other players' tokens.
- **SQL Injection Prevention**: All database queries utilize parameterized `PreparedStatement` instances.
- **Session Security**: Session tokens are verified on every protected route against active database sessions.
- **Graceful Reconnection**: Players can refresh or reconnect without losing their match state; sessions are reconstructed from persistent PostgreSQL records.

---

## License
RobinHood is open-source software provided under the MIT License.
