<template>
  <div class="answer-prompt" v-loading="isLoading">
    <el-card shadow="never" class="answer-prompt__card">
      <header class="answer-prompt__header">
        <div>
          <h2 class="answer-prompt__title">问答提示词</h2>
          <p class="answer-prompt__subtitle">
            控制 AI 问答的行业边界：系统提示词 + 低召回拒答，保存后下一次提问立即生效，无需重启
          </p>
        </div>
        <el-tag v-if="updateTime" type="success" effect="plain">
          最近保存 {{ updateTime }}
        </el-tag>
        <el-tag v-else type="info" effect="plain">尚未保存，当前为系统默认</el-tag>
      </header>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="answer-prompt__alert"
        title="「只能依据【资料N】作答」是抑制模型编造的核心约束，删除前请确认后果；对客只展示参考文件名与片段摘要，不展示检索得分与分片序号，提示词中无需再要求标注编号；提示词对全部知识库问答生效。"
      />

      <div class="answer-prompt__body">
        <!-- 左栏：提示词与拒答策略编辑 -->
        <section class="answer-prompt__main">
          <div class="answer-prompt__section">
            <div class="answer-prompt__section-title">
              系统提示词
              <span class="answer-prompt__counter">{{ form.systemPrompt.length }} / 8000</span>
            </div>
            <el-input
              ref="promptInputRef"
              v-model="form.systemPrompt"
              type="textarea"
              :autosize="{ minRows: 14, maxRows: 20 }"
              maxlength="8000"
              resize="vertical"
              placeholder="请输入系统提示词，可使用下方占位符"
            />
            <div class="answer-prompt__placeholders">
              <span class="answer-prompt__placeholders-label">插入占位符</span>
              <el-button
                v-for="item in placeholders"
                :key="item.value"
                size="small"
                plain
                @click="insertPlaceholder(item.value)"
              >
                {{ item.value }}
              </el-button>
            </div>
            <ul class="answer-prompt__hints">
              <li v-for="item in placeholders" :key="item.value">
                <code>{{ item.value }}</code>
                {{ item.desc }}
              </li>
            </ul>
          </div>

          <div class="answer-prompt__section">
            <div class="answer-prompt__section-title">拒答话术</div>
            <el-input
              v-model="form.refusalMessage"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              maxlength="512"
              show-word-limit
            />
            <p class="answer-prompt__desc">
              检索未命中或问题超出业务范围时的固定回复，同时通过 {refusal} 注入提示词。
            </p>
          </div>

          <div class="answer-prompt__section">
            <div class="answer-prompt__row">
              <div class="answer-prompt__row-text">
                <div class="answer-prompt__row-label">检索未命中时直接拒答</div>
                <p class="answer-prompt__desc">
                  开启后不调用大模型，直接返回拒答话术，避免模型用通用知识编造；关闭则无资料时也会让模型自由回答。
                </p>
              </div>
              <el-switch v-model="form.refuseWhenNoContext" />
            </div>
            <div class="answer-prompt__row">
              <div class="answer-prompt__row-text">
                <div class="answer-prompt__row-label">最低应答相似度</div>
                <p class="answer-prompt__desc">
                  仅语义检索模式生效：最高相似度低于该值时同样拒答；混合与全文检索的分数尺度不同，不参与该判断。留空表示不按得分拒答。
                </p>
              </div>
              <el-input-number
                v-model="minScoreInput"
                :min="0"
                :max="1"
                :step="0.05"
                :precision="2"
                controls-position="right"
                class="answer-prompt__score"
              />
            </div>
          </div>

          <div class="answer-prompt__actions">
            <el-button type="primary" :loading="isSaving" @click="saveConfig">
              保存配置
            </el-button>
            <el-button :disabled="isSaving" @click="restoreDefault">恢复默认</el-button>
            <el-button link :disabled="isSaving" @click="loadConfig">重新加载</el-button>
          </div>
        </section>

        <!-- 右栏：占位符替换后的生效预览 -->
        <aside class="answer-prompt__preview">
          <div class="answer-prompt__section-title">
            生效预览
            <span class="answer-prompt__counter">{{ previewPrompt.length }} 字符</span>
          </div>
          <p class="answer-prompt__desc">
            占位符替换后模型每轮收到的系统提示词，{current_data} 按当天日期展示。
          </p>
          <pre class="answer-prompt__preview-body">{{ previewPrompt }}</pre>
        </aside>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getAnswerConfigApi,
  saveAnswerConfigApi,
  getAnswerDefaultApi,
} from "@/api/KnowHubApi";
import type { AnswerConfigDto } from "@/api/dto";

