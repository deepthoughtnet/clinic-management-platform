import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const apiTarget = process.env.VITE_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5176,
    proxy: {
      "/api": {
        target: apiTarget,
        changeOrigin: true,
      },
      "/ws": {
        target: apiTarget,
        changeOrigin: true,
        ws: true,
      },
    },
  },
});
