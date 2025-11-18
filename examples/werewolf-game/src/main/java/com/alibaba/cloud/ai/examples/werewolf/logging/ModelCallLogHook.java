package com.alibaba.cloud.ai.examples.werewolf.logging;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 模型调用日志 Hook
 * 记录每次 LLM 调用的详细信息
 */
@Slf4j
public class ModelCallLogHook extends ModelHook {

    @Override
    public String getName() {
        return "model_call_log";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(
            OverAllState state, RunnableConfig config) {
        
        log.info("🤖 [MODEL CALL START] Agent: {}", getAgentName());
        
        // 记录消息历史
        Optional<Object> messagesOpt = state.value("messages");
        if (messagesOpt.isPresent() && messagesOpt.get() instanceof List) {
            @SuppressWarnings("unchecked")
            List<Message> messages = (List<Message>) messagesOpt.get();
            log.info("  消息数量: {}", messages.size());
            
            // 记录最后一条用户消息
            messages.stream()
                .filter(msg -> msg instanceof UserMessage)
                .reduce((first, second) -> second)
                .ifPresent(msg -> {
                    String content = ((UserMessage) msg).getText();
                    log.info("  用户消息: {}", truncate(content, 150));
                });
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("model_call_time_" + getAgentName(), System.currentTimeMillis());
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
            OverAllState state, RunnableConfig config) {
        
        log.info("🤖 [MODEL CALL END] Agent: {}", getAgentName());
        
        // 计算模型调用耗时
        String timeKey = "model_call_time_" + getAgentName();
        Optional<Object> callTimeOpt = state.value(timeKey);
        if (callTimeOpt.isPresent()) {
            long callTime = (Long) callTimeOpt.get();
            long duration = System.currentTimeMillis() - callTime;
            log.info("  模型响应耗时: {}ms ({}s)", duration, duration / 1000.0);
        }
        
        // 记录 Token 使用量
        Optional<Object> tokenUsageOpt = state.value("_TOKEN_USAGE_");
        tokenUsageOpt.ifPresent(usage -> 
            log.info("  Token 使用: {}", usage)
        );
        
        return CompletableFuture.completedFuture(Map.of());
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
