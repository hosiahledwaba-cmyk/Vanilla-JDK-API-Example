package com.robinhood.core;

import com.robinhood.controller.AuthController;
import com.robinhood.security.AuthManager;
import com.robinhood.util.ResponseBuilder;

import java.io.*;
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

        String method = req.method;
        String path = req.path;
        Map<String, String> headers = req.headers;
        String body = req.body;

        // Public endpoints
        if (method.equals("POST") && path.equals("/signup")) {
            writer.write(AuthController.signup(body));
            writer.flush();
            return;
        }

        if (method.equals("POST") && path.equals("/login")) {
            writer.write(AuthController.login(body));
            writer.flush();
            return;
        }

        // Protected endpoints
        String authHeader = headers.getOrDefault("Authorization", "");
        if (!authHeader.startsWith("Bearer ")) {
            writer.write(ResponseBuilder.json(401, "Missing or invalid token"));
            writer.flush();
            return;
        }

        String token = authHeader.substring(7);
        if (!AuthManager.validateToken(token)) {
            writer.write(ResponseBuilder.json(401, "Invalid or expired token"));
            writer.flush();
            return;
        }

        // Example protected route
        if (method.equals("GET") && path.equals("/profile")) {
            String email = AuthManager.getEmailFromToken(token);
            writer.write(ResponseBuilder.json(200, "{\"email\": \"" + email + "\"}"));
            writer.flush();
            return;
        }

        writer.write(ResponseBuilder.json(404, "Route not found"));
        writer.flush();
    }

    public static String handleRequest(String rawRequest) {
        String[] lines = rawRequest.split("\r\n");
        if (lines.length == 0) return ResponseBuilder.json(400, "Bad Request");

        HttpRequest req = parseRequest(rawRequest);
        if (req == null) return ResponseBuilder.json(400, "Bad Request");

        String method = req.method;
        String path = req.path;
        Map<String, String> headers = req.headers;
        String body = req.body;
		
		MetricsCollector.incrementRequests();
		MetricsCollector.setActiveSessions(GameManager.getAllSessions().size());

		// expose metrics:
		if (method.equals("GET") && path.equals("/metrics")) {
			return "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\n" +
				   MetricsCollector.scrape();
		}

        if (method.equals("POST") && path.equals("/signup")) {
            return AuthController.signup(body);
        }

        if (method.equals("POST") && path.equals("/login")) {
            return AuthController.login(body);
        }

        String authHeader = headers.getOrDefault("Authorization", "");
        if (!authHeader.startsWith("Bearer ") || !AuthManager.validateToken(authHeader.substring(7))) {
            return ResponseBuilder.json(401, "Missing or invalid token");
        }

		if (path.startsWith("/admin/")) {
			switch (path) {
			  case "/admin/stats":           return AdminController.stats(token);
			  case "/admin/users":           return AdminController.listUsers(token);
			  case "/admin/user":            return AdminController.userDetails(token, queryParams);
			  case "/admin/withdrawals":     return AdminController.withdrawals(token, queryParams);
			  case "/admin/withdrawals/approve": return AdminController.approveWithdrawal(token, body);
			  case "/admin/withdrawals/reject":  return AdminController.rejectWithdrawal(token, body);
			  case "/admin/sessions":        return AdminController.listSessions(token);
			  case "/admin/session/history": return AdminController.sessionHistory(token, queryParams);
			  case "/admin/kill":            return AdminController.killSession(token, body);
			  case "/admin/shutdown":        return AdminController.shutdown(token);
			  default:
				return ResponseBuilder.json(404, "Admin route not found");
			}
		}

		// At the top of handleRequest, after parsing method/path:
		if ("GET".equals(method) && path.startsWith("/stream")) {
			Map<String, String> params = parseQuery(path);
			String sessionId = params.get("sessionId");
			String token = params.get("token");
			String email = AuthManager.getEmailFromToken(token);

			// validate session & membership
			GameSession session = GameManager.getSession(sessionId);
			if (session == null || session.getPlayers().stream().noneMatch(p -> p.getEmail().equals(email))) {
				writer.write(ResponseBuilder.json(403, "Forbidden"));
				writer.flush();
				return;
			}

			// upgrade to SSE
			RealTimeDispatcher.subscribe(sessionId, clientChannel); 
			// Note: here we abandon the usual HTTP response flow—do NOT writer.close()
			return;  // remaining I/O is handled by RealTimeDispatcher
		}


        String token = authHeader.substring(7);

        if (method.equals("GET") && path.equals("/profile")) {
            String email = AuthManager.getEmailFromToken(token);
            return ResponseBuilder.json(200, "{\"email\": \"" + email + "\"}");
        }
		
		if (method.equals("POST") && path.equals("/create-game")) {
			GameSession session = GameManager.createSession(4);
			return ResponseBuilder.json(201, "{\"sessionId\": \"" + session.getSessionId() + "\"}");
		}

		else if (method.equals("POST") && path.equals("/join-game")) {
			String sessionId = bodyMap.get("sessionId");
			GameSession session = GameManager.getSession(sessionId);
			if (session == null) return ResponseBuilder.json(404, "Session not found");

			String email = AuthManager.getEmailFromToken(token);
			Player player = new Player(email, token);

			boolean joined = session.addPlayer(player);
			if (!joined) return ResponseBuilder.json(409, "Join failed");

			return ResponseBuilder.json(200, "Joined game " + sessionId);
		}

		else if (method.equals("POST") && path.equals("/start-game")) {
			String sessionId = bodyMap.get("sessionId");
			GameSession session = GameManager.getSession(sessionId);
			if (session == null) return ResponseBuilder.json(404, "Session not found");

			session.start();
			return ResponseBuilder.json(200, "Game started");
		}

		else if (method.equals("POST") && path.equals("/quit")) {
			String sessionId = bodyMap.get("sessionId");
			GameSession session = GameManager.getSession(sessionId);
			if (session != null) {
				String email = AuthManager.getEmailFromToken(token);
				session.removePlayer(email);
			}
			return ResponseBuilder.json(200, "Quit session");
		}

		if (method.equals("POST") && path.equals("/deposit")) {
			return WalletController.deposit(token, body.toString());
		}

		else if (method.equals("GET") && path.equals("/balance")) {
			return WalletController.balance(token);
		}

		// inside Router.handleRequest

		if (method.equals("POST") && path.equals("/create-game")) {
			Map<String, String> data = JSONParser.parse(body.toString());
			int maxPlayers = 3; // or configurable
			int wagerAmount = Integer.parseInt(data.getOrDefault("bet", "50"));

			// Validate wager > 0
			if (wagerAmount <= 0) {
				return ResponseBuilder.json(400, "Invalid bet amount");
			}

			String email = AuthManager.getEmailFromToken(token);
			GameSession session = GameManager.createSession(maxPlayers, wagerAmount);

			// Auto join creator
			Player player = new Player(email, token);
			boolean joined = session.addPlayer(player);
			if (!joined) {
				return ResponseBuilder.json(409, "Unable to join game due to insufficient balance or session started");
			}

			return ResponseBuilder.json(201, "{\"sessionId\":\"" + session.getSessionId() + "\"}");
		}

		else if (method.equals("POST") && path.equals("/join-game")) {
			Map<String, String> data = JSONParser.parse(body.toString());
			String sessionId = data.get("sessionId");
			GameSession session = GameManager.getSession(sessionId);
			if (session == null) return ResponseBuilder.json(404, "Session not found");

			String email = AuthManager.getEmailFromToken(token);
			Player player = new Player(email, token);
			boolean joined = session.addPlayer(player);
			if (!joined) return ResponseBuilder.json(409, "Cannot join (insufficient balance or game started/full)");

			return ResponseBuilder.json(200, "Joined session");
		}

		else if (method.equals("POST") && path.equals("/start-game")) {
			Map<String, String> data = JSONParser.parse(body.toString());
			String sessionId = data.get("sessionId");
			GameSession session = GameManager.getSession(sessionId);
			if (session == null) return ResponseBuilder.json(404, "Session not found");

			boolean started = session.start();
			if (!started) return ResponseBuilder.json(409, "Not enough players to start");

			return ResponseBuilder.json(200, "Game started");
		}

		else if (method.equals("POST") && path.equals("/move")) {
			String sessionId = data.get("sessionId");
			String move = data.get("move");
			String turnKey = data.get("turnKey");

			GameSession session = GameManager.getSession(sessionId);
			if (!session.validateTurnKey(email, turnKey)) return ResponseBuilder.json(403, "Invalid or missing turnKey");

			boolean accepted = session.submitMove(email, move);
			session.updateLastMove(email);
			if (!accepted) return ResponseBuilder.json(409, "Move rejected");

			return ResponseBuilder.json(200, "Move accepted");
		}


		if (method.equals("POST") && path.equals("/result")) {
			Map<String, String> data = JSONParser.parse(body.toString());
			String sessionId = data.get("sessionId");

			GameSession session = GameManager.getSession(sessionId);
			if (session == null) return ResponseBuilder.json(404, "Session not found");

			// Parse final ranks from payload
			Map<String, Integer> ranks = new HashMap<>();
			for (Map.Entry<String, String> entry : data.entrySet()) {
				if (!entry.getKey().equals("sessionId")) {
					ranks.put(entry.getKey(), Integer.parseInt(entry.getValue()));
				}
			}

			session.endGame(ranks);

			// Log results and XP
			for (Map.Entry<String, Integer> rankEntry : ranks.entrySet()) {
				String email = rankEntry.getKey();
				int rank = rankEntry.getValue();
				int xp = session.getPlayerXP().getOrDefault(email, 0);
				int tokensWon = WalletController.getBalance(email); // Approximate
				GameResultLogger.log(new GameResult(sessionId, email, rank, xp, tokensWon));
				LeaderboardManager.addXP(email, xp);
			}

			GameManager.removeSession(sessionId);
			return ResponseBuilder.json(200, "Results saved");
		}

		else if (method.equals("GET") && path.equals("/leaderboard/weekly")) {
			return ResponseBuilder.json(200, LeaderboardManager.getTop100Json());
		}

		if (method.equals("POST") && path.equals("/matchmake")) {
			Map<String, String> data = JSONParser.parse(body.toString());
			int wager = Integer.parseInt(data.getOrDefault("bet", "50"));
			int players = Integer.parseInt(data.getOrDefault("players", "3"));
			if (wager <= 0 || (players != 3 && players != 4)) {
				return ResponseBuilder.json(400, "Invalid bet or player count");
			}

			String email = AuthManager.getEmailFromToken(token);
			int xp = LeaderboardManager.getXP(email);
			Player player = new Player(email, token);

			// Check wallet before entering matchmaking
			if (WalletController.getBalance(email) < wager) {
				return ResponseBuilder.json(403, "Insufficient balance for matchmaking");
			}

			String sessionId = Matchmaker.requestMatch(player, wager, players, xp);
			if (sessionId != null) {
				return ResponseBuilder.json(200, "{\"matched\":true, \"sessionId\":\"" + sessionId + "\"}");
			} else {
				return ResponseBuilder.json(202, "{\"matched\":false, \"message\":\"Waiting for opponents\"}");
			}
		}
		
		if (method.equals("GET") && path.equals("/audit-log")) {
			String sessionId = queryParams.get("sessionId");
			GameSession session = GameManager.getSession(sessionId);
			if (session == null) return ResponseBuilder.json(404, "Session not found");
			
			if (!session.getPlayers().stream().anyMatch(p -> p.getEmail().equals(email))) {
				return ResponseBuilder.json(403, "You are not part of this session");
			}

			Map<Integer, String> moves = session.getMovesLog();
			StringBuilder json = new StringBuilder("[");
			for (Map.Entry<Integer, String> entry : moves.entrySet()) {
				json.append("{\"turn\":").append(entry.getKey())
					.append(",\"move\":\"").append(entry.getValue()).append("\"},");
			}
			if (moves.size() > 0) json.setLength(json.length() - 1);
			json.append("]");
			return ResponseBuilder.json(200, json.toString());
		}

		if (method.equals("POST") && path.equals("/withdraw")) {
			return WithdrawalController.request(token, body.toString());
		}
		else if (method.equals("GET") && path.equals("/withdraw/history")) {
			return WithdrawalController.history(token);
		}

		// Admin routes
		if (path.startsWith("/admin/withdraw") && token.equals(ConfigLoader.get("admin.token"))) {
			if (path.equals("/admin/withdraw/pending")) {
				return ResponseBuilder.json(200, WithdrawalManager.getAllPending());
			}
			if (path.equals("/admin/withdraw/approve")) {
				// parse id, approve in DB, set processed_at, return JSON
			}
			if (path.equals("/admin/withdraw/reject")) {
				// parse id, reject, refund wallet via atomic transaction
			}
		}

		String clientIp = ((InetSocketAddress)client.getRemoteAddress()).getAddress().getHostAddress();
		if (!RateLimiter.allow(clientIp)) {
			writer.write(ResponseBuilder.json(429, "Too many requests"));
			writer.flush();
			client.close();
			return;
		}
		
		if (method.equals("GET") && path.equals("/health")) {
			return "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nOK";
		}
		
		// KYC
		if (method.equals("POST") && path.equals("/kyc/apply")) {
		  return KycController.apply(token, body.toString());
		}
		else if (method.equals("POST") && path.equals("/kyc/status")) {
		  return KycController.status(token, body.toString());
		}

		// Payment
		// Deposit (gateway param in query, e.g., /payment/deposit?gateway=stripe)
		if (method.equals("POST") && path.equals("/payment/deposit")) {
			String gateway = query.get("gateway");
			if (gateway == null) return ResponseBuilder.json(400, "Missing gateway param");
			return PaymentController.deposit(gateway, token, body.toString());
		}

		// Webhook (gateway param in query, e.g., /payment/webhook?gateway=ozow)
		else if (method.equals("POST") && path.equals("/payment/webhook")) {
			String gateway = query.get("gateway");
			if (gateway == null) return ResponseBuilder.json(400, "Missing gateway param");
			return PaymentController.webhook(gateway, body.toString(), headers);
		}


        return ResponseBuilder.json(404, "Route not found");
    }

    private static HttpRequest parseRequest(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null || line.isEmpty()) return null;

        String[] requestLine = line.split(" ");
        if (requestLine.length < 2) return null;

        String method = requestLine[0];
        String path = requestLine[1];

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

        return new HttpRequest(method, path, headers, body.toString());
    }

    private static HttpRequest parseRequest(String rawRequest) {
        String[] lines = rawRequest.split("\r\n");
        if (lines.length == 0) return null;

        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 2) return null;

        String method = requestLine[0];
        String path = requestLine[1];

        Map<String, String> headers = new HashMap<>();
        StringBuilder body = new StringBuilder();

        boolean inBody = false;
        for (String line : lines) {
            if (inBody) {
                body.append(line);
            }
            if (line.isEmpty()) {
                inBody = true;
            } else if (!inBody && line.contains(":")) {
                String[] kv = line.split(":", 2);
                headers.put(kv[0].trim(), kv[1].trim());
            }
        }

        return new HttpRequest(method, path, headers, body.toString());
    }

    private static class HttpRequest {
        String method;
        String path;
        Map<String, String> headers;
        String body;

        HttpRequest(String method, String path, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }
    }
}
