package com.alibaba.cloud.ai.examples.werewolf.controller;

import com.alibaba.cloud.ai.examples.werewolf.agent.night.WerewolfNightAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.config.WerewolfConfig;
import com.alibaba.cloud.ai.examples.werewolf.model.Player;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.examples.werewolf.service.GameStateService;
import com.alibaba.cloud.ai.examples.werewolf.service.SpeechOrderService;
import com.alibaba.cloud.ai.examples.werewolf.service.VictoryCheckerService;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateBuilder;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

	private final WerewolfNightAgentBuilder nightAgentBuilder;

	private final Random random = new Random();

	private final ObjectMapper objectMapper = new ObjectMapper();

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

		// 1. 狼人行动：使用 Agent 智能决策击杀目标
		try {
			// 构建游戏历史信息（嵌入到 Agent 的 Prompt 中）
			String gameHistory = buildGameHistory(gameState);
			
			// 构建 Agent，游戏状态已通过 instruction 传入
			Agent werewolfAgent = nightAgentBuilder.buildWerewolfDiscussionAgent(gameState, gameHistory);
			
			// 执行 Agent：传入简单的触发指令
			// 注意：详细的游戏状态和历史已经嵌入到 Prompt 中
			String input = String.format("现在是第%d回合的夜晚，请决定今晚的击杀目标。", gameState.getCurrentRound());
			Optional<OverAllState> resultOpt = werewolfAgent.invoke(input);
			
			// 解析 Agent 返回的击杀目标
			if (resultOpt.isPresent()) {
				OverAllState result = resultOpt.get();
				Object killTargetObj = result.data().get("werewolf_kill_target");
				if (killTargetObj != null) {
					String targetPlayer = parseTargetPlayer(killTargetObj);
					if (targetPlayer != null && !targetPlayer.isEmpty()) {
						gameState.setNightKilledPlayer(targetPlayer);
						log.info("狼人 Agent 决策击杀: {}", targetPlayer);
					} else {
						log.warn("狼人 Agent 未返回有效目标，跳过击杀");
					}
				} else {
					log.warn("狼人 Agent 返回结果为空，跳过击杀");
				}
			} else {
				log.warn("狼人 Agent 执行返回空结果，跳过击杀");
			}
		} catch (GraphStateException e) {
			log.error("狼人 Agent 执行异常，降级为随机决策", e);
			fallbackToRandomKill(gameState);
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
	 * 构建游戏历史信息，用于 Agent 决策
	 */
	private String buildGameHistory(WerewolfGameState gameState) {
		StringBuilder history = new StringBuilder();
		
		// 添加当前回合信息
		history.append(String.format("当前回合：%d\n", gameState.getCurrentRound()));
		
		// 添加淘汰历史
		if (!gameState.getEliminationHistory().isEmpty()) {
			history.append("\n淘汰历史：\n");
			for (WerewolfGameState.EliminationRecord record : gameState.getEliminationHistory()) {
				history.append(String.format("  第%d回合 %s：%s - %s\n", 
					record.getRound(), 
					record.getPhase().equals("night") ? "夜晚" : "白天",
					record.getPlayerName(),
					record.getReason()));
			}
		}
		
		// 添加历史发言记录（最近3回合）
		if (!gameState.getHistoricalSpeeches().isEmpty()) {
			history.append("\n最近白天发言：\n");
			gameState.getHistoricalSpeeches().entrySet().stream()
				.sorted((e1, e2) -> Integer.compare(e2.getKey(), e1.getKey())) // 降序
				.limit(3)
				.forEach(entry -> {
					history.append(String.format("  第%d回合：\n", entry.getKey()));
					entry.getValue().forEach((player, speech) -> 
						history.append(String.format("    %s: %s\n", player, speech)));
				});
		}
		
		return history.toString();
	}
	
	/**
	 * 解析 Agent 返回的击杀目标
	 */
	private String parseTargetPlayer(Object killTargetObj) {
		try {
			if (killTargetObj instanceof String) {
				// 如果是 JSON 字符串，解析它
				JsonNode jsonNode = objectMapper.readTree((String) killTargetObj);
				if (jsonNode.has("targetPlayer")) {
					return jsonNode.get("targetPlayer").asText();
				}
			} else if (killTargetObj instanceof Map) {
				// 如果是 Map，直接取值
				@SuppressWarnings("unchecked")
				Map<String, Object> resultMap = (Map<String, Object>) killTargetObj;
				Object target = resultMap.get("targetPlayer");
				return target != null ? target.toString() : null;
			}
			log.warn("无法解析击杀目标，返回对象类型: {}", killTargetObj.getClass().getName());
		} catch (Exception e) {
			log.error("解析击杀目标失败", e);
		}
		return null;
	}

	/**
	 * 降级策略：随机选择击杀目标
	 */
	private void fallbackToRandomKill(WerewolfGameState gameState) {
		List<Player> aliveVillagers = gameState.getAllPlayers()
			.stream()
			.filter(Player::isAlive)
			.filter(Player::isVillager)
			.toList();

		if (!aliveVillagers.isEmpty()) {
			Player target = aliveVillagers.get(random.nextInt(aliveVillagers.size()));
			gameState.setNightKilledPlayer(target.getName());
			log.info("随机选择击杀: {}", target.getName());
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
