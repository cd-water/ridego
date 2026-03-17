# RideGo 运行手册

## 依赖

- Java 21
- Docker + Docker Compose
- 本地镜像：`postgres:16.15`、`redis:7.4.11`、`apache/kafka:4.1.2`（监控：`prom/prometheus:v3.14.0`、`grafana/grafana:13.2.2`）
- 可选（Compose app 模式）：`eclipse-temurin:21-jre`

## 1. 基础设施

```bash
# 启动 PostgreSQL / Redis / Kafka
docker compose -f ops/docker-compose.yml up -d

# 连同 Prometheus / Grafana
docker compose -f ops/docker-compose.yml --profile monitoring up -d

# 停止（保留数据）
docker compose -f ops/docker-compose.yml down

# 清空数据（重建数据库与 topic）
docker compose -f ops/docker-compose.yml down -v
```

Kafka 已关闭 broker 自动建 topic；所有 topic 由各服务启动时的 `NewTopic` 创建。

可复现的容器模式（与宿主 JVM 模式**互斥**，端口相同；先构建 jar）：

```bash
# 六个服务 + 基础设施
docker compose -f ops/docker-compose.yml --profile app up -d

# 连同监控，Prometheus 改用容器 DNS 抓取
PROM_CONFIG=prometheus-app.yml \
  docker compose -f ops/docker-compose.yml --profile app --profile monitoring up -d

# 服务健康后运行仿真器
docker compose -f ops/docker-compose.yml --profile app --profile simulator \
  run --rm -e SIM_SCENARIO=happy simulator
```

## 2. 构建与测试

```bash
bash ./mvnw -DskipTests package

# 本机 Docker 守护进程走代理且 ryuk 镜像不可用时
TESTCONTAINERS_RYUK_DISABLED=true bash ./mvnw test
```

## 3. 启动服务

```bash
# 启动基础设施 + 构建 + 后台启动六个服务 + 等待健康
bash ops/run-local.sh up

# 查看状态 / 停止应用（保留基础设施）
bash ops/run-local.sh status
bash ops/run-local.sh down
```

脚本从 `services/<name>/target/` 读取 jar，日志与 PID 在 `ops/.run/`；trip 默认以 `MATCHING_WINDOW_SECONDS=20` 启动（可用环境变量覆盖）。

手动启动（等价于脚本）：

```bash
docker compose -f ops/docker-compose.yml up -d --wait
bash ./mvnw -DskipTests package
MATCHING_WINDOW_SECONDS=20 java -jar services/trip/target/trip-0.0.1-SNAPSHOT.jar
# 其余服务同理：identity / dispatch / location / billing / gateway
```

| 服务 | 端口 |
|---|---|
| gateway | 8080 |
| identity | 8081 |
| trip | 8082 |
| dispatch | 8083 |
| location | 8084 |
| billing | 8085 |

种子账号：`rider1 / ridego`（乘客）、`driver1 / ridego`（司机）。

## 4. 仿真场景

```bash
# 单次完整行程
SIM_SCENARIO=happy java -jar simulator/target/simulator-0.0.1-SNAPSHOT.jar

# 乘客取消后司机被释放并接到下一单
SIM_SCENARIO=cancel java -jar simulator/target/simulator-0.0.1-SNAPSHOT.jar

# Offer 超时 → 重派排除该司机 → 匹配窗口耗尽（建议 trip 以 MATCHING_WINDOW_SECONDS=20 启动）
SIM_SCENARIO=timeout java -jar simulator/target/simulator-0.0.1-SNAPSHOT.jar

# 司机停止 GPS → 心跳过期 → 新行程 unmatched
SIM_SCENARIO=heartbeat java -jar simulator/target/simulator-0.0.1-SNAPSHOT.jar

# 负载：万级司机 + 100 单/分钟（duration 可调到 30）
SIM_SCENARIO=load \
  SIM_DRIVERS=10000 SIM_TRIP_RATE=100 SIM_DURATION_MINUTES=2 \
  java -jar simulator/target/simulator-0.0.1-SNAPSHOT.jar

# 可复现与失败注入（默认值即设计基线）
SIM_SEED=42 SIM_DRIVER_REJECT_RATE=0.1 SIM_RIDER_CANCEL_RATE=0.05 \
  SIM_SCENARIO=load SIM_DRIVERS=1000 SIM_DURATION_MINUTES=1 \
  java -jar simulator/target/simulator-0.0.1-SNAPSHOT.jar
```

## 5. 可观测性

| 组件 | 地址 |
|---|---|
| Actuator 指标 | `http://localhost:<port>/actuator/prometheus` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3000`（`ridego` / `ridego`） |

- Grafana 预置数据源与 `RideGo` 看板：`ops/grafana/`
- 告警规则：`ops/prometheus/alerts.yml`
- 业务指标：`ridego_trips_*`、`ridego_offers_*`、`ridego_positions_ingested_total`、`ridego_invoices_paid_total`

默认 `prometheus.yml` 通过 `host.docker.internal` 抓取宿主机上运行的服务（Compose 已配置 `host-gateway`）；app 模式设置 `PROM_CONFIG=prometheus-app.yml`，改用容器 DNS 抓取，避免同一实例被两套目标重复抓取。

## 6. 常用排查

```bash
# 在线司机数与 GEO 索引
docker exec -it ridego-redis redis-cli -a rideredis SCARD drivers:online
docker exec -it ridego-redis redis-cli -a rideredis ZCARD geo:drivers:city-1

# 行程与 Offer 状态
docker exec -it ridego-postgres psql -U ridepostgres -d ridego_trip \
  -c "SELECT state, count(*) FROM trips GROUP BY state;"
docker exec -it ridego-postgres psql -U ridepostgres -d ridego_dispatch \
  -c "SELECT state, count(*) FROM offers GROUP BY state;"

# 轨迹
docker exec -it ridego-postgres psql -U ridepostgres -d ridego_location \
  -c "SELECT count(*), count(DISTINCT trip_id) FROM trip_positions;"

# 被隔离的 outbox 行（达到 max-attempts 后不再自动重试）
# 依次替换 database：ridego_trip / ridego_dispatch / ridego_billing / ridego_location
docker exec -it ridego-postgres psql -U ridepostgres -d ridego_trip \
  -c "SELECT id, topic, event_key, attempts, occurred_at FROM outbox WHERE parked_at IS NOT NULL ORDER BY id;"

# 人工修复根因后重新投递该行
docker exec -it ridego-postgres psql -U ridepostgres -d ridego_trip \
  -c "UPDATE outbox SET parked_at = NULL, attempts = 0 WHERE id = <id>;"
```
