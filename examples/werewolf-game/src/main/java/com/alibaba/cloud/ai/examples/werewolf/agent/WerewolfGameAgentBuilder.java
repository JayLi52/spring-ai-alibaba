package com.alibaba.cloud.ai.examples.werewolf.agent;

import com.alibaba.cloud.ai.examples.werewolf.agent.day.WerewolfDayAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.agent.night.WerewolfNightAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.config.WerewolfConfig;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.examples.werewolf.service.GameStateService;
import com.alibaba.cloud.ai.examples.werewolf.service.SpeechOrderService;
import com.alibaba.cloud.ai.examples.werewolf.service.VictoryCheckerService;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.LoopAgent;
import com.alibaba.cloud.ai.graph.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LoopMode;
import com.alibaba.cloud.ai.graph.core.context.OverAllState;
import com.alibaba.cloud.ai.graph.core.context.OverAllStateBuilder;
import com.alibaba.cloud.ai.graph.core.context.strategy.KeyStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 狼人杀游戏主 Agent 构建器 负责编排整个游戏流程：Loop(Sequential(Night, Day))
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WerewolfGameAgentBuilder {

	private final WerewolfNightAgentBuilder nightAgentBuilder;

	private final WerewolfDayAgentBuilder dayAgentBuilder;

	private final GameStateService gameStateService;

	private final VictoryCheckerService victoryChecker;

	private final SpeechOrderService speechOrderService;

	private final WerewolfConfig config;

	/**
	 * 构建游戏主循环 Agent
	 */
	public Agent buildGameLoopAgent(WerewolfGameState gameState) {
		// 单回合 Agent: 夜晚 -> 白天
		Agent singleRound = SequentialAgent.builder()
			.name("single_round")
			.agents(List.of(nightAgentBuilder.buildNightPhaseAgent(gameState),
					dayAgentBuilder.buildDayPhaseAgent(gameState)))
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

	/**
	 * 创建初始 OverAllState
	 */
	public OverAllState createInitialState(WerewolfGameState gameState) {
		return OverAllStateBuilder.builder()
			.addKey("game_state", gameState, KeyStrategy.REPLACE)
			.addKey("alive_players", gameState.getAlivePlayers(), KeyStrategy.REPLACE)
			.addKey("current_round", gameState.getCurrentRound(), KeyStrategy.REPLACE)
			.addKey("night_killed_player", null, KeyStrategy.REPLACE)
			.addKey("witch_saved_player", null, KeyStrategy.REPLACE)
			.addKey("witch_poisoned_player", null, KeyStrategy.REPLACE)
			.addKey("seer_checked_player", null, KeyStrategy.REPLACE)
			.addKey("seer_check_result", null, KeyStrategy.REPLACE)
			.addKey("day_speeches", gameState.getDaySpeeches(), KeyStrategy.REPLACE)
			.addKey("speech_order", gameState.getSpeechOrder(), KeyStrategy.REPLACE)
			.addKey("voted_out_player", null, KeyStrategy.REPLACE)
			.addKey("game_over", false, KeyStrategy.REPLACE)
			.addKey("winner", null, KeyStrategy.REPLACE)
			.build();
	}

}
