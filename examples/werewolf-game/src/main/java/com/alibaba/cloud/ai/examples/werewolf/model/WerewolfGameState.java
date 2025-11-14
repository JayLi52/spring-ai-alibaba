package com.alibaba.cloud.ai.examples.werewolf.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * 狼人杀游戏状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WerewolfGameState {

	/**
	 * 所有玩家列表（包含已淘汰玩家）
	 */
	@Builder.Default
	private List<Player> allPlayers = new ArrayList<>();

	/**
	 * 存活玩家名称列表
	 */
	@Builder.Default
	private List<String> alivePlayers = new ArrayList<>();

	/**
	 * 玩家角色映射表
	 */
	@Builder.Default
	private Map<String, Role> playerRoles = new HashMap<>();

	/**
	 * 当前回合数
	 */
	@Builder.Default
	private int currentRound = 0;

	/**
	 * 是否为第一个白天
	 */
	@Builder.Default
	private boolean firstDay = true;

	/**
	 * 夜晚被狼人杀害的玩家
	 */
	private String nightKilledPlayer;

	/**
	 * 女巫救治的玩家
	 */
	private String witchSavedPlayer;

	/**
	 * 女巫毒杀的玩家
	 */
	private String witchPoisonedPlayer;

	/**
	 * 女巫是否还有解药
	 */
	@Builder.Default
	private boolean witchHasAntidote = true;

	/**
	 * 女巫是否还有毒药
	 */
	@Builder.Default
	private boolean witchHasPoison = true;

	/**
	 * 预言家查验的玩家
	 */
	private String seerCheckedPlayer;

	/**
	 * 查验结果
	 */
	private Boolean seerCheckResult;

	/**
	 * 白天投票淘汰的玩家
	 */
	private String votedOutPlayer;

	/**
	 * 猎人开枪带走的玩家
	 */
	private String hunterShotPlayer;

	/**
	 * 白天玩家发言内容
	 */
	@Builder.Default
	private Map<String, String> daySpeeches = new LinkedHashMap<>();

	/**
	 * 当前回合发言顺序
	 */
	@Builder.Default
	private List<String> speechOrder = new ArrayList<>();

	/**
	 * 发言起始位置索引
	 */
	private Integer speechStartIndex;

	/**
	 * 发言方向（"forward" 或 "backward"）
	 */
	private String speechDirection;

	/**
	 * 游戏是否结束
	 */
	@Builder.Default
	private boolean gameOver = false;

	/**
	 * 获胜阵营
	 */
	private String winner;

	/**
	 * 历史发言记录（回合 -> 玩家 -> 发言）
	 */
	@Builder.Default
	private Map<Integer, Map<String, String>> historicalSpeeches = new HashMap<>();

	/**
	 * 淘汰历史
	 */
	@Builder.Default
	private List<EliminationRecord> eliminationHistory = new ArrayList<>();

	/**
	 * 获取存活的狼人数量
	 */
	public long getAliveWerewolfCount() {
		return allPlayers.stream().filter(Player::isAlive).filter(Player::isWerewolf).count();
	}

	/**
	 * 获取存活的好人数量
	 */
	public long getAliveVillagerCount() {
		return allPlayers.stream().filter(Player::isAlive).filter(Player::isVillager).count();
	}

	/**
	 * 根据名称获取玩家
	 */
	public Optional<Player> getPlayerByName(String name) {
		return allPlayers.stream().filter(p -> p.getName().equals(name)).findFirst();
	}

	/**
	 * 获取存活的狼人玩家
	 */
	public List<Player> getAliveWerewolves() {
		return allPlayers.stream().filter(Player::isAlive).filter(Player::isWerewolf).toList();
	}

	/**
	 * 淘汰记录
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class EliminationRecord {

		private int round;

		private String phase;

		private String playerName;

		private String reason;

	}

}
