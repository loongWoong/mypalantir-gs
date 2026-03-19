/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  safelist: [
    'col-span-6', 'col-span-12', 'row-span-1', 'row-span-2',
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
        sans: ['system-ui', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'Roboto', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', '"Liberation Mono"', '"Courier New"', 'monospace'],
      },
    },
  },
  plugins: [
    require('@tailwindcss/typography'),
  ],
}

