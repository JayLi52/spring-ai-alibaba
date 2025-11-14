# 狼人杀多 Agent 游戏工作流

基于 Spring AI Alibaba Agent Framework 实现的 9 人狼人杀游戏，通过 LoopAgent、ParallelAgent 和 SequentialAgent 的组合编排，模拟完整的狼人杀游戏流程。

## 项目特点

- **真实沉浸的游戏体验**：多 Agent 协作模拟真实玩家互动
- **固定 9 人配置**：3 狼人 + 1 预言家 + 1 女巫 + 1 猎人 + 3 村民
- **智能 Agent 编排**：使用 Loop/Sequential/Parallel Agent 实现复杂游戏流程
- **灵活发言顺序**：随机起始位置和方向，更贴近真实游戏
- **深度策略推理**：LLM 驱动的角色行为和策略决策

## 技术栈

- Spring Boot 3.5.7
- Spring AI Alibaba Agent Framework 1.1.0.0-M4
- Spring AI OpenAI
- Java 17+

## 环境要求

### 必需环境变量

```bash
export AI_DASHSCOPE_API_KEY=sk-xxxxx
export AI_DASHSCOPE_CHAT_MODEL=qwen-max  # 可选，默认 qwen-max
```

**注意**：本项目使用 DashScope 作为 LLM 后端，也可以替换为 OpenAI 或其他兼容的模型服务。

## 快速开始

### 1. 构建项目

```bash
cd /Users/terry/work/spring-ai-alibaba/examples/werewolf-game
mvn clean install
```

### 2. 运行游戏

```bash
mvn spring-boot:run
```

### 3. 启动游戏

项目提供了两个版本：

#### A. 完整 LLM Agent 版本（推荐）

```bash
# 启动 Agent 驱动的游戏
curl -X POST http://localhost:8080/api/werewolf/agent/start

# 查看状态
curl http://localhost:8080/api/werewolf/agent/status
```

**特点**：
- ✅ 使用 LLM 驱动所有决策
- ✅ 狼人多 Agent 协作讨论
- ✅ 玩家并行发言（ParallelAgent）
- ✅ 深度策略推理
- ✅ 真实沉浸的游戏体验

#### B. 简化版本（随机决策）

```bash
# 启动简化版游戏
curl -X POST http://localhost:8080/api/werewolf/start

# 查看状态
curl http://localhost:8080/api/werewolf/status
```

**特点**：
- ✅ 完整游戏流程
- ✅ 随机决策（不使用 LLM）
- ✅ 快速运行测试

### 4. 查看游戏日志

游戏运行过程中会输出详细的游戏日志，包括：
- 角色分配
- 夜晚阶段各角色行动
- 白天讨论发言（按发言顺序展示）
- 投票淘汰结果
- 最终游戏结果

**日志文件位置**：
```bash
# 查看游戏专用日志（推荐）
tail -f logs/game-only.log

# 查看完整日志
tail -f logs/werewolf-game.log

# 查看错误日志
tail -f logs/error.log
```

## 项目结构

```
werewolf-game/
├── src/main/java/com/alibaba/cloud/ai/examples/werewolf/
│   ├── WerewolfGameApplication.java          # 应用启动类
│   ├── model/                                 # 数据模型
│   │   ├── Role.java                         # 角色枚举
│   │   ├── Player.java                       # 玩家模型
│   │   ├── WerewolfGameState.java           # 游戏状态
│   │   └── GameResult.java                   # 游戏结果
│   ├── config/                                # 配置类
│   │   ├── WerewolfConfig.java               # 游戏配置
│   │   └── RolePromptConfig.java             # 角色 Prompt 配置
│   ├── agent/                                 # Agent 实现
│   │   ├── night/                            # 夜晚阶段 Agent
│   │   │   ├── WerewolfDiscussionAgent.java  # 狼人讨论
│   │   │   ├── SeerAgent.java                # 预言家
│   │   │   └── WitchAgent.java               # 女巫
│   │   ├── day/                              # 白天阶段 Agent
│   │   │   ├── PlayerDiscussAgent.java       # 玩家发言
│   │   │   └── VotingAgent.java              # 投票
│   │   └── GameLoopAgent.java                # 游戏主循环
│   ├── service/                               # 业务服务
│   │   ├── GameStateService.java             # 状态管理
│   │   ├── VictoryCheckerService.java        # 胜利条件判定
│   │   └── SpeechOrderService.java           # 发言顺序生成
│   └── controller/                            # 控制器
│       └── WerewolfGameController.java       # 游戏控制器
└── src/main/resources/
    └── application.yml                        # 应用配置
```

## 游戏流程

1. **初始化**：随机分配角色
2. **夜晚阶段**（Sequential）
   - 狼人讨论并选择击杀目标（Multi-Agent）
   - 女巫决定是否使用药水
   - 预言家查验玩家身份
3. **白天阶段**（Sequential）
   - 生成随机发言顺序
   - 所有存活玩家并行生成发言（Parallel）
   - 按发言顺序展示
   - 投票淘汰
4. **胜利判定**：检查是否满足胜利条件
5. **循环**：重复 2-4 直到游戏结束

## 核心特性

### 1. 多 Agent 狼人讨论

狼人夜晚行动采用多 Agent 协作：
- 每个狼人独立分析并提出建议
- 综合所有狼人意见达成一致
- 模拟真实团队协商过程

### 2. 灵活发言顺序

每回合随机生成发言顺序：
- 随机选择起始位置
- 随机选择方向（forward/backward）
- 确保游戏的随机性和公平性

### 3. 深度策略推理

- 预言家：决定何时跳身份、如何报验人
- 女巫：判断是否藏药、何时用毒
- 狼人：协商击杀策略、配合发言污染
- 村民：通过逻辑推理找出破绽

## 设计文档

详细设计文档请参考：[狼人杀多 Agent 游戏工作流设计](../../.qoder/quests/werewolf-multi-agent-game.md)

## License

Apache License 2.0
