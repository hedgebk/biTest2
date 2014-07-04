package bthdg.run;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.exch.Currency;
import bthdg.util.Utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * - save open orders/state on stop; read/restore state on start
 * - maintain isExchLive, correctly fold if exch is down to not affect others;
 *   - fold all if no inet connection; restart fine on connectivity
 */
class BiAlgo implements Runner.IAlgo {
    private static final double START_LEVEL = 0.0001; // 0.0003;
    private static final long MOVING_AVERAGE = Utils.toMillis(4, 28); // TODO: make exchange_pair dependent
    private static final long MIN_ITERATION_TIME = 2500;
    private static final double USE_ACCT_FUNDS = 0.95;

    private final Pair PAIR = Pair.BTC_CNH;
    private final Exchange[] ALL_EXCHANGES = new Exchange[] {Exchange.BTCN, Exchange.OKCOIN/*, Exchange.HUOBI*/};
    private final Exchange[] TRADE_EXCHANGES = new Exchange[] {Exchange.BTCN, Exchange.OKCOIN};

    private List<ExchangesPair> m_exchPairs = mkExchPairs();
    private List<ExchangesPairData> m_exchPairsDatas = mkExchPairsDatas();
    private MdStorage m_mdStorage = new MdStorage();
    private Runnable m_stopRunnable;
    private int m_maxExchNameLen; // for formatting
    private Map<Exchange, AccountData> m_startAccountMap = new HashMap<Exchange, AccountData>();
    private Map<Exchange, AccountData> m_accountMap = new HashMap<Exchange, AccountData>();
    private static final int MAX_PLACE_ORDER_REPEAT = 2;
    private static int s_notEnoughFundsCounter;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }
    @Override public void stop(Runnable runnable) { m_stopRunnable = runnable; }

    public BiAlgo() {}

    @Override public void runAlgo() {
        new Thread("Bialgo") {
            @Override public void run() {
                runAlgoInt();
            }
        }.start();
    }

    private void runAlgoInt() {
        try {
            runInit();
            long start = System.currentTimeMillis();
            int iterationCount = 0;
            while (m_stopRunnable == null) {
                long millis = System.currentTimeMillis();
                log("iteration " + iterationCount + " ---------------------- elapsed: " + Utils.millisToDHMSStr(millis - start));
                IterationHolder ih = new IterationHolder(iterationCount);
                runIteration(ih);
                sleepIfNeeded(millis);
                iterationCount++;
            }
            m_stopRunnable.run();
        } catch (Exception e) {
            err("runAlgo error: " + e, e);
        }
    }

    private void runInit() throws Exception {
        doInParallel("getAccountData", new IExchangeRunnable() {
            @Override public void run(Exchange exchange) throws Exception {
                Properties keys = BaseExch.loadKeys();
                exchange.init(keys);
                AccountData accountData = Fetcher.fetchAccount(exchange);
                m_accountMap.put(exchange, accountData);
                m_startAccountMap.put(exchange, accountData.copy());
                log("Start account(" + exchange.m_name + "): " + accountData);
            }
        });
    }

    private void runIteration(IterationHolder ih) throws Exception {
        runBfrMkt(ih);

        doInParallel("getMktData", new IExchangeRunnable() {
            @Override public void run(Exchange exchange) throws Exception {
//                TopData topData = Fetcher.fetchTop(exchange, m_pair);

                DeepData deeps = Fetcher.fetchDeep(exchange, PAIR);
                TopData topData = deeps.getTopDataAdapter();

                m_mdStorage.put(exchange, PAIR, deeps, topData);
            }
        });

        int maxExchNameLen = getMaxExchNameLen();
        for (Exchange exchange : ALL_EXCHANGES) { // log loaded tops
            MktDataHolder topDataHolder = m_mdStorage.get(exchange, PAIR);
            log(Utils.padRight(exchange.name(), maxExchNameLen) + ": " + topDataHolder.topData());
        }

        runForPairs(ih);
    }

    private void runBfrMkt(IterationHolder ih) {
        for (ExchangesPairData exchPairsData : m_exchPairsDatas) {
            try {
                exchPairsData.runBfrMkt(ih);
            } catch (Exception e) {
                err("runBfrMkt failed: " + exchPairsData + " :" + e, e);
            }
        }
    }

    private void runForPairs(IterationHolder ih) {
        for (ExchangesPairData exchPairsData : m_exchPairsDatas) {
            try {
                exchPairsData.run(ih);
            } catch (Exception e) {
                err("runForPair failed: " + exchPairsData + " :" + e, e);
            }
        }
    }

    private void doInParallel(String name, final IExchangeRunnable eRunnable) throws InterruptedException {
        doInParallel(name, ALL_EXCHANGES, eRunnable);
    }

    private void doInParallel(String name, Exchange[] exchanges, final IExchangeRunnable eRunnable) throws InterruptedException {
        int length = exchanges.length;
        final AtomicInteger counter = new AtomicInteger(length);
        for (int i = length - 1; i >= 0; i--) {
            final Exchange exchange = exchanges[i];
            Runnable r = new Runnable() {
                @Override public void run() {
                    try {
                        eRunnable.run(exchange);
                    } catch (Exception e) {
                        err("runForOnExch error: " + e, e);
                    } finally {
                        synchronized (counter) {
                            int value = counter.decrementAndGet();
                            if (value == 0) {
                                counter.notifyAll();
                            }
                        }
                    }
                }
            };
            new Thread(r, exchange.name() + "_" + name).start();
        }
        synchronized (counter) {
            if(counter.get() != 0) {
                counter.wait();
            }
        }
    }

    private List<ExchangesPairData> mkExchPairsDatas() {
        List<ExchangesPairData> ret = new ArrayList<ExchangesPairData>(m_exchPairs.size());
        for (ExchangesPair exchangesPair : m_exchPairs) {
            ret.add(new ExchangesPairData(exchangesPair));
        }
        return ret;
    }

    private List<ExchangesPair> mkExchPairs() {
        List<ExchangesPair> ret = new ArrayList<ExchangesPair>();
        for (Exchange exch1 : ALL_EXCHANGES) {
            boolean foundSelf = false;
            for (Exchange exch2 : ALL_EXCHANGES) {
                if (foundSelf) {
                    ret.add(new ExchangesPair(exch1, exch2));
                } else if (exch1 == exch2) {
                    foundSelf = true;
                }
            }
        }
        return ret;
    }

    private boolean isTradeExchange(Exchange exch1) {
        return (Arrays.binarySearch(TRADE_EXCHANGES, exch1) >= 0);
    }

    private void sleepIfNeeded(long start) throws InterruptedException {
        long took = System.currentTimeMillis() - start;
        log(" iteration took " + took + " ms");
        if(took < MIN_ITERATION_TIME) {
            long toSleep = MIN_ITERATION_TIME - took;
            log("  to sleep " + toSleep + " ms...");
            Thread.sleep(toSleep);
        }
    }

    public int getMaxExchNameLen() {
        if (m_maxExchNameLen == 0) { // calc once
            for (Exchange exchange : ALL_EXCHANGES) {
                m_maxExchNameLen = Math.max(m_maxExchNameLen, exchange.name().length());
            }
        }
        return m_maxExchNameLen;
    }

    private static OrderData.OrderPlaceStatus placeOrderToExchange(AccountData account, OrderData orderData, OrderState state) throws Exception {
        Exchange exchange = account.m_exchange;
        int repeatCounter = MAX_PLACE_ORDER_REPEAT;
        while( true ) {
            OrderData.OrderPlaceStatus ret;
            PlaceOrderData poData = Fetcher.placeOrder(orderData, exchange);
            log(" PlaceOrderData: " + poData.toString(exchange, orderData.m_pair));
            String error = poData.m_error;
            if (error == null) {
                orderData.m_status = OrderStatus.SUBMITTED;
                orderData.m_state = state;
                ret = OrderData.OrderPlaceStatus.OK;
            } else {
                orderData.m_status = OrderStatus.ERROR;
                orderData.m_state = OrderState.NONE;
                if (error.contains("SocketTimeoutException")) {
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
//                } else if (error.contains("must be greater than")) { // Value BTC must be greater than 0.01 BTC.
//                    ret = OrderData.OrderPlaceStatus.ERROR; // too small order - can not continue
//                    orderData.m_status = OrderStatus.REJECTED;
//                    log("  too small order - can not continue: " + error );
//                } else if (error.contains("invalid nonce parameter")) {
//                    throw new RuntimeException("from server: "+ error);
                } else {
                    ret = OrderData.OrderPlaceStatus.ERROR;
                }
            }
            return ret;
        }
    }

    /** **************************************************************************************** */
    private static class ExchangesPair {
        public final Exchange m_exchange1;
        public final Exchange m_exchange2;

        private ExchangesPair(Exchange exchange1, Exchange exchange2) {
            m_exchange1 = exchange1;
            m_exchange2 = exchange2;
        }

        public String name() {
            return m_exchange1.name() + "_" + m_exchange2.name();
        }
    }

    private class ExchangesPairData {
        public final ExchangesPair m_exchangesPair;
        private final Exchange[] m_exchanges;
        private final boolean m_doTrade;
        private final Utils.AverageCounter m_diffAverageCounter;
        private final List<TimeDiff> m_timeDiffs = new ArrayList<TimeDiff>();
        private final List<BiAlgoData> m_live = new ArrayList<BiAlgoData>();

        public String name() { return m_exchangesPair.name(); }

        private ExchangesPairData(ExchangesPair exchangesPair) {
            m_exchangesPair = exchangesPair;
            m_doTrade = isTradeExchange(m_exchangesPair.m_exchange1) && isTradeExchange(m_exchangesPair.m_exchange2);
            m_diffAverageCounter = new Utils.AverageCounter(MOVING_AVERAGE);
            m_exchanges = new Exchange[]{m_exchangesPair.m_exchange1, m_exchangesPair.m_exchange2};
        }

        public void run(IterationHolder ih) throws Exception {
            // TODO: introduce exchangesPair.priceFormatter - since precisions can be different on exchanges
            Exchange e1 = m_exchangesPair.m_exchange1;
            Exchange e2 = m_exchangesPair.m_exchange2;
            MktDataHolder mktData1 = m_mdStorage.get(e1, PAIR);
            MktDataHolder mktData2 = m_mdStorage.get(e2, PAIR);
            MktDataPoint mdPoint1 = mktData1.m_mdPoint;
            MktDataPoint mdPoint2 = mktData2.m_mdPoint;
            TopData td1 = mdPoint1.m_topData;
            TopData td2 = mdPoint2.m_topData;
            double mid1 = td1.getMid();
            double mid2 = td2.getMid();
            double diff = mid1 - mid2;
            double bidAskDiff1 = td1.getBidAskDiff();
            double bidAskDiff2 = td2.getBidAskDiff();
            double bidAskDiff = bidAskDiff1 + bidAskDiff2;
            double avgDiff = m_diffAverageCounter.add(diff);
            double diffDiff = diff - avgDiff;
            log(name() + " diff=" + e1.roundPriceStr(diff, PAIR) + // TODO: introduce roundPriceStrPlus - to add some precision
                    ", avgDiff="+ e1.roundPriceStr(avgDiff, PAIR) +
                    ", diffDiff="+ e1.roundPriceStr(diffDiff, PAIR) +
                    ", bidAskDiff="+ e1.roundPriceStr(bidAskDiff, PAIR));
            m_timeDiffs.add(new TimeDiff(System.currentTimeMillis(), diff));

            BiPoint mktPoint = new BiPoint(mdPoint1, mdPoint2, avgDiff, diffDiff, bidAskDiff);

            checkLiveForEnd(ih, mktPoint);

            checkForNew(ih, mktPoint);
        }

        private void checkLiveForEnd(IterationHolder ih, BiPoint mktPoint) throws Exception {
            for(BiAlgoData data : m_live){
                checkForEnd(ih, data, mktPoint);
            }
        }

        private void checkForEnd(IterationHolder ih, BiAlgoData data, BiPoint mktPoint) throws Exception {
            if(((mktPoint.m_diffDiff > 0) && data.m_up)
               || ((mktPoint.m_diffDiff < 0) && !data.m_up)) { // look for opposite difDiffs
                double gain = mktPoint.gain();
                double midMid = mktPoint.midMid();
                double level = gain / midMid;

                Exchange e1 = m_exchangesPair.m_exchange1;
                log(name() + " E  gain=" + e1.roundPriceStr(gain, PAIR) +
                        ", midMid="+ e1.roundPriceStr(midMid, PAIR) +
                        ", level="+ Utils.format8(level));
                if (level > START_LEVEL) {
                    log(name() + "   GOT END");
                    onEnd(ih, data, mktPoint);
                }
            }
        }

        private void checkForNew(IterationHolder ih, BiPoint mktPoint) throws Exception {
            double gain = mktPoint.gain();
            double midMid = mktPoint.midMid();
            double level = gain / midMid;

            Exchange e1 = m_exchangesPair.m_exchange1;
            log(name() + " S  gain=" + e1.roundPriceStr(gain, PAIR) +
                    ", midMid="+ e1.roundPriceStr(midMid, PAIR) +
                    ", level="+ Utils.format8(level));
            if (level > START_LEVEL) {
                log(name() + "   GOT START");
                if(m_doTrade) {
                    open(ih, mktPoint, null);
                }
            }
        }

        private void open(IterationHolder ih, BiPoint mktPoint, BiAlgoData baData) throws Exception {
            boolean up = !mktPoint.aboveAverage();
            boolean start = (baData == null);
            log("start=" + start+ ";   up=" + up + ";   PAIR=" + PAIR);
            if (baData != null) {
                log("BiAlgoData=" + baData);
            }

            // up -> 1st buy, 2nd sell; buy BTC_CNH meant spent CNH get BTC;
            Currency currency1 = PAIR.currencyFrom(up);
            Currency currency2 = PAIR.currencyFrom(!up);
            Currency baseCurrency = PAIR.currencyFrom(false);
            log("currency1=" + currency1 + ";   currency2=" + currency2 + ";   baseCurrency=" + baseCurrency);

            MktDataPoint mdPoint1 = mktPoint.m_mdPoint1;
            MktDataPoint mdPoint2 = mktPoint.m_mdPoint2;

            DeepData deeps1 = mdPoint1.m_deeps;
            log("deeps1=" + deeps1.toString(2));
            DeepData deeps2 = mdPoint2.m_deeps;
            log("deeps2=" + deeps2.toString(2));

            OrderSide side1 = up ? OrderSide.BUY : OrderSide.SELL;
            OrderSide side2 = up ? OrderSide.SELL : OrderSide.BUY;
            log("side1=" + side1 + ";   side2=" + side2);

            DeepData.Deep deep1 = side1.getDeep(deeps1);
            log("deep1=" + deep1);
            DeepData.Deep deep2 = side2.getDeep(deeps2);
            log("deep2=" + deep2);

            double size1 = deep1.m_size;
            double size2 = deep2.m_size;
            log("size1=" + size1 + " " + baseCurrency + ";   size2=" + size2 + " " + baseCurrency);
            double minSize = Math.min(size1, size2);
            log("minMktAvailableSize=" + minSize + " " + baseCurrency);

            Exchange exchange1 = m_exchangesPair.m_exchange1;
            Exchange exchange2 = m_exchangesPair.m_exchange2;
            log("exchange1=" + exchange1 + ";   exchange2=" + exchange2);

            AccountData account1 = m_accountMap.get(exchange1);
            log("account1=" + account1);
            AccountData account2 = m_accountMap.get(exchange2);
            log("account2=" + account2);

            Double amount = start
                    ? getOpenAmount(up, mktPoint, minSize)
                    : getCloseAmount(baData);

            if (amount != null) {
                if (baData != null) {
                    double openAmount = baData.m_amount;
                    amount = Math.min(openAmount, openAmount);
                    log("openAmount=" + openAmount + "  ->  amount=" + amount);
                }
                double price1 = deep1.m_price;
                double price2 = deep2.m_price;
                log("price1=" + price1 + ";   price2=" + price2);

                OrderData orderData1 = new OrderData(PAIR, side1, price1, amount);
                log("orderData1=" + orderData1);
                OrderData orderData2 = new OrderData(PAIR, side2, price2, amount);
                log("orderData2=" + orderData2);

                OrderDataExchange ode1 = new OrderDataExchange(orderData1, exchange1);
                OrderDataExchange ode2 = new OrderDataExchange(orderData2, exchange2);

                if(start) {
                    BiAlgoData biAlgoData = new BiAlgoData(up, PAIR, mktPoint, amount, ode1, ode2);
                    m_live.add(biAlgoData);
                    biAlgoData.placeOpenOrders(ih);
                } else {
                    baData.placeCloseOrders(ih, ode1, ode2);
                }
            }

            log(".....................................");
        }

        private Double getCloseAmount(BiAlgoData baData) {
            // todo: use max(remained) here
            // todo: check against available in account
            return baData.m_amount;
        }

        private Double getOpenAmount(boolean up, BiPoint mktPoint, double minMktSize) {
            Currency currency1 = PAIR.currencyFrom(up);
            Currency currency2 = PAIR.currencyFrom(!up);
            Currency baseCurrency = PAIR.currencyFrom(false);

            Exchange exchange1 = m_exchangesPair.m_exchange1;
            Exchange exchange2 = m_exchangesPair.m_exchange2;

            AccountData account1 = m_accountMap.get(exchange1);
            AccountData account2 = m_accountMap.get(exchange2);

            double available1 = account1.available(currency1);
            double available2 = account2.available(currency2);
            log("available1=" + available1 + " " + currency1 + ";  available2=" + available2 + " " + currency2);
            if (currency1 != baseCurrency) {
                MktDataPoint mdPoint1 = mktPoint.m_mdPoint1;
                double mid = mdPoint1.m_topData.getMid();
                available1 = available1 / mid;
                log(" converted available1=" + available1 + " " + baseCurrency);
            }
            if (currency2 != baseCurrency) {
                MktDataPoint mdPoint2 = mktPoint.m_mdPoint2;
                double mid = mdPoint2.m_topData.getMid();
                available2 = available2 / mid;
                log(" converted available2=" + available2 + " " + baseCurrency);
            }

            double minAvailable = Math.min(available1, available2);
            log("minAvailable=" + minAvailable);

            double amount = Math.min(minMktSize, minAvailable * USE_ACCT_FUNDS);
            log("amount for order=" + amount + " " + baseCurrency);

            double minOrderToCreate1 = exchange1.minOrderToCreate(PAIR);
            double minOrderToCreate2 = exchange2.minOrderToCreate(PAIR);
            log("minOrderToCreate1=" + minOrderToCreate1 + ";   minOrderToCreate2=" + minOrderToCreate2);

            if (amount >= minOrderToCreate1) {
                if (amount >= minOrderToCreate2) {
amount = Math.max(minOrderToCreate1, minOrderToCreate2);
log("FOR NOW amount HACKED to=" + amount);
                    return amount;
                } else {
                    log("amount=" + amount + " not reached minOrderToCreate2=" + minOrderToCreate2);
                }
            } else {
                log("amount=" + amount + " not reached minOrderToCreate1=" + minOrderToCreate1);
            }
            return null;
        }

        private void onEnd(IterationHolder ih, BiAlgoData data, BiPoint end) throws Exception {
            boolean startUp = data.m_up;
            BiPoint start = data.m_startPoint;
            boolean endUp = !end.aboveAverage();
            double balance1 = startUp
                    ? -start.m_mdPoint1.m_topData.m_ask + end.m_mdPoint1.m_topData.m_bid
                    :  start.m_mdPoint1.m_topData.m_bid - end.m_mdPoint1.m_topData.m_ask;
            double balance2 = startUp
                    ?  start.m_mdPoint2.m_topData.m_bid - end.m_mdPoint2.m_topData.m_ask
                    : -start.m_mdPoint2.m_topData.m_ask + end.m_mdPoint2.m_topData.m_bid;
            double balance = balance1 + balance2;

            log(name() + "%%%%%%%%%");
            log(name() + "%%%%%% START up=" + startUp + "; top1=" + start.m_mdPoint1.m_topData + "; top2=" + start.m_mdPoint2.m_topData);
            log(name() + "%%%%%% END   up=" + endUp +   "; top1=" + end.m_mdPoint1.m_topData +     "; top2=" + end.m_mdPoint2.m_topData    );
            log(name() + "%%%%%%");
            log(name() + "%%%%%% START buy " + (startUp ? "1" : "2") + " @ " + (startUp ? start.m_mdPoint1.m_topData : start.m_mdPoint2.m_topData).askStr() +
                                   "; sell " + (startUp ? "2" : "1") + " @ " + (startUp ? start.m_mdPoint2.m_topData : start.m_mdPoint1.m_topData).bidStr() );
            log(name() + "%%%%%% END   buy " + (startUp ? "2" : "1") + " @ " + (startUp ? end.m_mdPoint2.m_topData   : end.m_mdPoint1.m_topData  ).askStr() +
                                   "; sell " + (startUp ? "1" : "2") + " @ " + (startUp ? end.m_mdPoint1.m_topData   : end.m_mdPoint2.m_topData  ).bidStr() );
            log(name() + "%%%%%%");
            log(name() + "%%%%%% balance1=" + balance1 + "; balance2=" + balance2 + "; balance=" + balance);
            log(name() + "%%%%%%");

            open(ih, end, data);
            log("***********************************************");
        }

        public void runBfrMkt(IterationHolder ih) {
//            for(BiAlgoData data : m_live){
//                BiAlgoState state = data.m_state;
//                if(state.isPending()) {
//
//                }
//
//            }
        }

    }

    // pre-calculated top data relations
    static class BiPoint {
        final MktDataPoint m_mdPoint1;
        final MktDataPoint m_mdPoint2;
        final double m_avgDiff;
        // cached
        final double m_diffDiff;
        final double m_bidAskDiff;

        public boolean aboveAverage() { return (m_diffDiff > 0); }

        public BiPoint(MktDataPoint mdPoint1, MktDataPoint mdPoint2, double avgDiff, double diffDiff, double bidAskDiff) {
            m_mdPoint1 = mdPoint1;
            m_mdPoint2 = mdPoint2;
            m_avgDiff = avgDiff;
            m_diffDiff = diffDiff;
            m_bidAskDiff = bidAskDiff;
        }

        public double gain() {
            return Math.abs(m_diffDiff) - (m_bidAskDiff / 2);
        }

        public double midMid() {
            double mid1 = m_mdPoint1.m_topData.getMid();
            double mid2 = m_mdPoint2.m_topData.getMid();
            return (mid1 + mid2) / 2;
        }
    }

    public static class TimeDiff {
        public final long m_millis;
        public final double m_diff;

        public TimeDiff(long millis, double diff) {
            m_millis = millis;
            m_diff = diff;
        }
    }

    private interface IExchangeRunnable {
        void run(Exchange exchange) throws Exception;
    }
}
