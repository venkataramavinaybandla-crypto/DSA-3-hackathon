/**
 * CERBERUS SYSTEM — Citation analysis dashboard
 * Optional palette mirror.
 *
 * The dashboard ships as plain CSS with no build step; `web/static/style.css`
 * is the source of truth. This file only mirrors those tokens so the theme is
 * available if the project is ever wired up to Tailwind. Keep the two in sync.
 *
 * @type {import('tailwindcss').Config}
 */
module.exports = {
  content: ['./web/static/**/*.{html,js}'],
  theme: {
    extend: {
      colors: {
        // canvas
        bg: { DEFAULT: '#0b0813', raised: '#100b1c', sunken: '#08060f' },
        line: { DEFAULT: '#241a3c', strong: '#362a5a' },
        // accent — violet carries structure and interaction
        accent: {
          DEFAULT: '#a78bfa',
          strong: '#8b5cf6',
          dim: '#4c3390',
        },
        // secondary accent — magenta, primary actions only
        hot: { DEFAULT: '#f0559b', dim: '#7a2350' },
        // text
        text: { DEFAULT: '#ece9f3', muted: '#a99fc0', faint: '#746c88' },
      },
      fontFamily: {
        display: ['Orbitron', '"Russo One"', 'system-ui', 'sans-serif'],
        ui: ['Inter', 'system-ui', '-apple-system', '"Segoe UI"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', '"SFMono-Regular"', 'monospace'],
      },
      borderRadius: { DEFAULT: '8px', sm: '5px' },
    },
  },
  plugins: [],
};
