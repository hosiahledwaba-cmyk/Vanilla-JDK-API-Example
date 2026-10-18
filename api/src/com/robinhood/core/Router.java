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
import java.util.HashMap;
import java.util.Map;

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
        if (method.equals("GET")) {
            String staticFileResp = serveStaticFile(path);
            if (staticFileResp != null) {
                return staticFileResp;
            }
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

        // Real-time SSE Stream Endpoint (/stream or /ws/{sessionId})
        if (method.equals("GET") && (path.equals("/stream") || path.startsWith("/ws/"))) {
            String sessionId = path.startsWith("/ws/") ? path.substring("/ws/".length()) : queryParams.get("sessionId");
            String token = queryParams.get("token");
            if (token == null && (headers.containsKey("Authorization") || headers.containsKey("authorization"))) {
                String authH = headers.getOrDefault("Authorization", headers.get("authorization"));
                if (authH.startsWith("Bearer ")) token = authH.substring(7).trim();
            }
            if (token == null || !AuthManager.validateToken(token)) {
                return ResponseBuilder.json(401, "Invalid token");
            }
            String email = AuthManager.getEmailFromToken(token);
            GameSession session = GameManager.getSession(sessionId);
            if (session == null || session.getPlayers().stream().noneMatch(p -> p.getEmail().equals(email))) {
                return ResponseBuilder.json(403, "Forbidden");
            }

            if (clientChannel != null) {
                try {
                    RealTimeDispatcher.subscribe(sessionId, clientChannel);
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
        if (method.equals("POST") && (path.equals("/games/queue/join") || path.equals("/queue/join"))) {
            Map<String, String> data = JSONParser.parse(body);
            int bet = 50;
            try {
                bet = Integer.parseInt(data.getOrDefault("bet", "50"));
            } catch (Exception ignored) {}
            if (WalletController.getBalance(email) < bet) {
                return ResponseBuilder.json(400, "{\"status\":\"error\",\"message\":\"Insufficient balance for bet of " + bet + " tokens\"}");
            }
            Player player = new Player(email, token);
            String matchedSession = Matchmaker.joinQueue(player, bet);
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
            int maxPlayers = Integer.parseInt(data.getOrDefault("maxPlayers", "3"));
            int wagerAmount = Integer.parseInt(data.getOrDefault("bet", "50"));
            if (wagerAmount < 0) return ResponseBuilder.json(400, "Invalid bet amount");

            GameSession session = GameManager.createSession(maxPlayers, wagerAmount);
            Player player = new Player(email, token);
            boolean joined = session.addPlayer(player);
            if (!joined) {
                return ResponseBuilder.json(409, "Unable to join created game");
            }
            return ResponseBuilder.json(201, "{\"sessionId\":\"" + session.getSessionId() + "\"}");
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
                session.removePlayer(email);
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
}
