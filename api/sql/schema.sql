CREATE DATABASE robinhood;

USE robinhood;

-- USERS (for reference)
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) UNIQUE,
    password VARCHAR(256)
);

-- LEDGER: Tracks all token transactions
CREATE TABLE IF NOT EXISTS ledger (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100),
    type VARCHAR(20),         -- DEPOSIT, WAGER, WIN, RAKE
    amount INT,
    note TEXT,
    timestamp DATETIME
);

-- GAME_RESULTS: Archives completed games
CREATE TABLE IF NOT EXISTS game_results (
    id INT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64),
    email VARCHAR(100),
    rank INT,
    xp_awarded INT,
    tokens_won INT,
    timestamp DATETIME
);

-- LEADERBOARD: Weekly standings
CREATE TABLE IF NOT EXISTS leaderboard (
    email VARCHAR(100) PRIMARY KEY,
    weekly_xp INT DEFAULT 0,
    total_xp INT DEFAULT 0,
    games_played INT DEFAULT 0,
    wins INT DEFAULT 0,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS withdrawal_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100),
    amount INT NOT NULL,
    method VARCHAR(50),
    details TEXT,
    status ENUM('pending', 'approved', 'rejected') DEFAULT 'pending',
    requested_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME
);

CREATE TABLE IF NOT EXISTS wallets (
  email VARCHAR(100) PRIMARY KEY,
  balance INT NOT NULL DEFAULT 0,
  FOREIGN KEY (email) REFERENCES users(email)
);

ALTER TABLE withdrawal_requests
  ADD COLUMN processed_at DATETIME,
  ADD COLUMN admin_note TEXT;

-- Ensure indexing for performance
CREATE INDEX idx_withdrawal_email ON withdrawal_requests(email);
CREATE INDEX idx_withdrawal_status ON withdrawal_requests(status);

-- KYC requests
CREATE TABLE IF NOT EXISTS kyc_requests (
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(100),
  full_name VARCHAR(200),
  document_type ENUM('passport','id_card','driver_license'),
  document_url VARCHAR(500),
  status ENUM('pending','approved','rejected') DEFAULT 'pending',
  submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  processed_at DATETIME NULL,
  admin_note TEXT
);

-- Payment transactions
CREATE TABLE IF NOT EXISTS payment_transactions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(100),
  type ENUM('deposit','withdrawal'),
  provider VARCHAR(50),    -- e.g. 'Stripe','Payfast'
  provider_tx_id VARCHAR(200),
  amount INT,
  currency VARCHAR(3),
  status ENUM('pending','success','failed') DEFAULT 'pending',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  metadata JSON
);

CREATE TABLE game_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    game_type VARCHAR(50),              -- e.g., "snakes_and_ladders"
    session_code VARCHAR(64) UNIQUE,    -- unique game code/hash
    player_ids TEXT,                    -- comma-separated user IDs
    board_config JSON,                  -- board state (snakes/ladders)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL
);

CREATE TABLE game_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_code VARCHAR(64),
    player_id VARCHAR(64),
    move_type VARCHAR(32),              -- e.g., "roll", "move", "timeout"
    dice_roll INT,
    from_tile INT,
    to_tile INT,
    angle DOUBLE,
    intensity DOUBLE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE game_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_code VARCHAR(64),
    winner_id VARCHAR(64),
    xp_gain JSON,                       -- {"p1": 30, "p2": 15, ...}
    token_distribution JSON,           -- {"p1": 115, "p2": 20, ...}
    house_cut INT,
    finalized_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
