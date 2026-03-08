# AI 零代码应用生成平台

> 一套对标大厂的企业级 AI 应用生成平台，采用微服务架构 + AI 智能体技术，实现"所想即所得"的代码自动生成能力。

---

## 一、项目背景与价值

### 1.1 项目定位

本项目是一个 **AI 驱动的低代码/零代码平台**，用户只需用自然语言描述需求，AI 即可自动生成完整的前端应用代码。核心解决以下痛点：

| 痛点 | 解决方案 |
|------|----------|
| 前端开发门槛高 | 自然语言描述即可生成代码，零编程基础可用 |
| 原型验证周期长 | 分钟级生成可用原型，快速验证产品想法 |
| 重复性工作多 | AI 自动生成通用页面，开发者专注核心业务 |
| 技术选型困难 | 统一技术栈（Vue 3），标准化输出 |

### 1.2 核心功能

| 功能模块 | 功能描述 | 技术亮点 |
|----------|----------|----------|
| **智能代码生成** | 输入自然语言，AI 自动生成完整前端项目 | LangChain4j + AI Agent + Tool Calling |
| **可视化编辑** | 选中页面元素，对话式修改页面 | iframe 通信 + 元素定位 |
| **实时预览** | 代码即改即看，实时渲染效果 | Node.js 构建 + 热更新 |
| **一键部署** | 自动构建、部署、截图、生成分享链接 | Docker + Nginx + Selenium |
| **源码下载** | 下载完整项目源码，支持二次开发 | Zip 压缩 + 项目结构生成 |
| **管理后台** | 用户管理、应用管理、数据统计 | RBAC + 监控大盘 |

### 1.3 技术选型对比

| 技术点 | 可选方案 | 最终选择 | 选择理由 |
|--------|----------|----------|----------|
| AI 框架 | LangChain4j / Spring AI / 原生 SDK | LangChain4j + Spring AI Alibaba | LangChain4j 提供完整的 AI Agent 能力，Spring AI Alibaba 支持阿里云模型 |
| 微服务框架 | Spring Cloud Netflix / Spring Cloud Alibaba | Spring Cloud Alibaba | 国内生态更好，Nacos 一站式解决注册+配置 |
| RPC 框架 | Feign / Dubbo / gRPC | Dubbo 3.3 | 性能更优，支持 Triple 协议，云原生友好 |
| ORM 框架 | MyBatis / MyBatis-Plus / MyBatis-Flex | MyBatis-Flex | 更轻量，性能更好，支持多表关联 |
| 缓存方案 | Redis / Memcached / 本地缓存 | Redis + Caffeine 多级缓存 | 兼顾性能与一致性 |

---

## 二、系统架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户层 (Client)                                 │
│         ┌─────────────────┐         ┌─────────────────┐                    │
│         │   Web Browser   │         │   Mobile App    │                    │
│         │   (Vue 3 SPA)   │         │   (响应式适配)   │                    │
│         └────────┬────────┘         └────────┬────────┘                    │
└──────────────────┼──────────────────────────┼──────────────────────────────┘
                   │                          │
                   ▼                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            接入层 (Gateway)                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         Nginx                                        │   │
│  │   • 静态资源服务 (Vue 构建产物)                                       │   │
│  │   • 反向代理 (API 请求转发)                                          │   │
│  │   • 负载均衡 (多实例部署)                                            │   │
│  │   • SSL 终止 (HTTPS)                                                │   │
│  │   • Gzip 压缩                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          微服务层 (Services)                                 │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ user-service │  │ app-service  │  │screenshot-svc│  │ node-builder │   │
│  │   用户服务    │  │   应用服务    │  │   截图服务    │  │   构建服务    │   │
│  │  Port: 8124  │  │  Port: 8126  │  │  Port: 8127  │  │  Port: 3000  │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │
│         │                 │                 │                 │            │
│         │    ┌────────────┼─────────────────┼─────────────────┘            │
│         │    │            │                 │                              │
│         │    │  ┌─────────▼─────────────────▼─────────┐                    │
│         │    │  │         Dubbo RPC (Triple)          │                    │
│         │    │  │   服务注册: Nacos                    │                    │
│         │    │  │   服务发现: Nacos                    │                    │
│         │    │  └─────────────────────────────────────┘                    │
│         │    │                                                               │
└─────────┼────┼───────────────────────────────────────────────────────────────┘
          │    │
          ▼    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           AI 服务层 (AI Layer)                               │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    AI Code Generator Module                          │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐  │   │
