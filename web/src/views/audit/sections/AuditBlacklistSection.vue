<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Delete, Lock, Plus, Refresh, Search, Unlock } from "@element-plus/icons-vue";

const props = defineProps<{
  riskPolicy: {
    blacklistEnabled: boolean;
  };
  savePolicy: (message: string) => void;
}>();

interface BlacklistItem {
  id: number;
  type: "IP" | "USER" | "DEVICE" | "TENANT";
  targetValue: string;
  reason: string;
  blockedAt: string;
  expireAt: string;
  status: "ACTIVE" | "EXPIRED";
  operator: string;
}

const list = ref<BlacklistItem[]>([
  {
    id: 1,
    type: "IP",
    targetValue: "192.168.1.105",
    reason: "连续触发密码爆破达 10 次阈值",
    blockedAt: "2026-10-17 14:20:00",
    expireAt: "2026-10-24 14:20:00",
    status: "ACTIVE",
    operator: "system_detector"
  },
  {
    id: 2,
    type: "USER",
    targetValue: "malicious_bot",
    reason: "检测到非法 SQL AST 注入行为",
    blockedAt: "2026-10-16 11:05:12",
    expireAt: "永久封禁",
    status: "ACTIVE",
    operator: "admin"
  },
  {
    id: 3,
    type: "IP",
    targetValue: "45.120.89.22",
    reason: "跨租户数据穿透探测探针拦截",
    blockedAt: "2026-10-15 08:30:19",
    expireAt: "2026-10-18 08:30:19",
    status: "ACTIVE",
    operator: "system_detector"
  }
]);

const searchKeyword = ref("");
const dialogVisible = ref(false);

const form = reactive({
  type: "IP" as const,
  targetValue: "",
  reason: "",
  expireDays: 7
});

function openAddDialog() {
  form.type = "IP";
  form.targetValue = "";
  form.reason = "";
  form.expireDays = 7;
  dialogVisible.value = true;
}

function handleAdd() {
  if (!form.targetValue.trim()) {
    ElMessage.warning("请输入封禁目标值 (IP/账号/设备标识)");
    return;
  }
  const now = new Date();
  const expire = new Date(now.getTime() + form.expireDays * 86400000);
  list.value.unshift({
    id: Date.now(),
    type: form.type,
    targetValue: form.targetValue.trim(),
    reason: form.reason || "管理员手动封禁处置",
    blockedAt: now.toISOString().replace("T", " ").substring(0, 19),
    expireAt: form.expireDays === 0 ? "永久封禁" : expire.toISOString().replace("T", " ").substring(0, 19),
    status: "ACTIVE",
    operator: "admin"
  });
  dialogVisible.value = false;
  ElMessage.success(`目标「${form.targetValue}」已加入高危黑名单拦截引擎！`);
}

async function handleUnblock(row: BlacklistItem) {
  try {
    await ElMessageBox.confirm(
      `确定解封目标「${row.targetValue}」吗？解封后其将恢复正常的认证与访问权限。`,
      "确认解除封禁",
      { type: "warning", confirmButtonText: "确认解封", cancelButtonText: "取消" }
    );
    list.value = list.value.filter((i) => i.id !== row.id);
    ElMessage.success(`目标「${row.targetValue}」已成功解封！`);
  } catch {
    // cancelled
  }
}
</script>

<template>
  <div class="blacklist-container">
    <el-card shadow="never" class="policy-card">
      <template #header>
        <div class="panel-header">
          <div class="header-left">
            <h3>黑名单治理与访问阻断</h3>
            <p>统一管理异常 IP、失陷账号、恶意设备指纹与违规租户，并在网关层执行阻断。</p>
          </div>
          <div class="header-right">
            <el-switch
              v-model="riskPolicy.blacklistEnabled"
              active-text="全局黑名单阻断引擎生效"
              @change="savePolicy('黑名单治理引擎策略已更新')"
            />
          </div>
        </div>
      </template>

      <!-- Table Toolbar -->
      <div class="table-toolbar">
        <div class="filter-group">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索封禁目标 (IP/账号/原因)..."
            clearable
            style="width: 280px"
          />
        </div>
        <div class="action-group">
          <el-button type="primary" :icon="Plus" @click="openAddDialog">添加封禁目标</el-button>
        </div>
      </div>

      <!-- Blacklist Table -->
      <el-table
        :data="list.filter(i => !searchKeyword || i.targetValue.includes(searchKeyword) || i.reason.includes(searchKeyword))"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="type" label="封禁类型" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.type === 'IP'" size="small" type="danger">IP 封禁</el-tag>
            <el-tag v-else-if="row.type === 'USER'" size="small" type="warning">账号锁定</el-tag>
            <el-tag v-else-if="row.type === 'DEVICE'" size="small" type="info">设备指纹</el-tag>
            <el-tag v-else size="small" type="danger">租户沙箱</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetValue" label="拦截目标值" min-width="180">
          <template #default="{ row }">
            <span class="mono-target">{{ row.targetValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="封禁原因与规则触发" min-width="220" />
        <el-table-column prop="blockedAt" label="阻断时间" width="170" />
        <el-table-column prop="expireAt" label="有效期至" width="170" />
        <el-table-column prop="operator" label="操作人" width="140" />
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Unlock" @click="handleUnblock(row)">解除封禁</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Add Dialog -->
    <el-dialog v-model="dialogVisible" title="添加黑名单拦截目标" width="500px">
      <el-form label-position="top">
        <el-form-item label="封禁类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="客户端 IP 地址" value="IP" />
            <el-option label="系统用户账号" value="USER" />
            <el-option label="设备指纹特征" value="DEVICE" />
            <el-option label="违规租户空间" value="TENANT" />
          </el-select>
        </el-form-item>
        <el-form-item label="封禁目标值" required>
          <el-input v-model="form.targetValue" :placeholder="form.type === 'IP' ? '如: 192.168.1.100' : '如: bad_actor_01'" />
        </el-form-item>
        <el-form-item label="封禁时长">
          <el-select v-model="form.expireDays" style="width: 100%">
            <el-option label="封禁 24 小时" :value="1" />
            <el-option label="封禁 7 天" :value="7" />
            <el-option label="封禁 30 天" :value="30" />
            <el-option label="永久阻断" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置理由与依据">
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="填写封禁违规依据或告警工单号..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">立即下发拦截</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.blacklist-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.mono-target {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-weight: 600;
  color: #0f172a;
}
</style>
