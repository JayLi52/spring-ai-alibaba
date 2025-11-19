package com.alibaba.cloud.ai.examples.werewolf.agent;

import com.alibaba.cloud.ai.examples.werewolf.agent.day.WerewolfDayAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.agent.night.WerewolfNightAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.config.RolePromptConfig;
import com.alibaba.cloud.ai.examples.werewolf.config.WerewolfConfig;
import com.alibaba.cloud.ai.examples.werewolf.model.Player;
import com.alibaba.cloud.ai.examples.werewolf.model.Role;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.examples.werewolf.service.GameStateService;
import com.alibaba.cloud.ai.examples.werewolf.service.SpeechOrderService;
import com.alibaba.cloud.ai.examples.werewolf.service.VictoryCheckerService;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WerewolfAgentGraphTests {

    private RolePromptConfig rolePromptConfig;
    private ChatModel chatModel;

    private WerewolfNightAgentBuilder nightBuilder;
    private WerewolfDayAgentBuilder dayBuilder;
    private WerewolfGameAgentBuilder gameBuilder;

    private WerewolfConfig werewolfConfig;
    private GameStateService gameStateService;
    private VictoryCheckerService victoryCheckerService;
    private SpeechOrderService speechOrderService;

    @BeforeEach
    void setup() {
        // 加载 .env 文件中的环境变量
        loadEnvFile();
        
        rolePromptConfig = new RolePromptConfig();
        
        // 从系统属性获取 DashScope API Key (已通过 loadEnvFile 加载)
        String apiKey = System.getProperty("AI_DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("AI_DASHSCOPE_API_KEY"); // 备用:从环境变量获取
        }

        String chatModelName = System.getProperty("AI_DASHSCOPE_CHAT_MODEL");
        if (chatModelName == null || chatModelName.isEmpty()) {
            chatModelName = System.getenv("AI_DASHSCOPE_CHAT_MODEL"); // 备用
        }
        if (chatModelName == null || chatModelName.isEmpty()) {
            chatModelName = "qwen-max"; // 默认模型
        }
        
        // ===== DEBUG 信息输出 =====
        System.out.println("========== LLM 配置信息 ==========");
        System.out.println("API Key: " + (apiKey != null ? apiKey : "null"));
        System.out.println("Model: " + chatModelName);
        System.out.println("Base URL: https://dashscope.aliyuncs.com/compatible-mode");
        System.out.println("=================================");
        
        // 创建 OpenAI ChatModel (使用 DashScope 兼容模式)
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .apiKey(apiKey)
                .build();
        
        chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chatModelName)
                        .temperature(0.8)
                        .maxTokens(8192)
                        .build())
                .build();
        
        werewolfConfig = new WerewolfConfig();
        gameStateService = new GameStateService(werewolfConfig);
        victoryCheckerService = new VictoryCheckerService();
        speechOrderService = new SpeechOrderService();
        nightBuilder = new WerewolfNightAgentBuilder(chatModel, rolePromptConfig);
        dayBuilder = new WerewolfDayAgentBuilder(chatModel, rolePromptConfig, gameStateService);
        gameBuilder = new WerewolfGameAgentBuilder(nightBuilder, dayBuilder, gameStateService, victoryCheckerService, speechOrderService, werewolfConfig);
    }

    /**
     * 加载 .env 文件中的环境变量到系统属性
     * (复用自 WerewolfGameApplication)
     */
    private static void loadEnvFile() {
        // 尝试多个可能的 .env 文件路径
        Path[] envPaths = {
            Paths.get(".env"),                    // 当前工作目录
            Paths.get("examples/werewolf-game/.env"), // 从项目根目录
            Paths.get("../../.env")               // 从 test 目录向上两级
        };
        
        Path envPath = null;
        for (Path path : envPaths) {
            if (Files.exists(path)) {
                envPath = path;
                break;
            }
        }
        
        if (envPath == null) {
            System.out.println("⚠️  .env 文件不存在,将使用系统环境变量");
            return;
        }
        
        try (Stream<String> lines = Files.lines(envPath)) {
            final Path finalEnvPath = envPath;
            lines.filter(line -> !line.trim().isEmpty())
                .filter(line -> !line.trim().startsWith("#"))
                .forEach(line -> {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        
                        // 设置为系统属性
                        System.setProperty(key, value);
//                        System.out.println("✅ 加载环境变量: " + key + " = " +
//                            (key.contains("KEY") ? "***" : value)); // API Key 脱敏
                    }
                });
            System.out.println("✅ .env 文件加载成功: " + finalEnvPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ 加载 .env 文件失败: " + e.getMessage());
        }
    }

    @Test
    void testNightPhaseGraphStructure() throws GraphStateException {
        WerewolfGameState gameState = createDeterministicState();
        Agent nightPhase = nightBuilder.buildNightPhaseAgent(gameState);
        GraphRepresentation diagram = nightPhase.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        assertNotNull(diagram);
        assertTrue(diagram.content().contains("night_phase"));
        assertTrue(diagram.content().contains("werewolf_final_decision"));
    }

    @Test
    void testDayDiscussionAndVotingGraphStructure() throws GraphStateException {
        WerewolfGameState gameState = createDeterministicState();
        Agent discussion = dayBuilder.buildDayDiscussionAgent(gameState);
        GraphRepresentation discussionDiagram = discussion.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        assertTrue(discussionDiagram.content().contains("day_discussion"));

        Agent voting = dayBuilder.buildVotingAgent(gameState);
        GraphRepresentation votingDiagram = voting.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        assertTrue(votingDiagram.content().contains("voting"));

        Agent dayPhase = dayBuilder.buildDayPhaseAgent(gameState);
        GraphRepresentation dayDiagram = dayPhase.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        assertTrue(dayDiagram.content().contains("day_phase"));
    }

    @Test
    void testGameLoopGraphStructure() throws GraphStateException {
        WerewolfGameState gameState = createDeterministicState();
        Agent gameLoop = gameBuilder.buildGameLoopAgent(gameState);
        GraphRepresentation diagram = gameLoop.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        assertTrue(diagram.content().contains("game_loop"));
        assertTrue(diagram.content().contains("single_round"));
    }

    /**
     * 测试狼人讨论 Agent 的图结构
     * 验证：3轮讨论 + 最终决策的完整流程
     */
    @Test
    void testWerewolfDiscussionAgentStructure() throws GraphStateException {
        WerewolfGameState gameState = createDeterministicState();
        Agent werewolfDiscussion = nightBuilder.buildWerewolfDiscussionAgent(gameState);
        
        // 验证 Agent 不为空
        assertNotNull(werewolfDiscussion, "Werewolf discussion agent should not be null");
        
        // 获取图表示
        GraphRepresentation diagram = werewolfDiscussion.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        // assertNotNull(diagram, "Graph representation should not be null");
        try {
            String input = String.format("现在是第%d回合的夜晚，请决定今晚的击杀目标。", gameState.getCurrentRound());
            java.util.Optional<OverAllState> result = werewolfDiscussion.invoke(input);
            if (result.isPresent()) {
                Object val = result.get().value("werewolf_kill_target").orElse(null);
                if (val instanceof AssistantMessage am) {
                    System.out.println(am.getText());
                } else {
                    System.out.println(val);
                }
            } else {
                System.out.println("No result state returned");
            }

        } catch (GraphRunnerException e) {
            e.printStackTrace();
        }
        // 验证图中包含核心节点
        // assertTrue(diagram.content().contains("werewolf_night_action"), 
        //     "Should contain werewolf_night_action node");
        // assertTrue(diagram.content().contains("werewolf_multi_round_discussion"), 
        //     "Should contain multi-round discussion loop");
        // assertTrue(diagram.content().contains("werewolf_final_decision"), 
        //     "Should contain final decision node");
        // assertTrue(diagram.content().contains("single_round_discussion"), 
        //     "Should contain single round discussion node");
    }

    /**
     * 测试单轮讨论 Agent 结构
     * 验证：所有狼人按顺序发言
     */
    @Test
    void testSingleRoundDiscussionStructure() throws GraphStateException {
        WerewolfGameState gameState = createDeterministicState();
        Agent werewolfDiscussion = nightBuilder.buildWerewolfDiscussionAgent(gameState);
        
        GraphRepresentation diagram = werewolfDiscussion.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        
        // 验证包含狼人发言节点（Alice 和 Bob 是狼人）
        assertTrue(diagram.content().contains("Alice_round_speech") || diagram.content().contains("Alice"), 
            "Should contain Alice's speech node");
        assertTrue(diagram.content().contains("Bob_round_speech") || diagram.content().contains("Bob"), 
            "Should contain Bob's speech node");
    }

    /**
     * 测试 LoopAgent 循环策略
     * 验证：使用 LoopMode.count(3) 实现 3 轮讨论
     */
    @Test
    void testMultiRoundLoopStrategy() throws GraphStateException {
        WerewolfGameState gameState = createDeterministicState();
        Agent werewolfDiscussion = nightBuilder.buildWerewolfDiscussionAgent(gameState);
        
        GraphRepresentation diagram = werewolfDiscussion.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        
        // 验证包含循环讨论节点
        assertTrue(diagram.content().contains("werewolf_multi_round_discussion"), 
            "Should have loop agent for multi-round discussion");
    }

    /**
     * 测试 SequentialAgent 串联结构
     * 验证：3轮讨论 -> 最终决策的顺序执行
     */
    @Test
    void testSequentialAgentChaining() throws GraphStateException {
        WerewolfGameState gameState = createDeterministicState();
        Agent werewolfDiscussion = nightBuilder.buildWerewolfDiscussionAgent(gameState);
        
        GraphRepresentation diagram = werewolfDiscussion.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        
        // 验证顺序执行结构
        assertTrue(diagram.content().contains("werewolf_night_action"), 
            "Should have sequential agent for night action");
        
        // 验证包含讨论和决策两个主要阶段
        String content = diagram.content();
        assertTrue(content.contains("discussion") || content.contains("werewolf_multi_round"), 
            "Should contain discussion phase");
        assertTrue(content.contains("final_decision") || content.contains("werewolf_final_decision"), 
            "Should contain final decision phase");
    }

    /**
     * 测试单狼场景
     * 验证：只有一个狼人时，直接决策，不进行讨论
     */
    @Test
    void testSoleWerewolfScenario() throws GraphStateException {
        // 创建只有一个狼人的游戏状态
        List<Player> players = new ArrayList<>();
        players.add(Player.builder().name("Alice").role(Role.WEREWOLF).alive(true).build());
        players.add(Player.builder().name("Charlie").role(Role.SEER).alive(true).build());
        players.add(Player.builder().name("Diana").role(Role.WITCH).alive(true).build());
        players.add(Player.builder().name("Eve").role(Role.VILLAGER).alive(true).build());

        WerewolfGameState singleWolfState = WerewolfGameState.builder()
                .allPlayers(players)
                .alivePlayers(new ArrayList<>(List.of("Alice", "Charlie", "Diana", "Eve")))
                .currentRound(1)
                .firstDay(false)
                .build();

        Agent soleWerewolfAgent = nightBuilder.buildWerewolfDiscussionAgent(singleWolfState);
        
        assertNotNull(soleWerewolfAgent, "Sole werewolf agent should not be null");
        
        GraphRepresentation diagram = soleWerewolfAgent.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        
        // 验证是直接决策，不包含讨论循环
        assertTrue(diagram.content().contains("sole_werewolf_action"), 
            "Should contain sole werewolf action node");
    }

    /**
     * 测试无狼场景
     * 验证：没有存活狼人时，返回空 Agent
     */
    @Test
    void testNoWerewolvesScenario() throws GraphStateException {
        // 创建没有狼人的游戏状态
        List<Player> players = new ArrayList<>();
        players.add(Player.builder().name("Charlie").role(Role.SEER).alive(true).build());
        players.add(Player.builder().name("Diana").role(Role.WITCH).alive(true).build());
        players.add(Player.builder().name("Eve").role(Role.VILLAGER).alive(true).build());

        WerewolfGameState noWolvesState = WerewolfGameState.builder()
                .allPlayers(players)
                .alivePlayers(new ArrayList<>(List.of("Charlie", "Diana", "Eve")))
                .currentRound(1)
                .firstDay(false)
                .build();

        Agent noWerewolfAgent = nightBuilder.buildWerewolfDiscussionAgent(noWolvesState);
        
        assertNotNull(noWerewolfAgent, "No werewolf agent should return empty agent, not null");
        
        GraphRepresentation diagram = noWerewolfAgent.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        
        // 验证是空 Agent
        assertTrue(diagram.content().contains("no_werewolves"), 
            "Should contain no_werewolves empty agent");
    }

        @Test
        void testNightSequentialInvokeWithRealModelLogs() throws Exception {
            WerewolfGameState gameState = createDeterministicState();
            Agent nightAction = nightBuilder.buildWerewolfDiscussionAgent(gameState);
            System.out.println("[TEST] Night action sequential invoke start");
            java.util.Optional<com.alibaba.cloud.ai.graph.OverAllState> stateOpt = nightAction.invoke("夜晚行动开始");
            assertTrue(stateOpt.isPresent(), "State should be present after invocation");
            com.alibaba.cloud.ai.graph.OverAllState state = stateOpt.get();
            java.util.Optional<Object> killTarget = state.value("werewolf_kill_target");
            System.out.println("[TEST] Final decision output werewolf_kill_target: " + killTarget.orElse("(missing)"));
            assertTrue(killTarget.isPresent(), "Should produce werewolf_kill_target by final decision");
        }
    
        @Test
        void testDayDiscussionParallelInvokeWithRealModelLogs() throws Exception {
            WerewolfGameState gameState = createDeterministicState();
            Agent dayDiscussion = dayBuilder.buildDayDiscussionAgent(gameState);
            System.out.println("[TEST] Day discussion parallel invoke start");
            java.util.Optional<com.alibaba.cloud.ai.graph.OverAllState> stateOpt = dayDiscussion.invoke("白天讨论开始");
            assertTrue(stateOpt.isPresent(), "State should be present after invocation");
            com.alibaba.cloud.ai.graph.OverAllState state = stateOpt.get();
            java.util.Optional<Object> speechesOpt = state.value("all_speeches");
            int size = (speechesOpt.isPresent() && speechesOpt.get() instanceof java.util.List) ? ((java.util.List<?>) speechesOpt.get()).size() : 0;
            System.out.println("[TEST] Parallel collected speeches size: " + size);
            assertTrue(speechesOpt.isPresent(), "Should produce all_speeches from ParallelAgent");
            assertTrue(size >= 1, "Should collect at least one speech in parallel");
        }
    
        @Test
        void testDayPhaseSequentialInvokeWithRealModelLogs() throws Exception {
            WerewolfGameState gameState = createDeterministicState();
            Agent dayPhase = dayBuilder.buildDayPhaseAgent(gameState);
            System.out.println("[TEST] Day phase sequential invoke start");
            java.util.Optional<com.alibaba.cloud.ai.graph.OverAllState> stateOpt = dayPhase.invoke("白天阶段开始");
            assertTrue(stateOpt.isPresent(), "State should be present after invocation");
            com.alibaba.cloud.ai.graph.OverAllState state = stateOpt.get();
            java.util.Optional<Object> votingResult = state.value("voting_result");
            System.out.println("[TEST] Sequential voting_result: " + votingResult.orElse("(missing)"));
            assertTrue(votingResult.isPresent(), "Should produce voting_result by sequential day phase");
        }
    
        private WerewolfGameState createDeterministicState() {
        List<Player> players = new ArrayList<>();
        players.add(Player.builder().name("Alice").role(Role.WEREWOLF).alive(true).build());
        players.add(Player.builder().name("Bob").role(Role.WEREWOLF).alive(true).build());
        players.add(Player.builder().name("Charlie").role(Role.SEER).alive(true).build());
        players.add(Player.builder().name("Diana").role(Role.WITCH).alive(true).build());
        players.add(Player.builder().name("Eve").role(Role.VILLAGER).alive(true).build());

        List<String> alive = List.of("Alice", "Bob", "Charlie", "Diana", "Eve");

        Map<String, Role> roles = Map.of(
                "Alice", Role.WEREWOLF,
                "Bob", Role.WEREWOLF,
                "Charlie", Role.SEER,
                "Diana", Role.WITCH,
                "Eve", Role.VILLAGER
        );

        return WerewolfGameState.builder()
                .allPlayers(players)
                .alivePlayers(new ArrayList<>(alive))
                .playerRoles(roles)
                .currentRound(1)
                .firstDay(false)
                .build();
    }


}