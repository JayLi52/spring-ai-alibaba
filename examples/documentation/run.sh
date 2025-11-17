#!/bin/bash

# 加载环境变量
if [ -f "../../.env" ]; then
  export $(cat ../../.env | grep -v '^#' | xargs)
fi

# 设置 JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home)

echo "🚀 Starting Documentation Examples..."
echo "📌 JAVA_HOME: $JAVA_HOME"
echo "📌 DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY:0:10}..."
echo ""

# 启动应用
mvn spring-boot:run