│  │  │ AI Router   │  │ Code Gen    │  │ Tool Manager│  │ Memory     │  │   │
│  │  │ 智能路由    │  │ 代码生成    │  │ 工具管理    │  │ 对话记忆    │  │   │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬─────┘  │   │
│  │         │                │                │                │        │   │
│  │         ▼                ▼                ▼                ▼        │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │               LangChain4j AI Services                        │    │   │
│  │  │  • AiService 接口代理                                        │    │   │
│  │  │  • Tool Calling 机制                                         │    │   │
│  │  │  • Structured Output 结构化输出                              │    │   │
│  │  │  • Streaming 流式输出                                        │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      LLM Providers                                   │   │
│  │   ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐       │   │
│  │   │ 通义千问   │  │ DeepSeek  │  │  OpenAI   │  │  Claude   │       │   │
│  │   │ DashScope │  │   API     │  │   API     │  │   API     │       │   │
│  │   └───────────┘  └───────────┘  └───────────┘  └───────────┘       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        中间件 & 存储层 (Infra)                               │
│                                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │   Nacos     │  │   Redis     │  │   MySQL     │  │   COS       │        │
│  │ ┌─────────┐ │  │ ┌─────────┐ │  │             │  │             │        │
│  │ │服务注册 │ │  │ │  会话   │ │  │  user      │  │  代码文件   │        │
│  │ │配置中心 │ │  │ │  缓存   │ │  │  app       │  │  截图图片   │        │
│  │ └─────────┘ │  │ │  限流   │ │  │  chat_hist │  │  封面图片   │        │
│  └─────────────┘  │ │  分布式锁│ │  │             │  │             │        │
│                   │ └─────────┘ │  └─────────────┘  └─────────────┘        │
│                   │ ┌─────────┐ │                                        │
│                   │ │对话记忆 │ │                                        │
│                   │ │(Redis)  │ │                                        │
│                   │ └─────────┘ │                                        │
│                   └─────────────┘                                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 微服务模块详解

```
yu-ai-code-mother-microservice/
├── yu-ai-code-common/                    # 公共基础模块
│   ├── annotation/                       # 自定义注解（@AuthCheck 等）
│   ├── common/                           # 通用响应封装
│   ├── config/                           # 通用配置（CORS、JSON 等）
│   ├── exception/                        # 全局异常处理
│   ├── manager/                          # 通用管理器（COS 文件上传）
│   └── utils/                            # 工具类
│
├── yu-ai-code-model/                     # 数据模型模块
│   ├── dto/                              # 数据传输对象
│   ├── entity/                           # 数据库实体
│   ├── enums/                            # 枚举定义
│   └── vo/                               # 视图对象
│
├── yu-ai-code-client/                    # Dubbo 服务接口模块
│   └── innerservice/                     # 内部服务接口定义
│       ├── InnerUserService.java         # 用户服务接口
│       └── InnerScreenshotService.java   # 截图服务接口
│
├── yu-ai-code-user/                      # 用户服务 (Port: 8124)
│   ├── controller/                       # REST 接口
│   ├── service/                          # 业务逻辑
│   └── aop/                              # 权限拦截器
│
├── yu-ai-code-app/                       # 应用服务 (Port: 8126) ⭐核心服务
│   ├── controller/                       # REST 接口
│   ├── service/                          # 业务逻辑
│   ├── core/                             # 核心业务
│   │   ├── builder/                      # 项目构建器
│   │   ├── parser/                       # 代码解析器
│   │   ├── saver/                        # 文件保存器
│   │   └── handler/                      # 流式处理器
│   ├── ratelimter/                       # 分布式限流
│   └── mapper/                           # 数据访问层
│
├── yu-ai-code-ai/                        # AI 服务模块 ⭐核心模块
│   ├── ai/                               # AI 服务实现
│   │   ├── AiCodeGeneratorService.java   # 代码生成服务
│   │   └── AiCodeGenTypeRoutingService.java # 智能路由服务
│   ├── config/                           # AI 配置
│   ├── guardrail/                        # AI 安全护轨
│   ├── model/                            # AI 模型定义
│   └── tools/                            # AI 工具集
│       ├── FileWriteTool.java            # 文件写入工具
│       ├── FileReadTool.java             # 文件读取工具
│       ├── FileModifyTool.java           # 文件修改工具
│       ├── FileDeleteTool.java           # 文件删除工具
│       ├── FileDirReadTool.java          # 目录读取工具
│       └── ExitTool.java                 # 任务完成工具
│
└── yu-ai-code-screenshot/                # 截图服务 (Port: 8127)
    ├── service/                          # 截图服务实现
    └── utils/                            # 截图工具类
```

### 2.3 核心业务流程

#### 2.3.1 AI 代码生成流程

```
┌─────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  用户   │    │  app-service │    │  ai-service │    │    LLM      │
└────┬────┘    └──────┬──────┘    └──────┬──────┘    └──────┬──────┘
     │                │                  │                  │
     │ 输入需求描述    │                  │                  │
     │───────────────>│                  │                  │
     │                │                  │                  │
     │                │ 1. 智能路由      │                  │
     │                │ 选择生成策略     │                  │
     │                │─────────────────>│                  │
     │                │                  │                  │
     │                │                  │ 2. 构建 Prompt   │
     │                │                  │─────────────────>│
     │                │                  │                  │
     │                │                  │ 3. 流式输出      │
     │                │                  │<─────────────────│
     │                │                  │                  │
     │                │                  │ 4. Tool Calling  │
     │                │                  │<────────────────>│
     │                │                  │                  │
     │                │                  │ 5. 执行工具      │
     │                │                  │  (写文件/读文件) │
     │                │                  │                  │
     │                │ 6. 返回生成结果  │                  │
     │                │<─────────────────│                  │
     │                │                  │                  │
     │                │ 7. 保存文件      │                  │
     │                │  更新数据库      │                  │
     │                │                  │                  │
     │ 8. 返回结果    │                  │                  │
     │<───────────────│                  │                  │
     │                │                  │                  │
```

