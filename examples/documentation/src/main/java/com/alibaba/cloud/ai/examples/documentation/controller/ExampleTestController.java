/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.examples.documentation.framework.advanced.a2a;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 示例测试控制器
 */
@RestController
@RequestMapping("/api/examples")
public class ExampleTestController {

    private static final Logger logger = LoggerFactory.getLogger(ExampleTestController.class);

    /**
     * 测试1: 基础 Agent 调用
     */
    @GetMapping("/test1-basic-agent")
    public Map<String, Object> testBasicAgent(@RequestParam(defaultValue = "你好，请介绍一下自己") String question) {
        logger.info("=== 测试1: 基础 Agent 调用 ===");
        logger.info("输入问题: {}", question);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 创建 DashScope API
            DashScopeApi dashScopeApi = DashScopeApi.builder()
                    .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                    .build();
            logger.info("✓ DashScope API 创建成功");

            // 创建 ChatModel
            ChatModel chatModel = DashScopeChatModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .build();
            logger.info("✓ ChatModel 创建成功");

            // 创建 Agent
            ReactAgent agent = ReactAgent.builder()
                    .name("basic_agent")
                    .model(chatModel)
                    .instruction("你是一个友好的AI助手，请简洁明了地回答问题。")
                    .build();
            logger.info("✓ Agent 构建成功: basic_agent");

            // 调用 Agent
            logger.info("开始调用 Agent...");
            Optional<OverAllState> response = agent.invoke(question);
            logger.info("Agent 调用完成，返回值 isPresent: {}", response.isPresent());
            
            if (response.isPresent()) {
                OverAllState state = response.get();
                logger.info("返回状态包含的 keys: {}", state.data().keySet());
                
                Optional<Object> messages = state.value("messages");
                if (messages.isPresent()) {
                    result.put("status", "success");
                    result.put("response", messages.get().toString());
                    logger.info("✓ 测试成功");
                } else {
                    result.put("status", "warning");
                    result.put("message", "响应中没有 messages 字段");
                    result.put("availableKeys", state.data().keySet());
                }
            } else {
                result.put("status", "error");
                result.put("message", "Agent 返回值为空");
                logger.warn("⚠ Agent 返回值为空");
            }
            
        } catch (Exception e) {
            logger.error("✗ 测试失败", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        
        return result;
    }

    /**
     * 测试2: 带系统提示词的 Agent
     */
    @GetMapping("/test2-system-prompt")
    public Map<String, Object> testSystemPrompt(@RequestParam(defaultValue = "什么是Spring AI?") String question) {
        logger.info("=== 测试2: 带系统提示词的 Agent ===");
        logger.info("输入问题: {}", question);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            DashScopeApi dashScopeApi = DashScopeApi.builder()
                    .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                    .build();

            ChatModel chatModel = DashScopeChatModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .build();

            // 创建带有专业系统提示词的 Agent
            ReactAgent agent = ReactAgent.builder()
                    .name("tech_expert_agent")
                    .model(chatModel)
                    .instruction("""
                        你是一个专业的技术专家。
                        
                        在回答技术问题时，请：
                        1. 提供准确的技术信息
                        2. 使用清晰的术语解释
                        3. 如果适用，给出实际应用场景
                        4. 保持简洁但全面
                        """)
                    .build();
            logger.info("✓ 技术专家 Agent 构建成功");

            Optional<OverAllState> response = agent.invoke(question);
            
            if (response.isPresent()) {
                Optional<Object> messages = response.get().value("messages");
                if (messages.isPresent()) {
                    result.put("status", "success");
                    result.put("response", messages.get().toString());
                    logger.info("✓ 测试成功");
                } else {
                    result.put("status", "warning");
                    result.put("message", "响应中没有 messages 字段");
                }
            } else {
                result.put("status", "error");
                result.put("message", "Agent 返回值为空");
            }
            
        } catch (Exception e) {
            logger.error("✗ 测试失败", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        return result;
    }

    /**
     * 测试3: 多轮对话
     */
    @GetMapping("/test3-multi-turn")
    public Map<String, Object> testMultiTurn() {
        logger.info("=== 测试3: 多轮对话 ===");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            DashScopeApi dashScopeApi = DashScopeApi.builder()
                    .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                    .build();

            ChatModel chatModel = DashScopeChatModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .build();

            ReactAgent agent = ReactAgent.builder()
                    .name("chat_agent")
                    .model(chatModel)
                    .instruction("你是一个友好的对话助手。")
                    .build();

            // 第一轮对话
            logger.info("第一轮: 你好");
            Optional<OverAllState> round1 = agent.invoke("你好");
            String response1 = round1.isPresent() && round1.get().value("messages").isPresent() 
                    ? round1.get().value("messages").get().toString() 
                    : "无响应";

            // 第二轮对话
            logger.info("第二轮: 1+1等于几?");
            Optional<OverAllState> round2 = agent.invoke("1+1等于几?");
            String response2 = round2.isPresent() && round2.get().value("messages").isPresent()
                    ? round2.get().value("messages").get().toString()
                    : "无响应";

            result.put("status", "success");
            result.put("round1", response1);
            result.put("round2", response2);
            logger.info("✓ 多轮对话测试完成");
            
        } catch (Exception e) {
            logger.error("✗ 测试失败", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        return result;
    }

    /**
     * 测试列表
     */
    @GetMapping("/list")
    public Map<String, Object> listTests() {
        Map<String, Object> tests = new HashMap<>();
        tests.put("test1", "/api/examples/test1-basic-agent?question=你好");
        tests.put("test2", "/api/examples/test2-system-prompt?question=什么是Spring AI");
        tests.put("test3", "/api/examples/test3-multi-turn");
        return tests;
    }
}
