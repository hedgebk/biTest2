package bthdg.triplet;

import bthdg.*;
import bthdg.exch.*;

import java.util.*;

/**
 * - try place brackets for non-profitable pairs / sorted by trades num
 * - try to place inner mkt order before mkt
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
 */
public class Triplet {
    public static final boolean SIMULATE = false;
    public static final boolean USE_ACCOUNT_TEST_STR = SIMULATE;
    public static final boolean SIMULATE_ORDER_EXECUTION = SIMULATE;
    public static final boolean ONLY_ONE_ACTIVE_TRIANGLE = false;

    public static final double LVL = 100.602408; // commission level - complex percnts
    public static final double LVL2 = 100.63; // min target level
    public static final double USE_ACCOUNT_FUNDS = 0.93;
    public static final int WAIT_MKT_ORDER_STEPS = 6;
    public static final int ITERATIONS_SLEEP_TIME = 3000; // sleep between iterations
    public static final int LOAD_TRADES_NUM = 30; // num of last trades to load api

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
                td.checkState(iData);
                int sleep = ITERATIONS_SLEEP_TIME;
                if(td.m_triTrades.isEmpty()) {
                    sleep += sleep/2;
                }
                Thread.sleep(sleep);
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
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
        log("placeOrder(): " + orderData);

        OrderData.OrderPlaceStatus ret;
        if (account.allocateOrder(orderData)) {
            if (Fetcher.SIMULATE_ORDER_EXECUTION) {
                orderData.m_status = OrderStatus.SUBMITTED;
                orderData.m_state = state;
                ret = OrderData.OrderPlaceStatus.OK;
            } else {
                PlaceOrderData poData = Fetcher.placeOrder(orderData, Exchange.BTCE);
                log(" PlaceOrderData: " + poData);
                String error = poData.m_error;
                if (error == null) {
                    orderData.m_status = OrderStatus.SUBMITTED;
                    double amount = poData.m_received;
                    if (amount != 0) {
                        log("  some part of order (" + amount + " from " + orderData.m_amount + ") is executed at the time of placing ");
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
                    // todo; track not enough funds here separately
                    //orderData.m_status = OrderStatus.REJECTED;
                    orderData.m_status = OrderStatus.ERROR;
                    orderData.m_state = OrderState.NONE;
                    if (error.contains("invalid sign")) {
                        ret = OrderData.OrderPlaceStatus.CAN_REPEAT;
                    } else if (error.contains("SocketTimeoutException")) {
                        ret = OrderData.OrderPlaceStatus.CAN_REPEAT;
                    } else {
                        ret = OrderData.OrderPlaceStatus.ERROR;
                    }
                }
                iData.resetLiveOrders(); // clean cached data
            }
            if(ret != OrderData.OrderPlaceStatus.OK) {
                account.releaseOrder(orderData);
            }
        } else {
            log("ERROR: account allocateOrder unsuccessful: " + orderData + ", account: " + account);
            ret = OrderData.OrderPlaceStatus.ERROR;
        }
        log("placeOrder() END: " + orderData);
        return ret;
    }

    public static String formatAndPad(double value) {
        return Utils.padLeft(format(value - 100), 6);
    }

    private static String format(double number) {
        return Utils.PLUS_YYY.format(number);
    }

    public static String format4(double number) {
        return Utils.X_YYYY.format(number);
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
