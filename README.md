# TicketForge

[English README](README.en.md)

TicketForge 是一个模拟 ePlus、Ticket Pia、大麦等票务平台核心交易流程的高并发票务系统实验项目。

当前阶段是 **Phase 2: PostgreSQL Inventory Reservation and Idempotent Orders**。本阶段推荐在 Windows 本地运行 PostgreSQL，不依赖 Docker、Redis、支付、虚拟排队或消息队列。

## 当前功能

- 演出和票档查询。
- PostgreSQL/Flyway 管理数据库版本。
- 创建待支付订单。
- PostgreSQL 原子条件更新实现库存预占。
- 防止库存超卖和负库存。
- `Idempotency-Key` 幂等订单提交。
- 主动取消订单并释放库存。
- 5 分钟待支付超时自动取消并释放库存。
- React 页面支持预占、订单查看、倒计时、取消和我的订单。

## 技术栈

- Backend: Java 21, Spring Boot 3.5.15, Maven Wrapper, Spring Web, Spring Data JPA, Validation, Actuator, Flyway, PostgreSQL Driver, JUnit 5, Mockito
- Frontend: React, TypeScript, Vite, npm, CSS
- Database: Windows 本地 PostgreSQL
- Optional infrastructure: `compose.yaml` 仍保留 PostgreSQL/Redis/Adminer，但 Phase 2 不要求使用 Docker

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

## 本地环境

推荐数据库：

```text
Host: localhost
Port: 5432
Database: ticketforge
Username: ticketforge
Password: ticketforge_dev
```

Phase 2 不需要启动 Redis。后端已关闭 Redis health indicator，因此 Redis 未运行时 `/actuator/health` 仍可为 `UP`。

Clash 代理：

```powershell
$env:HTTP_PROXY="http://127.0.0.1:7890"
$env:HTTPS_PROXY="http://127.0.0.1:7890"
git config --local http.proxy http://127.0.0.1:7890
git config --local https.proxy http://127.0.0.1:7890
```

## 启动后端

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Flyway 文件：

```text
backend/src/main/resources/db/migration/
```

- `V1__create_core_schema.sql`: 核心表。
- `V2__seed_demo_data.sql`: 演示用户、演出、票档和库存。
- `V3__prepare_order_reservation.sql`: 订单取消时间、幂等键非空、待支付超时索引、订单时间约束，并把演示演出开票时间移到过去。

## 启动前端

```powershell
cd frontend
npm ci
npm run dev
```

访问：

- Frontend: http://localhost:5173
- Health: http://localhost:8080/actuator/health
- Events API: http://localhost:8080/api/events

## 模拟用户身份

当前没有登录系统。前后端使用请求头模拟当前用户：

```http
X-User-Email: user@ticketforge.local
```

前端页面标记为 `Demo User`。这只是开发阶段模拟身份，后续会被真实认证替换。

## API

演出：

```http
GET /api/events
GET /api/events/{eventId}
GET /api/events/slug/{slug}
```

订单：

```http
POST /api/orders
GET /api/orders/{orderNumber}
GET /api/orders/me
GET /api/orders/me?status=PENDING_PAYMENT
POST /api/orders/{orderNumber}/cancel
```

创建订单：

```powershell
$headers = @{
  "X-User-Email" = "user@ticketforge.local"
  "Idempotency-Key" = [guid]::NewGuid().ToString()
}

$body = @{
  ticketTierId = 1
  quantity = 1
} | ConvertTo-Json

$order = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/orders" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body

$order
```

用同一组 `$headers` 和 `$body` 再提交一次，会返回同一个订单，且 `idempotentReplay = true`。

查询订单：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/orders/$($order.orderNumber)" `
  -Headers @{ "X-User-Email" = "user@ticketforge.local" }
```

取消订单：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/orders/$($order.orderNumber)/cancel" `
  -Headers @{ "X-User-Email" = "user@ticketforge.local" }
```

## 库存预占与防超卖

创建订单在一个 PostgreSQL 事务中完成：

1. 按 `X-User-Email` 锁定模拟用户行。
2. 查询 `user_id + idempotency_key` 是否已有订单。
3. 校验票档、演出状态和开票时间。
4. 使用 PostgreSQL 条件 `UPDATE` 原子预占库存。
5. 创建 `PENDING_PAYMENT` 订单。

核心库存更新：

```sql
UPDATE ticket_inventory
SET available_stock = available_stock - :quantity,
    reserved_stock = reserved_stock + :quantity,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE ticket_tier_id = :ticketTierId
  AND available_stock >= :quantity;
```

受影响行数为 `0` 时返回 `OUT_OF_STOCK`。任何时候都必须保持：

```text
available_stock >= 0
reserved_stock >= 0
sold_stock >= 0
available_stock + reserved_stock + sold_stock = total_stock
```

## 幂等语义

同一用户使用相同 `Idempotency-Key` 重复提交，只会创建一个订单并预占一次库存。应用层通过用户行 `PESSIMISTIC_WRITE` 锁串行化同一用户的重复提交，数据库唯一约束 `UNIQUE(user_id, idempotency_key)` 作为最终保护。

## 取消与超时

主动取消只允许取消 `PENDING_PAYMENT` 订单。取消时在同一事务中锁定订单、设置 `CANCELLED/cancelled_at`，并通过条件 `UPDATE` 将库存从 `reserved_stock` 释放回 `available_stock`。

待支付订单默认 5 分钟过期：

```yaml
ticketforge:
  orders:
    reservation-ttl: PT5M
    expiration-scan-delay-ms: 10000
```

调度器每批最多扫描 100 个过期待支付订单，逐个安全取消。业务代码使用注入的 `Clock`，测试可固定时间。

## 测试

默认测试不依赖 Docker、Redis 或外部数据库：

```powershell
cd backend
.\mvnw.cmd test
```

真实 PostgreSQL 集成测试使用 Maven profile：

```powershell
cd backend
.\mvnw.cmd verify -Pintegration
```

集成测试使用 `ticketforge_test` 数据库，并覆盖并发库存防超卖和并发幂等提交。GitHub Actions 会启动 PostgreSQL 17 service 并运行该 profile。

前端：

```powershell
cd frontend
npm ci
npm run build
```

## pgAdmin 检查

Flyway：

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

订单：

```sql
SELECT order_number, user_id, ticket_tier_id, quantity, unit_price,
       total_amount, status, idempotency_key, expires_at,
       cancelled_at, created_at
FROM ticket_orders
ORDER BY created_at DESC;
```

库存守恒：

```sql
SELECT tt.code, tt.total_stock, ti.available_stock, ti.reserved_stock,
       ti.sold_stock,
       ti.available_stock + ti.reserved_stock + ti.sold_stock AS calculated_total
FROM ticket_tiers tt
JOIN ticket_inventory ti ON ti.ticket_tier_id = tt.id
ORDER BY tt.code;
```

每行 `calculated_total` 必须等于 `total_stock`。

## 当前未实现

- 登录注册和 JWT
- 真实支付
- Redis 业务逻辑
- 虚拟排队
- 消息队列
- k6 压力测试
- WebSocket/SSE
- 微服务和 Kubernetes
- 选座系统

## Git 提交规范

使用 Conventional Commits：

```text
feat: add idempotent order reservation
fix: correct reservation rollback
docs: update local postgres workflow
test: add order concurrency integration tests
```
