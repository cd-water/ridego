# RideGo 系统设计

状态：草案，待确认
日期：2025-11-19
相关：`GLOSSARY.md`、`docs/adr/0001-microservices-event-driven-cqrs.md`、`docs/adr/0002-cqrs-without-event-sourcing.md`、`docs/adr/0003-location-read-model-api.md`、`docs/adr/0004-offer-based-dispatch.md`

## 1. 目标与范围

RideGo 是单城市的类 Uber 网约车系统：Rider 发起 Trip，Dispatch 为其匹配 Driver，Driver 接驾并完成行程，Billing 生成账单。

定位：面向真实产品的作品集项目。按生产形状组织（微服务、事件驱动、DDD、CQRS），规模限定为单城市。

规模目标：
- 在线司机 10,000
- 峰值并发行程 1,000–2,000
- GPS 上报 3 秒/次 ≈ 3.3k 消息/秒

v1 包含：
- 身份与认证（Rider / Driver）、Driver 与 Vehicle 绑定
- 叫车、顺序 Offer 匹配、接单、到达、开始、完成
- 实时位置索引与在线判定（不做 ETA / 路径规划）
- Offer 超时、拒绝、司机取消后的重派、匹配窗口耗尽后的 Unmatched
- 取消（Rider / Driver / System）
- 行程分享（只读令牌）
- 零金额账单与模拟支付（真实计价后期）
- 仿真器：万级司机 GPS + 乘客叫车

v1 不做：ETA / 路径规划、双向评价、拼车、预约、动态调价、多车型、优惠券、风控 / 安全中心、真实支付、多城市。

## 2. 服务与职责

| 服务 | 职责 | 拥有数据 | 主要 API | 发出事件 |
|---|---|---|---|---|
| `gateway` | 统一入口、JWT 校验、路由 | 无 | 所有外部 API 入口 | 无 |
| `identity` | 账号、登录、JWT 签发、Vehicle 绑定 | 账号、车辆 | `/auth/*`、`/drivers/*` | 无（v1） |
| `trip` | Trip 聚合与状态机、Trip 读模型、分享令牌 | Trip 写表、投影、分享令牌 | `/trips/*`、`/shared/trips/*` | `TripRequested`、`TripAccepted`、`TripArrived`、`TripStarted`、`TripCompleted`、`TripCancelled`、`TripDriverCancelled`、`TripUnmatched`、`TripAcceptRejected` |
| `dispatch` | 匹配、Offer 生命周期、司机可用性读模型 | Offer、`driver_availability` | `/offers/*` | `MatchingStarted`、`OfferCreated`、`OfferExpired`、`OfferRejected`、`OfferAccepted` |
| `location` | GPS 摄入、Redis GEO、在线判定、行程轨迹 | 位置索引（Redis）、轨迹（PG） | `/internal/drivers/nearby`、`/drivers/{id}/presence` | `DriverPresenceChanged` |
| `billing` | 账单与模拟支付 | Invoice | `/invoices/*` | `InvoiceIssued`、`InvoicePaid` |
| `simulator` | 司机 GPS 与乘客 / 司机行为模拟、验收指标 | 场景配置 | 调用 gateway + Kafka | 无 |

数据所有权规则：
- 只有拥有者可以写自己的数据；跨服务读取一律通过 API 或事件。
- `dispatch` 不直读 Redis GEO；它调用 `location` 的查询 API（ADR-0003）。
- `dispatch` 拥有司机可用性（IDLE / OFFERED / ON_TRIP），`location` 只负责位置与在线 / 离线。

## 3. Trip 状态机

状态：
- `requested`：Trip 已创建，Dispatch 尚未接手
- `matching`：Dispatch 正在寻找司机
- `offered`：已有 Offer 发给某位司机，等待接受
- `accepted`：司机已接受，前往上车点
- `arrived`：司机已到达上车点
- `in_progress`：乘客已上车，行程进行中
- `completed`：终态
- `cancelled`：终态，记录 `actor`（rider / driver / system）与 `reason`
- `unmatched`：终态，匹配窗口内未找到司机

