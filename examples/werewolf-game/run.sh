#!/bin/bash

# 加载 .env 文件中的环境变量
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# 设置 PATH
export PATH=$JAVA_HOME/bin:$PATH

# 运行 Maven 命令
mvn spring-boot:run
