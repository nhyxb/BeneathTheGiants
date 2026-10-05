# AGENTS.md

## 项目概况
- 项目名：NeoForge 1.21.1 Mod 开发环境
- 日期：2026-10-04
- 技术栈：Java 21、Minecraft 1.21.1、NeoForge、ModDevGradle
- 包管理/构建：Gradle Wrapper（`./gradlew`）
- 主要目录：`src/main/java`、`src/main/resources`、`src/test/java`

## 项目约定
- Minecraft 1.21.1 的源代码与目标字节码使用 Java 21。
- 优先通过 `gradle.properties` 管理 mod 元信息；Gradle Wrapper 是唯一构建入口。
- 提交前至少运行 `./gradlew build`；改动运行配置时同时检查 `runClient` 或 `runServer`。
- 不提交 `build/`、`run/`、IDE 私有状态和本机工具链缓存。
- 不在仓库记录密钥、账号令牌或个人机器的绝对路径。
