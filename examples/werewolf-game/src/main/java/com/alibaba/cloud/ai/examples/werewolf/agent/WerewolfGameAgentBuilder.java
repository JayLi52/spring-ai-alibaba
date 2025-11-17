package com.alibaba.cloud.ai.examples.werewolf.agent;

import com.alibaba.cloud.ai.examples.werewolf.agent.day.WerewolfDayAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.agent.night.WerewolfNightAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.config.WerewolfConfig;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.examples.werewolf.service.GameStateService;
import com.alibaba.cloud.ai.examples.werewolf.service.SpeechOrderService;
import com.alibaba.cloud.ai.examples.werewolf.service.VictoryCheckerService;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LoopAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.loop.LoopMode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateBuilder;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
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
	public Agent buildGameLoopAgent(WerewolfGameState gameState) throws GraphStateException {
		// 单回合 Agent: 夜晚 -> 白天
		Agent singleRound = SequentialAgent.builder()
			.name("single_round")
			.subAgents(List.of(nightAgentBuilder.buildNightPhaseAgent(gameState),
					dayAgentBuilder.buildDayPhaseAgent(gameState)))
			.build();

		// 循环控制
		return LoopAgent.builder()
			.name("game_loop")
			.subAgent(singleRound)
			.loopStrategy(LoopMode.condition(messages -> {
				// 检查游戏是否结束
				return gameState.isGameOver(); // true 时终止循环
			}))
			.build();
	}

	/**
	 * 创建初始 OverAllState
	 */
	public OverAllState createInitialState(WerewolfGameState gameState) throws GraphStateException {
		return OverAllStateBuilder.builder()
			.putData("game_state", gameState)
			.putData("alive_players", gameState.getAlivePlayers())
			.putData("current_round", gameState.getCurrentRound())
			.putData("night_killed_player", null)
			.putData("witch_saved_player", null)
			.putData("witch_poisoned_player", null)
			.putData("seer_checked_player", null)
			.putData("seer_check_result", null)
			.putData("day_speeches", gameState.getDaySpeeches())
			.putData("speech_order", gameState.getSpeechOrder())
			.putData("voted_out_player", null)
			.putData("game_over", false)
			.putData("winner", null)
			.build();
	}

}
