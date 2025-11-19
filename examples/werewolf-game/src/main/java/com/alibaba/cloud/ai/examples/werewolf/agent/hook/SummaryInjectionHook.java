package com.alibaba.cloud.ai.examples.werewolf.agent.hook;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@HookPositions({HookPosition.BEFORE_MODEL})
public class SummaryInjectionHook extends ModelHook {

    @Override
    public String getName() {
        return "summary_injection";
    }

    @Override
    public Map<String, KeyStrategy> getKeyStrategys() {
        return Map.of("messages", new ReplaceStrategy());
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        state.registerKeyAndStrategy("messages", new ReplaceStrategy());
        String summary = (String) state.value("last_summary").orElse("");
        List<Message> msgs = (List<Message>) state.value("messages").orElse(List.of());
        String instruction = msgs.stream().filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).getText()).reduce((a,b) -> b).orElse("");
        String composite = (summary.isEmpty() ? instruction : summary + "\n\n" + instruction);
        List<Message> newMsgs = new ArrayList<>();
        newMsgs.add(new UserMessage(composite));
        return CompletableFuture.completedFuture(Map.of("messages", newMsgs));
    }
}