package com.alibaba.cloud.ai.examples.werewolf.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类
 * 负责配置 LLM 相关的 Bean
 */
@Configuration
public class AgentConfig {

	/**
	 * 配置 ChatModel Bean
	 * Spring AI OpenAI Starter 会自动创建 ChatModel 实例
	 * 这里直接注入并暴露为 Bean，供 Agent 使用
	 */
	@Bean
	public ChatModel chatModel(ChatModel openAiChatModel) {
		return openAiChatModel;
	}

}
