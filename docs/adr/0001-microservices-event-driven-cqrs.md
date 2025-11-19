# 微服务 + 事件驱动 + CQRS

RideGo 按领域边界拆分为独立部署的服务（identity、trip、dispatch、location、billing），服务间通过 Kafka 事件协作，Trip 等核心写模型采用 CQRS 分离读写。

单城市万级司机规模用模块化单体本可以更省运维，但项目定位为面向真实产品的作品集，需要在真实分布式形态下实践 DDD、事件驱动与 CQRS。我们接受多 JVM 部署与最终一致性的复杂度，换取清晰的服务边界与架构实践。
