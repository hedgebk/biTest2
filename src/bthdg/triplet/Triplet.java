package bthdg.triplet;

import bthdg.*;
import bthdg.Currency;
import bthdg.exch.*;

import java.util.*;

/**
 * - when checking for 'peg to live' check mkt availability too
 * - give penalty on triangle+rotation base
 * - have leven on triangle+rotation base
 * + discard my peg orders from loaded deep data
 * - try place brackets for non-profitable pairs / sorted by trades num
 * - do not start new peg orders if we have running non-peg - they need to be executed quickly
 * - try adjust account
 *   - better to place pegs on void iterations
 *   - try to fix dis-balance on account sync - at least place mkt from bigger to lower
 *   - leave some amount on low funds nodes (while processing MKT prices)
 * - drop very long running MKT orders - should be filled in 1-3 steps
 * - when MKT order is non-profitable, but zeroProfit is close to mkt - try zero profit and without delay run mkt
 * - monitor and run quickly 3*mkt-10 triplet
 * - calculate pegs over average top data using current and previous tick - do not eat 1 tick peaks
 * - parallel triangles processing - need logging upgrade (print prefix everywhere)
 *   - need int funds lock - other triangles cant use needed funds
 *   - then can run > 2 triangles at once/ no delays
 *
 * - stats:                                                              ratio       btc
 *  LTC->USD;USD->EUR;EUR->LTC	29		LTC->BTC;BTC->EUR;EUR->LTC	56 = 0.386206896 0.076121379
 *  USD->BTC;BTC->LTC;LTC->USD	24		LTC->BTC;BTC->USD;USD->LTC
 *  BTC->USD;USD->LTC;LTC->BTC	17		LTC->EUR;EUR->BTC;BTC->LTC
 *  LTC->EUR;EUR->BTC;BTC->LTC	16		LTC->EUR;EUR->USD;USD->LTC
 *  EUR->LTC;LTC->BTC;BTC->EUR	9		LTC->USD;USD->BTC;BTC->LTC
 *  EUR->LTC;LTC->USD;USD->EUR	9		LTC->USD;USD->EUR;EUR->LTC
 *  USD->LTC;LTC->BTC;BTC->USD	9		USD->BTC;BTC->EUR;EUR->USD	35 = 0.241379310 0.047575862
 *  EUR->BTC;BTC->USD;USD->EUR	7		USD->BTC;BTC->LTC;LTC->USD
 *  BTC->USD;USD->EUR;EUR->BTC	6		USD->LTC;LTC->BTC;BTC->USD
 *  LTC->USD;USD->BTC;BTC->LTC	5		USD->LTC;LTC->EUR;EUR->USD
 *  BTC->LTC;LTC->USD;USD->BTC	3		BTC->EUR;EUR->LTC;LTC->BTC	28 = 0.193103448 0.038060689
 *  LTC->BTC;BTC->USD;USD->LTC	3		BTC->LTC;LTC->EUR;EUR->BTC
 *  LTC->EUR;EUR->USD;USD->LTC	2		BTC->LTC;LTC->USD;USD->BTC
 *  BTC->EUR;EUR->LTC;LTC->BTC	1		BTC->USD;USD->EUR;EUR->BTC
 *  BTC->LTC;LTC->EUR;EUR->BTC	1		BTC->USD;USD->LTC;LTC->BTC
 *  EUR->BTC;BTC->LTC;LTC->EUR	1		EUR->BTC;BTC->LTC;LTC->EUR	26 = 0.179310344 0.035342068
 *  LTC->BTC;BTC->EUR;EUR->LTC	1		EUR->BTC;BTC->USD;USD->EUR
 *  USD->BTC;BTC->EUR;EUR->USD	1		EUR->LTC;LTC->BTC;BTC->EUR
 *  USD->LTC;LTC->EUR;EUR->USD	1		EUR->LTC;LTC->USD;USD->EUR
 *                                                                  145
 * account=AccountData{name='btce' funds={USD=22.57809, EUR=14.66755, LTC=2.59098, BTC=0.03806}; allocated={} , fee=0.002}; valuateEur=69.88963140051925 EUR; valuateUsd=93.09032635429794 USD
 * account: AccountData{name='btce' funds={LTC=2.27774, USD=21.86919, EUR=18.24521, BTC=0.03888}; allocated={} , fee=0.002}
 * account: AccountData{name='btce' funds={LTC=3.17240, USD=21.97626, EUR=12.62895, BTC=0.02680}; allocated={} , fee=0.002}; evaluateEur: 71.43604 evaluateUsd: 95.37413
 * account: AccountData{name='btce' funds={BTC=0.02688, USD=75.17193, LTC=0.22164, EUR=3.33161}; allocated={} , fee=0.002}   evaluateEur: 71.19479 evaluateUsd: 96.14770
 * account: AccountData{name='btce' funds={EUR=12.74370, USD=23.28824, LTC=2.67312, BTC=0.03719}; allocated={} , fee=0.002}  evaluateEur: 71.52619 evaluateUsd: 95.84035
 * account: AccountData{name='btce' funds={USD=23.28823, EUR=12.74370, BTC=0.04637, LTC=2.33259}; allocated={} , fee=0.002}  evaluateEur: 71.70368 evaluateUsd: 96.82125
 * account: AccountData{name='btce' funds={USD=22.92819, LTC=2.68048, EUR=11.90301, BTC=0.04017}; allocated={} , fee=0.002}  evaluateEur: 71.62901 evaluateUsd: 96.13304
 * account: AccountData{name='btce' funds={LTC=2.38048, EUR=11.90301, USD=27.06820, BTC=0.04013}; allocated={} , fee=0.002}  evaluateEur: 71.55296 evaluateUsd: 95.88487
 * account: AccountData{name='btce' funds={EUR=11.92631, USD=21.78576, LTC=2.66067, BTC=0.03734}; allocated={} , fee=0.002}  evaluateEur: 66.27443 evaluateUsd: 90.74563
 * account: AccountData{name='btce' funds={EUR=11.95935, LTC=2.66453, USD=21.83333, BTC=0.03848}; allocated={} , fee=0.002}  evaluateEur: 66.34168 evaluateUsd: 90.27166
 * account: AccountData{name='btce' funds={EUR=11.95267, LTC=2.68616, USD=21.51320, BTC=0.03848}; allocated={} , fee=0.002}  evaluateEur: 66.49649 evaluateUsd: 90.34984
 * account: AccountData{name='btce' funds={BTC=0.03733, USD=23.71019, EUR=10.59954, LTC=2.65862}; allocated={} , fee=0.002}  evaluateEur: 66.78655 evaluateUsd: 90.03562
 * account: AccountData{name='btce' funds={LTC=2.66919, USD=21.62340, BTC=0.03728, EUR=11.81885}; allocated={} , fee=0.002}  evaluateEur: 66.39638 evaluateUsd: 89.52065
 * account: AccountData{name='btce' funds={EUR=11.81885, LTC=2.63499, BTC=0.03738, USD=22.06731}; allocated={} , fee=0.002}  evaluateEur: 66.23796 evaluateUsd: 89.25250
 * account: AccountData{name='btce' funds={LTC=2.63499, USD=22.06731, EUR=11.81885, BTC=0.03738}; allocated={} , fee=0.002}  evaluateEur: 66.22481 evaluateUsd: 89.18593
 * account: AccountData{name='btce' funds={EUR=11.81885, USD=21.42877, LTC=2.66835, BTC=0.03733}; allocated={} , fee=0.002}  evaluateEur: 65.88974 evaluateUsd: 88.65482
 * account: AccountData{name='btce' funds={LTC=2.66876, USD=21.42871, EUR=11.79688, BTC=0.03732}; allocated={} , fee=0.002}  evaluateEur: 65.99338 evaluateUsd: 89.04589
 * account: AccountData{name='btce' funds={LTC=2.57592, USD=22.24297, EUR=12.73021, BTC=0.03638}; allocated={} , fee=0.002}  evaluateEur: 68.18542 evaluateUsd: 92.28889
 * account: AccountData{name='btce' funds={USD=20.14310, LTC=2.79220, BTC=0.03659, EUR=12.73021}; allocated={} , fee=0.002}  evaluateEur: 68.31025 evaluateUsd: 92.21095
 * account: AccountData{name='btce' funds={USD=20.17423, EUR=12.73021, BTC=0.04168, LTC=2.61609}; allocated={} , fee=0.002}  evaluateEur: 68.43546 evaluateUsd: 92.68294
 * account: AccountData{name='btce' funds={LTC=2.70028, EUR=12.73011, USD=22.27144, BTC=0.03558}; allocated={} , fee=0.002}  evaluateEur: 68.07087 evaluateUsd: 92.22809
 * account: AccountData{name='btce' funds={BTC=0.03558, USD=22.28602, LTC=2.70059, EUR=12.73011}; allocated={} , fee=0.002}  evaluateEur: 68.08198 evaluateUsd: 92.25919
 * account: AccountData{name='btce' funds={EUR=12.73011, USD=22.24245, BTC=0.03560, LTC=2.70622}; allocated={} , fee=0.002}  evaluateEur: 68.34063 evaluateUsd: 92.26624
 * account: AccountData{name='btce' funds={LTC=2.68859, BTC=0.03665, USD=22.34098, EUR=12.26514}; allocated={} , fee=0.002}  evaluateEur: 68.28362 evaluateUsd: 92.50624
 * account: AccountData{name='btce' funds={BTC=0.03652, LTC=2.70237, USD=22.48932, EUR=12.37476}; allocated={} , fee=0.002}  evaluateEur: 69.05520 evaluateUsd: 93.07373
 * account: AccountData{name='btce' funds={EUR=12.29769, USD=22.51507, LTC=2.67356, BTC=0.03731}; allocated={} , fee=0.002}  evaluateEur: 67.15116 evaluateUsd: 89.94537
 * account: AccountData{name='btce' funds={EUR=11.89775, LTC=2.82626, BTC=0.03752, USD=21.18753}; allocated={} , fee=0.002}  evaluateEur: 65.98443 evaluateUsd: 87.93049
 * account: AccountData{name='btce' funds={EUR=11.59681, BTC=0.03806, USD=20.77948, LTC=2.94535}; allocated={} , fee=0.002}  evaluateEur: 64.38586 evaluateUsd: 85.82370
 * account: AccountData{name='btce' funds={USD=18.64292, EUR=11.59681, LTC=2.75415, BTC=0.04734}; allocated={} , fee=0.002}  evaluateEur: 64.73080 evaluateUsd: 85.90225
 * account: AccountData{name='btce' funds={BTC=0.03234, USD=25.32938, EUR=11.59586, LTC=2.73575}; allocated={} , fee=0.002}  evaluateEur: 63.59411 evaluateUsd: 83.95240
 * account: AccountData{name='btce' funds={EUR=11.59586, BTC=0.03813, USD=19.74571, LTC=3.02394}; allocated={} , fee=0.002}  evaluateEur: 63.39227 evaluateUsd: 83.51752
 * account: AccountData{name='btce' funds={BTC=0.03819, USD=20.84158, EUR=11.10746, LTC=3.06003}; allocated={} , fee=0.002}  evaluateEur: 63.05567 evaluateUsd: 83.91506
 * account: AccountData{name='btce' funds={BTC=0.04906, USD=16.25132, EUR=11.09549, LTC=3.08846}; allocated={} , fee=0.002}  evaluateEur: 62.99141 evaluateUsd: 84.26006
 * account: AccountData{name='btce' funds={EUR=11.09549, LTC=3.05676, USD=20.71768, BTC=0.03811}; allocated={} , fee=0.002}  evaluateEur: 64.67839 evaluateUsd: 86.06910
 * account: AccountData{name='btce' funds={EUR=12.74699, LTC=2.84737, BTC=0.03899, USD=20.36479}; allocated={} , fee=0.002}  evaluateEur: 63.37806 evaluateUsd: 84.36548
 * account: AccountData{name='btce' funds={USD=18.14144, BTC=0.03738, EUR=12.99810, LTC=2.98036}; allocated={} , fee=0.002}  evaluateEur: 63.29617 evaluateUsd: 86.15721
 * account: AccountData{name='btce' funds={USD=21.83478, BTC=0.03732, EUR=11.46111, LTC=2.88838}; allocated={} , fee=0.002}  evaluateEur: 63.27659 evaluateUsd: 85.14999
 * account: AccountData{name='btce' funds={LTC=2.52380, EUR=11.45314, BTC=0.02219, USD=32.79849}; allocated={} , fee=0.002}  evaluateEur: 63.55309 evaluateUsd: 86.16763
 * account: AccountData{name='btce' funds={BTC=0.03280, LTC=3.17588, USD=20.72897, EUR=11.45314}; allocated={} , fee=0.002}  evaluateEur: 63.36702 evaluateUsd: 85.91002
 * account: AccountData{name='btce' funds={BTC=0.03728, USD=21.29369, LTC=2.91970, EUR=11.45314}; allocated={} , fee=0.002}  evaluateEur: 64.61165 evaluateUsd: 88.22558
 * account: AccountData{name='btce' funds={EUR=11.45314, LTC=3.25309, USD=21.29369, BTC=0.02877}; allocated={} , fee=0.002}  evaluateEur: 64.30440 evaluateUsd: 86.68882
 * account: AccountData{name='btce' funds={BTC=0.03777, LTC=2.93381, USD=20.90069, EUR=11.45314}; allocated={} , fee=0.002}  evaluateEur: 64.27578 evaluateUsd: 86.55528
 * account: AccountData{name='btce' funds={BTC=0.06956, USD=7.65018, LTC=2.86976, EUR=11.46445}; allocated={} , fee=0.002}   evaluateEur: 63.86114 evaluateUsd: 85.19992
 * account: AccountData{name='btce' funds={BTC=0.03756, USD=20.65875, LTC=2.96956, EUR=11.46445}; allocated={} , fee=0.002}  evaluateEur: 63.91006 evaluateUsd: 86.11504
 * account: AccountData{name='btce' funds={LTC=2.97341, USD=20.77583, BTC=0.03767, EUR=11.51558}; allocated={} , fee=0.002}  evaluateEur: 64.28155 evaluateUsd: 85.96322
 * account: AccountData{name='btce' funds={BTC=0.04019, LTC=2.97341, USD=20.75292, EUR=11.24278}; allocated={} , fee=0.002}  evaluateEur: 62.66591 evaluateUsd: 82.81530
 * account: AccountData{name='btce' funds={EUR=10.97564, LTC=3.09816, BTC=0.03879, USD=19.75331}; allocated={} , fee=0.002}  evaluateEur: 60.75445 evaluateUsd: 81.99110
 * account: AccountData{name='btce' funds={BTC=0.03826, LTC=3.10910, EUR=10.77592, USD=19.38016}; allocated={} , fee=0.002}  evaluateEur: 58.46791 evaluateUsd: 79.14658
 * account: AccountData{name='btce' funds={EUR=10.85752, USD=19.55631, BTC=0.04219, LTC=3.59753}; allocated={} , fee=0.002}  evaluateEur: 60.17619 evaluateUsd: 80.96285
 * account: AccountData{name='btce' funds={LTC=3.61514, EUR=10.31013, USD=18.71340, BTC=0.04729}; allocated={} , fee=0.002}  evaluateEur: 60.10606 evaluateUsd: 81.39723
 * account: AccountData{name='btce' funds={LTC=12.15273, EUR=33.79188, USD=61.99164, BTC=0.14316}; allocated={} , fee=0.002} evaluateEur: 187.22232 evaluateUsd: 255.17610
 * account: AccountData{name='btce' funds={BTC=0.14293, LTC=11.36785, USD=71.99854, EUR=39.60616}; allocated={} , fee=0.002} evaluateEur: 223.86317 evaluateUsd: 307.83647
 * account: AccountData{name='btce' funds={LTC=11.57169, USD=72.10936, BTC=0.14402, EUR=39.60617}; allocated={} , fee=0.002} evaluateEur: 225.69962 evaluateUsd: 308.03827
 * account: AccountData{name='btce' funds={BTC=0.14229, USD=72.09173, LTC=11.45220, EUR=40.80059}; allocated={} , fee=0.002} evaluateEur: 226.73370 evaluateUsd: 309.29524
 * account: AccountData{name='btce' funds={EUR=40.80059, USD=72.05188, BTC=0.14229, LTC=11.50326}; allocated={} , fee=0.002} evaluateEur: 225.27491 evaluateUsd: 304.56961
 * account: AccountData{name='btce' funds={EUR=40.80059, USD=72.05188, BTC=0.14229, LTC=11.50326}; allocated={} , fee=0.002} evaluateEur: 227.08010 evaluateUsd: 308.48368
 * account: AccountData{name='btce' funds={EUR=40.75439, USD=72.05188, BTC=0.14229, LTC=11.50836}; allocated={} , fee=0.002} evaluateEur: 226.81183 evaluateUsd: 307.42652
 * account: AccountData{name='btce' funds={BTC=0.14355, EUR=43.12330, USD=70.45480, LTC=11.31590}; allocated={} , fee=0.002} evaluateEur: 227.41484 evaluateUsd: 310.18520
 * account: AccountData{name='btce' funds={LTC=11.13956, USD=74.60065, BTC=0.14169, EUR=41.74470}; allocated={} , fee=0.002} evaluateEur: 227.43251 evaluateUsd: 307.35967
 * account: AccountData{name='btce' funds={BTC=0.13627, EUR=41.38254, LTC=11.03225, USD=77.10954}; allocated={} , fee=0.002} evaluateEur: 227.79516 evaluateUsd: 306.51334
 * account: AccountData{name='btce' funds={BTC=0.14003, EUR=40.18754, USD=77.23637, LTC=11.07166}; allocated={} , fee=0.002} evaluateEur: 226.78828 evaluateUsd: 304.66485
 * account: AccountData{name='btce' funds={USD=75.85451, LTC=11.21973, EUR=40.18754, BTC=0.13984}; allocated={} , fee=0.002} evaluateEur: 226.04917 evaluateUsd: 306.88534
 * account: AccountData{name='btce' funds={USD=70.83242, EUR=40.18754, LTC=10.94649, BTC=0.14858}; allocated={} , fee=0.002} evaluateEur: 224.46079 evaluateUsd: 304.53489
 * account: AccountData{name='btce' funds={USD=70.83242, BTC=0.14858, EUR=40.18754, LTC=10.71017}; allocated={} , fee=0.002} evaluateEur: 223.64223 evaluateUsd: 304.65268
 * account: AccountData{name='btce' funds={BTC=0.14858, LTC=10.71017, USD=70.83242, EUR=40.18754}; allocated={} , fee=0.002} evaluateEur: 223.78147 evaluateUsd: 303.55591
 * account: AccountData{name='btce' funds={BTC=0.14828, EUR=40.18754, USD=71.34032, LTC=10.44436}; allocated={} , fee=0.002} evaluateEur: 227.40522 evaluateUsd: 310.73877
 * account: AccountData{name='btce' funds={LTC=10.42665, USD=75.70075, BTC=0.13800, EUR=40.22284}; allocated={} , fee=0.002} evaluateEur: 227.94654 evaluateUsd: 306.94649
 * account: AccountData{name='btce' funds={LTC=10.34465, USD=75.76040, BTC=0.13800, EUR=40.22284}; allocated={} , fee=0.002} evaluateEur: 228.78097 evaluateUsd: 308.77125
 * account: AccountData{name='btce' funds={BTC=0.14436, USD=76.14425, EUR=39.93247, LTC=10.19092}; allocated={} , fee=0.002} evaluateEur: 224.94338 evaluateUsd: 304.10089
 * account: AccountData{name='btce' funds={USD=73.51231, EUR=40.15367, LTC=11.01263, BTC=0.14008}; allocated={} , fee=0.002} evaluateEur: 223.43266 evaluateUsd: 303.64127
 * account: AccountData{name='btce' funds={LTC=11.15112, BTC=0.14034, EUR=38.92147, USD=73.51231}; allocated={} , fee=0.002} evaluateEur: 223.38719 evaluateUsd: 303.72223
 * account: AccountData{name='btce' funds={LTC=11.62885, USD=70.31816, BTC=0.14385, EUR=38.13283}; allocated={} , fee=0.002} evaluateEur: 216.63639 evaluateUsd: 292.77171
 * account: AccountData{name='btce' funds={BTC=0.14285, LTC=0.69773, USD=180.58583, EUR=38.13283}; allocated={} , fee=0.002} evaluateEur: 219.72764 evaluateUsd: 297.63049
 * account: AccountData{name='btce' funds={LTC=11.43449, USD=71.12999, BTC=0.14285, EUR=38.14984}; allocated={} , fee=0.002} evaluateEur: 218.17511 evaluateUsd: 294.21111
 * account: AccountData{name='btce' funds={EUR=39.62539, USD=72.67060, BTC=0.14252, LTC=10.87314}; allocated={} , fee=0.002} evaluateEur: 216.39233 evaluateUsd: 291.69602
 * account: AccountData{name='btce' funds={LTC=0.65239, USD=72.51008, BTC=0.14157, EUR=115.57691}; allocated={} , fee=0.002} evaluateEur: 216.61190 evaluateUsd: 293.02641
 * account: AccountData{name='btce' funds={BTC=0.13358, LTC=10.68740, EUR=37.41949, USD=76.54978}; allocated={} , fee=0.002} evaluateEur: 222.60919 evaluateUsd: 304.45624
 * account: AccountData{name='btce' funds={LTC=10.62200, USD=76.54990, BTC=0.13284, EUR=37.41949}; allocated={} , fee=0.002} evaluateEur: 224.84136 evaluateUsd: 306.57886
 * account: AccountData{name='btce' funds={LTC=10.71570, USD=75.48420, BTC=0.12982, EUR=37.41948}; allocated={} , fee=0.002} evaluateEur: 226.79691 evaluateUsd: 310.10622
 * account: AccountData{name='btce' funds={BTC=0.13330, USD=74.47495, LTC=10.51983, EUR=40.12152}; allocated={} , fee=0.002} evaluateEur: 227.35024 evaluateUsd: 308.61603
 * account: AccountData{name='btce' funds={EUR=40.12152, USD=77.25663, BTC=0.11311, LTC=11.09390}; allocated={} , fee=0.002} evaluateEur: 224.89306 evaluateUsd: 305.41613
 * account: AccountData{name='btce' funds={BTC=0.13163, LTC=10.54027, USD=74.98176, EUR=40.92109}; allocated={} , fee=0.002} evaluateEur: 228.59803 evaluateUsd: 311.67522
 * account: AccountData{name='btce' funds={USD=76.12873, EUR=40.92109, BTC=0.13228, LTC=10.50140}; allocated={} , fee=0.002} evaluateEur: 230.01065 evaluateUsd: 313.65255
 * account: AccountData{name='btce' funds={LTC=10.50720, EUR=40.92109, BTC=0.13198, USD=76.12873}; allocated={} , fee=0.002} evaluateEur: 229.41584 evaluateUsd: 313.36692
 * account: AccountData{name='btce' funds={USD=76.12873, EUR=38.97734, LTC=10.73916, BTC=0.13198}; allocated={} , fee=0.002} evaluateEur: 228.76814 evaluateUsd: 311.96886
 * account: AccountData{name='btce' funds={BTC=0.13194, USD=70.66981, EUR=41.20981, LTC=10.93878}; allocated={} , fee=0.002} evaluateEur: 230.57973 evaluateUsd: 314.73061
 * account: AccountData{name='btce' funds={BTC=0.11195, LTC=10.04169, USD=80.31374, EUR=48.10936}; allocated={} , fee=0.002} evaluateEur: 244.71770 evaluateUsd: 334.77162
 * account: AccountData{name='btce' funds={USD=81.06053, EUR=41.86075, BTC=0.11410, LTC=10.63675}; allocated={} , fee=0.002} evaluateEur: 242.27257 evaluateUsd: 327.84699
 * account: AccountData{name='btce' funds={BTC=0.14920, USD=91.78109, EUR=42.79427, LTC=8.07585}; allocated={} , fee=0.002}  evaluateEur: 238.70857 evaluateUsd: 323.50813
 * account: AccountData{name='btce' funds={USD=78.46747, EUR=42.91475, BTC=0.12655, LTC=9.92483}; allocated={} , fee=0.002}  evaluateEur: 238.57563 evaluateUsd: 324.58841
 * account: AccountData{name='btce' funds={USD=77.20723, EUR=42.91475, LTC=9.92483, BTC=0.12887}; allocated={} , fee=0.002}  evaluateEur: 238.96654 evaluateUsd: 326.25728
 * account: AccountData{name='btce' funds={EUR=42.91475, LTC=9.89044, USD=77.20723, BTC=0.12920}; allocated={} , fee=0.002}  evaluateEur: 240.68700 evaluateUsd: 328.22765
 * account: AccountData{name='btce' funds={BTC=0.12106, LTC=8.88360, USD=90.56309, EUR=47.88739}; allocated={} , fee=0.002}  evaluateEur: 247.83867 evaluateUsd: 338.70192
 * account: AccountData{name='btce' funds={LTC=7.84122, EUR=53.41834, BTC=0.13217, USD=87.74147}; allocated={} , fee=0.002}  evaluateEur: 251.12482 evaluateUsd: 340.78786
 * account: AccountData{name='btce' funds={USD=79.27247, EUR=52.40784, LTC=7.94102, BTC=0.14639}; allocated={} , fee=0.002}  evaluateEur: 249.96798 evaluateUsd: 340.71100
 * account: AccountData{name='btce' funds={USD=72.23466, EUR=60.44327, LTC=8.90779, BTC=0.12000}; allocated={} , fee=0.002}  evaluateEur: 247.33253 evaluateUsd: 333.40623
 * account: AccountData{name='btce' funds={LTC=7.28947, EUR=47.47389, BTC=0.17953, USD=82.85590}; allocated={} , fee=0.002}  evaluateEur: 242.93579 evaluateUsd: 328.51065
 * account: AccountData{name='btce' funds={LTC=9.66328, EUR=47.47389, BTC=0.13194, USD=76.40188}; allocated={} , fee=0.002}  evaluateEur: 244.13588 evaluateUsd: 332.22036
 * account: AccountData{name='btce' funds={BTC=0.13456, USD=88.76101, EUR=46.49972, LTC=8.48901}; allocated={} , fee=0.002}  evaluateEur: 245.55995 evaluateUsd: 335.75524
 * account: AccountData{name='btce' funds={EUR=46.49972, BTC=0.13961, LTC=8.48901, USD=86.14181}; allocated={} , fee=0.002}  evaluateEur: 244.59412 evaluateUsd: 334.61073
 * account: AccountData{name='btce' funds={LTC=8.44392, EUR=46.49972, USD=86.14181, BTC=0.13961}; allocated={} , fee=0.002}  evaluateEur: 242.97606 evaluateUsd: 333.09885
 * account: AccountData{name='btce' funds={BTC=0.13961, LTC=8.44392, EUR=46.49972, USD=86.14181}; allocated={} , fee=0.002}  evaluateEur: 243.71975 evaluateUsd: 333.98025
 * account: AccountData{name='btce' funds={EUR=46.59331, BTC=0.13961, LTC=8.45374, USD=86.14181}; allocated={} , fee=0.002}  evaluateEur: 245.74419 evaluateUsd: 338.24268
 * account: AccountData{name='btce' funds={EUR=46.69311, USD=91.41318, BTC=0.12404, LTC=8.79601}; allocated={} , fee=0.002}  evaluateEur: 249.54805 evaluateUsd: 341.79073
 * account: AccountData{name='btce' funds={EUR=52.48431, USD=74.83421, BTC=0.13239, LTC=8.98348}; allocated={} , fee=0.002}  evaluateEur: 244.69759 evaluateUsd: 331.45086
 * account: AccountData{name='btce' funds={EUR=46.33423, BTC=0.12135, LTC=9.65797, USD=92.94051}; allocated={} , fee=0.002}  evaluateEur: 249.48304 evaluateUsd: 334.22945
 * account: AccountData{name='btce' funds={LTC=9.65803, USD=90.30235, EUR=42.11297, BTC=0.13701}; allocated={} , fee=0.002}  evaluateEur: 247.90468 evaluateUsd: 332.09378
 * account: AccountData{name='btce' funds={BTC=0.14300, LTC=9.65803, USD=85.70124, EUR=43.65036}; allocated={} , fee=0.002}  evaluateEur: 247.70408 evaluateUsd: 334.60124
 * account: AccountData{name='btce' funds={EUR=43.65036, BTC=0.22582, LTC=7.71917, USD=69.36836}; allocated={} , fee=0.002}  evaluateEur: 248.06091 evaluateUsd: 333.56557
 * account: AccountData{name='btce' funds={LTC=10.07713, USD=79.66049, EUR=43.66121, BTC=0.14411}; allocated={} , fee=0.002} evaluateEur: 248.54438 evaluateUsd: 335.07191
 * account: AccountData{name='btce' funds={BTC=0.14411, LTC=10.07713, EUR=43.66121, USD=79.66049}; allocated={} , fee=0.002} evaluateEur: 246.75298 evaluateUsd: 332.70164
 * account: AccountData{name='btce' funds={BTC=0.16841, LTC=6.95672, EUR=63.66752, USD=83.14819}; allocated={} , fee=0.002}  evaluateEur: 247.97794 evaluateUsd: 331.21396
 * account: AccountData{name='btce' funds={BTC=0.12490, LTC=8.39044, USD=45.99591, EUR=93.78454}; allocated={} , fee=0.002}  evaluateEur: 248.28417 evaluateUsd: 332.13187
 * account: AccountData{name='btce' funds={LTC=8.39044, USD=45.99591, BTC=0.12490, EUR=95.57980}; allocated={} , fee=0.002}  evaluateEur: 250.60737 evaluateUsd: 338.66394
 * account: AccountData{name='btce' funds={EUR=96.10802, USD=43.71127, BTC=0.13617, LTC=8.18962}; allocated={} , fee=0.002}  evaluateEur: 255.69189 evaluateUsd: 344.52483
 * account: AccountData{name='btce' funds={BTC=0.13854, LTC=8.45209, USD=40.57572, EUR=96.15131}; allocated={} , fee=0.002}  evaluateEur: 257.07721 evaluateUsd: 347.41633
 * account: AccountData{name='btce' funds={LTC=8.31249, EUR=93.52338, USD=43.52722, BTC=0.13694}; allocated={} , fee=0.002}  evaluateEur: 251.92002 evaluateUsd: 338.36977
 * account: AccountData{name='btce' funds={USD=39.35151, EUR=93.52370, BTC=0.14700, LTC=8.27216}; allocated={} , fee=0.002}  evaluateEur: 252.35064 evaluateUsd: 338.62189
 * account: AccountData{name='btce' funds={LTC=8.31619, USD=37.64957, EUR=94.45606, BTC=0.14751}; allocated={} , fee=0.002}  evaluateEur: 252.25424 evaluateUsd: 339.31412
 * account: AccountData{name='btce' funds={USD=44.96829, EUR=94.45606, BTC=0.14751, LTC=7.73572}; allocated={} , fee=0.002}  evaluateEur: 251.19434 evaluateUsd: 339.44242
 * account: AccountData{name='btce' funds={LTC=7.69006, USD=42.96804, BTC=0.15151, EUR=94.29459}; allocated={} , fee=0.002}  evaluateEur: 249.91172 evaluateUsd: 337.63074
 * account: AccountData{name='btce' funds={LTC=7.69006, USD=42.96804, BTC=0.15151, EUR=94.29459}; allocated={} , fee=0.002}  evaluateEur: 249.75692 evaluateUsd: 337.22251
 * account: AccountData{name='btce' funds={BTC=0.15191, USD=41.82918, LTC=10.65953, EUR=67.94968}; allocated={} , fee=0.002} evaluateEur: 250.28976 evaluateUsd: 337.64621
 * account: AccountData{name='btce' funds={EUR=67.94968, BTC=0.15191, USD=43.31561, LTC=10.53881}; allocated={} , fee=0.002} evaluateEur: 249.10617 evaluateUsd: 336.79393
 * account: AccountData{name='btce' funds={LTC=10.54012, USD=43.31762, BTC=0.15191, EUR=67.94968}; allocated={} , fee=0.002} evaluateEur: 249.89793 evaluateUsd: 337.96784
 * account: AccountData{name='btce' funds={LTC=10.51818, USD=44.68995, BTC=0.15253, EUR=67.99612}; allocated={} , fee=0.002} evaluateEur: 249.92708 evaluateUsd: 338.99370
 * account: AccountData{name='btce' funds={BTC=0.15548, USD=42.48025, LTC=10.30278, EUR=71.49326}; allocated={} , fee=0.002} evaluateEur: 250.07258 evaluateUsd: 339.03996
 * account: AccountData{name='btce' funds={LTC=10.30278, USD=42.58243, BTC=0.15549, EUR=71.49327}; allocated={} , fee=0.002} evaluateEur: 248.86998 evaluateUsd: 336.36894
 * account: AccountData{name='btce' funds={EUR=71.49327, USD=32.17802, LTC=11.50698, BTC=0.14922}; allocated={} , fee=0.002} evaluateEur: 248.25027 evaluateUsd: 337.50819
 * account: AccountData{name='btce' funds={USD=32.17803, LTC=11.50885, BTC=0.14953, EUR=71.50956}; allocated={} , fee=0.002} evaluateEur: 248.82276 evaluateUsd: 338.44045
 * account: AccountData{name='btce' funds={LTC=10.75037, EUR=71.50956, USD=46.06287, BTC=0.14806}; allocated={} , fee=0.002} evaluateEur: 252.63862 evaluateUsd: 344.00420
 * account: AccountData{name='btce' funds={EUR=74.66729, BTC=0.14806, USD=46.17033, LTC=10.74547}; allocated={} , fee=0.002} evaluateEur: 254.97208 evaluateUsd: 345.96656
 * account: AccountData{name='btce' funds={EUR=74.66729, BTC=0.14806, USD=46.25057, LTC=10.76787}; allocated={} , fee=0.002} evaluateEur: 256.53535 evaluateUsd: 349.19338
 * account: AccountData{name='btce' funds={LTC=9.77267, USD=41.45660, EUR=89.42442, BTC=0.14845}; allocated={} , fee=0.002}  evaluateEur: 259.69329 evaluateUsd: 355.27258
 */
