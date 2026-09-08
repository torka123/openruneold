# Wilderness Interaction Audit

Generated: 2026-09-06T21:43:26.574540900Z

Scope: All decoded loc instances on planes 0-3 in area.wilderness, Ferox Enclave, Wilderness dungeon/boss-area children, and cache-backed KBD, Mage Arena/Bank, and Corporeal Beast connected map squares.

## Summary

| Metric | Value |
|---|---:|
| Active cache revision | 240 |
| Regions scanned | 97 |
| Connected areas scanned | 6 |
| Planes scanned | 0, 1, 2, 3 |
| Candidate objects | 1193 |
| Route records | 1224 |
| Registered routes | 312 |
| Discovery-handler passes | 300 |
| Missing handlers | 7 |
| Wrong destinations | 0 |
| Missing reverse routes | 0 |
| Intentional one-way routes | 1 |
| Invalid destinations | 0 |
| Missing cache content | 25 |
| Multiple handler layers | 18 |

## Route records

PASS means the active revision-240 object action resolves to a direct, content-group, category, or exact-coordinate handler. Route-table validation and manual-client status are reported separately per row.

| # | Area | Object | ID | Action | Source | Shape | Handler | Status | Automated | Manual |
|---:|---|---|---:|---|---|---|---|---|---|---|
| 1 | Wilderness Boss Caves | Cave | 47122 | Exit | 1758,11531,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 2 | King Black Dragon Lair | loc.cavewall_face1_for_lever | 6628 | <missing> | 2271,4679,0 | WallStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 3 | King Black Dragon Lair | Lever | 1817 | Pull | 2271,4680,0 | WallDecorStraightNoOffset/3 | exact-coordinate + direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 4 | King Black Dragon Lair | Rockslide | 12584 | <missing> | 2288,4682,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 5 | King Black Dragon Lair | Rockslide | 12585 | <missing> | 2288,4684,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 6 | King Black Dragon Lair | Rockslide | 12584 | <missing> | 2288,4686,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 7 | Mage Arena and Mage Bank | Rockslide | 11435 | <missing> | 2512,4687,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 8 | Mage Arena and Mage Bank | Rockslide | 11435 | <missing> | 2513,4691,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 9 | Mage Arena and Mage Bank | loc.cavewall_face1_for_lever_grey | 5962 | <missing> | 2539,4711,0 | WallStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 10 | Mage Arena and Mage Bank | Lever | 5960 | Pull | 2539,4712,0 | WallDecorStraightNoOffset/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 11 | Mage Arena and Mage Bank | Rockslide | 11435 | <missing> | 2543,4715,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 12 | Mage Arena and Mage Bank | Rockslide | 11435 | <missing> | 2545,4719,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 13 | Wilderness Surface | Rockslide | 11428 | <missing> | 2946,3571,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 14 | Wilderness Surface | Rockslide | 11426 | <missing> | 2946,3573,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 15 | Wilderness Surface | Rockslide | 11428 | <missing> | 2947,3558,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 16 | Wilderness Surface | Rockslide | 11428 | <missing> | 2947,3569,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 17 | Wilderness Surface | Rockslide | 11426 | <missing> | 2948,3560,0 | CentrepieceDiagonal/0 |  | WRONG_OPTION | FAIL | PENDING |
| 18 | Wilderness Surface | Rockslide | 11426 | <missing> | 2948,3566,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 19 | Wilderness Surface | Rockslide | 11428 | <missing> | 2949,3562,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 20 | Wilderness Surface | Rockslide | 11428 | <missing> | 2951,3567,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 21 | Wilderness Surface | Rockslide | 11428 | <missing> | 2952,3555,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 22 | Wilderness Surface | loc.ditch_soil_soft1 | 23272 | <missing> | 2994,3532,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 23 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 2995,3530,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 24 | Wilderness Surface | loc.ditch_soil_soft1 | 23272 | <missing> | 2995,3531,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 25 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 2995,3532,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 26 | Wilderness Surface | loc.ditch_soil_soft1 | 23272 | <missing> | 2995,3533,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 27 | Wilderness Surface | loc.ditch_wilderness_end_south | 23270 | <missing> | 2996,3529,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 28 | Wilderness Surface | loc.ditch_wilderness1_ground | 23261 | <missing> | 2996,3530,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 29 | Wilderness Surface | Wilderness Ditch | 23271 | Cross | 2996,3530,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 30 | Wilderness Surface | loc.ditch_wilderness4_ground | 23265 | <missing> | 2996,3531,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 31 | Wilderness Surface | Wilderness Ditch | 23271 | Cross | 2996,3531,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 32 | Wilderness Surface | loc.ditch_wildernesse_ground | 23267 | <missing> | 2996,3532,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 33 | Wilderness Surface | Wilderness Ditch | 23271 | Cross | 2996,3532,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 34 | Wilderness Surface | loc.ditch_wildernesse_ground | 23267 | <missing> | 2996,3533,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 35 | Wilderness Surface | Wilderness Ditch | 23271 | Cross | 2996,3533,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 36 | Wilderness Surface | loc.ditch_wilderness_end_north | 23269 | <missing> | 2996,3534,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 37 | Wilderness Surface | loc.ditch_wilderness_end_north | 23269 | <missing> | 2997,3529,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 38 | Wilderness Surface | loc.ditch_wildernesse_ground | 23267 | <missing> | 2997,3530,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 39 | Wilderness Surface | loc.ditch_wildernesse_ground | 23267 | <missing> | 2997,3531,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 40 | Wilderness Surface | loc.ditch_wilderness4_ground | 23265 | <missing> | 2997,3532,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 41 | Wilderness Surface | loc.ditch_wilderness1_ground | 23261 | <missing> | 2997,3533,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 42 | Wilderness Surface | loc.ditch_wilderness_end_south | 23270 | <missing> | 2997,3534,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 43 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 2998,3532,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 44 | Wilderness Surface | loc.ditch_soil_soft1 | 23272 | <missing> | 2998,3533,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 45 | Wilderness Surface | Rockslide | 11426 | <missing> | 2950,3620,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 46 | Wilderness Surface | Rockslide | 11426 | <missing> | 2951,3608,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 47 | Wilderness Surface | Rockslide | 11431 | <missing> | 2952,3590,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 48 | Wilderness Surface | Rockslide | 11428 | <missing> | 2953,3596,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 49 | Wilderness Surface | Rockslide | 11428 | <missing> | 2954,3588,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 50 | Wilderness Surface | Rockslide | 11431 | <missing> | 2955,3593,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 51 | Wilderness Surface | Rockslide | 11431 | <missing> | 2956,3602,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 52 | Wilderness Surface | Rockslide | 11428 | <missing> | 2956,3617,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 53 | Wilderness Surface | Rockslide | 11431 | <missing> | 2956,3620,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 54 | Wilderness Surface | Rockslide | 11428 | <missing> | 2956,3632,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 55 | Wilderness Surface | Rockslide | 11431 | <missing> | 2956,3638,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 56 | Wilderness Surface | Rockslide | 11428 | <missing> | 2956,3641,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 57 | Wilderness Surface | Rockslide | 11431 | <missing> | 2956,3645,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 58 | Wilderness Surface | Rockslide | 11428 | <missing> | 2957,3597,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 59 | Wilderness Surface | Rockslide | 11428 | <missing> | 2957,3606,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 60 | Wilderness Surface | Rockslide | 11431 | <missing> | 2957,3612,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 61 | Wilderness Surface | Rockslide | 11428 | <missing> | 2957,3624,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 62 | Wilderness Surface | Rockslide | 11431 | <missing> | 2957,3629,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 63 | Wilderness Surface | Rocks | 16545 | Climb | 2947,3678,0 | GroundDecor/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 64 | Wilderness Surface | Rocks | 16545 | Climb | 2948,3679,0 | GroundDecor/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 65 | Wilderness Surface | Rocks | 16545 | Climb | 2949,3680,0 | GroundDecor/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 66 | Wilderness Surface | Rocky handholds | 26406 | Climb | 2944,3768,0 | GroundDecor/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 67 | Wilderness Surface | Rocky handholds | 26406 | Climb | 2945,3767,0 | GroundDecor/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 68 | Wilderness Surface | Rocky handholds | 26406 | Climb | 2946,3767,0 | GroundDecor/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 69 | Wilderness Surface | Rocky handholds | 26406 | Climb | 2947,3767,0 | GroundDecor/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 70 | Wilderness Surface | Rocky handholds | 26404 | Climb | 2949,3767,0 | GroundDecor/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 71 | Wilderness Surface | Large door | 1521 | Open | 2958,3820,0 | WallStraight/0 | content-group:2 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 72 | Wilderness Surface | Large door | 1524 | Open | 2958,3821,0 | WallStraight/0 | content-group:3 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 73 | Wilderness Surface | Rockslide | 11922 | <missing> | 2944,3877,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 74 | Wilderness Surface | Rockslide | 11921 | <missing> | 2944,3879,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 75 | Wilderness Surface | Rockslide | 11921 | <missing> | 2944,3883,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 76 | Wilderness Surface | Rockslide | 11921 | <missing> | 2944,3892,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 77 | Wilderness Surface | Rockslide | 11921 | <missing> | 2945,3881,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 78 | Wilderness Surface | Rockslide | 11922 | <missing> | 2946,3879,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 79 | Wilderness Surface | Rockslide | 11922 | <missing> | 2946,3883,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 80 | Wilderness Surface | Rockslide | 11922 | <missing> | 2947,3863,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 81 | Wilderness Surface | Rockslide | 11920 | <missing> | 2947,3865,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 82 | Wilderness Surface | Rockslide | 11922 | <missing> | 2947,3867,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 83 | Wilderness Surface | Rockslide | 11923 | <missing> | 2947,3881,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 84 | Wilderness Surface | Rockslide | 11922 | <missing> | 2948,3847,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 85 | Wilderness Surface | Rockslide | 11920 | <missing> | 2948,3849,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 86 | Wilderness Surface | Rockslide | 11922 | <missing> | 2948,3851,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 87 | Wilderness Surface | Rockslide | 11923 | <missing> | 2948,3859,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 88 | Wilderness Surface | Rockslide | 11923 | <missing> | 2949,3855,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 89 | Wilderness Surface | Rockslide | 11923 | <missing> | 2949,3863,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 90 | Wilderness Surface | Rockslide | 11922 | <missing> | 2949,3865,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 91 | Wilderness Surface | Rockslide | 11922 | <missing> | 2950,3849,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 92 | Wilderness Surface | Rockslide | 11923 | <missing> | 2950,3851,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 93 | Wilderness Surface | Rockslide | 11923 | <missing> | 2950,3853,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 94 | Wilderness Surface | Rockslide | 11923 | <missing> | 2951,3865,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 95 | Wilderness Surface | Rockslide | 11921 | <missing> | 2951,3872,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 96 | Wilderness Surface | Rockslide | 11923 | <missing> | 2952,3851,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 97 | Wilderness Surface | Rockslide | 11921 | <missing> | 2952,3853,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 98 | Wilderness Surface | Rockslide | 11922 | <missing> | 2952,3855,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 99 | Wilderness Surface | Rockslide | 11920 | <missing> | 2953,3865,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 100 | Wilderness Surface | Rockslide | 11922 | <missing> | 2954,3845,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 101 | Wilderness Surface | Rockslide | 11923 | <missing> | 2954,3848,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 102 | Wilderness Surface | Rockslide | 11923 | <missing> | 2954,3853,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 103 | Wilderness Surface | Rockslide | 11923 | <missing> | 2954,3869,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 104 | Wilderness Surface | Rockslide | 11922 | <missing> | 2954,3880,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 105 | Wilderness Surface | Rockslide | 11923 | <missing> | 2955,3855,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 106 | Wilderness Surface | Rockslide | 11922 | <missing> | 2956,3878,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 107 | Wilderness Surface | Rockslide | 11921 | <missing> | 2956,3880,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 108 | Wilderness Surface | Rockslide | 11923 | <missing> | 2956,3882,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 109 | Wilderness Surface | Rockslide | 11923 | <missing> | 2957,3851,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 110 | Wilderness Surface | Rockslide | 11923 | <missing> | 2958,3862,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 111 | Wilderness Surface | Rockslide | 11923 | <missing> | 2958,3880,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 112 | Wilderness Surface | Rockslide | 11923 | <missing> | 2959,3873,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 113 | Wilderness Surface | Rockslide | 11922 | <missing> | 2961,3884,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 114 | Wilderness Surface | Rockslide | 11922 | <missing> | 2961,3895,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 115 | Wilderness Surface | Rockslide | 11922 | <missing> | 2962,3872,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 116 | Wilderness Surface | Rockslide | 11923 | <missing> | 2963,3878,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 117 | Wilderness Surface | Rockslide | 11920 | <missing> | 2963,3884,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 118 | Wilderness Surface | Rockslide | 11922 | <missing> | 2963,3886,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 119 | Wilderness Surface | Rockslide | 11923 | <missing> | 2964,3893,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 120 | Wilderness Surface | Rockslide | 11920 | <missing> | 2966,3893,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 121 | Wilderness Surface | Obelisk | 14826 | <missing> | 2978,3864,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 122 | Wilderness Surface | Obelisk | 14826 | <missing> | 2978,3868,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 123 | Wilderness Surface | Obelisk | 14826 | <missing> | 2982,3864,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 124 | Wilderness Surface | Obelisk | 14826 | <missing> | 2982,3868,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 125 | Wilderness Surface | Rockslide | 11923 | <missing> | 2944,3912,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 126 | Wilderness Surface | Rockslide | 11922 | <missing> | 2945,3925,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 127 | Wilderness Surface | Rockslide | 11920 | <missing> | 2945,3927,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 128 | Wilderness Surface | Rockslide | 11922 | <missing> | 2945,3929,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 129 | Wilderness Surface | Rockslide | 11923 | <missing> | 2946,3910,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 130 | Wilderness Surface | Rockslide | 11921 | <missing> | 2946,3912,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 131 | Wilderness Surface | Rockslide | 11923 | <missing> | 2946,3914,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 132 | Wilderness Surface | Rockslide | 11922 | <missing> | 2946,3935,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 133 | Wilderness Surface | Gate | 1728 | Open | 2947,3904,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 134 | Wilderness Surface | Rockslide | 11922 | <missing> | 2947,3927,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 135 | Wilderness Surface | Gate | 1727 | Open | 2948,3904,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 136 | Wilderness Surface | Rockslide | 11922 | <missing> | 2948,3912,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 137 | Wilderness Surface | Runite rocks | 11376 | <missing> | 2948,3914,0 | CentrepieceDiagonal/2 |  | WRONG_OPTION | FAIL | PENDING |
| 138 | Wilderness Surface | Rockslide | 11923 | <missing> | 2953,3923,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 139 | Wilderness Surface | Rockslide | 11923 | <missing> | 2955,3931,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 140 | Wilderness Surface | Rockslide | 11922 | <missing> | 2956,3905,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 141 | Wilderness Surface | Rockslide | 11921 | <missing> | 2958,3905,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 142 | Wilderness Surface | Rockslide | 11922 | <missing> | 2958,3907,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 143 | Wilderness Surface | Rockslide | 11923 | <missing> | 2961,3926,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 144 | Wilderness Surface | Runite rocks | 11376 | <missing> | 2964,3933,0 | CentrepieceDiagonal/1 |  | WRONG_OPTION | FAIL | PENDING |
| 145 | Wilderness Surface | Runite rocks | 11377 | <missing> | 2976,3937,0 | CentrepieceDiagonal/1 |  | WRONG_OPTION | FAIL | PENDING |
| 146 | Wilderness Surface | Rockslide | 11922 | <missing> | 2977,3935,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 147 | Wilderness Surface | Rockslide | 11920 | <missing> | 2977,3937,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 148 | Wilderness Surface | Rockslide | 11922 | <missing> | 2977,3939,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 149 | Wilderness Surface | Rockslide | 11922 | <missing> | 2979,3937,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 150 | Wilderness Surface | Rockslide | 11922 | <missing> | 2987,3926,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 151 | Wilderness Surface | Rockslide | 11920 | <missing> | 2989,3926,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 152 | Wilderness Surface | Rockslide | 11922 | <missing> | 2989,3928,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 153 | Wilderness Surface | Rockslide | 11922 | <missing> | 2991,3926,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 154 | Wilderness Surface | Rocks | 23640 | Climb | 2993,3936,0 | GroundDecor/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 155 | Wilderness Surface | Rocks | 23640 | Climb | 2994,3936,0 | GroundDecor/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 156 | Wilderness Surface | Rocks | 23640 | Climb | 2995,3936,0 | GroundDecor/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 157 | Wilderness Surface | Gate | 23554 | Open | 2997,3931,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 158 | Wilderness Surface | Door | 23555 | Open | 2998,3917,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 159 | Wilderness Surface | Gate | 23552 | Open | 2998,3931,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 160 | Wilderness Surface | Log balance | 23542 | Walk-across | 3001,3945,0 | GroundDecor/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 161 | Wilderness Surface | Stepping stone | 23556 | Cross | 3001,3960,0 | GroundDecor/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 162 | Wilderness Surface | Obstacle pipe | 23137 | Squeeze-through | 3004,3938,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 163 | Wilderness Surface | Obstacle pipe | 23137 | Squeeze-through | 3004,3948,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 164 | Wilderness Surface | Ropeswing | 23129 | <missing> | 3004,3954,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 165 | Wilderness Surface | Ropeswing | 23132 | Swing-on | 3005,3952,0 | CentrepieceStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 166 | Wilderness Surface | Ladder | 14758 | Climb-down | 3005,3963,0 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 167 | Wilderness Surface | Ropeswing | 23129 | <missing> | 3006,3954,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 168 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 2951,9927,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 169 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 2951,9928,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 170 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 2951,9929,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 171 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 2952,9926,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 172 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 2952,9927,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 173 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 2952,9928,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 174 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 2953,9926,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 175 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 2954,9926,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 176 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 2954,9927,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 177 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 2954,9929,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 178 | Wilderness Dungeons | Staircase | 47315 | Climb-up | 2975,9951,0 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 179 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 2998,9933,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 180 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 2998,9934,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 181 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 2998,9935,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 182 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 2999,9933,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 183 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 2999,9934,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 184 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 2999,9935,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 185 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3000,9933,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 186 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3000,9934,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 187 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3000,9935,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 188 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3001,9933,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 189 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3001,9934,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 190 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3001,9935,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 191 | Wilderness Dungeons | Ladder | 17385 | Climb-up | 3005,10363,0 | CentrepieceStraight/0 | content-group:23 | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 192 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3009,3590,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 193 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3010,3589,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 194 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3010,3590,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 195 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3010,3591,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 196 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3011,3593,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 197 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3011,3595,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 198 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3012,3586,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 199 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3012,3588,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 200 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3013,3596,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 201 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3014,3594,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 202 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3015,3586,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 203 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3015,3591,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 204 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3015,3596,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 205 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3016,3587,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 206 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3016,3591,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 207 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3016,3592,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 208 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3016,3598,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 209 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3017,3587,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 210 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3017,3596,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 211 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3017,3597,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 212 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3017,3598,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 213 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3018,3586,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 214 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3018,3597,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 215 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3020,3588,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 216 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3021,3587,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 217 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3021,3589,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 218 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3021,3590,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 219 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3021,3598,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 220 | Wilderness Surface | Door | 14752 | Open | 3021,3631,0 | WallStraight/2 | content-group:3 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 221 | Wilderness Surface | Door | 14751 | Open | 3021,3632,0 | WallStraight/2 | content-group:2 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 222 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3022,3587,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 223 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3022,3588,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 224 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3022,3597,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 225 | Wilderness Surface | Ladder | 14745 | Climb-up | 3022,3625,0 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 226 | Wilderness Surface | Ladder | 14748 | <missing> | 3022,3638,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 227 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3023,3588,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 228 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3023,3589,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 229 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3023,3594,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 230 | Wilderness Surface | Door | 14749 | Open | 3023,3627,0 | WallStraight/1 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 231 | Wilderness Surface | Door | 14749 | Open | 3024,3626,0 | WallStraight/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 232 | Wilderness Surface | Door | 14749 | Open | 3024,3637,0 | WallStraight/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 233 | Wilderness Surface | Door | 14749 | Open | 3033,3632,0 | WallStraight/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 234 | Wilderness Surface | Door | 14749 | Open | 3034,3626,0 | WallStraight/0 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 235 | Wilderness Surface | Door | 14749 | Open | 3034,3637,0 | WallStraight/0 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 236 | Wilderness Surface | Door | 14749 | Open | 3035,3627,0 | WallStraight/1 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 237 | Wilderness Surface | Door | 14749 | Open | 3035,3636,0 | WallStraight/3 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 238 | Wilderness Surface | Ladder | 14745 | Climb-up | 3036,3625,0 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 239 | Wilderness Surface | Ladder | 14745 | Climb-up | 3036,3638,0 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 240 | Wilderness Surface | loc.chaostemple_ruined | 34822 | Enter | 3059,3590,0 | CentrepieceStraight/3 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 241 | Wilderness Surface | Door | 14749 | Open | 3026,3699,0 | WallStraight/3 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 242 | Wilderness Surface | Door | 14749 | Open | 3027,3702,0 | WallStraight/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 243 | Wilderness Surface | Door | 14749 | Open | 3035,3707,0 | WallStraight/0 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 244 | Wilderness Surface | Door | 14749 | Open | 3038,3705,0 | WallStraight/3 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 245 | Wilderness Surface | Cave | 26766 | Enter | 3016,3738,0 | CentrepieceStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 246 | Wilderness Surface | Obelisk | 14827 | <missing> | 3033,3730,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 247 | Wilderness Surface | Obelisk | 14827 | <missing> | 3033,3734,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 248 | Wilderness Surface | Obelisk | 14827 | <missing> | 3037,3730,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 249 | Wilderness Surface | Obelisk | 14827 | <missing> | 3037,3734,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 250 | Wilderness Surface | Crevice | 40386 | Jump-Down | 3067,3740,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 251 | Wilderness Surface | Gate | 1727 | Open | 3008,3849,0 | WallStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 252 | Wilderness Surface | Gate | 1728 | Open | 3008,3850,0 | WallStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 253 | Wilderness Surface | Ladder | 18987 | Climb-down | 3017,3849,0 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 254 | Wilderness Surface | Dead tree | 1288 | <missing> | 3030,3851,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 255 | Wilderness Surface | Web | 733 | Slash | 3030,3852,0 | WallStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 256 | Wilderness Surface | Dead tree | 1287 | <missing> | 3030,3853,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 257 | Wilderness Surface | Runite rocks | 11376 | <missing> | 3059,3885,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 258 | Wilderness Surface | Runite rocks | 11377 | <missing> | 3060,3884,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 259 | Wilderness Surface | Ladder | 18989 | Climb-down | 3069,3856,0 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 260 | Wilderness Surface | Gate | 1728 | Open | 3071,3856,0 | WallStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 261 | Wilderness Surface | Gate | 1727 | Open | 3071,3857,0 | WallStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 262 | Wilderness Surface | Boulder | 10790 | <missing> | 3014,3958,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 263 | Wilderness Surface | Boulder | 10790 | <missing> | 3015,3953,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 264 | Wilderness Surface | Ship's ladder | 272 | Climb-up | 3018,3957,0 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 265 | Wilderness Surface | Door | 11727 | Open | 3038,3956,0 | WallStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 266 | Wilderness Surface | Door | 11727 | Pick-lock | 3038,3956,0 | WallStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 267 | Wilderness Surface | Pile of rope | 227 | <missing> | 3041,3948,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 268 | Wilderness Surface | Door | 11727 | Open | 3041,3959,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 269 | Wilderness Surface | Door | 11727 | Pick-lock | 3041,3959,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 270 | Wilderness Surface | Staircase | 16664 | Climb-down | 3044,3924,0 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 271 | Wilderness Surface | Door | 11727 | Open | 3044,3956,0 | WallStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 272 | Wilderness Surface | Door | 11727 | Pick-lock | 3044,3956,0 | WallStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 273 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3052,3946,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 274 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3053,3942,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 275 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3055,3944,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 276 | Wilderness Surface | Mithril rocks | 11372 | <missing> | 3055,3947,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 277 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3056,3950,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 278 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3057,3941,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 279 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3057,3944,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 280 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3057,3946,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 281 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3059,3943,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 282 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3059,3948,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 283 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3017,10152,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 284 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3017,10158,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 285 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3018,10150,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 286 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3018,10152,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 287 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3018,10154,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 288 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3018,10157,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 289 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3019,10147,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 290 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3019,10159,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 291 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3019,10160,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 292 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3019,10162,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 293 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3020,10123,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 294 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3020,10126,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 295 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3020,10149,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 296 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3020,10153,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 297 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3020,10155,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 298 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3020,10163,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 299 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3021,10119,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 300 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3021,10121,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 301 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3021,10151,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 302 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3021,10160,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 303 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3021,10161,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 304 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3022,10118,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 305 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3022,10120,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 306 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3022,10126,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 307 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3022,10147,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 308 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3022,10150,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 309 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3023,10119,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 310 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3023,10123,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 311 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3023,10128,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 312 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3023,10144,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 313 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3023,10150,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 314 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3023,10155,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 315 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3023,10157,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 316 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3024,10117,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 317 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3024,10124,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 318 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3024,10144,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 319 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3024,10145,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 320 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3024,10147,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 321 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3024,10148,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 322 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3024,10153,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 323 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3024,10162,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 324 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3025,10118,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 325 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3025,10119,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 326 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3025,10129,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 327 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3025,10158,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 328 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3025,10161,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 329 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3025,10163,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 330 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3025,10165,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 331 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3026,10121,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 332 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3026,10122,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 333 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3026,10124,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 334 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3026,10126,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 335 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3026,10128,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 336 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3026,10130,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 337 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3026,10131,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 338 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3026,10133,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 339 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3026,10140,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 340 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3026,10145,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 341 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3026,10156,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 342 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3026,10160,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 343 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3026,10162,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 344 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3027,10117,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 345 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3027,10132,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 346 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3027,10134,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 347 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3027,10136,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 348 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3027,10138,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 349 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3027,10147,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 350 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3027,10148,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 351 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3027,10152,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 352 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3027,10160,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 353 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3028,10122,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 354 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3028,10125,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 355 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3028,10143,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 356 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3028,10149,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 357 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3028,10154,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 358 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3028,10158,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 359 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3029,10126,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 360 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3029,10132,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 361 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3029,10134,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 362 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3029,10141,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 363 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3029,10145,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 364 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3029,10158,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 365 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3030,10119,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 366 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3030,10120,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 367 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3030,10121,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 368 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3030,10129,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 369 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3030,10136,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 370 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3030,10145,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 371 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3030,10153,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 372 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3031,10127,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 373 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3031,10132,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 374 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3031,10147,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 375 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3031,10148,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 376 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3031,10151,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 377 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3031,10155,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 378 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3031,10161,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 379 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3031,10163,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 380 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3032,10121,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 381 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3032,10123,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 382 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3032,10128,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 383 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3032,10150,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 384 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3033,10121,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 385 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3033,10126,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 386 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3033,10146,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 387 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3033,10148,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 388 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3033,10152,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 389 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3033,10155,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 390 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3033,10158,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 391 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3034,10123,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 392 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3034,10124,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 393 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3034,10125,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 394 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3034,10128,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 395 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3034,10130,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 396 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3034,10157,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 397 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3035,10127,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 398 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3035,10133,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 399 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3035,10141,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 400 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3035,10148,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 401 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3035,10153,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 402 | Wilderness Dungeons | Crevice | 26769 | Use | 3035,10158,0 | WallStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 403 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3036,10125,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 404 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3036,10143,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 405 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3036,10153,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 406 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3037,10128,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 407 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3037,10131,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 408 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3037,10145,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 409 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3037,10151,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 410 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3038,10126,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 411 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3038,10129,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 412 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3038,10147,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 413 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3038,10152,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 414 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3039,10125,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 415 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3039,10137,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 416 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3039,10142,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 417 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3039,10149,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 418 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3040,10124,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 419 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3040,10133,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 420 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3040,10135,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 421 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3041,10125,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 422 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3041,10132,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 423 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3041,10138,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 424 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3041,10143,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 425 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3041,10148,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 426 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3041,10150,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 427 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3042,10122,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 428 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3042,10127,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 429 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3042,10133,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 430 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3042,10137,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 431 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3042,10140,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 432 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3042,10145,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 433 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3043,10124,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 434 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3043,10128,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 435 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3043,10140,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 436 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3043,10147,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 437 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3043,10148,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 438 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3044,10121,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 439 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3044,10123,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 440 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3044,10131,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 441 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3044,10134,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 442 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3044,10135,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 443 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3044,10146,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 444 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3044,10149,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 445 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3046,10121,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 446 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3046,10124,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 447 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3046,10125,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 448 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3046,10133,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 449 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3046,10141,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 450 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3046,10142,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 451 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3046,10148,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 452 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3047,10121,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 453 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3047,10135,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 454 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3047,10136,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 455 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3047,10146,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 456 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3048,10126,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 457 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3048,10146,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 458 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3049,10121,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 459 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3049,10123,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 460 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3049,10125,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 461 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3049,10131,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 462 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3049,10133,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 463 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3049,10141,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 464 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3049,10144,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 465 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3050,10129,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 466 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3050,10139,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 467 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3050,10145,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 468 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3051,10119,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 469 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3051,10143,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 470 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3052,10121,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 471 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3052,10126,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 472 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3052,10134,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 473 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3052,10141,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 474 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3052,10143,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 475 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3053,10122,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 476 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3053,10130,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 477 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3053,10132,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 478 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3054,10124,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 479 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3054,10136,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 480 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3054,10139,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 481 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3055,10119,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 482 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3055,10125,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 483 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3055,10138,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 484 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3056,10134,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 485 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3057,10119,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 486 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3057,10120,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 487 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3057,10121,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 488 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3057,10127,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 489 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3057,10133,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 490 | Wilderness Dungeons | loc.godwars_entrance_snow3 | 26398 | <missing> | 3058,10122,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 491 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3058,10131,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 492 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3059,10119,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 493 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3059,10123,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 494 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3059,10129,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 495 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3059,10133,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 496 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3060,10121,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 497 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3060,10124,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 498 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3060,10127,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 499 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3061,10119,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 500 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3061,10123,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 501 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3061,10130,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 502 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3062,10120,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 503 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor02 | 26409 | <missing> | 3062,10126,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 504 | Wilderness Dungeons | Crevice | 26769 | Use | 3062,10131,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 505 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3063,10121,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 506 | Wilderness Dungeons | loc.godwars_entrance_snow2 | 26397 | <missing> | 3063,10125,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 507 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3063,10128,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 508 | Wilderness Dungeons | loc.godwars_entrance_boulder_floor01 | 26408 | <missing> | 3064,10122,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 509 | Wilderness Dungeons | loc.godwars_entrance_snow1 | 26396 | <missing> | 3065,10122,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 510 | Wilderness Dungeons | Ladder | 18990 | Climb-up | 3017,10249,0 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 511 | Wilderness Dungeons | Runite rocks | 11376 | <missing> | 3046,10265,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 512 | Wilderness Dungeons | loc.cavewall_face1_for_lever | 6628 | <missing> | 3067,10252,0 | WallStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 513 | Wilderness Dungeons | Lever | 1816 | Pull | 3067,10253,0 | WallDecorStraightNoOffset/3 | exact-coordinate + direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 514 | Wilderness Dungeons | Ladder | 18988 | Climb-up | 3069,10256,0 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 515 | Wilderness Dungeons | Cave web | 727 | <missing> | 3020,10333,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 516 | Wilderness Dungeons | Gate | 1569 | Open | 3022,10311,0 | WallStraight/2 | exact-coordinate + content-group:28 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 517 | Wilderness Dungeons | Gate | 1568 | Open | 3022,10312,0 | WallStraight/2 | exact-coordinate + content-group:27 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 518 | Wilderness Dungeons | Cave web | 727 | <missing> | 3026,10323,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 519 | Wilderness Dungeons | Cave web | 727 | <missing> | 3029,10347,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 520 | Wilderness Dungeons | Cave web | 727 | <missing> | 3033,10331,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 521 | Wilderness Dungeons | Gate | 1569 | Open | 3040,10307,0 | WallStraight/2 | exact-coordinate + content-group:28 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 522 | Wilderness Dungeons | Gate | 1568 | Open | 3040,10308,0 | WallStraight/2 | exact-coordinate + content-group:27 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 523 | Wilderness Dungeons | Staircase | 16665 | Climb-up | 3044,10324,0 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 524 | Wilderness Dungeons | Gate | 1569 | Open | 3044,10341,0 | WallStraight/2 | exact-coordinate + content-group:28 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 525 | Wilderness Dungeons | Gate | 1568 | Open | 3044,10342,0 | WallStraight/2 | exact-coordinate + content-group:27 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 526 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3046,10327,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 527 | Wilderness Dungeons | Crevice | 19043 | Use | 3046,10327,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 528 | Wilderness Dungeons | Rockslide | 11190 | <missing> | 3047,10318,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 529 | Wilderness Dungeons | Rockslide | 11192 | <missing> | 3047,10320,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 530 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3048,10335,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 531 | Wilderness Dungeons | Crevice | 19043 | Use | 3048,10335,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 532 | Wilderness Surface | Obelisk of Air | 2152 | <missing> | 3088,3569,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 533 | Wilderness Surface | Ladder | 16680 | Climb-down | 3088,3571,0 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 534 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3103,3565,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 535 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3103,3566,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 536 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3104,3567,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 537 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3104,3570,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 538 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3105,3569,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 539 | Ferox Enclave | Barrier | 39653 | Pass-Through | 3123,3628,0 | WallStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 540 | Ferox Enclave | loc.wildy_hub_entrance_fx | 39656 | <missing> | 3123,3628,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 541 | Ferox Enclave | Barrier | 39652 | Pass-Through | 3123,3629,0 | WallStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 542 | Ferox Enclave | loc.tournament_clanwars_tempportal_2 | 1729 | Use | 3123,3630,0 | CentrepieceStraight/1 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 543 | Ferox Enclave | Death's domain | 39637 | Enter | 3125,3630,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 544 | Ferox Enclave | Ladder | 14745 | Climb-up | 3125,3633,0 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 545 | Ferox Enclave | Ladder | 14745 | Climb-up | 3125,3639,0 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 546 | Ferox Enclave | Challenge portal | 26642 | Enter | 3126,3620,0 | CentrepieceStraight/1 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 547 | Ferox Enclave | Free-for-all portal | 26645 | Enter | 3127,3626,0 | CentrepieceStraight/0 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 548 | Wilderness Surface | Corpse | 39675 | <missing> | 3131,3611,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 549 | Ferox Enclave | loc.wildy_hub_door_bars | 39685 | <missing> | 3131,3636,0 | WallStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 550 | Ferox Enclave | Stairs | 39643 | Climb | 3132,3619,0 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 551 | Ferox Enclave | Stairs | 39643 | Climb | 3132,3623,0 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 552 | Ferox Enclave | loc.wildy_hub_door_bars | 39685 | <missing> | 3133,3621,0 | WallStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 553 | Ferox Enclave | loc.wildy_hub_door_bars_m | 39686 | <missing> | 3133,3622,0 | WallStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 554 | Ferox Enclave | Barrier | 39652 | Pass-Through | 3134,3617,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 555 | Ferox Enclave | loc.wildy_hub_entrance_fx | 39656 | <missing> | 3134,3617,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 556 | Ferox Enclave | Barrier | 39653 | Pass-Through | 3134,3639,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 557 | Ferox Enclave | loc.wildy_hub_entrance_fx | 39656 | <missing> | 3134,3639,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 558 | Ferox Enclave | Barrier | 39653 | Pass-Through | 3135,3617,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 559 | Ferox Enclave | Barrier | 39652 | Pass-Through | 3135,3639,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 560 | Wilderness Surface | Cavern | 31555 | Enter | 3073,3654,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 561 | Wilderness Surface | Trapdoor | 31557 | <missing> | 3103,3655,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 562 | Wilderness Surface | Cave Entrance | 47141 | Enter | 3115,3676,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 563 | Wilderness Surface | Adamantite rocks | 11375 | <missing> | 3075,3757,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 564 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3075,3759,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 565 | Wilderness Surface | Adamantite rocks | 11375 | <missing> | 3075,3760,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 566 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3075,3771,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 567 | Wilderness Surface | Adamantite rocks | 11375 | <missing> | 3076,3759,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 568 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3076,3770,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 569 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3076,3771,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 570 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3077,3763,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 571 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3078,3756,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 572 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3078,3767,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 573 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3079,3766,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 574 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3079,3767,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 575 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3080,3765,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 576 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3080,3766,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 577 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3080,3773,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 578 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3080,3774,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 579 | Wilderness Surface | Adamantite rocks | 11375 | <missing> | 3081,3755,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 580 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3081,3765,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 581 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3081,3773,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 582 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3081,3774,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 583 | Wilderness Surface | Adamantite rocks | 11375 | <missing> | 3082,3755,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 584 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3082,3774,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 585 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3083,3749,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 586 | Wilderness Surface | Adamantite rocks | 11375 | <missing> | 3083,3754,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 587 | Wilderness Surface | Mithril rocks | 11372 | <missing> | 3083,3760,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 588 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3084,3748,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 589 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3084,3749,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 590 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3084,3761,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 591 | Wilderness Surface | Mithril rocks | 11372 | <missing> | 3084,3764,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 592 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3084,3765,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 593 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3084,3770,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 594 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3085,3748,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 595 | Wilderness Surface | Mithril rocks | 11372 | <missing> | 3085,3761,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 596 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3085,3765,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 597 | Wilderness Surface | Mithril rocks | 11372 | <missing> | 3085,3766,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 598 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3085,3770,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 599 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3086,3748,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 600 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3086,3755,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 601 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3086,3764,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 602 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3087,3749,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 603 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3087,3756,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 604 | Wilderness Surface | Mithril rocks | 11372 | <missing> | 3087,3757,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 605 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3087,3758,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 606 | Wilderness Surface | Mithril rocks | 11372 | <missing> | 3088,3756,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 607 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3088,3763,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 608 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3088,3769,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 609 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3089,3764,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 610 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3089,3768,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 611 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3090,3759,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 612 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3090,3767,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 613 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3090,3768,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 614 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3091,3760,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 615 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3091,3768,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 616 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3091,3774,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 617 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3092,3760,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 618 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3092,3761,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 619 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3093,3761,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 620 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3093,3773,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 621 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3096,3754,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 622 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3097,3754,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 623 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3097,3755,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 624 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3098,3755,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 625 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3099,3765,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 626 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3099,3766,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 627 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3099,3767,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 628 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3099,3768,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 629 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3100,3762,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 630 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3100,3765,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 631 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3100,3766,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 632 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3101,3761,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 633 | Wilderness Surface | Mithril rocks | 11372 | <missing> | 3101,3762,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 634 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3101,3765,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 635 | Wilderness Surface | Iron rocks | 11365 | <missing> | 3101,3768,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 636 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3102,3759,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 637 | Wilderness Surface | Obelisk | 14828 | <missing> | 3104,3792,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 638 | Wilderness Surface | Obelisk | 14828 | <missing> | 3104,3796,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 639 | Wilderness Surface | Obelisk | 14828 | <missing> | 3108,3792,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 640 | Wilderness Surface | Obelisk | 14828 | <missing> | 3108,3796,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 641 | Wilderness Surface | Trapdoor | 31557 | <missing> | 3122,3805,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 642 | Wilderness Surface | Cavern | 31556 | Enter | 3124,3831,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 643 | Wilderness Surface | Gate | 1568 | Open | 3075,3867,0 | WallStraight/1 | exact-coordinate + content-group:27 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 644 | Wilderness Surface | Gate | 1569 | Open | 3076,3867,0 | WallStraight/1 | exact-coordinate + content-group:28 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 645 | Wilderness Surface | Stepping stone | 14917 | Cross | 3092,3879,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 646 | Wilderness Surface | loc.agility_shortcut_icon | 17030 | <missing> | 3093,3880,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 647 | Wilderness Surface | Dead tree | 1287 | <missing> | 3114,3859,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 648 | Wilderness Surface | Web | 733 | Slash | 3115,3859,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 649 | Wilderness Surface | Dead tree | 1288 | <missing> | 3116,3859,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 650 | Wilderness Surface | Lever | 5959 | Pull | 3090,3956,0 | WallDecorStraightNoOffset/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 651 | Wilderness Surface | Web | 733 | Slash | 3092,3957,0 | WallStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 652 | Wilderness Surface | Web | 733 | Slash | 3095,3957,0 | WallStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 653 | Wilderness Surface | Lever | 9706 | Pull | 3104,3956,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 654 | Wilderness Surface | Lever | 9707 | Pull | 3105,3952,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 655 | Wilderness Surface | Web | 733 | Slash | 3105,3958,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 656 | Wilderness Surface | Web | 733 | Slash | 3106,3958,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 657 | Wilderness Surface | Rockslide | 11192 | <missing> | 3080,9802,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 658 | Wilderness Surface | Rockslide | 11192 | <missing> | 3085,9806,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 659 | Wilderness Surface | Rockslide | 11192 | <missing> | 3086,9794,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 660 | Wilderness Surface | Rocks | 29491 | Climb | 3091,9805,0 | CentrepieceStraight/0 | direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 661 | Wilderness Surface | Rocks | 29491 | Climb | 3091,9806,0 | CentrepieceStraight/0 | direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 662 | Wilderness Surface | Gate | 29489 | Open | 3091,9815,0 | WallStraight/1 | direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 663 | Wilderness Surface | Gate | 29489 | Quick-exit | 3091,9815,0 | WallStraight/1 |  | NO_HANDLER | FAIL | PENDING |
| 664 | Wilderness Surface | Rocks | 29491 | Climb | 3092,9805,0 | CentrepieceStraight/0 | direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 665 | Wilderness Surface | Rocks | 29491 | Climb | 3092,9806,0 | CentrepieceStraight/0 | direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 666 | Wilderness Surface | Gate | 29488 | Open | 3092,9815,0 | WallStraight/1 | direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 667 | Wilderness Surface | Gate | 29488 | Quick-exit | 3092,9815,0 | WallStraight/1 |  | NO_HANDLER | FAIL | PENDING |
| 668 | Wilderness Surface | Gate | 29487 | Open | 3095,9832,0 | WallStraight/0 | direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 669 | Wilderness Surface | Gate | 29486 | Open | 3095,9833,0 | WallStraight/0 | direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 670 | Wilderness Surface | Rockslide | 11192 | <missing> | 3096,9794,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 671 | Wilderness Surface | Rockslide | 11192 | <missing> | 3100,9803,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 672 | Wilderness Surface | Rockslide | 11193 | <missing> | 3102,9803,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 673 | Wilderness Surface | Ladder | 17385 | Climb-up | 3116,9852,0 | CentrepieceStraight/3 | content-group:23 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 674 | Wilderness Surface | Rockslide | 11190 | <missing> | 3096,9888,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 675 | Wilderness Surface | Rockslide | 11193 | <missing> | 3096,9890,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 676 | Wilderness Surface | Ladder | 17385 | Climb-up | 3097,9867,0 | CentrepieceStraight/3 | exact-coordinate + content-group:23 | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 677 | Wilderness Surface | Rockslide | 11190 | <missing> | 3097,9872,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 678 | Wilderness Surface | Rockslide | 11193 | <missing> | 3102,9885,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 679 | Wilderness Surface | Gate | 1569 | Open | 3103,9909,0 | WallStraight/2 | exact-coordinate + content-group:28 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 680 | Wilderness Surface | Gate | 1568 | Open | 3103,9910,0 | WallStraight/2 | exact-coordinate + content-group:27 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 681 | Wilderness Surface | Rockslide | 11192 | <missing> | 3106,9883,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 682 | Wilderness Surface | Rockslide | 11190 | <missing> | 3107,9907,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 683 | Wilderness Surface | Rockslide | 11191 | <missing> | 3108,9883,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 684 | Wilderness Surface | Rockslide | 11192 | <missing> | 3109,9907,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 685 | Wilderness Surface | Rockslide | 11192 | <missing> | 3110,9883,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 686 | Wilderness Surface | Rockslide | 11192 | <missing> | 3115,9871,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 687 | Wilderness Surface | Rockslide | 11190 | <missing> | 3115,9873,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 688 | Wilderness Surface | Rockslide | 11192 | <missing> | 3117,9873,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 689 | Wilderness Surface | Rockslide | 11190 | <missing> | 3117,9893,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 690 | Wilderness Surface | Obstacle pipe | 62409 | Squeeze-through | 3122,9881,0 | CentrepieceStraight/1 |  | NO_HANDLER | FAIL | PENDING |
| 691 | Wilderness Surface | loc.agility_shortcut_icon | 17030 | <missing> | 3122,9882,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 692 | Wilderness Surface | Rockslide | 11191 | <missing> | 3123,9891,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 693 | Wilderness Surface | Rockslide | 11431 | <missing> | 3125,9860,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 694 | Wilderness Surface | Rockslide | 11193 | <missing> | 3126,9876,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 695 | Wilderness Surface | Rockslide | 11190 | <missing> | 3126,9878,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 696 | Wilderness Surface | Rockslide | 11426 | <missing> | 3127,9866,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 697 | Wilderness Surface | Rockslide | 11193 | <missing> | 3127,9907,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 698 | Wilderness Surface | Rockslide | 11428 | <missing> | 3129,9863,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 699 | Wilderness Surface | Rockslide | 11193 | <missing> | 3129,9905,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 700 | Wilderness Surface | Rockslide | 11190 | <missing> | 3129,9907,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 701 | Wilderness Surface | Rockslide | 11193 | <missing> | 3129,9909,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 702 | Wilderness Surface | Rockslide | 11426 | <missing> | 3131,9863,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 703 | Wilderness Surface | Gate | 1727 | Open | 3131,9917,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 704 | Wilderness Surface | Gate | 1728 | Open | 3132,9917,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 705 | Wilderness Surface | Mithril rocks | 11373 | <missing> | 3134,9871,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 706 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3134,9872,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 707 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3134,9878,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 708 | Wilderness Surface | Tin rocks | 11361 | <missing> | 3134,9879,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 709 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3135,9869,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 710 | Wilderness Dungeons | Obelisk of Earth | 2150 | <missing> | 3086,9932,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 711 | Wilderness Dungeons | Ladder | 17385 | Climb-up | 3088,9971,0 | CentrepieceStraight/1 | exact-coordinate + content-group:23 | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 712 | Wilderness Dungeons | Gate | 1568 | Open | 3105,9944,0 | WallStraight/1 | exact-coordinate + content-group:27 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 713 | Wilderness Dungeons | Gate | 1569 | Open | 3106,9944,0 | WallStraight/1 | exact-coordinate + content-group:28 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 714 | Wilderness Dungeons | Cave rocks | 11174 | <missing> | 3116,9973,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 715 | Wilderness Dungeons | Cave rocks | 11175 | <missing> | 3116,9975,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 716 | Wilderness Dungeons | Cave rocks | 11174 | <missing> | 3118,9975,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 717 | Wilderness Dungeons | Cave rocks | 11174 | <missing> | 3118,9980,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 718 | Wilderness Dungeons | Cave rocks | 11174 | <missing> | 3122,9978,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 719 | Wilderness Dungeons | Cave rocks | 11175 | <missing> | 3124,9972,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 720 | Wilderness Dungeons | Cave rocks | 11174 | <missing> | 3125,9975,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 721 | Wilderness Dungeons | Cave rocks | 11175 | <missing> | 3126,9975,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 722 | Wilderness Dungeons | Cave rocks | 11174 | <missing> | 3117,9994,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 723 | Wilderness Dungeons | Cave rocks | 11175 | <missing> | 3118,9997,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 724 | Wilderness Dungeons | Cave rocks | 11174 | <missing> | 3120,9998,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 725 | Wilderness Dungeons | Cave rocks | 11175 | <missing> | 3120,9999,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 726 | Wilderness Dungeons | Cave rocks | 11175 | <missing> | 3124,9997,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 727 | Wilderness Dungeons | Cave rocks | 11174 | <missing> | 3126,9996,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 728 | Wilderness Dungeons | Cave rocks | 11174 | <missing> | 3127,9993,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 729 | Ferox Enclave | loc.wildy_hub_door_bars_m | 39686 | <missing> | 3136,3621,0 | WallStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 730 | Ferox Enclave | loc.wildy_hub_door_bars | 39685 | <missing> | 3136,3622,0 | WallStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 731 | Ferox Enclave | Stairs | 39643 | Climb | 3137,3619,0 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 732 | Ferox Enclave | Stairs | 39643 | Climb | 3137,3623,0 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 733 | Ferox Enclave | Stairs | 39643 | Climb | 3138,3636,0 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 734 | Ferox Enclave | Stairs | 39643 | Climb | 3138,3637,0 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 735 | Ferox Enclave | loc.bh_portal_stairs_trim | 47423 | <missing> | 3139,3620,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 736 | Ferox Enclave | loc.bh_portal_stairs | 47422 | <missing> | 3139,3621,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 737 | Ferox Enclave | loc.bh_portal_stairs | 47422 | <missing> | 3139,3622,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 738 | Ferox Enclave | loc.bh_portal_stairs_trim | 47423 | <missing> | 3139,3623,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 739 | Ferox Enclave | loc.bh_portal_stairs | 47422 | <missing> | 3140,3620,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 740 | Ferox Enclave | Bounty Hunter portal | 47346 | Enter | 3140,3621,0 | CentrepieceStraight/1 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 741 | Ferox Enclave | loc.bh_portal_floor | 47421 | <missing> | 3140,3621,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 742 | Ferox Enclave | loc.bh_portal_floor | 47421 | <missing> | 3140,3622,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 743 | Ferox Enclave | loc.bh_portal_stairs | 47422 | <missing> | 3140,3623,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 744 | Ferox Enclave | loc.bh_portal_stairs | 47422 | <missing> | 3141,3620,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 745 | Ferox Enclave | loc.bh_portal_floor | 47421 | <missing> | 3141,3621,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 746 | Ferox Enclave | loc.bh_portal_floor | 47421 | <missing> | 3141,3622,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 747 | Ferox Enclave | loc.bh_portal_stairs | 47422 | <missing> | 3141,3623,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 748 | Ferox Enclave | loc.wildy_hub_door_bars_m | 39686 | <missing> | 3141,3632,0 | WallStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 749 | Ferox Enclave | Competitive | 39640 | Pass-Through | 3141,3639,0 | WallStraight/3 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 750 | Ferox Enclave | loc.bh_portal_stairs_trim | 47423 | <missing> | 3142,3620,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 751 | Ferox Enclave | Bounty Hunter portal | 47428 | <missing> | 3142,3620,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 752 | Ferox Enclave | loc.bh_portal_stairs | 47422 | <missing> | 3142,3621,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 753 | Ferox Enclave | loc.bh_portal_stairs | 47422 | <missing> | 3142,3622,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 754 | Ferox Enclave | loc.bh_portal_stairs_trim | 47423 | <missing> | 3142,3623,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 755 | Ferox Enclave | Bounty Hunter portal | 47427 | <missing> | 3142,3623,0 | CentrepieceDiagonal/1 |  | WRONG_OPTION | FAIL | PENDING |
| 756 | Ferox Enclave | loc.wildy_hub_door_bars | 39685 | <missing> | 3142,3632,0 | WallStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 757 | Ferox Enclave | Competitive | 39640 | Pass-Through | 3142,3639,0 | WallStraight/3 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 758 | Ferox Enclave | Stairs | 39643 | Climb | 3144,3636,0 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 759 | Ferox Enclave | Stairs | 39643 | Climb | 3144,3637,0 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 760 | Ferox Enclave | Castle Wars portal | 30386 | Enter | 3146,3638,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 761 | Ferox Enclave | loc.wildy_hub_door_bars | 39685 | <missing> | 3150,3641,0 | WallStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 762 | Ferox Enclave | loc.wildy_hub_door_bars | 39685 | <missing> | 3151,3641,0 | WallStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 763 | Ferox Enclave | Stairs | 39647 | <missing> | 3153,3643,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 764 | Wilderness Surface | Obelisk | 14829 | <missing> | 3154,3618,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 765 | Wilderness Surface | Obelisk | 14829 | <missing> | 3154,3622,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 766 | Ferox Enclave | Barrier | 39652 | Pass-Through | 3154,3634,0 | WallStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 767 | Ferox Enclave | loc.wildy_hub_entrance_fx | 39656 | <missing> | 3154,3634,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 768 | Ferox Enclave | Barrier | 39653 | Pass-Through | 3154,3635,0 | WallStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 769 | Wilderness Surface | Obelisk | 14829 | <missing> | 3158,3618,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 770 | Wilderness Surface | Obelisk | 14829 | <missing> | 3158,3622,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 771 | Wilderness Surface | Ladder | 14745 | Climb-up | 3184,3610,0 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 772 | Wilderness Surface | Memorial | 46996 | Enter | 3180,3683,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 773 | Wilderness Surface | loc.fai_falador_furnace_door_right_mirror | 24011 | <missing> | 3145,3735,0 | WallStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 774 | Wilderness Surface | loc.fai_falador_furnace_door_left | 24010 | <missing> | 3145,3737,0 | WallStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 775 | Wilderness Surface | Web | 733 | Slash | 3147,3727,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 776 | Wilderness Surface | Ladder | 17028 | Climb-up | 3154,3743,0 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 777 | Wilderness Surface | Web | 733 | Slash | 3163,3736,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 778 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3178,3744,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 779 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3180,3745,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 780 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3180,3746,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 781 | Wilderness Surface | Web sack | 47069 | <missing> | 3181,3744,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 782 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3181,3745,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 783 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3181,3746,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 784 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3182,3743,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 785 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3182,3744,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 786 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3182,3745,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 787 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3182,3746,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 788 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3182,3747,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 789 | Wilderness Surface | loc.wild_venanatis_web_floor07 | 47076 | <missing> | 3182,3747,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 790 | Wilderness Surface | Web | 733 | Slash | 3183,3733,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 791 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3183,3743,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 792 | Wilderness Surface | loc.wild_venanatis_web_floor07 | 47076 | <missing> | 3183,3743,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 793 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3183,3744,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 794 | Wilderness Surface | Cave Entrance | 47078 | Enter | 3183,3744,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 795 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3183,3745,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 796 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3183,3746,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 797 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3183,3747,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 798 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3184,3742,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 799 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3184,3744,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 800 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3184,3745,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 801 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3184,3746,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 802 | Wilderness Surface | loc.wildoutlever | 1815 | Pull | 3153,3923,0 | WallDecorStraightOffset/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 803 | Wilderness Surface | Dead tree | 1287 | <missing> | 3157,3951,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 804 | Wilderness Surface | Web | 733 | Slash | 3158,3951,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 805 | Wilderness Surface | Dead tree | 1288 | <missing> | 3159,3951,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 806 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3175,3925,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 807 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3175,3927,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 808 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3175,3939,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 809 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3175,3941,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 810 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3176,3926,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 811 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3176,3940,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 812 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3177,3925,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 813 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3177,3927,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 814 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3177,3939,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 815 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3177,3941,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 816 | Wilderness Surface | Gold rocks | 11370 | <missing> | 3182,3941,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 817 | Wilderness Surface | Gold rocks | 11370 | <missing> | 3183,3940,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 818 | Wilderness Surface | Gold rocks | 11370 | <missing> | 3184,3941,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 819 | Wilderness Surface | Gate | 26760 | Open | 3184,3944,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 820 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3190,3941,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 821 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3190,3943,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 822 | Wilderness Surface | Door | 11726 | Open | 3190,3957,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 823 | Wilderness Surface | Door | 11726 | Pick-lock | 3190,3957,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 824 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3191,3942,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 825 | Wilderness Surface | Door | 11726 | Open | 3191,3963,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 826 | Wilderness Surface | Door | 11726 | Pick-lock | 3191,3963,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 827 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3192,3925,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 828 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3192,3927,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 829 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3192,3941,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 830 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3192,3943,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 831 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3193,3926,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 832 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3193,3933,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 833 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3193,3935,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 834 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3194,3925,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 835 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3194,3927,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 836 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3194,3934,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 837 | Wilderness Surface | Gold rocks | 11370 | <missing> | 3195,3933,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 838 | Wilderness Surface | Mithril rocks | 11372 | <missing> | 3195,3935,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 839 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3136,9868,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 840 | Wilderness Surface | Silver rocks | 11368 | <missing> | 3137,9874,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 841 | Wilderness Surface | Silver rocks | 11369 | <missing> | 3137,9880,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 842 | Wilderness Surface | Coal rocks | 11367 | <missing> | 3138,9868,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 843 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3138,9873,0 | CentrepieceDiagonal/0 |  | WRONG_OPTION | FAIL | PENDING |
| 844 | Wilderness Surface | Silver rocks | 11369 | <missing> | 3138,9879,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 845 | Wilderness Surface | Coal rocks | 11366 | <missing> | 3139,9868,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 846 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3139,9875,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 847 | Wilderness Surface | Adamantite rocks | 11374 | <missing> | 3140,9874,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 848 | Wilderness Surface | Tin rocks | 11361 | <missing> | 3141,9868,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 849 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3141,9872,0 | CentrepieceDiagonal/0 |  | WRONG_OPTION | FAIL | PENDING |
| 850 | Wilderness Surface | Copper rocks | 11161 | <missing> | 3141,9880,0 | CentrepieceDiagonal/2 |  | WRONG_OPTION | FAIL | PENDING |
| 851 | Wilderness Surface | Iron rocks | 11364 | <missing> | 3143,9876,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 852 | Wilderness Surface | Copper rocks | 11161 | <missing> | 3143,9877,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 853 | Wilderness Surface | Gate | 1569 | Open | 3145,9870,0 | WallStraight/2 | exact-coordinate + content-group:28 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 854 | Wilderness Surface | Gate | 1568 | Open | 3145,9871,0 | WallStraight/2 | exact-coordinate + content-group:27 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 855 | Wilderness Surface | Obstacle pipe | 16511 | Squeeze-through | 3150,9906,0 | CentrepieceStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 856 | Wilderness Surface | loc.agility_shortcut_icon | 17030 | <missing> | 3153,9905,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 857 | Wilderness Surface | Obstacle pipe | 16511 | Squeeze-through | 3153,9906,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 858 | Wilderness Surface | Large sewer pipe | 872 | <missing> | 3171,9901,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 859 | Wilderness Surface | Gate | 32534 | Open | 3174,9901,0 | WallStraight/3 | direct-object-id | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 860 | Wilderness Dungeons | Soul Wars Portal | 40475 | Enter | 3156,10025,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 861 | Wilderness Dungeons | loc.wildy_hub_grotto_exit_inactive | 39649 | <missing> | 3156,10040,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 862 | Wilderness Dungeons | Opening | 39648 | Exit | 3162,10042,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 863 | Wilderness Dungeons | loc.qip_digsite_tent_door | 17361 | <missing> | 3170,10006,0 | WallStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 864 | Wilderness Dungeons | Blocked opening | 40387 | <missing> | 3194,10055,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 865 | Wilderness Dungeons | Pillar | 31561 | Jump-to | 3180,10209,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 866 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3181,10209,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 867 | Wilderness Surface | Cave | 678 | Enter | 3201,3679,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 868 | Wilderness Surface | Obelisk | 14830 | <missing> | 3225,3665,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 869 | Wilderness Surface | Obelisk | 14830 | <missing> | 3225,3669,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 870 | Wilderness Surface | Obelisk | 14830 | <missing> | 3229,3665,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 871 | Wilderness Surface | Obelisk | 14830 | <missing> | 3229,3669,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 872 | Wilderness Surface | Stairs | 40388 | <missing> | 3259,3664,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 873 | Wilderness Surface | loc.agility_shortcut_icon | 17030 | <missing> | 3201,3807,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 874 | Wilderness Surface | Stepping stone | 14918 | Cross | 3201,3808,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 875 | Wilderness Surface | Crevice | 46995 | Jump-Down | 3221,3787,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 876 | Wilderness Surface | Cave | 47175 | Enter | 3260,3832,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 877 | Wilderness Surface | Gate | 1728 | Open | 3201,3856,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 878 | Wilderness Surface | Gate | 1727 | Open | 3202,3856,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 879 | Wilderness Surface | Rockslide | 11432 | <missing> | 3219,3950,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 880 | Wilderness Surface | Rockslide | 11435 | <missing> | 3220,3942,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 881 | Wilderness Surface | Rockslide | 11435 | <missing> | 3221,3938,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 882 | Wilderness Surface | Rockslide | 11435 | <missing> | 3221,3951,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 883 | Wilderness Surface | Rockslide | 11432 | <missing> | 3222,3943,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 884 | Wilderness Surface | Gate | 1728 | Open | 3224,3904,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 885 | Wilderness Surface | Rockslide | 11432 | <missing> | 3224,3953,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 886 | Wilderness Surface | Gate | 1727 | Open | 3225,3904,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 887 | Wilderness Surface | Rockslide | 11435 | <missing> | 3225,3947,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 888 | Wilderness Surface | Rockslide | 11435 | <missing> | 3227,3933,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 889 | Wilderness Surface | Rockslide | 11435 | <missing> | 3227,3948,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 890 | Wilderness Surface | Rockslide | 11435 | <missing> | 3227,3954,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 891 | Wilderness Surface | Rockslide | 11432 | <missing> | 3229,3938,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 892 | Wilderness Surface | Rockslide | 11432 | <missing> | 3229,3948,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 893 | Wilderness Surface | Cavern | 26762 | Enter | 3231,3936,0 | CentrepieceStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 894 | Wilderness Surface | Cavern | 26762 | Enter | 3231,3951,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 895 | Wilderness Surface | Rockslide | 11435 | <missing> | 3232,3932,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 896 | Wilderness Surface | Rockslide | 11433 | <missing> | 3232,3955,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 897 | Wilderness Surface | Rockslide | 11435 | <missing> | 3236,3955,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 898 | Wilderness Surface | Rockslide | 11432 | <missing> | 3238,3933,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 899 | Wilderness Surface | Rockslide | 11432 | <missing> | 3238,3940,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 900 | Wilderness Surface | Rockslide | 11432 | <missing> | 3239,3955,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 901 | Wilderness Surface | Rockslide | 11434 | <missing> | 3241,3941,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 902 | Wilderness Surface | Cavern | 26762 | Enter | 3241,3949,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 903 | Wilderness Surface | Rockslide | 11435 | <missing> | 3241,3953,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 904 | Wilderness Surface | Rockslide | 11435 | <missing> | 3242,3933,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 905 | Wilderness Surface | Rockslide | 11433 | <missing> | 3243,3935,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 906 | Wilderness Surface | Rockslide | 11435 | <missing> | 3244,3954,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 907 | Wilderness Surface | Rockslide | 11435 | <missing> | 3247,3937,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 908 | Wilderness Surface | Rockslide | 11435 | <missing> | 3247,3949,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 909 | Wilderness Surface | Rockslide | 11432 | <missing> | 3247,3956,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 910 | Wilderness Surface | Rockslide | 11435 | <missing> | 3250,3956,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 911 | Wilderness Surface | Rockslide | 11432 | <missing> | 3251,3941,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 912 | Wilderness Surface | Rockslide | 11434 | <missing> | 3251,3946,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 913 | Wilderness Surface | Rockslide | 11432 | <missing> | 3252,3958,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 914 | Wilderness Surface | Rockslide | 11435 | <missing> | 3253,3942,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 915 | Wilderness Surface | Rockslide | 11434 | <missing> | 3253,3952,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 916 | Wilderness Surface | Rockslide | 11435 | <missing> | 3254,3958,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 917 | Wilderness Surface | Rockslide | 11434 | <missing> | 3255,3949,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 918 | Wilderness Surface | Rockslide | 11433 | <missing> | 3256,3944,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 919 | Wilderness Surface | Rockslide | 11434 | <missing> | 3258,3951,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 920 | Wilderness Surface | Rockslide | 11434 | <missing> | 3258,3955,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 921 | Wilderness Dungeons | Stairs | 31558 | Climb-up | 3218,10058,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 922 | Wilderness Dungeons | Pillar | 31561 | Jump-to | 3220,10086,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 923 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3221,10086,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 924 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3200,10135,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 925 | Wilderness Dungeons | Pillar | 31561 | Jump-to | 3200,10136,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 926 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3241,10144,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 927 | Wilderness Dungeons | Pillar | 31561 | Jump-to | 3241,10145,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 928 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3202,10195,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 929 | Wilderness Dungeons | Pillar | 31561 | Jump-to | 3202,10196,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 930 | Wilderness Dungeons | Blocked opening | 40387 | <missing> | 3240,10235,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 931 | Wilderness Dungeons | Stairs | 43868 | Climb-up | 3244,10215,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 932 | Wilderness Dungeons | Crevice | 26763 | Use | 3232,10352,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 933 | Wilderness Dungeons | Crevice | 26763 | Use | 3233,10331,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 934 | Wilderness Dungeons | Crevice | 26763 | Use | 3243,10352,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 935 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 3295,3525,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 936 | Wilderness Surface | loc.ditch_soil_soft1 | 23272 | <missing> | 3296,3525,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 937 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 3298,3525,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 938 | Wilderness Surface | Stepping stone | 53237 | Cross | 3268,3627,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 939 | Wilderness Surface | loc.agility_shortcut_icon | 17030 | <missing> | 3268,3628,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 940 | Wilderness Surface | Cave entrance | 14436 | <missing> | 3300,3656,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 941 | Wilderness Surface | Cave entrance | 14436 | <missing> | 3306,3654,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 942 | Wilderness Surface | Cave | 47175 | Enter | 3282,3774,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 943 | Wilderness Surface | Stairs | 40390 | <missing> | 3293,3746,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 944 | Wilderness Surface | Cave | 47175 | Enter | 3284,3807,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 945 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3317,3794,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 946 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3317,3795,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 947 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3317,3796,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 948 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3318,3793,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 949 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3318,3794,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 950 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3318,3796,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 951 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3318,3797,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 952 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3319,3793,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 953 | Wilderness Surface | loc.wild_venanatis_web_floor07 | 47076 | <missing> | 3319,3793,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 954 | Wilderness Surface | Web sack | 47069 | <missing> | 3319,3794,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 955 | Wilderness Surface | Web sack | 47069 | <missing> | 3319,3797,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 956 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3319,3798,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 957 | Wilderness Surface | Web sack | 47068 | <missing> | 3320,3797,0 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 958 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3320,3799,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 959 | Wilderness Surface | Cave | 47175 | Enter | 3320,3830,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 960 | Wilderness Surface | Web sack | 47069 | <missing> | 3321,3793,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 961 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3321,3793,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 962 | Wilderness Surface | Cave Entrance | 47077 | Enter | 3321,3794,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 963 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3321,3798,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 964 | Wilderness Surface | loc.wild_venanatis_web_floor07 | 47076 | <missing> | 3321,3798,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 965 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3322,3793,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 966 | Wilderness Surface | loc.wild_venanatis_web_floor01 | 47074 | <missing> | 3322,3794,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 967 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3324,3796,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 968 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3324,3797,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 969 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3324,3798,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 970 | Wilderness Surface | loc.wild_venanatis_web_floor02 | 47075 | <missing> | 3325,3799,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 971 | Wilderness Surface | Cave Entrance | 47140 | Enter | 3291,3849,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 972 | Wilderness Surface | Door | 14749 | Open | 3276,3929,0 | WallStraight/3 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 973 | Wilderness Surface | Door | 14749 | Open | 3276,3933,0 | WallStraight/1 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 974 | Wilderness Surface | Door | 14749 | Open | 3279,3939,0 | WallDiagonal/0 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 975 | Wilderness Surface | Staircase | 14735 | Climb-up | 3280,3936,0 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 976 | Wilderness Surface | Door | 14749 | Open | 3283,3931,0 | WallStraight/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 977 | Wilderness Surface | Door | 14749 | Open | 3283,3935,0 | WallDiagonal/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 978 | Wilderness Surface | Door | 1546 | Open | 3285,3938,0 | WallStraight/3 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 979 | Wilderness Surface | Door | 1546 | Open | 3286,3935,0 | WallStraight/1 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 980 | Wilderness Surface | Door | 1535 | Open | 3288,3930,0 | WallStraight/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 981 | Wilderness Surface | Door | 14749 | Open | 3292,3931,0 | WallStraight/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 982 | Wilderness Surface | Door | 14749 | Open | 3294,3932,0 | WallStraight/3 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 983 | Wilderness Surface | Obelisk | 14831 | <missing> | 3305,3914,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 984 | Wilderness Surface | Obelisk | 14831 | <missing> | 3305,3918,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 985 | Wilderness Surface | Obelisk | 14831 | <missing> | 3309,3914,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 986 | Wilderness Surface | Obelisk | 14831 | <missing> | 3309,3918,0 | CentrepieceStraight/0 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | BLOCKED_BY_CACHE | BLOCKED |
| 987 | Wilderness Surface | loc.ditch_soil_soft1 | 23272 | <missing> | 3328,3525,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 988 | Wilderness Surface | loc.ditch_soil_soft1 | 23272 | <missing> | 3328,3527,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 989 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 3328,3528,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 990 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 3329,3527,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 991 | Wilderness Surface | loc.ditch_soil_soft1 | 23272 | <missing> | 3330,3527,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 992 | Wilderness Surface | loc.ditch_soil_soft1 | 23272 | <missing> | 3331,3525,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 993 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 3331,3527,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 994 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 3332,3525,0 | GroundDecor/3 |  | WRONG_OPTION | FAIL | PENDING |
| 995 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 3332,3526,0 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 996 | Wilderness Surface | loc.ditch_soil_soft2 | 23273 | <missing> | 3333,3525,0 | GroundDecor/2 |  | WRONG_OPTION | FAIL | PENDING |
| 997 | Wilderness Surface | Ladder | 22274 | Climb | 3363,3625,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 998 | Wilderness Surface | Gate | 1728 | Open | 3336,3896,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 999 | Wilderness Surface | Gate | 1727 | Open | 3337,3896,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1000 | Wilderness Surface | loc.fountain_of_rune_obelisk | 26782 | <missing> | 3373,3892,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1001 | Wilderness Dungeons | Opening | 40389 | Exit | 3384,10050,0 | CentrepieceStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1002 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3334,10119,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1003 | Wilderness Dungeons | Crevice | 53259 | Squeeze-Through | 3334,10119,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1004 | Wilderness Dungeons | Crevice | 53259 | Squeeze-Through | 3334,10121,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1005 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3342,10149,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1006 | Wilderness Dungeons | Crevice | 53259 | Squeeze-Through | 3342,10149,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1007 | Wilderness Dungeons | Crevice | 53259 | Squeeze-Through | 3342,10153,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1008 | Wilderness Dungeons | Cave | 47148 | Exit | 3336,10287,0 | CentrepieceStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1009 | Wilderness Dungeons | loc.wild_boss_escape_cave_exit_noop | 47151 | <missing> | 3339,10252,0 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1010 | Wilderness Dungeons | Cave | 47147 | Exit | 3358,10244,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1011 | Wilderness Dungeons | Exit | 47149 | Climb-up | 3361,10273,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1012 | Wilderness Dungeons | loc.wild_boss_escape_cave_exit_noop | 47151 | <missing> | 3361,10298,0 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1013 | Wilderness Dungeons | loc.wild_boss_escape_cave_exit_noop | 47151 | <missing> | 3378,10254,0 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1014 | Wilderness Dungeons | Cave | 47148 | Exit | 3382,10287,0 | CentrepieceStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1015 | Wilderness Dungeons | Cave | 47122 | Exit | 3358,10315,0 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1016 | Wilderness Dungeons | Crevice | 53259 | Squeeze-Through | 3434,10091,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1017 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3434,10093,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1018 | Wilderness Dungeons | Crevice | 53259 | Squeeze-Through | 3434,10093,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1019 | Wilderness Dungeons | Opening | 40391 | Exit | 3405,10146,0 | CentrepieceStraight/0 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1020 | Wilderness Dungeons | Crevice | 53259 | Squeeze-Through | 3435,10112,0 | WallStraight/3 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1021 | Wilderness Dungeons | loc.agility_shortcut_icon | 17030 | <missing> | 3435,10115,0 | GroundDecor/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1022 | Wilderness Dungeons | Crevice | 53259 | Squeeze-Through | 3435,10115,0 | WallStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1023 | Wilderness Boss Caves | Web sack | 47070 | <missing> | 1615,11551,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1024 | Wilderness Boss Caves | Web sack | 47068 | <missing> | 1616,11543,1 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1025 | Wilderness Boss Caves | Web sack | 47070 | <missing> | 1621,11562,1 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1026 | Wilderness Boss Caves | Web sack | 47069 | <missing> | 1629,11529,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1027 | Wilderness Boss Caves | Web sack | 47069 | <missing> | 1629,11565,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1028 | Wilderness Boss Caves | loc.wild_venanatis_exit_noop | 46999 | <missing> | 1630,11526,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1029 | Wilderness Boss Caves | Web sack | 47068 | <missing> | 1630,11566,1 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1030 | Wilderness Boss Caves | Web sack | 47068 | <missing> | 1632,11565,1 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1031 | Wilderness Boss Caves | Web sack | 47068 | <missing> | 1634,11531,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1032 | Wilderness Boss Caves | Web sack | 47068 | <missing> | 1638,11564,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1033 | Wilderness Boss Caves | Web sack | 47068 | <missing> | 1643,11558,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1034 | Wilderness Boss Caves | Web sack | 47069 | <missing> | 1644,11559,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1035 | Wilderness Boss Caves | Web sack | 47070 | <missing> | 1648,11543,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1036 | Wilderness Boss Caves | Cave | 46925 | Exit | 1886,11534,1 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1037 | Wilderness Surface | Ledge | 53289 | Jump | 2947,3822,1 | GroundDecor/1 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 1038 | Wilderness Surface | Gate | 1570 | <missing> | 2997,3931,1 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1039 | Wilderness Surface | Ropeswing | 23128 | <missing> | 3004,3954,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1040 | Wilderness Surface | Ropeswing | 23128 | <missing> | 3006,3954,1 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1041 | Wilderness Surface | Ladder | 14747 | Climb | 3022,3625,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1042 | Wilderness Surface | Ladder | 14747 | Climb-down | 3022,3625,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1043 | Wilderness Surface | Ladder | 14747 | Climb-up | 3022,3625,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1044 | Wilderness Surface | Ladder | 14747 | Climb | 3022,3638,1 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1045 | Wilderness Surface | Ladder | 14747 | Climb-down | 3022,3638,1 | CentrepieceStraight/2 |  | BLOCKED_BY_MISSING_CACHE_CONTENT | FAIL | PENDING |
| 1046 | Wilderness Surface | Ladder | 14747 | Climb-up | 3022,3638,1 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1047 | Wilderness Surface | Door | 14749 | Open | 3023,3627,1 | WallStraight/1 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1048 | Wilderness Surface | Door | 14749 | Open | 3023,3636,1 | WallStraight/3 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1049 | Wilderness Surface | Door | 14749 | Open | 3024,3626,1 | WallStraight/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1050 | Wilderness Surface | Door | 14749 | Open | 3024,3637,1 | WallStraight/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1051 | Wilderness Surface | Door | 14749 | Open | 3034,3626,1 | WallStraight/0 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1052 | Wilderness Surface | Door | 14749 | Open | 3034,3637,1 | WallStraight/0 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1053 | Wilderness Surface | Door | 14749 | Open | 3035,3627,1 | WallStraight/1 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1054 | Wilderness Surface | Door | 14749 | Open | 3035,3636,1 | WallStraight/3 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1055 | Wilderness Surface | Ladder | 14747 | Climb | 3036,3625,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1056 | Wilderness Surface | Ladder | 14747 | Climb-down | 3036,3625,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1057 | Wilderness Surface | Ladder | 14747 | Climb-up | 3036,3625,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1058 | Wilderness Surface | Ladder | 14747 | Climb | 3036,3638,1 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1059 | Wilderness Surface | Ladder | 14747 | Climb-down | 3036,3638,1 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1060 | Wilderness Surface | Ladder | 14747 | Climb-up | 3036,3638,1 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1061 | Wilderness Surface | Gate | 1570 | <missing> | 3008,3849,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1062 | Wilderness Surface | Ship's ladder | 245 | Climb-up | 3017,3959,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1063 | Wilderness Surface | Ship's ladder | 273 | Climb-down | 3018,3957,1 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1064 | Wilderness Surface | Ship's ladder | 245 | Climb-up | 3019,3959,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1065 | Wilderness Dungeons | Gate | 1570 | <missing> | 3023,10311,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1066 | Wilderness Dungeons | Gate | 1570 | <missing> | 3041,10307,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1067 | Wilderness Dungeons | Gate | 1570 | <missing> | 3045,10341,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1068 | Wilderness Surface | loc.wildy_hub_entrance_arch_r | 39655 | <missing> | 3123,3628,1 | WallStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1069 | Wilderness Surface | loc.wildy_hub_entrance_arch_l | 39654 | <missing> | 3123,3629,1 | WallStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1070 | Wilderness Surface | Ladder | 14746 | Climb-down | 3125,3633,1 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1071 | Wilderness Surface | Ladder | 14746 | Climb-down | 3125,3639,1 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1072 | Wilderness Surface | Ladder | 14745 | Climb-up | 3126,3618,1 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1073 | Wilderness Surface | Ladder | 14745 | Climb-up | 3126,3625,1 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1074 | Wilderness Surface | Ladder | 14745 | Climb-up | 3130,3633,1 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1075 | Wilderness Surface | Ladder | 14745 | Climb-up | 3130,3639,1 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1076 | Wilderness Surface | Stairs | 39650 | Climb | 3132,3619,1 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1077 | Wilderness Surface | Stairs | 39650 | Climb | 3132,3623,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1078 | Wilderness Surface | loc.wildy_hub_entrance_arch_l | 39654 | <missing> | 3134,3617,1 | WallStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1079 | Wilderness Surface | loc.wildy_hub_entrance_arch_r | 39655 | <missing> | 3134,3639,1 | WallStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1080 | Wilderness Surface | loc.wildy_hub_entrance_arch_r | 39655 | <missing> | 3135,3617,1 | WallStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1081 | Wilderness Surface | loc.wildy_hub_entrance_arch_l | 39654 | <missing> | 3135,3639,1 | WallStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1082 | Wilderness Dungeons | Gate | 1570 | <missing> | 3105,9944,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1083 | Wilderness Surface | Stairs | 39650 | Climb | 3137,3619,1 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1084 | Wilderness Surface | Stairs | 39650 | Climb | 3137,3623,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1085 | Wilderness Surface | Stairs | 39650 | Climb | 3138,3636,1 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1086 | Wilderness Surface | Stairs | 39650 | Climb | 3138,3637,1 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1087 | Wilderness Surface | Stairs | 39643 | Climb | 3141,3635,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1088 | Wilderness Surface | Casual | 39639 | Pass-Through | 3141,3638,1 | WallStraight/1 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 1089 | Wilderness Surface | Stairs | 39643 | Climb | 3142,3635,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1090 | Wilderness Surface | Casual | 39639 | Pass-Through | 3142,3638,1 | WallStraight/1 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 1091 | Wilderness Surface | Ladder | 14745 | Climb-up | 3143,3618,1 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1092 | Wilderness Surface | Ladder | 14745 | Climb-up | 3143,3625,1 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1093 | Wilderness Surface | Stairs | 39650 | Climb | 3144,3636,1 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1094 | Wilderness Surface | Stairs | 39650 | Climb | 3144,3637,1 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1095 | Wilderness Surface | loc.wildy_hub_entrance_arch_l | 39654 | <missing> | 3154,3634,1 | WallStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1096 | Wilderness Surface | loc.wildy_hub_entrance_arch_r | 39655 | <missing> | 3154,3635,1 | WallStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1097 | Wilderness Surface | Climbing rope | 18968 | <missing> | 3181,3608,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1098 | Wilderness Surface | Ladder | 14747 | Climb | 3184,3610,1 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1099 | Wilderness Surface | Ladder | 14747 | Climb-down | 3184,3610,1 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1100 | Wilderness Surface | Ladder | 14747 | Climb-up | 3184,3610,1 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1101 | Wilderness Surface | Gate | 1570 | <missing> | 3201,3856,1 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1102 | Wilderness Surface | Staircase | 14736 | Climb | 3280,3936,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1103 | Wilderness Surface | Staircase | 14736 | Climb-down | 3280,3936,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1104 | Wilderness Surface | Staircase | 14736 | Climb-up | 3280,3936,1 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1105 | Wilderness Surface | Door | 14749 | Open | 3290,3931,1 | WallStraight/2 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1106 | Wilderness Surface | Door | 14749 | Open | 3293,3934,1 | WallStraight/0 | content-group:0 | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1107 | Wilderness Dungeons | Cave | 46925 | Exit | 3294,10190,1 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1108 | Wilderness Surface | Ladder | 22275 | Climb | 3363,3625,1 | CentrepieceStraight/3 |  | NO_HANDLER | FAIL | PENDING |
| 1109 | Wilderness Surface | loc.brain_boat_upperfloor_stairs_side_02_mirror_inactive | 53220 | <missing> | 3367,3624,1 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1110 | Wilderness Surface | loc.brain_boat_upperfloor_stairs_mid_02_inactive | 53221 | <missing> | 3367,3625,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1111 | Wilderness Surface | loc.brain_boat_upperfloor_stairs_side_02_inactive | 53219 | <missing> | 3367,3626,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1112 | Wilderness Surface | loc.brain_boat_upperfloor_stairs_side_01_mirror | 22246 | <missing> | 3368,3624,1 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1113 | Wilderness Surface | loc.brain_boat_upperfloor_stairs_mid_01 | 22249 | <missing> | 3368,3625,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1114 | Wilderness Surface | loc.brain_boat_upperfloor_stairs_side_01 | 22245 | <missing> | 3368,3626,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1115 | Wilderness Surface | Gate | 1570 | <missing> | 3336,3896,1 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1116 | Wilderness Dungeons | Web sack | 47070 | <missing> | 3407,10207,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1117 | Wilderness Dungeons | Web sack | 47068 | <missing> | 3408,10199,1 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1118 | Wilderness Dungeons | Web sack | 47070 | <missing> | 3413,10218,1 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1119 | Wilderness Dungeons | Web sack | 47069 | <missing> | 3421,10185,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1120 | Wilderness Dungeons | Web sack | 47069 | <missing> | 3421,10221,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1121 | Wilderness Dungeons | loc.wild_venanatis_exit_noop | 46999 | <missing> | 3422,10182,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1122 | Wilderness Dungeons | Web sack | 47068 | <missing> | 3422,10222,1 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1123 | Wilderness Dungeons | Web sack | 47068 | <missing> | 3424,10221,1 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1124 | Wilderness Dungeons | Web sack | 47068 | <missing> | 3426,10187,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1125 | Wilderness Dungeons | Web sack | 47068 | <missing> | 3430,10220,1 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1126 | Wilderness Dungeons | Web sack | 47068 | <missing> | 3435,10214,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1127 | Wilderness Dungeons | Web sack | 47069 | <missing> | 3436,10215,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1128 | Wilderness Dungeons | Web sack | 47070 | <missing> | 3440,10199,1 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1129 | Wilderness Boss Caves | Web sack | 47071 | <missing> | 1618,11554,2 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1130 | Wilderness Boss Caves | Web sack | 47071 | <missing> | 1619,11556,2 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1131 | Wilderness Boss Caves | Cave | 47000 | Exit | 1630,11527,2 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1132 | Wilderness Boss Caves | Web sack | 47071 | <missing> | 1646,11539,2 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1133 | Wilderness Boss Caves | Web sack | 47071 | <missing> | 1646,11545,2 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1134 | Wilderness Surface | Cave exit | 679 | Exit | 2963,4254,2 | WallStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1135 | Wilderness Surface | Passage | 677 | Go-through | 2971,4254,2 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1136 | Wilderness Surface | Cave exit | 679 | Exit | 2963,4382,2 | WallStraight/2 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1137 | Wilderness Surface | Passage | 677 | Go-through | 2971,4382,2 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1138 | Wilderness Surface | Ladder | 14747 | Climb | 3022,3625,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1139 | Wilderness Surface | Ladder | 14747 | Climb-down | 3022,3625,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1140 | Wilderness Surface | Ladder | 14747 | Climb-up | 3022,3625,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1141 | Wilderness Surface | Ladder | 14747 | Climb | 3022,3638,2 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1142 | Wilderness Surface | Ladder | 14747 | Climb-down | 3022,3638,2 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1143 | Wilderness Surface | Ladder | 14747 | Climb-up | 3022,3638,2 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1144 | Wilderness Surface | Ladder | 14747 | Climb | 3036,3625,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1145 | Wilderness Surface | Ladder | 14747 | Climb-down | 3036,3625,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1146 | Wilderness Surface | Ladder | 14747 | Climb-up | 3036,3625,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1147 | Wilderness Surface | Ladder | 14747 | Climb | 3036,3638,2 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1148 | Wilderness Surface | Ladder | 14747 | Climb-down | 3036,3638,2 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1149 | Wilderness Surface | Ladder | 14747 | Climb-up | 3036,3638,2 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1150 | Wilderness Surface | Ship's ladder | 246 | Climb-down | 3017,3959,2 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1151 | Wilderness Surface | Ship's ladder | 246 | Climb-down | 3019,3959,2 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1152 | Wilderness Surface | Ladder | 14746 | Climb-down | 3126,3618,2 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1153 | Wilderness Surface | Ladder | 14746 | Climb-down | 3126,3625,2 | CentrepieceStraight/1 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1154 | Wilderness Surface | Ladder | 14746 | Climb-down | 3130,3633,2 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1155 | Wilderness Surface | Ladder | 14746 | Climb-down | 3130,3639,2 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1156 | Wilderness Surface | loc.wildy_hub_door_bars | 39685 | <missing> | 3133,3622,2 | WallStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1157 | Wilderness Surface | Stairs | 39650 | Climb | 3141,3635,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1158 | Wilderness Surface | High Stakes | 39641 | Pass-Through | 3141,3639,2 | WallStraight/3 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 1159 | Wilderness Surface | Stairs | 39650 | Climb | 3142,3635,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1160 | Wilderness Surface | High Stakes | 39641 | Pass-Through | 3142,3639,2 | WallStraight/3 | exact-coordinate | INTENTIONAL_OPENRUNE_CUSTOM_ROUTE | INTENTIONAL_EXCEPTION | NOT_APPLICABLE |
| 1161 | Wilderness Surface | Ladder | 14746 | Climb-down | 3143,3618,2 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1162 | Wilderness Surface | Ladder | 14746 | Climb-down | 3143,3625,2 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1163 | Wilderness Surface | Climbing rope | 18968 | <missing> | 3181,3608,2 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1164 | Wilderness Surface | Ladder | 14747 | Climb | 3184,3610,2 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1165 | Wilderness Surface | Ladder | 14747 | Climb-down | 3184,3610,2 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1166 | Wilderness Surface | Ladder | 14747 | Climb-up | 3184,3610,2 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1167 | Wilderness Surface | Staircase | 14736 | Climb | 3280,3936,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1168 | Wilderness Surface | Staircase | 14736 | Climb-down | 3280,3936,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1169 | Wilderness Surface | Staircase | 14736 | Climb-up | 3280,3936,2 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1170 | Wilderness Surface | Stairs | 22254 | Climb-down | 3368,3624,2 | CentrepieceStraight/3 |  | NO_HANDLER | FAIL | PENDING |
| 1171 | Wilderness Surface | Stairs | 22252 | Climb-down | 3368,3625,2 | CentrepieceStraight/1 |  | NO_HANDLER | FAIL | PENDING |
| 1172 | Wilderness Surface | Stairs | 22253 | Climb-down | 3368,3626,2 | CentrepieceStraight/1 |  | NO_HANDLER | FAIL | PENDING |
| 1173 | Wilderness Surface | loc.brain_boat_edge_stairs | 22200 | <missing> | 3369,3624,2 | WallSquareCorner/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1174 | Wilderness Surface | loc.brain_boat_stairs_floor | 22251 | <missing> | 3369,3625,2 | GroundDecor/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1175 | Wilderness Surface | loc.brain_boat_edge_stairs_mirror | 22201 | <missing> | 3369,3626,2 | WallSquareCorner/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1176 | Wilderness Surface | Locked door | 22283 | <missing> | 3373,3624,2 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1177 | Wilderness Surface | Locked door | 22281 | <missing> | 3373,3625,2 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1178 | Wilderness Surface | Locked door | 22282 | <missing> | 3373,3626,2 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1179 | Wilderness Dungeons | Web sack | 47071 | <missing> | 3410,10210,2 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1180 | Wilderness Dungeons | Web sack | 47071 | <missing> | 3411,10212,2 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1181 | Wilderness Dungeons | Cave | 47000 | Exit | 3422,10183,2 | CentrepieceStraight/1 | exact-coordinate | PASS | DISCOVERY_HANDLER_ONLY | PENDING |
| 1182 | Wilderness Dungeons | Web sack | 47071 | <missing> | 3438,10195,2 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1183 | Wilderness Dungeons | Web sack | 47071 | <missing> | 3438,10201,2 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1184 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1612,11536,3 | CentrepieceDiagonal/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1185 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1613,11534,3 | CentrepieceDiagonal/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1186 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1614,11551,3 | CentrepieceDiagonal/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1187 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1615,11548,3 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1188 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1616,11546,3 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1189 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1617,11553,3 | CentrepieceDiagonal/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1190 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1619,11539,3 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1191 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1619,11549,3 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1192 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1621,11558,3 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1193 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1622,11561,3 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1194 | Wilderness Boss Caves | Web sack | 47071 | <missing> | 1623,11540,3 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1195 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1624,11543,3 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1196 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1626,11563,3 | CentrepieceDiagonal/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1197 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1627,11557,3 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1198 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1628,11531,3 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1199 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1628,11562,3 | CentrepieceDiagonal/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1200 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1630,11565,3 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1201 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1633,11530,3 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1202 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1634,11561,3 | CentrepieceDiagonal/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1203 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1634,11564,3 | CentrepieceDiagonal/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1204 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1637,11562,3 | CentrepieceDiagonal/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1205 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1638,11531,3 | CentrepieceDiagonal/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1206 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1638,11550,3 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1207 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1639,11535,3 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1208 | Wilderness Boss Caves | Web sack | 47071 | <missing> | 1639,11557,3 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1209 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1639,11562,3 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1210 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1640,11534,3 | CentrepieceDiagonal/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1211 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1641,11547,3 | CentrepieceStraight/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1212 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1642,11531,3 | CentrepieceDiagonal/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1213 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1642,11550,3 | CentrepieceDiagonal/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1214 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1643,11554,3 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1215 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1645,11539,3 | CentrepieceStraight/1 |  | WRONG_OPTION | FAIL | PENDING |
| 1216 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling02 | 47073 | <missing> | 1645,11548,3 | CentrepieceStraight/2 |  | WRONG_OPTION | FAIL | PENDING |
| 1217 | Wilderness Boss Caves | loc.wild_venanatis_web_ceiling01 | 47072 | <missing> | 1646,11550,3 | CentrepieceDiagonal/0 |  | WRONG_OPTION | FAIL | PENDING |
| 1218 | Wilderness Surface | Ladder | 14746 | Climb-down | 3022,3625,3 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1219 | Wilderness Surface | Ladder | 14746 | Climb-down | 3022,3638,3 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1220 | Wilderness Surface | Ladder | 14746 | Climb-down | 3036,3625,3 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1221 | Wilderness Surface | Ladder | 14746 | Climb-down | 3036,3638,3 | CentrepieceStraight/2 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1222 | Wilderness Surface | Broken ladder | 741 | <missing> | 3134,3623,3 | CentrepieceStraight/3 |  | WRONG_OPTION | FAIL | PENDING |
| 1223 | Wilderness Surface | Ladder | 14746 | Climb-down | 3184,3610,3 | CentrepieceStraight/3 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |
| 1224 | Wilderness Surface | Staircase | 14737 | Climb-down | 3281,3936,3 | CentrepieceStraight/0 | exact-coordinate | PASS | ROUTE_TABLE_VALIDATED | PENDING |

The JSON and CSV beside this file contain every required route field, including requirements, restrictions, failure behavior, reverse-route data, source evidence, and notes.
