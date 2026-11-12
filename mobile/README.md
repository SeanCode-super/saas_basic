# Antigravity SaaS 跨端移动工作台 (Mobile Workspace)

基于 **Vue 3 + TypeScript + Vite + uni-app** 架构打造的跨端多平台客户端工程模板，支持编译输出为：
- **微信小程序 (WeChat Mini Program)**
- **移动端 H5 响应式微应用**
- **iOS / Android App 原生打包**

---

## 核心功能清单
1. **移动工作台 (`pages/index/index`)**：经营统计指标、快捷功能入口、审批待办看板与悬浮灵动岛 TabBar；
2. **移动审批流转 (`pages/workflow/list`, `pages/workflow/apply`)**：待办审批列表、查看工单详情、快捷发起审批、一键同意/驳回；
3. **移动 CRM 与外勤跟进 (`pages/crm/customers`, `pages/crm/follow-up`)**：客户档案检索、客户跟进记录、商机状态浏览；
4. **移动云仓库存 (`pages/erp/inventory`)**：实时库存明细查询、安全库存警戒标示、出入库核验；
5. **AI 移动副驾驶 (`pages/ai/index`)**：移动端自然语言助手与指令快捷查询；
6. **企业通讯录与消息中心 (`pages/contact/index`, `pages/notify/index`)**：组织同事检索与站内消息通知；
7. **个人数字身份与多租户安全 (`pages/profile/profile`, `pages/login/login`)**：真实头像上传与预览、个人扩展资料编辑、修改密码、组织归属与任职明细查看、安全评分展示、本人活跃会话查询与踢除其他设备、多租户空间切换与 Bearer Token 无感刷新。

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
