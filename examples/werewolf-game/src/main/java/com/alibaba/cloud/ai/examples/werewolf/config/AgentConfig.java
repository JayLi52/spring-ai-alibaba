package com.alibaba.cloud.ai.examples.werewolf.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类
 */
@Configuration
public class AgentConfig {

	/**
	 * 注入 ChatModel Bean
	 * 
	 * Spring AI Alibaba 会自动配置 DashScope ChatModel
	 */
	@Bean
	public ChatModel werewolfChatModel(ChatModel chatModel) {
		return chatModel;
	}

}
