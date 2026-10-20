# Antigravity SaaS 跨端移动工作台 (Mobile Workspace)

基于 **Vue 3 + TypeScript + Vite + uni-app** 架构打造的跨端多平台客户端工程模板，支持编译输出为：
- **微信小程序 (WeChat Mini Program)**
- **移动端 H5 响应式微应用**
- **iOS / Android App 原生打包**

---

## 核心功能清单
1. **移动工作台 (Workbench)**：经营统计大卡、快捷扫码核销、审批待办看板；
2. **移动审批流转 (Mobile BPM)**：待办审批列表、查看工单详情、一键移动端同意/驳回退回；
3. **多仓库存盘点 (Mobile ERP)**：实时库存明细查询、安全库存警戒红标、条码扫码核验；
4. **多租户与安全认证**：多租户 Header 自动注入、Bearer Token 无感刷新、会话安全登出。

---

## 开发与编译指令

```bash
# 1. 进入目录并安装依赖
cd mobile
npm install

# 2. 启动 H5 移动微应用预览 (运行在 http://localhost:5173)
npm run dev:h5

# 3. 编译发布微信小程序
npm run build:mp-weixin

# 4. 编译发布 H5 生产产物
npm run build:h5
```
