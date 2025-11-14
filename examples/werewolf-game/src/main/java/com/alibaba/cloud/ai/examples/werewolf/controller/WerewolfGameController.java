package com.alibaba.cloud.ai.examples.werewolf.controller;

import com.alibaba.cloud.ai.examples.werewolf.config.WerewolfConfig;
import com.alibaba.cloud.ai.examples.werewolf.model.Player;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.examples.werewolf.service.GameStateService;
import com.alibaba.cloud.ai.examples.werewolf.service.SpeechOrderService;
import com.alibaba.cloud.ai.examples.werewolf.service.VictoryCheckerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 狼人杀游戏控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/werewolf")
@RequiredArgsConstructor
public class WerewolfGameController {

	private final GameStateService gameStateService;

	private final VictoryCheckerService victoryChecker;

	private final SpeechOrderService speechOrderService;

	private final WerewolfConfig config;

	private final Random random = new Random();

	/**
	 * 启动新游戏
	 */
	@PostMapping("/start")
	public Map<String, Object> startGame() {
		log.info("启动新游戏...");

		// 初始化游戏状态
		WerewolfGameState gameState = gameStateService.initializeGame();

		// 运行游戏循环（简化版本）
		runGameLoop(gameState);

		// 返回游戏结果
		Map<String, Object> result = new HashMap<>();
		result.put("gameOver", gameState.isGameOver());
		result.put("winner", gameState.getWinner());
		result.put("totalRounds", gameState.getCurrentRound());
		result.put("eliminationHistory", gameState.getEliminationHistory());
		result.put("finalMessage", victoryChecker.getVictoryMessage(gameState));

		return result;
	}

	/**
	 * 游戏主循环（简化版本，不使用LLM）
	 * 
	 * 注意：完整版本应使用 Spring AI Alibaba Agent Framework 的 LoopAgent、 SequentialAgent 和 ParallelAgent
	 * 进行编排 这里提供的是一个可运行的简化版本，展示游戏流程
	 */
	private void runGameLoop(WerewolfGameState gameState) {
		int maxRounds = config.getMaxRounds();

		for (int round = 1; round <= maxRounds; round++) {
			gameStateService.startNewRound(gameState);

			// 夜晚阶段
			executeNightPhase(gameState);

			// 处理夜晚死亡
			processNightDeaths(gameState);

			// 检查胜利条件
			if (victoryChecker.checkVictoryCondition(gameState)) {
				break;
			}

			// 白天阶段
			executeDayPhase(gameState);

			// 处理白天淘汰
			processDayElimination(gameState);

			// 检查胜利条件
			if (victoryChecker.checkVictoryCondition(gameState)) {
				break;
			}
		}

		if (!gameState.isGameOver()) {
			log.warn("达到最大回合数 {}，游戏强制结束", maxRounds);
			gameState.setGameOver(true);
			// 根据剩余人数判定胜者
			if (gameState.getAliveWerewolfCount() > gameState.getAliveVillagerCount()) {
				gameState.setWinner("werewolf");
			}
			else {
				gameState.setWinner("villager");
			}
		}
	}

	/**
	 * 夜晚阶段（简化版本）
	 */
	private void executeNightPhase(WerewolfGameState gameState) {
		log.info("--- 夜晚阶段 ---");

		// 1. 狼人行动：随机选择一个存活的好人击杀
		List<Player> aliveVillagers = gameState.getAllPlayers()
			.stream()
			.filter(Player::isAlive)
			.filter(Player::isVillager)
			.toList();

		if (!aliveVillagers.isEmpty()) {
			Player target = aliveVillagers.get(random.nextInt(aliveVillagers.size()));
			gameState.setNightKilledPlayer(target.getName());
			log.info("狼人选择击杀: {}", target.getName());
		}

		// 2. 女巫行动：简化处理，不使用药水
		log.info("女巫本回合不使用药水");

		// 3. 预言家行动：随机查验一个玩家
		List<Player> alivePlayers = gameState.getAllPlayers().stream().filter(Player::isAlive).toList();
		if (!alivePlayers.isEmpty()) {
			Player checked = alivePlayers.get(random.nextInt(alivePlayers.size()));
			gameState.setSeerCheckedPlayer(checked.getName());
			gameState.setSeerCheckResult(checked.isWerewolf());
			log.info("预言家查验: {}，结果: {}", checked.getName(), checked.isWerewolf() ? "狼人" : "好人");
		}
	}

	/**
	 * 处理夜晚死亡
	 */
	private void processNightDeaths(WerewolfGameState gameState) {
		String killed = gameState.getNightKilledPlayer();
		String saved = gameState.getWitchSavedPlayer();
		String poisoned = gameState.getWitchPoisonedPlayer();

		// 处理狼人击杀（如果女巫没救）
		if (killed != null && !killed.equals(saved)) {
			gameStateService.eliminatePlayer(gameState, killed, "night", "被狼人击杀");
		}

		// 处理女巫毒杀
		if (poisoned != null) {
			gameStateService.eliminatePlayer(gameState, poisoned, "night", "被女巫毒杀");
		}

		log.info(gameStateService.getNightSummary(gameState));
	}

	/**
	 * 白天阶段（简化版本）
	 */
	private void executeDayPhase(WerewolfGameState gameState) {
		log.info("--- 白天阶段 ---");

		// 生成发言顺序
		SpeechOrderService.SpeechOrderResult orderResult = speechOrderService
			.generateSpeechOrder(gameState.getAlivePlayers());
		gameState.setSpeechOrder(orderResult.getSpeechOrder());
		gameState.setSpeechStartIndex(orderResult.getStartIndex());
		gameState.setSpeechDirection(orderResult.getDirection());

		log.info("发言顺序: {}", gameState.getSpeechOrder());

		// 简化发言阶段：每个玩家简单发言
		for (String playerName : gameState.getSpeechOrder()) {
			String speech = String.format("%s的发言内容（简化版本）", playerName);
			gameState.getDaySpeeches().put(playerName, speech);
		}

		// 投票阶段：随机选择一个存活玩家淘汰
		List<String> alivePlayers = gameState.getAlivePlayers();
		if (!alivePlayers.isEmpty()) {
			String votedOut = alivePlayers.get(random.nextInt(alivePlayers.size()));
			gameState.setVotedOutPlayer(votedOut);
			log.info("投票结果: {} 被投票淘汰", votedOut);
		}
	}

	/**
	 * 处理白天淘汰
	 */
	private void processDayElimination(WerewolfGameState gameState) {
		String votedOut = gameState.getVotedOutPlayer();
		if (votedOut != null) {
			gameStateService.eliminatePlayer(gameState, votedOut, "day", "被投票淘汰");
		}
	}

	/**
	 * 获取游戏状态（用于调试）
	 */
	@GetMapping("/status")
	public Map<String, Object> getStatus() {
		Map<String, Object> status = new HashMap<>();
		status.put("message", "Werewolf Game is running");
		status.put("maxRounds", config.getMaxRounds());
		status.put("playerCount", config.getPlayerCount());
		return status;
	}

}
