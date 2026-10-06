/**
 * CERBERUS SYSTEM — Citation Analysis dashboard
 * Tailwind theme tokens: the recognised Cyberpunk palette + display/mono typography.
 *
 * This file is the single source of truth for the colour system and the font stacks.
 * `web/static/style.css` mirrors these exact values as CSS custom properties, so the
 * dashboard renders identically whether it is built with Tailwind or served as plain CSS.
 *
 * @type {import('tailwindcss').Config}
 */
module.exports = {
  content: ['./web/static/**/*.{html,js}'],
  theme: {
    extend: {
      colors: {
        // 60% — solid ink-dark canvas
        obsidian: {
          DEFAULT: '#0B0813',
          deep: '#07050D',
          soft: '#120D1F',
          line: '#1C1430',
        },
        // 30% — structural framework: frames, inner borders, divider grids
        tech: {
          DEFAULT: '#5B0FFF',
          deep: '#3E0AAE',
          bright: '#7B3FFF',
          dim: '#2A0A66',
        },
        // secondary — categories, secondary labels, data-structure types
        cyber: {
          DEFAULT: '#A32EFF',
          bright: '#B85CFF',
          dim: '#5A1A8C',
        },
        // 10% — primary action accent: interactive items, active hover, metrics
        laser: {
          DEFAULT: '#FF007F',
          bright: '#FF4DA6',
          dim: '#8C0046',
        },
        // main readable text
        ghost: {
          DEFAULT: '#F5F3F7',
          muted: '#B9B1C9',
          faint: '#6E6684',
        },
      },
      fontFamily: {
        display: ['Orbitron', '"Russo One"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', '"Fira Code"', 'ui-monospace', 'monospace'],
      },
      letterSpacing: {
        rage: '0.18em',
      },
      boxShadow: {
        frame: '0 0 0 1px #5B0FFF, 0 0 60px -18px rgba(163, 46, 255, 0.65)',
        laser: '0 0 14px rgba(255, 0, 127, 0.65)',
        violet: '0 0 14px rgba(91, 15, 255, 0.60)',
      },
      keyframes: {
        'glitch-a': {
          '0%, 72%, 100%': { transform: 'translate(0, 0)', opacity: '0' },
          '73%': { transform: 'translate(-5px, -2px)', clipPath: 'polygon(0 2%, 100% 0, 100% 20%, 0 24%)', opacity: '0.95' },
          '76%': { transform: 'translate(6px, 1px)', clipPath: 'polygon(0 36%, 100% 30%, 100% 50%, 0 58%)', opacity: '0.95' },
          '79%': { transform: 'translate(-4px, 2px)', clipPath: 'polygon(0 64%, 100% 60%, 100% 82%, 0 90%)', opacity: '0.95' },
          '82%': { transform: 'translate(3px, -1px)', clipPath: 'polygon(0 86%, 100% 84%, 100% 100%, 0 100%)', opacity: '0.8' },
        },
        'glitch-b': {
          '0%, 68%, 100%': { transform: 'translate(0, 0)', opacity: '0' },
          '69%': { transform: 'translate(4px, 2px)', clipPath: 'polygon(0 8%, 100% 4%, 100% 28%, 0 30%)', opacity: '0.95' },
          '72%': { transform: 'translate(-6px, -1px)', clipPath: 'polygon(0 42%, 100% 38%, 100% 60%, 0 64%)', opacity: '0.95' },
          '75%': { transform: 'translate(5px, -2px)', clipPath: 'polygon(0 72%, 100% 68%, 100% 92%, 0 96%)', opacity: '0.95' },
        },
        flicker: {
          '0%, 97%, 100%': { opacity: '1' },
          '98%': { opacity: '0.82' },
          '99%': { opacity: '0.94' },
        },
      },
      animation: {
        'glitch-a': 'glitch-a 3.2s infinite steps(1, end)',
        'glitch-b': 'glitch-b 2.6s infinite steps(1, end)',
        flicker: 'flicker 6s infinite',
      },
    },
  },
  plugins: [],
};
