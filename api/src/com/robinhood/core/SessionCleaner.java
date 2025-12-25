package com.robinhood.core;

import com.robinhood.game.GameManager;
import com.robinhood.game.GameSession;

import java.util.TimerTask;

public class SessionCleaner extends TimerTask {
    @Override
	public void run() {
		for (GameSession session : GameManager.getAllSessions()) {
			if (!session.isStarted()) continue;

			if (session.currentPlayerTimedOut()) {
				String player = session.getCurrentPlayer();
				System.out.println("[Timeout] Player: " + player + " in session " + session.getSessionId());

				// Rank player last (auto-loss), others randomly
				Map<String, Integer> finalRanks = new HashMap<>();
				int rank = 1;
				for (var p : session.getPlayers()) {
					if (p.getEmail().equals(player)) {
						finalRanks.put(p.getEmail(), 4); // worst rank
					} else {
						finalRanks.put(p.getEmail(), rank++);
					}
				}

				session.endGame(finalRanks);
				GameManager.removeSession(session.getSessionId());
			}
		}
	}

}
