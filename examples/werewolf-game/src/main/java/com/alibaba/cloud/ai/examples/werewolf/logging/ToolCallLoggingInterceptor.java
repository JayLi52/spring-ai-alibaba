package com.alibaba.cloud.ai.examples.werewolf.logging;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具调用日志拦截器
 * 记录每次工具调用的详细信息
 */
@Slf4j
public class ToolCallLoggingInterceptor extends ToolInterceptor {

    @Override
    public String getName() {
        return "tool_call_logging";
    }

    @Override
    public ToolCallResponse interceptToolCall(
            ToolCallRequest request, ToolCallHandler handler) {
        
        // ===== 工具调用前日志 =====
        log.info("🔧 [TOOL CALL START]");
        log.info("  工具名称: {}", request.getToolName());
        log.info("  调用参数: {}", truncate(request.getArguments(), 200));
        
        long startTime = System.currentTimeMillis();
        
        // ===== 执行实际工具调用 =====
        ToolCallResponse response;
        try {
            response = handler.call(request);
            
            // ===== 工具调用成功日志 =====
            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ [TOOL CALL SUCCESS]");
            log.info("  工具名称: {}", request.getToolName());
            log.info("  耗时: {}ms", duration);
            log.info("  返回结果: {}", truncate(String.valueOf(response.getResult()), 200));
            
        } catch (Exception e) {
            // ===== 工具调用失败日志 =====
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ [TOOL CALL FAILED]");
            log.error("  工具名称: {}", request.getToolName());
            log.error("  耗时: {}ms", duration);
            log.error("  错误信息: {}", e.getMessage());
            throw e;
        }
        
        return response;
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
