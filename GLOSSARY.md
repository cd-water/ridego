# RideGo

一个类 Uber 的网约车系统：乘客发起行程，系统调度司机，双方完成行程并结算。

## Language

**Trip（行程）**:
乘客发起的一次用车行程的完整生命周期，从创建到完成、取消或未匹配。
_Avoid_: Ride, Order, 订单

**Rider（乘客）**:
发起 Trip 并在行程中乘车的人。
_Avoid_: Customer, User, 用户

**Driver（司机）**:
接受 Offer 并驾驶车辆完成 Trip 的人。
_Avoid_: Partner, 车主

**Vehicle（车辆）**:
司机用于完成 Trip 的车辆，与司机绑定。
_Avoid_: Car

**Dispatch（调度）**:
为等待匹配的 Trip 选择候选司机、发出并跟踪 Offer 的过程。
_Avoid_: Assignment, 派单服务

**Matching（匹配中）**:
Trip 正在寻找司机的阶段；在此期间 Dispatch 会依次发出 Offer。
_Avoid_: Pending, 派单

**Offer（接单邀请）**:
Dispatch 向某位具体司机发出的、限时可接受或拒绝的接单邀请。
_Avoid_: Request, Invitation, 派单

**Unmatched（未匹配）**:
Trip 因未找到司机而终止的终态。
_Avoid_: Expired, Failed

**City（城市）**:
Trip 与司机可见性的隔离边界；跨城市的 Trip 与司机互不可见。
_Avoid_: Region, Zone

**Driver Presence（司机在线状态）**:
司机是否在线：ONLINE / OFFLINE。
_Avoid_: Status

**Driver Availability（司机可用性）**:
司机能否被 Dispatch 考虑的状态：IDLE、OFFERED、ON_TRIP、OFFLINE。
_Avoid_: Status

**Fare（车费）**:
完成一次 Trip 后 Rider 应付的金额。
_Avoid_: Price, 价格
