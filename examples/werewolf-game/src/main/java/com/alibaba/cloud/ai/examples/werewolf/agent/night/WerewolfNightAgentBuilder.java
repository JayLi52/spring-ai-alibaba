package com.alibaba.cloud.ai.examples.werewolf.agent.night;

import com.alibaba.cloud.ai.examples.werewolf.config.RolePromptConfig;
import com.alibaba.cloud.ai.examples.werewolf.debug.GraphDebugLifecycleListener;
import com.alibaba.cloud.ai.examples.werewolf.model.Player;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent.ListMergeStrategy;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 狼人夜晚阶段 Agent 构建器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WerewolfNightAgentBuilder {

	private final ChatModel chatModel;

	private final RolePromptConfig promptConfig;

	/**
	 * 构建狼人讨论 Agent（多 Agent 协作）
	 * @param gameState 游戏状态
	 */
	public Agent buildWerewolfDiscussionAgent(WerewolfGameState gameState) throws GraphStateException {
		List<Player> aliveWerewolves = gameState.getAliveWerewolves();
		
		// 构建狼人专属的游戏历史信息
		String werewolfGameHistory = buildWerewolfGameHistory(gameState);

		if (aliveWerewolves.isEmpty()) {
			// 没有存活狼人，返回空 Agent
			return buildEmptyAgent("no_werewolves");
		}

		// 如果只有一个狼人，直接决策
		if (aliveWerewolves.size() == 1) {
			Player soleWerewolf = aliveWerewolves.get(0);
			return ReactAgent.builder()
				.name("sole_werewolf_action")
				.model(chatModel)
				.instruction(promptConfig.getWerewolfNightSystemPrompt(soleWerewolf.getName(), Collections.emptyList(),
						gameState.getAlivePlayers(), werewolfGameHistory))  // 使用狼人专属历史
				.outputSchema("""
						{
							"targetPlayer": "击杀目标玩家名称",
							"reason": "选择理由"
						}
						""")
				.outputKey("werewolf_kill_target")
				.build();
		}

		// 多个狼人：并行讨论 + 综合决策
		List<Agent> werewolfAgents = new ArrayList<>();
		for (Player werewolf : aliveWerewolves) {
			List<String> otherWerewolves = aliveWerewolves.stream()
				.map(Player::getName)
				.filter(name -> !name.equals(werewolf.getName()))
				.collect(Collectors.toList());

			ReactAgent agent = ReactAgent.builder()
				.name(werewolf.getName() + "_werewolf_discuss")
				.model(chatModel)
				.instruction(promptConfig.getWerewolfNightSystemPrompt(werewolf.getName(), otherWerewolves,
						gameState.getAlivePlayers(), werewolfGameHistory))  // 使用狼人专属历史
				.outputSchema("""
						{
							"targetPlayer": "推荐击杀的玩家名称",
							"reason": "选择理由和策略分析"
						}
						""")
				.outputKey(werewolf.getName() + "_suggestion")
				.build();
			werewolfAgents.add(agent);
		}

		// 使用 ParallelAgent 让所有狼人并行讨论
		ParallelAgent parallelDiscussion = ParallelAgent.builder()
			.name("werewolf_parallel_discussion")
			.subAgents(werewolfAgents)
			.mergeStrategy(new ListMergeStrategy())
			.mergeOutputKey("werewolf_suggestions")
			.build();

		// 综合所有狼人意见的最终决策 Agent
		ReactAgent finalDecision = ReactAgent.builder()
			.name("werewolf_final_decision")
			.model(chatModel)
			.instruction(String.format("""
					你需要综合所有狼人的建议，做出最终击杀决策。
					
					存活玩家：%s
					
					请分析所有建议，选择一个最优的击杀目标。
					
					输出格式（JSON）：
					{
						"targetPlayer": "最终击杀目标玩家名称",
						"reason": "决策理由"
					}
					""", String.join(", ", gameState.getAlivePlayers())))
			.outputSchema("""
					{
						"targetPlayer": "最终击杀目标",
						"reason": "决策理由"
					}
					""")
			.outputKey("werewolf_kill_target")
			.build();

		// 创建带调试监听器的 CompileConfig
		CompileConfig debugConfig = CompileConfig.builder()
			.withLifecycleListener(new GraphDebugLifecycleListener())
			.build();

		// 使用 SequentialAgent 串联：讨论 -> 决策
		return SequentialAgent.builder()
			.name("werewolf_night_action")
			.subAgents(List.of(parallelDiscussion, finalDecision))
			.compileConfig(debugConfig)
			.build();
	}

	/**
	 * 构建预言家查验 Agent
	 */
	public Agent buildSeerAgent(WerewolfGameState gameState) {
		// 构建查验历史
		Map<String, Boolean> checkHistory = new HashMap<>();
		// TODO: 从 gameState 中获取历史查验记录
		
		// 构建预言家专属历史信息
		String seerGameHistory = buildSeerGameHistory(gameState);

		return ReactAgent.builder()
			.name("seer_check")
			.model(chatModel)
			.instruction(promptConfig.getSeerCheckSystemPrompt(gameState.getAlivePlayers(), checkHistory))
			.outputSchema("""
					{
						"checkedPlayer": "被查验的玩家名称",
						"reason": "选择理由"
					}
					""")
			.outputKey("seer_check_result")
			.build();
	}

	/**
	 * 构建女巫行动 Agent
	 */
	public Agent buildWitchAgent(WerewolfGameState gameState) {
		// 构建女巫专属历史信息
		String witchGameHistory = buildWitchGameHistory(gameState);
		
		return ReactAgent.builder()
			.name("witch_action")
			.model(chatModel)
			.instruction(promptConfig.getWitchActionSystemPrompt(gameState.getNightKilledPlayer(),
					gameState.isWitchHasAntidote(), gameState.isWitchHasPoison(), gameState.getAlivePlayers()))
			.outputSchema("""
					{
						"useAntidote": true,
						"savedPlayer": "被救玩家名称",
						"usePoison": false,
						"poisonedPlayer": "被毒玩家名称",
						"reason": "决策理由"
					}
					""")
			.outputKey("witch_action_result")
			.build();
	}

	/**
	 * 构建完整的夜晚阶段 SequentialAgent
	 */
	public Agent buildNightPhaseAgent(WerewolfGameState gameState) throws GraphStateException {
		List<Agent> nightAgents = new ArrayList<>();

		// 1. 狼人行动
		nightAgents.add(buildWerewolfDiscussionAgent(gameState));

		// 2. 女巫行动（如果存活）
		if (hasAliveWitch(gameState)) {
			nightAgents.add(buildWitchAgent(gameState));
		}

		// 3. 预言家行动（如果存活）
		if (hasAliveSeer(gameState)) {
			nightAgents.add(buildSeerAgent(gameState));
		}

		return SequentialAgent.builder().name("night_phase").subAgents(nightAgents).build();
	}

	/**
	 * 构建狼人专属的游戏历史信息
	 * 只包含狼人应该知道的信息，不包含其他神职的隐私信息
	 */
	private String buildWerewolfGameHistory(WerewolfGameState gameState) {
		StringBuilder history = new StringBuilder();
		
		history.append("=== 游戏历史信息（狼人视角）===\n\n");
		history.append(String.format("当前回合：第 %d 回合\n", gameState.getCurrentRound()));
		history.append(String.format("存活玩家：%s\n\n", String.join(", ", gameState.getAlivePlayers())));
		
		// 1. 淘汰历史（所有玩家都知道）
		if (!gameState.getEliminationHistory().isEmpty()) {
			history.append("### 淘汰历史\n");
			for (WerewolfGameState.EliminationRecord record : gameState.getEliminationHistory()) {
				history.append(String.format("- 第 %d 回合 %s：%s 被淘汰（%s）\n", 
					record.getRound(), record.getPhase(), record.getPlayerName(), record.getReason()));
			}
			history.append("\n");
		}
		
		// 2. 历史发言记录（所有玩家白天的发言都是公开的）
		if (!gameState.getHistoricalSpeeches().isEmpty()) {
			history.append("### 历史发言记录\n");
			gameState.getHistoricalSpeeches().entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(entry -> {
					int round = entry.getKey();
					Map<String, String> speeches = entry.getValue();
					history.append(String.format("\n第 %d 回合白天发言：\n", round));
					speeches.forEach((player, speech) -> {
						history.append(String.format("  【%s】: %s\n", player, speech));
					});
				});
			history.append("\n");
		}
		
		// 3. 狼人队友信息（狼人特权：知道其他狼人身份）
		List<Player> aliveWerewolves = gameState.getAliveWerewolves();
		if (aliveWerewolves.size() > 1) {
			history.append("### 狼人队友\n");
			history.append("存活的狼人队友：");
			history.append(aliveWerewolves.stream()
				.map(Player::getName)
				.collect(Collectors.joining(", ")));
			history.append("\n\n");
		}
		
		// 4. 策略提示
		history.append("### 策略建议\n");
		history.append("- 优先击杀神职玩家（预言家、女巫）\n");
		history.append("- 注意分析白天发言，识别谁可能是神职\n");
		history.append("- 避免被发现身份，注意隐藏\n");
		
		return history.toString();
	}
	
	/**
	 * 构建预言家专属的游戏历史信息
	 */
	private String buildSeerGameHistory(WerewolfGameState gameState) {
		StringBuilder history = new StringBuilder();
		
		history.append("=== 游戏历史信息（预言家视角）===\n\n");
		history.append(String.format("当前回合：第 %d 回合\n", gameState.getCurrentRound()));
		history.append(String.format("存活玩家：%s\n\n", String.join(", ", gameState.getAlivePlayers())));
		
		// 淘汰历史
		if (!gameState.getEliminationHistory().isEmpty()) {
			history.append("### 淘汰历史\n");
			for (WerewolfGameState.EliminationRecord record : gameState.getEliminationHistory()) {
				history.append(String.format("- 第 %d 回合 %s：%s 被淘汰（%s）\n", 
					record.getRound(), record.getPhase(), record.getPlayerName(), record.getReason()));
			}
			history.append("\n");
		}
		
		// 历史发言记录
		if (!gameState.getHistoricalSpeeches().isEmpty()) {
			history.append("### 历史发言记录\n");
			gameState.getHistoricalSpeeches().entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(entry -> {
					int round = entry.getKey();
					Map<String, String> speeches = entry.getValue();
					history.append(String.format("\n第 %d 回合白天发言：\n", round));
					speeches.forEach((player, speech) -> {
						history.append(String.format("  【%s】: %s\n", player, speech));
					});
				});
			history.append("\n");
		}
		
		// TODO: 添加历史查验记录（需要在 WerewolfGameState 中添加字段）
		history.append("### 查验记录\n");
		history.append("（历史查验记录将在后续版本实现）\n\n");
		
		history.append("### 策略建议\n");
		history.append("- 查验可疑玩家，帮助好人阵营识别狼人\n");
		history.append("- 合理选择跳身份的时机\n");
		history.append("- 注意保护自己，避免被狼人击杀\n");
		
		return history.toString();
	}
	
	/**
	 * 构建女巫专属的游戏历史信息
	 */
	private String buildWitchGameHistory(WerewolfGameState gameState) {
		StringBuilder history = new StringBuilder();
		
		history.append("=== 游戏历史信息（女巫视角）===\n\n");
		history.append(String.format("当前回合：第 %d 回合\n", gameState.getCurrentRound()));
		history.append(String.format("存活玩家：%s\n\n", String.join(", ", gameState.getAlivePlayers())));
		
		// 药水状态
		history.append("### 药水状态\n");
		history.append(String.format("- 解药：%s\n", gameState.isWitchHasAntidote() ? "可用" : "已使用"));
		history.append(String.format("- 毒药：%s\n\n", gameState.isWitchHasPoison() ? "可用" : "已使用"));
		
		// 淘汰历史
		if (!gameState.getEliminationHistory().isEmpty()) {
			history.append("### 淘汰历史\n");
			for (WerewolfGameState.EliminationRecord record : gameState.getEliminationHistory()) {
				history.append(String.format("- 第 %d 回合 %s：%s 被淘汰（%s）\n", 
					record.getRound(), record.getPhase(), record.getPlayerName(), record.getReason()));
			}
			history.append("\n");
		}
		
		// 历史发言记录
		if (!gameState.getHistoricalSpeeches().isEmpty()) {
			history.append("### 历史发言记录\n");
			gameState.getHistoricalSpeeches().entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(entry -> {
					int round = entry.getKey();
					Map<String, String> speeches = entry.getValue();
					history.append(String.format("\n第 %d 回合白天发言：\n", round));
					speeches.forEach((player, speech) -> {
						history.append(String.format("  【%s】: %s\n", player, speech));
					});
				});
			history.append("\n");
		}
		
		// 当前夜晚被杀玩家
		if (gameState.getNightKilledPlayer() != null) {
			history.append(String.format("### 当前夜晚\n今晚被狼人击杀的玩家：%s\n\n", gameState.getNightKilledPlayer()));
		}
		
		history.append("### 策略建议\n");
		history.append("- 第一晚可以选择不救人，保留解药\n");
		history.append("- 毒药应谨慎使用，确认目标是狼人\n");
		history.append("- 分析发言和淘汰记录，推断狼人身份\n");
		
		return history.toString();
	}

	/**
	 * 检查是否有存活的女巫
	 */
	private boolean hasAliveWitch(WerewolfGameState gameState) {
		return gameState.getAllPlayers()
			.stream()
			.anyMatch(p -> p.isAlive() && p.getRole() == com.alibaba.cloud.ai.examples.werewolf.model.Role.WITCH);
	}

	/**
	 * 检查是否有存活的预言家
	 */
	private boolean hasAliveSeer(WerewolfGameState gameState) {
		return gameState.getAllPlayers()
			.stream()
			.anyMatch(p -> p.isAlive() && p.getRole() == com.alibaba.cloud.ai.examples.werewolf.model.Role.SEER);
	}

	/**
	 * 构建空 Agent（当某角色不存在时）
	 */
	private Agent buildEmptyAgent(String name) {
		return ReactAgent.builder()
			.name(name)
			.model(chatModel)
			.instruction("No action needed")
			.outputKey(name + "_result")
			.build();
	}

}
