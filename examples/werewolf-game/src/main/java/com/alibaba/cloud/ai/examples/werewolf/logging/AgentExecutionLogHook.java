package com.alibaba.cloud.ai.examples.werewolf.logging;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Agent 生命周期日志 Hook
 * 记录 Agent 启动和结束时的关键信息
 */
@Slf4j
public class AgentExecutionLogHook extends AgentHook {

    @Override
    public String getName() {
        return "agent_execution_log";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(
            OverAllState state, RunnableConfig config) {
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🎯 [AGENT START] Agent: {}", getAgentName());
        
        // 记录输入
        state.value("input").ifPresent(input -> 
            log.info("  输入: {}", truncate(input.toString(), 200))
        );
        
        // 记录线程ID
        config.threadId().ifPresent(threadId -> 
            log.info("  线程ID: {}", threadId)
        );
        
        log.info("  开始时间: {}", System.currentTimeMillis());
        
        // 在状态中记录启动时间
        Map<String, Object> result = new HashMap<>();
        result.put("agent_start_time_" + getAgentName(), System.currentTimeMillis());
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(
            OverAllState state, RunnableConfig config) {
        
        log.info("🏁 [AGENT END] Agent: {}", getAgentName());
        
        // 计算执行时间
        String timeKey = "agent_start_time_" + getAgentName();
        Optional<Object> startTimeOpt = state.value(timeKey);
        if (startTimeOpt.isPresent()) {
            long startTime = (Long) startTimeOpt.get();
            long duration = System.currentTimeMillis() - startTime;
            log.info("  执行耗时: {}ms ({}s)", duration, duration / 1000.0);
        }
        
        // 记录最终输出
        state.value("output").ifPresent(output -> 
            log.info("  输出: {}", truncate(output.toString(), 200))
        );
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return CompletableFuture.completedFuture(Map.of());
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
