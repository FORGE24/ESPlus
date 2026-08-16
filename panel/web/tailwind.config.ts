import type { Config } from 'tailwindcss'

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Mission-control dark palette
        base: {
          900: '#0a0e14',
          800: '#0f1620',
          700: '#151e2b',
          600: '#1c2738',
          500: '#243248',
        },
        accent: {
          cyan: '#22d3ee',
          emerald: '#34d399',
          amber: '#fbbf24',
          violet: '#a78bfa',
          rose: '#fb7185',
        },
        ink: {
          100: '#f1f5f9',
          200: '#e2e8f0',
          300: '#cbd5e1',
          400: '#94a3b8',
          500: '#64748b',
        },
      },
      fontFamily: {
        sans: ['Inter', '"Microsoft YaHei"', '"PingFang SC"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'Consolas', '"Courier New"', 'monospace'],
        display: ['Syne', 'Inter', 'sans-serif'],
      },
      animation: {
        'fade-in': 'fadeIn 0.4s ease-out',
        'slide-up': 'slideUp 0.5s cubic-bezier(0.16, 1, 0.3, 1)',
        'glow-pulse': 'glowPulse 2s ease-in-out infinite',
        'shimmer': 'shimmer 2s linear infinite',
      },
      keyframes: {
        fadeIn: { from: { opacity: '0' }, to: { opacity: '1' } },
        slideUp: { from: { opacity: '0', transform: 'translateY(16px)' }, to: { opacity: '1', transform: 'translateY(0)' } },
        glowPulse: { '0%, 100%': { opacity: '0.6' }, '50%': { opacity: '1' } },
        shimmer: { '0%': { backgroundPosition: '-200% 0' }, '100%': { backgroundPosition: '200% 0' } },
      },
    },
  },
  plugins: [],
}
export default config
