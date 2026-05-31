# LLM-Hub

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.x-red.svg)](https://mybatis.org/)

LLM-Hub 是一个基于 Spring Boot 的社区论坛项目。项目从基础的发帖、评论、点赞、通知功能出发，进一步引入 Redis、RabbitMQ、WebSocket 和大模型能力，实现了内容自动审核、AI 帖子总结、异步通知、消息补偿重试等更贴近真实业务系统的能力。

当前项目采用前后端分离思路组织接口，后端提供 RESTful API，前端静态页面位于 `src/main/resources/static`，可以随 Spring Boot 服务一起访问。

## 功能特性

- **用户系统**：支持注册、登录、登出，登录后返回 JWT。
- **无状态认证**：使用 Spring Security + JWT 保护接口，登出后将 token 写入 Redis 黑名单。
- **帖子管理**：支持帖子列表、详情、发布、编辑、删除和当前用户帖子查询。
- **内容审核**：帖子发布或编辑后先进入待审核状态，通过 RabbitMQ 异步触发 AI 审核。
- **审核日志**：记录模型决策、风险等级、原因、工具调用、原始响应和异常信息，方便追踪。
- **评论系统**：支持帖子评论的查询、发布、编辑和删除。
- **点赞系统**：支持点赞、取消点赞、点赞状态和点赞数查询。
- **实时通知**：评论、AI 总结、审核结果通过 WebSocket 推送给用户。
- **AI 帖子总结**：用户可请求 AI 总结帖子内容，结果异步生成并缓存。
- **缓存与限流保护**：Redis 缓存帖子列表、帖子详情和 AI 总结，并通过 AOP 防止重复提交。
- **消息可靠性**：注册欢迎邮件使用本地消息表、RabbitMQ confirm callback 和定时补偿重试。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.4.8
- Spring Web
- Spring Security
- Spring AMQP
- Spring WebSocket
- Spring Cache
- Spring Scheduling
- Spring AOP
- MyBatis
- MySQL
- Redis
- RabbitMQ
- Resilience4j CircuitBreaker
- Java JWT
- Maven

### 前端

- HTML
- CSS
- JavaScript
- Axios
- SockJS / STOMP

### AI 能力

- DeepSeek Chat Completion API
- Function calling 风格的审核工具调用
- 平台规则工具
- 用户审核上下文工具

## 整体架构

```text
Browser
  |
  | REST API / WebSocket
  v
Spring Boot Application
  |
  |-- Controller: 接收 HTTP 请求，做基础参数校验
  |-- Security Filter: 解析 JWT，写入 SecurityContext
  |-- Service: 处理用户、帖子、评论、点赞、通知、AI 业务
  |-- Mapper: 通过 MyBatis 访问 MySQL
  |
  |-- Redis: 缓存、JWT 黑名单、防重复提交、MQ 幂等
  |-- RabbitMQ: 邮件、评论通知、AI 总结、内容审核异步任务
  |-- WebSocket: 向用户推送通知、审核结果和 AI 总结
  |-- DeepSeek API: 内容审核和帖子总结
```

项目整体是分层单体架构：Controller 负责接口入口，Service 负责业务编排，Mapper 负责数据访问；Redis、RabbitMQ 和 WebSocket 作为基础设施增强性能、解耦和实时性。

## 核心业务流程

### 发帖与内容审核

1. 用户调用 `POST /posts` 提交帖子。
2. 后端将帖子写入 MySQL，状态设置为 `PENDING_REVIEW`。
3. Controller 向 `moderation.queue` 发送审核任务。
4. `ModerationListener` 消费任务，调用 `ModerationAgent`。
5. `ModerationAgent` 调用平台规则工具和用户上下文工具，并请求大模型给出审核结果。
6. 审核结果映射为 `PUBLISHED`、`REJECTED` 或 `NEEDS_HUMAN_REVIEW`。
7. 后端写入审核日志，并通过 WebSocket 推送给用户。

为了避免旧审核任务覆盖新内容，审核完成时会校验帖子仍处于待审核状态，并且标题、内容与任务快照一致。

### AI 帖子总结

1. 用户调用 `POST /posts/{id}/ai-summary`。
2. 后端先检查 Redis 是否已有缓存结果。
3. 未命中缓存时，检查队列长度，避免高峰期继续堆积任务。
4. 后端向 `ai.summary.queue` 发送异步任务。
5. `AiTaskListener` 调用 AI 生成 50 字以内中文总结。
6. 成功结果缓存到 Redis，并通过 WebSocket 推送给用户。
7. AI 服务异常时由 Resilience4j 熔断降级。

### 评论通知

1. 用户在帖子下发表评论。
2. 评论写入 MySQL。
3. 如果评论者不是帖子作者，发送消息到 `new.comment.queue`。
4. `NotificationListener` 创建通知记录。
5. 通知通过 WebSocket 推送给帖子作者。

### 注册欢迎邮件

1. 用户注册成功后，后端先写入本地消息表。
2. RabbitMQ 发送欢迎邮件消息，并携带 `CorrelationData`。
3. Confirm callback 根据 RabbitMQ ack 更新消息状态。
4. `EmailListener` 消费消息并发送邮件。
5. 消费端使用 Redis 幂等 key 避免重复发送。
6. 定时任务每 30 秒扫描失败或超时消息，最多重试 3 次。

## 快速开始

### 环境要求

- JDK 17 或更高版本
- Maven 3.8 或更高版本
- MySQL 8.0 或更高版本
- Redis
- RabbitMQ
- 可用的 SMTP 邮箱服务
- DeepSeek API Key

### 获取项目

```bash
git clone https://github.com/EricBennetts/LLM-Hub.git
cd LLM-Hub
```

### 数据库准备

创建数据库：

```sql
CREATE DATABASE llm_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

项目当前使用 MyBatis 注解 SQL，没有内置自动建表迁移脚本。需要根据 `src/main/java/com/example/pojo` 中的实体和 `src/main/java/com/example/mapper` 中的 SQL 创建对应表，主要包括：

- `user`
- `post`
- `comment`
- `post_like`
- `notification`
- `message_log`
- `moderation_log`

### 配置应用

复制示例配置并修改本地环境参数：

```bash
cp src/main/resources/application.properties.example.txt src/main/resources/application.properties
```

重点配置项：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/llm_hub
spring.datasource.username=your_database_username
spring.datasource.password=your_database_password

spring.data.redis.host=localhost
spring.data.redis.port=6379

spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=your_rabbitmq_username
spring.rabbitmq.password=your_rabbitmq_password

spring.mail.host=smtp.your-email-provider.com
spring.mail.username=your-email@domain.com
spring.mail.password=your-email-password-or-app-password

ai.deepseek.api-key=your_deepseek_api_key
ai.deepseek.model=deepseek-chat
```

如果暂时不想启用 Redis 缓存，可以保持：

```properties
spring.cache.type=none
```

如果要启用 Redis 缓存，改为：

```properties
spring.cache.type=redis
```

### 启动服务

```bash
mvn spring-boot:run
```

服务启动后访问：

```text
http://localhost:8080
```

健康检查：

```text
http://localhost:8080/actuator/health
```

## API 端点

需要登录的接口请在请求头中携带：

```http
Authorization: Bearer <your-jwt-token>
```

### 用户

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| `POST` | `/users/register` | 注册用户 | 否 |
| `POST` | `/users/login` | 用户登录，返回 JWT | 否 |
| `POST` | `/users/logout` | 登出并拉黑当前 token | 是 |

### 帖子

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| `GET` | `/posts` | 获取已发布帖子列表 | 否 |
| `GET` | `/posts/{id}` | 获取已发布帖子详情 | 否 |
| `POST` | `/posts` | 提交帖子并进入审核流程 | 是 |
| `PUT` | `/posts/{id}` | 修改自己的帖子并重新审核 | 是 |
| `DELETE` | `/posts/{id}` | 删除自己的帖子 | 是 |
| `GET` | `/user/posts` | 获取当前用户发布的帖子 | 是 |
| `POST` | `/posts/{id}/ai-summary` | 请求 AI 总结帖子 | 是 |
| `GET` | `/posts/{id}/moderation-logs` | 查看自己帖子的审核日志 | 是 |

### 评论

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| `GET` | `/posts/{postId}/comments` | 获取帖子评论 | 否 |
| `POST` | `/posts/{postId}/comments` | 发表评论 | 是 |
| `PUT` | `/comments/{id}` | 修改自己的评论 | 是 |
| `DELETE` | `/comments/{id}` | 删除自己的评论 | 是 |

### 点赞

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| `POST` | `/posts/{postId}/like` | 点赞帖子 | 是 |
| `DELETE` | `/posts/{postId}/like` | 取消点赞 | 是 |
| `GET` | `/posts/{postId}/like/status` | 获取点赞状态和点赞数 | 否 |

### 通知

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| `GET` | `/api/notifications` | 获取当前用户通知 | 是 |
| `POST` | `/api/notifications/{id}/read` | 标记通知已读 | 是 |
| `GET` | `/api/notifications/unread-count` | 获取未读通知数量 | 是 |

## WebSocket

WebSocket 端点：

```text
/ws
```

消息代理前缀：

```text
/topic
/user
```

项目当前使用的用户级推送主题包括：

```text
/topic/user/{userId}/notifications
/topic/user/{userId}/ai
/topic/user/{userId}/moderation
```

客户端连接 STOMP 时可以在 header 中携带 JWT：

```text
Authorization: Bearer <your-jwt-token>
```

## 项目亮点

- **清晰的分层结构**：Controller、Service、Mapper、配置、监听器和 Agent 模块边界明确。
- **异步解耦**：邮件、通知、AI 总结、内容审核都通过 RabbitMQ 解耦。
- **可靠消息设计**：注册邮件链路包含本地消息表、confirm callback、定时补偿和消费幂等。
- **AI Agent 化审核**：审核不仅直接调用模型，还通过工具获取平台规则和用户历史上下文。
- **实时反馈**：异步任务完成后通过 WebSocket 推送，避免前端轮询。
- **缓存与降级**：Redis 缓存热点数据，Resilience4j 为 AI 总结提供熔断降级。
- **并发安全意识**：审核任务使用内容快照校验，避免旧任务覆盖新内容。

## 后续优化方向

- 引入 Flyway 或 Liquibase 管理数据库迁移脚本。
- 将明文密码升级为 BCrypt 哈希存储。
- 为 RabbitMQ 配置死信队列，完善失败消息隔离。
- 将 WebSocket 用户推送改造为 Spring 原生 user destination。
- 增加 Docker Compose，一键启动 MySQL、Redis、RabbitMQ 和应用。
- 补充 OpenAPI/Swagger 文档。
- 增加更多集成测试覆盖核心异步流程。

## 许可证

本项目采用 MIT License。

## 联系方式

Max Peng - maxpeng688@gmail.com
