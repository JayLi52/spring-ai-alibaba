# 本地开发指南

## 问题说明

`werewolf-game` 项目通过 BOM (Bill of Materials) 引用远程依赖，这会导致本地修改的代码无法被正确引用。

## 解决方案

### 方案 1：使用 Maven install（推荐）

这是最简单的方法，将本地修改的模块安装到本地 Maven 仓库。

#### 步骤：

1. **在项目根目录安装所有模块到本地仓库**：
   ```bash
   cd /Users/terry/work/spring-ai-alibaba
   mvn clean install -DskipTests
   ```

2. **如果只想安装特定模块**：
   ```bash
   # 安装 graph-core
   cd spring-ai-alibaba-graph-core
   mvn clean install -DskipTests
   
   # 安装 agent-framework
   cd ../spring-ai-alibaba-agent-framework
   mvn clean install -DskipTests
   ```

3. **在 werewolf-game 中使用**：
   ```bash
   cd examples/werewolf-game
   mvn clean compile
   ```

#### 优点：
- 简单直接
- 不需要修改 pom.xml
- 本地仓库会优先使用本地安装的版本

#### 注意事项：
- 每次修改代码后需要重新执行 `mvn install`
- 确保版本号与 BOM 中的版本号一致（当前是 `1.1.0.0-M4`）

---

### 方案 2：使用相对路径依赖（多模块项目）

如果 `werewolf-game` 是主项目的子模块，可以使用相对路径依赖。

#### 步骤：

1. **修改根 pom.xml**，将 `werewolf-game` 添加为模块：
   ```xml
   <modules>
       <!-- 现有模块 -->
       <module>spring-ai-alibaba-bom</module>
       <module>spring-ai-alibaba-graph-core</module>
       <module>spring-ai-alibaba-agent-framework</module>
       <!-- 添加 examples -->
       <module>examples/werewolf-game</module>
   </modules>
   ```

2. **修改 werewolf-game/pom.xml**，添加父项目引用：
   ```xml
   <parent>
       <groupId>com.alibaba.cloud.ai</groupId>
       <artifactId>spring-ai-alibaba</artifactId>
       <version>1.1.0.0-M4</version>
       <relativePath>../../pom.xml</relativePath>
   </parent>
   ```

3. **直接使用本地模块**（不需要版本号）：
   ```xml
   <dependency>
       <groupId>com.alibaba.cloud.ai</groupId>
       <artifactId>spring-ai-alibaba-agent-framework</artifactId>
       <!-- 不需要版本号，从父 POM 继承 -->
   </dependency>
   ```

#### 优点：
- 自动使用本地代码
- 不需要手动 install
- 适合持续开发

#### 缺点：
- 需要修改项目结构
- 如果 examples 不想作为主项目的一部分，不适用

---

### 方案 3：使用 Maven 的 `-U` 参数强制更新

如果本地仓库中有旧版本，可以使用 `-U` 参数强制更新：

```bash
cd examples/werewolf-game
mvn clean compile -U
```

---

## 验证本地代码是否生效

### 方法 1：检查依赖树

```bash
cd examples/werewolf-game
mvn dependency:tree | grep spring-ai-alibaba
```

查看输出中的版本号和路径，确认使用的是本地安装的版本。

### 方法 2：添加调试日志

在代码中添加日志，输出类的加载路径：

```java
log.info("GraphDebugLifecycleListener 类路径: {}", 
    GraphDebugLifecycleListener.class.getProtectionDomain()
        .getCodeSource().getLocation());
```

### 方法 3：修改代码并验证

1. 在本地模块中添加一个明显的日志输出
2. 重新 install 该模块
3. 运行 werewolf-game
4. 查看日志确认修改生效

---

## 常见问题

### Q: 为什么修改了代码但没生效？

A: 可能的原因：
1. 没有执行 `mvn install` 安装到本地仓库
2. IDE 缓存了旧的 class 文件，需要重新编译
3. 使用了错误的版本号

### Q: 如何快速切换本地/远程依赖？

A: 可以使用 Maven Profile：

```xml
<profiles>
    <profile>
        <id>local-dev</id>
        <activation>
            <property>
                <name>use.local</name>
                <value>true</value>
            </property>
        </activation>
        <dependencies>
            <dependency>
                <groupId>com.alibaba.cloud.ai</groupId>
                <artifactId>spring-ai-alibaba-agent-framework</artifactId>
                <version>1.1.0.0-M4</version>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

然后使用：
```bash
mvn clean compile -P local-dev -Duse.local=true
```

---

## 推荐工作流程

1. **开发时**：使用方案 1（mvn install），每次修改后重新 install
2. **调试时**：使用方案 2（多模块），自动使用最新代码
3. **发布前**：切换回远程 BOM，确保与生产环境一致