合法转移：

```
requested   → matching | cancelled
matching    → offered | unmatched | cancelled
offered     → accepted | matching(拒绝/超时) | cancelled
accepted    → arrived | matching(司机取消) | cancelled
arrived     → in_progress | matching(司机取消) | cancelled(乘客取消/爽约)
in_progress → completed
```

不变量：
- 只有上表列出的转移合法；非法转移被拒绝并记录。
- `completed`、`cancelled`、`unmatched` 不可再变。
- 一个 Trip 同时最多一个未决 Offer。
- 一位 Driver 同时最多一个未决 Offer 或一个未完成 Trip（由 `dispatch` 的可用性约束保证）。
- `arrived` 之前不能 `in_progress`；`in_progress` 在 v1 不允许取消。

默认参数（可配置）：Offer 有效期 10 秒；匹配窗口 90 秒；司机取消后重派重置匹配窗口；候选半径 3 km，30 秒后扩到 5 km。

写侧由 `TripLifecycle` 模块拥有：状态转移表、状态 CAS、时间戳、事件构造与 outbox 追加。HTTP、Kafka 与匹配窗口调度都是薄适配器；`TripState` 是模块内部的唯一状态词汇。

## 4. 关键流程

### 4.1 叫车与匹配

1. Rider 经 gateway 调用 `POST /trips`；`trip` 创建 Trip（`requested`），同事务写 outbox，发出 `TripRequested`。
2. `dispatch` 消费 `TripRequested`，发出 `MatchingStarted`，开始匹配循环。
3. `dispatch` 调用 `location` 的附近司机 API，取同城在线司机坐标；与自身 `driver_availability` 中的 IDLE 集合求交。
4. 按距离排序（距离相同取空闲时间更长者），向最近的一位发出 Offer：PG 唯一约束 + Redis 抢占键双保险，将司机置为 OFFERED，发出 `OfferCreated`。
5. `trip` 消费 `OfferCreated`，`matching → offered`。
6. 司机经 gateway 调用 `POST /offers/{id}/accept`，由 `dispatch` 处理：原子地把 Offer 置为 ACCEPTED、司机置为 ON_TRIP，发出 `OfferAccepted`。
7. `trip` 消费 `OfferAccepted`，`offered → accepted`，发出 `TripAccepted`。
8. 若 `trip` 此时已不在 `offered`（例如乘客已取消），发出 `TripAcceptRejected`；`dispatch` 释放司机回 IDLE 并关闭 Offer。

### 4.2 Offer 超时、拒绝与重派

- `dispatch` 为每个 Offer 维护超时；到期发出 `OfferExpired`，司机回 IDLE。
- 司机拒绝时发出 `OfferRejected`，司机回 IDLE。
- `trip` 消费以上事件：若仍在匹配窗口内，`offered → matching`；`dispatch` 排除该司机后继续下一位。
- 匹配窗口耗尽由 `trip` 判定：`matching → unmatched`，发出 `TripUnmatched`；`dispatch` 收到后停止该 Trip 的匹配。

### 4.3 行程执行与取消

- 司机经 gateway 调用 `POST /trips/{id}/arrive`、`/start`、`/complete`，由 `trip` 校验当前司机并转移状态。
- Rider 取消：`trip` 按状态判断是否允许，进入 `cancelled`，发出 `TripCancelled`；`dispatch` 释放司机，`location` 停止轨迹。
- 司机在 `accepted` / `arrived` 取消：`trip` 回到 `matching`（重置匹配窗口），发出 `TripDriverCancelled`；`dispatch` 释放司机并重新开始匹配。
- `in_progress` 不可取消，v1 不处理行程中异常终止。

### 4.4 GPS 管道与在线判定

