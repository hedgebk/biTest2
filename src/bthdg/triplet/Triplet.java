package bthdg.triplet;

import bthdg.*;
import bthdg.exch.Currency;
import bthdg.exch.*;

import java.util.*;

/**
 * - do not wait between iterations if just placed
 *   - if just placed and just executed - place blind mkt orders - will save time
 * - join with nearest if best in the book has TOO small size
 * - track original orders size and log with forks at least % of original order
 * - do not place second order on the same triangle if we have one (bracket can be exception if allocated < available).
 * - place both MKT orders at the same time no wait for first execution - looks fork/split will be complex
 * - when checking for 'peg to live' check mkt availability too
 * - give penalty on triangle+rotation base
 * - have levels on triangle+rotation base
 * - discard my peg orders from loaded deep data
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
 */
public class Triplet {
    public static final int NUMBER_OF_ACTIVE_TRIANGLES = 3;
    public static final boolean START_ONE_TRIANGLE_PER_ITERATION = true;

    public static final double LVL = 100.602408; // commission level - note - complex percents here
    public static final double LVL2 = 100.71; // min target level
    public static final int WAIT_MKT_ORDER_STEPS = 1;
    public static final boolean TRY_WITH_MKT_OFFSET = true;
    public static final double MKT_OFFSET_PRICE_MINUS = 0.07; // mkt - 10%
    public static final double MKT_OFFSET_LEVEL_DELTA = 0.07;
    public static final int ITERATIONS_SLEEP_TIME = 2100; // sleep between iterations

    public static final boolean PREFER_EUR_CRYPT_PAIRS = false; // BTC_EUR, LTC_EUR
    public static final boolean PREFER_LIQUID_PAIRS = false; // LTC_BTC, BTC_USD, LTC_USD
    public static final boolean LOWER_LEVEL_FOR_LIQUIDITY_PAIRS = false; // LTC_BTC, BTC_USD, LTC_USD: level -= 0.02
    public static final double LIQUIDITY_PAIRS_LEVEL_DELTA = 0.02;

    public static final boolean USE_BRACKETS = false;
    public static final double BRACKET_LEVEL_EXTRA = 0.19;
    public static final int BRACKET_DISTANCE_MAX = 2;

    public static final boolean USE_DEEP = true;
    public static final boolean ADJUST_AMOUNT_TO_MKT_AVAILABLE = true;
    public static final double PLACE_MORE_THAN_MKT_AVAILABLE = 1.1;
    public static final boolean ADJUST_TO_MIN_ORDER_SIZE = true;

    public static final boolean USE_NEW = true;
    public static final boolean USE_RALLY = false;
    public static final boolean ALLOW_ONE_PRICE_STEP_CONCURRENT_PEG = false;
    public static final int LOAD_TRADES_NUM = 30; // num of last trades to load api
    public static final int LOAD_ORDERS_NUM = 3; // num of deep orders to load api
    public static final double USE_ACCOUNT_FUNDS = 0.95;
    private static final int MAX_PLACE_ORDER_REPEAT = 3;
    public static final double TOO_BIG_LOSS_LEVEL = 0.994; // stop current trade if mkt conditions will give big loss
    public static final boolean SIMULATE = false;
    public static final boolean USE_ACCOUNT_TEST_STR = SIMULATE;
    public static final boolean SIMULATE_ORDER_EXECUTION = SIMULATE;

    public static double s_totalRatio = 1;
    public static int s_counter = 0;
    public static double s_level = LVL2;

    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD, Pair.PPC_USD, Pair.PPC_BTC};

    public static final Triangle T1 = new Triangle(Currency.USD, Currency.LTC, Currency.BTC); // usd -> ltc -> btc -> usd
    public static final Triangle T2 = new Triangle(Currency.EUR, Currency.LTC, Currency.BTC); // eur -> ltc -> btc -> eur
    public static final Triangle T3 = new Triangle(Currency.USD, Currency.LTC, Currency.EUR); // usd -> ltc -> eur -> usd
    public static final Triangle T4 = new Triangle(Currency.EUR, Currency.USD, Currency.BTC); // eur -> usd -> btc -> eur
    public static final Triangle T5 = new Triangle(Currency.USD, Currency.PPC, Currency.BTC); // usd -> ppc -> btc -> usd
    public static final Triangle[] TRIANGLES = new Triangle[]{T1, T2, T3, T4, T5};

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
