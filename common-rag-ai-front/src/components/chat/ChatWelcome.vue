<template>
  <div class="welcome">
    <div class="welcome__hero">
      <div class="welcome__avatar">
        <img :src="robotUrl" alt="智能助手" />
      </div>
      <h2 class="welcome__title">你好，我是智能知识库客服</h2>
      <p class="welcome__desc">
        我已连接企业知识库，可以解答制度、流程与规则问题。
        可以直接提问，也可以从下面的示例开始。
      </p>
    </div>

    <div class="welcome__cards">
      <div v-for="item in capabilities" :key="item.title" class="capability-card">
        <div class="capability-card__icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div class="capability-card__title">{{ item.title }}</div>
        <div class="capability-card__desc">{{ item.desc }}</div>
      </div>
    </div>

    <div v-if="visibleHotQuestions.length" class="welcome__hot">
      <div class="welcome__hot-header">
        <span class="welcome__hot-label">大家都在问</span>
        <button v-if="canRotate" type="button" class="welcome__hot-more" @click="rotateHotQuestions">
          <el-icon><Refresh /></el-icon>
          <span>换一批</span>
        </button>
      </div>
      <div class="welcome__hot-list">
        <button
          v-for="(item, index) in visibleHotQuestions"
          :key="`${item.question}-${index}`"
          type="button"
          class="hot-card"
          @click="emit('ask', item.question)"
        >
          <span class="hot-card__question">{{ item.question }}</span>
          <span class="hot-card__meta">
            <span v-if="item.hitCount > 0" class="hot-card__hit">{{ item.hitCount }} 次提问</span>
            <span v-for="keyword in item.keywords" :key="keyword" class="hot-card__tag">{{ keyword }}</span>
          </span>
        </button>
      </div>
    </div>

    <div v-else-if="fallbackSuggestions.length" class="welcome__suggestions">
      <div class="welcome__suggestions-label">你可以这样问</div>
      <div class="welcome__chips">
        <button
          v-for="question in fallbackSuggestions"
          :key="question"
          type="button"
          class="suggestion-chip"
          @click="emit('ask', question)"
        >
          <el-icon><ArrowRight /></el-icon>
          <span>{{ question }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ArrowRight, ChatDotRound, Document, Guide, Reading, Refresh } from "@element-plus/icons-vue";
import robotUrl from "@/assets/robot.svg";
import type { HotQuestion } from "@/api/ChatApi";

/** 单次展示的推荐问题条数，“换一批”按该步长轮转 */
const HOT_BATCH_SIZE = 3;

/** 后端无推荐数据时的兜底引导，保证首屏始终可用 */
const DEFAULT_SUGGESTIONS = [
  "差旅报销需要在几天内提交？",
  "报销需要准备哪些凭证？",
  "财务审核多久能完成？",
  "出差前需要先走审批吗？",
];

const props = withDefaults(
  defineProps<{
    suggestions?: string[];
    hotQuestions?: HotQuestion[];
  }>(),
  {
    suggestions: () => [],
    hotQuestions: () => [],
  }
);

const emit = defineEmits<{
  (e: "ask", question: string): void;
}>();

// 仅在前端轮转切片，不重复请求后端
const hotOffset = ref(0);

const visibleHotQuestions = computed(() => {
  const list = props.hotQuestions;
  if (!list.length) return [];
  const size = Math.min(HOT_BATCH_SIZE, list.length);
  const start = hotOffset.value % list.length;
  return Array.from({ length: size }, (_, index) => list[(start + index) % list.length]);
});

const canRotate = computed(() => props.hotQuestions.length > HOT_BATCH_SIZE);

const rotateHotQuestions = () => {
  hotOffset.value = (hotOffset.value + HOT_BATCH_SIZE) % props.hotQuestions.length;
};

const fallbackSuggestions = computed(() =>
  props.suggestions.length ? props.suggestions : DEFAULT_SUGGESTIONS
);

const capabilities = [
  {
    icon: Reading,
    title: "制度问答",
    desc: "差旅、报销、考勤等条款一秒定位",
  },
  {
    icon: Document,
    title: "文档要点",
    desc: "长文档自动切片检索并提炼要点",
  },
  {
    icon: Guide,
    title: "流程指引",
    desc: "审批与办事流程分步骤说明",
  },
  {
    icon: ChatDotRound,
    title: "多轮追问",
    desc: "结合上下文深入追问，答案可溯源",
  },
];
</script>

<style scoped lang="less">
.welcome {
  max-width: 720px;
  margin: 0 auto;
  padding: 12px 4px 24px;
}

.welcome__hero {
  text-align: center;
}

.welcome__avatar {
  display: inline-flex;
  width: 62px;
  height: 62px;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  background: linear-gradient(140deg, #eaf2ff 0%, #d9e7ff 100%);
  box-shadow: 0 10px 24px rgba(47, 124, 246, 0.16);

  img {
    width: 34px;
    height: 34px;
  }
}

.welcome__title {
  margin: 16px 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: #16233a;
}

.welcome__desc {
  margin: 0 auto;
  max-width: 520px;
  font-size: 13px;
  line-height: 1.8;
  color: #6b7a90;
}

.welcome__cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 26px;
}

.capability-card {
  padding: 14px 16px;
  border: 1px solid #e8eef7;
  border-radius: 12px;
  background: #fbfcff;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;

  &:hover {
    border-color: #cfe0ff;
    box-shadow: 0 8px 18px rgba(47, 124, 246, 0.1);
    transform: translateY(-2px);
  }
}

.capability-card__icon {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: #eef4ff;
  color: #2f7cf6;
  font-size: 15px;
}

.capability-card__title {
  margin-top: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #24344d;
}

.capability-card__desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #8b9bb2;
}

.welcome__hot {
  margin-top: 24px;
}

.welcome__hot-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.welcome__hot-label {
  font-size: 12px;
  color: #93a1b5;
}

.welcome__hot-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: none;
  background: transparent;
  color: #2f7cf6;
  font-size: 12px;
  cursor: pointer;

  &:hover {
    color: #1a5fd0;
  }
}

.welcome__hot-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.hot-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid #dbe7f9;
  border-radius: 12px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: #2f7cf6;
    box-shadow: 0 8px 18px rgba(47, 124, 246, 0.12);
    transform: translateY(-2px);

    .hot-card__question {
      color: #2f7cf6;
    }
  }
}

.hot-card__question {
  display: -webkit-box;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.6;
  color: #24344d;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.hot-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.hot-card__hit {
  padding: 2px 8px;
  border-radius: 999px;
  background: #fff5e6;
  color: #f0932b;
  font-size: 11px;
}

.hot-card__tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f0f4fb;
  color: #48576c;
  font-size: 11px;
}

.welcome__suggestions {
  margin-top: 24px;
}

.welcome__suggestions-label {
  margin-bottom: 10px;
  font-size: 12px;
  color: #93a1b5;
}

.welcome__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.suggestion-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid #dbe7f9;
  border-radius: 999px;
  background: #ffffff;
  color: #48576c;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;

  .el-icon {
    font-size: 12px;
    color: #8fa3bf;
  }

  &:hover {
    border-color: #2f7cf6;
    color: #2f7cf6;
    background: #f4f8ff;

    .el-icon {
      color: #2f7cf6;
    }
  }
}

@media (max-width: 900px) {
  .welcome__cards {
    grid-template-columns: minmax(0, 1fr);
  }

  .welcome__hot-list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>