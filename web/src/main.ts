import { createPinia } from "pinia";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { createApp } from "vue";
import App from "./App.vue";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import DictTag from "./components/common/DictTag.vue";
import { registerDirectives } from "./directives";
import { router } from "./router";
import { useDictStore } from "./stores/modules/dict";
import { useLocaleStore } from "./stores/modules/locale";
import "element-plus/dist/index.css";
import "./assets/styles/index.scss";

NProgress.configure({ showSpinner: false });

const app = createApp(App);
const pinia = createPinia();

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}
app.component("DictTag", DictTag);

app.use(pinia);
app.use(router);
registerDirectives(app);

const localeStore = useLocaleStore(pinia);
localeStore.bootstrap();
const dictStore = useDictStore(pinia);
void dictStore.bootstrap();

app.mount("#app");
