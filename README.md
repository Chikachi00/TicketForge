# TicketForge

[English README](README.en.md)

TicketForge 是一个模拟 ePlus、Ticket Pia、大麦等票务平台核心交易流程的高并发票务系统实验项目。当前阶段是 **Phase 3: Simulated Payment, Idempotent Callback and Order State Machine**。

本阶段仍采用 React + TypeScript、Spring Boot Java 21、Windows 本地 PostgreSQL。不要使用 Docker、Redis、消息队列、真实第三方支付、JWT、虚拟排队或微服务。

## 当前功能

- 演出与票档查询。
- PostgreSQL + Flyway 管理数据库版本。
- 创建 `PENDING_PAYMENT` 订单并原子预占库存。
- 订单幂等提交：`Idempotency-Key`。
- 主动取消订单并释放库存。
- 待支付订单超时取消。
- 模拟支付会话创建。
- HMAC-SHA256 支付回调验签。
- 支付成功后库存从 `reserved_stock` 转为 `sold_stock`。
- 支付失败后订单保持待支付，库存继续预占，可在过期前重新发起支付。
- 重复成功回调幂等处理。
- React 页面支持预占、取消、模拟支付成功/失败、订单状态刷新。

## 技术栈

- Backend: Java 21, Spring Boot 3.5.15, Maven Wrapper, Spring Web, Spring Data JPA, Validation, Actuator, Flyway, PostgreSQL Driver, JUnit 5, Mockito
- Frontend: React, TypeScript, Vite, npm, CSS
- Database: Windows 本地 PostgreSQL
- CI: GitHub Actions + PostgreSQL 17 service

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

```text
Host: localhost
Port: 5432
Database: ticketforge
Test database: ticketforge_test
Username: ticketforge
Password: ticketforge_dev
```

Clash 代理示例：

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
- `V3__prepare_order_reservation.sql`: 订单预占、取消和过期所需结构。
- `V4__prepare_simulated_payment.sql`: 模拟支付字段、金额/币种约束、回调幂等索引、每订单一个 pending 支付索引。

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

## 模拟身份

当前没有登录系统。前后端使用请求头模拟当前用户：

```http
X-User-Email: user@ticketforge.local
```

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
POST /api/orders/{orderNumber}/cancel
```

支付：

```http
POST /api/payments/orders/{orderNumber}
GET /api/payments/{paymentTransactionId}
POST /api/payments/callback
POST /api/payment-simulator/{paymentTransactionId}/success
POST /api/payment-simulator/{paymentTransactionId}/failure
```

模拟器端点仅在非 `prod` profile 注册。

## 支付会话

创建支付会话：

```powershell
$paymentHeaders = @{
  "X-User-Email" = "user@ticketforge.local"
  "Idempotency-Key" = [guid]::NewGuid().ToString()
}

$payment = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/payments/orders/$($order.orderNumber)" `
  -Headers $paymentHeaders
```

规则：

- 订单必须属于当前用户。
- 订单状态必须为 `PENDING_PAYMENT`。
- 订单不能过期。
- 金额只从数据库订单读取，客户端不能传金额。
- 同一订单已有 `PENDING` 支付记录时返回原支付会话。

## HMAC 回调

配置：

```yaml
ticketforge:
  payment:
    callback-secret: ${TICKETFORGE_PAYMENT_CALLBACK_SECRET:ticketforge-local-dev-secret}
```

签名字符串：

```text
providerEventId|paymentTransactionId|orderNumber|status|amount|currency|occurredAt
```

金额固定为两位小数，例如 `1280.00`。时间使用 UTC ISO 8601，例如 `2026-06-16T10:02:00Z`。签名算法为 HMAC-SHA256，UTF-8 编码，服务端使用常量时间比较。

## 状态机和库存

允许：

```text
PENDING_PAYMENT -> PAID
PENDING_PAYMENT -> CANCELLED
```

预留：

```text
PAID -> REFUNDED
```

禁止：

```text
CANCELLED -> PAID
PAID -> CANCELLED
CANCELLED -> PENDING_PAYMENT
```

支付成功事务顺序：

1. 锁定 `payment_records`。
2. 锁定 `ticket_orders`。
3. 原子更新 `ticket_inventory`，将 reserved 转为 sold。
4. 设置订单 `PAID/paid_at`。
5. 设置支付 `SUCCESS/processed_at/provider_event_id`。

核心库存更新：

```sql
UPDATE ticket_inventory
SET reserved_stock = reserved_stock - :quantity,
    sold_stock = sold_stock + :quantity,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE ticket_tier_id = :ticketTierId
  AND reserved_stock >= :quantity;
```

支付失败只设置支付记录为 `FAILED`，订单仍为 `PENDING_PAYMENT`，库存仍在 `reserved_stock`。

重复成功回调返回 `idempotentReplay=true`，不会再次转移库存。支付与取消、支付与过期并发时，只允许一个最终状态和一次库存转移。

库存守恒：

```text
available_stock + reserved_stock + sold_stock = total_stock
```

## 测试

默认后端测试不依赖 Docker、Redis 或外部 PostgreSQL：

```powershell
cd backend
.\mvnw.cmd test
```

真实 PostgreSQL 集成测试：

```powershell
cd backend
.\mvnw.cmd verify -Pintegration
```

集成测试使用 `ticketforge_test`，覆盖并发预占、幂等订单、重复支付回调、支付与取消竞态、支付与过期竞态。

前端：

```powershell
cd frontend
npm ci
npm run build
```

## PostgreSQL 检查

Flyway：

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

支付与订单：

```sql
SELECT o.order_number, o.status AS order_status,
       p.payment_transaction_id, p.status AS payment_status,
       p.provider_event_id, p.amount, p.currency, p.processed_at
FROM ticket_orders o
LEFT JOIN payment_records p ON p.order_id = o.id
ORDER BY o.created_at DESC;
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

## 当前未实现

- 注册、登录、JWT
- 真实支付网关、退款
- Redis 业务逻辑、Redis 锁、Lua
- 虚拟排队
- 消息队列
- k6 压力测试
- WebSocket/SSE
- 微服务、Kubernetes
- 选座系统

## Git 提交规范

使用 Conventional Commits：

```text
feat: add simulated payment callback handling
fix: preserve inventory invariant during payment race
docs: update phase 3 payment workflow
test: add payment callback concurrency tests
```