- 司机设备（仿真器）每 3 秒向 Kafka `location.driver-positions.v1` 写入 `{driverId, cityId, lat, lng, ts}`，按 `driverId` 分区。
- `location` 批量消费：更新 Redis GEO 索引与该司机 `last_seen`。
- `location` 每 10 秒扫描在线集合；`last_seen` 超过 30 秒的司机判为离线，发出 `DriverPresenceChanged(offline)`。
- 轨迹：`location` 从 `TripStarted` / `TripCompleted` / `TripCancelled` 事件维护"行程中司机"集合，只为这些司机的位置写 PG 轨迹表（按天分区）。
- 空驶位置只留在 Redis，不落库。

### 4.5 账单与行程分享

- `billing` 消费 `TripCompleted`，按 `tripId` 幂等生成 0 元 Invoice，发出 `InvoiceIssued`，随后模拟支付成功发出 `InvoicePaid`。
- `billing` 消费 `TripCancelled` 时只记录（v1 无取消费）；真实计价规则后期替换计算器。
- Rider 可创建分享令牌；公开接口以只读方式返回 Trip 的状态投影，令牌可设过期时间。

## 5. 数据与一致性

### 5.1 PostgreSQL

单实例，每服务独立 database，独立 Flyway 迁移，禁止跨库 SQL：

| database | 主要表 |
|---|---|
| `ridego_identity` | `accounts`、`vehicles` |
| `ridego_trip` | `trips`（写）、`trip_read`（投影）、`share_tokens`、`outbox`、`processed_events` |
| `ridego_dispatch` | `offers`、`driver_availability`、`outbox`、`processed_events` |
| `ridego_location` | `trip_positions`、`outbox`、`processed_events` |
| `ridego_billing` | `invoices`、`outbox`、`processed_events` |

数据库在 PostgreSQL 容器首次启动时由 `ops/postgres/init/01-create-databases.sql` 创建，所有者均为 `ridepostgres`。

### 5.2 一致性

- 写模型使用状态 CAS（`WHERE state = expected`）；`version` 单调递增，用于审计。状态变更与 outbox 记录在同一事务，先 save 后发事件。
- outbox 轮询投递器每 500 ms 扫描并发布到 Kafka；至少一次投递。
- 所有消费者按 `eventId` 幂等（`processed_events` 主键去重）。
- 司机分配：`dispatch` 的 PG 唯一约束（司机上只能有一条活跃 Offer / 活跃 Trip 引用）+ Redis `SET NX EX` 抢占键；两者都成功才发 Offer，失败方回滚。
- 补偿：`TripAcceptRejected` 让 `dispatch` 释放司机；`TripCancelled` / `TripUnmatched` 让 `dispatch` 关闭未决 Offer。

### 5.3 Redis

| 键 | 所有者 | 用途 |
|---|---|---|
| `geo:drivers:{cityId}` | `location` | 司机实时 GEO 索引 |
| `driver:last_seen:{driverId}` | `location` | 心跳时间 |
| `driver:presence:{driverId}` | `location` | online / offline |
| `dispatch:claim:{driverId}` | `dispatch` | Offer 抢占，TTL = Offer 有效期 |

### 5.4 Kafka

| topic | key | 分区 | 生产者 |
|---|---|---|---|
| `trip.events.v1` | `tripId` | 12 | `trip` |
| `dispatch.events.v1` | `tripId` | 12 | `dispatch` |
| `location.events.v1` | `driverId` | 12 | `location` |
| `billing.events.v1` | `tripId` | 6 | `billing` |
| `location.driver-positions.v1` | `driverId` | 24 | 司机设备 / 仿真器 |

事件信封（JSON）：

```json
{
  "eventId": "uuid",
  "eventType": "OfferAccepted",
  "eventVersion": 1,
  "occurredAt": "2026-11-13T10:00:00Z",
  "aggregateId": "trip-id 或 driver-id",
  "cityId": "city-1",
  "correlationId": "uuid",
  "causationId": "uuid",
  "payload": {}
}
```

- 与数据库同事务的事件走 outbox；GPS 遥测直接进 Kafka，不落 outbox。
- 不引入 Schema Registry；通过 `eventType + eventVersion` 演进。