#### 2.3.2 流式输出实现原理

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Streaming Pipeline                            │
│                                                                       │
│   LLM API  ──►  SSE/Chunk  ──►  WebFlux/Flux  ──►  HTTP SSE Response │
│                                                                       │
│   ┌──────────────────────────────────────────────────────────────┐   │
│   │                        详细流程                                │   │
│   │                                                                │   │
│   │  1. LLM 返回 Token 流                                         │   │
│   │     ┌────────────────────────────────────────────┐           │   │
│   │     │ data: {"content": "生成"}                  │           │   │
│   │     │ data: {"content": "一个"}                  │           │   │
│   │     │ data: {"content": "按钮"}                  │           │   │
│   │     │ ...                                       │           │   │
│   │     └────────────────────────────────────────────┘           │   │
│   │                                                                │   │
│   │  2. LangChain4j TokenStream 接收                              │   │
│   │     TokenStream.onNext(token -> {                             │   │
│   │         // 发送到前端                                          │   │
│   │     });                                                        │   │
│   │                                                                │   │
│   │  3. 前端 EventSource 接收                                      │   │
│   │     const eventSource = new EventSource(url);                 │   │
│   │     eventSource.onmessage = (event) => {                      │   │
│   │         // 实时渲染                                            │   │
│   │     };                                                         │   │
│   └──────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 三、技术栈详解

### 3.1 后端技术栈

| 分类 | 技术 | 版本 | 选型理由 |
|------|------|------|----------|
| **核心框架** | Java | 21 LTS | 最新长期支持版，支持虚拟线程、Record、模式匹配 |
| | Spring Boot | 3.5.4 | 最新版本，AOT 编译、Observability 内置支持 |
| | Spring Cloud Alibaba | 2023.0.1.0 | 国内生态成熟，文档丰富 |
| **微服务** | Dubbo | 3.3.0 | 高性能 RPC，支持 Triple 协议，云原生 |
| | Nacos | 2.5.2 | 注册中心+配置中心二合一 |
| **数据访问** | MyBatis-Flex | 1.11.1 | 比 MyBatis-Plus 更轻量，性能更优 |
| | MySQL | 8.0 | 主数据库，支持 JSON 类型 |
| | HikariCP | 4.0.3 | 高性能连接池 |
| **缓存** | Redis | 7.x | 分布式缓存、会话、限流、分布式锁 |
| | Redisson | 3.50.0 | Redis 客户端，丰富的分布式对象 |
| | Caffeine | - | 本地缓存，与 Redis 组成二级缓存 |
| **AI 框架** | LangChain4j | 1.1.0 | Java 版 LangChain，AI 应用开发首选 |
| | LangGraph4j | 1.6.0-rc2 | AI 工作流引擎，构建复杂 Agent |
| | Spring AI Alibaba | 1.1.0.0-RC2 | 阿里云 AI 框架，支持通义千问 |
| | DashScope SDK | 2.21.1 | 阿里云大模型 API |
| **工具库** | Hutool | 5.8.38 | Java 工具类库 |
| | Lombok | 1.18.36 | 简化代码 |
| **文档** | Knife4j | 4.4.0 | OpenAPI 3.0 文档生成 |
| **监控** | Actuator | - | Spring Boot 监控端点 |
| | Prometheus | - | 指标采集 |
| | Grafana | - | 可视化监控大盘 |
| **其他** | Selenium | 4.33.0 | 网页自动化 |
| | 腾讯云 COS | 5.6.227 | 对象存储 |

### 3.2 前端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.17 | Composition API，性能优化 |
| TypeScript | 5.8 | 类型安全 |
| Vite | 7.0 | 极速构建 |
| Ant Design Vue | 4.2.6 | 企业级 UI |
| Pinia | 3.0.3 | 状态管理 |
| Vue Router | 4.5.1 | 路由 |
| Axios | 1.11.0 | HTTP 客户端 |
| Markdown-it | 14.1.0 | Markdown 渲染 |
| Highlight.js | 11.11.1 | 代码高亮 |

### 3.3 运维部署

| 技术 | 说明 |
|------|------|
| Docker | 容器化 |
| Docker Compose | 编排 |
| Nginx | 反向代理、静态资源 |

---

## 四、核心技术实现详解

### 4.1 AI 智能体架构 ⭐⭐⭐⭐⭐

