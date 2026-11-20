<template>
  <div class="profile-page">
    <!-- 顶部欢迎区 -->
    <section class="profile-hero">
      <span class="hero-glow hero-glow--one"></span>
      <span class="hero-glow hero-glow--two"></span>

      <div class="hero-main">
        <div class="hero-avatar">{{ userStore.avatarText }}</div>
        <div class="hero-text">
          <p class="hero-greeting">{{ greeting }}，{{ userStore.displayName }}</p>
          <div class="hero-meta">
            <el-tag class="hero-tag" size="small" effect="dark" round>
              {{ userStore.isAdmin ? "管理员" : "普通用户" }}
            </el-tag>
            <span>@{{ userInfo.userName || "-" }}</span>
            <span v-if="userInfo.createTime">加入于 {{ userInfo.createTime }}</span>
          </div>
        </div>
      </div>

      <div class="hero-actions">
        <el-button class="ghost-button" @click="openEdit">编辑资料</el-button>
        <el-button class="ghost-button" @click="openPassword">修改密码</el-button>
      </div>
    </section>

    <!-- 数据概览：全部来自真实接口与本地会话，请求失败时显示占位符 -->
    <section class="stat-grid">
      <article v-for="card in statCards" :key="card.key" class="stat-card">
        <span class="stat-icon" :class="`is-${card.tone}`">
          <el-icon :size="20"><component :is="card.icon" /></el-icon>
        </span>
        <div class="stat-body">
          <p class="stat-label">{{ card.label }}</p>
          <p class="stat-value">
            {{ card.value }}<small v-if="card.unit">{{ card.unit }}</small>
          </p>
          <p class="stat-hint">{{ card.hint }}</p>
        </div>
      </article>
    </section>

    <!-- 编辑资料 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑资料"
      width="460px"
      align-center
      :close-on-click-modal="false"
    >
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
        <el-form-item label="用户名" prop="userName">
          <el-input v-model="editForm.userName" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="editForm.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="性别" prop="sex">
          <el-select v-model="editForm.sex" placeholder="请选择性别">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="isSaving" @click="handleUpdate">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 修改密码 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="460px"
      align-center
      :close-on-click-modal="false"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="90px"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="passwordDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="isChangingPassword" @click="handleUpdatePassword">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import {
  ChatDotRound,
  Collection,
  Document,
  Files,
} from "@element-plus/icons-vue";
import { useUserStore } from "@/store/user";
import { useChatSessions } from "@/composables/useChatSessions";
import { queryFileApi } from "@/api/KnowHubApi";
import { updatePasswordApi, updateUserApi } from "@/api/UserApi";

const userStore = useUserStore();
const userInfo = computed(() => userStore.userInfo);

// 本地会话数量，用于概览卡展示
const { sessions } = useChatSessions();

const stats = reactive({
  docs: 0,
  chunks: 0,
  categories: 0,
  success: 0,
  processing: 0,
  failed: 0,
  size: 0,
  loaded: false,
});

// 本地会话数量直接读响应式数据，会话增减后无需重新拉取
const sessionCount = computed(() => sessions.value.length);

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return "夜深了";
  if (hour < 12) return "上午好";
  if (hour < 18) return "下午好";
  return "晚上好";
});

// 字节转可读大小，避免统计卡出现 10485760 这种数字
const formatSize = (size?: number) => {
  if (!size || size <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = size;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index++;
  }
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
};

const statCards = computed(() => {
  const placeholder = "--";
  const value = (input: number) => (stats.loaded ? input : placeholder);
  return [
    {
      key: "docs",
      label: "知识库文档",
      value: value(stats.docs),
      unit: "篇",
      icon: Document,
      tone: "blue",
      hint: `已解析 ${value(stats.success)} · 处理中 ${value(stats.processing)} · 失败 ${value(stats.failed)}`,
    },
    {
      key: "chunks",
      label: "向量分片",
      value: value(stats.chunks),
      unit: "段",
      icon: Files,
      tone: "violet",
      hint: `存储占用 ${stats.loaded ? formatSize(stats.size) : placeholder}`,
    },
    {
      key: "categories",
      label: "知识库分类",
      value: value(stats.categories),
      unit: "个",
      icon: Collection,
      tone: "green",
      hint: "按文档分类去重统计",
    },
    {
      key: "sessions",
      label: "我的会话",
      value: sessionCount.value,
      unit: "个",
      icon: ChatDotRound,
      tone: "amber",
      hint: "保存在本地浏览器",
    },
  ];
});

/**
 * 文档概览：一次拉取全部分类下的文档，前端聚合文档数、分片数、分类数与存储占用
 */
const loadStats = async () => {
  try {
    const res: any = await queryFileApi({ page: 1, pageSize: 1000, fileName: "" });
    if (res?.code !== 0 || !res.data) return;
    const records: any[] = res.data.records ?? [];
    const categories = new Set<string>();

    let chunks = 0;
    let size = 0;
    let success = 0;
    let processing = 0;
    let failed = 0;

    records.forEach((file) => {
      chunks += Number(file.chunkCount) || 0;
      size += Number(file.fileSize) || 0;
      if (file.category) categories.add(file.category);
      if (file.status === "SUCCESS") success++;
      else if (file.status === "FAILED") failed++;
      else processing++;
    });

    stats.docs = Number(res.data.total) || records.length;
    stats.chunks = chunks;
    stats.categories = categories.size;
    stats.size = size;
    stats.success = success;
    stats.processing = processing;
    stats.failed = failed;
    stats.loaded = true;
  } catch (error) {
    console.warn("加载知识库概览失败:", error);
  }
};

