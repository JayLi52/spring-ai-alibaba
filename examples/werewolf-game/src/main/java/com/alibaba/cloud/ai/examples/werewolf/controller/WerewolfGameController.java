package com.alibaba.cloud.ai.examples.werewolf.controller;

import com.alibaba.cloud.ai.examples.werewolf.agent.WerewolfGameAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.agent.day.WerewolfDayAgentBuilder;
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
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

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

    private final WerewolfGameAgentBuilder gameAgentBuilder;
    private final WerewolfNightAgentBuilder nightAgentBuilder;
    private final WerewolfDayAgentBuilder dayAgentBuilder;
    private Random random = new Random();
    private ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 启动新游戏
	 */
    @PostMapping("/start")
    public Map<String, Object> startGame() {
        log.info("启动 LLM 驱动的狼人杀游戏...");

        WerewolfGameState gameState = gameStateService.initializeGame();

        try {
            Agent gameLoopAgent = gameAgentBuilder.buildGameLoopAgent(gameState);
            gameLoopAgent.invoke("开始游戏");
        } catch (Exception e) {
            log.error("游戏执行出错", e);
        }

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
		// 检查是否还有存活的狼人
		if (gameState.getAliveWerewolfCount() == 0) {
			log.info("没有存活的狼人，跳过狼人行动");
		} else {
			try {
				// 构建 Agent，游戏状态和历史信息会在方法内部构建
				log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
				log.info("🔧 [DEBUG] 开始构建狼人 Agent");
				log.info("存活狼人数量: {}", gameState.getAliveWerewolfCount());
				log.info("存活玩家: {}", gameState.getAlivePlayers());
				
				Agent werewolfAgent = nightAgentBuilder.buildWerewolfDiscussionAgent(gameState);
				log.info("✅ [DEBUG] 狼人 Agent 构建成功");
				log.info("Agent 名称: {}", werewolfAgent.name());
				log.info("Agent 描述: {}", werewolfAgent.description());
				
				// 获取编译后的图信息（用于调试）
				try {
					var compiledGraph = werewolfAgent.getAndCompileGraph();
					log.info("✅ [DEBUG] Graph 编译成功");
//					log.info("Graph 节点数量: {}", compiledGraph.getGraph().nodes().size());
//					log.info("Graph 边数量: {}", compiledGraph.getGraph().edges().size());
				} catch (Exception graphEx) {
					log.warn("⚠️  [DEBUG] 无法获取 Graph 信息: {}", graphEx.getMessage());
				}
				
				// 执行 Agent：传入简单的触发指令
				// 注意：详细的游戏状态和历史已经嵌入到 Prompt 中
				String input = String.format("现在是第%d回合的夜晚，请决定今晚的击杀目标。", gameState.getCurrentRound());
				log.info("📥 [DEBUG] 输入消息: {}", input);
				
				log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
				log.info("🚀 [DEBUG] ====== 开始调用 Agent.invoke() ======");
				long startTime = System.currentTimeMillis();
				
				Object rawResult = werewolfAgent.invoke(input);
				
				long endTime = System.currentTimeMillis();
				log.info("✅ [DEBUG] ====== Agent.invoke() 调用完成 (耗时: {}ms) ======", endTime - startTime);
				log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
				
                log.info("📊 [DEBUG] 返回结果分析:");
                log.info("  返回对象是否为 null: {}", rawResult == null);
                if (rawResult != null) {
                    log.info("  返回对象类型: {}", rawResult.getClass().getName());
                    log.info("  返回对象 toString(): {}", rawResult);
                    if (rawResult instanceof Optional opt) {
                        log.info("  ✅ 返回是 Optional");
                        log.info("  Optional.isPresent(): {}", opt.isPresent());
                        if (opt.isPresent()) {
                            Object innerValue = opt.get();
                            log.info("  Optional 内部值类型: {}", innerValue.getClass().getName());
                            log.info("  Optional 内部值 toString(): {}", innerValue);
                            if (innerValue instanceof OverAllState state) {
                                log.info("  ✅ 内部值是 OverAllState");
                                log.info("  OverAllState.data() keys ({} 个): {}", state.data().size(), state.data().keySet());
                                state.data().forEach((key, value) -> {
                                    if (value instanceof String && ((String) value).length() > 100) {
                                        log.info("    {} = {}... (截断)", key, ((String) value).substring(0, 100));
                                    } else {
                                        log.info("    {} = {}", key, value);
                                    }
                                });
                                Object killTarget = state.data().get("werewolf_kill_target");
                                if (killTarget != null) {
                                    String target = parseTargetPlayer(killTarget);
                                    if (target != null && !target.isBlank()) {
                                        gameState.setNightKilledPlayer(target);
                                        log.info("🎯 使用 Agent 决策击杀: {}", target);
                                    } else {
                                        log.warn("  ⚠️  解析 'werewolf_kill_target' 失败");
                                    }
                                } else {
                                    log.warn("  ⚠️  未找到 'werewolf_kill_target' 键");
                                }
                            } else {
                                log.info("  ⚠️  内部值不是 OverAllState，而是: {}", innerValue.getClass().getName());
                            }
                        } else {
                            log.warn("  ⚠️  Optional 为空，没有返回值");
                        }
                    } else {
                        log.info("  ⚠️  返回不是 Optional，直接是: {}", rawResult.getClass().getName());
                    }
                }
				
				log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
				
                if (gameState.getNightKilledPlayer() == null || gameState.getNightKilledPlayer().isBlank()) {
                    log.warn("未从 Agent 输出中获取到击杀目标，回退为随机击杀");
                    fallbackToRandomKill(gameState);
                }
				
			} catch (GraphRunnerException e) {
				log.error("❌ [DEBUG] GraphRunnerException - 图执行异常", e);
				log.error("异常类型: {}", e.getClass().getName());
				log.error("异常消息: {}", e.getMessage());
				if (e.getCause() != null) {
					log.error("根本原因: {}", e.getCause().getMessage());
				}
				log.error("异常堆栈:", e);
				fallbackToRandomKill(gameState);
			} catch (GraphStateException e) {
				log.error("❌ [DEBUG] GraphStateException - 图状态异常", e);
				log.error("异常类型: {}", e.getClass().getName());
				log.error("异常消息: {}", e.getMessage());
				log.error("异常堆栈:", e);
				fallbackToRandomKill(gameState);
			} catch (Exception e) {
				log.error("❌ [DEBUG] 未知异常 - 狼人 Agent 执行异常", e);
				log.error("异常类型: {}", e.getClass().getName());
				log.error("异常消息: {}", e.getMessage());
				if (e.getCause() != null) {
					log.error("根本原因: {}", e.getCause().getMessage());
				}
				log.error("异常堆栈:", e);
				fallbackToRandomKill(gameState);
			}
		}

        // 2. 女巫行动
        try {
            Agent witchAgent = nightAgentBuilder.buildWitchAgent(gameState);
            Optional<OverAllState> witchStateOpt = witchAgent.invoke("女巫行动");
            if (witchStateOpt.isPresent()) {
                OverAllState witchState = witchStateOpt.get();
                Object witchResult = witchState.data().get("witch_action_result");
                if (witchResult != null) {
                    Map<String, Object> resultMap;
                    if (witchResult instanceof String) {
                        try {
                            JsonNode node = objectMapper.readTree((String) witchResult);
                            resultMap = objectMapper.convertValue(node, Map.class);
                        } catch (Exception ex) {
                            resultMap = Map.of();
                        }
                    } else if (witchResult instanceof Map) {
                        resultMap = (Map<String, Object>) witchResult;
                    } else {
                        resultMap = Map.of();
                    }
                    Object useAntidote = resultMap.get("useAntidote");
                    Object savedPlayer = resultMap.get("savedPlayer");
                    Object usePoison = resultMap.get("usePoison");
                    Object poisonedPlayer = resultMap.get("poisonedPlayer");
                    if (useAntidote instanceof Boolean && (Boolean) useAntidote && savedPlayer != null) {
                        gameState.setWitchSavedPlayer(savedPlayer.toString());
                        gameState.setWitchHasAntidote(false);
                        log.info("🧪 女巫使用解药救治: {}", savedPlayer);
                    }
                    if (usePoison instanceof Boolean && (Boolean) usePoison && poisonedPlayer != null) {
                        gameState.setWitchPoisonedPlayer(poisonedPlayer.toString());
                        gameState.setWitchHasPoison(false);
                        log.info("☠️ 女巫使用毒药毒杀: {}", poisonedPlayer);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("女巫行动处理异常: {}", ex.getMessage());
        }

        // 3. 预言家行动
        try {
            Agent seerAgent = nightAgentBuilder.buildSeerAgent(gameState);
            Optional<OverAllState> seerStateOpt = seerAgent.invoke("预言家查验");
            if (seerStateOpt.isPresent()) {
                OverAllState seerState = seerStateOpt.get();
                Object seerResult = seerState.data().get("seer_check_result");
                if (seerResult != null) {
                    String checkedPlayer = null;
                    if (seerResult instanceof String) {
                        try {
                            JsonNode node = objectMapper.readTree((String) seerResult);
                            if (node.has("checkedPlayer")) {
                                checkedPlayer = node.get("checkedPlayer").asText();
                            }
                        } catch (Exception ignore) {}
                    } else if (seerResult instanceof Map) {
                        Object val = ((Map<?, ?>) seerResult).get("checkedPlayer");
                        if (val != null) {
                            checkedPlayer = val.toString();
                        }
                    }
                    if (checkedPlayer != null && !checkedPlayer.isBlank()) {
                        gameState.setSeerCheckedPlayer(checkedPlayer);
                        boolean isWolf = gameState.getPlayerByName(checkedPlayer).map(Player::isWerewolf).orElse(false);
                        gameState.setSeerCheckResult(isWolf);
                        gameStateService.recordSeerCheck(gameState, checkedPlayer, isWolf);
                        log.info("🔎 预言家查验: {}，结果: {}", checkedPlayer, isWolf ? "狼人" : "好人");
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("预言家行动处理异常: {}", ex.getMessage());
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

        try {
            Agent discussion = dayAgentBuilder.buildDayDiscussionAgent(gameState);
            Optional<OverAllState> discussStateOpt = discussion.invoke("白天并行讨论");
            if (discussStateOpt.isPresent()) {
                OverAllState discussState = discussStateOpt.get();
                Object speeches = discussState.data().get("all_speeches");
                if (speeches instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof String s) {
                            try {
                                JsonNode node = objectMapper.readTree(s);
                                String playerName = node.has("playerName") ? node.get("playerName").asText() : null;
                                String speech = node.has("speech") ? node.get("speech").asText() : null;
                                if (playerName != null && speech != null) {
                                    gameState.getDaySpeeches().put(playerName, speech);
                                }
                            } catch (Exception ignore) {}
                        } else if (item instanceof Map<?, ?> m) {
                            Object pn = m.get("playerName");
                            Object sp = m.get("speech");
                            if (pn != null && sp != null) {
                                gameState.getDaySpeeches().put(pn.toString(), sp.toString());
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("并行讨论解析异常: {}", ex.getMessage());
        }

        try {
            Agent voting = dayAgentBuilder.buildVotingAgent(gameState);
            Optional<OverAllState> votingStateOpt = voting.invoke("白天投票");
            if (votingStateOpt.isPresent()) {
                OverAllState votingState = votingStateOpt.get();
                Object votingResult = votingState.data().get("voting_result");
                String votedOut = null;
                if (votingResult instanceof String s) {
                    try {
                        JsonNode node = objectMapper.readTree(s);
                        if (node.has("votedOutPlayer")) {
                            votedOut = node.get("votedOutPlayer").asText();
                        }
                    } catch (Exception ignore) {}
                } else if (votingResult instanceof Map<?, ?> m) {
                    Object v = m.get("votedOutPlayer");
                    if (v != null) {
                        votedOut = v.toString();
                    }
                }
                if (votedOut != null && !votedOut.isBlank()) {
                    gameState.setVotedOutPlayer(votedOut);
                    log.info("投票结果: {} 被投票淘汰", votedOut);
                } else {
                    List<String> alivePlayers = gameState.getAlivePlayers();
                    if (!alivePlayers.isEmpty()) {
                        String fallback = alivePlayers.get(random.nextInt(alivePlayers.size()));
                        gameState.setVotedOutPlayer(fallback);
                        log.info("投票结果缺失，随机淘汰: {}", fallback);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("投票阶段解析异常: {}", ex.getMessage());
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