#### 4.1.1 LangChain4j AI Service 设计

LangChain4j 提供了声明式的 AI 服务定义方式，通过注解实现 Prompt 模板与接口绑定：

```java
/**
 * AI 代码生成服务接口
 * 使用 LangChain4j 的 AiService 注解自动代理实现
 */
public interface AiCodeGeneratorService {

    /**
     * 生成 Vue 项目代码（流式输出）
     *
     * @MemoryId 绑定对话记忆，实现多轮对话上下文
     * @SystemMessage 从资源文件加载系统提示词
     * @UserMessage 用户输入
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    TokenStream generateVueProjectCodeStream(
        @MemoryId long appId,        // 对话记忆 ID，用于多租户隔离
        @UserMessage String userMessage
    );

    /**
     * 生成 HTML 代码（同步输出）
     * 返回结构化对象 HtmlCodeResult
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);
}
```

#### 4.1.2 Tool Calling 工具调用机制

AI Agent 通过 Tool Calling 实现与外部系统的交互。本项目设计了 6 个核心工具：

```java
/**
 * 文件写入工具
 * 让 AI 能够在服务器上创建文件
 */
@Tool("将内容写入指定文件，用于创建代码文件")
public String writeFile(
    @P("文件路径，如 /app/src/main.js") String filePath,
    @P("文件内容") String content
) {
    // 实现文件写入逻辑
    Path path = Paths.get(baseDir, filePath);
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
    return "文件写入成功: " + filePath;
}

/**
 * 文件读取工具
 * 让 AI 能够查看已生成的代码
 */
@Tool("读取指定文件的内容")
public String readFile(@P("文件路径") String filePath) {
    return Files.readString(Paths.get(baseDir, filePath));
}

/**
 * 文件修改工具
 * 支持增量修改，减少 Token 消耗
 */
@Tool("修改文件中的指定内容")
public String modifyFile(
    @P("文件路径") String filePath,
    @P("要查找的内容") String oldContent,
    @P("替换为的内容") String newContent
) {
    String content = readFile(filePath);
    return content.replace(oldContent, newContent);
}
```

**Tool Calling 执行流程：**

```
用户: "帮我生成一个登录页面"
          │
          ▼
    ┌─────────────────────────────────────────────┐
    │              LLM 推理                        │
    │  "我需要创建一个 Login.vue 文件"             │
    │  决定调用: writeFile("/src/pages/Login.vue") │
    └─────────────────────────────────────────────┘
          │
          ▼
    ┌─────────────────────────────────────────────┐
    │           Tool 执行结果                      │
    │  writeFile 返回: "文件写入成功"              │
    └─────────────────────────────────────────────┘
          │
          ▼
    ┌─────────────────────────────────────────────┐
    │              LLM 继续推理                    │
    │  "文件已创建，我需要继续添加样式"            │
    │  决定调用: modifyFile(...)                   │
    └─────────────────────────────────────────────┘
          │
          ▼
        循环执行，直到调用 ExitTool 或达到最大步数
```

#### 4.1.3 智能路由设计

根据用户输入自动选择最优生成策略：

```java
/**
 * AI 智能路由服务
 * 分析用户意图，选择最合适的代码生成策略
 */
public interface AiCodeGenTypeRoutingService {

    @SystemMessage("""
        你是一个代码生成策略路由器。
        分析用户的需求描述，选择最合适的生成类型：

        - HTML: 适合简单的单页面、展示类网页
        - MULTI_FILE: 适合中等复杂度的多页面应用
        - VUE_PROJECT: 适合复杂的企业级应用，需要完整项目结构

        只返回枚举值，不要返回其他内容。
        """)
    CodeGenTypeEnum routeCodeGenType(String userPrompt);
}
```

**路由示例：**

| 用户输入 | 路由结果 | 原因 |
|----------|----------|------|
| "帮我做一个个人介绍页面" | HTML | 简单单页 |
| "做一个待办事项应用" | MULTI_FILE | 需要多个组件 |
| "帮我做一个后台管理系统" | VUE_PROJECT | 复杂项目结构 |

### 4.2 流式输出实现 ⭐⭐⭐⭐⭐

#### 4.2.1 服务端实现

采用 Reactor Flux 实现响应式流式输出：

```java
/**
 * 流式代码生成
 * 使用 Reactor Flux 实现实时输出
 */
public Flux<String> generateCodeStream(String userPrompt) {
    return Flux.create(emitter -> {
        // 创建 TokenStream 处理器
        TokenStream tokenStream = aiService.generateVueProjectCodeStream(appId, userPrompt);

        tokenStream.onNext(token -> {
            // 每个 Token 立即发送给前端
            emitter.next(token);
        });

        tokenStream.onComplete(response -> {
            // 生成完成
            emitter.complete();
        });

        tokenStream.onError(error -> {
            // 错误处理
            emitter.error(error);
        });

        tokenStream.start();
    });
}
```

#### 4.2.2 HTTP SSE 接口

