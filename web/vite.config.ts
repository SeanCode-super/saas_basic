import { fileURLToPath, URL } from "node:url";
import { env } from "node:process";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// main.ts loads the full stylesheet. Per-component style imports would make
// Vite re-optimize dependencies as users visit previously unseen routes.
const elementPlus = ElementPlusResolver({ importStyle: false });

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ["vue", "vue-router", "pinia"],
      dts: "src/types/auto-imports.d.ts",
      eslintrc: {
        enabled: false
      },
      resolvers: [elementPlus]
    }),
    Components({
      dts: "src/types/components.d.ts",
      resolvers: [elementPlus]
    })
  ],
  optimizeDeps: {
    include: ["element-plus/es"]
  },
  build: {
    chunkSizeWarningLimit: 850,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) {
            return undefined;
          }

          if (id.includes("@element-plus/icons-vue")) {
            return "ep-icons";
          }

          if (id.includes("element-plus")) {
            return "element-plus";
          }

          if (id.includes("echarts")) {
            return "echarts";
          }

          if (id.includes("axios") || id.includes("dayjs")) {
            return "vendor-utils";
          }

          if (id.includes("vue-router") || id.includes("/vue/") || id.includes("pinia")) {
            return "vue-core";
          }

          return "vendor";
        }
      }
    }
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  server: {
    port: 9527,
    host: "0.0.0.0",
    allowedHosts: true,
    proxy: {
      "/api": {
        target: env.VITE_API_PROXY_TARGET ?? "http://127.0.0.1:8080",
        changeOrigin: true,
        xfwd: true
      },
      "/open": {
        target: env.VITE_API_PROXY_TARGET ?? "http://127.0.0.1:8080",
        changeOrigin: true,
        xfwd: true
      },
      "/scim": {
        target: env.VITE_API_PROXY_TARGET ?? "http://127.0.0.1:8080",
        changeOrigin: true,
        xfwd: true
      },
      "/doc.html": {
        target: env.VITE_API_PROXY_TARGET ?? "http://127.0.0.1:8080",
        changeOrigin: true
      },
      "/v3/api-docs": {
        target: env.VITE_API_PROXY_TARGET ?? "http://127.0.0.1:8080",
        changeOrigin: true
      },
      "/api-docs": {
        target: env.VITE_API_PROXY_TARGET ?? "http://127.0.0.1:8080",
        changeOrigin: true
      },
      "/swagger-ui": {
        target: env.VITE_API_PROXY_TARGET ?? "http://127.0.0.1:8080",
        changeOrigin: true
      },
      "/webjars": {
        target: env.VITE_API_PROXY_TARGET ?? "http://127.0.0.1:8080",
        changeOrigin: true
      }
    }
  }
});
