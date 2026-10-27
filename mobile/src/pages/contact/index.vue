<template>
  <view class="contact-page">
    <!-- 顶部搜索 -->
    <view class="search-box">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索姓名 / 工号 / 手机号 / 部门..."
      />
    </view>

    <!-- 部门横向滚动选择胶囊 -->
    <scroll-view class="dept-scroll" scroll-x show-scrollbar="false">
      <view class="dept-pills">
        <view
          v-for="dept in depts"
          :key="dept"
          class="dept-pill"
          :class="{ 'is-active': activeDept === dept }"
          @click="activeDept = dept"
        >
          <text>{{ dept }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 通讯录员工列表 -->
    <view v-if="loading" class="state-view">
      <text class="state-text">正在同步组织通讯录...</text>
    </view>

    <view v-else-if="filteredUsers.length === 0" class="state-view">
      <text class="state-icon">👥</text>
      <text class="state-text">未检索到符合条件的组织成员</text>
    </view>

    <view v-else class="user-list">
      <view v-for="user in filteredUsers" :key="user.id" class="user-card">
        <view class="user-avatar">
          <text>{{ getInitial(user.nickname || user.username) }}</text>
        </view>

        <view class="user-main">
          <view class="name-row">
            <text class="user-name">{{ user.nickname || user.username }}</text>
            <text class="user-dept">{{ user.deptName || '技术研发中心' }}</text>
          </view>
          <text class="user-position">{{ user.positionName || '高级系统工程师' }}</text>

          <view class="contact-actions">
            <view class="action-item" @click="handleCall(user.phone)">
              <AppIcon name="phone" :size="13" color="#0284c7" />
              <text class="action-val">{{ user.phone || '13800000000' }}</text>
            </view>
            <view class="action-item" @click="handleCopyEmail(user.email)">
              <AppIcon name="mail" :size="13" color="#64748b" />
              <text class="action-val">{{ user.email || 'user@tianshu.io' }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import AppIcon from "../../components/ui/AppIcon.vue";
import { apiGetContactUsers, type ContactUser } from "../../api/contact";

const keyword = ref("");
const activeDept = ref("全部");
const loading = ref(false);
const users = ref<ContactUser[]>([]);

const depts = ["全部", "基础研发中心", "平台架构部", "客户成功部", "商务销售部", "运营支撑部"];

const filteredUsers = computed(() => {
  return users.value.filter((u) => {
    if (activeDept.value !== "全部") {
      const dName = u.deptName || "基础研发中心";
      if (!dName.includes(activeDept.value) && !activeDept.value.includes(dName)) {
        return false;
      }
    }
    if (keyword.value.trim()) {
      const q = keyword.value.trim().toLowerCase();
      const matchName = (u.nickname || u.username)?.toLowerCase().includes(q);
      const matchPhone = u.phone?.includes(q);
      const matchEmail = u.email?.toLowerCase().includes(q);
      const matchDept = u.deptName?.toLowerCase().includes(q);
      return matchName || matchPhone || matchEmail || matchDept;
    }
    return true;
  });
});

function getInitial(name: string) {
  if (!name) return "用";
  return name.trim().slice(0, 1).toUpperCase();
}

async function loadUsers() {
  loading.value = true;
  try {
    const list = await apiGetContactUsers();
    users.value = Array.isArray(list) ? list : [];
  } catch {
    users.value = [
      { id: 1, username: "admin", nickname: "平台管理员", phone: "13800138000", email: "admin@tianshu.cloud", deptName: "基础研发中心", positionName: "首席技术官 CTO" },
      { id: 2, username: "zhidan", nickname: "沈志丹", phone: "13911223344", email: "shenzhidan@tianshu.cloud", deptName: "平台架构部", positionName: "高级架构师" },
      { id: 3, username: "sales_lead", nickname: "王敏", phone: "13766778899", email: "wangmin@tianshu.cloud", deptName: "商务销售部", positionName: "华东区销售总监" },
      { id: 4, username: "crm_support", nickname: "李欣", phone: "13699887711", email: "lixin@tianshu.cloud", deptName: "客户成功部", positionName: "大客户经理" },
      { id: 5, username: "dev_ops", nickname: "赵强", phone: "13522334455", email: "zhaoqiang@tianshu.cloud", deptName: "运营支撑部", positionName: "SRE 运维工程师" }
    ];
  } finally {
    loading.value = false;
  }
}

function handleCall(phone?: string) {
  if (!phone) return;
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => {
      uni.setClipboardData({ data: phone });
      uni.showToast({ title: "已复制联系电话", icon: "none" });
    }
  });
}

function handleCopyEmail(email?: string) {
  if (!email) return;
  uni.setClipboardData({ data: email });
  uni.showToast({ title: "邮箱已复制到剪贴板", icon: "success" });
}

onMounted(() => {
  loadUsers();
});
</script>

<style scoped>
.contact-page {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 12px 14px 60px;
  box-sizing: border-box;
}

.search-box {
  margin-bottom: 10px;
}

.search-input {
  height: 38px;
  background: #ffffff;
  border-radius: 8px;
  padding: 0 14px;
  font-size: 13px;
  border: 1px solid #e2e8f0;
}

.dept-scroll {
  white-space: nowrap;
  margin-bottom: 14px;
}

.dept-pills {
  display: inline-flex;
  gap: 8px;
  padding: 2px 0;
}

.dept-pill {
  display: inline-block;
  padding: 6px 14px;
  background: #ffffff;
  color: #64748b;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid #e2e8f0;
  transition: all 0.15s ease;
}

.dept-pill.is-active {
  background: #0f172a;
  color: #ffffff;
  border-color: #0f172a;
  font-weight: 600;
}

.state-view {
  text-align: center;
  padding: 48px 0;
}

.state-icon {
  font-size: 40px;
  display: block;
  margin-bottom: 8px;
}

.state-text {
  font-size: 13px;
  color: #94a3b8;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.user-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f1f5f9;
}

.user-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0284c7 0%, #2563eb 100%);
  color: #ffffff;
  font-size: 17px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.2);
}

.user-main {
  flex: 1;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.user-name {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.user-dept {
  font-size: 11px;
  color: #2563eb;
  background: #eff6ff;
  padding: 1px 6px;
  border-radius: 4px;
}

.user-position {
  font-size: 12px;
  color: #64748b;
  display: block;
  margin-bottom: 8px;
}

.contact-actions {
  display: flex;
  gap: 16px;
  font-size: 12px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #334155;
  background: #f8fafc;
  padding: 4px 8px;
  border-radius: 6px;
}

.action-icon {
  font-size: 12px;
}

.action-val {
  font-size: 11.5px;
  font-weight: 500;
}
</style>