```java
/**
 * SSE 流式接口
 * Content-Type: text/event-stream
 */
@GetMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> generateCodeStream(@RequestParam String prompt) {
    return aiCodeGeneratorService.generateCodeStream(prompt)
        .delayElements(Duration.ofMillis(50)); // 控制输出速度，避免前端卡顿
}
```

#### 4.2.3 前端接收

```javascript
// 使用 EventSource 接收 SSE 流
function startStreamGeneration(prompt) {
  const eventSource = new EventSource(`/api/generate/stream?prompt=${prompt}`)

  eventSource.onmessage = (event) => {
    // 实时追加内容
    codeContent.value += event.data
    // 实时渲染预览
    updatePreview()
  }

  eventSource.onerror = () => {
    eventSource.close()
  }
}
```

### 4.3 多级缓存架构 ⭐⭐⭐⭐

#### 4.3.1 缓存架构设计

```
┌────────────────────────────────────────────────────────────────────┐
│                        请求处理流程                                 │
│                                                                     │
│   请求 ──► L1 Caffeine ──► L2 Redis ──► 数据库                     │
│            (本地缓存)      (分布式缓存)                             │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │                    缓存策略                                  │  │
│   │                                                              │  │
│   │   L1 (Caffeine):                                            │  │
│   │   - 容量: 1000 条                                           │  │
│   │   - 过期: 5 分钟                                            │  │
│   │   - 适用: 热点数据、用户信息、系统配置                        │  │
│   │                                                              │  │
│   │   L2 (Redis):                                               │  │
│   │   - 过期: 30 分钟                                           │  │
│   │   - 适用: 应用信息、会话数据、对话记忆                        │  │
│   └─────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
```

#### 4.3.2 缓存配置实现

```java
/**
 * 多级缓存配置
 */
@Configuration
public class CacheConfig {

    /**
     * L1 本地缓存 - Caffeine
     */
    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)                    // 最大缓存条数
            .expireAfterWrite(5, TimeUnit.MINUTES) // 写入 5 分钟后过期
            .recordStats());                       // 记录统计信息
        return cacheManager;
    }

    /**
     * L2 分布式缓存 - Redis
     */
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))      // 30 分钟过期
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
    }
}
```

#### 4.3.3 缓存使用示例

```java
@Service
public class AppServiceImpl implements AppService {

    // L1 缓存 - 本地热点数据
    @Cacheable(value = "app:local", key = "#appId")
    public App getAppById(Long appId) {
        return appMapper.selectOneById(appId);
    }

    // L2 缓存 - 分布式共享数据
    @Cacheable(value = "app:global", key = "#userId + ':' + #appId")
    public App getUserApp(Long userId, Long appId) {
        return appMapper.selectOneByCondition(
            APP.USER_ID.eq(userId).and(APP.ID.eq(appId))
        );
    }

    // 缓存更新
    @CacheEvict(value = {"app:local", "app:global"}, key = "#app.id")
    public void updateApp(App app) {
        appMapper.update(app);
    }
}
```

### 4.4 分布式限流实现 ⭐⭐⭐⭐

#### 4.4.1 限流架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                       分布式限流架构                                  │
│                                                                      │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐                     │
│   │ 限流维度  │    │ 实现方式  │    │ 存储位置  │                     │
│   ├──────────┼────┼──────────┼────┼──────────┤                     │
│   │ API 级别 │ ──► │ 方法签名 │ ──► │ Redis    │                     │
│   │ USER 级别│ ──► │ 用户 ID  │ ──► │ Redis    │                     │
│   │ IP 级别  │ ──► │ 客户端 IP│ ──► │ Redis    │                     │
│   └──────────┘    └──────────┘    └──────────┘                     │
│                                                                      │
│   Redisson RRateLimiter 实现:                                        │
│   - 基于 Token Bucket 算法                                           │
│   - 支持动态调整速率                                                  │
│   - 自动过期清理                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

#### 4.4.2 限流注解定义

```java
/**
 * 分布式限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /**
     * 限流 key 前缀
     */
    String key() default "";

    /**
     * 限流类型
     */
    RateLimitType limitType() default RateLimitType.API;

    /**
     * 时间窗口内允许的请求数
     */
    int rate() default 10;

    /**
     * 时间窗口（秒）
     */
    int rateInterval() default 60;

    /**
     * 限流提示消息
     */
    String message() default "请求过于频繁，请稍后再试";
}
```

#### 4.4.3 限流切面实现

