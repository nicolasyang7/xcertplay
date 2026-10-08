# iAP2 route guidance fields

Source: https://lapiw.icu/posts/IAP2_CSM_research/ (community reverse-engineering notes, not Apple's spec).

- `0x5200 StartRouteGuidanceUpdates` (accessory -> phone): param 0 = u16 display/component id, repeatable.
  XcertPlay sends id 1. The notes say 0x1D01 identification has no route-guidance component parameter.
- `0x5201 RouteGuidanceUpdate`: 1 state (enum), 2 maneuverState, 3 currentRoadName, 4 destinationName,
  5 ETA (u64), 6 timeRemaining (u64), 7 distanceRemaining (u32), 10 distanceToNextManeuver (u32),
  13 maneuverList (u16[]), 14 maneuverCount.
- `0x5202 RouteGuidanceManeuverUpdate`: 1 index (u16), 2 description, 3 maneuverType (enum),
  4 afterManeuverRoadName, 5 distanceBetweenManeuver (u16), 13 exitInfo.
- `0x5204 LaneGuidanceInfoUpdate`: 1 index, 2 laneInfoList (children undocumented), 3 instructionText.

Unverified assumptions (not in the notes; confirm on the vehicle with `rawType`/state in logs):
- state values: 0 not active, 2 arrived, 5 rerouting.
- timeRemaining is in milliseconds.
- ManeuverType values 0..13 (0 left, 1 right, 2 straight, 3 U-turn, 4 follow road, 5/6 roundabout enter/exit,
  7 off ramp, 8 on ramp, 9/11 arrive, 12/13 keep left/right).
