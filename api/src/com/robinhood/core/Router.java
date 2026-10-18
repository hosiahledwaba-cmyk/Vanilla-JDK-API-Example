package com.robinhood.core;

import com.robinhood.controller.*;
import com.robinhood.db.GameResultLogger;
import com.robinhood.db.LeaderboardManager;
import com.robinhood.db.WithdrawalManager;
import com.robinhood.game.GameManager;
import com.robinhood.game.GameSession;
import com.robinhood.game.Matchmaker;
import com.robinhood.game.Player;
import com.robinhood.model.GameResult;
import com.robinhood.realtime.RealTimeDispatcher;
import com.robinhood.security.AdminAuthManager;
import com.robinhood.security.AuthManager;
import com.robinhood.security.RateLimiter;
import com.robinhood.util.JSONParser;
import com.robinhood.util.MetricsCollector;
import com.robinhood.util.ResponseBuilder;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Router {

    public static void route(BufferedReader reader, BufferedWriter writer) throws IOException {
        HttpRequest req = parseRequest(reader);

        if (req == null) {
            writer.write(ResponseBuilder.json(400, "Bad Request"));
            writer.flush();
            return;
        }

        String response = handleRequest(req, null);
        if (response != null) {
            writer.write(response);
            writer.flush();
        }
    }

    public static String handleRequest(String rawRequest) {
        return handleRequest(rawRequest, null);
    }

    public static String handleRequest(String rawRequest, SocketChannel clientChannel) {
        HttpRequest req = parseRequest(rawRequest);
        if (req == null) return ResponseBuilder.json(400, "Bad Request");
        return handleRequest(req, clientChannel);
    }

    private static String handleRequest(HttpRequest req, SocketChannel clientChannel) {
        MetricsCollector.incrementRequests();
        MetricsCollector.setActiveSessions(GameManager.getActiveSessionCount());

        if (clientChannel != null) {
            try {
                if (clientChannel.getRemoteAddress() instanceof InetSocketAddress addr) {
                    String clientIp = addr.getAddress().getHostAddress();
                    if (!RateLimiter.allow(clientIp)) {
                        return ResponseBuilder.json(429, "Too many requests");
                    }
                }
            } catch (IOException ignored) {}
        }

        String method = req.method;
        String path = req.path;
        Map<String, String> headers = req.headers;
        Map<String, String> queryParams = req.queryParams;
        String body = req.body;

        // CORS Preflight
        if (method.equals("OPTIONS")) {
            return "HTTP/1.1 204 No Content\r\n" +
                   "Access-Control-Allow-Origin: *\r\n" +
                   "Access-Control-Allow-Headers: *\r\n" +
                   "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                   "Connection: close\r\n\r\n";
        }

        // Static Files Serving from client/web vanilla
        if (method.equals("GET") || method.equals("HEAD")) {
            String staticFileResp = serveStaticFile(path);
            if (staticFileResp != null) {
                return staticFileResp;
            }
        }

        // Swagger / API Docs & Live Monitoring Console
        if ((method.equals("GET") || method.equals("HEAD")) && (path.equals("/docs") || path.equals("/swagger") || path.equals("/swagger-ui") || path.equals("/api-docs"))) {
            return serveStaticFile("/docs.html");
        }

        // Live System Monitoring Endpoint
        if (method.equals("GET") && (path.equals("/api/system/stats") || path.equals("/monitoring/stats") || path.equals("/api/monitoring"))) {
            Runtime rt = Runtime.getRuntime();
            long totalMem = rt.totalMemory();
            long freeMem = rt.freeMemory();
            long maxMem = rt.maxMemory();
            long usedMem = totalMem - freeMem;
            int activeThreads = Thread.activeCount();
            int userCount = com.robinhood.db.DBManager.getUserCount();
            int matchCount = com.robinhood.db.DBManager.getMatchCount();
            int activeSessions = GameManager.getActiveSessionCount();
            int openRooms = com.robinhood.game.RoomManager.getActiveRoomCount();
            int queuedPlayers = com.robinhood.db.MatchmakerDao.getQueueCount();

            String statsJson = "{" +
                "\"status\":\"UP\"," +
                "\"timestamp\":" + System.currentTimeMillis() + "," +
                "\"server\":{" +
                    "\"port\":8080," +
                    "\"protocol\":\"HTTP/1.1 NIO\"," +
                    "\"threads\":" + activeThreads + "," +
                    "\"availableProcessors\":" + rt.availableProcessors() +
                "}," +
                "\"memory\":{" +
                    "\"usedBytes\":" + usedMem + "," +
                    "\"freeBytes\":" + freeMem + "," +
                    "\"totalBytes\":" + totalMem + "," +
                    "\"maxBytes\":" + maxMem +
                "}," +
                "\"database\":{" +
                    "\"type\":\"PostgreSQL 16 (Docker)\"," +
                    "\"port\":5433," +
                    "\"connected\":" + com.robinhood.db.DBManager.isAvailable() + "," +
                    "\"totalUsers\":" + userCount + "," +
                    "\"totalMatches\":" + matchCount +
                "}," +
                "\"engine\":{" +
                    "\"activeGameSessions\":" + activeSessions + "," +
                    "\"openWaitingRooms\":" + openRooms + "," +
                    "\"queuedPlayers\":" + queuedPlayers + "," +
                    "\"supportedGames\":[\"KHASINO\",\"CRAZY_EIGHT\",\"UNO\",\"SNAKES_AND_LADDERS\",\"LUDO\"]" +
                "}" +
            "}";
            return ResponseBuilder.json(200, statsJson);
        }

        // Health & Metrics
        if (method.equals("GET") && path.equals("/health")) {
            return ResponseBuilder.text(200, "OK");
        }
        if (method.equals("GET") && path.equals("/metrics")) {
            return ResponseBuilder.text(200, MetricsCollector.scrape());
        }

        // Public Auth Endpoints
        if (method.equals("POST") && (path.equals("/signup") || path.equals("/auth/signup"))) {
            return AuthController.signup(body);
        }
        if (method.equals("POST") && (path.equals("/login") || path.equals("/auth/login"))) {
            return AuthController.login(body);
        }

        // Public Webhook Endpoints
        if (method.equals("POST") && path.equals("/payment/webhook")) {
            String gateway = queryParams.getOrDefault("gateway", "stripe");
            return PaymentController.webhook(gateway, body, headers);
        }

        // Real-time SSE Stream Endpoint (/events, /stream, or /ws/{sessionId})
        if (method.equals("GET") && (path.equals("/events") || path.equals("/stream") || path.startsWith("/ws/"))) {
            String sessionId = path.startsWith("/ws/") ? path.substring("/ws/".length()) : queryParams.get("sessionId");
            String roomId = queryParams.get("roomId");
            String token = queryParams.get("token");
            if (token == null && (headers.containsKey("Authorization") || headers.containsKey("authorization"))) {
                String authH = headers.getOrDefault("Authorization", headers.get("authorization"));
                if (authH.startsWith("Bearer ")) token = authH.substring(7).trim();
            }
            if (token == null || !AuthManager.validateToken(token)) {
                return ResponseBuilder.json(401, "Invalid token");
            }
            String email = AuthManager.getEmailFromToken(token);

            String topic = null;
            if (sessionId != null && !sessionId.isBlank()) {
                GameSession session = GameManager.getSession(sessionId);
                if (session == null || session.getPlayers().stream().noneMatch(p -> p.getEmail().equalsIgnoreCase(email))) {
                    return ResponseBuilder.json(403, "Forbidden");
                }
                topic = sessionId;
            } else if (roomId != null && !roomId.isBlank()) {
                com.robinhood.db.RoomDao.Room room = com.robinhood.game.RoomManager.getRoom(roomId);
                if (room == null || room.members.stream().noneMatch(m -> m.playerId.equalsIgnoreCase(email))) {
                    return ResponseBuilder.json(403, "Forbidden");
                }
                topic = room.id;
            } else {
                return ResponseBuilder.json(400, "Missing sessionId or roomId");
            }

            if (clientChannel != null) {
                try {
                    RealTimeDispatcher.subscribe(topic, clientChannel);
                    return null; // Keep channel open for SSE
                } catch (IOException e) {
                    return ResponseBuilder.json(500, "SSE subscription error");
                }
            }
            return ResponseBuilder.json(400, "SSE stream requires direct socket connection");
        }

        // Authorization check for protected endpoints
        String authHeader = headers.getOrDefault("Authorization", headers.getOrDefault("authorization", ""));
        String token = "";
        if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (queryParams.containsKey("token")) {
            token = queryParams.get("token");
        }

        boolean isValidUser = AuthManager.validateToken(token);
        boolean isValidAdmin = AdminAuthManager.isAdminToken(token) || token.equals(ConfigLoader.get("admin.token"));

        if (!isValidUser && !isValidAdmin) {
            return ResponseBuilder.json(401, "Missing or invalid token");
        }

        // Admin Routes
        if (path.startsWith("/admin/")) {
            if (!isValidAdmin) return ResponseBuilder.json(403, "Admin authorization required");
            return switch (path) {
                case "/admin/stats" -> AdminController.stats(token);
                case "/admin/users" -> AdminController.listUsers(token);
                case "/admin/user" -> AdminController.userDetails(token, queryParams);
                case "/admin/withdrawals" -> AdminController.withdrawals(token, queryParams);
                case "/admin/withdrawals/approve" -> AdminController.approveWithdrawal(token, body);
                case "/admin/withdrawals/reject" -> AdminController.rejectWithdrawal(token, body);
                case "/admin/sessions" -> AdminController.listSessions(token);
                case "/admin/session/history" -> AdminController.sessionHistory(token, queryParams);
                case "/admin/kill" -> AdminController.killSession(token, body);
                case "/admin/shutdown" -> AdminController.shutdown(token);
                case "/admin/withdraw/pending" -> ResponseBuilder.json(200, WithdrawalManager.getAllPending());
                default -> ResponseBuilder.json(404, "Admin route not found");
            };
        }

        // User Routes
        String email = AuthManager.getEmailFromToken(token);

        if (method.equals("GET") && path.equals("/profile")) {
            return ResponseBuilder.json(200, "{\"email\": \"" + email + "\"}");
        }

        // Wallet & Payments
        if (method.equals("POST") && (path.equals("/deposit") || path.equals("/wallet/deposit"))) {
            return WalletController.deposit(token, body);
        }
        if (method.equals("GET") && (path.equals("/balance") || path.equals("/wallet/balance"))) {
            return WalletController.balance(token);
        }
        if (method.equals("POST") && path.equals("/payment/deposit")) {
            String gateway = queryParams.getOrDefault("gateway", "stripe");
            return PaymentController.deposit(gateway, token, body);
        }
        if (method.equals("POST") && (path.equals("/withdraw") || path.equals("/wallet/withdraw"))) {
            return WithdrawalController.request(token, body);
        }
        if (method.equals("GET") && (path.equals("/withdraw/history") || path.equals("/wallet/history") || path.equals("/wallet/transactions"))) {
            return WithdrawalController.history(token);
        }

        // Queue Matchmaking
        if (method.equals("POST") && (path.equals("/games/queue/join") || path.equals("/queue/join") || path.equals("/games/quick-match"))) {
            Map<String, String> data = JSONParser.parse(body);
            int bet = 50;
            try {
                bet = Integer.parseInt(data.getOrDefault("bet", "50"));
            } catch (Exception ignored) {}
            if (WalletController.getBalance(email) < bet) {
                return ResponseBuilder.json(400, "{\"status\":\"error\",\"message\":\"Insufficient balance for bet of " + bet + " tokens\"}");
            }
            String gameReq = data.getOrDefault("game", data.getOrDefault("gameType", "snl"));
            com.robinhood.engine.GameType gt = resolveGameType(gameReq);
            int maxPlayers = 2;
            try { maxPlayers = Integer.parseInt(data.getOrDefault("maxPlayers", "2")); } catch (Exception ignored) {}
            Player player = new Player(email, token);
            String matchedSession = Matchmaker.joinQueue(player, bet, gt, null, maxPlayers);
            if (matchedSession != null) {
                return ResponseBuilder.json(200, "{\"status\":\"matched\",\"sessionId\":\"" + matchedSession + "\"}");
            }
            return ResponseBuilder.json(200, "{\"status\":\"queued\",\"message\":\"Searching for opponents...\"}");
        }

        if (method.equals("POST") && (path.equals("/games/queue/status") || path.equals("/queue/status"))) {
            String matchedSession = Matchmaker.pollQueue(email);
            if (matchedSession != null) {
                return ResponseBuilder.json(200, "{\"status\":\"matched\",\"sessionId\":\"" + matchedSession + "\"}");
            }
            return ResponseBuilder.json(200, "{\"status\":\"queued\",\"message\":\"Waiting for more players...\"}");
        }

        if (method.equals("POST") && (path.equals("/games/queue/cancel") || path.equals("/queue/cancel"))) {
            Matchmaker.cancelQueue(email);
            return ResponseBuilder.json(200, "{\"cancelled\":true}");
        }

        // Custom Multiplayer Room Endpoints
        if (method.equals("POST") && path.equals("/rooms/create")) {
            Map<String, String> data = JSONParser.parse(body);
            String gameReq = data.getOrDefault("game", data.getOrDefault("gameType", "SNAKES_AND_LADDERS"));
            String khasinoMode = data.get("khasinoMode");
            String aiDifficulty = data.getOrDefault("aiDifficulty", "MEDIUM");
            int maxPlayers = 2;
            try { maxPlayers = Integer.parseInt(data.getOrDefault("maxPlayers", "2")); } catch (Exception ignored) {}
            int wager = 50;
            try { wager = Integer.parseInt(data.getOrDefault("wager", data.getOrDefault("wagerAmount", data.getOrDefault("bet", "50")))); } catch (Exception ignored) {}
            boolean isBotMatch = Boolean.parseBoolean(data.getOrDefault("isBotMatch", "false"));

            try {
                com.robinhood.db.RoomDao.Room room = com.robinhood.game.RoomManager.createRoom(
                    email, gameReq, khasinoMode, aiDifficulty, maxPlayers, wager, isBotMatch
                );
                return ResponseBuilder.json(201, "{\"room\":" + room.toJson() + "}");
            } catch (Exception e) {
                return ResponseBuilder.json(400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        if (method.equals("POST") && path.equals("/rooms/join")) {
            Map<String, String> data = JSONParser.parse(body);
            String codeOrId = data.getOrDefault("roomCode", data.getOrDefault("code", data.get("roomId")));
            if (codeOrId == null || codeOrId.isBlank()) {
                return ResponseBuilder.json(400, "{\"error\":\"Missing roomCode or roomId\"}");
            }
            try {
                com.robinhood.db.RoomDao.Room room = com.robinhood.game.RoomManager.joinRoom(codeOrId.trim(), email);
                return ResponseBuilder.json(200, "{\"room\":" + room.toJson() + "}");
            } catch (Exception e) {
                return ResponseBuilder.json(400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        if (method.equals("GET") && (path.startsWith("/rooms/info") || path.matches("^/rooms/[^/]+$"))) {
            String roomId = path.startsWith("/rooms/info") ? queryParams.getOrDefault("roomId", queryParams.get("roomCode")) : path.substring("/rooms/".length());
            com.robinhood.db.RoomDao.Room room = com.robinhood.game.RoomManager.getRoom(roomId);
            if (room == null) return ResponseBuilder.json(404, "{\"error\":\"Room not found\"}");
            return ResponseBuilder.json(200, "{\"room\":" + room.toJson() + "}");
        }

        if (method.equals("POST") && (path.equals("/rooms/ready") || path.endsWith("/ready"))) {
            Map<String, String> data = JSONParser.parse(body);
            String roomId = data.getOrDefault("roomId", path.replace("/rooms/", "").replace("/ready", ""));
            boolean ready = Boolean.parseBoolean(data.getOrDefault("ready", "true"));
            try {
                com.robinhood.db.RoomDao.Room room = com.robinhood.game.RoomManager.setReady(roomId, email, ready);
                return ResponseBuilder.json(200, "{\"room\":" + room.toJson() + "}");
            } catch (Exception e) {
                return ResponseBuilder.json(400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        if (method.equals("POST") && (path.equals("/rooms/leave") || path.endsWith("/leave"))) {
            Map<String, String> data = JSONParser.parse(body);
            String roomId = data.getOrDefault("roomId", path.replace("/rooms/", "").replace("/leave", ""));
            com.robinhood.game.RoomManager.leaveRoom(roomId, email);
            return ResponseBuilder.json(200, "{\"left\":true}");
        }

        if (method.equals("POST") && (path.equals("/rooms/start") || path.endsWith("/start"))) {
            Map<String, String> data = JSONParser.parse(body);
            String roomId = data.getOrDefault("roomId", path.replace("/rooms/", "").replace("/start", ""));
            boolean allowBotFill = Boolean.parseBoolean(data.getOrDefault("allowBotFill", "false"));
            try {
                GameSession session = com.robinhood.game.RoomManager.startMatch(roomId, email, allowBotFill);
                return ResponseBuilder.json(200, "{\"sessionId\":\"" + session.getSessionId() + "\",\"gameType\":\"" + session.getGameType().name() + "\"}");
            } catch (Exception e) {
                return ResponseBuilder.json(400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        // Forfeit Active Match
        if (method.equals("POST") && (path.equals("/games/forfeit") || path.equals("/forfeit"))) {
            Map<String, String> data = JSONParser.parse(body);
            String sId = data.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            boolean ok = session.forfeit(email);
            return ResponseBuilder.json(ok ? 200 : 400, "{\"status\":\"" + (ok ? "ok" : "failed") + "\",\"forfeited\":" + ok + ",\"winner\":\"" + (session.getWinner() != null ? session.getWinner() : "") + "\",\"forfeitedBy\":\"" + email + "\"}");
        }

        // Match Results & History
        if (method.equals("GET") && (path.equals("/games/history") || path.equals("/history") || path.equals("/match/history"))) {
            List<com.robinhood.db.MatchSessionDao.GameResultRecord> history = com.robinhood.db.MatchSessionDao.getPlayerHistory(email);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < history.size(); i++) {
                com.robinhood.db.MatchSessionDao.GameResultRecord r = history.get(i);
                if (i > 0) sb.append(",");
                sb.append("{")
                  .append("\"matchId\":\"").append(r.matchId).append("\",")
                  .append("\"gameType\":\"").append(r.gameType).append("\",")
                  .append("\"isWinner\":").append(r.isWinner).append(",")
                  .append("\"isForfeit\":").append(r.isForfeit).append(",")
                  .append("\"score\":").append(r.score).append(",")
                  .append("\"outcome\":\"").append(r.outcome).append("\",")
                  .append("\"createdAt\":\"").append(r.createdAt != null ? r.createdAt.toString() : "").append("\"")
                  .append("}");
            }
            sb.append("]");
            return ResponseBuilder.json(200, "{\"history\":" + sb.toString() + "}");
        }

        // Generic Session Info
        if (method.equals("GET") && (path.startsWith("/games/session/") || path.startsWith("/session/"))) {
            String sId = path.startsWith("/games/session/") ? path.substring("/games/session/".length()) : path.substring("/session/".length());
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            return ResponseBuilder.json(200,
                "{" +
                "\"sessionId\":\"" + sId + "\"," +
                "\"gameType\":\"" + session.getGameType().name() + "\"," +
                "\"started\":" + session.isStarted() + "," +
                "\"isOver\":" + session.isGameOver() + "," +
                "\"winner\":" + (session.getWinner() != null ? "\"" + session.getWinner() + "\"" : "null") + "," +
                "\"currentTurn\":\"" + (session.getCurrentTurnPlayer() != null ? session.getCurrentTurnPlayer() : "") + "\"," +
                "\"wager\":" + session.getWagerAmount() + "," +
                "\"maxPlayers\":" + session.getMaxPlayers() + "," +
                "\"players\":" + session.getPlayersJson() + "," +
                "\"khasinoMode\":\"" + (session.getKhasinoMode() != null ? session.getKhasinoMode().name() : "") + "\"," +
                "\"aiDifficulty\":\"" + session.getKhasinoDifficulty().name() + "\"" +
                "}"
            );
        }

        // Card Game Endpoints (Crazy Eights & Uno)
        if (method.equals("GET") && path.startsWith("/games/card/state")) {
            String sId = path.startsWith("/games/card/state/") ? path.substring("/games/card/state/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            String stateJson = (session.getGameType() == com.robinhood.engine.GameType.UNO) ?
                    session.getUnoGame().toJson() : session.getCrazyEightGame().toJson();
            return ResponseBuilder.json(200, "{\"sessionId\":\"" + sId + "\",\"gameType\":\"" + session.getGameType().name() + "\",\"state\":" + stateJson + "}");
        }

        if (method.equals("POST") && path.startsWith("/games/card/play")) {
            String sId = path.startsWith("/games/card/play/") ? path.substring("/games/card/play/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            Map<String, String> data = JSONParser.parse(body);
            String cardId = data.get("cardId");
            String colorOrSuit = data.getOrDefault("color", data.get("suit"));
            Map<String, Object> res;
            if (session.getGameType() == com.robinhood.engine.GameType.UNO) {
                res = session.submitUnoMove(email, cardId, colorOrSuit);
            } else {
                res = session.submitCrazyEightMove(email, cardId, colorOrSuit);
            }
            boolean ok = Boolean.TRUE.equals(res.get("success"));
            return ResponseBuilder.json(ok ? 200 : 400, JSONParser.stringify(res));
        }

        if (method.equals("POST") && path.startsWith("/games/card/draw")) {
            String sId = path.startsWith("/games/card/draw/") ? path.substring("/games/card/draw/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            Map<String, Object> res = (session.getGameType() == com.robinhood.engine.GameType.UNO) ?
                    session.submitUnoDraw(email) : session.submitCrazyEightDraw(email);
            return ResponseBuilder.json(200, JSONParser.stringify(res));
        }

        if (method.equals("POST") && path.startsWith("/games/card/pass")) {
            String sId = path.startsWith("/games/card/pass/") ? path.substring("/games/card/pass/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            Map<String, Object> res = (session.getGameType() == com.robinhood.engine.GameType.UNO) ?
                    session.submitUnoPass(email) : session.submitCrazyEightPass(email);
            return ResponseBuilder.json(200, JSONParser.stringify(res));
        }

        // South African Khasino (Kasi Kasino) Endpoints
        if (method.equals("GET") && path.startsWith("/games/khasino/state")) {
            String sId = path.startsWith("/games/khasino/state/") ? path.substring("/games/khasino/state/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            return ResponseBuilder.json(200, "{\"sessionId\":\"" + sId + "\",\"gameType\":\"KHASINO\",\"state\":" + session.getKhasinoGame().toJson(email) + "}");
        }

        if (method.equals("GET") && path.startsWith("/games/khasino/legal-moves")) {
            String sId = path.startsWith("/games/khasino/legal-moves/") ? path.substring("/games/khasino/legal-moves/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            List<com.robinhood.engine.khasino.KhasinoAction> actions = session.getKhasinoGame().getLegalActions(email);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < actions.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(actions.get(i).toJson());
            }
            sb.append("]");
            return ResponseBuilder.json(200, "{\"actions\":" + sb.toString() + "}");
        }

        if (method.equals("POST") && path.startsWith("/games/khasino/action")) {
            String sId = path.startsWith("/games/khasino/action/") ? path.substring("/games/khasino/action/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");

            Map<String, String> data = JSONParser.parse(body);
            String typeStr = data.getOrDefault("type", "DRIFT");
            com.robinhood.engine.khasino.KhasinoAction.ActionType actionType;
            try {
                actionType = com.robinhood.engine.khasino.KhasinoAction.ActionType.valueOf(typeStr.toUpperCase());
            } catch (Exception e) {
                actionType = com.robinhood.engine.khasino.KhasinoAction.ActionType.DRIFT;
            }
            String playedCardId = data.get("playedCardId");

            List<String> floorIds = new ArrayList<>();
            String rawFloor = data.get("targetFloorCardIds");
            if (rawFloor != null && !rawFloor.isBlank()) {
                for (String id : rawFloor.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
                    String trimmed = id.trim();
                    if (!trimmed.isEmpty()) floorIds.add(trimmed);
                }
            }

            List<String> buildIds = new ArrayList<>();
            String rawBuilds = data.get("targetBuildIds");
            if (rawBuilds != null && !rawBuilds.isBlank()) {
                for (String id : rawBuilds.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
                    String trimmed = id.trim();
                    if (!trimmed.isEmpty()) buildIds.add(trimmed);
                }
            }

            String targetBuildId = data.get("targetBuildId");
            int declVal = 0;
            try {
                declVal = Integer.parseInt(data.getOrDefault("declaredValue", "0"));
            } catch (Exception ignored) {}

            String stolenCardId = data.get("stolenCardId");
            String stolenOpponentId = data.get("stolenOpponentId");

            com.robinhood.engine.khasino.KhasinoAction action = new com.robinhood.engine.khasino.KhasinoAction(
                    actionType, playedCardId, floorIds, buildIds, targetBuildId, declVal, stolenCardId, stolenOpponentId);

            Map<String, Object> res = session.submitKhasinoMove(email, action);
            boolean ok = Boolean.TRUE.equals(res.get("success"));
            return ResponseBuilder.json(ok ? 200 : 400, JSONParser.stringify(res));
        }

        // Ludo Game Endpoints
        if (method.equals("GET") && path.startsWith("/games/ludo/state")) {
            String sId = path.startsWith("/games/ludo/state/") ? path.substring("/games/ludo/state/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            return ResponseBuilder.json(200, "{\"sessionId\":\"" + sId + "\",\"gameType\":\"LUDO\",\"state\":" + session.getLudoGame().toJson(email) + "}");
        }

        if (method.equals("GET") && path.startsWith("/games/ludo/legal-moves")) {
            String sId = path.startsWith("/games/ludo/legal-moves/") ? path.substring("/games/ludo/legal-moves/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            List<Integer> moves = session.getLudoGame().getLegalMoves(email);
            return ResponseBuilder.json(200, "{\"legalMoves\":" + moves.toString() + ",\"roll\":" + session.getLudoGame().getCurrentRoll() + "}");
        }

        if (method.equals("POST") && path.startsWith("/games/ludo/roll")) {
            String sId = path.startsWith("/games/ludo/roll/") ? path.substring("/games/ludo/roll/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            Map<String, String> data = JSONParser.parse(body);
            int forcedRoll = -1;
            if (data.containsKey("forcedRoll")) {
                try { forcedRoll = Integer.parseInt(data.get("forcedRoll")); } catch (Exception ignored) {}
            }
            Map<String, Object> res = session.submitLudoRoll(email, forcedRoll);
            boolean ok = Boolean.TRUE.equals(res.get("success"));
            return ResponseBuilder.json(ok ? 200 : 400, JSONParser.stringify(res));
        }

        if (method.equals("POST") && path.startsWith("/games/ludo/move")) {
            String sId = path.startsWith("/games/ludo/move/") ? path.substring("/games/ludo/move/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            Map<String, String> data = JSONParser.parse(body);
            int tokenId = 0;
            try {
                tokenId = Integer.parseInt(data.getOrDefault("tokenId", "0"));
            } catch (Exception ignored) {}
            Map<String, Object> res = session.submitLudoMove(email, tokenId);
            boolean ok = Boolean.TRUE.equals(res.get("success"));
            return ResponseBuilder.json(ok ? 200 : 400, JSONParser.stringify(res));
        }

        // Snakes and Ladders game endpoints
        if (method.equals("GET") && (path.startsWith("/games/snl/board") || path.equals("/board"))) {
            String sId = path.startsWith("/games/snl/board/") ? path.substring("/games/snl/board/".length()) : queryParams.get("sessionId");
            GameSession session = (sId != null && !sId.isBlank()) ? GameManager.getSession(sId) : null;
            if (session != null) {
                return ResponseBuilder.json(200, session.getBoard().toJson());
            } else {
                com.robinhood.engine.snakesandladders.Board defaultBoard = new com.robinhood.engine.snakesandladders.Board();
                return ResponseBuilder.json(200, defaultBoard.toJson());
            }
        }

        if (method.equals("GET") && (path.startsWith("/games/snl/state") || path.equals("/state"))) {
            String sId = path.startsWith("/games/snl/state/") ? path.substring("/games/snl/state/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            StringBuilder pos = new StringBuilder("{");
            int c = 0;
            for (Map.Entry<String, Integer> e : session.getPlayerPositions().entrySet()) {
                if (c++ > 0) pos.append(",");
                pos.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            }
            pos.append("}");
            String currentTurn = session.getCurrentTurnPlayer();
            return ResponseBuilder.json(200,
                "{" +
                "\"sessionId\":\"" + sId + "\"," +
                "\"positions\":" + pos + "," +
                "\"currentTurn\":\"" + (currentTurn != null ? currentTurn : "") + "\"," +
                "\"isOver\":" + !session.isStarted() +
                "}"
            );
        }

        if (method.equals("POST") && (path.startsWith("/games/snl/roll") || path.equals("/roll"))) {
            String sId = path.startsWith("/games/snl/roll/") ? path.substring("/games/snl/roll/".length()) : queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sId);
            if (session == null) return ResponseBuilder.json(404, "{\"error\":\"Session not found\"}");
            if (!session.isPlayerTurn(email)) {
                return ResponseBuilder.json(403, "{\"error\":\"Not your turn\"}");
            }
            int roll = (int) (Math.random() * 6) + 1;
            boolean moved = session.submitMove(email, String.valueOf(roll));
            int newPos = session.getPlayerPositions().getOrDefault(email, 1);
            return ResponseBuilder.json(200,
                "{" +
                "\"roll\":" + roll + "," +
                "\"position\":" + newPos + "," +
                "\"success\":" + moved + "," +
                "\"nextPlayer\":\"" + (session.getCurrentTurnPlayer() != null ? session.getCurrentTurnPlayer() : "") + "\"" +
                "}"
            );
        }

        // KYC
        if (method.equals("POST") && path.equals("/kyc/apply")) {
            return KycController.apply(token, body);
        }
        if (method.equals("POST") && path.equals("/kyc/status")) {
            return KycController.status(token, body);
        }

        // Game management
        if (method.equals("POST") && path.equals("/create-game")) {
            Map<String, String> data = JSONParser.parse(body);
            int maxPlayers = Integer.parseInt(data.getOrDefault("maxPlayers", "2"));
            int wagerAmount = Integer.parseInt(data.getOrDefault("bet", "50"));
            if (wagerAmount < 0) return ResponseBuilder.json(400, "Invalid bet amount");

            String gameReq = data.getOrDefault("game", data.getOrDefault("gameType", "snl"));
            com.robinhood.engine.GameType gt = resolveGameType(gameReq);

            GameSession session = GameManager.createSession(maxPlayers, wagerAmount, gt);
            if (gt == com.robinhood.engine.GameType.KHASINO) {
                String modeReq = data.getOrDefault("khasinoMode", data.getOrDefault("mode", ""));
                if (!modeReq.isBlank()) {
                    String m = modeReq.toLowerCase();
                    if (m.contains("partner") || m.contains("2v2")) {
                        session.setKhasinoMode(com.robinhood.engine.khasino.KhasinoGame.Mode.FOUR_PLAYER_PARTNERSHIPS);
                    } else if (m.contains("single") || m.equals("4p") || m.equals("4")) {
                        session.setKhasinoMode(com.robinhood.engine.khasino.KhasinoGame.Mode.FOUR_PLAYER_SINGLES);
                    } else if (m.contains("3") || m.equals("3p")) {
                        session.setKhasinoMode(com.robinhood.engine.khasino.KhasinoGame.Mode.THREE_PLAYER);
                    } else {
                        session.setKhasinoMode(com.robinhood.engine.khasino.KhasinoGame.Mode.TWO_PLAYER);
                    }
                } else {
                    if (maxPlayers == 3) session.setKhasinoMode(com.robinhood.engine.khasino.KhasinoGame.Mode.THREE_PLAYER);
                    else if (maxPlayers == 4) session.setKhasinoMode(com.robinhood.engine.khasino.KhasinoGame.Mode.FOUR_PLAYER_PARTNERSHIPS);
                    else session.setKhasinoMode(com.robinhood.engine.khasino.KhasinoGame.Mode.TWO_PLAYER);
                }

                String diffReq = data.getOrDefault("difficulty", data.getOrDefault("aiDifficulty", "MEDIUM"));
                try {
                    session.setKhasinoDifficulty(com.robinhood.engine.khasino.KhasinoAi.Difficulty.valueOf(diffReq.toUpperCase()));
                } catch (Exception ignored) {}
            }

            Player player = new Player(email, token);
            boolean joined = session.addPlayer(player);
            if (!joined) {
                return ResponseBuilder.json(409, "Unable to join created game");
            }
            return ResponseBuilder.json(201, "{\"sessionId\":\"" + session.getSessionId() + "\",\"gameType\":\"" + gt.name() + "\"}");
        }

        if (method.equals("POST") && path.equals("/join-game")) {
            Map<String, String> data = JSONParser.parse(body);
            String sessionId = data.get("sessionId");
            GameSession session = GameManager.getSession(sessionId);
            if (session == null) return ResponseBuilder.json(404, "Session not found");

            Player player = new Player(email, token);
            boolean joined = session.addPlayer(player);
            if (!joined) return ResponseBuilder.json(409, "Cannot join (insufficient balance or game started/full)");

            return ResponseBuilder.json(200, "{\"joined\":true, \"sessionId\":\"" + sessionId + "\"}");
        }

        if (method.equals("POST") && path.equals("/start-game")) {
            Map<String, String> data = JSONParser.parse(body);
            String sessionId = data.get("sessionId");
            GameSession session = GameManager.getSession(sessionId);
            if (session == null) return ResponseBuilder.json(404, "Session not found");

            boolean started = session.start();
            if (!started) return ResponseBuilder.json(409, "Not enough players to start");

            return ResponseBuilder.json(200, "{\"started\":true}");
        }

        if (method.equals("POST") && path.equals("/move")) {
            Map<String, String> data = JSONParser.parse(body);
            String sessionId = data.get("sessionId");
            String move = data.get("move");
            String turnKey = data.get("turnKey");

            GameSession session = GameManager.getSession(sessionId);
            if (session == null) return ResponseBuilder.json(404, "Session not found");
            if (!session.validateTurnKey(email, turnKey)) return ResponseBuilder.json(403, "Invalid or missing turnKey");

            boolean accepted = session.submitMove(email, move);
            session.updateLastMove(email);
            if (!accepted) return ResponseBuilder.json(409, "Move rejected");

            return ResponseBuilder.json(200, "{\"accepted\":true}");
        }

        if (method.equals("POST") && path.equals("/quit")) {
            Map<String, String> data = JSONParser.parse(body);
            String sessionId = data.get("sessionId");
            GameSession session = GameManager.getSession(sessionId);
            if (session != null) {
                if (session.isStarted()) {
                    session.forfeit(email);
                } else {
                    session.removePlayer(email);
                }
            }
            return ResponseBuilder.json(200, "{\"quit\":true}");
        }

        if (method.equals("POST") && path.equals("/result")) {
            Map<String, String> data = JSONParser.parse(body);
            String sessionId = data.get("sessionId");
            GameSession session = GameManager.getSession(sessionId);
            if (session == null) return ResponseBuilder.json(404, "Session not found");

            Map<String, Integer> ranks = new HashMap<>();
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (!entry.getKey().equals("sessionId")) {
                    try {
                        ranks.put(entry.getKey(), Integer.parseInt(entry.getValue()));
                    } catch (NumberFormatException ignored) {}
                }
            }

            session.endGame(ranks);

            for (Map.Entry<String, Integer> rankEntry : ranks.entrySet()) {
                String pEmail = rankEntry.getKey();
                int rank = rankEntry.getValue();
                int xp = session.getPlayerXP().getOrDefault(pEmail, 0);
                int tokensWon = WalletController.getBalance(pEmail);
                GameResultLogger.log(new GameResult(sessionId, pEmail, rank, xp, tokensWon));
                LeaderboardManager.addXP(pEmail, xp);
            }

            GameManager.removeSession(sessionId);
            return ResponseBuilder.json(200, "{\"resultsSaved\":true}");
        }

        if (method.equals("GET") && path.equals("/leaderboard/weekly")) {
            return ResponseBuilder.json(200, LeaderboardManager.getTop100Json());
        }

        if (method.equals("POST") && path.equals("/matchmake")) {
            Map<String, String> data = JSONParser.parse(body);
            int wager = Integer.parseInt(data.getOrDefault("bet", "50"));
            int players = Integer.parseInt(data.getOrDefault("players", "3"));
            if (wager <= 0 || (players != 3 && players != 4)) {
                return ResponseBuilder.json(400, "Invalid bet or player count");
            }

            int xp = LeaderboardManager.getXP(email);
            Player player = new Player(email, token);

            if (WalletController.getBalance(email) < wager) {
                return ResponseBuilder.json(403, "Insufficient balance for matchmaking");
            }

            String matchedSessionId = Matchmaker.requestMatch(player, wager, players, xp);
            if (matchedSessionId != null) {
                return ResponseBuilder.json(200, "{\"matched\":true, \"sessionId\":\"" + matchedSessionId + "\"}");
            } else {
                return ResponseBuilder.json(202, "{\"matched\":false, \"message\":\"Waiting for opponents\"}");
            }
        }

        if (method.equals("GET") && path.equals("/audit-log")) {
            String sessionId = queryParams.get("sessionId");
            GameSession session = GameManager.getSession(sessionId);
            if (session == null) return ResponseBuilder.json(404, "Session not found");

            if (session.getPlayers().stream().noneMatch(p -> p.getEmail().equals(email))) {
                return ResponseBuilder.json(403, "You are not part of this session");
            }

            Map<Integer, String> moves = session.getMovesLog();
            StringBuilder json = new StringBuilder("[");
            for (Map.Entry<Integer, String> entry : moves.entrySet()) {
                json.append("{\"turn\":").append(entry.getKey())
                    .append(",\"move\":\"").append(entry.getValue()).append("\"},");
            }
            if (!moves.isEmpty()) json.setLength(json.length() - 1);
            json.append("]");
            return ResponseBuilder.json(200, json.toString());
        }

        return ResponseBuilder.json(404, "Route not found");
    }

    private static HttpRequest parseRequest(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null || line.isEmpty()) return null;

        String[] requestLine = line.split(" ");
        if (requestLine.length < 2) return null;

        String method = requestLine[0];
        String rawPath = requestLine[1];

        Map<String, String> headers = new HashMap<>();
        int contentLength = 0;

        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int idx = line.indexOf(":");
            if (idx > 0) {
                String key = line.substring(0, idx).trim();
                String val = line.substring(idx + 1).trim();
                headers.put(key, val);
                if (key.equalsIgnoreCase("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(val);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        StringBuilder body = new StringBuilder();
        if (contentLength > 0) {
            for (int i = 0; i < contentLength; i++) {
                body.append((char) reader.read());
            }
        } else {
            while (reader.ready()) {
                body.append((char) reader.read());
            }
        }

        return new HttpRequest(method, rawPath, headers, body.toString());
    }

    private static HttpRequest parseRequest(String rawRequest) {
        if (rawRequest == null || rawRequest.isBlank()) return null;

        int sepIdx = rawRequest.indexOf("\r\n\r\n");
        int sepLen = 4;
        if (sepIdx == -1) {
            sepIdx = rawRequest.indexOf("\n\n");
            sepLen = 2;
        }

        String headersPart = (sepIdx != -1) ? rawRequest.substring(0, sepIdx) : rawRequest;
        String body = (sepIdx != -1 && sepIdx + sepLen <= rawRequest.length()) ? rawRequest.substring(sepIdx + sepLen) : "";

        String[] headerLines = headersPart.split("\r?\n");
        if (headerLines.length == 0 || headerLines[0].isBlank()) return null;

        String[] requestLine = headerLines[0].split(" ");
        if (requestLine.length < 2) return null;

        String method = requestLine[0];
        String rawPath = requestLine[1];

        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < headerLines.length; i++) {
            String line = headerLines[i];
            int idx = line.indexOf(":");
            if (idx > 0) {
                String key = line.substring(0, idx).trim();
                String val = line.substring(idx + 1).trim();
                headers.put(key, val);
                headers.put(key.toLowerCase(), val);
            }
        }

        return new HttpRequest(method, rawPath, headers, body);
    }

    private static class HttpRequest {
        String method;
        String rawPath;
        String path;
        Map<String, String> queryParams;
        Map<String, String> headers;
        String body;

        HttpRequest(String method, String rawPath, Map<String, String> headers, String body) {
            this.method = method;
            this.rawPath = rawPath;
            this.headers = headers;
            this.body = body != null ? body : "";

            this.queryParams = new HashMap<>();
            int qIdx = rawPath.indexOf("?");
            if (qIdx >= 0) {
                this.path = rawPath.substring(0, qIdx);
                String queryStr = rawPath.substring(qIdx + 1);
                String[] pairs = queryStr.split("&");
                for (String p : pairs) {
                    String[] kv = p.split("=", 2);
                    if (kv.length == 2) {
                        try {
                            queryParams.put(
                                URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                                URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                            );
                        } catch (Exception ignored) {
                            queryParams.put(kv[0], kv[1]);
                        }
                    } else if (kv.length == 1 && !kv[0].isBlank()) {
                        queryParams.put(kv[0], "");
                    }
                }
            } else {
                this.path = rawPath;
            }
        }
    }

    private static Path clientDirPath = null;

    private static Path getClientDir() {
        if (clientDirPath != null && Files.isDirectory(clientDirPath)) return clientDirPath;
        Path[] candidates = new Path[]{
            Path.of("client", "web vanilla"),
            Path.of("..", "client", "web vanilla"),
            Path.of("Vanilla-JDK-API-Example", "client", "web vanilla")
        };
        for (Path c : candidates) {
            if (Files.isDirectory(c)) {
                clientDirPath = c.toAbsolutePath().normalize();
                return clientDirPath;
            }
        }
        return Path.of("client", "web vanilla").toAbsolutePath().normalize();
    }

    private static String serveStaticFile(String path) {
        if (!path.equals("/") && !path.equals("/index.html") &&
            !path.startsWith("/css/") && !path.startsWith("/scripts/") &&
            !path.startsWith("/assets/") && !path.endsWith(".html") &&
            !path.endsWith(".css") && !path.endsWith(".js") &&
            !path.endsWith(".svg") && !path.endsWith(".json") &&
            !path.endsWith(".png") && !path.endsWith(".ico")) {
            return null;
        }

        try {
            Path base = getClientDir();
            String rel = path.equals("/") ? "index.html" : path.startsWith("/") ? path.substring(1) : path;
            Path file = base.resolve(rel).normalize();
            if (file.startsWith(base) && Files.exists(file) && !Files.isDirectory(file)) {
                byte[] bytes = Files.readAllBytes(file);
                String mime = getMimeType(file.getFileName().toString());
                return ResponseBuilder.file(mime, bytes);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String getMimeType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "text/plain; charset=utf-8";
    }

    private static com.robinhood.engine.GameType resolveGameType(String str) {
        if (str == null) return com.robinhood.engine.GameType.SNAKES_AND_LADDERS;
        String s = str.trim().toLowerCase();
        if (s.contains("uno")) return com.robinhood.engine.GameType.UNO;
        if (s.contains("crazy") || s.contains("eight") || s.equals("c8") || s.equals("crazy8"))
            return com.robinhood.engine.GameType.CRAZY_EIGHT;
        if (s.contains("khasino") || s.contains("kasino") || s.contains("kasi"))
            return com.robinhood.engine.GameType.KHASINO;
        if (s.contains("ludo"))
            return com.robinhood.engine.GameType.LUDO;
        return com.robinhood.engine.GameType.SNAKES_AND_LADDERS;
    }
}
