# RobinHood — Vanilla JDK Real-Time Gaming Platform

[![Java](https://img.shields.io/badge/Java-21%2B%20(Pure%20JDK)-orange?logo=openjdk)](https://openjdk.org/)
[![Database](https://img.shields.io/badge/Database-PostgreSQL%2015%2B-blue?logo=postgresql)](https://www.postgresql.org/)
[![Frontend](https://img.shields.io/badge/Frontend-Vanilla%20HTML5%20%2F%20CSS3%20%2F%20ES6-yellow?logo=javascript)](https://developer.mozilla.org/en-US/docs/Web/JavaScript)
[![License](https://img.shields.io/badge/Architecture-Zero%20Frameworks-green)](#architecture-philosophy)

A high-performance, real-time multiplayer gaming platform engineered with **zero external frameworks** — running entirely on standard **Vanilla JDK 21+** (`com.sun.net.httpserver`, virtual threads, raw JDBC) and a contemporary, glassmorphic **Vanilla HTML5/CSS3/JavaScript (ES6+)** frontend.

---

## 🎮 Supported Game Modes

RobinHood features five authoritative game modes, complete with custom rules engines, deterministic AI opponents, real-time synchronization, and PostgreSQL persistence:

| Game | Players | Engine Mechanics | Play Modes |
| :--- | :---: | :--- | :--- |
| **🎲 Ludo Master** | 2, 3, or 4 | 15×15 canonical grid, outer track (52 squares), sanctuary/safe squares (`★`), yard mobilization on 6, extra roll on 6, 3-consecutive-six penalty, friendly stacking, captures, and exact finish roll. | Practice vs Bot, Quick Match, Custom Room |
| **🃏 Khasino (Casino)** | 2 | Traditional fishing card game: building, trailing, capturing by value/sum, Spades count, Most Cards, Most Spades, Big Casino (10♦), Little Casino (2♠), and Aces scoring. | Practice vs Bot, Quick Match, Custom Room |
| **🎴 Crazy Eights** | 2 to 4 | Classic shedding game: rank/suit matching, 8s wild with suit declaration, draw accumulation, and hand penalty scoring. | Practice vs Bot, Quick Match, Custom Room |
| **🟥 Uno** | 2 to 4 | Modern color-shedding: Number matching, Skip, Reverse, Draw Two, Wild, Wild Draw Four, and penalty cards. | Practice vs Bot, Quick Match, Custom Room |
| **🐍 Snakes & Ladders** | 2 to 4 | 100-tile board race with sliding snakes, climbing ladders, authoritative dice rolls, and exact finish tiles. | Practice vs Bot, Quick Match, Custom Room |

---

## 🏛️ Architecture Philosophy

```
┌─────────────────────────────────────────────────────────────┐
│                 Vanilla ES6+ Web Client                     │
│    (Glassmorphic CSS3, Responsive 15x15 Canvas, SSE Client) │
└──────────────────────────────▲──────────────────────────────┘
                               │ HTTP / SSE
┌──────────────────────────────▼──────────────────────────────┐
│                    RobinHood HTTP Server                    │
│     Built on JDK com.sun.net.httpserver & Virtual Threads   │
├──────────────────────────────┬──────────────────────────────┤
│  REST Router & Controllers   │   RealTimeDispatcher (SSE)   │
│  - /auth, /wallet, /games    │   - State Broadcasts         │
│  - Matchmaker & RoomManager  │   - Connection Heartbeats    │
├──────────────────────────────┴──────────────────────────────┤
│                 Core Game Engine Library                    │
│  - Authoritative Rules Modules (Ludo, Khasino, Uno, etc.)   │
│  - Deterministic AI Decision Engines (RobinBot)             │
├─────────────────────────────────────────────────────────────┤
│                  Data Persistence Layer                     │
│  - Raw JDBC Connection Pooling (DBManager)                  │
│  - PostgreSQL 15+ (Users, Sessions, Rooms, History, Wallet) │
└─────────────────────────────────────────────────────────────┘
```

1. **Pure Vanilla JDK (Backend)**: No Spring Boot, Quarkus, Micronaut, or Jakarta EE. Every route, JSON serializer, JWT verifier, and HTTP handler runs directly on Java standard libraries.
2. **Authoritative Rules Engines**: Clients never dictate dice values, card draws, legal moves, or win conditions. Actions are submitted as intent requests, validated by backend state machines, and broadcasted to players.
3. **Deterministic AI (RobinBot)**: Every game mode includes built-in bot intelligence executing the exact same authoritative API flows as human players.
4. **Real-Time Multiplayer (SSE)**: Built-in `RealTimeDispatcher` streams state transitions using lightweight Server-Sent Events (SSE).
5. **No Frontend Frameworks**: No React, Vue, Angular, or Tailwind. Pure CSS Custom Properties, flexbox/grid layouts, responsive breakpoints, and modular ES6 imports.

---

## 🎲 Ludo Rules & Engine Specification

The Ludo rules engine (`com.robinhood.engine.ludo`) adheres strictly to traditional standards:

- **Board & Coordinates**: 15×15 grid with four colored yard quadrants, a 52-tile outer loop, home stretches (5 tiles per color), and a central finish triangle.
- **Color Assignments**:
  - **2 Players**: Red vs. Yellow (diametrically opposed).
  - **3 Players**: Red, Green, Yellow.
  - **4 Players**: Red, Green, Yellow, Blue.
- **Starting a Token**: Tokens remain in the yard until a **6** is rolled, which mobilizes a token to that color's starting tile (`Track 0` for Red, `13` for Green, `26` for Yellow, `39` for Blue).
- **Consecutive Sixes Rule**: Rolling a 6 grants a bonus turn. If a player rolls **three consecutive sixes**, the turn is immediately forfeited and passes to the next player.
- **Sanctuaries (Safe Squares)**: Track squares `0, 8, 13, 21, 26, 34, 39, 47` are marked with `★`. Tokens on safe squares are immune to capture. Multiple tokens (including opposing tokens) may co-exist on safe squares.
- **Captures**: Landing on an opponent's token on any standard track tile captures it, immediately returning it to its owner's yard (`step = -1`).
- **Exact Finish Roll**: Moving into the home column requires the exact roll to land on square 56. Overshooting rolls are invalid.
- **Automatic Turn Advancement**: If no legal moves can be made with the rolled value, the turn advances automatically.

---

## 📁 Repository Structure

```
Vanilla-JDK-API-Example/
├── api/                                # Backend HTTP Server & REST API
│   └── src/com/robinhood/
│       ├── Main.java                   # Server entrypoint & route registration
│       ├── core/
│       │   ├── HttpServer.java         # JDK HTTP server wrapper
│       │   └── Router.java             # Regex route dispatcher & middleware
│       ├── controller/                 # HTTP controllers (Wallet, Auth, Games)
│       ├── db/                         # PostgreSQL JDBC DAOs & schema definitions
│       │   ├── DBManager.java          # JDBC connection manager
│       │   ├── PostgresSchema.java     # Table creation & migrations
│       │   ├── MatchSessionDao.java    # Active match state persistence
│       │   └── RoomDao.java            # Custom room persistence
│       ├── game/                       # Matchmaking & room orchestration
│       │   ├── GameManager.java        # Session lifecycle manager
│       │   ├── GameSession.java        # Authoritative room session & bot loop
│       │   ├── Matchmaker.java         # Quick Match queue manager
│       │   └── RoomManager.java        # 6-character room code manager
│       ├── realtime/                   # SSE Real-time dispatcher
│       └── security/                   # Session & JWT token verification
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
│       │   └── LudoRulesEngineTest.java# 14/14 automated unit tests
│       ├── khasino/                    # Khasino (Casino) card engine & AI
│       ├── crazyeight/                 # Crazy Eights card engine & AI
│       └── uno/                        # Uno card engine & AI
├── client/web vanilla/                 # Frontend Web Application
│   ├── index.html                      # Single-page web app entrypoint
│   ├── docs.html                       # Built-in OpenAPI / Swagger explorer
│   ├── css/
│   │   └── style.css                   # Comprehensive design system & animations
│   └── scripts/
│       ├── main.js                     # App router & authentication check
│       ├── auth.js                     # Register / Login modal view
│       ├── lobby.js                    # Game selection grid, player chips, rules modal
│       ├── game.js                     # Dynamic game board renderers (Ludo, Cards, Board)
│       ├── wallet.js                   # Token balance, deposit, and withdraw dialogs
│       ├── leaderboard.js              # Global high-scores & rankings
│       ├── history.js                  # Past match history & results
│       └── docs.js                     # Interactive API documentation runner
├── lib/
│   └── postgresql-42.7.4.jar           # Official PostgreSQL JDBC Driver
├── docker-compose.yml                  # PostgreSQL container service
├── build.bat / build.ps1               # Incremental Java compiler scripts
├── run.bat / run.ps1                   # Background server runner with toggle & PID tracking
└── test_ludo_integration.ps1           # End-to-end PowerShell integration test suite
```

---

## 🚀 Getting Started

### Prerequisites
- **JDK 21 or higher** (`java -version` and `javac -version`).
- **PostgreSQL 15+** running locally, OR **Docker** for containerized database execution.

---

### Step 1: Start PostgreSQL
If using Docker, start the pre-configured database container:
```bash
docker-compose up -d
```
The database will be available at `localhost:5433` with user `postgres` and password `postgres`.

---

### Step 2: Build the Application
Use either PowerShell or Command Prompt:

**PowerShell:**
```powershell
.\build.ps1
```
*Optional debug build:* `.\build.ps1 -Debug`

**Batch Script:**
```cmd
build.bat
```
*Optional debug build:* `build.bat debug`

This compiles all engine, API, and test classes into `bin/` with zero warnings.

---

### Step 3: Run the Server
Use either PowerShell or Command Prompt:

**PowerShell:**
```powershell
.\run.ps1
```
*Tip: Running `.\run.ps1` a second time will cleanly stop the running background server.*

**Batch Script:**
```cmd
run.bat
```
*Tip: Running `run.bat` a second time will cleanly stop the running background server.*

The server will initialize database tables automatically and listen on:
- **Web App**: [http://localhost:8080/](http://localhost:8080/)
- **Interactive API Docs & Monitor**: [http://localhost:8080/docs](http://localhost:8080/docs)
- **OpenAPI Schema**: [http://localhost:8080/api/openapi.json](http://localhost:8080/api/openapi.json)

---

## 📡 REST API Reference

All protected endpoints require an `Authorization: Bearer <token>` header obtained from `/auth/login`.

### Authentication
- `POST /auth/register` — Create a new account (`{ "email": "...", "password": "..." }`)
- `POST /auth/login` — Authenticate and receive a session token
- `GET /auth/user` — Fetch current user profile and balance
- `GET /auth/status` — Check token validity

### Wallet & Tokens
- `GET /wallet/balance` — View current token balance
- `POST /wallet/deposit` — Add practice tokens (`{ "amount": 500 }`)
- `POST /wallet/withdraw` — Withdraw tokens (`{ "amount": 100 }`)

### Matchmaking & Rooms
- `POST /games/rooms/create` — Create a custom room (`{ "gameType": "LUDO", "maxPlayers": 4 }`)
- `POST /games/rooms/join` — Join via 6-character room code (`{ "roomCode": "ABC123" }`)
- `POST /games/queue/join` — Enter Quick Match queue (`{ "gameType": "LUDO", "maxPlayers": 2 }`)
- `POST /games/forfeit` — Concede an active match and award pot to opponents

### Ludo Authoritative Actions
- `GET /games/ludo/state/{sessionId}` — Retrieve full board coordinates, active turn, and dice roll
- `GET /games/ludo/legal-moves/{sessionId}` — Retrieve movable token IDs for the rolled value
- `POST /games/ludo/roll/{sessionId}` — Authoritatively roll the dice (1–6)
- `POST /games/ludo/move/{sessionId}` — Move a token (`{ "tokenId": 1 }`)

### System & Telemetry
- `GET /health` — Service health and database ping
- `GET /metrics` — Memory usage, active threads, and running match sessions
- `GET /docs` — Swagger-style interactive API documentation

---

## 🧪 Testing & Quality Assurance

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

## 🛡️ Security & Integrity

- **Authoritative Validation**: Clients cannot spoof moves, fake dice rolls, advance out of turn, or access other players' tokens.
- **SQL Injection Prevention**: All database queries utilize parameterized `PreparedStatement` instances.
- **Session Security**: Session tokens are verified on every protected route against active database sessions.
- **Graceful Reconnection**: Players can refresh or reconnect without losing their match state; sessions are reconstructed from persistent PostgreSQL records.

---

## 📄 License
RobinHood is open-source software provided under the MIT License.
