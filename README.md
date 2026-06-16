# TicketForge

[English README](README.en.md)

TicketForge 是一个模拟 ePlus、Ticket Pia、大麦等票务平台核心交易流程的高并发票务系统实验项目。当前阶段是 **Phase 4: k6 Load Testing, Observability and PostgreSQL Baseline**。

本阶段目标是建立可复现的 PostgreSQL 单机基线、k6 正确性/负载脚本、Spring Boot/Micrometer 指标和手动触发的性能测试工作流。仍然不引入 Redis、消息队列、真实支付、JWT、虚拟排队、WebSocket、微服务、Kubernetes、Grafana Server 或 Prometheus Server。

## 当前功能

- 演出、票档、订单、模拟支付链路。
- PostgreSQL 原子库存预占、取消释放、支付成功 reserved -> sold。
- 支付回调 HMAC-SHA256 验签与重复回调幂等。
- `loadtest` profile，默认指向独立数据库 `ticketforge_loadtest`。
- 仅 `loadtest` profile 暴露 `/api/load-test/*` 管理接口。
- k6 smoke、order baseline、oversell spike、idempotency retry、payment callback replay、full journey 场景。
- Actuator 暴露 `health`、`info`、`metrics`、`prometheus`。
- 低基数业务指标，用于观察订单、库存和支付行为。

## 数据库

普通开发库：

```text
Database: ticketforge
Username: ticketforge
Password: ticketforge_dev
```

集成测试库：

```text
Database: ticketforge_test
Username: ticketforge
Password: ticketforge_dev
```

压力测试专用库：

```text
Database: ticketforge_loadtest
Username: ticketforge
Password: ticketforge_dev
```

不要在普通 `ticketforge` 数据库中运行压力测试。使用 PostgreSQL 管理员账号执行一次：

```sql
CREATE DATABASE ticketforge_loadtest
    OWNER ticketforge
    ENCODING 'UTF8';
```

不要为了本流程自动授予 `ticketforge` 用户 `CREATE DATABASE` 权限。

## 启动 loadtest 后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=loadtest"
```

`backend/src/main/resources/application-loadtest.yml` 会把数据源默认指向 `ticketforge_loadtest`，并把订单过期时间调长到 `PT30M`，避免压测中途被定时取消。

## API

业务 API：

```http
GET /api/events
GET /api/events/{eventId}
GET /api/events/slug/{slug}
POST /api/orders
GET /api/orders/{orderNumber}
GET /api/orders/me
POST /api/orders/{orderNumber}/cancel
POST /api/payments/orders/{orderNumber}
GET /api/payments/{paymentTransactionId}
POST /api/payments/callback
POST /api/payment-simulator/{paymentTransactionId}/success
POST /api/payment-simulator/{paymentTransactionId}/failure
```

loadtest 管理 API 仅在 `loadtest` profile 可用，并要求：

```http
X-Load-Test-Secret: ticketforge-local-loadtest-secret
```

```http
GET /api/load-test/profile
POST /api/load-test/reset
GET /api/load-test/state?eventSlug=ticketforge-load-test-live
```

`reset` 只影响专用 load-test 演出、订单、支付记录和 `loadtest-user-*@ticketforge.local` 用户，不删除普通 demo 数据。

## Metrics

Actuator：

```text
/actuator/health
/actuator/info
/actuator/metrics
/actuator/prometheus
```

不会暴露 `/env`、`/configprops`、`/beans`、`/heapdump`。

业务指标：

```text
ticketforge_orders_created_total
ticketforge_orders_idempotent_replay_total
ticketforge_orders_rejected_total
ticketforge_inventory_reserved_total
ticketforge_inventory_released_total
ticketforge_payments_success_total
ticketforge_payments_failed_total
ticketforge_payment_callback_replay_total
ticketforge_order_reservation_duration
ticketforge_payment_callback_duration
```

指标标签只使用低基数字段，如 `result`、`status`、`reason`、`ticket_tier_code`。禁止把 `orderNumber`、`paymentTransactionId`、`email`、`idempotencyKey`、`providerEventId`、`userId` 放进 metrics tag。

## k6

本地使用原生 Windows k6，不使用 Docker：

```powershell
k6 version
```

如果未安装：

```powershell
winget install k6 --source winget
```

运行：

```powershell
cd load-tests
.\scripts\run-smoke.ps1
.\scripts\run-correctness.ps1
.\scripts\run-baseline.ps1
```

场景：

- `smoke.js`: health、events、下单、查单、创建支付、模拟支付成功、查最终状态。
- `order-baseline.js`: 稳定下单基线，不支付，不测试售罄。
- `oversell-spike.js`: 小库存大并发，验证无超卖和无负库存。
- `idempotency-retry.js`: 同一用户同一 `Idempotency-Key` 并发重试。
- `payment-callback-replay.js`: 完全相同成功回调并发重复到达。
- `full-journey.js`: 查询演出 -> 下单 -> 支付会话 -> 模拟成功 -> 查单。

严格正确性阈值：

```text
oversell_detected count == 0
inventory_inconsistent count == 0
duplicate_order_detected count == 0
duplicate_payment_processing_detected count == 0
unexpected_error count == 0
```

临时性能保护线：

```text
http_req_duration p(95) < 3000ms
http_req_duration p(99) < 5000ms
```

These are provisional guardrails, not production SLOs.

`OUT_OF_STOCK` 在 oversell spike 中是正常业务结果，不是系统错误。其他 409、5xx、无效 JSON、负库存、库存不守恒和超卖都是失败。

## 报告

模板：

```text
docs/performance/baseline-template.md
load-tests/reports/baseline-template.md
```

只有实际运行成功后才创建 `docs/performance/baseline.md`。不得编造 RPS、P50、P95、P99 或任何性能数字。

## 测试

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd verify -Pintegration
```

```powershell
cd frontend
npm ci
npm run build
```

## GitHub Actions

- `.github/workflows/ci.yml`: push 和 pull_request 时运行后端/前端测试。
- `.github/workflows/performance.yml`: 仅 `workflow_dispatch` 手动触发，使用 PostgreSQL 17 service 和低规模 k6 correctness，不作为真实性能基线。

## 未实现

- Redis、Redis Lua、Redis 分布式锁
- 虚拟排队
- 消息队列
- 真实支付和退款
- JWT 登录
- WebSocket/SSE
- 微服务、Kubernetes
- Grafana Server、Prometheus Server
