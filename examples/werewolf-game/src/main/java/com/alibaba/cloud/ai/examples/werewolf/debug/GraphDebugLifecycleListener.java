package com.alibaba.cloud.ai.examples.werewolf.debug;

import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 图执行调试监听器
 * 用于跟踪和调试图执行过程中的各个节点和状态变化
 */
@Slf4j
public class GraphDebugLifecycleListener implements GraphLifecycleListener {

	private static final String INDENT = "  ";

	@Override
	public void onStart(String nodeId, Map<String, Object> state, RunnableConfig config) {
		log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
		log.info("🚀 [GRAPH START] 节点: {}", nodeId);
		log.info("{}线程ID: {}", INDENT, config.threadId().orElse("N/A"));
		logState(nodeId, state, "初始状态");
		log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
	}

	@Override
	public void before(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
		log.info("▶️  [NODE BEFORE] 节点: {} | 时间戳: {}", nodeId, curTime);
		logState(nodeId, state, "执行前状态");
	}

	@Override
	public void after(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
		log.info("◀️  [NODE AFTER] 节点: {} | 时间戳: {}", nodeId, curTime);
		logState(nodeId, state, "执行后状态");
	}

	@Override
	public void onError(String nodeId, Map<String, Object> state, Throwable ex, RunnableConfig config) {
		log.error("❌ [GRAPH ERROR] 节点: {}", nodeId);
		log.error("{}异常类型: {}", INDENT, ex.getClass().getName());
		log.error("{}异常消息: {}", INDENT, ex.getMessage());
		logState(nodeId, state, "错误时状态");
		if (log.isDebugEnabled()) {
			log.error("{}异常堆栈:", INDENT, ex);
		}
		log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
	}

	@Override
	public void onComplete(String nodeId, Map<String, Object> state, RunnableConfig config) {
		log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
		log.info("✅ [GRAPH COMPLETE] 节点: {}", nodeId);
		logState(nodeId, state, "最终状态");
		log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
	}

	private void logState(String nodeId, Map<String, Object> state, String label) {
		if (state == null || state.isEmpty()) {
			log.info("{}[{}] {}: (空)", INDENT, nodeId, label);
			return;
		}

		log.info("{}[{}] {} (共 {} 个键):", INDENT, nodeId, label, state.size());
		
		// 记录关键状态信息
		state.forEach((key, value) -> {
			String valueStr;
			if (value == null) {
				valueStr = "null";
			} else if (value instanceof String) {
				String str = (String) value;
				// 如果字符串太长，截断
				if (str.length() > 200) {
					valueStr = str.substring(0, 200) + "... (截断)";
				} else {
					valueStr = str;
				}
			} else if (value instanceof java.util.List) {
				java.util.List<?> list = (java.util.List<?>) value;
				valueStr = String.format("List[%d]", list.size());
			} else if (value instanceof java.util.Map) {
				java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
				valueStr = String.format("Map[%d keys]", map.size());
			} else {
				valueStr = value.toString();
				if (valueStr.length() > 200) {
					valueStr = valueStr.substring(0, 200) + "... (截断)";
				}
			}
			log.info("{}{}  {} = {}", INDENT, INDENT, key, valueStr);
		});
	}
}

