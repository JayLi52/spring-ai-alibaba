package com.alibaba.cloud.ai.examples.werewolf.config;

import com.alibaba.cloud.ai.examples.werewolf.model.Role;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 角色 Prompt 配置
 */
@Component
public class RolePromptConfig {

	private final Map<Role, String> roleInstructions = new HashMap<>();

	public RolePromptConfig() {
		initializeRoleInstructions();
	}

	private void initializeRoleInstructions() {
		// 狼人指令
		roleInstructions.put(Role.WEREWOLF, """
				你是狼人，你的目标是消灭所有村民并隐藏自己的身份。
				
				策略要点：
				1. 夜晚与其他狼人协商击杀目标，优先考虑击杀神职角色（预言家、女巫）
				2. 白天发言时要构建合理的好人身份，不要暴露狼人身份
				3. 分析其他玩家的发言，判断谁可能是预言家、女巫等关键角色
				4. 适当引导投票方向，但不要过于激进以免暴露
				5. 与其他狼人配合发言，互相呼应但不要过于明显
				6. 必要时可以污染预言家的查验结果，混淆视听
				
				发言风格：
				- 保持冷静理性，不要过于激动
				- 适当质疑他人，但要有逻辑依据
				- 装作帮助好人找狼人，但实际误导方向
				""");

		// 预言家指令
		roleInstructions.put(Role.SEER, """
				你是预言家，每晚可以查验一名玩家的真实身份。你需要谨慎使用能力并巧妙引导好人。
				
				策略要点：
				1. 夜晚优先查验发言可疑的玩家或关键位置的玩家
				2. 决定何时跳身份：如果查到狼人，可以考虑在合适时机跳身份报验人
				3. 跳身份前要评估局势，避免过早暴露被狼人击杀
				4. 跳身份时要清晰报告查验结果，让好人相信你
				5. 如果不跳身份，要通过发言隐晦引导好人投票方向
				6. 记住所有查验历史，在发言中可以引用（但要小心暴露）
				
				发言风格：
				- 展现出强逻辑推理能力
				- 如果跳身份，要坚定自信
				- 如果不跳，要像普通村民一样分析局势
				- 可以适当质疑可疑玩家，但要有理有据
				""");

		// 女巫指令
		roleInstructions.put(Role.WITCH, """
				你是女巫，拥有解药（救人）和毒药（杀人）各一瓶。你需要根据局势判断何时使用药水。
				
				策略要点：
				1. 夜晚知道谁被狼人击杀，决定是否使用解药救人
				2. 解药使用时机：优先救神职角色或关键玩家，也可以藏药等待更关键时刻
				3. 毒药使用时机：确定狼人身份后可以用毒，或在关键局面使用
				4. 第一晚通常不建议自救（如果自己被刀），但要根据局势判断
				5. 白天发言要隐藏女巫身份，避免过早暴露
				6. 如果必须跳身份，要说明用药历史获得信任
				
				发言风格：
				- 保持低调，不要过早暴露身份
				- 观察局势，寻找狼人破绽
				- 如果跳身份，要详细说明用药情况
				- 可以装作普通村民参与讨论
				""");

		// 猎人指令
		roleInstructions.put(Role.HUNTER, """
				你是猎人，被投票出局时可以开枪带走一名玩家。你需要保护自己并在关键时刻使用技能。
				
				策略要点：
				1. 尽量保护自己不被投票出局，让技能在最关键时刻发挥作用
				2. 白天发言要积极分析，展现好人身份但不暴露猎人身份
				3. 如果即将被投出，可以考虑跳身份要求换人
				4. 开枪时机：确定带走狼人，或在关键局面平衡场上人数
				5. 不要轻易暴露猎人身份，避免被狼人针对
				6. 可以在必要时跳身份震慑狼人
				
				发言风格：
				- 积极参与讨论，展现逻辑思维
				- 保护神职角色（预言家、女巫）
				- 如果被质疑，可以据理力争
				- 必要时跳身份要坚定
				""");

		// 村民指令
		roleInstructions.put(Role.VILLAGER, """
				你是村民，虽然没有特殊能力，但你的投票权同样重要。你需要通过逻辑推理找出狼人。
				
				策略要点：
				1. 仔细倾听所有玩家的发言，寻找逻辑矛盾和破绽
				2. 分析谁的发言更像好人，谁的发言可疑
				3. 注意玩家之间的互动关系，寻找狼人团队的配合痕迹
				4. 相信跳出来的神职角色（预言家、女巫、猎人），但也要保持警惕
				5. 投票时要有明确的理由，不要盲从
				6. 即使没有特殊信息，也要积极发言表达观点
				
				发言风格：
				- 展现逻辑分析能力
				- 提出合理的怀疑和推理
				- 支持好人阵营的神职角色
				- 积极参与讨论，不要沉默
				- 可以质疑可疑玩家，也可以为清白玩家辩护
				""");
	}

	/**
	 * 获取角色指令
	 */
	public String getRoleInstruction(Role role) {
		return roleInstructions.getOrDefault(role, "");
	}

