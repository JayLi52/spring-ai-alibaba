package com.alibaba.cloud.ai.examples.werewolf.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 发言顺序生成服务
 */
@Slf4j
@Service
public class SpeechOrderService {

	private final Random random = new Random();

	/**
	 * 生成发言顺序
	 */
	public SpeechOrderResult generateSpeechOrder(List<String> alivePlayers) {
		if (alivePlayers == null || alivePlayers.isEmpty()) {
			return new SpeechOrderResult(Collections.emptyList(), 0, "forward");
		}

		int playerCount = alivePlayers.size();

		// 随机起始索引
		int startIndex = random.nextInt(playerCount);

		// 随机方向
		String direction = random.nextBoolean() ? "forward" : "backward";

		// 生成发言顺序
		List<String> speechOrder = new ArrayList<>();

		if ("forward".equals(direction)) {
			// 顺序：从 startIndex 开始往后
			for (int i = 0; i < playerCount; i++) {
				int index = (startIndex + i) % playerCount;
				speechOrder.add(alivePlayers.get(index));
			}
		}
		else {
			// 逆序：从 startIndex 开始往前
			for (int i = 0; i < playerCount; i++) {
				int index = (startIndex - i + playerCount) % playerCount;
				speechOrder.add(alivePlayers.get(index));
			}
		}

		log.info("生成发言顺序 - 起始位置: {}, 方向: {}, 顺序: {}", alivePlayers.get(startIndex), direction, speechOrder);

		return new SpeechOrderResult(speechOrder, startIndex, direction);
	}

	/**
	 * 发言顺序结果
	 */
	@Data
	public static class SpeechOrderResult {

		private final List<String> speechOrder;

		private final int startIndex;

		private final String direction;

	}

}
