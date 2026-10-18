/**
 * RobinHood Platform — Universal SVG Icon System
 * Clean, lightweight, scalable 24x24 vector icons matching the premium design aesthetic.
 */

const ICONS = {
  // Brand & Navigation
  brand: `<path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>`,
  zap: `<polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>`,
  activity: `<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>`,
  radio: `<circle cx="12" cy="12" r="2"/><path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14"/>`,
  chart: `<line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>`,
  
  // Games & Lobby
  gamepad: `<rect x="2" y="6" width="20" height="12" rx="4"/><path d="M6 12h4M8 10v4M16 10h.01M18 12h.01"/>`,
  dice: `<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r="1.5" fill="currentColor"/><circle cx="16" cy="16" r="1.5" fill="currentColor"/><circle cx="12" cy="12" r="1.5" fill="currentColor"/><circle cx="16" cy="8" r="1.5" fill="currentColor"/><circle cx="8" cy="16" r="1.5" fill="currentColor"/>`,
  cards: `<rect x="3" y="5" width="12" height="16" rx="2"/><path d="M7 3h12a2 2 0 0 1 2 2v14"/>`,
  spade: `<path d="M12 2C9 7 4 9 4 14a5 5 0 0 0 7 4.58V22h2v-3.42A5 5 0 0 0 20 14c0-5-5-7-8-12z" fill="currentColor" stroke="none"/>`,
  heart: `<path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" fill="currentColor" stroke="none"/>`,
  ludo: `<circle cx="12" cy="12" r="9"/><path d="M12 3v18M3 12h18"/><circle cx="7.5" cy="7.5" r="2" fill="currentColor"/><circle cx="16.5" cy="7.5" r="2" fill="currentColor"/><circle cx="16.5" cy="16.5" r="2" fill="currentColor"/><circle cx="7.5" cy="16.5" r="2" fill="currentColor"/>`,
  snake: `<path d="M18 4a3 3 0 0 0-3 3v2a3 3 0 0 1-6 0V7a3 3 0 0 0-6 0v10a3 3 0 0 0 6 0v-2a3 3 0 0 1 6 0v2a3 3 0 0 0 6 0V7a3 3 0 0 0-3-3z"/>`,
  ladder: `<path d="M7 2v20M17 2v20M7 6h10M7 11h10M7 16h10"/>`,
  target: `<circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/>`,
  sparkles: `<path d="M12 3v3m0 12v3m9-9h-3M6 12H3m14.36-6.36l-2.12 2.12M7.76 16.24l-2.12 2.12m12.72 0l-2.12-2.12M7.76 7.76L5.64 5.64"/>`,
  flame: `<path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"/>`,

  // Players & Matchmaking
  user: `<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>`,
  users: `<path d="M17 21v-2a4 4 0 0 0-3-3.87M9 21v-2a4 4 0 0 1 3-3.87M16 3.13a4 4 0 0 1 0 7.75M8 3.13a4 4 0 0 0 0 7.75"/>`,
  bot: `<rect x="4" y="4" width="16" height="16" rx="3"/><rect x="9" y="9" width="6" height="6"/><path d="M9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3"/>`,
  room: `<path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>`,
  door: `<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>`,
  plus: `<line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>`,

  // Wallet & Economy
  coin: `<circle cx="12" cy="12" r="9"/><path d="M12 7v10M9 9.5h6M9 14.5h6"/>`,
  coins: `<ellipse cx="12" cy="7" rx="8" ry="3"/><path d="M4 7v5c0 1.66 3.58 3 8 3s8-1.34 8-3V7M4 12v5c0 1.66 3.58 3 8 3s8-1.34 8-3v-5"/>`,
  wallet: `<path d="M20 12V8H6a2 2 0 0 1-2-2c0-1.1.9-2 2-2h12v4"/><path d="M4 6v12a2 2 0 0 0 2 2h14v-4"/><circle cx="18" cy="14" r="1"/>`,
  card: `<rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/>`,
  arrowUpRight: `<line x1="7" y1="17" x2="17" y2="7"/><polyline points="7 7 17 7 17 17"/>`,
  arrowDownLeft: `<line x1="17" y1="7" x2="7" y2="17"/><polyline points="17 17 7 17 7 7"/>`,

  // Leaderboard & Achievements
  trophy: `<path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.45 1-1 1H8v4h8v-4h-1c-.55 0-1-.45-1-1v-2.34"/><path d="M18 4H6v7a6 6 0 0 0 12 0V4z"/>`,
  crown: `<path d="M3 18h18M4 18l2-10 5 5 5-5 2 10z"/>`,
  medal: `<circle cx="12" cy="14" r="5"/><path d="M8.21 13.89L7 22l5-3 5 3-1.21-8.11M14.53 4L12 2 9.47 4"/>`,
  star: `<polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>`,
  diamond: `<path d="M6 3h12l4 6-10 12L2 9z"/>`,

  club: `<path d="M12 2a4 4 0 0 0-3.5 6A5 5 0 0 0 4 14a5 5 0 0 0 6.5 4.8V22h3v-3.2A5 5 0 0 0 20 14a5 5 0 0 0-4.5-6A4 4 0 0 0 12 2z" fill="currentColor" stroke="none"/>`,
  diamondSuit: `<path d="M12 2l7 10-7 10-7-10z" fill="currentColor" stroke="none"/>`,
  layers: `<polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/>`,
  feather: `<path d="M20.24 12.24a6 6 0 0 0-8.49-8.49L5 10.5V19h8.5z"/><line x1="16" y1="8" x2="2" y2="22"/><line x1="17.5" y1="15" x2="9" y2="15"/>`,
  swords: `<line x1="14.5" y1="17.5" x2="3" y2="6"/><polyline points="14 3 21 3 21 10"/><line x1="21" y1="3" x2="14" y2="10"/><line x1="9.5" y1="6.5" x2="21" y2="18"/><polyline points="3 14 3 21 10 21"/><line x1="3" y1="21" x2="10" y2="14"/>`,
  handshake: `<path d="M11 17l-5-5a3 3 0 0 1 0-4.24l.7-.7a3 3 0 0 1 4.24 0L12 8.17l1.06-1.11a3 3 0 0 1 4.24 0l.7.7a3 3 0 0 1 0 4.24l-5 5"/><path d="M18 11l3 3-5 5-2-2"/><path d="M6 13L3 16l5 5 2-2"/>`,
  book: `<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>`,
  home: `<path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>`,
  logOut: `<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>`,
  play: `<polygon points="5 3 19 12 5 21 5 3"/>`,
  pointer: `<path d="M3 3l7.07 16.97 2.51-7.39 7.39-2.51L3 3z"/><path d="M13 13l6 6"/>`,

  // Actions & Alerts
  scroll: `<path d="M19 17h2a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H9a2 2 0 0 0-2 2v2"/><path d="M3 8a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8z"/><path d="M7 11h6M7 15h4"/>`,
  refresh: `<path d="M21.5 2v6h-6M2.5 22v-6h6"/><path d="M20.49 15a9 9 0 0 1-14.85 3.36L2.5 16M3.51 9a9 9 0 0 1 14.85-3.36L21.5 8"/>`,
  check: `<polyline points="20 6 9 17 4 12"/>`,
  checkCircle: `<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>`,
  x: `<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>`,
  xCircle: `<circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>`,
  alert: `<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>`,
  info: `<circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>`,
  help: `<circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>`,
  lock: `<rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>`,
  unlock: `<rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 9.9-1"/>`,
  shield: `<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>`,
  key: `<path d="M21 2l-2 2m-1.5 1.5L16 7l-1.5-1.5-2 2 1.5 1.5L12 11l-4 4a5.5 5.5 0 1 1-2-2l4-4 2-2-1.5-1.5 2-2 1.5 1.5L18 5.5l2-2z"/>`,
  chevronRight: `<polyline points="9 18 15 12 9 6"/>`,
  flag: `<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" y1="22" x2="4" y2="15"/>`
};

