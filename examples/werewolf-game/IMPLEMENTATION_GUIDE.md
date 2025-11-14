# 狼人杀 Agent 完整实现指南

本项目当前提供了**可运行的简化版本**，展示了完整的游戏流程。要实现基于 LLM 的完整 Agent 版本，请按以下步骤操作。

## 当前实现状态

### ✅ 已完成
- 项目结构和配置
- 数据模型（Role、Player、WerewolfGameState）
- 角色 Prompt 配置（RolePromptConfig）
- 游戏配置（WerewolfConfig）
- 发言顺序生成服务（SpeechOrderService）
- 胜利条件判定服务（VictoryCheckerService）
- 游戏状态管理服务（GameStateService）
- 简化版游戏控制器（WerewolfGameController）

### 🚧 待完善（使用 Agent Framework）
- 狼人夜晚讨论 Agent（多 Agent 协作）
- 预言家、女巫、猎人 Agent
- 白天讨论 ParallelAgent
- 投票 Agent
- 完整的 Agent 编排（Loop/Sequential/Parallel）

## 完整 Agent 实现步骤

### 第一步：配置 ChatModel Bean

在配置类中注入 OpenAI ChatModel：

```java
@Configuration
public class AgentConfig {
    
    @Bean
    public ChatModel chatModel(OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }
}
```

### 第二步：实现狼人夜晚 Agent

创建 `WerewolfNightAgentBuilder.java`：

```java
@Service
@RequiredArgsConstructor
public class WerewolfNightAgentBuilder {
    
    private final ChatModel chatModel;
    private final RolePromptConfig promptConfig;
    
    /**
     * 构建狼人讨论 Agent（多 Agent 协作）
     */
    public Agent buildWerewolfDiscussionAgent(WerewolfGameState gameState) {
        List<Player> aliveWerewolves = gameState.getAliveWerewolves();
        
        // 为每个狼人创建讨论 Agent
        List<Agent> werewolfAgents = new ArrayList<>();
        for (Player werewolf : aliveWerewolves) {
            ReactAgent agent = ReactAgent.builder()
                .name(werewolf.getName() + "_werewolf_discuss")
                .model(chatModel)
                .instruction(promptConfig.getWerewolfNightSystemPrompt(
                    werewolf.getName(),
                    getOtherWerewolfNames(aliveWerewolves, werewolf),
                    gameState.getAlivePlayers()
                ))
                .outputSchema("""
                    {
                        "targetPlayer": "推荐击杀的玩家名称",
                        "reason": "选择理由"
                    }
                """)
                .outputKey(werewolf.getName() + "_suggestion")
                .build();
            werewolfAgents.add(agent);
        }
        
        // 使用 ParallelAgent 让所有狼人并行讨论
        ParallelAgent parallelDiscussion = ParallelAgent.builder()
            .name("werewolf_parallel_discussion")
            .agents(werewolfAgents)
            .mergeStrategy(new ListMergeStrategy())
            .mergeOutputKey("werewolf_suggestions")
            .build();
        
        // 综合所有狼人意见的最终决策 Agent
        ReactAgent finalDecision = ReactAgent.builder()
            .name("werewolf_final_decision")
            .model(chatModel)
            .instruction("综合所有狼人的建议，做出最终击杀决策")
            .outputSchema("""
                {
                    "targetPlayer": "最终击杀目标",
                    "reason": "决策理由"
                }
            """)
            .outputKey("werewolf_kill_target")
            .build();
        
        // 使用 SequentialAgent 串联：讨论 -> 决策
        return SequentialAgent.builder()
            .name("werewolf_night_action")
            .agents(List.of(parallelDiscussion, finalDecision))
            .build();
    }
}
```

### 第三步：实现预言家 Agent

```java
public Agent buildSeerAgent(WerewolfGameState gameState) {
    return ReactAgent.builder()
        .name("seer_check")
        .model(chatModel)
        .instruction(promptConfig.getSeerCheckSystemPrompt(
            gameState.getAlivePlayers(),
            getSeerCheckHistory(gameState)
        ))
        .outputSchema("""
            {
                "checkedPlayer": "被查验的玩家名称",
                "reason": "选择理由"
            }
        """)
        .outputKey("seer_check_result")
        .build();
}
```

### 第四步：实现女巫 Agent

```java
public Agent buildWitchAgent(WerewolfGameState gameState) {
    return ReactAgent.builder()
        .name("witch_action")
        .model(chatModel)
        .instruction(promptConfig.getWitchActionSystemPrompt(
            gameState.getNightKilledPlayer(),
            gameState.isWitchHasAntidote(),
            gameState.isWitchHasPoison(),
            gameState.getAlivePlayers()
        ))
        .outputSchema("""
            {
                "useAntidote": true/false,
                "savedPlayer": "被救玩家",
                "usePoison": true/false,
                "poisonedPlayer": "被毒玩家",
                "reason": "决策理由"
            }
        """)
        .outputKey("witch_action_result")
        .build();
}
```

### 第五步：实现夜晚阶段 SequentialAgent

```java
public Agent buildNightPhaseAgent(WerewolfGameState gameState) {
    List<Agent> nightAgents = new ArrayList<>();
    
    // 1. 狼人行动
    nightAgents.add(buildWerewolfDiscussionAgent(gameState));
    
    // 2. 女巫行动
    if (hasAliveWitch(gameState)) {
        nightAgents.add(buildWitchAgent(gameState));
    }
    
    // 3. 预言家行动
    if (hasAliveSeer(gameState)) {
        nightAgents.add(buildSeerAgent(gameState));
    }
    
    return SequentialAgent.builder()
        .name("night_phase")
        .agents(nightAgents)
        .build();
}
```

