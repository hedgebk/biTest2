package bthdg.triplet;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;

import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * - even on MKT steps - check prev steps live orders - have the case when peg order is missed but appers on next step
 * - catch all 3 @ mkt - just consequentially
 *   - cancel possible pegs first
 * - looks 'executed in X ms' calculated not properly
 * - when we increase to min order size - we run out of funds - check first
 * - if just placed and just executed - place blind mkt orders - will save time
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
    private enum Preset {
        BTCE{
            @Override void apply() {
                Triplet.s_exchange = Exchange.BTCE;
                Triplet.NUMBER_OF_ACTIVE_TRIANGLES = 7;
                Triplet.START_TRIANGLES_PER_ITERATION = 3;
                Triplet.LVL = 100.602408; // commission level - note - complex percents here
                Triplet.LVL2 = 100.72; // min target level
                Triplet.WAIT_MKT_ORDER_STEPS = 2;
                Triplet.TRY_WITH_MKT_OFFSET = false;
                Triplet.MKT_OFFSET_PRICE_MINUS = 0.11; // mkt - 10%
                Triplet.MKT_OFFSET_LEVEL_DELTA = 0.11;
                Triplet.ITERATIONS_SLEEP_TIME = 2100; // sleep between iterations
                Triplet.MIN_SLEEP_TIME = 300; // min sleep between iterations
                Triplet.PREFER_LIQUID_PAIRS = true; // prefer start from LTC_BTC, BTC_USD, LTC_USD
                Triplet.LOWER_LEVEL_FOR_LIQUIDITY_PAIRS = false; // LTC_BTC, BTC_USD, LTC_USD: level -= 0.02
                Triplet.LIQUIDITY_PAIRS_LEVEL_DELTA = 0.02;
                Triplet.USE_DEEP = true;

                Triplet.PAIRS = new Pair[]{Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD,
                                           Pair.PPC_USD, Pair.PPC_BTC, Pair.NMC_USD, Pair.NMC_BTC, Pair.NVC_USD, Pair.NVC_BTC,
                                           Pair.BTC_RUR, Pair.LTC_RUR, Pair.USD_RUR, Pair.EUR_RUR,
                                           Pair.BTC_GBP, Pair.LTC_GBP, Pair.GBP_USD,
                                           Pair.BTC_CNH, Pair.LTC_CNH, Pair.USD_CNH};

                Triplet.TRIANGLES = new Triangle[]{
                    new Triangle(Currency.USD, Currency.LTC, Currency.BTC), // usd -> ltc -> btc -> usd

                    new Triangle(Currency.EUR, Currency.LTC, Currency.BTC), // eur -> ltc -> btc -> eur
                    new Triangle(Currency.USD, Currency.LTC, Currency.EUR), // usd -> ltc -> eur -> usd
                    new Triangle(Currency.EUR, Currency.USD, Currency.BTC), // eur -> usd -> btc -> eur

                    new Triangle(Currency.USD, Currency.PPC, Currency.BTC), // usd -> ppc -> btc -> usd
                    new Triangle(Currency.USD, Currency.NMC, Currency.BTC), // usd -> nmc -> btc -> usd
                    new Triangle(Currency.USD, Currency.NVC, Currency.BTC), // usd -> nvc -> btc -> usd

                    new Triangle(Currency.BTC, Currency.RUR, Currency.LTC), // btc -> rur -> ltc -> btc
                    new Triangle(Currency.BTC, Currency.RUR, Currency.USD), // btc -> rur -> usd -> btc
                    new Triangle(Currency.USD, Currency.RUR, Currency.LTC), // usd -> rur -> ltc -> usd
                    new Triangle(Currency.BTC, Currency.RUR, Currency.EUR), // btc -> rur -> eur -> btc
                    new Triangle(Currency.EUR, Currency.RUR, Currency.LTC), // eur -> rur -> ltc -> eur
                    new Triangle(Currency.RUR, Currency.USD, Currency.EUR), // rur -> usd -> eur -> rur

                    new Triangle(Currency.GBP, Currency.USD, Currency.BTC), // gbp -> usd -> btc -> gbp
                    new Triangle(Currency.GBP, Currency.USD, Currency.LTC), // gbp -> usd -> ltc -> gbp
                    new Triangle(Currency.GBP, Currency.LTC, Currency.BTC), // gbp -> ltc -> btc -> gbp

                    new Triangle(Currency.CNH, Currency.USD, Currency.BTC), // cnh -> usd -> btc -> gbp
                    new Triangle(Currency.CNH, Currency.USD, Currency.LTC), // cnh -> usd -> ltc -> gbp
                    new Triangle(Currency.CNH, Currency.LTC, Currency.BTC), // cnh -> ltc -> btc -> gbp
                };
                Btce.LOG_PARSE = false;
                Btce.JOIN_SMALL_QUOTES = JOIN_SMALL_QUOTES;
            }
        },
        BTCN {
            @Override void apply() {
                Triplet.s_exchange = Exchange.BTCN;
                Triplet.NUMBER_OF_ACTIVE_TRIANGLES = 2;
                Triplet.START_TRIANGLES_PER_ITERATION = 1;
                Triplet.LVL  = 100.011; // commission level
                Triplet.LVL2 = 100.012; // min target level
                Triplet.WAIT_MKT_ORDER_STEPS = 0;
                Triplet.TRY_WITH_MKT_OFFSET = false;
                Triplet.MKT_OFFSET_PRICE_MINUS = 0.15; // mkt - 10%
                Triplet.MKT_OFFSET_LEVEL_DELTA = 0.15;
                Triplet.ITERATIONS_SLEEP_TIME = 3100; // sleep between iterations
                Triplet.MIN_SLEEP_TIME = 1500; // min sleep between iterations
                Triplet.PREFER_LIQUID_PAIRS = false; // prefer start from LTC_BTC, BTC_USD, LTC_USD
                Triplet.LOWER_LEVEL_FOR_LIQUIDITY_PAIRS = false; // LTC_BTC, BTC_USD, LTC_USD: level -= 0.02
                Triplet.LIQUIDITY_PAIRS_LEVEL_DELTA = 0.02;
                Triplet.USE_DEEP = false;

                Triplet.PAIRS = new Pair[]{Pair.LTC_BTC, Pair.BTC_CNH, Pair.LTC_CNH};

                Triplet.TRIANGLES = new Triangle[]{
                        new Triangle(Currency.CNH, Currency.LTC, Currency.BTC), // cnh -> ltc -> btc -> cnh
                };
            }
        };

        void apply(){}
    }

    public static Preset PRESET;

    public static int NUMBER_OF_ACTIVE_TRIANGLES = 7;
    public static int START_TRIANGLES_PER_ITERATION = 3;

    public static double LVL = 100.602408; // commission level - note - complex percents here
    public static double LVL2 = 100.70; // min target level
    public static final double LVL_INCREASE_RATE = 1.3;
    public static final double LVL_DECREASE_RATE = 1.2;
    public static int WAIT_MKT_ORDER_STEPS = 0;
    public static boolean TRY_WITH_MKT_OFFSET = false;
    public static double MKT_OFFSET_PRICE_MINUS = 0.15; // mkt - 10%
    public static double MKT_OFFSET_LEVEL_DELTA = 0.15;
    public static int ITERATIONS_SLEEP_TIME = 2100; // sleep between iterations
    public static int MIN_SLEEP_TIME = 300; // min sleep between iterations

    public static boolean PREFER_EUR_CRYPT_PAIRS = false; // BTC_EUR, LTC_EUR
    public static boolean PREFER_LIQUID_PAIRS = true; // prefer start from LTC_BTC, BTC_USD, LTC_USD
    public static boolean LOWER_LEVEL_FOR_LIQUIDITY_PAIRS = false; // LTC_BTC, BTC_USD, LTC_USD: level -= 0.02
    public static double LIQUIDITY_PAIRS_LEVEL_DELTA = 0.02;

    public static boolean USE_DEEP = true;
    public static final boolean JOIN_SMALL_QUOTES = true;
    public static final boolean ADJUST_AMOUNT_TO_MKT_AVAILABLE = true;
    public static final double PLACE_MORE_THAN_MKT_AVAILABLE = 1.1;
    public static final boolean ADJUST_TO_MIN_ORDER_SIZE = true;

    public static final boolean USE_TRI_MKT = false;
    public static final double TRI_MKT_LVL = 100.65; // min target level

    public static final boolean USE_BRACKETS = false;
    public static final double BRACKET_LEVEL_EXTRA = 0.19;
    public static final int BRACKET_DISTANCE_MAX = 2;

    public static final boolean USE_NEW = true;
    public static final boolean USE_RALLY = false;
    public static final boolean ALLOW_ONE_PRICE_STEP_CONCURRENT_PEG = false;
    public static final int LOAD_TRADES_NUM = 30; // num of last trades to load api
    public static final int LOAD_ORDERS_NUM = 3; // num of deep orders to load api
    public static final double USE_ACCOUNT_FUNDS = 0.96; // leave some for rounding
    private static final int MAX_PLACE_ORDER_REPEAT = 3;
    public static final double TOO_BIG_LOSS_LEVEL = 0.9985; // stop current trade if mkt conditions will give big loss
    public static final boolean SIMULATE = false;
    public static final boolean USE_ACCOUNT_TEST_STR = SIMULATE;
    public static final boolean SIMULATE_ORDER_EXECUTION = SIMULATE;

    public static final int NOT_ENOUGH_FUNDS_TREHSOLD = 2; // do not start new triangles

    public static double s_totalRatio = 1;
    public static int s_counter = 0;
    public static double s_level = LVL2;

    static Exchange s_exchange;
    static Pair[] PAIRS;
    public static Triangle[] TRIANGLES;

    static AccountData s_startAccount;
    public static double s_startEur;
    public static double s_startUsd;
    public static double s_startBtc;
    public static double s_startLtc;
    public static double s_startCnh;
    static boolean s_stopRequested;
    static int s_notEnoughFundsCounter;

    public static void main(String[] args) {
        System.out.println("Started");

        Fetcher.LOG_LOADING = false;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;

        Fetcher.USE_ACCOUNT_TEST_STR = USE_ACCOUNT_TEST_STR;
        Fetcher.SIMULATE_ORDER_EXECUTION = SIMULATE_ORDER_EXECUTION;
        Fetcher.SIMULATE_ACCEPT_ORDER_PRICE = false;
        Fetcher.SIMULATE_ACCEPT_ORDER_PRICE_RATE = 0.99;
        Btce.BTCE_TRADES_IN_REQUEST = LOAD_TRADES_NUM;
        Btce.BTCE_DEEP_ORDERS_IN_REQUEST = LOAD_ORDERS_NUM;

        try {
            applyPresets(args);
            s_level = LVL2;

            if (TRY_WITH_MKT_OFFSET && (WAIT_MKT_ORDER_STEPS < 1)) {
                System.out.println("WARNING: TRY_WITH_MKT_OFFSET used but WAIT_MKT_ORDER_STEPS=" + WAIT_MKT_ORDER_STEPS);
            }

            TradesAggregator tAgg = new TradesAggregator();
            tAgg.load();

            ConsoleReader consoleReader = new IntConsoleReader();
            consoleReader.start();

            try {
                TopsData tops = null;
                AccountData account = init(tAgg);

                long start = System.currentTimeMillis();
                int counter = 1;
                TriTradesData td = new TriTradesData(account);
                while (true) {
                    log("============================================== iteration " + (counter++) +
                            "; active " + td.m_triTrades.size() +
                            "; time=" + Utils.millisToDHMSStr(System.currentTimeMillis() - start) +
                            "; date=" + new Date() );
                    IterationData iData = new IterationData(tAgg, tops);
                    td.checkState(iData); // <<---------------------------------------------------------------------<<

                    int size = td.m_triTrades.size();
                    int sleep = ITERATIONS_SLEEP_TIME;
                    if (size == 0) {
                        if (s_stopRequested) {
                            System.out.println("stopRequested; nothing to process - exit");
                            break;
                        }
                        td.m_account = syncAccountIfNeeded(td.m_account);
                        sleep += sleep / 2; // no trades - sleep more

                        if (s_level > LVL2) { // decrease penalty level on empty iterations
                            double level = s_level;
                            s_level = (s_level - LVL) * 0.99 + LVL;
                            s_level = Math.max(s_level, LVL2);
                            log(" LEVEL decreased (-1%) from " + Utils.format8(level) + " to " + Utils.format8(Triplet.s_level));
                        }
                    } else {
                        if (size > 1) {
                            sleep /= 2;
                            if (size > 2) {
                                sleep /= 2;
                                if (size > 3) {
                                    sleep /= 2;
                                }
                            }
                        }
                    }
                    if(iData.isNoSleep()) {
                        System.out.println(" @@ isNoSleep requested");
                        sleep = MIN_SLEEP_TIME;
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

    private static void applyPresets(String[] args) {
        if (args.length == 0) {
            PRESET = Preset.BTCE; // use btce by def
        } else {
            String name = args[0];
            List<Exchange> exchanges = Exchange.resolveExchange(name);
            if (exchanges.isEmpty()) {
                throw new RuntimeException("unknown exchange '" + name + "'");
            }
            Exchange exchange = exchanges.get(0);
            switch (exchange) {
                case BTCE:
                    PRESET = Preset.BTCE;
                    break;
                case BTCN:
                    PRESET = Preset.BTCN;
                    break;
                default:
                    throw new RuntimeException("exchange '" + name + "' not supported by Triplet");
            }
        }
        PRESET.apply();
    }

    private static AccountData syncAccountIfNeeded(AccountData account) throws Exception {
        boolean gotFundDiff = account.m_gotFundDiff;
        if ((s_notEnoughFundsCounter > 0) || gotFundDiff) {
            System.out.println("!!!!!----- account is out of sync (notEnoughFundsCounter=" + s_notEnoughFundsCounter + ", gotFundDiff=" + gotFundDiff + "): " + account);
            AccountData newAccount = Fetcher.fetchAccount(s_exchange);
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
        Btcn.init(keys);

        AccountData account = Fetcher.fetchAccount(s_exchange);
        System.out.println("account: " + account);

        s_startAccount = account.copy();

        IterationData iData = new IterationData(tAgg, null);
        TopsData tops = iData.getTops();

        boolean supportsEur = s_exchange.supportsCurrency(Currency.EUR);
        if (supportsEur) {
            s_startEur = s_startAccount.evaluateEur(tops, s_exchange);
        }
        boolean supportsUsd = s_exchange.supportsCurrency(Currency.USD);
        if (supportsUsd) {
            s_startUsd = s_startAccount.evaluateUsd(tops, s_exchange);
        }
        boolean supportsBtc = s_exchange.supportsCurrency(Currency.BTC);
        if (supportsBtc) {
            s_startBtc = s_startAccount.evaluate(tops, Currency.BTC, s_exchange);
        }
        boolean supportsLtc = s_exchange.supportsCurrency(Currency.LTC);
        if (supportsLtc) {
            s_startLtc = s_startAccount.evaluate(tops, Currency.LTC, s_exchange);
        }
        boolean supportsCnh = s_exchange.supportsCurrency(Currency.CNH);
        if (supportsCnh) {
            s_startCnh = s_startAccount.evaluate(tops, Currency.CNH, s_exchange);
        }

        System.out.println((supportsEur?" evaluateEur: " + format5(s_startEur):"") +
                           (supportsUsd?" evaluateUsd: " + format5(s_startUsd):"")  +
                           (supportsBtc?" evaluateBtc: " + format5(s_startBtc):"") +
                           (supportsLtc?" evaluateLtc: " + format5(s_startLtc):"") +
                           (supportsLtc?" evaluateCnh: " + format5(s_startCnh):"")
        );
        return account;
    }

    // todo: to move this to OrderData as NON-static method
    public static OrderData.OrderPlaceStatus placeOrder(AccountData account, OrderData orderData, OrderState state, IterationData iData) throws Exception {
        log("placeOrder() " + iData.millisFromStart() + "ms: " + orderData.toString(s_exchange));

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
        //log("placeOrder() END: " + orderData.toString(s_exchange));
        return ret;
    }

    private static OrderData.OrderPlaceStatus placeOrderToExchange(AccountData account, OrderData orderData, OrderState state, IterationData iData) throws Exception {
        int repeatCounter = MAX_PLACE_ORDER_REPEAT;
        while( true ) {
            OrderData.OrderPlaceStatus ret;
            PlaceOrderData poData = Fetcher.placeOrder(orderData, s_exchange);
            log(" PlaceOrderData: " + poData);
            String error = poData.m_error;
            if (error == null) {
                orderData.m_status = OrderStatus.SUBMITTED;
                double amount = poData.m_received;
                if (amount != 0) {
                    String amountStr = orderData.roundAmountStr(s_exchange, amount);
                    String orderAmountStr = orderData.roundAmountStr(s_exchange);
                    log("  some part of order (" + amountStr + " from " + orderAmountStr + ") is executed at the time of placing ");
                    double price = orderData.m_price;
                    orderData.addExecution(price, amount, s_exchange);
                    account.releaseTrade(orderData.m_pair, orderData.m_side, price, amount);
                }
                if (poData.m_accountData != null) {
                    poData.m_accountData.compareFunds(account);
                }
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
                } else if (error.contains("It is not enough") || // It is not enough BTC in the account for sale
                          (error.contains("Insufficient") && error.contains("balance"))) { // Insufficient CNY balance
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

    private static void log(String s) {
        Log.log(s);
    }

    public static double roundPrice(double price, Pair pair) {
        return s_exchange.roundPrice(price, pair);
    }

    public static String roundPriceStr(double price, Pair pair) {
        return s_exchange.roundPriceStr(price, pair);
    }

    private static class IntConsoleReader extends ConsoleReader {
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
