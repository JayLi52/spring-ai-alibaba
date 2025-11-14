package com.alibaba.cloud.ai.examples.werewolf;

import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.examples.werewolf.service.GameStateService;
import com.alibaba.cloud.ai.examples.werewolf.service.SpeechOrderService;
import com.alibaba.cloud.ai.examples.werewolf.service.VictoryCheckerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WerewolfGameApplicationTests {

	@Autowired
	private GameStateService gameStateService;

	@Autowired
	private VictoryCheckerService victoryChecker;

	@Autowired
	private SpeechOrderService speechOrderService;

	@Test
	void contextLoads() {
		assertNotNull(gameStateService);
		assertNotNull(victoryChecker);
		assertNotNull(speechOrderService);
	}

	@Test
	void testGameInitialization() {
		WerewolfGameState gameState = gameStateService.initializeGame();

		assertNotNull(gameState);
		assertEquals(9, gameState.getAllPlayers().size());
		assertEquals(9, gameState.getAlivePlayers().size());
		assertFalse(gameState.isGameOver());
		assertEquals(0, gameState.getCurrentRound());

		// 验证角色分配
		long werewolfCount = gameState.getAllPlayers().stream().filter(p -> p.getRole().isWerewolf()).count();
		long villagerCount = gameState.getAllPlayers().stream().filter(p -> p.getRole().isVillager()).count();

		assertEquals(3, werewolfCount, "应该有3个狼人");
		assertEquals(6, villagerCount, "应该有6个好人");
	}

	@Test
	void testSpeechOrderGeneration() {
		List<String> players = List.of("Alice", "Bob", "Charlie", "David", "Eve");

		SpeechOrderService.SpeechOrderResult result = speechOrderService.generateSpeechOrder(players);

		assertNotNull(result);
		assertNotNull(result.getSpeechOrder());
		assertEquals(5, result.getSpeechOrder().size());
		assertTrue(result.getStartIndex() >= 0 && result.getStartIndex() < 5);
		assertTrue(result.getDirection().equals("forward") || result.getDirection().equals("backward"));

		// 验证所有玩家都在发言顺序中
		for (String player : players) {
			assertTrue(result.getSpeechOrder().contains(player));
		}
	}

	@Test
	void testVictoryCondition() {
		WerewolfGameState gameState = gameStateService.initializeGame();

		// 游戏刚开始，应该没有获胜方
		assertFalse(victoryChecker.checkVictoryCondition(gameState));

		// 模拟所有狼人被淘汰
		gameState.getAllPlayers()
			.stream()
			.filter(p -> p.getRole().isWerewolf())
			.forEach(p -> gameStateService.eliminatePlayer(gameState, p.getName(), "test", "test"));

		// 好人应该获胜
		assertTrue(victoryChecker.checkVictoryCondition(gameState));
		assertEquals("villager", gameState.getWinner());
	}

	@Test
	void testPlayerElimination() {
		WerewolfGameState gameState = gameStateService.initializeGame();

		int initialCount = gameState.getAlivePlayers().size();
		String playerToEliminate = gameState.getAlivePlayers().get(0);

		gameStateService.eliminatePlayer(gameState, playerToEliminate, "test", "test reason");

		assertEquals(initialCount - 1, gameState.getAlivePlayers().size());
		assertFalse(gameState.getAlivePlayers().contains(playerToEliminate));
		assertEquals(1, gameState.getEliminationHistory().size());

		// 验证玩家状态已更新
		gameState.getPlayerByName(playerToEliminate).ifPresent(p -> assertFalse(p.isAlive()));
	}

	@Test
	void testRoundProgression() {
		WerewolfGameState gameState = gameStateService.initializeGame();

		assertEquals(0, gameState.getCurrentRound());

		gameStateService.startNewRound(gameState);
		assertEquals(1, gameState.getCurrentRound());
		assertTrue(gameState.isFirstDay());

		gameStateService.startNewRound(gameState);
		assertEquals(2, gameState.getCurrentRound());
		assertFalse(gameState.isFirstDay());
	}

}
