package com.alibaba.cloud.ai.examples.werewolf.logging;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;

/**
 * 模型调用日志拦截器
 * 记录每次模型调用的请求和响应详情
 */
@Slf4j
public class ModelCallLoggingInterceptor extends ModelInterceptor {

    @Override
    public String getName() {
        return "model_call_logging";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // ===== 请求前日志 =====
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("📤 [MODEL REQUEST]");
        log.info("  消息数量: {}", request.getMessages() != null ? request.getMessages().size() : 0);
        log.info("  工具数量: {}", request.getTools() != null ? request.getTools().size() : 0);
        
        // 记录最后一条用户消息
        if (request.getMessages() != null) {
            request.getMessages().stream()
                .filter(msg -> msg instanceof UserMessage)
                .reduce((first, second) -> second)
                .ifPresent(msg -> {
                    String content = ((UserMessage) msg).getText();
                    log.info("  用户消息预览: {}", truncate(content, 200));
                });
        }
        
        long startTime = System.currentTimeMillis();
        
        // ===== 执行实际调用 =====
        ModelResponse response;
        try {
            response = handler.call(request);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ [MODEL CALL FAILED] 耗时: {}ms", duration);
            log.error("  错误: {}", e.getMessage());
            throw e;
        }
        
        // ===== 响应后日志 =====
        long duration = System.currentTimeMillis() - startTime;
        log.info("📥 [MODEL RESPONSE]");
        log.info("  耗时: {}ms ({}s)", duration, duration / 1000.0);
        
        // 记录助手回复
        if (response.getMessage() instanceof AssistantMessage assistantMsg) {
            String content = assistantMsg.getText();
            if (content != null && !content.isEmpty()) {
                log.info("  回复预览: {}", truncate(content, 200));
            }
            
            if (assistantMsg.hasToolCalls()) {
                log.info("  工具调用数量: {}", assistantMsg.getToolCalls().size());
                assistantMsg.getToolCalls().forEach(tc -> 
                    log.info("    - 工具: {} | 参数: {}", tc.name(), truncate(tc.arguments(), 100))
                );
            }
        }
        
        // 记录 Token 使用
        if (response.getChatResponse() != null && response.getChatResponse().getMetadata() != null) {
            Usage usage = response.getChatResponse().getMetadata().getUsage();
            if (usage != null) {
                log.info("  Token 使用: Prompt={}, Completion={}, Total={}", 
                    usage.getPromptTokens(), 
                    usage.getCompletionTokens(), 
                    usage.getTotalTokens());
            }
        }
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return response;
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
