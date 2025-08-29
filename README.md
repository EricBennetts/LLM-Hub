# LLM-Hub: 一个全栈社区论坛项目

[![许可证](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.x-4FC08D.svg)](https://vuejs.org/)

LLM-Hub 是一个基于 Spring Boot 和 Vue.js 构建的现代化全栈 Web 应用。它实现了一个简洁的社区论坛，用户可以注册、登录、浏览帖子，并在登录后发布自己的内容。项目采用前后端分离架构，通过 RESTful API 进行通信，并使用 JWT (JSON Web Token) 进行无状态认证。
## ✨ 功能特性

*   **用户系统**: 支持新用户注册和登录功能。
*   **JWT 认证**: 使用 Spring Security 和 JWT 实现安全的、无状态的 API 认证。
*   **帖子管理**:
    *   所有用户（包括游客）均可浏览帖子列表。
    *   已登录用户可以创建新帖子。
*   **前后端分离**: 后端提供 RESTful API，前端通过 Vue.js 和 Axios 动态消费数据。

## 🛠️ 技术栈

#### 后端 (Backend)
*   **核心框架**: Spring Boot 3.4.8
*   **安全框架**: Spring Security
*   **数据库持久化**: MyBatis
*   **认证方案**: JSON Web Token (JWT)
*   **数据库**: MySQL
*   **构建工具**: Maven

#### 前端 (Frontend)
*   **JavaScript 框架**: Vue.js 3
*   **HTTP 客户端**: Axios
*   **语言**: HTML, CSS

## 🚀 快速开始

请确保你已经安装了以下环境，这是运行本项目的先决条件。

### 1. 先决条件
在开始之前，请确保你的系统已安装以下软件：
*   **JDK 17** 或更高版本
*   **Maven 3.8** 或更高版本
*   **MySQL 8.0** 或更高版本
*   一个你喜欢的 IDE (例如：IntelliJ IDEA, VS Code, Eclipse)

### 2. 后端设置

1.  **克隆项目**
    ```bash
    git clone https://github.com/EricBennetts/LLM-Hub.git
    cd LLM-Hub
    ```
2.  **创建数据库和表**
    *   登录到你的 MySQL 客户端。
    *   创建一个新的数据库（例如 `llm_hub`）。
      ```sql
      CREATE DATABASE llm_hub;
      USE llm_hub;
      ```
    *   执行以下 SQL 语句来创建 `user` 和 `post` 表：
      ```sql
      -- 创建用户表
      CREATE TABLE user (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          username VARCHAR(255) NOT NULL UNIQUE,
          password VARCHAR(255) NOT NULL,
          email VARCHAR(255) NOT NULL UNIQUE,
          avatar_url VARCHAR(255),
          bio TEXT,
          role VARCHAR(50) DEFAULT 'user',
          create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
      );

      -- 创建帖子表
      CREATE TABLE post (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          title VARCHAR(255) NOT NULL,
          content TEXT NOT NULL,
          user_id BIGINT NOT NULL,
          create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          FOREIGN KEY (user_id) REFERENCES user(id)
      );
      ```

### 安装与配置

1.  **克隆项目到本地**
    ```bash
    git clone https://github.com/[你的GitHub用户名]/[你的仓库名].git
    cd [你的仓库名]
    ```

2.  **创建数据库和表**
    *   登录到你的 MySQL 客户端。
    *   创建一个新的数据库（例如 `llm_hub`）。
      ```sql
      CREATE DATABASE llm_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
      USE llm_hub;
      ```
    *   执行以下 SQL 语句来创建 `user` 和 `post` 表：
      ```sql
      -- 创建用户表
      CREATE TABLE user (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          username VARCHAR(50) NOT NULL UNIQUE,
          password VARCHAR(100) NOT NULL,
          email VARCHAR(100) NOT NULL UNIQUE,
          avatar_url VARCHAR(255),
          bio varchar(255),
          role VARCHAR(20) DEFAULT 'user',
          create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
      );

      -- 创建帖子表
      CREATE TABLE post (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          title VARCHAR(100) NOT NULL,
          content TEXT NOT NULL,
          user_id BIGINT NOT NULL,
          create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
      );
    -- 创建post表中user_id的索引
      create index idx_user_id
      on post (user_id)
      comment '用于快速查找用户帖子的索引';
      ```
       
3.  **配置应用程序**
    *   在 `src/main/resources/` 目录下，找到名为 `application.properties.example` 的文件。
    *   根据你的本地数据库环境修改其中以 `{}` 包围的占位符。


4.  **运行后端服务**
    *   编译项目并运行启动类：LlmHubApplication.java
    *   当看到 Spring Boot 的启动日志时，表示后端服务已在 `http://localhost:8080` 上成功运行。

### 3. 前端访问

后端启动后，前端页面已经可以直接访问。

*   打开你的浏览器 (如 Chrome, Firefox)。
*   访问 `http://localhost:8080`。

你现在应该能看到 LLM-Hub 的主页了！你可以尝试注册一个新用户，登录，然后发布一个帖子。

## 📖 API 端点文档

本项目提供以下 RESTful API 端点：

| HTTP 方法 | 路径                 | 描述                 | 是否需要认证 |
| :-------- | :------------------- | :------------------- | :----------- |
| `POST`    | `/users/register`    | 注册一个新用户       | 否           |
| `POST`    | `/users/login`       | 用户登录，返回 JWT   | 否           |
| `GET`     | `/posts`             | 获取所有帖子的列表   | 否           |
| `GET`     | `/posts/{id}`        | 获取单个帖子的详情   | 否           |
| `POST`    | `/posts`             | 创建一篇新的帖子     | **是**       |

> **注意**: 需要认证的接口必须在 HTTP 请求头中包含 `Authorization: Bearer <your-jwt-token>`。


## 📜 许可证

本项目采用 [MIT](https://opensource.org/licenses/MIT) 许可证。详情请见 `LICENSE` 文件。

## 📧 联系方式

Max Peng - maxpeng688@gmail.com

