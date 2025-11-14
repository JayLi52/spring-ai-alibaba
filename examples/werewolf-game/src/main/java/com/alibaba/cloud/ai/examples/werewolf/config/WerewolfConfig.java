package com.alibaba.cloud.ai.examples.werewolf.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 狼人杀游戏配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "werewolf")
public class WerewolfConfig {

	/**
	 * 最大回合数
	 */
	private int maxRounds = 20;

	/**
	 * 玩家数量（固定9人）
	 */
	private int playerCount = 9;

	/**
	 * 是否启用详细日志
	 */
	private boolean enableLogging = true;

	/**
	 * 玩家名称列表
	 */
	private List<String> playerNames = List.of("Alice", "Bob", "Charlie", "David", "Eve", "Frank", "Grace", "Henry",
			"Ivy");

}
