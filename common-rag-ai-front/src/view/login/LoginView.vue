<template>
  <div class="login-page">
    <!-- 左侧品牌区：深蓝渐变 + 光斑装饰，窄屏隐藏 -->
    <section class="login-brand">
      <span class="brand-glow brand-glow--one"></span>
      <span class="brand-glow brand-glow--two"></span>

      <div class="brand-content">
        <div class="brand-logo">
          <img src="/logo.svg" alt="Common-RAG-AI" />
          <span>Common-RAG-AI</span>
        </div>

        <h1 class="brand-title">
          让个人知识库<br />
          真正“会回答”
        </h1>
        <p class="brand-desc">
          基于 RAG 技术的个人知识库 AI 问答系统，文档切片、混合检索与流式生成一体化。
        </p>

        <ul class="brand-points">
          <li v-for="point in brandPoints" :key="point.title">
            <span class="point-icon">
              <el-icon :size="18"><component :is="point.icon" /></el-icon>
            </span>
            <span class="point-text">
              <strong>{{ point.title }}</strong>
              <small>{{ point.desc }}</small>
            </span>
          </li>
        </ul>
      </div>

      <p class="brand-footer">© {{ year }} Common-RAG-AI · 知识库问答系统</p>
    </section>

    <!-- 右侧登录卡 -->
    <section class="login-panel">
      <div class="login-card">
        <header class="card-head">
          <h2>欢迎回来</h2>
          <p>登录后即可管理知识库并开始 AI 问答</p>
        </header>

        <el-form
          ref="formRef"
          :model="loginForm"
          :rules="loginRules"
          size="large"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="userName">
            <el-input
              v-model="loginForm.userName"
              placeholder="请输入用户名"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            class="login-submit"
            type="primary"
            :loading="isLoading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form>

        <p class="card-switch">
          还没有账号？
          <el-link type="primary" :underline="false" @click="showRegister">
            立即注册
          </el-link>
        </p>
      </div>
    </section>

    <!-- 注册弹窗：与登录卡保持同一套视觉 -->
    <el-dialog
      v-model="registerDialogVisible"
      title="注册账号"
      width="480px"
      align-center
      class="register-dialog"
      :close-on-click-modal="false"
    >
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="userName">
          <el-input v-model="registerForm.userName" placeholder="用于登录，请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            show-password
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="registerForm.name" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="registerForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="性别" prop="sex">
          <el-select v-model="registerForm.sex" placeholder="请选择性别">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="registerDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="isRegistering" @click="handleRegister">
            注 册
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import {
  ChatDotRound,
  Collection,
  Lock,
  Search,
  User,
} from "@element-plus/icons-vue";
import router from "@/router";
import { loginApi, registerUserApi } from "@/api/UserApi";

const year = new Date().getFullYear();

// 品牌区能力要点，纯展示文案
const brandPoints = [
  { icon: Collection, title: "知识库管理", desc: "上传文档自动切片、向量化" },
  { icon: Search, title: "混合检索", desc: "语义、全文与混合召回可配置" },
  { icon: ChatDotRound, title: "流式问答", desc: "回答附带引用来源，可追溯" },
];

const formRef = ref<FormInstance>();
const registerFormRef = ref<FormInstance>();
const isLoading = ref(false);
const isRegistering = ref(false);
const registerDialogVisible = ref(false);

// 默认预填管理员账号，方便本地直接登录；清空后仍按原校验提示
const loginForm = ref({
  userName: "admin",
  password: "admin",
});

const registerForm = ref({
  userName: "",
  password: "",
  name: "",
  phone: "",
  sex: "男",
});

const loginRules: FormRules = {
  userName: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};

const registerRules: FormRules = {
  userName: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 5, max: 20, message: "密码长度在 5 到 20 个字符", trigger: "blur" },
  ],
  name: [{ required: true, message: "请输入姓名", trigger: "blur" }],
  phone: [
    { required: true, message: "请输入手机号", trigger: "blur" },
    { pattern: /^1\d{10}$/, message: "手机号格式不正确", trigger: "blur" },
  ],
};

const handleLogin = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  isLoading.value = true;
  try {
    const res: any = await loginApi(loginForm.value.userName.trim(), loginForm.value.password);
    if (res?.code === 0 && res.data) {
      localStorage.setItem("token", res.data.token);
      // 沿用既有约定：userRole 存用户名，admin 视为管理员
      localStorage.setItem("userRole", res.data.userName);
      localStorage.setItem("userId", String(res.data.id));
      ElMessage({ message: "登录成功", type: "success" });
      router.push({ name: "ragChat" });
    } else {
      ElMessage({ message: res?.message || "登录失败", type: "error" });
    }
  } catch (error) {
    console.error("登录错误:", error);
    ElMessage({ message: "登录失败，请稍后重试", type: "error" });
  } finally {
    isLoading.value = false;
  }
};

