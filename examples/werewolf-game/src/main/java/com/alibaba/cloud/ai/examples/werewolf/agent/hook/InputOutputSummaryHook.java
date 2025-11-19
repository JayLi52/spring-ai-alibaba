package com.alibaba.cloud.ai.examples.werewolf.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@HookPositions({HookPosition.AFTER_MODEL})
public class InputOutputSummaryHook extends ModelHook {

    private final ChatModel summaryModel;
    private final String instruction;

    public InputOutputSummaryHook() {
        this.summaryModel = null;
        this.instruction = null;
    }

    public InputOutputSummaryHook(ChatModel summaryModel) {
        this.summaryModel = summaryModel;
        this.instruction = null;
    }

    public InputOutputSummaryHook(ChatModel summaryModel, String instruction) {
        this.summaryModel = summaryModel;
        this.instruction = instruction;
    }

    @Override
    public String getName() {
        return "io_summary";
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        Object messagesObj = state.value("messages").orElse(List.of());
        List<Message> msgs;
        if (messagesObj instanceof List<?>) {
            List<?> list = (List<?>) messagesObj;
            msgs = list.stream()
                    .filter(Message.class::isInstance)
                    .map(Message.class::cast)
                    .collect(java.util.stream.Collectors.toList());
        } else if (messagesObj instanceof Message) {
            msgs = java.util.List.of((Message) messagesObj);
        } else {
            msgs = java.util.List.of();
        }

        String lastUser = msgs.stream().filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).getText()).reduce((a,b) -> b).orElse("");
        String lastAssistant = msgs.stream().filter(m -> m instanceof AssistantMessage)
                .map(m -> ((AssistantMessage) m).getText()).reduce((a,b) -> b).orElse("");
        String summary;
        if (summaryModel != null) {
            String promptText = (instruction == null || instruction.isEmpty() ? "" : instruction + "\n\n")
                    + "请将以下对话简洁总结为关键要点，保留可用于下一步决策的核心信息：\n\n"
                    + "输入（人类）：\n" + lastUser + "\n\n"
                    + "输出（助理）：\n" + lastAssistant + "\n\n"
                    + "要求：\n- 语言简洁\n- 保留重要实体、动作和理由\n- 不要重复原文，直接给出浓缩总结";
            try {
                var resp = summaryModel.call(new Prompt(List.of(new UserMessage(promptText))));
                summary = resp.getResult().getOutput().getText();
            } catch (Exception e) {
                summary = "输入:\n" + lastUser + "\n输出:\n" + lastAssistant;
            }
        } else {
            summary = "输入:\n" + lastUser + "\n输出:\n" + lastAssistant;
        }
        return CompletableFuture.completedFuture(Map.of("last_summary", summary));
    }
}