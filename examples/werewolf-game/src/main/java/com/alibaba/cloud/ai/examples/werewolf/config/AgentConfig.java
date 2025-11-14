package com.alibaba.cloud.ai.examples.werewolf.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Agent 配置类
 * 负责配置 LLM 相关的 Bean
 */
@Slf4j
@Configuration
public class AgentConfig {

	/**
	 * 配置 ChatModel Bean
	 * Spring AI Alibaba DashScope Starter 会自动创建 ChatModel 实例
	 * 这里直接注入并暴露为 Bean，供 Agent 使用
	 */
	@Bean
	public ChatModel chatModel(ChatModel dashscopeChatModel) {
		return dashscopeChatModel;
	}

	/**
	 * 启动时检查 API Key 配置
	 */
	@PostConstruct
	public void checkApiKeyConfiguration() {
		String apiKey = System.getenv("AI_DASHSCOPE_API_KEY");
		if (apiKey == null || apiKey.trim().isEmpty()) {
			apiKey = System.getProperty("AI_DASHSCOPE_API_KEY");
		}
		
		if (apiKey == null || apiKey.trim().isEmpty()) {
			log.error("==========================================");
			log.error("⚠️  AI_DASHSCOPE_API_KEY 未配置！");
			log.error("请设置环境变量或系统属性：");
			log.error("  export AI_DASHSCOPE_API_KEY=sk-xxxxx");
			log.error("或者在 .env 文件中配置：");
			log.error("  AI_DASHSCOPE_API_KEY=sk-xxxxx");
			log.error("==========================================");
		} else {
			log.info("✅ API Key 已配置（长度: {}）", apiKey.length());
		}
		
		String model = System.getenv("AI_DASHSCOPE_CHAT_MODEL");
		if (model == null || model.trim().isEmpty()) {
			model = System.getProperty("AI_DASHSCOPE_CHAT_MODEL");
		}
		if (model == null || model.trim().isEmpty()) {
			log.info("使用默认模型: qwen-max");
		} else {
			log.info("使用模型: {}", model);
		}
	}

}