/**
 * Generate a clean, accessible inline SVG icon string.
 * @param {string} name - The icon name in ICONS dictionary
 * @param {number} size - Width & height in px (default 18)
 * @param {string} className - Optional CSS classes
 * @param {string} style - Optional inline CSS styles
 * @returns {string} SVG HTML string
 */
export function getIcon(name, size = 18, className = '', style = '') {
  const path = ICONS[name] || ICONS['help'];
  const classes = className ? `app-icon ${className}` : 'app-icon';
  const inlineStyle = style ? ` style="${style}"` : '';
  return `<svg class="${classes}" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"${inlineStyle}>${path}</svg>`;
}

/**
 * Generate an authentic vector SVG dice face (1-6) with crisp pips.
 * @param {number} val - Dice value from 1 to 6
 * @param {number} size - Width & height in px
 * @param {string} color - Pip color (defaults to currentColor)
 * @returns {string} SVG HTML string
 */
export function getSvgDice(val, size = 32, color = 'currentColor') {
  const pips = {
    1: [[12, 12]],
    2: [[7, 7], [17, 17]],
    3: [[7, 7], [12, 12], [17, 17]],
    4: [[7, 7], [17, 7], [7, 17], [17, 17]],
    5: [[7, 7], [17, 7], [12, 12], [7, 17], [17, 17]],
    6: [[7, 7], [17, 7], [7, 12], [17, 12], [7, 17], [17, 17]]
  };
  const list = pips[Math.max(1, Math.min(6, val || 1))];
  const circles = list.map(([cx, cy]) => `<circle cx="${cx}" cy="${cy}" r="2" fill="${color}"/>`).join('');
  return `<svg class="app-icon svg-dice-face" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <rect x="2" y="2" width="20" height="20" rx="4"/>
    ${circles}
  </svg>`;
}

// Attach globally for dynamic scripts and inline HTML templates
if (typeof window !== 'undefined') {
  window.getIcon = getIcon;
  window.getSvgDice = getSvgDice;
}