	/**
	 * 获取夜晚阶段狼人讨论的系统提示（增强版：包含游戏历史）
	 */
	public String getWerewolfNightSystemPrompt(String werewolfName, java.util.List<String> otherWerewolves,
			java.util.List<String> alivePlayers, String gameHistory) {
		String basePrompt = String.format("""
				你是狼人 %s，正在与其他狼人（%s）讨论今晚击杀目标。
				
				当前存活玩家：%s
				""", werewolfName, String.join(", ", otherWerewolves), String.join(", ", alivePlayers));
		
		// 如果有游戏历史，添加到Prompt中
		if (gameHistory != null && !gameHistory.isEmpty()) {
			basePrompt += "\n\n游戏历史信息：\n" + gameHistory + "\n";
		}
		
		basePrompt += """
				
				请分析局势并提出你的击杀建议：
				1. 推荐击杀的目标玩家及理由
				2. 对其他狼人建议的看法
				3. 明天白天如何配合发言的策略
				
				输出格式（JSON）：
				\\{
					"targetPlayer": "推荐击杀的玩家名称",
					"reason": "选择理由和策略分析"
				\\}
				""";
		
		return basePrompt;
	}
	
	/**
	 * 获取夜晚阶段狼人讨论的系统提示（兼容旧版本）
	 */
	public String getWerewolfNightSystemPrompt(String werewolfName, java.util.List<String> otherWerewolves,
			java.util.List<String> alivePlayers) {
		return getWerewolfNightSystemPrompt(werewolfName, otherWerewolves, alivePlayers, null);
	}

	/**
	 * 获取预言家查验的系统提示
	 */
	public String getSeerCheckSystemPrompt(java.util.List<String> alivePlayers,
			java.util.Map<String, Boolean> checkHistory) {
		StringBuilder historyStr = new StringBuilder("查验历史：\n");
		checkHistory.forEach((player, result) -> historyStr.append(String.format("- %s: %s\n", player,
				result ? "狼人" : "好人")));

		return String.format("""
				你是预言家，现在是夜晚阶段，你可以查验一名玩家的身份。
				
				当前存活玩家：%s
				%s
				
				请选择今晚要查验的玩家，优先考虑：
				1. 发言可疑的玩家
				2. 关键位置的玩家
				3. 尚未查验过的玩家
				
				输出格式（JSON）：
				\\{
					"checkedPlayer": "要查验的玩家名称",
					"reason": "选择理由"
				\\}
				""", String.join(", ", alivePlayers), historyStr.toString());
	}

	/**
	 * 获取女巫行动的系统提示
	 */
	public String getWitchActionSystemPrompt(String killedPlayer, boolean hasAntidote, boolean hasPoison,
			java.util.List<String> alivePlayers) {
		return String.format("""
				你是女巫，现在是夜晚阶段。
				
				今晚被狼人杀害的玩家：%s
				你还有解药：%s
				你还有毒药：%s
				当前存活玩家：%s
				
				请决定是否使用药水：
				1. 是否使用解药救治被杀玩家
				2. 是否使用毒药毒杀某个玩家
				
				注意：解药和毒药不能在同一夜晚使用
				
				输出格式（JSON）：
				\\{
					"useAntidote": true/false,
					"savedPlayer": "被救的玩家名称（如果使用解药）",
					"usePoison": true/false,
					"poisonedPlayer": "被毒的玩家名称（如果使用毒药）",
					"reason": "决策理由"
				\\}
				""", killedPlayer != null ? killedPlayer : "无", hasAntidote ? "是" : "否", hasPoison ? "是" : "否",
				String.join(", ", alivePlayers));
	}

	/**
	 * 获取白天发言的系统提示
	 */
	public String getDayDiscussionSystemPrompt(String playerName, Role role, String nightInfo,
			java.util.Map<String, String> previousSpeeches, int round) {
		StringBuilder context = new StringBuilder();
		context.append(String.format("你是 %s，角色：%s\n\n", playerName, role.getDisplayName()));
		context.append(String.format("当前是第 %d 回合的白天讨论阶段。\n\n", round));

		if (nightInfo != null && !nightInfo.isEmpty()) {
			context.append("昨晚情况：\n").append(nightInfo).append("\n\n");
		}

		if (previousSpeeches != null && !previousSpeeches.isEmpty()) {
			context.append("之前的玩家发言：\n");
			previousSpeeches.forEach((player, speech) -> context.append(String.format("- %s: %s\n", player, speech)));
			context.append("\n");
		}

		context.append("""
				请根据你的角色身份和已知信息进行发言：
				1. 分析昨晚发生的事件
				2. 表达你对其他玩家的看法
				3. 提出你认为可疑的玩家
				4. 根据角色策略决定是否跳身份
				
				输出格式（JSON）：
				\\{
					"speech": "你的发言内容（200-500字）",
					"suspectedPlayers": ["可疑玩家1", "可疑玩家2"]
				\\}
				""");

		return context.toString();
	}

}
