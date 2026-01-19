/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#3B82F6',
          foreground: '#FFFFFF',
        },
        secondary: {
          DEFAULT: '#60A5FA',
          foreground: '#FFFFFF',
        },
        cta: {
          DEFAULT: '#F97316',
          foreground: '#FFFFFF',
        },
        background: '#F8FAFC',
        text: '#1E293B',
      },
      fontFamily: {
        sans: ['"Fira Sans"', 'sans-serif'],
        mono: ['"Fira Code"', 'monospace'],
      },
    },
  },
  plugins: [],
}

