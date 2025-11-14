package com.alibaba.cloud.ai.examples.werewolf.agent.night;

import com.alibaba.cloud.ai.examples.werewolf.config.RolePromptConfig;
import com.alibaba.cloud.ai.examples.werewolf.model.Player;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.strategy.merge.ListMergeStrategy;
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
	 * 构建狼人讨论 Agent（多 Agent 协作） 为了更贴近真实狼人杀场景，狼人夜晚行动采用多 Agent 协作模式
	 */
	public Agent buildWerewolfDiscussionAgent(WerewolfGameState gameState) {
		List<Player> aliveWerewolves = gameState.getAliveWerewolves();

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
						gameState.getAlivePlayers()))
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
						gameState.getAlivePlayers()))
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
			.agents(werewolfAgents)
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

		// 使用 SequentialAgent 串联：讨论 -> 决策
		return SequentialAgent.builder()
			.name("werewolf_night_action")
			.agents(List.of(parallelDiscussion, finalDecision))
			.build();
	}

	/**
	 * 构建预言家查验 Agent
	 */
	public Agent buildSeerAgent(WerewolfGameState gameState) {
		// 构建查验历史
		Map<String, Boolean> checkHistory = new HashMap<>();
		// TODO: 从 gameState 中获取历史查验记录

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
	public Agent buildNightPhaseAgent(WerewolfGameState gameState) {
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

		return SequentialAgent.builder().name("night_phase").agents(nightAgents).build();
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
