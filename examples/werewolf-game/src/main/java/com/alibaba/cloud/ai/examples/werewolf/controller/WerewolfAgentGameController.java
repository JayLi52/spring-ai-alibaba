package com.alibaba.cloud.ai.examples.werewolf.controller;

import com.alibaba.cloud.ai.examples.werewolf.agent.WerewolfGameAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.config.WerewolfConfig;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.examples.werewolf.service.GameStateService;
import com.alibaba.cloud.ai.examples.werewolf.service.SpeechOrderService;
import com.alibaba.cloud.ai.examples.werewolf.service.VictoryCheckerService;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 狼人杀 Agent 游戏控制器（完整 LLM 版本）
 */
@Slf4j
@RestController
@RequestMapping("/api/werewolf/agent")
@RequiredArgsConstructor
public class WerewolfAgentGameController {

	private final WerewolfGameAgentBuilder gameAgentBuilder;

	private final GameStateService gameStateService;

	private final VictoryCheckerService victoryChecker;

	private final SpeechOrderService speechOrderService;

	private final WerewolfConfig config;

	/**
	 * 启动新游戏（Agent 版本） 使用 Spring AI Alibaba Agent Framework 的 LLM 驱动游戏
	 */
	@PostMapping("/start")
	public Map<String, Object> startAgentGame() {
		log.info("=== 启动 Agent 驱动的狼人杀游戏 ===");

		// 初始化游戏状态
		WerewolfGameState gameState = gameStateService.initializeGame();
		log.info("游戏初始化完成，玩家角色分配：");
		gameState.getPlayerRoles().forEach((player, role) -> log.info("  {} - {}", player, role.getDisplayName()));

		try {
			// 创建初始 OverAllState
			OverAllState overAllState = gameAgentBuilder.createInitialState(gameState);
			
			// 构建游戏主循环 Agent
			Agent gameLoopAgent = gameAgentBuilder.buildGameLoopAgent(gameState);

			// 执行游戏（这会运行完整的游戏循环）
			log.info("开始游戏主循环...");
			gameLoopAgent.invoke("开始游戏");

			log.info("游戏主循环结束");

			// 返回游戏结果
			Map<String, Object> result = buildGameResult(gameState);
			log.info("=== 游戏结束 ===");
			log.info("获胜阵营：{}", gameState.getWinner());
			log.info("总回合数：{}", gameState.getCurrentRound());

			return result;
		}
		catch (Exception e) {
			log.error("游戏执行出错", e);
			Map<String, Object> error = new HashMap<>();
			error.put("error", true);
			error.put("message", e.getMessage());
			error.put("gameState", gameState);
			return error;
		}
	}

	/**
	 * 构建游戏结果
	 */
	private Map<String, Object> buildGameResult(WerewolfGameState gameState) {
		Map<String, Object> result = new HashMap<>();
		result.put("gameOver", gameState.isGameOver());
		result.put("winner", gameState.getWinner());
		result.put("totalRounds", gameState.getCurrentRound());
		result.put("eliminationHistory", gameState.getEliminationHistory());
		result.put("finalMessage", victoryChecker.getVictoryMessage(gameState));
		result.put("survivingPlayers", gameState.getAlivePlayers());
		result.put("playerRoles", gameState.getPlayerRoles());
		return result;
	}

	/**
	 * 获取游戏状态（用于调试）
	 */
	@GetMapping("/status")
	public Map<String, Object> getStatus() {
		Map<String, Object> status = new HashMap<>();
		status.put("message", "Werewolf Agent Game is running");
		status.put("mode", "LLM-driven with Spring AI Alibaba Agent Framework");
		status.put("maxRounds", config.getMaxRounds());
		status.put("playerCount", config.getPlayerCount());
		return status;
	}

}
