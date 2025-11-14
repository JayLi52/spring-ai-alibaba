package com.alibaba.cloud.ai.examples.werewolf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 狼人杀多 Agent 游戏应用启动类
 */
@Slf4j
@SpringBootApplication
public class WerewolfGameApplication {

	public static void main(String[] args) {
		// 自动加载 .env 文件中的环境变量
		loadEnvFile();
		
		SpringApplication.run(WerewolfGameApplication.class, args);
	}

	/**
	 * 加载 .env 文件中的环境变量到系统属性
	 */
	private static void loadEnvFile() {
		Path envPath = Paths.get(".env");
		
		if (!Files.exists(envPath)) {
			log.warn(".env 文件不存在，跳过环境变量加载");
			return;
		}
		
		try (Stream<String> lines = Files.lines(envPath)) {
			lines.filter(line -> !line.trim().isEmpty())
				.filter(line -> !line.trim().startsWith("#"))
				.forEach(line -> {
					String[] parts = line.split("=", 2);
					if (parts.length == 2) {
						String key = parts[0].trim();
						String value = parts[1].trim();
						
						// 设置为系统属性，Spring Boot 会自动读取
						System.setProperty(key, value);
						log.debug("加载环境变量: {} = {}", key, 
							key.contains("KEY") ? "***" : value); // API Key 脱敏
					}
				});
			log.info(".env 文件加载成功");
		} catch (IOException e) {
			log.error("加载 .env 文件失败", e);
		}
	}

}