const editDialogVisible = ref(false);
const editFormRef = ref<FormInstance>();
const isSaving = ref(false);
const editForm = ref({
  id: 0,
  userName: "",
  name: "",
  phone: "",
  sex: "",
});

const editRules: FormRules = {
  userName: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  name: [{ required: true, message: "请输入姓名", trigger: "blur" }],
  phone: [{ pattern: /^1\d{10}$/, message: "手机号格式不正确", trigger: "blur" }],
};

const openEdit = () => {
  editForm.value = {
    id: userInfo.value.id,
    userName: userInfo.value.userName,
    name: userInfo.value.name,
    phone: userInfo.value.phone,
    sex: userInfo.value.sex,
  };
  editDialogVisible.value = true;
};

const handleUpdate = async () => {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  isSaving.value = true;
  try {
    const res: any = await updateUserApi(editForm.value);
    if (res?.code === 0) {
      ElMessage({ message: "资料更新成功", type: "success" });
      editDialogVisible.value = false;
      await userStore.fetchUserInfo(true);
    } else {
      ElMessage({ message: res?.message || "更新失败", type: "error" });
    }
  } catch (error) {
    console.error("更新用户信息失败:", error);
    ElMessage({ message: "更新失败，请稍后重试", type: "error" });
  } finally {
    isSaving.value = false;
  }
};

const passwordDialogVisible = ref(false);
const passwordFormRef = ref<FormInstance>();
const isChangingPassword = ref(false);
const passwordForm = ref({
  id: 0,
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
});

const passwordRules: FormRules = {
  oldPassword: [
    { required: true, message: "请输入原密码", trigger: "blur" },
    { min: 5, max: 20, message: "密码长度在 5 到 20 个字符", trigger: "blur" },
  ],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 5, max: 20, message: "密码长度在 5 到 20 个字符", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请再次输入新密码", trigger: "blur" },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.value.newPassword) {
          callback(new Error("两次输入的密码不一致"));
        } else {
          callback();
        }
      },
      trigger: "blur",
    },
  ],
};

const openPassword = () => {
  passwordForm.value = {
    id: userInfo.value.id,
    oldPassword: "",
    newPassword: "",
    confirmPassword: "",
  };
  passwordDialogVisible.value = true;
};

const handleUpdatePassword = async () => {
  if (!passwordFormRef.value) return;
  const valid = await passwordFormRef.value.validate().catch(() => false);
  if (!valid) return;

  isChangingPassword.value = true;
  try {
    const res: any = await updatePasswordApi(passwordForm.value);
    if (res?.code === 0) {
      ElMessage.success("密码修改成功");
      passwordDialogVisible.value = false;
    } else {
      ElMessage.error(res?.message || "密码修改失败");
    }
  } catch (error) {
    console.error("修改密码失败:", error);
    ElMessage.error("密码修改失败，请稍后重试");
  } finally {
    isChangingPassword.value = false;
  }
};

onMounted(() => {
  userStore.fetchUserInfo();
  loadStats();
});
</script>

<style scoped lang="less">
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 4px;
}

/* 顶部欢迎区 */
.profile-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 26px 30px;
  overflow: hidden;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(130deg, #1a478a 0%, #2d6bc4 100%);
  box-shadow: 0 16px 32px -20px rgba(22, 54, 110, 0.6);
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.hero-glow--one {
  top: -90px;
  right: 60px;
  width: 240px;
  height: 240px;
  background: radial-gradient(circle, rgba(145, 204, 117, 0.34), transparent 70%);
}

.hero-glow--two {
  bottom: -120px;
  left: 30%;
  width: 260px;
  height: 260px;
  background: radial-gradient(circle, rgba(120, 190, 255, 0.28), transparent 70%);
}

.hero-main {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.hero-avatar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  font-size: 28px;
  font-weight: 600;
  color: #1a478a;
  background: #fff;
  box-shadow: 0 8px 20px -8px rgba(9, 26, 51, 0.6);
}

.hero-text {
  min-width: 0;
}

.hero-greeting {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.4px;
}

.hero-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.78);

  .hero-tag {
    color: #1a478a;
    background: #e8f3ff;
    border-color: transparent;
  }
}

.hero-actions {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 10px;

  .ghost-button {
    color: #fff;
    background: rgba(255, 255, 255, 0.14);
    border: 1px solid rgba(255, 255, 255, 0.4);

    &:hover {
      color: #fff;
      background: rgba(255, 255, 255, 0.24);
      border-color: rgba(255, 255, 255, 0.6);
    }
  }
}

/* 数据概览 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #eef1f7;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 16px 30px -22px rgba(22, 54, 110, 0.55);
  }
}

.stat-icon {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;

  &.is-blue {
    color: #1a478a;
    background: #e8f0fc;
  }

  &.is-violet {
    color: #6f4bd8;
    background: #f0ebfd;
  }

  &.is-green {
    color: #3d8b46;
    background: #e9f6ea;
  }

  &.is-amber {
    color: #b7791f;
    background: #fdf3e2;
  }
}

.stat-body {
  min-width: 0;
}

.stat-label {
  font-size: 13px;
  color: #8a97ad;
}

.stat-value {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 600;
  color: #16325c;

  small {
    margin-left: 4px;
    font-size: 12px;
    font-weight: 400;
    color: #8a97ad;
  }
}

.stat-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #a3aec1;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* 中等宽度屏幕：概览卡两列 */
@media (max-width: 1280px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .profile-hero {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