### 第六步：实现白天讨论 ParallelAgent

```java
public Agent buildDayDiscussionAgent(WerewolfGameState gameState) {
    List<Agent> playerAgents = new ArrayList<>();
    
    for (String playerName : gameState.getAlivePlayers()) {
        Player player = gameState.getPlayerByName(playerName).orElseThrow();
        
        ReactAgent playerAgent = ReactAgent.builder()
            .name(playerName + "_discuss")
            .model(chatModel)
            .instruction(promptConfig.getDayDiscussionSystemPrompt(
                playerName,
                player.getRole(),
                gameState.getNightSummary(),
                getPreviousSpeeches(gameState),
                gameState.getCurrentRound()
            ))
            .outputSchema("""
                {
                    "speech": "发言内容",
                    "suspectedPlayers": ["可疑玩家"]
                }
            """)
            .outputKey(playerName + "_speech")
            .build();
        
        playerAgents.add(playerAgent);
    }
    
    return ParallelAgent.builder()
        .name("day_discussion")
        .agents(playerAgents)
        .mergeStrategy(new ListMergeStrategy())
        .mergeOutputKey("all_speeches")
        .build();
}
```

### 第七步：实现投票 Agent

```java
public Agent buildVotingAgent(WerewolfGameState gameState) {
    return ReactAgent.builder()
        .name("voting")
        .model(chatModel)
        .instruction("""
            根据所有玩家的发言内容，模拟投票过程，决定谁被投票淘汰。
            考虑发言顺序的影响（后发言者可能更具说服力）。
            
            玩家发言：
            """ + formatSpeeches(gameState.getDaySpeeches()) + """
            
            存活玩家：""" + String.join(", ", gameState.getAlivePlayers()))
        .outputSchema("""
            {
                "votedOutPlayer": "被投票淘汰的玩家",
                "voteDetails": {"玩家": "投给谁"},
                "voteCount": {"玩家": 票数}
            }
        """)
        .outputKey("voting_result")
        .build();
}
```

### 第八步：实现白天阶段 SequentialAgent

```java
public Agent buildDayPhaseAgent(WerewolfGameState gameState) {
    // 1. 生成发言顺序（可以用代码直接生成，也可以用 Agent）
    // 2. 讨论阶段
    Agent discussion = buildDayDiscussionAgent(gameState);
    // 3. 投票阶段
    Agent voting = buildVotingAgent(gameState);
    
    return SequentialAgent.builder()
        .name("day_phase")
        .agents(List.of(discussion, voting))
        .build();
}
```

### 第九步：实现游戏主循环 LoopAgent

```java
public Agent buildGameLoopAgent(WerewolfGameState gameState) {
    // 单回合 Agent
    Agent singleRound = SequentialAgent.builder()
        .name("single_round")
        .agents(List.of(
            buildNightPhaseAgent(gameState),
            buildDayPhaseAgent(gameState)
        ))
        .build();
    
    // 循环控制
    return LoopAgent.builder()
        .name("game_loop")
        .agent(singleRound)
        .loopMode(LoopMode.CONDITION)
        .maxLoops(config.getMaxRounds())
        .loopCondition(messages -> {
            // 检查游戏是否结束
            return !gameState.isGameOver();
        })
        .build();
}
```

### 第十步：在 Controller 中使用 Agent

```java
@PostMapping("/start")
public Map<String, Object> startGame() {
    WerewolfGameState gameState = gameStateService.initializeGame();
    
    // 构建游戏循环 Agent
    Agent gameLoopAgent = agentBuilder.buildGameLoopAgent(gameState);
    
    // 创建 OverAllState
    OverAllState overAllState = OverAllStateBuilder.builder()
        .addKey("game_state", gameState, KeyStrategy.REPLACE)
        .build();
    
    // 执行游戏
    Output output = gameLoopAgent.call(overAllState);
    
    // 返回结果
    return buildGameResult(gameState);
}
```

## 使用 OverAllState 进行状态管理

OverAllState 是 Agent 间共享状态的核心：

```java
// 注册状态键
OverAllState state = OverAllStateBuilder.builder()
    .addKey("alive_players", gameState.getAlivePlayers(), KeyStrategy.REPLACE)
    .addKey("night_killed", null, KeyStrategy.REPLACE)
    .addKey("speeches", new HashMap<>(), KeyStrategy.REPLACE)
    .build();

// Agent 读取状态
List<String> alivePlayers = state.value("alive_players", List.class).orElse(new ArrayList<>());

// Agent 更新状态
state.update("night_killed", "Alice", KeyStrategy.REPLACE);
```

## 测试建议

1. **单元测试**：测试每个 Agent 的输入输出
2. **集成测试**：测试完整游戏流程
3. **Prompt 调优**：通过实际运行优化各角色的 Prompt

## 性能优化

- 使用 gpt-4o-mini 处理简单任务（如发言顺序生成）
- 使用 gpt-4o 处理复杂推理（如狼人策略讨论）
- 合理设置 ParallelAgent 的 maxConcurrency
- 实现请求队列避免超出 API 限制

## 下一步工作

1. 实现上述所有 Agent 构建器
2. 集成到 Controller 中替换简化版本
3. 调优 Prompt 提升游戏质量
4. 添加详细日志和可视化
5. 实现游戏回放功能

完整实现请参考设计文档：`.qoder/quests/werewolf-multi-agent-game.md`
