## 当前状态概述
- 已具备完整 LLM Agent 版代码骨架：
  - 控制器（LLM 版）：`examples/werewolf-game/src/main/java/com/alibaba/cloud/ai/examples/werewolf/controller/WerewolfAgentGameController.java:23,40-41`
  - 主流程构建器：`examples/werewolf-game/src/main/java/com/alibaba/cloud/ai/examples/werewolf/agent/WerewolfGameAgentBuilder.java:49-63,69-84`
  - 夜晚阶段构建器：`examples/werewolf-game/src/main/java/com/alibaba/cloud/ai/examples/werewolf/agent/night/WerewolfNightAgentBuilder.java:91-97,100-123,131-135,191-208`
  - 白天阶段构建器：`examples/werewolf-game/src/main/java/com/alibaba/cloud/ai/examples/werewolf/agent/day/WerewolfDayAgentBuilder.java:70-76,85-116,122-131`
  - Prompt 配置：`examples/werewolf-game/src/main/java/com/alibaba/cloud/ai/examples/werewolf/config/RolePromptConfig.java`
  - ChatModel 配置（DashScope）：`examples/werewolf-game/src/main/java/com/alibaba/cloud/ai/examples/werewolf/config/AgentConfig.java:19-22`
  - LLM 配置：`examples/werewolf-game/src/main/resources/application.yml:5-13`（使用 `AI_DASHSCOPE_API_KEY` 与 `AI_DASHSCOPE_CHAT_MODEL`）
- 简化版控制器仍存在降级逻辑：`examples/werewolf-game/src/main/java/com/alibaba/cloud/ai/examples/werewolf/controller/WerewolfGameController.java:228-231,398-410`（随机击杀与随机投票）

## 实施目标
- 按 IMPLEMENTATION_GUIDE 完成“基于 LLM 的完整 Agent 版本”落地：去除所有简化/降级策略，全面以 Agent 输出驱动游戏状态更新与胜负判定。

## 技术实施
### 1. 夜晚阶段（狼人/女巫/预言家）
- 狼人并行讨论+最终决策已具备（并行 `ParallelAgent`、汇总 `ReactAgent` → 串联 `SequentialAgent`）：`WerewolfNightAgentBuilder.java:91-97,100-123,131-135`
- 接入击杀结果：从 Agent 返回的 `OverAllState` 读取 `werewolf_kill_target`，设置 `gameState.setNightKilledPlayer(target)`；替换随机降级逻辑：修改 `WerewolfGameController.java:206-215,228-231` 调用路径，优先使用 `parseTargetPlayer`（`WerewolfGameController.java:373-393`）成功解析的目标。
- 女巫行动：消费 `witch_action_result`（`useAntidote/savedPlayer/usePoison/poisonedPlayer`），更新 `gameState.setWitchSavedPlayer(...)` 与 `setWitchPoisonedPlayer(...)`，并在夜晚处理阶段生效（`processNightDeaths` 已就绪：`WerewolfGameController.java:275-291`）。
- 预言家查验：补齐查验历史 TODO（`WerewolfNightAgentBuilder.java:143-147,303-306`）
  - 在 `WerewolfGameState` 增加查验历史结构（如 `Map<String, Boolean> seerCheckHistory`）与读写方法
  - `GameStateService` 在每夜完成后记录查验结果
  - Agent 输出 `seer_check_result` 映射到 `gameState.setSeerCheckedPlayer(...)` 与 `setSeerCheckResult(...)`

### 2. 白天阶段（讨论+投票）
- 并行发言已具备：`WerewolfDayAgentBuilder.java:41-76`，输出合并键 `all_speeches`
- 将发言写回：把每个 `playerName + "_speech"` 结果写入 `gameState.getDaySpeeches()`，同时维护 `gameState.getHistoricalSpeeches()`（最近回合持久化）
- 投票模拟已具备：`WerewolfDayAgentBuilder.java:81-117`，输出键 `voting_result`
- 接入投票结果：解析 `voting_result.votedOutPlayer`，设置 `gameState.setVotedOutPlayer(...)`，随后按 `processDayElimination` 淘汰：`WerewolfGameController.java:326-331`

### 3. 主循环与状态
- 单回合编排：`WerewolfGameAgentBuilder.java:49-54`
- 循环控制：补充最大回合限制并保持条件终止（游戏 `gameOver` 为真时结束）
  - 在 `WerewolfGameAgentBuilder.java:56-63` 增加 `.maxLoops(config.getMaxRounds())`，并确认 `loopStrategy(LoopMode.condition(...))` 的语义为“条件成立时停止循环”。
- 初始状态键：`WerewolfGameAgentBuilder.java:69-84`；确保夜晚/白天各 Agent 写入的输出键与此保持一致（如 `werewolf_kill_target`, `seer_check_result`, `witch_action_result`, `all_speeches`, `voting_result`）。

### 4. Prompt 与鲁棒性
- RolePromptConfig 已为各角色提供策略与风格：`RolePromptConfig.java:21-115`
- 加强 JSON 输出约束：保持 `outputSchema` 与文字说明一致，必要时在解析前进行 JSON 校验/容错（`parseTargetPlayer` 已支持 Map/JSON 字符串）。

## 接口与运行
- 启动入口：`WerewolfGameApplication.java:20-25`，启动时自动加载 `.env`（`AI_DASHSCOPE_API_KEY` 等）：`WerewolfGameApplication.java:31-57`
- LLM 版接口：`POST /api/werewolf/agent/start`（`WerewolfAgentGameController.java:23,40-41`）
- 状态接口：`GET /api/werewolf/agent/status`（`WerewolfAgentGameController.java:98-105`）
- 运行与配置：
  - `.env` 示例：`AI_DASHSCOPE_API_KEY=sk-xxx`
  - 可选：`AI_DASHSCOPE_CHAT_MODEL=qwen-max`（默认已是 `qwen-max`）
  - 启动命令：`mvn spring-boot:run`，日志参考 `application.yml:31-39`

## 验证与测试
- 集成测试：调用 `/api/werewolf/agent/start` 验证整个 LoopAgent（夜晚→白天）与胜负判定；检查 `gameState` 的淘汰历史、发言记录与最终赢家。
- 单元测试：
  - 夜晚各 Agent 的输出解析与状态更新（狼人击杀、女巫药水、预言家查验）
  - 白天发言合并与投票结果解析
- 观测性：保留 `GraphDebugLifecycleListener`（`WerewolfNightAgentBuilder.java:126-135`）与控制器详细日志（`WerewolfGameController.java:137-171`）以便问题定位。

## 交付清单（代码改动点）
- 替换夜晚击杀降级逻辑，改为消费 `werewolf_kill_target`
- 接入女巫与预言家 Agent 输出到 `gameState`
- 将白天并行发言与投票结果写回状态并驱动淘汰
- `LoopAgent` 增加最大回合限制
- 为查验历史补充 `WerewolfGameState` 字段与 `GameStateService` 写入

如果同意以上计划，我将按上述改动点逐项实现与验证，并提供可运行的完整 LLM Agent 版本。