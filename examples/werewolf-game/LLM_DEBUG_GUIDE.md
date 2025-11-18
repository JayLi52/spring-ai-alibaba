# LLM 调用调试指南

## 🔍 问题现象
```
org.springframework.ai.retry.NonTransientAiException: 404
```

## 📍 调用链路分析

### 1. **完整调用栈**

```
你的测试代码 (WerewolfAgentGraphTests.java:136)
  ↓
Agent.invoke()
  ↓
AgentLlmNode.apply() (L174 或 L233)
  ↓
buildChatClientRequestSpec(request).stream().chatResponse()
  ↓
ChatClient → OpenAiChatModel
  ↓
RestClient (Spring Web)
  ↓
RetryUtils (Spring AI 内置重试机制)  ← 这里报错
  ↓
HTTP Request → https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
  ↓
404 错误 ← DashScope API 返回
```

### 2. **关键代码位置**

#### AgentLlmNode.java (实际 LLM 调用位置)

**流式调用 (第 174 行)**:
```java
Flux<ChatResponse> chatResponseFlux = buildChatClientRequestSpec(request).stream().chatResponse();
```

**非流式调用 (第 233 行)**:
```java
ChatResponse response = buildChatClientRequestSpec(request).call().chatResponse();
```

#### RetryUtils (Spring AI 框架内置)
Spring AI 的 retry 逻辑在 `org.springframework.ai.retry.RetryUtils` 中:
- 默认会重试 3 次
- 遇到 404 等错误会标记为 `NonTransientAiException` (不可重试错误)
- 位于 Spring AI 框架内部,不在你的项目代码中

## 🛠️ Debug 方法

### 方法 1: 检查 API 配置

404 错误通常是以下原因:

1. **API Key 无效或过期**
   ```bash
   # 检查环境变量
   echo $AI_DASHSCOPE_API_KEY
   ```

2. **Base URL 错误**
   - 正确: `https://dashscope.aliyuncs.com/compatible-mode/v1`
   - 错误: `https://dashscope.aliyuncs.com/api/v1` (非兼容模式)

3. **模型名称错误**
   - 正确: `qwen-max`, `qwen-plus`, `qwen-turbo`
   - 错误: `gpt-3.5-turbo` (不是 OpenAI 模型)

### 方法 2: 在 IDE 中添加断点

在以下位置打断点:

1. **测试入口**:
   - `WerewolfAgentGraphTests.java:136` (invoke 调用处)

2. **Agent 框架入口**:
   - `AgentLlmNode.java:174` (流式调用)
   - `AgentLlmNode.java:233` (非流式调用)

3. **异常捕获**:
   - `AgentLlmNode.java:191` (流式异常)
   - `AgentLlmNode.java:245` (非流式异常)

### 方法 3: 启用 HTTP 日志

在测试类中添加 HTTP 请求日志:

```java
@BeforeEach
void setup() {
    // 启用 HTTP 调试日志
    System.setProperty("logging.level.org.springframework.web.client.RestClient", "DEBUG");
    System.setProperty("logging.level.org.springframework.ai.retry", "DEBUG");
    
    // ... 现有代码
}
```

### 方法 4: 手动测试 API 连接

使用 curl 测试 DashScope API:

```bash
export API_KEY="your-api-key"

curl https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-max",
    "messages": [
      {"role": "user", "content": "你好"}
    ]
  }'
```

如果 curl 返回 404,说明是 API 配置问题。

### 方法 5: 添加自定义拦截器查看请求

创建测试专用的 ModelInterceptor:

```java
public class DebugModelInterceptor extends ModelInterceptor {
    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        System.out.println("===== 即将调用大模型 =====");
        System.out.println("消息数量: " + request.getMessages().size());
        System.out.println("最后一条消息: " + request.getMessages().get(request.getMessages().size() - 1));
        
        long startTime = System.currentTimeMillis();
        
        try {
            ModelResponse response = handler.call(request);
            System.out.println("✅ 调用成功,耗时: " + (System.currentTimeMillis() - startTime) + "ms");
            return response;
        } catch (Exception e) {
            System.err.println("❌ 调用失败: " + e.getMessage());
            System.err.println("错误类型: " + e.getClass().getName());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Override
    public String getName() {
        return "DebugInterceptor";
    }
}
```

在测试中使用:

```java
Agent agent = nightBuilder.buildWerewolfDiscussionAgent(gameState);
agent.getReactAgent().setModelInterceptors(List.of(new DebugModelInterceptor()));
```

## 🔧 常见问题排查

### 问题 1: API Key 无效
**现象**: 401 或 404 错误

**解决**:
```bash
# 检查 API Key
curl https://dashscope.aliyuncs.com/compatible-mode/v1/models \
  -H "Authorization: Bearer $AI_DASHSCOPE_API_KEY"
```

### 问题 2: 模型名称错误
**现象**: 404 或 400 错误

**解决**: 使用正确的模型名称
- ✅ `qwen-max`
- ✅ `qwen-plus`
- ❌ `gpt-4` (不支持)

### 问题 3: 网络问题
**现象**: Connection timeout 或 404

**解决**:
```bash
# 测试网络连通性
curl -I https://dashscope.aliyuncs.com
```

## 📝 Spring AI Retry 逻辑说明

Spring AI 的 `RetryUtils` 提供了内置的重试机制:

```java
// 位于: spring-ai-retry 模块
public class RetryUtils {
    // 默认重试配置
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF = 1000; // 1秒
    
    // 可重试的错误: 429, 500, 502, 503, 504
    // 不可重试的错误: 400, 401, 403, 404
}
```

**404 错误为何不重试?**
- 404 表示资源不存在,是永久性错误
- 重试不会改变结果
- Spring AI 将其标记为 `NonTransientAiException`

## 🎯 快速排查步骤

1. **检查环境变量**
   ```bash
   echo $AI_DASHSCOPE_API_KEY
   echo $AI_DASHSCOPE_CHAT_MODEL
   ```

2. **检查测试日志输出**
   - 运行测试,查看 "LLM 配置信息" 输出
   - 确认 API Key 和 Model 是否正确

3. **手动测试 API**
   ```bash
   curl https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
     -H "Authorization: Bearer $AI_DASHSCOPE_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"model":"qwen-max","messages":[{"role":"user","content":"test"}]}'
   ```

4. **在 IDE 中打断点**
   - `AgentLlmNode.java:174` (查看实际调用参数)
   - `AgentLlmNode.java:191` (查看异常详情)

5. **启用详细日志**
   ```java
   System.setProperty("logging.level.org.springframework.web.client", "DEBUG");
   ```

## 📚 相关文件

- **LLM 调用入口**: `AgentLlmNode.java:174, 233`
- **异常处理**: `AgentLlmNode.java:191, 245`
- **测试配置**: `WerewolfAgentGraphTests.java:45-82`
- **Spring AI Retry**: `spring-ai-retry` 模块 (外部依赖)
