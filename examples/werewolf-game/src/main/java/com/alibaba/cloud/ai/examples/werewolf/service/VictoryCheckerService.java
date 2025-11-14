package com.alibaba.cloud.ai.examples.werewolf.service;

import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 胜利条件判定服务
 */
@Slf4j
@Service
public class VictoryCheckerService {

	/**
	 * 检查游戏是否结束并更新状态
	 * 
	 * @return true 如果游戏结束，false 如果游戏继续
	 */
	public boolean checkVictoryCondition(WerewolfGameState gameState) {
		long aliveWerewolves = gameState.getAliveWerewolfCount();
		long aliveVillagers = gameState.getAliveVillagerCount();

		// 好人阵营获胜：所有狼人被淘汰
		if (aliveWerewolves == 0) {
			gameState.setGameOver(true);
			gameState.setWinner("villager");
			log.info("游戏结束 - 好人阵营获胜！所有狼人已被淘汰");
			return true;
		}

		// 狼人阵营获胜：好人数量 <= 狼人数量
		if (aliveVillagers <= aliveWerewolves) {
			gameState.setGameOver(true);
			gameState.setWinner("werewolf");
			log.info("游戏结束 - 狼人阵营获胜！好人数量({}) <= 狼人数量({})", aliveVillagers, aliveWerewolves);
			return true;
		}

		log.debug("游戏继续 - 存活狼人: {}, 存活好人: {}", aliveWerewolves, aliveVillagers);
		return false;
	}

	/**
	 * 获取游戏结果描述
	 */
	public String getVictoryMessage(WerewolfGameState gameState) {
		if (!gameState.isGameOver()) {
			return "游戏尚未结束";
		}

		String winner = "werewolf".equals(gameState.getWinner()) ? "狼人阵营" : "好人阵营";
		return String.format("游戏结束！%s 获得胜利！总回合数: %d", winner, gameState.getCurrentRound());
	}

}
