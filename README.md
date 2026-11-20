# common-rag-ai 企业级知识库 + 智能问答系统

基于 Spring AI 与阿里云百炼（DashScope / 通义千问）构建的 RAG 知识库问答系统，配套 Vue 3 管理后台。覆盖文档入库、切片策略、向量化、混合检索、引用溯源、内容安全与运营统计的完整链路，可直接作为企业知识库应用的二次开发底座。
![img_1.png](img_1.png)
![img.png](img.png)
<img width="1914" height="887" alt="img-4" src="https://github.com/user-attachments/assets/37b504df-4d82-4edc-a0eb-d8c34bcddc0a" />
![img_3.png](img_3.png)
## 项目定位

- 面向企业内部资料的检索增强问答：上传文档即可问答，答案只依据知识库资料生成，资料不足时按规则拒答
- 面向二次开发：检索模式、切片策略、问答提示词、敏感词等关键能力均实现为可扩展模块，配置项支持页面化管理
- 前后端分离：`common-rag-ai-back`（Spring Boot 3 + Spring AI）与 `common-rag-ai-front`（Vue 3 + TypeScript）

## 功能特性

### AI 助手

- AI 问答：基于知识库的流式（SSE）多轮问答，支持会话列表与会话记忆
- 参考文件：回答下方展示引用的来源文件，按文件聚合去重，前端仅对客展示脱敏后的编号引用，不暴露检索排名
- 拒答策略：检索未命中或最高相关度低于阈值时返回固定拒答文案，并支持在页面上自定义话术
- AI 绘画：文生图能力页面

### 我的知识库

- 文档管理：文件上传至阿里云 OSS，按分类管理，展示大小、分类、状态、切片策略与分片数量，支持列表查询、下载（单文件流式下载与批量打包）
- 切片策略：固定长度、递归字符（推荐）、语义切片、结构化感知（目录优先）四种策略，参数可在上传弹窗内实时调整
- 切片预览：不落库、不写向量库的实时预览接口，用于上传前确认切片效果
- 文档切片管理：查看、编辑、重新切片，失败文件可重试
- 问答提示词：系统提示词与拒答文案页面化配置，落库 `answer_config` 表，配置文件中仅保留兜底默认值
- 检索配置：语义检索、全文检索、混合检索三种模式，可绑定知识库分类生效范围，支持 Rerank 重排

### 内容安全

- 敏感词管理：敏感词与敏感词分类维护，问答链路中参与输入输出过滤
- 日志管理：基于 `@Loggable` 注解与 AOP 的操作日志留痕
- 热点词分析：对问答日志做分词与词频统计，前端以词云与图表展示
- 热门问题配置：支持自动挖掘问答日志生成候选，再由运营人工干预确认

### 系统管理

- 用户管理：账号、角色（admin / user）与状态维护
- 个人中心：账号资料、安全设置与知识库数据概览

## 技术栈

| 层次 | 技术 | 版本 | 用途 |
| --- | --- | --- | --- |
| 后端框架 | Spring Boot | 3.4.5 | Web、JDBC、Redis、JPA 等基础能力 |
| AI 框架 | Spring AI + Spring AI Alibaba | 1.0.0 / 1.0.0.2 | ChatClient、Advisor、向量库抽象 |
| 大模型 | 阿里云百炼 DashScope | - | 对话、Embedding、Rerank |
| 向量库 | Milvus | 2.4.15（standalone，内嵌 etcd） | 向量存储与相似度检索，collection `vector_store` |
| 关系库 | MySQL | 8.x | 业务数据与切片元数据 |
| 缓存 | Redis | 7-alpine | 热词、热门问题等列表缓存 |
| 持久层 | MyBatis-Plus + PageHelper + Druid | 3.5.9 / 2.1.0 / 1.2.23 | ORM、分页、连接池 |
| 文档解析 | Spring AI Tika / PDFBox | 3.0.4 | PDF、Word、Markdown、TXT 等文档抽取 |
| 中文分词 | ik-analyzer | 8.5.0 | BM25 关键词检索与词频统计 |
| 对象存储 | 阿里云 OSS SDK | 3.17.4 | 原始文件存储与下载 |
| 鉴权 | jjwt | 0.9.1 | JWT 双令牌 |
| 前端框架 | Vue + TypeScript + Vite | 3.4 / 5.2 / 6.3 | SPA 与工程化构建 |
| 前端 UI | Element Plus + Pinia + Vue Router | 2.6 / 2.1 / 4.3 | 组件、状态、路由 |
| 前端能力 | fetch-event-source、marked、highlight.js、MathJax、ECharts | - | SSE 流式渲染、Markdown 与公式、图表与词云 |

## 目录结构

