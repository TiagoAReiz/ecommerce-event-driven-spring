import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// A porta 3000 nao e escolha estetica: o gateway so aceita esta origem no CORS
// e so redireciona o login para ela (app.front-url, default http://localhost:3000).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 3000, strictPort: true },
  preview: { port: 3000, strictPort: true },
})
