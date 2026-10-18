// client/web vanilla/scripts/history.js

import { renderNavTabs, bindNavTabs } from './main.js';

export const HistoryModule = {
  async render(container) {
    const token = localStorage.getItem('token');

    container.innerHTML = `
      <div>
        ${renderNavTabs('history')}

        <div class="glass-card">
          <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.5rem;">
            <div>
              <h2 style="font-size: 1.6rem; font-weight: 800; letter-spacing: -0.02em;">Activity & History</h2>
              <p style="color: var(--text-secondary); font-size: 0.95rem;">
                Audit log of deposits, withdrawals, and game wagers
              </p>
            </div>
            <button id="refreshHistoryBtn" class="btn btn-secondary" style="font-size: 0.85rem; padding: 0.45rem 0.85rem;">
              🔄 Refresh Log
            </button>
          </div>

          <div style="overflow-x: auto;">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Description</th>
                  <th>Amount</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody id="historyBody">
                <tr>
                  <td colspan="4" style="text-align: center; color: var(--text-muted); padding: 2rem;">
                    Loading activity history...
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    `;

    bindNavTabs(container);

    const tbody = document.getElementById('historyBody');

    async function fetchHistory() {
      try {
        const res = await fetch('/wallet/history', {
          headers: { Authorization: `Bearer ${token}` }
        });
        if (res.ok) {
          const list = await res.json();
          renderRows(list);
        } else {
          tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color: var(--text-muted); padding: 2rem;">No transaction history found yet.</td></tr>`;
        }
      } catch (err) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color: #fb7185; padding: 2rem;">Error fetching history.</td></tr>`;
      }
    }

    function renderRows(list) {
      if (!list || list.length === 0) {
        tbody.innerHTML = `
          <tr>
            <td colspan="4" style="text-align: center; color: var(--text-secondary); padding: 2rem;">
              No transactions recorded yet. Fund your wallet or play a match to view history.
            </td>
          </tr>
        `;
        return;
      }

      tbody.innerHTML = list.map((item) => {
        const type = item.type || (item.status ? 'Withdrawal' : 'Transaction');
        const amount = item.amount ?? 0;
        const status = item.status || 'SUCCESS';
        const statusColor = status === 'APPROVED' || status === 'SUCCESS' ? '#34d399' : status === 'PENDING' ? '#fbbf24' : '#fb7185';
        const icon = type.toLowerCase().includes('withdraw') ? '💸' : '🪙';

        return `
          <tr>
            <td>
              <div style="display: flex; align-items: center; gap: 0.5rem; font-weight: 600;">
                <span>${icon}</span>
                <span>${type}</span>
              </div>
            </td>
            <td style="color: var(--text-secondary); font-size: 0.85rem;">
              ${item.description || item.method || 'Standard transaction'}
            </td>
            <td style="font-family: var(--font-mono); font-weight: 700; color: #fbbf24;">
              ${amount} tokens
            </td>
            <td>
              <span style="display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 0.75rem; font-weight: 700; background: rgba(255,255,255,0.05); color: ${statusColor};">
                ${status}
              </span>
            </td>
          </tr>
        `;
      }).join('');
    }

    document.getElementById('refreshHistoryBtn').onclick = () => {
      fetchHistory();
      window.showToast('History refreshed', 'info');
    };

    fetchHistory();
  }
};
