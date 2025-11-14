package com.alibaba.cloud.ai.examples.werewolf.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 玩家模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

	/**
	 * 玩家名称
	 */
	private String name;

	/**
	 * 角色
	 */
	private Role role;

	/**
	 * 是否存活
	 */
	@Builder.Default
	private boolean alive = true;

	/**
	 * 是否为人类玩家
	 */
	@Builder.Default
	private boolean human = false;

	/**
	 * 获取玩家身份描述（用于 Agent prompt）
	 */
	public String getIdentityDescription() {
		return String.format("玩家 %s，角色: %s，阵营: %s", name, role.getDisplayName(), role.getCamp().getDisplayName());
	}

	/**
	 * 是否为狼人阵营
	 */
	public boolean isWerewolf() {
		return role.isWerewolf();
	}

	/**
	 * 是否为好人阵营
	 */
	public boolean isVillager() {
		return role.isVillager();
	}

}