public class Triplet {
    public static final int NUMBER_OF_ACTIVE_TRIANGLES = 2;
    public static final boolean START_ONE_TRIANGLE_PER_ITERATION = true;

    public static final double LVL = 100.602408; // commission level - note - complex percents here
    public static final double LVL2 = 100.68; // min target level
    public static final int WAIT_MKT_ORDER_STEPS = 0;
    public static final boolean TRY_WITH_MKT_OFFSET = false;
    public static final double MKT_OFFSET_PRICE_MINUS = 0.15; // mkt - 10%
    public static final double MKT_OFFSET_LEVEL_DELTA = 0.08;
    public static final int ITERATIONS_SLEEP_TIME = 1900; // sleep between iterations
    public static final boolean PREFER_LIQUID_PAIRS = false; // LTC_BTC, BTC_USD, LTC_USD
    public static final boolean LOWER_LEVEL_FOR_LIQUIDITY_PAIRS = false; // LTC_BTC, BTC_USD, LTC_USD: level -= 0.02
    public static final double LIQUIDITY_PAIRS_LEVEL_DELTA = 0.02;
    public static final boolean PREFER_EUR_CRYPT_PAIRS = false; // BTC_EUR, LTC_EUR

    public static final boolean USE_BRACKETS = false;
    public static final double BRACKET_LEVEL_EXTRA = 0.25;
    public static final int BRACKET_DISTANCE_MAX = 2;