// 与后端 AnswerConfigDTO 的长度上限保持一致
const MAX_PROMPT_LENGTH = 8000;

const placeholders = [
  {
    value: "{refusal}",
    desc: "拒答话术，与下方「拒答话术」保持一致",
  },
  {
    value: "{current_data}",
    desc: "当天日期，形如 2026-11-19",
  },
];

// 默认值由后端 GET /default 提供，前端不再重复一份，避免两处漂移
const createEmptyConfig = (): AnswerConfigDto => ({
  systemPrompt: "",
  refusalMessage: "",
  refuseWhenNoContext: true,
  minAnswerScore: null,
});

const form = ref<AnswerConfigDto>(createEmptyConfig());
const updateTime = ref("");
const isLoading = ref(false);
const isSaving = ref(false);
const promptInputRef = ref();

// 最低应答相似度允许留空，留空表示不按得分拒答
const minScoreInput = computed<number | undefined>({
  get: () => form.value.minAnswerScore ?? undefined,
  set: (value) => {
    form.value.minAnswerScore = value ?? null;
  },
});

const today = () => {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
};

// 预览与实际请求同一套替换规则：先填拒答话术，再填日期
const previewPrompt = computed(() =>
  (form.value.systemPrompt || "")
    .replace(/\{refusal\}/g, form.value.refusalMessage || "{refusal}")
    .replace(/\{current_data\}/g, today())
);

const formatTime = (value?: string | null) => {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (item: number) => String(item).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate()
  )} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

const fillForm = (data: AnswerConfigDto) => {
  form.value = {
    systemPrompt: data.systemPrompt ?? "",
    refusalMessage: data.refusalMessage ?? "",
    refuseWhenNoContext: data.refuseWhenNoContext ?? true,
    minAnswerScore: data.minAnswerScore ?? null,
  };
};

const loadConfig = () => {
  isLoading.value = true;
  getAnswerConfigApi()
    .then((res) => {
      if (res.code === 0 && res.data) {
        fillForm(res.data);
        updateTime.value = formatTime(res.data.updateTime);
        return;
      }
      ElMessage({ type: "error", message: res.message || "提示词配置加载失败" });
    })
    .catch((err) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "提示词配置加载失败",
      });
    })
    .finally(() => {
      isLoading.value = false;
    });
};

// 在光标处插入占位符，插入后把光标移到占位符之后
const insertPlaceholder = (value: string) => {
  const textarea = promptInputRef.value?.textarea as HTMLTextAreaElement | undefined;
  if (!textarea) {
    form.value.systemPrompt = `${form.value.systemPrompt}${value}`;
    return;
  }
  if (form.value.systemPrompt.length + value.length > MAX_PROMPT_LENGTH) {
    ElMessage({ type: "warning", message: "提示词长度已达上限，无法插入占位符" });
    return;
  }
  const start = textarea.selectionStart ?? form.value.systemPrompt.length;
  const end = textarea.selectionEnd ?? start;
  const text = form.value.systemPrompt;
  form.value.systemPrompt = `${text.slice(0, start)}${value}${text.slice(end)}`;
  nextTick(() => {
    textarea.focus();
    textarea.setSelectionRange(start + value.length, start + value.length);
  });
};

const saveConfig = () => {
  if (!form.value.systemPrompt.trim()) {
    ElMessage({ type: "warning", message: "系统提示词不能为空" });
    return;
  }
  if (form.value.systemPrompt.length > MAX_PROMPT_LENGTH) {
    ElMessage({ type: "warning", message: `系统提示词不能超过 ${MAX_PROMPT_LENGTH} 字符` });
    return;
  }
  isSaving.value = true;
  saveAnswerConfigApi({ ...form.value })
    .then((res) => {
      if (res.code === 0) {
        ElMessage({ type: "success", message: res.message || "配置已保存，下一次提问生效" });
        if (res.data) {
          fillForm(res.data);
          updateTime.value = formatTime(res.data.updateTime);
        }
        return;
      }
      ElMessage({ type: "error", message: res.message });
    })
    .catch((err) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "保存失败，请稍后重试",
      });
    })
    .finally(() => {
      isSaving.value = false;
    });
};

