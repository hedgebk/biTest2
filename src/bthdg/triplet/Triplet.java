package bthdg.triplet;

import bthdg.*;
import bthdg.exch.BaseExch;
import bthdg.exch.Btce;
import bthdg.exch.PlaceOrderData;
import bthdg.exch.TopData;

import java.util.*;

/**
 * - try place brackets for non-profitable pairs / sorted by trades num
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
 */
public class Triplet {
    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};
    public static final int LOAD_TRADES_NUM = 30;
    public static final double LVL = 100.6; // commission level
    public static final double LVL2 = 100.65; // min target level
    public static final boolean ONLY_ONE_ACTIVE_TRIANGLE = true;

    public static final double USE_ACCOUNT_FUNDS = 0.97;
    public static double s_totalRatio = 1;
    public static int s_counter = 0;
    public static double s_level = LVL2;
    public static final int WAIT_MKT_ORDER_STEPS = 5;
    public static final int ITERATIONS_SLEEP_TIME = 3000; // sleep between iterations

    public static final Triangle T1 = new Triangle(Pair.LTC_USD, true,  Pair.LTC_BTC, false, Pair.BTC_USD, false); // usd -> ltc -> btc -> usd
    public static final Triangle T2 = new Triangle(Pair.LTC_EUR, true,  Pair.LTC_BTC, false, Pair.BTC_EUR, false); // eur -> ltc -> btc -> eur
    public static final Triangle T3 = new Triangle(Pair.LTC_USD, true,  Pair.LTC_EUR, false, Pair.EUR_USD, false); // usd -> ltc -> eur -> usd
    public static final Triangle T4 = new Triangle(Pair.EUR_USD, false, Pair.BTC_USD, true,  Pair.BTC_EUR, false); // eur -> usd -> btc -> eur

    public static final Triangle[] TRIANGLES = new Triangle[]{T1, T2, T3, T4};
    private static AccountData s_startAccount;
    public static double s_startEvaluate;

    public static void main(String[] args) {
        System.out.println("Started");
        Fetcher.LOG_LOADING = false;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
        Fetcher.USE_ACCOUNT_TEST_STR = true;
        Fetcher.SIMULATE_ORDER_EXECUTION = true;
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
        Map<Pair,TopData> tops = iData.getTops();
        s_startEvaluate = s_startAccount.evaluate(tops);
        return account;
    }

    public static boolean placeOrder(AccountData account, OrderData orderData, OrderState state) throws Exception {
        log("placeOrder(): " + orderData);

        boolean success = account.allocateOrder(orderData);
        if (success) {
            if (Fetcher.SIMULATE_ORDER_EXECUTION) {
                orderData.m_status = OrderStatus.SUBMITTED;
                orderData.m_state = state;
            } else {
                PlaceOrderData poData = Fetcher.placeOrder(orderData, Exchange.BTCE);
                log(" PlaceOrderData: " + poData);
                if (poData.m_error == null) {
                    orderData.m_status = OrderStatus.SUBMITTED;
                    orderData.m_state = state;
                } else {
                    orderData.m_status = OrderStatus.REJECTED;
                    orderData.m_state = OrderState.NONE;
                    success = false;
                }
            }
        } else {
            log("ERROR: account allocateOrder unsuccessful: " + orderData + ", account: " + account);
        }
        return success;
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

    public static AccountData getAccount() throws Exception {
        AccountData account = Fetcher.fetchAccount(Exchange.BTCE);
        return account;
    }

    private static void log(String s) {
        Log.log(s);
    }
}
