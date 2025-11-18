package com.alibaba.cloud.ai.examples.werewolf.agent;

import com.alibaba.cloud.ai.examples.werewolf.agent.day.WerewolfDayAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.agent.night.WerewolfNightAgentBuilder;
import com.alibaba.cloud.ai.examples.werewolf.config.RolePromptConfig;
import com.alibaba.cloud.ai.examples.werewolf.config.WerewolfConfig;
import com.alibaba.cloud.ai.examples.werewolf.model.Player;
import com.alibaba.cloud.ai.examples.werewolf.model.Role;
import com.alibaba.cloud.ai.examples.werewolf.model.WerewolfGameState;
import com.alibaba.cloud.ai.examples.werewolf.service.GameStateService;
import com.alibaba.cloud.ai.examples.werewolf.service.SpeechOrderService;
import com.alibaba.cloud.ai.examples.werewolf.service.VictoryCheckerService;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WerewolfAgentGraphTests {

    private RolePromptConfig rolePromptConfig;
    private ChatModel chatModel;

    private WerewolfNightAgentBuilder nightBuilder;
    private WerewolfDayAgentBuilder dayBuilder;
    private WerewolfGameAgentBuilder gameBuilder;

    private WerewolfConfig werewolfConfig;
    private GameStateService gameStateService;
    private VictoryCheckerService victoryCheckerService;
    private SpeechOrderService speechOrderService;

    @BeforeEach
    void setup() {
        rolePromptConfig = new RolePromptConfig();
        chatModel = new NoOpChatModel();
        werewolfConfig = new WerewolfConfig();
        gameStateService = new GameStateService(werewolfConfig);
        victoryCheckerService = new VictoryCheckerService();
        speechOrderService = new SpeechOrderService();
        nightBuilder = new WerewolfNightAgentBuilder(chatModel, rolePromptConfig);
        dayBuilder = new WerewolfDayAgentBuilder(chatModel, rolePromptConfig, gameStateService);
        gameBuilder = new WerewolfGameAgentBuilder(nightBuilder, dayBuilder, gameStateService, victoryCheckerService, speechOrderService, werewolfConfig);
    }

    @Test
    void testNightPhaseGraphStructure() throws GraphStateException {
        WerewolfGameState gameState = createDeterministicState();
        Agent nightPhase = nightBuilder.buildNightPhaseAgent(gameState);
        GraphRepresentation diagram = nightPhase.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        assertNotNull(diagram);
        assertTrue(diagram.content().contains("night_phase"));
        assertTrue(diagram.content().contains("werewolf_final_decision"));
    }

    @Test
    void testDayDiscussionAndVotingGraphStructure() throws GraphStateException {
        WerewolfGameState gameState = createDeterministicState();
        Agent discussion = dayBuilder.buildDayDiscussionAgent(gameState);
        GraphRepresentation discussionDiagram = discussion.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        assertTrue(discussionDiagram.content().contains("day_discussion"));

        Agent voting = dayBuilder.buildVotingAgent(gameState);
        GraphRepresentation votingDiagram = voting.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        assertTrue(votingDiagram.content().contains("voting"));

        Agent dayPhase = dayBuilder.buildDayPhaseAgent(gameState);
        GraphRepresentation dayDiagram = dayPhase.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        assertTrue(dayDiagram.content().contains("day_phase"));
    }

    @Test
    void testGameLoopGraphStructure() throws GraphStateException {
        WerewolfGameState gameState = createDeterministicState();
        Agent gameLoop = gameBuilder.buildGameLoopAgent(gameState);
        GraphRepresentation diagram = gameLoop.getAndCompileGraph().getGraph(GraphRepresentation.Type.MERMAID);
        assertTrue(diagram.content().contains("game_loop"));
        assertTrue(diagram.content().contains("single_round"));
    }

    private WerewolfGameState createDeterministicState() {
        List<Player> players = new ArrayList<>();
        players.add(Player.builder().name("Alice").role(Role.WEREWOLF).alive(true).build());
        players.add(Player.builder().name("Bob").role(Role.WEREWOLF).alive(true).build());
        players.add(Player.builder().name("Charlie").role(Role.SEER).alive(true).build());
        players.add(Player.builder().name("Diana").role(Role.WITCH).alive(true).build());
        players.add(Player.builder().name("Eve").role(Role.VILLAGER).alive(true).build());

        List<String> alive = List.of("Alice", "Bob", "Charlie", "Diana", "Eve");

        Map<String, Role> roles = Map.of(
                "Alice", Role.WEREWOLF,
                "Bob", Role.WEREWOLF,
                "Charlie", Role.SEER,
                "Diana", Role.WITCH,
                "Eve", Role.VILLAGER
        );

        return WerewolfGameState.builder()
                .allPlayers(players)
                .alivePlayers(new ArrayList<>(alive))
                .playerRoles(roles)
                .currentRound(1)
                .firstDay(false)
                .build();
    }

    static class NoOpChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException("NoOpChatModel");
        }
    }
}