# 位置读模型由 location 服务独占

Redis 中的司机 GEO 索引与在线状态由 `location` 独占写入；`dispatch` 通过 `location` 的同步查询 API 获取附近司机，不直接读取 Redis。

直接共享 Redis 会让 key 布局成为跨服务契约，`location` 无法独立演进读模型，也无法在查询层执行城市隔离与限流。用一次毫秒级内部调用换取清晰的数据所有权是值得的；若查询成为热点，可下沉为 `location` 内的专用索引或 gRPC，契约不变。