## 6. API 表面（v1）

Rider（经 gateway，`/api/v1`）：
- `POST /auth/login`
- `POST /trips` `{pickup, dropoff}` → `{tripId, state}`
- `GET /trips/{id}`
- `POST /trips/{id}/cancel` `{reason}`
- `POST /trips/{id}/share` → `{token}`
- `GET /shared/trips/{token}`（公开只读）

Driver（经 gateway）：
- `POST /auth/login`
- `POST /drivers/{id}/presence` `{online}`（转 `location`）
- `GET /offers/pending`
- `POST /offers/{id}/accept` / `reject`（转 `dispatch`）
- `POST /trips/{id}/arrive` / `start` / `complete`（转 `trip`）

内部：
- `GET /internal/drivers/nearby?cityId=&lat=&lng=&radius=&limit=`（`location`，仅 `dispatch` 调用）
- `GET /internal/invoices/{tripId}`（`billing`，可选）

认证：`identity` 签发 HS256 JWT，`sub` = userId，`role` = RIDER / DRIVER，Driver 额外带 `driverId`。`gateway` 校验签名后把 `X-User-Id`、`X-Role`、`X-Driver-Id` 注入内部请求；内部服务信任网关（内网边界）。

## 7. 仿真器与验收

形态：独立 Maven 模块，可作为 Compose 服务或本地 CLI 运行；配置用 YAML，支持随机种子以复现。

场景配置：
```yaml
city: city-1
grid: { widthKm: 20, heightKm: 20 }
drivers: 10000
driverSpeedKmh: 30
gpsIntervalSeconds: 3
tripRatePerMinute: 100
riderCancelRate: 0.05
driverRejectRate: 0.1
durationMinutes: 30
seed: 42
```

接入方式：
- 司机 GPS 直接写 Kafka `location.driver-positions.v1`（模拟设备遥测）。
- 乘客叫车 / 取消、司机接单 / 到达 / 开始 / 完成全部经 gateway REST。
- 司机机器人轮询 `/offers/pending` 并按概率接受 / 拒绝；接受后按网格行驶时间依次到达、开始、完成。

验收基线：
- 10,000 在线司机，100 单/分钟，持续 30 分钟。
- 供给充足时匹配成功率 ≥ 95%。
- 无司机重复分配（同一司机不得同时存在两条活跃 Offer / Trip）。
- 事件 → 状态传播 p95 < 1 秒。
- GPS 摄入到 Redis 可查询 p95 < 2 秒。
- 输出匹配成功率、Offer 延迟、状态传播延迟、失败原因分布。

## 8. 技术栈与模块布局

```text
ridego/
├── pom.xml                      # 聚合与依赖版本
├── libs/
│   └── contracts/               # 事件信封、事件 DTO、JWT claim 常量（无业务逻辑）
├── services/
│   ├── pom.xml                  # 聚合
│   ├── identity/
│   ├── trip/
│   ├── dispatch/
│   ├── location/
│   ├── billing/
│   └── gateway/
├── simulator/
└── ops/
    ├── docker-compose.yml
    └── .env.example
```

### 8.1 基础设施版本与约定

| 组件 | 镜像 |
|---|---|
| PostgreSQL | `postgres:16.15` |
| Redis | `redis:7.4.11` |
| Kafka | `apache/kafka:4.1.2` |
| Prometheus | `prom/prometheus:v3.14.0` |
| Grafana | `grafana/grafana:13.2.2` |

本地基础设施由 `ops/docker-compose.yml` 编排：

