# NaviTool HUD 高德车机版广播协议规格说明

> 本文档记录 NaviTool（基于 `AmapNaviReceiver.java`）接收高德地图车机版广播（Action: `AUTONAVI_STANDARD_BROADCAST_SEND`）所消费的全部关键字段与消息类型（KEY_TYPE），作为 XcertPlay CarPlay 导航事件桥接转换的官方对接契约。

---

## 一、 基础导航与引导信息 (`KEY_TYPE = 10001`)

用于驱动 HUD 核心模组：`navi_turn_icon`（转向图标）、`navi_turn_dist`（转向距离）、`navi_next_road`（下根路名）、`navi_current_road`（当前路名）、`module_camera_distance`（测速电子眼）、`speed_limit`（限速）等。

### 1. 转向与动作
- **`ICON` / `NEW_ICON`** (`Int`)：转向图标编号（`0` 表示巡航/直行无转向，`1~108` 表示各种转向动作）。
- **`SEG_REMAIN_DIS_AUTO`** (`String` / `Int`)：当前路段剩余距离文本（如 `"300米"`、`"1.2公里"`）。
- **`SEG_REMAIN_DIS`** (`Int`): 当前路段剩余距离数值（米）。
- **`NEXT_ROAD_NAME`** (`String`)：下一阶段道路名称（如 `"黄山路"`）。
- **`CUR_ROAD_NAME`** (`String`)：当前行驶道路名称（如 `"天府大道"`）。

### 2. 行程总览与 ETA
- **`ROUTE_REMAIN_DIS_AUTO`** (`String`)：全程剩余总里程（如 `"15.4公里"`）。
- **`ROUTE_REMAIN_TIME_AUTO`** (`String`)：全程剩余总时间（如 `"25分钟"`）。
- **`ETA_TEXT`** (`String`)：预计到达时间（如 `"18:25"`）。
- **`ROUTE_REMAIN_DIS`** (`Int`) & **`ROUTE_ALL_DIS`** (`Int`)：剩余/总里程数值（米，用于计算底部行程进度条百分比）。
- **`endPOIName`** (`String`)：终点目的地名称。

### 3. 车速与测速电子眼
- **`CUR_SPEED`** (`Int`)：实时行驶车速。
- **`LIMITED_SPEED` / `SPEED_LIMIT`** (`Int`)：当前道路最高限速。
- **`CAMERA_DIST`** (`Int`)：测速电子眼/摄像头剩余距离（米）。
- **`CAMERA_SPEED`** (`Int`)：电子眼限速值。
- **`CAMERA_TYPE`** (`Int`)：电子眼类型（`1`=测速，`2`=闯红灯，`3`=应急车道等）。
- **`CAR_DIRECTION`** (`Float` / `Int`)：车头朝向方位角。

### 4. 出口与服务区
- **`EXIT_NAME_INFO`** & **`EXIT_DIRECTION_INFO`** (`String`)：高速/快速路出口编号及方向指示。
- **`SAPA_NAME`** / **`SAPA_DIST_AUTO`** / **`SAPA_TYPE`**：下一个服务区名称、距离及类型。

---

## 二、 车道线与推荐车道 (`KEY_TYPE = 13012`)

用于驱动 `module_lane_line`（HUD 车道线模组）。

- **`EXTRA_DRIVE_WAY`** (`String` / JSON)：车道线描述 JSON 字符串。
  - 包含车道总数、各车道箭头动作（直行/左转/右转/掉头/未划线等）以及当前推荐车道高亮标识（`is_recommended` / `extended_lane`）。

---

## 三、 红绿灯与倒计时 (`KEY_TYPE = 60073`)

用于驱动 `module_traffic_light`（导航红绿灯）与 `module_cruise_traffic_light`（巡航多车道红绿灯）。

- **`trafficLightStatus`** (`Int`)：灯状态（`1`=红灯，`2`=绿灯，`3`=黄灯）。
- **`dir`** (`Int`)：转向灯方向（`1`=左转，`2`=直行，`3`=右转，`4`=掉头）。
- **`redLightCountDownSeconds`** (`Int`)：红绿灯倒计时剩余秒数。
- **`lightsData`** (`String` / JSON)：多车道红绿灯 JSON 数组字符串（用于巡航下多车道红绿灯展示）。
- **`TRAFFIC_LIGHT_NUM`** & **`routeRemainTrafficLightNum`** (`Int`)：全路径红绿灯总数与剩余红绿灯个数。

---

## 四、 区间测速 (`KEY_TYPE = 12110`)

用于驱动 `module_average_speed` / `module_average_speed_v`（区间测速微型胶囊/带底边进度条模组）。

- **`START_DISTANCE` / `START_DISTANCE_TEXT`**：区间测速起点距离。
- **`AVERAGE_SPEED` / `AVG_SPEED`**：区间内当前平均车速。
- **`END_DISTANCE_TEXT`**：区间测速剩余里程（如 `"2.4km"`）。
- **`LIMITED_SPEED`**：区间测速最高限速值（如 `120`）。

---

## 五、 绿波车速、TMC、拥堵与事故 (`KEY_TYPE = 12120 / 13011 / 13013 / 13014`)

- **绿波车速 (`12120`)**：`GREEN_WAVE_SPEED_MIN` & `GREEN_WAVE_SPEED_MAX`（建议绿波通过速度区间）。
- **TMC 路况 (`13011`)**：`EXTRA_TMC_SEGMENT`（前瞻路况彩带数据）。
- **拥堵提示 (`13013`)**：`EXTRA_JAM_INFO`（前方拥堵距离及加塞提示）。
- **突发事故 (`13014`)**：`EXTRA_INCIDENT_INFO`（前方施工/事故预警信息）。

---

## 六、 生命周期与系统状态 (`KEY_TYPE = 10019`)

- **`EXTRA_STATE`** (`Int`)：
  - `3` / `4`：高德前台 / 后台切换。
  - `9`：导航明确结束（HUD 自动关闭导航画框切回巡航/仪表默认界面）。
  - `25`：巡航明确结束。
  - `37` / `38`：日间模式 / 夜间模式（HUD 随之触发白/夜/雪地暗色调切换）。
- **`EXTRA_CROSS_MAP`** (`Int`)：路口放大图状态（`1`=开启放大图）。

---

## 七、 XcertPlay 桥接实现对应优先级

| 优先级 | KEY_TYPE | 包含核心字段 | CarPlay 数据源支持度 |
|---|---|---|---|
| **P0 (核心基础)** | `10001` | `ICON`, `SEG_REMAIN_DIS_AUTO`, `NEXT_ROAD_NAME`, `CUR_ROAD_NAME`, `ROUTE_REMAIN_DIS_AUTO`, `ROUTE_REMAIN_TIME_AUTO` | iAP2 `0x5201` + `0x5202` 原生提供完整支持 |
| **P0 (生命周期)** | `10019` | `EXTRA_STATE` (`9` 导航结束 / `37`/`38` 昼夜) | iAP2 导航生命周期 / CarPlay 昼夜模式原生支持 |
| **P1 (车道引导)** | `13012` | `EXTRA_DRIVE_WAY` | iAP2 `0x5204` `LaneGuidanceInfoUpdate` 支持 |
| **P2 (扩展扩展)** | `60073` / `12110` | 红绿灯倒计时、区间测速 | 视 iOS 高德 App 是否在 RGI/CarPlay 扩展中下发 |
