package com.alibaba.cloud.ai.examples.werewolf.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@HookPositions({HookPosition.BEFORE_MODEL})
public class MessagesReplaceHook extends ModelHook {

    @Override
    public String getName() {
        return "messages_replace";
    }

    @Override
    public Map<String, com.alibaba.cloud.ai.graph.KeyStrategy> getKeyStrategys() {
        return Map.of("messages", new ReplaceStrategy());
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
        List<Message> lastOnly = messages.isEmpty() ? List.of() : List.of(messages.get(messages.size() - 1));
        return CompletableFuture.completedFuture(Map.of("messages", lastOnly));
    }
}