- 变量模板 `ops/.env.example`，实际值 `ops/.env`（已被 `.gitignore` 忽略）。
- `container_name` 统一 `ridego-<组件>`；卷统一 `<组件>-data`；网络统一 `ridego-network`。
- 账号密码统一 `ride<组件>`：Postgres `ridepostgres` / `ridepostgres`，Redis `rideredis`，Grafana `ridegrafana` / `ridegrafana`。
- 每服务 PostgreSQL database 由 `ops/postgres/init/01-create-databases.sql` 首次启动时创建。
- Kafka 单节点 KRaft、无认证：Compose 内服务连 `kafka:9092`，宿主机连 `localhost:9094`。
- Prometheus / Grafana 位于 `monitoring` profile，默认不启动（`docker compose --profile monitoring up -d`）。

### 8.2 应用技术栈

- Java 21、Spring Boot 4.1.1、Maven 多模块。
- Web：Spring MVC + REST；`gateway` 用 Spring Cloud Gateway。
- 持久化：Spring JDBC（`JdbcTemplate`，写模型显式 SQL）+ Flyway（每服务独立迁移）。
- 消息：Spring Kafka；outbox 轮询投递器。
- 缓存：Spring Data Redis。
- 测试：JUnit 5 + Testcontainers（PostgreSQL / Redis / Kafka）；仿真器做端到端验收。
- 文档：每服务 springdoc-openapi。
- 配置：环境变量 + Spring profiles；Docker Compose 编排。

## 9. 里程碑

| 里程碑 | 内容 | 完成标准 |
|---|---|---|
| M1 基座 | Compose 基础设施、多模块骨架、`libs/contracts`、gateway + identity 登录、trip 创建 / 查询 + outbox + 读投影 | 登录后创建并查询 Trip；Testcontainers 集成测试通过 |
| M2 快乐路径 | location 附近查询 + 位置摄入、dispatch Offer 流程、trip 接单 / 到达 / 开始 / 完成、billing 零元账单 | 仿真器驱动一次完整行程，事件链闭合 |
| M3 失败路径 | Offer 超时 / 拒绝、Unmatched、Rider / Driver 取消、重派、接单补偿、幂等 | 所有状态机分支有集成测试；无重复分配 |
| M4 规模验收 | 仿真器 10k GPS、100 单/分钟、心跳过期、轨迹、指标输出 | 达到第 7 节验收基线 |
| M5 运维硬化 | Prometheus + Grafana、告警、容量调优、文档 | 关键指标看板可用 |

## 10. 测试策略

- 单元测试：Trip 状态机合法 / 非法转移、司机可用性转移、Offer 选择与排除逻辑、事件信封序列化。
- 集成测试：每服务 Testcontainers（PG / Redis / Kafka）；覆盖 outbox 投递、幂等消费、并发抢占。
- 端到端：仿真器按第 7 节场景运行并断言验收基线。
- 合同：`libs/contracts` 的事件信封与事件类型有 schema 测试，防止生产者 / 消费者漂移。

## 11. 已知取舍与风险

- 微服务在此规模上运维成本高于模块化单体；选择它是为了架构实践（ADR-0001）。
- 单 PG 实例是共享故障域；v1 接受，后期按服务拆分实例。
- JSON 事件无 Schema Registry；跨服务契约靠版本字段与合同测试约束。
- 事件最终一致：读模型可能短暂滞后，写侧以 PG 约束保证正确性。
- 行程中司机掉线、`in_progress` 异常终止不在 v1 范围。
- 无真实地图与 ETA；仿真器使用合成网格与直线距离。

## 12. 可观测性与运维

- 所有服务接入 Spring Boot Actuator + Micrometer，暴露 `/actuator/prometheus`。
- 业务指标：`ridego.trips.created/completed/cancelled/unmatched`、`ridego.offers.created/accepted/rejected/expired`、`ridego.positions.ingested`、`ridego.invoices.paid`。
- Prometheus 抓取宿主机上的 6 个服务（`host.docker.internal`），规则见 `ops/prometheus/alerts.yml`：
  - 服务不可用、5xx 比例、p95 延迟、Kafka 消费积压。
- Grafana 预置 Prometheus 数据源与 `RideGo` 看板，位于 `ops/grafana/`。
- 本地启动、仿真场景与排查命令见 `docs/runbook.md`。