const showRegister = () => {
  registerDialogVisible.value = true;
};

const resetRegisterForm = () => {
  registerForm.value = {
    userName: "",
    password: "",
    name: "",
    phone: "",
    sex: "男",
  };
};

const handleRegister = async () => {
  if (!registerFormRef.value) return;
  const valid = await registerFormRef.value.validate().catch(() => false);
  if (!valid) return;

  isRegistering.value = true;
  try {
    const res: any = await registerUserApi({ ...registerForm.value, status: 1 });
    if (res?.code === 0) {
      ElMessage({ message: "注册成功，请使用新账号登录", type: "success" });
      // 注册成功后回填用户名并关闭弹窗，减少一次手动输入
      loginForm.value.userName = registerForm.value.userName;
      loginForm.value.password = "";
      registerDialogVisible.value = false;
      resetRegisterForm();
    } else {
      ElMessage({ message: res?.message || "注册失败", type: "error" });
    }
  } catch (error) {
    console.error("注册错误:", error);
    ElMessage({ message: "注册失败，请稍后重试", type: "error" });
  } finally {
    isRegistering.value = false;
  }
};

onMounted(() => {
  // 已有登录态直接进入 AI 问答首页，避免重复登录
  if (localStorage.getItem("token")) {
    router.replace({ name: "ragChat" });
  }
});
</script>

<style scoped lang="less">
.login-page {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #eef3fb;
}

/* 左侧品牌区 */
.login-brand {
  position: relative;
  flex: 1 1 54%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 56px 64px 32px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(150deg, #0f2f5c 0%, #1a478a 52%, #2d6bc4 100%);
}

.brand-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.brand-glow--one {
  top: -140px;
  right: -120px;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(145, 204, 117, 0.32), transparent 68%);
}

.brand-glow--two {
  bottom: -180px;
  left: -100px;
  width: 460px;
  height: 460px;
  background: radial-gradient(circle, rgba(120, 190, 255, 0.3), transparent 70%);
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 520px;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.4px;

  img {
    width: 32px;
    height: 32px;
  }
}

.brand-title {
  margin-top: 56px;
  font-size: 40px;
  font-weight: 700;
  line-height: 1.32;
  letter-spacing: 1px;
}

.brand-desc {
  margin-top: 20px;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.76);
}

.brand-points {
  margin-top: 48px;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 20px;

  li {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .point-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.14);
    border: 1px solid rgba(255, 255, 255, 0.22);
  }

  .point-text {
    display: flex;
    flex-direction: column;
    gap: 3px;

    strong {
      font-size: 15px;
      font-weight: 600;
    }

    small {
      font-size: 13px;
      color: rgba(255, 255, 255, 0.66);
    }
  }
}

.brand-footer {
  position: relative;
  z-index: 1;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

/* 右侧登录卡 */
.login-panel {
  flex: 0 0 46%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background:
    radial-gradient(circle at 82% 12%, rgba(26, 71, 138, 0.08), transparent 46%),
    #eef3fb;
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 44px 40px 36px;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 24px 60px -24px rgba(22, 54, 110, 0.32);
}

.card-head {
  margin-bottom: 30px;

  h2 {
    font-size: 26px;
    font-weight: 700;
    color: #16325c;
  }

  p {
    margin-top: 10px;
    font-size: 13px;
    color: #8a97ad;
  }
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 20px;
  }

  :deep(.el-input__wrapper) {
    padding: 4px 14px;
    border-radius: 10px;
    background: #f5f8fd;
    box-shadow: none;
    transition: box-shadow 0.2s ease, background 0.2s ease;
  }

  :deep(.el-input__wrapper.is-focus) {
    background: #fff;
    box-shadow: 0 0 0 1px #2d6bc4 inset;
  }
}

.login-submit {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  font-size: 15px;
  letter-spacing: 4px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #1a478a, #2d6bc4);
  transition: opacity 0.2s ease, transform 0.2s ease;

  &:hover {
    opacity: 0.92;
    transform: translateY(-1px);
  }
}

.card-switch {
  margin-top: 22px;
  text-align: center;
  font-size: 13px;
  color: #8a97ad;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* 窄屏隐藏品牌区，登录卡居中 */
@media (max-width: 960px) {
  .login-brand {
    display: none;
  }

  .login-panel {
    flex: 1 1 100%;
  }
}
</style>
