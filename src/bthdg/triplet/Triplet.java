package bthdg.triplet;

import bthdg.*;
import bthdg.exch.*;

import java.util.*;

/**
 * - try place brackets for non-profitable pairs / sorted by trades num
 * - try to place inner mkt order before mkt
 * - try adjust account if no profitables found
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
 */
public class Triplet {
    public static final boolean SIMULATE = false;
    public static final boolean USE_ACCOUNT_TEST_STR = SIMULATE;
    public static final boolean SIMULATE_ORDER_EXECUTION = SIMULATE;
    public static final boolean ONLY_ONE_ACTIVE_TRIANGLE = false;
    public static final boolean START_ONE_TRIANGLE_PER_ITERATION = true;

    public static final double LVL = 100.602408; // commission level - note - complex percents here
    public static final double LVL2 = 100.69; // min target level
    public static final double USE_ACCOUNT_FUNDS = 0.94;
    public static final int WAIT_MKT_ORDER_STEPS = 0;
    public static final int ITERATIONS_SLEEP_TIME = 3000; // sleep between iterations
    public static final int LOAD_TRADES_NUM = 30; // num of last trades to load api
    private static final int MAX_PLACE_ORDER_REPEAT = 3;
    public static final boolean TRY_WITH_MKT_OFFSET = true;
    public static final double MINUS_MKT_OFFSET = 0.10; // mkt - 10%

    public static double s_totalRatio = 1;
    public static int s_counter = 0;
    public static double s_level = LVL2;

    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};

    public static final Triangle T1 = new Triangle(Pair.LTC_USD, true,  Pair.LTC_BTC, false, Pair.BTC_USD, false); // usd -> ltc -> btc -> usd
    public static final Triangle T2 = new Triangle(Pair.LTC_EUR, true,  Pair.LTC_BTC, false, Pair.BTC_EUR, false); // eur -> ltc -> btc -> eur
    public static final Triangle T3 = new Triangle(Pair.LTC_USD, true,  Pair.LTC_EUR, false, Pair.EUR_USD, false); // usd -> ltc -> eur -> usd
    public static final Triangle T4 = new Triangle(Pair.EUR_USD, false, Pair.BTC_USD, true,  Pair.BTC_EUR, false); // eur -> usd -> btc -> eur
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

        try {
            TradesAggregator tAgg = new TradesAggregator();
            tAgg.load();

            Console.ConsoleReader consoleReader = new Console.ConsoleReader() {
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
            };
            consoleReader.start();

            try {
                AccountData account = init(tAgg);

                long start = System.currentTimeMillis();
                int counter = 1;
                TriangleData td = new TriangleData(account);
                while (true) {
                    log("============================================== iteration " + (counter++) +
                            "; active " + td.m_triTrades.size() +
                            "; time=" + Utils.millisToDHMSStr(System.currentTimeMillis() - start) +
                            "; date=" + new Date() );
                    IterationData iData = new IterationData(tAgg);
                    td.checkState(iData); // <<---------------------------------------------------------------------<<

                    int sleep = ITERATIONS_SLEEP_TIME;
                    if(td.m_triTrades.isEmpty()) {
                        if( s_stopRequested ) {
                            System.out.println("stopRequested; nothing to process - exit");
                            break;
                        }
                        td.m_account = syncAccountIfNeeded(td.m_account);
                        sleep += sleep/2;

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
        if (s_notEnoughFundsCounter > 3) {
            System.out.println("!!!!!----- account is out of sync: " + account);
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

        IterationData iData = new IterationData(tAgg);
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
        log("placeOrder() END: " + orderData.toString(Exchange.BTCE));
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
}