// 恢复默认只填充表单，需再点保存才落库，避免误覆盖线上提示词
const restoreDefault = () => {
  ElMessageBox.confirm(
    "将把系统默认的提示词与拒答策略填入表单，点击保存后才会生效，是否继续？",
    "恢复默认",
    {
      confirmButtonText: "填入默认值",
      cancelButtonText: "取消",
      type: "warning",
    }
  )
    .then(() => {
      isLoading.value = true;
      getAnswerDefaultApi()
        .then((res) => {
          if (res.code === 0 && res.data) {
            fillForm(res.data);
            ElMessage({ type: "success", message: "已填入默认值，点击保存后生效" });
            return;
          }
          ElMessage({ type: "error", message: res.message || "默认提示词获取失败" });
        })
        .catch((err) => {
          ElMessage({
            type: "error",
            message: err?.response?.data?.message ?? "默认提示词获取失败",
          });
        })
        .finally(() => {
          isLoading.value = false;
        });
    })
    .catch(() => {});
};

onMounted(loadConfig);
</script>

<style scoped lang="less">
.answer-prompt {
  display: flex;
  /* 与 RagChatView 对齐：主框架已扣除 Header/Footer，el-main 上下各 20px 内边距；
     全局 body 为 overflow hidden，页面必须自建滚动区域，否则底部保存操作会被裁掉 */
  height: var(--app-content-h);
  box-sizing: border-box;

  .answer-prompt__card {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-width: 0;
    min-height: 0;
    border: none;
    border-radius: 10px;
  }

  /* 卡片主体独立滚动，长提示词超出屏幕时仍能滚到保存按钮 */
  :deep(.el-card__body) {
    flex: 1;
    min-height: 0;
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      background: #d7e2f2;
      border-radius: 3px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }
  }

  .answer-prompt__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 16px;
  }

  .answer-prompt__title {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: #1f2d3d;
  }

  .answer-prompt__subtitle {
    margin: 6px 0 0;
    font-size: 13px;
    color: #8c939d;
  }

  .answer-prompt__alert {
    margin-bottom: 18px;
  }

  .answer-prompt__body {
    display: flex;
    gap: 20px;
    align-items: flex-start;
  }

  .answer-prompt__main {
    flex: 1 1 58%;
    min-width: 0;
  }

  .answer-prompt__preview {
    flex: 1 1 42%;
    min-width: 0;
    padding: 14px 16px;
    background: #f7f9fc;
    border: 1px solid #e6ebf2;
    border-radius: 8px;
  }

  .answer-prompt__section {
    margin-bottom: 22px;
  }

  .answer-prompt__section-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: 14px;
    font-weight: 600;
    color: #1f2d3d;
  }

  .answer-prompt__counter {
    font-size: 12px;
    font-weight: 400;
    color: #a8b0bb;
  }

  .answer-prompt__placeholders {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
  }

  .answer-prompt__placeholders-label {
    font-size: 12px;
    color: #8c939d;
  }

  .answer-prompt__hints {
    margin: 10px 0 0;
    padding-left: 18px;
    font-size: 12px;
    line-height: 1.9;
    color: #8c939d;

    code {
      padding: 1px 6px;
      margin-right: 6px;
      border-radius: 4px;
      background: #eef3fa;
      color: #1a478a;
    }
  }

  .answer-prompt__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    padding: 12px 16px;
    border: 1px solid #edf0f5;
    border-radius: 8px;

    & + .answer-prompt__row {
      margin-top: 12px;
    }
  }

  .answer-prompt__row-text {
    min-width: 0;
  }

  .answer-prompt__row-label {
    font-size: 14px;
    font-weight: 500;
    color: #1f2d3d;
  }

  .answer-prompt__desc {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 1.7;
    color: #8c939d;
  }

  .answer-prompt__score {
    width: 120px;
    flex: 0 0 120px;
  }

  .answer-prompt__actions {
    display: flex;
    align-items: center;
    gap: 12px;
    padding-top: 4px;
  }

  .answer-prompt__preview-body {
    margin: 10px 0 0;
    padding: 14px;
    max-height: calc(100vh - 320px);
    overflow: auto;
    background: #ffffff;
    border: 1px solid #e6ebf2;
    border-radius: 6px;
    font-family: "JetBrains Mono", Consolas, "Courier New", monospace;
    font-size: 12px;
    line-height: 1.8;
    color: #38485c;
    white-space: pre-wrap;
    word-break: break-word;
  }

  @media (max-width: 1240px) {
    .answer-prompt__body {
      flex-direction: column;
    }

    .answer-prompt__main,
    .answer-prompt__preview {
      flex: 1 1 auto;
      width: 100%;
    }

    .answer-prompt__preview-body {
      max-height: 320px;
    }
  }
}
</style>
