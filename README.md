# RideGo

单城市的类 Uber 网约车系统：微服务 + 事件驱动 + DDD + CQRS。

- 系统设计：`docs/design.md`
- 领域术语：`GLOSSARY.md`
- 决策记录：`docs/adr/`
- 运行手册：`docs/runbook.md`

快速开始：

```bash
docker compose -f ops/docker-compose.yml up -d
bash ./mvnw -DskipTests package
# 启动 6 个服务后运行仿真器
SIM_SCENARIO=happy java -jar simulator/target/simulator-0.0.1-SNAPSHOT.jar
```

服务端口、账号、仿真场景与监控地址见 `docs/runbook.md`。
