# 本地运行模式

本地开发默认在宿主机运行六个 Spring Boot 进程（`ops/run-local.sh up`），Docker Compose 只负责 PostgreSQL / Redis / Kafka；需要可复现的完整拓扑时启用 Compose `app` profile，把同一批 jar 挂进 `eclipse-temurin:21-jre` 容器。

选择宿主 JVM 作为默认，是因为它构建快、日志直出、调试器可直接附加，而 Compose 基础设施已经稳定；反过来，容器模式要求先构建 jar、拉取 JRE 镜像，并占用相同端口，因此两种模式互斥。Prometheus 因此保留两份抓取配置（`prometheus.yml` 抓宿主机、`prometheus-app.yml` 抓容器 DNS），由 `PROM_CONFIG` 选择，避免同一实例被重复抓取。

代价是本地不产出应用镜像，部署镜像不在本 ADR 范围内；容器模式的运行时验证依赖可用的 registry。