```java
/**
 * 分布式限流切面
 * 使用 Redisson 的 RRateLimiter 实现
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Resource
    private RedissonClient redissonClient;

    @Before("@annotation(rateLimit)")
    public void doBefore(JoinPoint point, RateLimit rateLimit) {
        String key = generateRateLimitKey(point, rateLimit);

        // 获取 Redisson 分布式限流器
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.expire(Duration.ofHours(1));

        // 设置令牌桶参数
        rateLimiter.trySetRate(
            RateType.OVERALL,           // 全局限流
            rateLimit.rate(),           // 令牌数量
            rateLimit.rateInterval(),   // 时间窗口
            RateIntervalUnit.SECONDS
        );

        // 尝试获取令牌
        if (!rateLimiter.tryAcquire(1)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, rateLimit.message());
        }
    }

    /**
     * 生成限流 Key
     */
    private String generateRateLimitKey(JoinPoint point, RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder("rate_limit:");

        switch (rateLimit.limitType()) {
            case API:
                // 接口级别: 类名.方法名
                Method method = ((MethodSignature) point.getSignature()).getMethod();
                keyBuilder.append("api:")
                    .append(method.getDeclaringClass().getSimpleName())
                    .append(".").append(method.getName());
                break;

            case USER:
                // 用户级别: 用户ID
                User loginUser = InnerUserService.getLoginUser();
                keyBuilder.append("user:").append(loginUser.getId());
                break;

            case IP:
                // IP 级别: 客户端IP
                keyBuilder.append("ip:").append(getClientIP());
                break;
        }

        return keyBuilder.toString();
    }
}
```

#### 4.4.4 使用示例

```java
@RestController
@RequestMapping("/app")
public class AppController {

    // 用户级别限流：每个用户每分钟最多 10 次
    @RateLimit(key = "generate", limitType = RateLimitType.USER, rate = 10, rateInterval = 60)
    @PostMapping("/generate")
    public BaseResponse<Flux<String>> generateCode(@RequestBody GenerateRequest request) {
        // 业务逻辑
    }

    // IP 级别限流：每个 IP 每分钟最多 30 次
    @RateLimit(limitType = RateLimitType.IP, rate = 30, rateInterval = 60)
    @GetMapping("/list")
    public BaseResponse<List<App>> listApps() {
        // 业务逻辑
    }
}
```

### 4.5 AI 安全护轨（Guardrail）⭐⭐⭐⭐

#### 4.5.1 安全护轨架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                       AI 安全护轨架构                                │
│                                                                      │
│   用户输入 ──► Input Guardrail ──► LLM ──► Output Guardrail ──► 响应 │
│                   │                           │                     │
│                   ▼                           ▼                     │
│              ┌─────────┐                ┌─────────┐                │
│              │安全检查 │                │内容审核 │                │
│              │敏感词   │                │格式验证 │                │
│              │注入检测 │                │重试机制 │                │
│              └─────────┘                └─────────┘                │
└─────────────────────────────────────────────────────────────────────┘
```

#### 4.5.2 输入护轨实现

```java
/**
 * Prompt 安全审查输入护轨
 * 防止 Prompt 注入攻击
 */
public class PromptSafetyInputGuardrail implements InputGuardrail {

    // 敏感词列表
    private static final List<String> SENSITIVE_WORDS = List.of(
        "忽略之前的指令", "ignore previous instructions",
        "破解", "hack", "绕过", "bypass", "越狱", "jailbreak"
    );

    // 注入攻击正则模式
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?)"),
        Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
        Pattern.compile("(?i)(?:pretend|act)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
        Pattern.compile("(?i)system\\s*:\\s*you\\s+are")
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();

        // 1. 长度检查
        if (input.length() > 1000) {
            return fatal("输入内容过长，不要超过 1000 字");
        }

        // 2. 空内容检查
        if (input.trim().isEmpty()) {
            return fatal("输入内容不能为空");
        }

        // 3. 敏感词检查
        String lowerInput = input.toLowerCase();
        for (String word : SENSITIVE_WORDS) {
            if (lowerInput.contains(word.toLowerCase())) {
                log.warn("检测到敏感词: {}", word);
                return fatal("输入包含不当内容，请修改后重试");
            }
        }

        // 4. 注入攻击检查
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("检测到注入攻击: {}", input);
                return fatal("检测到恶意输入，请求被拒绝");
            }
        }

        return success();
    }
}
```

#### 4.5.3 输出护轨实现

```java
/**
 * 重试输出护轨
 * 当 AI 输出不符合预期时自动重试
 */
public class RetryOutputGuardrail implements OutputGuardrail {

    private static final int MAX_RETRIES = 3;

    @Override
    public OutputGuardrailResult validate(AiMessage aiMessage) {
        String content = aiMessage.text();

        // 检查是否包含代码块
        if (!content.contains("```")) {
            return retryWith("请使用代码块格式输出代码，使用 ``` 包裹");
        }

        // 检查代码块是否为空
        if (content.contains("```\n```")) {
            return retryWith("代码块不能为空，请重新生成");
        }

        return success();
    }
}
```

### 4.6 多租户对话记忆 ⭐⭐⭐⭐

#### 4.6.1 对话记忆架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                     多租户对话记忆架构                                │
│                                                                      │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │                    Redis 存储结构                            │  │
│   │                                                              │  │
│   │   Key: chat_memory:{appId}:{userId}                         │  │
│   │   Value: [                                                   │  │
│   │     {"role": "user", "content": "帮我做一个登录页"},          │  │
│   │     {"role": "assistant", "content": "好的，我来生成..."},   │  │
│   │     {"role": "user", "content": "改一下按钮颜色"},           │  │
│   │     ...                                                      │  │
│   │   ]                                                          │  │
│   └─────────────────────────────────────────────────────────────┘  │
│                                                                      │
│   隔离维度:                                                          │
│   - appId: 应用隔离，不同应用独立记忆                                │
│   - userId: 用户隔离，同一应用不同用户独立记忆                       │
└─────────────────────────────────────────────────────────────────────┘
```

