package com.alibaba.cloud.ai.examples.werewolf.agent.day;

import com.alibaba.cloud.ai.examples.werewolf.config.RolePromptConfig;
import com.alibaba.cloud.ai.examples.werewolf.logging.AgentExecutionLogHook;
import com.alibaba.cloud.ai.examples.werewolf.logging.ModelCallLogHook;
import com.alibaba.cloud.ai.examples.werewolf.logging.ModelCallLoggingInterceptor;
import com.alibaba.cloud.ai.examples.werewolf.logging.ToolCallLoggingInterceptor;
import com.alibaba.cloud.ai.examples.werewolf.model.Player;
import com.alibaba.cloud.ai.examples.werewolf.model.Role;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.examples.werewolf.service.GameStateService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 白天阶段 Agent 构建器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WerewolfDayAgentBuilder {

	private final ChatModel chatModel;

	private final RolePromptConfig promptConfig;

	private final GameStateService gameStateService;

	/**
	 * 构建白天讨论 ParallelAgent 所有存活玩家并行生成发言内容
	 */
	public Agent buildDayDiscussionAgent(WerewolfGameState gameState) throws GraphStateException {
		List<Agent> playerAgents = new ArrayList<>();

		// 获取夜晚信息总结
		String nightInfo = gameStateService.getNightSummary(gameState);

		// 获取之前所有回合的发言
		Map<String, String> previousSpeeches = getPreviousSpeeches(gameState);

		for (String playerName : gameState.getAlivePlayers()) {
			Player player = gameState.getPlayerByName(playerName).orElseThrow();

			ReactAgent playerAgent = ReactAgent.builder()
				.name(playerName + "_discuss")
				.model(chatModel)
				.instruction(promptConfig.getDayDiscussionSystemPrompt(playerName, player.getRole(), nightInfo,
						previousSpeeches, gameState.getCurrentRound()))
                .outputSchema("""
                        {
                            "playerName": "你的名字",
                            "speech": "你的发言内容（200-500字）",
                            "suspectedPlayers": ["可疑玩家1", "可疑玩家2"]
                        }
                        """)
				.outputKey(playerName + "_speech")
				// .hooks(new AgentExecutionLogHook(), new ModelCallLogHook())
				// .interceptors(new ModelCallLoggingInterceptor(), new ToolCallLoggingInterceptor())
				.build();

			playerAgents.add(playerAgent);
		}

		return ParallelAgent.builder()
			.name("day_discussion")
			.subAgents(playerAgents)
			.mergeStrategy(new ListMergeStrategy())
			.mergeOutputKey("all_speeches")
			.build();
	}

	/**
	 * 构建投票 Agent 基于所有玩家的发言内容模拟投票过程
	 */
	public Agent buildVotingAgent(WerewolfGameState gameState) {
		String speechesText = formatSpeeches(gameState.getDaySpeeches(), gameState.getSpeechOrder());

		return ReactAgent.builder()
			.name("voting")
			.model(chatModel)
			.instruction(String.format("""
					根据所有玩家的发言内容，模拟投票过程，决定谁被投票淘汰。
					考虑发言顺序的影响（后发言者可能更具说服力）。
					每个玩家都必须投票，票数最多的玩家被淘汰。
					
					玩家发言（按发言顺序）：
					%s
					
					存活玩家：%s
					
					请分析每个玩家的发言，模拟投票过程。
					
					输出格式（JSON）：
					{
						"votedOutPlayer": "被投票淘汰的玩家名称",
						"voteDetails": {"玩家1": "投给玩家X", "玩家2": "投给玩家Y"},
						"voteCount": {"玩家X": 3, "玩家Y": 2},
						"reason": "投票理由分析"
					}
					""", speechesText, String.join(", ", gameState.getAlivePlayers())))
			.outputSchema("""
					{
						"votedOutPlayer": "被投票淘汰的玩家",
						"voteDetails": {},
						"voteCount": {},
						"reason": "投票理由分析"
					}
					""")
			.outputKey("voting_result")
			// .hooks(new AgentExecutionLogHook(), new ModelCallLogHook())
			// .interceptors(new ModelCallLoggingInterceptor(), new ToolCallLoggingInterceptor())
			.build();
	}

	/**
	 * 构建完整的白天阶段 SequentialAgent
	 */
	public Agent buildDayPhaseAgent(WerewolfGameState gameState) throws GraphStateException {
		List<Agent> dayAgents = new ArrayList<>();

		// 1. 讨论阶段（并行生成发言）
		dayAgents.add(buildDayDiscussionAgent(gameState));

		// 2. 投票阶段
		dayAgents.add(buildVotingAgent(gameState));

		return SequentialAgent.builder().name("day_phase").subAgents(dayAgents).build();
	}

	/**
	 * 获取之前所有回合的发言
	 */
	private Map<String, String> getPreviousSpeeches(WerewolfGameState gameState) {
		// 获取上一回合的发言
		int previousRound = gameState.getCurrentRound() - 1;
		if (previousRound > 0 && gameState.getHistoricalSpeeches().containsKey(previousRound)) {
			return gameState.getHistoricalSpeeches().get(previousRound);
		}
		return Map.of();
	}

	/**
	 * 格式化发言内容（按发言顺序）
	 */
	private String formatSpeeches(Map<String, String> speeches, List<String> speechOrder) {
		if (speeches == null || speeches.isEmpty()) {
			return "（暂无发言）";
		}

		StringBuilder sb = new StringBuilder();
		List<String> orderedPlayers = speechOrder != null && !speechOrder.isEmpty() ? speechOrder
				: new ArrayList<>(speeches.keySet());

		for (String playerName : orderedPlayers) {
			String speech = speeches.get(playerName);
			if (speech != null) {
				sb.append(String.format("\n【%s】: %s\n", playerName, speech));
			}
		}

		return sb.toString();
	}

}