    public static final boolean USE_DEEP = true;
    public static final boolean ADJUST_AMOUNT_TO_MKT_AVAILABLE = true;
    public static final double PLACE_MORE_THAN_MKT_AVAILABLE = 1.4;
    public static final int LOAD_TRADES_NUM = 30; // num of last trades to load api
    public static final int LOAD_ORDERS_NUM = 3; // num of deep orders to load api
    public static final double USE_ACCOUNT_FUNDS = 0.95;
    private static final int MAX_PLACE_ORDER_REPEAT = 3;
    public static final double TOO_BIG_LOSS_LEVEL = 0.992; // stop current trade if mkt conditions will give big loss
    public static final boolean SIMULATE = false;
    public static final boolean USE_ACCOUNT_TEST_STR = SIMULATE;
    public static final boolean SIMULATE_ORDER_EXECUTION = SIMULATE;

    public static double s_totalRatio = 1;
    public static int s_counter = 0;
    public static double s_level = LVL2;

    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};

    public static final Triangle T1 = new Triangle(Currency.USD, Currency.LTC, Currency.BTC); // usd -> ltc -> btc -> usd
    public static final Triangle T2 = new Triangle(Currency.EUR, Currency.LTC, Currency.BTC); // eur -> ltc -> btc -> eur
    public static final Triangle T3 = new Triangle(Currency.USD, Currency.LTC, Currency.EUR); // usd -> ltc -> eur -> usd
    public static final Triangle T4 = new Triangle(Currency.EUR, Currency.USD, Currency.BTC); // eur -> usd -> btc -> eur
    public static final Triangle[] TRIANGLES = new Triangle[]{T1, T2, T3, T4};

    static AccountData s_startAccount;
    public static double s_startEur;
    public static double s_startUsd;
    static boolean s_stopRequested;
    static int s_notEnoughFundsCounter;

    public static void main(String[] args) {
        System.out.println("Started");

        Fetcher.LOG_LOADING = false;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
        Btce.LOG_PARSE = false;
        Fetcher.USE_ACCOUNT_TEST_STR = USE_ACCOUNT_TEST_STR;
        Fetcher.SIMULATE_ORDER_EXECUTION = SIMULATE_ORDER_EXECUTION;
        Fetcher.SIMULATE_ACCEPT_ORDER_PRICE = false;
        Fetcher.SIMULATE_ACCEPT_ORDER_PRICE_RATE = 0.99;
        Btce.BTCE_TRADES_IN_REQUEST = LOAD_TRADES_NUM;
        Btce.BTCE_DEEP_ORDERS_IN_REQUEST = LOAD_ORDERS_NUM;

        if (TRY_WITH_MKT_OFFSET && WAIT_MKT_ORDER_STEPS < 1) {
            System.out.println("WARNING: TRY_WITH_MKT_OFFSET used but WAIT_MKT_ORDER_STEPS=" + WAIT_MKT_ORDER_STEPS);
        }

        try {
            TradesAggregator tAgg = new TradesAggregator();
            tAgg.load();

            Console.ConsoleReader consoleReader = new IntConsoleReader();
            consoleReader.start();

            try {
                TopsData tops = null;
                AccountData account = init(tAgg);

                long start = System.currentTimeMillis();
                int counter = 1;
                TriangleData td = new TriangleData(account);
                while (true) {
                    log("============================================== iteration " + (counter++) +
                            "; active " + td.m_triTrades.size() +
                            "; time=" + Utils.millisToDHMSStr(System.currentTimeMillis() - start) +
                            "; date=" + new Date() );
                    IterationData iData = new IterationData(tAgg, tops);
                    td.checkState(iData); // <<---------------------------------------------------------------------<<

                    int sleep = ITERATIONS_SLEEP_TIME;
                    if (td.m_triTrades.isEmpty()) {
                        if (s_stopRequested) {
                            System.out.println("stopRequested; nothing to process - exit");
                            break;
                        }
                        td.m_account = syncAccountIfNeeded(td.m_account);
                        sleep += sleep / 2;

                        if (s_level > LVL2) {
                            double level = s_level;
                            s_level = (s_level - LVL) * 0.99 + LVL;
                            s_level = Math.max(s_level, LVL2);
                            log(" LEVEL decreased (-1%) from " + Utils.X_YYYYYYYY.format(level) + " to " + Utils.X_YYYYYYYY.format(Triplet.s_level));
                        }
                    } else if (td.m_triTrades.size() > 1) {
                        sleep /= 2;
                    }
                    Thread.sleep(sleep);
                    if(iData.m_tops != null) {
                        tops = iData.m_tops;
                    }
                }
            } finally {
                consoleReader.interrupt();
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    private static AccountData syncAccountIfNeeded(AccountData account) throws Exception {
        boolean gotFundDiff = account.m_gotFundDiff;
        if ((s_notEnoughFundsCounter > 0) || gotFundDiff) {
            System.out.println("!!!!!----- account is out of sync (notEnoughFundsCounter=" + s_notEnoughFundsCounter + ", gotFundDiff=" + gotFundDiff + "): " + account);
            AccountData newAccount = getAccount();
            if (newAccount != null) {
                System.out.println(" synced with new Account: " + newAccount);
                s_notEnoughFundsCounter = 0;
                return newAccount;
            } else {
                System.out.println(" error getting account to sync");
            }
        }
        return account;
    }

    private static AccountData init(TradesAggregator tAgg) throws Exception {
        Properties keys = BaseExch.loadKeys();
        Btce.init(keys);

        AccountData account = getAccount();
        System.out.println("account: " + account);

        s_startAccount = account.copy();

        IterationData iData = new IterationData(tAgg, null);
        TopsData tops = iData.getTops();
        s_startEur = s_startAccount.evaluateEur(tops);
        s_startUsd = s_startAccount.evaluateUsd(tops);
        System.out.println(" evaluateEur: " + format5(s_startEur) + " evaluateUsd: " + format5(s_startUsd));
        return account;
    }

    // todo: to move this to OrderData as NON-static method
    public static OrderData.OrderPlaceStatus placeOrder(AccountData account, OrderData orderData, OrderState state, IterationData iData) throws Exception {
        log("placeOrder() " + iData.millisFromStart() + "ms: " + orderData.toString(Exchange.BTCE));

        OrderData.OrderPlaceStatus ret;
        if (account.allocateOrder(orderData)) {
            if (Fetcher.SIMULATE_ORDER_EXECUTION) {
                orderData.m_status = OrderStatus.SUBMITTED;
                orderData.m_state = state;
                ret = OrderData.OrderPlaceStatus.OK;
            } else {
                ret = placeOrderToExchange(account, orderData, state, iData);
            }
            if (ret != OrderData.OrderPlaceStatus.OK) {
                account.releaseOrder(orderData);
            }
        } else {
            log("ERROR: account allocateOrder unsuccessful: " + orderData + ", account: " + account);
            ret = OrderData.OrderPlaceStatus.ERROR;
        }
        //log("placeOrder() END: " + orderData.toString(Exchange.BTCE));
        return ret;
    }

    private static OrderData.OrderPlaceStatus placeOrderToExchange(AccountData account, OrderData orderData, OrderState state, IterationData iData) throws Exception {
        int repeatCounter = MAX_PLACE_ORDER_REPEAT;
        while( true ) {
            OrderData.OrderPlaceStatus ret;
            PlaceOrderData poData = Fetcher.placeOrder(orderData, Exchange.BTCE);
            log(" PlaceOrderData: " + poData);
            String error = poData.m_error;
            if (error == null) {
                orderData.m_status = OrderStatus.SUBMITTED;
                double amount = poData.m_received;
                if (amount != 0) {
                    String amountStr = orderData.roundAmountStr(Exchange.BTCE, amount);
                    String orderAmountStr = orderData.roundAmountStr(Exchange.BTCE);
                    log("  some part of order (" + amountStr + " from " + orderAmountStr + ") is executed at the time of placing ");
                    double price = orderData.m_price;
                    orderData.addExecution(price, amount, Exchange.BTCE);
                    account.releaseTrade(orderData.m_pair, orderData.m_side, price, amount);
                }
                poData.m_accountData.compareFunds(account);
                orderData.m_state = (orderData.m_status == OrderStatus.FILLED)
                        ? OrderState.NONE // can be fully filled once the order placed
                        : state;
                ret = OrderData.OrderPlaceStatus.OK;
            } else {
                orderData.m_status = OrderStatus.ERROR;
                orderData.m_state = OrderState.NONE;
                if (error.contains("invalid sign")) {
                    if (repeatCounter-- > 0) {
                        log(" repeat place order, count=" + repeatCounter);
                        continue;
                    }
                    ret = OrderData.OrderPlaceStatus.CAN_REPEAT;
                } else if (error.contains("SocketTimeoutException")) {
                    if (repeatCounter-- > 0) {
                        log(" repeat place order, count=" + repeatCounter);
                        continue;
                    }
                    ret = OrderData.OrderPlaceStatus.CAN_REPEAT;
                } else if (error.contains("It is not enough")) { // It is not enough BTC in the account for sale
                    s_notEnoughFundsCounter++;
                    ret = OrderData.OrderPlaceStatus.ERROR;
                    log("  NotEnoughFunds detected - increased account sync counter to " + s_notEnoughFundsCounter );
                } else if (error.contains("must be greater than")) { // Value BTC must be greater than 0.01 BTC.
                    ret = OrderData.OrderPlaceStatus.ERROR; // too small order - can not continue
                    orderData.m_status = OrderStatus.REJECTED;
                    log("  too small order - can not continue: " + error );
                } else if (error.contains("invalid nonce parameter")) {
                    throw new RuntimeException("from server: "+ error);
                } else {
                    ret = OrderData.OrderPlaceStatus.ERROR;
                }
            }
            iData.resetLiveOrders(); // clean cached data
            return ret;
        }
    }

    public static String formatAndPad(double value) {
        return Utils.padLeft(format(value - 100), 6);
    }

    private static String format(double number) {
        return Utils.PLUS_YYY.format(number);
    }

    public static String format5(double number) {
        return Utils.X_YYYYY.format(number);
    }

    public static AccountData getAccount() throws Exception {
        AccountData account = Fetcher.fetchAccount(Exchange.BTCE);
        return account;
    }

    private static void log(String s) {
        Log.log(s);
    }

    private static class IntConsoleReader extends Console.ConsoleReader {
        @Override protected void beforeLine() {}

        @Override protected boolean processLine(String line) throws Exception {
            if (line.equals("stop")) {
                System.out.println("~~~~~~~~~~~ stopRequested ~~~~~~~~~~~");
                s_stopRequested = true;
                return true;
            } else {
                System.out.println("~~~~~~~~~~~ command ignored: " + line);
            }
            return false;
        }
    }
}