#### 4.6.2 对话记忆配置

```java
/**
 * Redis 对话记忆存储配置
 */
@Configuration
public class RedisChatMemoryStoreConfig {

    @Bean
    public ChatMemoryStore chatMemoryStore(RedisClient redisClient) {
        return new RedisChatMemoryStore(redisClient);
    }

    /**
     * 消息窗口记忆
     * 保留最近 20 条消息
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryStore store) {
        return MessageWindowChatMemory.builder()
            .chatMemoryStore(store)
            .maxMessages(20)  // 滑动窗口大小
            .build();
    }
}
```

#### 4.6.3 使用示例

```java
// 通过 @MemoryId 注解绑定对话记忆
@SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
TokenStream generateVueProjectCodeStream(
    @MemoryId long appId,    // 对话记忆 ID，自动管理上下文
    @UserMessage String userMessage
);

// 调用时传入 appId，系统自动加载历史对话
aiService.generateVueProjectCodeStream(appId, "把按钮改成红色");
// AI 会结合之前的上下文进行修改，而不是重新生成
```

### 4.7 结构化输出 ⭐⭐⭐⭐

#### 4.7.1 结构化输出模型

```java
/**
 * 多文件代码生成结果
 * LangChain4j 自动将 AI 输出映射到此对象
 */
@Description("多文件代码生成结果")
public class MultiFileCodeResult {

    @Description("项目名称")
    private String projectName;

    @Description("文件列表")
    private List<CodeFile> files;

    @Description("项目说明")
    private String description;
}

public class CodeFile {

    @Description("文件路径，如 src/App.vue")
    private String path;

    @Description("文件内容")
    private String content;

    @Description("文件类型，如 vue、js、css")
    private String type;
}
```

#### 4.7.2 自动映射原理

```
用户输入 ──► LLM ──► JSON 输出 ──► 自动反序列化 ──► Java 对象

示例:
用户: "帮我做一个待办事项应用"
  │
  ▼
LLM 输出:
{
  "projectName": "todo-app",
  "files": [
    {"path": "src/App.vue", "content": "<template>...", "type": "vue"},
    {"path": "src/main.js", "content": "import ...", "type": "js"}
  ],
  "description": "一个简单的待办事项应用"
}
  │
  ▼
自动映射为 MultiFileCodeResult 对象
```

---

## 五、性能优化实践

### 5.1 性能优化点汇总

| 优化点 | 优化方案 | 效果 |
|--------|----------|------|
| AI 响应速度 | 流式输出 + SSE | 用户感知延迟降低 80% |
| 数据库查询 | MyBatis-Flex + 索引优化 | QPS 提升 50% |
| 热点数据 | Caffeine + Redis 多级缓存 | 命中率 95%+ |
| 并发控制 | Redisson 分布式限流 | 保护系统稳定性 |
| 构建速度 | Docker + 增量构建 | 构建时间降低 60% |

### 5.2 关键 SQL 优化

```sql
-- 对话历史表索引设计
CREATE TABLE chat_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    message     TEXT NOT NULL,
    messageType VARCHAR(32) NOT NULL,
    appId       BIGINT NOT NULL,
    userId      BIGINT NOT NULL,
    createTime  DATETIME DEFAULT CURRENT_TIMESTAMP,
    -- 游标查询核心索引：支持分页加载历史消息
    INDEX idx_appId_createTime (appId, createTime)
);

-- 优化查询：使用游标分页而非 OFFSET
SELECT * FROM chat_history
WHERE appId = ? AND createTime < ?
ORDER BY createTime DESC
LIMIT 20;
```

### 5.3 JVM 调优参数

```bash
# Docker 容器 JVM 参数
JAVA_TOOL_OPTIONS: "-Xmx4g -Xms2g -Xss4m -XX:+UseG1GC"

# 解释:
# -Xmx4g: 最大堆内存 4GB
# -Xms2g: 初始堆内存 2GB
# -Xss4m: 线程栈大小 4MB
# -XX:+UseG1GC: 使用 G1 垃圾回收器
```

---

## 六、常见问题解答（Q&A）

### Q1: 为什么选择 LangChain4j 而不是直接调用 API？

**回答要点：**

