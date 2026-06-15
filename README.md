# TicketForge

[English README](README.en.md)

TicketForge 是一个模拟 ePlus、Ticket Pia、大麦等票务平台核心交易流程的高并发票务系统实验项目。当前阶段只建立可靠的工程基础、数据库模型和最小演出查询链路。

## 当前阶段功能

- Docker Compose 启动 PostgreSQL、Redis 和 Adminer。
- Spring Boot 通过 Flyway 创建核心表并写入演示数据。
- 后端提供只读演出查询 API。
- React + TypeScript 前端展示演出、票档、价格和库存。
- 后端测试和前端构建纳入 GitHub Actions。

## 为什么开发 TicketForge

真实票务系统会面对突发流量、库存一致性、幂等、支付回调、排队和用户体验之间的取舍。TicketForge 用阶段化方式逐步搭建这些能力，本阶段先把可运行、可测试、可迁移的基础打牢。

## 技术栈

- Backend: Java 21, Spring Boot 3.5.15, Maven, Spring Web, Spring Data JPA, Validation, Redis, Actuator, Flyway, PostgreSQL Driver, JUnit 5, Mockito
- Frontend: React, TypeScript, Vite, npm, CSS
- Infrastructure: PostgreSQL, Redis, Adminer, Docker Compose

## 目录结构

```text
TicketForge/
├─ backend/
├─ frontend/
├─ load-tests/
├─ docs/
├─ compose.yaml
├─ .env.example
├─ README.md
└─ README.en.md
```

## 架构说明

当前采用模块化单体，而不是微服务。PostgreSQL 是业务数据的最终持久化来源；Redis 在本阶段仅启动，后续用于排队、临时预占和幂等控制；Flyway 管理数据库版本；React 只负责用户界面。更多说明见 [docs/architecture.md](docs/architecture.md)。

## 环境要求

- Java 21
- Node.js LTS
- npm
- Docker Desktop 或兼容 Docker Compose 的运行环境
- Git

## Clash 代理说明

如果依赖下载需要代理，当前终端可设置：

```powershell
$env:HTTP_PROXY="http://127.0.0.1:7890"
$env:HTTPS_PROXY="http://127.0.0.1:7890"
```

Git 本仓库代理：

```bash
git config --local http.proxy http://127.0.0.1:7890
git config --local https.proxy http://127.0.0.1:7890
```

Maven 如未自动读取环境变量，可追加：

```text
-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890
```

## 本地启动

复制环境变量模板：

```bash
cp .env.example .env
```

启动基础设施：

```bash
docker compose up -d
docker compose ps
```

启动后端：

```bash
cd backend
./mvnw spring-boot:run
```

Windows:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

访问：

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/actuator/health
- Events API: http://localhost:8080/api/events
- Adminer: http://localhost:8081

## Adminer 登录方式

- System: PostgreSQL
- Server: `postgres`
- Username: `ticketforge`
- Password: `ticketforge_dev`
- Database: `ticketforge`

以上账号仅用于本地开发。

## Flyway

SQL 文件位于：

```text
backend/src/main/resources/db/migration/
```

- `V1__create_core_schema.sql`: 创建用户、演出、票档、库存、订单和支付记录表。
- `V2__seed_demo_data.sql`: 创建演示用户、演出、三档票种和库存。

## API 示例

```http
GET /api/events
GET /api/events/{eventId}
GET /api/events/slug/{slug}
GET /actuator/health
```

示例：

```bash
curl http://localhost:8080/api/events
curl http://localhost:8080/api/events/1
curl http://localhost:8080/api/events/slug/ticketforge-opening-live
```

## 当前未实现功能

- 登录注册和 JWT
- 虚拟排队
- 下单
- 库存扣减
- 支付
- Redis 分布式锁和 Lua
- 消息队列
- k6 压力测试
- WebSocket 或 SSE
- 微服务
- Kubernetes
- 选座系统

## Roadmap

- Phase 2: 订单创建、库存预占和幂等键。
- Phase 3: Redis 队列、临时库存锁定和超时释放。
- Phase 4: 支付回调模拟、订单状态机和补偿流程。
- Phase 5: 压力测试、瓶颈分析和可观测性增强。

## 测试命令

后端：

```bash
cd backend
./mvnw test
```

前端：

```bash
cd frontend
npm ci
npm run build
```

## Git 提交规范

使用 Conventional Commits：

```text
feat: add event query API
fix: correct inventory mapping
docs: update local setup guide
test: add event service coverage
chore: update CI workflow
```

