package com.robinhood.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class PostgresSchema {

    public static void initialize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 1. Users table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  id SERIAL PRIMARY KEY," +
                "  email VARCHAR(255) UNIQUE NOT NULL," +
                "  password_hash VARCHAR(255) NOT NULL," +
                "  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                ");"
            );

            // 2. Rooms table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS rooms (" +
                "  id VARCHAR(64) PRIMARY KEY," +
                "  room_code VARCHAR(16) UNIQUE NOT NULL," +
                "  host_id VARCHAR(255) NOT NULL," +
                "  game_type VARCHAR(64) NOT NULL," +
                "  khasino_mode VARCHAR(64)," +
                "  ai_difficulty VARCHAR(64)," +
                "  max_players INT NOT NULL DEFAULT 2," +
                "  wager_amount INT NOT NULL DEFAULT 50," +
                "  status VARCHAR(32) NOT NULL DEFAULT 'WAITING'," +
                "  is_bot_match BOOLEAN NOT NULL DEFAULT FALSE," +
                "  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                ");"
            );

            // 3. Room Memberships table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS room_memberships (" +
                "  id SERIAL PRIMARY KEY," +
                "  room_id VARCHAR(64) NOT NULL REFERENCES rooms(id) ON DELETE CASCADE," +
                "  player_id VARCHAR(255) NOT NULL," +
                "  seat_index INT NOT NULL DEFAULT 0," +
                "  team_id INT NOT NULL DEFAULT 0," +
                "  is_host BOOLEAN NOT NULL DEFAULT FALSE," +
                "  is_ready BOOLEAN NOT NULL DEFAULT FALSE," +
                "  status VARCHAR(32) NOT NULL DEFAULT 'JOINED'," +
                "  joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                "  left_at TIMESTAMP WITH TIME ZONE," +
                "  CONSTRAINT uq_room_player UNIQUE (room_id, player_id)" +
                ");"
            );

            // 4. Match Sessions table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS match_sessions (" +
                "  match_id VARCHAR(64) PRIMARY KEY," +
                "  room_id VARCHAR(64)," +
                "  game_type VARCHAR(64) NOT NULL," +
                "  khasino_mode VARCHAR(64)," +
                "  max_players INT NOT NULL DEFAULT 2," +
                "  wager_amount INT NOT NULL DEFAULT 50," +
                "  status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS'," +
                "  current_turn VARCHAR(255)," +
                "  state_version INT NOT NULL DEFAULT 1," +
                "  winner_id VARCHAR(255)," +
                "  winner_team INT," +
                "  forfeit_by VARCHAR(255)," +
                "  forfeit_reason VARCHAR(255)," +
                "  outcome VARCHAR(255)," +
                "  started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                "  completed_at TIMESTAMP WITH TIME ZONE," +
                "  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                ");"
            );

            // 5. Game States table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS game_states (" +
                "  match_id VARCHAR(64) PRIMARY KEY REFERENCES match_sessions(match_id) ON DELETE CASCADE," +
                "  version INT NOT NULL DEFAULT 1," +
                "  state_json TEXT NOT NULL," +
                "  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                ");"
            );

            // 6. Game Actions table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS game_actions (" +
                "  id SERIAL PRIMARY KEY," +
                "  match_id VARCHAR(64) NOT NULL REFERENCES match_sessions(match_id) ON DELETE CASCADE," +
                "  player_id VARCHAR(255) NOT NULL," +
                "  action_type VARCHAR(64) NOT NULL," +
                "  payload_json TEXT NOT NULL," +
                "  sequence_num INT NOT NULL DEFAULT 1," +
                "  state_version INT NOT NULL DEFAULT 1," +
                "  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                ");"
            );

            // 7. Match Results table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS match_results (" +
                "  id SERIAL PRIMARY KEY," +
                "  match_id VARCHAR(64) NOT NULL," +
                "  player_id VARCHAR(255) NOT NULL," +
                "  game_type VARCHAR(64) NOT NULL," +
                "  is_winner BOOLEAN NOT NULL DEFAULT FALSE," +
                "  is_forfeit BOOLEAN NOT NULL DEFAULT FALSE," +
                "  final_score INT NOT NULL DEFAULT 0," +
                "  outcome VARCHAR(64)," +
                "  details_json TEXT," +
                "  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                ");"
            );

            // 8. Matchmaking Queue table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS matchmaking_queue (" +
                "  id SERIAL PRIMARY KEY," +
                "  player_id VARCHAR(255) UNIQUE NOT NULL," +
                "  game_type VARCHAR(64) NOT NULL," +
                "  khasino_mode VARCHAR(64)," +
                "  max_players INT NOT NULL DEFAULT 2," +
                "  wager_amount INT NOT NULL DEFAULT 50," +
                "  status VARCHAR(32) NOT NULL DEFAULT 'WAITING'," +
                "  matched_room_id VARCHAR(64)," +
                "  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                "  heartbeat_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                ");"
            );

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rooms_code ON rooms(room_code);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rooms_status ON rooms(status);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memberships_room ON room_memberships(room_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memberships_player ON room_memberships(player_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_actions_match ON game_actions(match_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_results_player ON match_results(player_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_queue_lookup ON matchmaking_queue(game_type, status, wager_amount, max_players);");
        }
    }
}
