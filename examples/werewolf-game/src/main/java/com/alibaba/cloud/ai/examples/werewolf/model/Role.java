package com.alibaba.cloud.ai.examples.werewolf.model;

/**
 * 狼人杀角色枚举
 */
public enum Role {
	/**
	 * 狼人 - 狼人阵营
	 */
	WEREWOLF("狼人", "werewolf", Camp.WEREWOLF),

	/**
	 * 村民 - 好人阵营
	 */
	VILLAGER("村民", "villager", Camp.VILLAGER),

	/**
	 * 预言家 - 好人阵营
	 */
	SEER("预言家", "seer", Camp.VILLAGER),

	/**
	 * 女巫 - 好人阵营
	 */
	WITCH("女巫", "witch", Camp.VILLAGER),

	/**
	 * 猎人 - 好人阵营
	 */
	HUNTER("猎人", "hunter", Camp.VILLAGER);

	private final String displayName;

	private final String code;

	private final Camp camp;

	Role(String displayName, String code, Camp camp) {
		this.displayName = displayName;
		this.code = code;
		this.camp = camp;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getCode() {
		return code;
	}

	public Camp getCamp() {
		return camp;
	}

	public boolean isWerewolf() {
		return this.camp == Camp.WEREWOLF;
	}

	public boolean isVillager() {
		return this.camp == Camp.VILLAGER;
	}

	/**
	 * 阵营枚举
	 */
	public enum Camp {
		/**
		 * 狼人阵营
		 */
		WEREWOLF("狼人阵营"),

		/**
		 * 好人阵营
		 */
		VILLAGER("好人阵营");

		private final String displayName;

		Camp(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return displayName;
		}
	}

}