```
rag/
├── common-rag-ai-back/                    后端服务
│   ├── src/main/java/com/common/rag/
│   │   ├── annotation/                    @Loggable 等自定义注解
│   │   ├── aop/                           LoggingAspect 操作日志切面
│   │   ├── config/                        OSS、JWT、MyBatis-Plus、Redis、RAG 应答等配置
│   │   ├── constant/ context/             常量与线程级用户上下文
│   │   ├── controller/                    14 个 REST 控制器
│   │   ├── core/                          BaseResponse、ResultUtils、PageResult、拦截器
│   │   ├── entity/ mapper/ pojo/          实体、Mapper、出入参对象
│   │   ├── enums/                         切片策略、检索模式、融合方式、ETL 状态枚举
│   │   ├── etl/                           文档入库：DocumentChunkService、KnowledgeChunkIndexService
│   │   │   └── splitter/                  四种切片策略实现
│   │   ├── retrieval/                     BM25 检索、向量检索、RRF 融合、Rerank
│   │   ├── scheduled/                     热门问题挖掘等定时任务
│   │   ├── service/                       业务接口与实现（含 analysis 词频分析）
│   │   └── utils/                         JWT、OSS、检索等工具
│   ├── src/main/resources/
│   │   ├── application.yml                主配置（端口、数据源、模型、向量库、OSS、JWT）
│   │   ├── application-dev.yml            dev profile 覆盖项
│   │   ├── mapper/                        Mapper XML
│   │   ├── prompts/system-qa.st           系统问答提示词模板
│   │   ├── sql/                           init.sql 与各业务表建表脚本
│   │   └── stopwords/zh-stopwords.txt     中文停用词
│   └── src/test/                          单测与测试语料（PDF、Markdown）
├── common-rag-ai-front/                   前端工程
│   └── src/
│       ├── api/ http/                     接口封装与 axios / SSE 客户端
│       ├── components/chat/               对话组件（会话列表、消息项、输入框、Markdown 渲染）
│       ├── composables/ useChatSessions.ts 会话状态复用逻辑
│       ├── layout/ router/ store/         布局、路由与菜单、Pinia
│       ├── utils/                         分类候选、切片策略参数、HTML 脱敏
│       └── view/                          页面：ragChat、draw、know、sensitive、frequency…
├── start-all.ps1                          一键拉起 MySQL 库、Redis、Milvus、后端与前端
├── stop-all.ps1                           一键停止前后端与容器
└── volumes/                               本地 Milvus 数据卷（不入库）
```

## 核心链路

```
文档入库（ETL）
上传 -> OSS 存原件 -> Tika/PDFBox 抽取文本 -> 选择切片策略切分
     -> DashScope 向量化 -> 写入 Milvus（vector_store）
     -> 切片元数据与 vectorId 写入 MySQL（knowledge_chunk）

知识库问答（RAG）
提问 -> 敏感词过滤 -> 检索（语义 / 全文 BM25 / 混合 RRF，可选 Rerank）
     -> 上下文按文件聚合、去排名 -> 注入系统提示词（资料N + 拒答规则）
     -> ChatClient 流式返回 -> 前端渲染 Markdown/公式 + 参考文件与反馈操作栏
```

- 检索融合默认使用 RRF（`RRF_K=60`），可切换为加权融合；检索配置可绑定分类，未配置时对全局知识库生效
- 引用信息对客展示时使用匿名编号，检索排名仅保留在内部溯源数据中

## 快速开始

### 环境要求

- JDK 17 及以上（本机可用 JDK 22）
- Maven 3.8 及以上（工程无 mvnw 包装器）
- Node.js 18 及以上与 npm
- MySQL 8.x（本地 3306）、Redis（6379）、Milvus 2.4（19530）
- Docker Desktop（用于 Redis 与 Milvus 容器）

### 一键启动（Windows PowerShell）

```powershell
.\start-all.ps1
# 跳过构建或跳过基础设施
.\start-all.ps1 -SkipBuild
.\start-all.ps1 -SkipInfra
# 停止
.\stop-all.ps1
```

脚本会自动定位 JDK 与 Maven、创建 `common_rag` 库并导入 `init.sql`、拉起 `rag-redis` 与 `milvus-standalone` 容器、构建并启动后端（8989）、启动前端开发服务（8980）。

### 手动启动

```powershell
# 1. 初始化数据库：创建 common_rag 库，依次执行 resources/sql 下的建表脚本
# 2. 设置必要环境变量（见下表）
$env:DASHSCOPE_API_KEY = "<你的百炼 API Key>"

# 3. 启动后端
$env:JAVA_HOME = "C:\Users\<user>\.jdks\openjdk-22.0.2"
cd E:\rag\common-rag-ai-back
& "C:\Path\To\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run

# 4. 启动前端
cd E:\rag\common-rag-ai-front
npm install
npm run dev
```

前端访问 `http://localhost:8980`，开发服务器已将 `/api` 代理到 `http://localhost:8989`。首次启动后可用 `init.sql` 中预置的账号登录。

### 环境变量

仓库不保存任何真实密钥，以下变量按需注入：

