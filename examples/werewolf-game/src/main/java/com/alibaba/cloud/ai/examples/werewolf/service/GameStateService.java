package com.alibaba.cloud.ai.examples.werewolf.service;

import com.alibaba.cloud.ai.examples.werewolf.config.WerewolfConfig;
import com.alibaba.cloud.ai.examples.werewolf.model.Player;
import com.alibaba.cloud.ai.examples.werewolf.model.Role;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏状态管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameStateService {

	private final WerewolfConfig config;

	/**
	 * 初始化游戏状态
	 */
	public WerewolfGameState initializeGame() {
		List<String> playerNames = config.getPlayerNames();

		// 创建角色列表（固定9人配置）
		List<Role> roles = Arrays.asList(Role.WEREWOLF, Role.WEREWOLF, Role.WEREWOLF, // 3个狼人
				Role.SEER, // 1个预言家
				Role.WITCH, // 1个女巫
				Role.HUNTER, // 1个猎人
				Role.VILLAGER, Role.VILLAGER, Role.VILLAGER // 3个村民
		);

		// 打乱角色顺序
		List<Role> shuffledRoles = new ArrayList<>(roles);
		Collections.shuffle(shuffledRoles);

		// 创建玩家列表
		List<Player> players = new ArrayList<>();
		Map<String, Role> playerRoles = new HashMap<>();

		for (int i = 0; i < playerNames.size(); i++) {
			String playerName = playerNames.get(i);
			Role role = shuffledRoles.get(i);

			Player player = Player.builder().name(playerName).role(role).alive(true).build();

			players.add(player);
			playerRoles.put(playerName, role);
		}

		// 打印角色分配
		log.info("=".repeat(60));
		log.info("游戏开始 - 角色分配：");
		players.forEach(p -> log.info("  {} -> {}", p.getName(), p.getRole().getDisplayName()));
		log.info("=".repeat(60));

		// 构建游戏状态
		WerewolfGameState gameState = WerewolfGameState.builder()
			.allPlayers(players)
			.alivePlayers(playerNames.stream().collect(Collectors.toList()))
			.playerRoles(playerRoles)
			.currentRound(0)
			.firstDay(true)
			.gameOver(false)
			.build();

		return gameState;
	}

	/**
	 * 处理玩家淘汰
	 */
	public void eliminatePlayer(WerewolfGameState gameState, String playerName, String phase, String reason) {
		if (playerName == null || playerName.isEmpty()) {
			return;
		}

		// 更新玩家存活状态
		gameState.getPlayerByName(playerName).ifPresent(player -> {
			player.setAlive(false);
			log.info("玩家淘汰 - {}, 角色: {}, 阶段: {}, 原因: {}", player.getName(), player.getRole().getDisplayName(),
					phase, reason);
		});

		// 从存活列表中移除
		gameState.getAlivePlayers().remove(playerName);

		// 添加到淘汰历史
		WerewolfGameState.EliminationRecord record = WerewolfGameState.EliminationRecord.builder()
			.round(gameState.getCurrentRound())
			.phase(phase)
			.playerName(playerName)
			.reason(reason)
			.build();

		gameState.getEliminationHistory().add(record);
	}

	/**
	 * 开始新回合
	 */
	public void startNewRound(WerewolfGameState gameState) {
		int newRound = gameState.getCurrentRound() + 1;
		gameState.setCurrentRound(newRound);
		gameState.setFirstDay(newRound == 1);

		// 清空上一回合的临时数据
		gameState.setNightKilledPlayer(null);
		gameState.setWitchSavedPlayer(null);
		gameState.setWitchPoisonedPlayer(null);
		gameState.setSeerCheckedPlayer(null);
		gameState.setSeerCheckResult(null);
		gameState.setVotedOutPlayer(null);
		gameState.setHunterShotPlayer(null);
		gameState.getDaySpeeches().clear();
		gameState.getSpeechOrder().clear();

		log.info("\n" + "=".repeat(60));
		log.info("第 {} 回合开始", newRound);
		log.info("存活玩家: {}", gameState.getAlivePlayers());
		log.info("=".repeat(60) + "\n");
	}

	/**
	 * 获取夜晚事件摘要
	 */
    public String getNightSummary(WerewolfGameState gameState) {
		StringBuilder summary = new StringBuilder("昨夜情况：\n");

		String killedPlayer = gameState.getNightKilledPlayer();
		String savedPlayer = gameState.getWitchSavedPlayer();
		String poisonedPlayer = gameState.getWitchPoisonedPlayer();

		// 死亡情况
		List<String> deaths = new ArrayList<>();

		if (killedPlayer != null && !killedPlayer.equals(savedPlayer)) {
			deaths.add(killedPlayer);
    }

    /**
     * 记录预言家查验历史
     */
    public void recordSeerCheck(WerewolfGameState gameState, String playerName, boolean isWerewolf) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        gameState.getSeerCheckHistory().put(playerName, isWerewolf);
    }

		if (poisonedPlayer != null) {
			deaths.add(poisonedPlayer);
		}

		if (deaths.isEmpty()) {
			summary.append("- 平安夜，无人死亡\n");
		}
		else {
			summary.append("- 死亡玩家: ").append(String.join(", ", deaths)).append("\n");
		}

		return summary.toString();
	}

}