1. **抽象层级**：LangChain4j 提供了 AI Service 抽象，通过注解即可定义 AI 接口，无需手动处理 Prompt 模板
2. **Tool Calling**：内置完整的工具调用机制，自动处理工具选择和参数解析
3. **对话记忆**：开箱即用的多轮对话支持，支持多种存储后端
4. **模型切换**：一套代码支持多种 LLM（OpenAI、通义千问、DeepSeek），切换只需改配置
5. **结构化输出**：自动将 AI 响应映射为 Java 对象，无需手动解析 JSON

### Q2: 流式输出如何保证数据一致性？

**回答要点：**

1. **SSE 协议**：HTTP Server-Sent Events 保证长连接稳定
2. **消息顺序**：Flux 保证消息按发送顺序到达
3. **错误处理**：onError 回调处理异常，前端可重试
4. **连接管理**：自动重连机制，超时自动关闭

### Q3: 多级缓存如何保证一致性？

**回答要点：**

1. **缓存失效策略**：更新时同时清除 L1 和 L2
2. **过期时间**：L1 时间短(5分钟)，L2 时间长(30分钟)
3. **穿透保护**：空值也缓存，防止缓存穿透
4. **雪崩保护**：过期时间加随机值，避免同时失效

### Q4: 如何防止 AI 生成恶意代码？

**回答要点：**

1. **输入护轨**：敏感词过滤、Prompt 注入检测
2. **输出护轨**：代码格式验证、危险函数检测
3. **沙箱执行**：生成代码在隔离环境预览
4. **用户审计**：记录所有生成操作，可追溯

### Q5: 微服务之间如何通信？

**回答要点：**

1. **Dubbo RPC**：使用 Triple 协议，基于 HTTP/2
2. **服务发现**：Nacos 注册中心，自动发现服务实例
3. **负载均衡**：Dubbo 内置多种负载均衡策略
4. **服务降级**：支持 Mock 返回，保证核心链路可用

---

## 七、项目扩展点

### 7.1 可扩展功能

| 扩展方向 | 实现思路 |
|----------|----------|
| 支持 React/Angular | 新增代码生成模板，调整 System Prompt |
| 接入更多 LLM | 实现 LangChain4j ChatModel 接口 |
| 代码沙箱 | Docker 容器隔离执行 |
| 多人协作 | WebSocket + OT 算法 |
| 版本控制 | Git 集成，支持回滚 |
| 模板市场 | 用户分享模板，AI 基于模板生成 |

### 7.2 学习进阶路径

```
入门 ──────────────────────────────────────────────────► 进阶

1. 理解 AI Service 注解使用          1. 自定义 Tool 实现
2. 掌握流式输出原理                  2. 实现 AI 工作流（LangGraph）
3. 了解多级缓存架构                  3. 设计多 Agent 协作系统
4. 理解分布式限流机制                4. AI 应用性能调优
```

---

## 八、快速开始

### 8.1 环境要求

| 环境 | 版本 |
|------|------|
| JDK | 21+ |
| Node.js | 18+ |
| Docker | 24+ |
| MySQL | 8.0+ |
| Redis | 7+ |

### 8.2 本地启动

```bash
# 1. 克隆项目
git clone https://github.com/your-repo/yu-ai-code-mother.git
cd yu-ai-code-mother

# 2. 启动基础设施
docker-compose up -d mysql redis nacos

# 3. 配置环境变量
cp .env.local.example .env.local
# 编辑 .env.local，配置:
# - AI_DASHSCOPE_API_KEY (阿里云通义千问)
# - DEEPSEEK_API_KEY (可选)
# - COS 配置 (可选)

# 4. 启动后端
mvn spring-boot:run

# 5. 启动前端
cd yu-ai-code-mother-frontend
npm install
npm run dev

# 访问 http://localhost:5173
```

### 8.3 Docker 一键部署

```bash
# 配置环境变量
cp .env.example .env

# 一键启动
docker-compose up -d

# 访问 http://localhost
```

---

## 九、总结

### 项目技术亮点总结

| 亮点 | 技术深度 | 实用价值 |
|------|----------|----------|
| AI Agent 架构 | LangChain4j + Tool Calling | ⭐⭐⭐⭐⭐ |
| 流式输出 | Reactor Flux + SSE | ⭐⭐⭐⭐⭐ |
| 多级缓存 | Caffeine + Redis | ⭐⭐⭐⭐ |
| 分布式限流 | Redisson RRateLimiter | ⭐⭐⭐⭐ |
| AI 安全 | Guardrail 机制 | ⭐⭐⭐⭐ |
| 多租户记忆 | Redis ChatMemory | ⭐⭐⭐⭐ |
| 微服务架构 | Dubbo + Nacos | ⭐⭐⭐⭐ |

### 核心竞争力

1. **紧跟 AI 时代**：掌握 LangChain4j 等 AI 应用开发框架
2. **架构设计能力**：从单体到微服务，多级缓存，分布式限流
3. **性能优化经验**：流式输出、缓存策略、SQL 优化
4. **安全意识**：Guardrail 安全护轨，防止 Prompt 注入
5. **工程化能力**：Docker 容器化，完善的监控体系