| 变量 | 是否必填 | 说明 |
| --- | --- | --- |
| `DASHSCOPE_API_KEY` | 必填 | 百炼 API Key，对话、Embedding、Rerank 均依赖 |
| `WEBSEARCH_API_KEY` | 可选 | 联网搜索能力 Key |
| `ALI_OSS_AK` / `ALI_OSS_SK` | 上传文件时必填 | 阿里云 OSS 访问密钥 |
| `ALI_OSS_ENDPOINT` / `ALI_OSS_BUCKET` | 可选 | 默认 `oss-cn-beijing.aliyuncs.com` / `spring-rag-file` |
| `MYSQL_URL` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 可选 | 默认 `localhost:3306/common_rag`、`root` / `123456` |
| `JWT_ADMIN_SECRET` / `JWT_USER_SECRET` | 生产必改 | JWT 签名密钥，存在本地默认值 |

### 本地私有配置

本地开发不想逐次设置环境变量时，把真实值写入 `common-rag-ai-back/src/main/resources/application-local.yml`：

- `application.yml` 中 `spring.profiles.active` 已设为 `dev,local`，该文件会自动加载并覆盖占位值；文件缺失时不影响启动，回退为环境变量方式
- `application-local.yml` 已被根 `.gitignore` 排除，任何以 `-local.yml` 结尾的配置文件都不应提交
- 后续如需新增敏感项，一律加到这个文件，不要写回 `application.yml`

### 分支协作

- `master` 只保留可发布的干净基线，不含任何密钥
- `rag-dev` 承载日常开发与新功能，完成后合回：`git switch master; git merge --no-ff rag-dev`
- 提交前用 `git status` 确认没有 `application-local.yml` 之类的本地文件被打包

## 配置说明

`common-rag-ai-back/src/main/resources/application.yml` 关键项：

- `server.port`：后端端口，默认 8989
- `spring.ai.dashscope.api-key`：大模型访问凭证，由 `DASHSCOPE_API_KEY` 注入
- `spring.ai.vectorstore.milvus.*`：Milvus 连接、`collectionName=vector_store`、`embeddingDimension=1536`、`metricType=COSINE`、`initializeSchema=true` 自动建集合
- `spring.datasource.*`：MySQL 连接，Druid 连接池
- `spring.data.redis.*`：Redis 连接与 Lettuce 连接池
- `aliyun.alioss.*`：OSS endpoint、bucket 与密钥
- `common.jwt.*`：管理端与用户端两套密钥、过期时间与令牌名
- `rag.answer.*`：问答兜底提示词、拒答文案、`refuse-when-no-context`、`min-answer-score`，均可在「问答提示词」页面覆盖

## 接口约定

- 统一前缀 `/api/v1`，按业务域划分：`ai`、`chat`、`draw`、`knowledge`、`knowledge/chunks`、`knowledge/retrieval`、`knowledge/answer`、`hot-question`、`sensitive`、`category`、`frequency`、`log`、`user`
- 统一响应体 `BaseResponse`（`code` / `message` / `data`），分页结果使用 `PageResult`
- 鉴权为 JWT 双令牌：管理端请求头 `token`，用户端请求头 `authorization`；`admin` 角色才可访问内容安全与系统管理接口
- 问答与部分长耗时接口使用 SSE 流式返回

## 二次开发指引

- 新增切片策略：在 `etl/splitter` 下实现 `TextChunkSplitter`，补充 `ChunkStrategyEnum` 与前端 `utils/chunkStrategy.ts` 的参数定义
- 新增检索模式或融合算法：在 `retrieval` 下扩展 `KnowledgeRetrievalService`，同步 `RetrievalModeEnum` / `FusionStrategyEnum` 与检索配置页面
- 更换业务话术：优先在「问答提示词」页面配置（落库 `answer_config`），配置文件仅作兜底
- 引入新的全局配置项：按项目惯例落库并提供页面维护，避免只改配置文件
- 版本管理：根 `.gitignore` 已排除 `target/`、`node_modules/`、`dist/`、`.vite/`、`volumes/`、`.run/`、`.qoder/`、日志与 `.env*`；`src/test` 与 `start-all.ps1`、`stop-all.ps1` 属于仓库内容
- 代码风格：后端遵循现有分层的 controller / service / mapper 结构，实体使用 Lombok 并显式保留无参构造器

## 常见问题

- 启动报「无效的目标发行版」：`JAVA_HOME` 未指向 JDK 17+，PATH 中的 java 可能是 1.8
- 问答报模型调用失败或向量化为空：`DASHSCOPE_API_KEY` 未设置或已失效
- 报 Milvus 连接超时（DEADLINE_EXCEEDED）：`milvus-standalone` 容器未启动，可执行 `docker start milvus-standalone`
- 上传知识库文件失败：`ALI_OSS_AK` / `ALI_OSS_SK` 未配置或 bucket 无权限
- `common-rag-ai-back/docker-compose.yml` 为早期 pgvector 方案的遗留文件，当前基础设施请使用 `start-all.ps1` 或手动启动 MySQL、Redis 与 Milvus
