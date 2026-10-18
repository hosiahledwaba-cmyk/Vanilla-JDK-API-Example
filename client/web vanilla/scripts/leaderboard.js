// client/web vanilla/scripts/leaderboard.js

import { renderNavTabs, bindNavTabs } from './main.js';

export const LeaderboardModule = {
  async render(container) {
    const token = localStorage.getItem('token');

    container.innerHTML = `
      <div>
        ${renderNavTabs('leaderboard')}

        <div class="glass-card">
          <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.5rem;">
            <div>
              <h2 style="font-size: 1.6rem; font-weight: 800; letter-spacing: -0.02em;">Weekly Leaderboard</h2>
              <p style="color: var(--text-secondary); font-size: 0.95rem;">
                Top players ranked by total XP earned across game sessions
              </p>
            </div>
            <button id="refreshLeaderboardBtn" class="btn btn-secondary" style="font-size: 0.85rem; padding: 0.45rem 0.85rem;">
              🔄 Refresh Ranks
            </button>
          </div>

          <div style="overflow-x: auto;">
            <table class="data-table">
              <thead>
                <tr>
                  <th style="width: 80px;">Rank</th>
                  <th>Player</th>
                  <th>Tier</th>
                  <th style="text-align: right;">Total XP</th>
                </tr>
              </thead>
              <tbody id="leaderboardBody">
                <tr>
                  <td colspan="4" style="text-align: center; color: var(--text-muted); padding: 2rem;">
                    Loading leaderboard rankings...
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    `;

    bindNavTabs(container);

    const tbody = document.getElementById('leaderboardBody');

    async function fetchLeaderboard() {
      try {
        const res = await fetch('/leaderboard/weekly', {
          headers: { Authorization: `Bearer ${token}` }
        });
        if (res.ok) {
          const list = await res.json();
          renderRows(list);
        } else {
          tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color: var(--text-muted); padding: 2rem;">No entries yet. Be the first to win a game!</td></tr>`;
        }
      } catch (err) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color: #fb7185; padding: 2rem;">Error fetching leaderboard.</td></tr>`;
      }
    }

    function renderRows(list) {
      if (!list || list.length === 0) {
        tbody.innerHTML = `
          <tr>
            <td colspan="4" style="text-align: center; color: var(--text-secondary); padding: 2rem;">
              No players on the leaderboard yet. Play and win a match to climb the ranks!
            </td>
          </tr>
        `;
        return;
      }

      tbody.innerHTML = list.map((item, idx) => {
        const rank = idx + 1;
        let rankBadge = `<span class="rank-badge">${rank}</span>`;
        if (rank === 1) rankBadge = `<span class="rank-badge rank-1">🥇</span>`;
        else if (rank === 2) rankBadge = `<span class="rank-badge rank-2">🥈</span>`;
        else if (rank === 3) rankBadge = `<span class="rank-badge rank-3">🥉</span>`;

        const xp = item.xp ?? 0;
        let tier = '🥉 Bronze';
        if (xp >= 500) tier = '👑 Diamond';
        else if (xp >= 200) tier = '💎 Platinum';
        else if (xp >= 100) tier = '🥇 Gold';
        else if (xp >= 50) tier = '🥈 Silver';

        const email = item.email || item.user || 'Unknown';
        const initial = email.charAt(0).toUpperCase();

        return `
          <tr>
            <td>${rankBadge}</td>
            <td>
              <div style="display: flex; align-items: center; gap: 0.65rem;">
                <div class="user-avatar" style="width: 28px; height: 28px; font-size: 0.85rem;">${initial}</div>
                <div style="font-weight: 600;">${email}</div>
              </div>
            </td>
            <td><span style="font-size: 0.85rem; font-weight: 600;">${tier}</span></td>
            <td style="text-align: right; font-family: var(--font-mono); font-weight: 700; color: #fbbf24;">
              ${xp.toLocaleString()} XP
            </td>
          </tr>
        `;
      }).join('');
    }

    document.getElementById('refreshLeaderboardBtn').onclick = () => {
      fetchLeaderboard();
      window.showToast('Leaderboard refreshed', 'info');
    };

    fetchLeaderboard();
  }
};
