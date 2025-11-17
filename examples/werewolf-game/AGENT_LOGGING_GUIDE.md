# Agent Framework 非侵入式日志记录指南

本指南介绍如何在不修改业务代码的情况下,为 Agent 添加全面的日志记录能力。

---

## 📋 目录

1. [概述](#概述)
2. [方案对比](#方案对比)
3. [GraphLifecycleListener - 图执行日志](#方案一graphlifecyclelistener---图执行日志)
4. [Hook - Agent 生命周期日志](#方案二hook---agent-生命周期日志)
5. [Interceptor - 模型和工具调用日志](#方案三interceptor---模型和工具调用日志)
6. [ObservationRegistry - 指标监控](#方案四observationregistry---指标监控)
7. [enableLogging - 内置日志](#方案五enablelogging---内置日志)
8. [最佳实践组合](#最佳实践组合)

---

## 概述

Agent Framework 在"先编译后构建"的执行模式下,提供了多种非侵入式的日志记录机制:

```
Graph Runtime 层面
├── GraphLifecycleListener  → 监听所有节点的执行生命周期
└── ObservationRegistry     → 集成 Spring Observability 监控

Agent 构建层面  
├── Hook (AgentHook/ModelHook)     → 在特定位置插入自定义逻辑
├── Interceptor (ModelInterceptor/ToolInterceptor) → 拦截和包装调用
└── enableLogging()                → 启用内置日志输出
```

---

## 方案对比

| 方案 | 粒度 | 适用场景 | 侵入性 | 性能开销 |
|------|------|---------|--------|---------|
| **GraphLifecycleListener** | 节点级 | 调试图执行流程、状态变化 | ❌ 无 | 低 |
| **Hook** | Agent 级 | Agent 启动/结束时的处理 | ❌ 无 | 低 |
| **Interceptor** | 调用级 | 模型/工具调用的拦截增强 | ❌ 无 | 中 |
| **ObservationRegistry** | 全链路 | 生产环境监控、APM 集成 | ❌ 无 | 中 |
| **enableLogging** | 推理级 | 快速查看推理过程 | ⚠️ 需配置 | 低 |

---

## 方案一:GraphLifecycleListener - 图执行日志

### 特点
- ✅ 监听 **所有节点** 的执行生命周期(启动/执行前/执行后/完成/错误)
- ✅ 可访问每个节点的 **输入/输出状态**
- ✅ 支持多个监听器,按 LIFO 顺序执行
- ✅ 完全非侵入,在编译时注入

### 使用方式

#### 1. 基础用法 - 监听所有节点

```java
import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentExecutionLogger implements GraphLifecycleListener {

    @Override
    public void onStart(String nodeId, Map<String, Object> state, RunnableConfig config) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🚀 [GRAPH START] 节点: {}", nodeId);
        log.info("  线程ID: {}", config.threadId().orElse("N/A"));
        logState(state);
    }

    @Override
    public void before(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        log.info("▶️  [NODE BEFORE] 节点: {} | 时间: {}", nodeId, curTime);
        logState(state);
    }

    @Override
    public void after(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        log.info("◀️  [NODE AFTER] 节点: {} | 时间: {}", nodeId, curTime);
        logState(state);
    }

    @Override
    public void onComplete(String nodeId, Map<String, Object> state, RunnableConfig config) {
        log.info("✅ [GRAPH COMPLETE] 节点: {}", nodeId);
        logState(state);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Override
    public void onError(String nodeId, Map<String, Object> state, Throwable ex, RunnableConfig config) {
        log.error("❌ [GRAPH ERROR] 节点: {}", nodeId);
        log.error("  异常类型: {}", ex.getClass().getName());
        log.error("  异常消息: {}", ex.getMessage());
        logState(state);
    }

    private void logState(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            log.info("  状态: (空)");
            return;
        }
        
        log.info("  状态键值对 (共 {} 个):", state.size());
        state.forEach((key, value) -> {
            String valueStr = formatValue(value);
            log.info("    {} = {}", key, valueStr);
        });
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String str) {
            return str.length() > 200 ? str.substring(0, 200) + "..." : str;
        }
        if (value instanceof List<?> list) {
            return String.format("List[%d]", list.size());
        }
        if (value instanceof Map<?, ?> map) {
            return String.format("Map[%d keys]", map.size());
        }
        String str = value.toString();
        return str.length() > 200 ? str.substring(0, 200) + "..." : str;
    }
}
```

#### 2. 注册监听器

**方式 A: 在 Agent 构建时注册**

```java
@Configuration
public class AgentConfig {

    @Bean
    public ReactAgent werewolfGameAgent(ChatModel chatModel) {
        return ReactAgent.builder()
            .name("werewolf_game")
            .model(chatModel)
            .instruction("你是狼人杀游戏主持人...")
            .build();
    }

    @Bean
    public CompiledGraph configureAgentWithListener(ReactAgent agent) {
        // Agent 内部会调用 graph.compile(),我们需要在那之前配置
        // 注意:ReactAgent.build() 已经调用了 compile(),
        // 所以需要通过修改 CompileConfig 来实现
        
        CompileConfig config = CompileConfig.builder()
            .withLifecycleListener(new AgentExecutionLogger())
            .build();
        
        // 需要重新编译 Agent 的内部 Graph
        return agent.getGraph().compile(config);
    }
}
```

**方式 B: 通过 CompileConfig 注册(推荐)**

如果你有访问 Agent 内部构建逻辑的权限,可以在 `Builder.buildConfig()` 中配置:

```java
public class CustomAgentBuilder extends DefaultBuilder {
    
    @Override
    protected CompileConfig buildConfig() {
        CompileConfig config = super.buildConfig();
        
        return CompileConfig.builder()
            .saverConfig(config.saverConfig())
            .recursionLimit(config.recursionLimit())
            .releaseThread(config.releaseThread())
            .withLifecycleListener(new AgentExecutionLogger())  // 添加监听器
            .build();
    }
}
```

**方式 C: 在狼人杀项目中应用(实际案例)**

```java
// 在 WerewolfGameAgentBuilder 中修改
public ReactAgent buildGameLoopAgent() {
    StateGraph graph = new StateGraph("werewolf_game_loop", 
        this::createGameLoopKeyStrategyFactory);
    
    // ... 添加节点和边 ...
    
    // 编译时添加监听器
    CompileConfig config = CompileConfig.builder()
        .saverConfig(SaverConfig.builder()
            .register(memorySaver)
            .build())
        .withLifecycleListener(new AgentExecutionLogger())  // ← 非侵入式日志
        .build();
    
    return new ReactAgent(llmNode, toolNode, config, this);
}
```

#### 3. 高级用法 - 性能统计

```java
@Slf4j
public class PerformanceTrackingListener implements GraphLifecycleListener {
    
    private final Map<String, Long> nodeStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> nodeDurations = new ConcurrentHashMap<>();

    @Override
    public void before(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        nodeStartTimes.put(nodeId, curTime);
    }

    @Override
    public void after(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        Long startTime = nodeStartTimes.get(nodeId);
        if (startTime != null) {
            long duration = curTime - startTime;
            nodeDurations.put(nodeId, duration);
            log.info("⏱️  节点 {} 执行耗时: {}ms", nodeId, duration);
        }
    }

    @Override
    public void onComplete(String nodeId, Map<String, Object> state, RunnableConfig config) {
        log.info("📊 性能统计:");
        nodeDurations.forEach((node, duration) -> 
            log.info("  {} → {}ms", node, duration)
        );
        
        long totalDuration = nodeDurations.values().stream()
            .mapToLong(Long::longValue)
            .sum();
        log.info("  总耗时: {}ms", totalDuration);
    }
}
```

---

## 方案二:Hook - Agent 生命周期日志

### 特点
- ✅ 在 Agent 的 **特定位置** 执行自定义逻辑(BEFORE_AGENT/AFTER_AGENT/BEFORE_MODEL/AFTER_MODEL)
- ✅ 可以 **修改状态** (返回 Map 会合并到状态中)
- ✅ 支持异步执行(返回 CompletableFuture)
- ✅ 更聚焦于 Agent 语义层面

### 使用方式

#### 1. AgentHook - Agent 启动/结束日志

```java
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;

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
        log.info("  输入: {}", state.value("input").orElse("N/A"));
        log.info("  线程ID: {}", config.threadId().orElse("N/A"));
        log.info("  时间: {}", System.currentTimeMillis());
        
        // 可以在状态中记录启动时间
        return CompletableFuture.completedFuture(
            Map.of("agent_start_time", System.currentTimeMillis())
        );
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(
            OverAllState state, RunnableConfig config) {
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🏁 [AGENT END] Agent: {}", getAgentName());
        
        // 计算执行时间
        Optional<Object> startTimeOpt = state.value("agent_start_time");
        if (startTimeOpt.isPresent()) {
            long startTime = (Long) startTimeOpt.get();
            long duration = System.currentTimeMillis() - startTime;
            log.info("  执行耗时: {}ms", duration);
        }
        
        // 记录最终输出
        state.value("output").ifPresent(output -> 
            log.info("  输出: {}", output)
        );
        
        return CompletableFuture.completedFuture(Map.of());
    }
}
```

#### 2. ModelHook - 模型调用日志

```java
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;

@Slf4j
public class ModelCallLogHook extends ModelHook {

    @Override
    public String getName() {
        return "model_call_log";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(
            OverAllState state, RunnableConfig config) {
        
        log.info("🤖 [MODEL CALL] Agent: {} 准备调用模型", getAgentName());
        
        // 记录消息历史
        Optional<Object> messagesOpt = state.value("messages");
        if (messagesOpt.isPresent()) {
            List<Message> messages = (List<Message>) messagesOpt.get();
            log.info("  消息数量: {}", messages.size());
            log.info("  最后一条消息: {}", 
                messages.isEmpty() ? "N/A" : messages.get(messages.size() - 1));
        }
        
        return CompletableFuture.completedFuture(
            Map.of("model_call_time", System.currentTimeMillis())
        );
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
            OverAllState state, RunnableConfig config) {
        
        log.info("🤖 [MODEL RESPONSE] Agent: {} 模型返回", getAgentName());
        
        // 计算模型调用耗时
        Optional<Object> callTimeOpt = state.value("model_call_time");
        if (callTimeOpt.isPresent()) {
            long callTime = (Long) callTimeOpt.get();
            long duration = System.currentTimeMillis() - callTime;
            log.info("  模型响应耗时: {}ms", duration);
        }
        
        // 记录 Token 使用量
        Optional<Object> tokenUsageOpt = state.value("_TOKEN_USAGE_");
        tokenUsageOpt.ifPresent(usage -> 
            log.info("  Token 使用: {}", usage)
        );
        
        return CompletableFuture.completedFuture(Map.of());
    }
}
```

#### 3. 注册 Hook

```java
@Configuration
public class AgentConfig {

    @Bean
    public ReactAgent werewolfGameAgent(ChatModel chatModel) {
        return ReactAgent.builder()
            .name("werewolf_game")
            .model(chatModel)
            .instruction("你是狼人杀游戏主持人...")
            .hooks(
                new AgentExecutionLogHook(),  // Agent 级别日志
                new ModelCallLogHook()        // 模型调用日志
            )
            .build();
    }
}
```

---

## 方案三:Interceptor - 模型和工具调用日志

### 特点
- ✅ 拦截 **模型调用** 和 **工具调用**,可以修改请求/响应
- ✅ 支持链式调用(责任链模式)
- ✅ 适合添加日志、重试、缓存等横切逻辑
- ✅ 粒度最细,可以看到每次调用的详细信息

### 使用方式

#### 1. ModelInterceptor - 模型调用日志

```java
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;

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
        log.info("  消息数量: {}", request.getMessages().size());
        log.info("  工具数量: {}", request.getTools().size());
        
        // 记录最后一条用户消息
        request.getMessages().stream()
            .filter(msg -> msg instanceof UserMessage)
            .reduce((first, second) -> second)
            .ifPresent(msg -> log.info("  用户消息: {}", 
                ((UserMessage) msg).getText()));
        
        long startTime = System.currentTimeMillis();
        
        // ===== 执行实际调用 =====
        ModelResponse response = handler.call(request);
        
        // ===== 响应后日志 =====
        long duration = System.currentTimeMillis() - startTime;
        log.info("📥 [MODEL RESPONSE]");
        log.info("  耗时: {}ms", duration);
        
        // 记录助手回复
        if (response.getMessage() instanceof AssistantMessage assistantMsg) {
            log.info("  回复内容: {}", assistantMsg.getText());
            if (assistantMsg.hasToolCalls()) {
                log.info("  工具调用数量: {}", assistantMsg.getToolCalls().size());
                assistantMsg.getToolCalls().forEach(tc -> 
                    log.info("    - {}: {}", tc.name(), tc.arguments())
                );
            }
        }
        
        // 记录 Token 使用
        if (response.getChatResponse() != null) {
            Usage usage = response.getChatResponse().getMetadata().getUsage();
            log.info("  Token 使用: Prompt={}, Completion={}, Total={}", 
                usage.getPromptTokens(), 
                usage.getGenerationTokens(), 
                usage.getTotalTokens());
        }
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return response;
    }
}
```

#### 2. ToolInterceptor - 工具调用日志

```java
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;

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
        log.info("  调用参数: {}", request.getArguments());
        
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
            log.info("  返回结果: {}", response.getResult());
            
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
}
```

#### 3. 注册 Interceptor

```java
@Configuration
public class AgentConfig {

    @Bean
    public ReactAgent werewolfGameAgent(ChatModel chatModel) {
        return ReactAgent.builder()
            .name("werewolf_game")
            .model(chatModel)
            .instruction("你是狼人杀游戏主持人...")
            .interceptors(
                new ModelCallLoggingInterceptor(),  // 模型调用日志
                new ToolCallLoggingInterceptor()    // 工具调用日志
            )
            .build();
    }
}
```

---

## 方案四:ObservationRegistry - 指标监控

### 特点
- ✅ 集成 **Spring Observability** 体系
- ✅ 支持 **Metrics**、**Tracing**、**Logging** 三大支柱
- ✅ 可以无缝对接 Micrometer、OpenTelemetry、Zipkin 等
- ✅ 适合生产环境的 APM 监控

### 使用方式

#### 1. 配置 ObservationRegistry

```java
@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        
        // 添加 Micrometer Metrics
        registry.observationConfig()
            .observationHandler(
                new DefaultMeterObservationHandler(meterRegistry())
            );
        
        // 添加 Tracing (可选)
        registry.observationConfig()
            .observationHandler(
                new DefaultTracingObservationHandler(tracer())
            );
        
        return registry;
    }

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
```

#### 2. 应用到 Agent

```java
@Configuration
public class AgentConfig {

    @Autowired
    private ObservationRegistry observationRegistry;

    @Bean
    public ReactAgent werewolfGameAgent(ChatModel chatModel) {
        return ReactAgent.builder()
            .name("werewolf_game")
            .model(chatModel)
            .instruction("你是狼人杀游戏主持人...")
            .observationRegistry(observationRegistry)  // ← 非侵入式监控
            .build();
    }
}
```

#### 3. 查看指标

```java
@RestController
@RequestMapping("/metrics")
public class MetricsController {

    @Autowired
    private MeterRegistry meterRegistry;

    @GetMapping("/agent")
    public Map<String, Object> getAgentMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        meterRegistry.getMeters().forEach(meter -> {
            if (meter.getId().getName().startsWith("agent")) {
                meter.measure().forEach(measurement -> 
                    metrics.put(
                        meter.getId().getName() + "." + measurement.getStatistic(),
                        measurement.getValue()
                    )
                );
            }
        });
        
        return metrics;
    }
}
```

Framework 会自动记录:
- 模型调用次数
- Token 使用量
- 节点执行时间
- 错误率
- ...

---

## 方案五:enableLogging - 内置日志

### 特点
- ✅ 最简单的方式,一行代码启用
- ✅ 自动记录 **推理过程** 和 **工具调用**
- ✅ 日志由框架内部实现,无需自定义

### 使用方式

```java
@Configuration
public class AgentConfig {

    @Bean
    public ReactAgent werewolfGameAgent(ChatModel chatModel) {
        return ReactAgent.builder()
            .name("werewolf_game")
            .model(chatModel)
            .instruction("你是狼人杀游戏主持人...")
            .enableLogging(true)  // ← 一键启用日志
            .build();
    }
}
```

### 日志输出示例

```
[ThreadId main] Agent werewolf_game reasoning round 1 model chain has started.
[ThreadId main] Agent werewolf_game reasoning round 1 model chain returned:
AssistantMessage[content='根据当前游戏状态...', toolCalls=[...]]

[ThreadId main] Agent werewolf_game acting with 3 tools.
[ThreadId main] Agent werewolf_game acting, executing tool start_night_phase.
[ThreadId main] Agent werewolf_game acting returned: ToolResponseMessage[...]
```

### 配置日志级别

```yaml
# application.yml
logging:
  level:
    com.alibaba.cloud.ai.graph.agent.node.AgentLlmNode: DEBUG
    com.alibaba.cloud.ai.graph.agent.node.AgentToolNode: DEBUG
```

---

## 最佳实践组合

根据不同场景,推荐以下组合方案:

### 🔍 开发调试阶段

```java
ReactAgent agent = ReactAgent.builder()
    .name("werewolf_game")
    .model(chatModel)
    .instruction("...")
    
    // 1. 启用内置日志 - 快速查看推理过程
    .enableLogging(true)
    
    // 2. 添加 GraphLifecycleListener - 跟踪状态变化
    .build();

// 在编译时添加监听器
CompileConfig config = CompileConfig.builder()
    .withLifecycleListener(new GraphDebugLifecycleListener())  // 已有的调试监听器
    .build();
```

### 🚀 生产环境

```java
ReactAgent agent = ReactAgent.builder()
    .name("werewolf_game")
    .model(chatModel)
    .instruction("...")
    
    // 1. ObservationRegistry - APM 监控
    .observationRegistry(observationRegistry)
    
    // 2. Interceptor - 记录关键调用
    .interceptors(
        new ModelCallLoggingInterceptor(),  // 记录模型调用
        new ToolCallLoggingInterceptor()    // 记录工具调用
    )
    
    // 3. Hook - 记录 Agent 级别事件
    .hooks(
        new AgentExecutionLogHook()  // 记录启动/结束
    )
    
    .build();
```

### 📊 性能分析

```java
ReactAgent agent = ReactAgent.builder()
    .name("werewolf_game")
    .model(chatModel)
    .instruction("...")
    
    // 1. 性能追踪监听器
    .build();

CompileConfig config = CompileConfig.builder()
    .withLifecycleListener(new PerformanceTrackingListener())
    .withLifecycleListener(new GraphObservationLifecycleListener(observationRegistry))
    .build();
```

### 🎯 狼人杀项目实际应用

```java
// 在 WerewolfGameAgentBuilder 中
public ReactAgent buildGameLoopAgent() {
    StateGraph graph = new StateGraph("werewolf_game_loop", 
        this::createGameLoopKeyStrategyFactory);
    
    // ... 添加节点和边 ...
    
    // 编译配置 - 非侵入式日志
    CompileConfig config = CompileConfig.builder()
        .saverConfig(SaverConfig.builder()
            .register(memorySaver)
            .build())
        
        // 添加多个监听器
        .withLifecycleListener(new GraphDebugLifecycleListener())  // 调试日志
        .withLifecycleListener(new PerformanceTrackingListener())  // 性能统计
        
        .build();
    
    return new ReactAgent(llmNode, toolNode, config, this);
}
```

---

## 总结

| 场景 | 推荐方案 | 优势 |
|------|---------|------|
| 快速调试 | `enableLogging(true)` | 一行代码,即开即用 |
| 状态追踪 | `GraphLifecycleListener` | 查看所有节点的状态流转 |
| 业务埋点 | `Hook` | 在特定位置记录业务日志 |
| 调用拦截 | `Interceptor` | 细粒度控制模型/工具调用 |
| 生产监控 | `ObservationRegistry` | APM 集成,完整可观测性 |

**关键原则**:
1. ✅ **完全非侵入** - 不修改业务代码
2. ✅ **灵活组合** - 多种方案可叠加使用
3. ✅ **分层设计** - Graph 层 → Agent 层 → 调用层
4. ✅ **性能友好** - 异步执行,最小开销

---

## 参考资料

- `GraphDebugLifecycleListener.java` - 现有的调试监听器实现
- `HooksExample.java` - Hook 使用示例
- `GraphObservationLifecycleListener.java` - Observability 集成示例
- `ModelInterceptor.java` / `ToolInterceptor.java` - 拦截器基类
