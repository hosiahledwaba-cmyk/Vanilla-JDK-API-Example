// client/web vanilla/scripts/wallet.js

import { renderNavTabs, bindNavTabs } from './main.js';

export const WalletModule = {
  async render(container) {
    const token = localStorage.getItem('token');
    const user = localStorage.getItem('user') || 'Player';

    container.innerHTML = `
      <div>
        ${renderNavTabs('wallet')}

        <div class="glass-card">
          <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.5rem;">
            <div>
              <h2 style="font-size: 1.6rem; font-weight: 800; letter-spacing: -0.02em;">Token Wallet</h2>
              <p style="color: var(--text-secondary); font-size: 0.95rem;">Manage your game balance for Snakes & Ladders wagers</p>
            </div>
            <button id="refreshBalanceBtn" class="btn btn-secondary" style="font-size: 0.85rem; padding: 0.45rem 0.85rem;">
              🔄 Refresh Balance
            </button>
          </div>

          <div class="balance-card">
            <div>
              <div style="font-size: 0.85rem; text-transform: uppercase; font-weight: 700; color: var(--text-secondary); margin-bottom: 0.25rem;">
                Available Token Balance
              </div>
              <div class="value" id="walletBalance">...</div>
            </div>
            <div style="font-size: 3rem;">🪙</div>
          </div>

          <div class="wallet-grid">
            <!-- Deposit Section -->
            <div style="background: var(--surface-elevated); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 1.5rem;">
              <h3 style="font-size: 1.15rem; font-weight: 700; margin-bottom: 0.5rem; display: flex; align-items: center; gap: 0.5rem;">
                <span>💳</span> Deposit Tokens
              </h3>
              <p style="color: var(--text-secondary); font-size: 0.85rem; margin-bottom: 1rem;">
                Fund your account with instant test credit
              </p>

              <form id="depositForm">
                <div class="form-group">
                  <label class="form-label">Quick Amount</label>
                  <div class="chip-select-group" id="depositQuickChips">
                    <button type="button" class="chip-btn" data-amt="50">+50</button>
                    <button type="button" class="chip-btn active" data-amt="100">+100</button>
                    <button type="button" class="chip-btn" data-amt="250">+250</button>
                    <button type="button" class="chip-btn" data-amt="500">+500</button>
                  </div>
                </div>

                <div class="form-group">
                  <label class="form-label" for="depositAmount">Amount (tokens)</label>
                  <input type="number" id="depositAmount" class="form-input" value="100" min="1" step="1" required />
                </div>

                <button type="submit" id="depositBtn" class="btn btn-primary" style="width: 100%;">
                  Deposit Now
                </button>
              </form>
            </div>

            <!-- Withdraw Section -->
            <div style="background: var(--surface-elevated); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 1.5rem;">
              <h3 style="font-size: 1.15rem; font-weight: 700; margin-bottom: 0.5rem; display: flex; align-items: center; gap: 0.5rem;">
                <span>💸</span> Cash Out Tokens
              </h3>
              <p style="color: var(--text-secondary); font-size: 0.85rem; margin-bottom: 1rem;">
                Withdraw your match earnings anytime
              </p>

              <form id="withdrawForm">
                <div class="form-group">
                  <label class="form-label" for="withdrawAmount">Amount (tokens)</label>
                  <input type="number" id="withdrawAmount" class="form-input" placeholder="e.g. 50" min="1" step="1" required />
                </div>

                <div class="form-group">
                  <label class="form-label" for="withdrawMethod">Withdrawal Channel</label>
                  <select id="withdrawMethod" class="form-select">
                    <option value="crypto">Instant Crypto Vault (USDT)</option>
                    <option value="bank">Direct EFT / Wire Transfer</option>
                    <option value="ozow">Ozow Instant Payout</option>
                  </select>
                </div>

                <button type="submit" id="withdrawBtn" class="btn btn-danger" style="width: 100%;">
                  Request Cashout
                </button>
              </form>
            </div>
          </div>
        </div>
      </div>
    `;

    bindNavTabs(container);

    const balanceEl = document.getElementById('walletBalance');
    let currentBalance = 0;

    async function fetchBalance() {
      try {
        const res = await fetch('/wallet/balance', {
          headers: { Authorization: `Bearer ${token}` }
        });
        if (res.ok) {
          const data = await res.json();
          currentBalance = data.balance ?? 0;
          balanceEl.textContent = `${currentBalance.toLocaleString()} tokens`;
          window.updateHeaderBalance();
        }
      } catch (err) {
        balanceEl.textContent = 'Error loading';
      }
    }

    document.getElementById('refreshBalanceBtn').onclick = () => {
      fetchBalance();
      window.showToast('Balance updated.', 'info');
    };

    // Quick deposit chips
    document.querySelectorAll('#depositQuickChips .chip-btn').forEach(btn => {
      btn.onclick = () => {
        document.querySelectorAll('#depositQuickChips .chip-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById('depositAmount').value = btn.getAttribute('data-amt');
      };
    });

    // Handle deposit
    document.getElementById('depositForm').onsubmit = async (e) => {
      e.preventDefault();
      const amount = parseInt(document.getElementById('depositAmount').value, 10);
      if (!amount || amount <= 0) return window.showToast('Enter a valid amount', 'error');

      const btn = document.getElementById('depositBtn');
      btn.disabled = true;
      btn.textContent = 'Depositing...';

      try {
        const res = await fetch('/wallet/deposit', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ amount })
        });
        const data = await res.json();
        if (res.ok) {
          window.showToast(`Successfully deposited ${amount} tokens!`, 'success');
          fetchBalance();
        } else {
          window.showToast(data.message || data.error || 'Deposit failed', 'error');
        }
      } catch (err) {
        window.showToast('Connection error', 'error');
      } finally {
        btn.disabled = false;
        btn.textContent = 'Deposit Now';
      }
    };

    // Handle withdraw
    document.getElementById('withdrawForm').onsubmit = async (e) => {
      e.preventDefault();
      const amount = parseInt(document.getElementById('withdrawAmount').value, 10);
      if (!amount || amount <= 0) return window.showToast('Enter a valid amount', 'error');
      if (amount > currentBalance) return window.showToast('Insufficient balance for this withdrawal', 'error');

      const btn = document.getElementById('withdrawBtn');
      btn.disabled = true;
      btn.textContent = 'Processing...';

      try {
        const res = await fetch('/wallet/withdraw', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ amount, method: document.getElementById('withdrawMethod').value })
        });
        const data = await res.json();
        if (res.ok) {
          window.showToast(`Withdrawal of ${amount} tokens requested!`, 'success');
          document.getElementById('withdrawAmount').value = '';
          fetchBalance();
        } else {
          window.showToast(data.message || data.error || 'Withdrawal failed', 'error');
        }
      } catch (err) {
        window.showToast('Connection error', 'error');
      } finally {
        btn.disabled = false;
        btn.textContent = 'Request Cashout';
      }
    };

    fetchBalance();
  }
};
