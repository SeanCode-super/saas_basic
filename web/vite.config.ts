import { fileURLToPath, URL } from "node:url";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ["vue", "vue-router", "pinia"],
      dts: "src/types/auto-imports.d.ts",
      eslintrc: {
        enabled: false
      },
      resolvers: [ElementPlusResolver()]
    }),
    Components({
      dts: "src/types/components.d.ts",
      resolvers: [ElementPlusResolver()]
    })
  ],
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
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true
      }
    }
  }
});
