import type { Config } from 'tailwindcss';

const config: Config = {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#f0f9ff',
          100: '#e0f2fe',
          500: '#0ea5e9',
          600: '#0284c7',
          700: '#0369a1',
          900: '#0c4a6e',
        },
        accent: {
          500: '#f43f5e', // 用于好感度、重要提示
        },
        surface: {
          0: '#ffffff',
          100: '#f8fafc',
          200: '#e2e8f0',
          300: '#cbd5e1', // 用于 border 和 hover 中间态
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
        },
        text: {
          primary: '#1e293b',
          secondary: '#64748b',
        }
      },
      fontFamily: {
        sans: ['"Noto Sans SC"', 'system-ui', 'sans-serif'],
      },
      screens: {
        xs: '375px',
      },
      animation: {
        'fade-in-up': 'fadeInUp 0.3s ease-out forwards',
      },
      keyframes: {
        fadeInUp: {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
    },
  },
  plugins: [],
};

export default config;
