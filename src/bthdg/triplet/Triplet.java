package bthdg.triplet;

import bthdg.*;
import bthdg.exch.BaseExch;
import bthdg.exch.Btce;

import java.util.*;

// - try place brackets for non-profitable pairs / sorted by trades num
public class Triplet {
    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};
    public static final int LOAD_TRADES_NUM = 30;
    public static final double LVL = 100.6; // commission level
    public static final double LVL2 = 100.68; // min target level

    public static final double USE_ACCOUNT_FUNDS = 0.97;
    public static double s_totalRatio = 1;
    public static int s_counter = 0;
    public static double s_level = LVL2;
    public static final int WAIT_MKT_ORDER_STEPS = 4;

    public static final Triangle T1 = new Triangle(Pair.LTC_USD, true,  Pair.LTC_BTC, false, Pair.BTC_USD, false); // usd -> ltc -> btc -> usd
    public static final Triangle T2 = new Triangle(Pair.LTC_EUR, true,  Pair.LTC_BTC, false, Pair.BTC_EUR, false); // eur -> ltc -> btc -> eur
    public static final Triangle T3 = new Triangle(Pair.LTC_USD, true,  Pair.LTC_EUR, false, Pair.EUR_USD, false); // usd -> ltc -> eur -> usd
    public static final Triangle T4 = new Triangle(Pair.EUR_USD, false, Pair.BTC_USD, true,  Pair.BTC_EUR, false); // eur -> usd -> btc -> eur

    public static final Triangle[] TRIANGLES = new Triangle[]{T1, T2, T3, T4};

    public static void main(String[] args) {
        System.out.println("Started");
        Fetcher.LOG_LOADING = false;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
        Fetcher.USE_ACCOUNT_TEST_STR = true;
        Fetcher.SIMULATE_ACCEPT_ORDER_PRICE = false;
        Fetcher.SIMULATE_ACCEPT_ORDER_PRICE_RATE = 0.99;
        Btce.BTCE_TRADES_IN_REQUEST = LOAD_TRADES_NUM;

        try {
            Properties keys = BaseExch.loadKeys();
            Btce.init(keys);

            TradesAggregator tAgg = new TradesAggregator();
            tAgg.load();

            AccountData account = getAccount();
            System.out.println("account: " + account);

            long start = System.currentTimeMillis();
            int counter = 1;
            TriangleData td = new TriangleData(account);
            while (true) {
                log("============================================== iteration " + (counter++) + "; time=" + Utils.millisToDHMSStr(System.currentTimeMillis() - start));
                IterationData iData = new IterationData(tAgg);
                td.checkState(iData);
                Thread.sleep(3000);
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    public static boolean placeOrder(AccountData account, OrderData orderData, OrderState state) {
        log("placeOrder() not implemented yet: " + orderData);

        boolean success = account.allocateOrder(orderData);
        if (success) {
            orderData.m_status = OrderStatus.SUBMITTED;
            orderData.m_state = state;
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
