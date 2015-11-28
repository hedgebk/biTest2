package bthdg.run;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.exch.Currency;
import bthdg.util.Sync;
import bthdg.util.Utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * - save open orders/state on stop; read/restore state on start
 * - maintain isExchLive, correctly fold if exch is down to not affect others;
 *   - fold all if no inet connection; restart fine on connectivity
 */
class BiAlgo implements Runner.IAlgo {
    private static final long MOVING_AVERAGE = Utils.toMillis(4, 19); // TODO: make exchange_pair dependent
    private static final long MIN_ITERATION_TIME = 3000;
    private static final double USE_ACCT_FUNDS = 0.95;
    public static final int MAX_TRADES_PER_ITERATION = 1;
    public static final int MAX_LIVE_PER_DIRECTION = 5;
    public static int MIN_ORDER_CAP_RATIO = 1;
    private static double START_LEVEL;
    public static int CANCEL_DEEP_PRICE_INDEX;

    private final Pair PAIR = Pair.BTC_CNH;
    private final Exchange[] ALL_EXCHANGES = new Exchange[] {/*Exchange.BTCN, */Exchange.OKCOIN, Exchange.HUOBI};
    private final Exchange[] TRADE_EXCHANGES = new Exchange[] {/*Exchange.BTCN, */Exchange.OKCOIN, Exchange.HUOBI};

    private long m_startMillis;
    private List<ExchangesPair> m_exchPairs = mkExchPairs();
    private List<ExchangesPairData> m_exchPairsDatas = mkExchPairsDatas();
    MdStorage m_mdStorage = new MdStorage();
    private Runnable m_stopRunnable;
    private int m_maxExchNameLen; // for formatting
    private Map<Exchange, AccountData> m_startAccountMap = new HashMap<Exchange, AccountData>();
    public static Map<Exchange,Map<Currency,Double>> s_startValuate = new HashMap<Exchange,Map<Currency,Double>>();
    private Map<Currency, Double> s_totalStartValuate = new HashMap<Currency, Double>();
    Map<Exchange, AccountData> m_accountMap = new HashMap<Exchange, AccountData>();
    private static final int MAX_PLACE_ORDER_REPEAT = 2;
    private static int s_notEnoughFundsCounter;

    private BalancedMgr m_balancer = new BalancedMgr() {
        protected Pair getPair() { return PAIR; }
        protected AccountData getAccount(Exchange exchange) { return m_accountMap.get(exchange); }
        protected TopData getTop(Exchange exchange, Pair pair) { return m_mdStorage.get(exchange, pair).topData(); }
        protected boolean hasLiveExchOrder(Exchange exch) { return BiAlgo.this.hasLiveExchOrder(exch); }
    };

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }
    @Override public void stop(Runnable runnable) { m_stopRunnable = runnable; }

    static {
        Fetcher.SIMULATE_ORDER_EXECUTION = false;
        Fetcher.LOG_LOADING = true;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;

        Btcn.JOIN_SMALL_QUOTES  = true;
        OkCoin.JOIN_SMALL_QUOTES  = true;
        DeepData.LOG_JOIN_SMALL_QUOTES = true;
    }

    public BiAlgo() {
        Collections.addAll(m_balancer.m_checkBalancedExchanges, ALL_EXCHANGES);
    }

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
            m_startMillis = System.currentTimeMillis();
            int iterationCount = 0;
            while (true) {
                long millis = System.currentTimeMillis();
                log("iteration " + iterationCount + " -------------------------------------------- elapsed: " + Utils.millisToDHMSStr(millis - m_startMillis) + "; date=" + new Date());
                IterationHolder ih = new IterationHolder(iterationCount);
                runIteration(ih);
                sleepIfNeeded(millis);
                iterationCount++;
                if(m_stopRunnable != null) {
                    cancelAll();
                    logBalance();
                    m_stopRunnable.run();
                    break;
                }
            }
            log("runAlgo END " + this);
        } catch (Exception e) {
            err("runAlgo error: " + e, e);
        }
    }

    private void runInit() throws Exception {
        final Properties keys = Config.loadKeys();
        START_LEVEL = Double.parseDouble(keys.getProperty("bialgo.start_level"));
        CANCEL_DEEP_PRICE_INDEX = Integer.parseInt(keys.getProperty("bialgo.cancel_deep_price_index"));
        MIN_ORDER_CAP_RATIO = Integer.parseInt(keys.getProperty("bialgo.cap_ratio"));

        log(" date=" + new Date() +
                "; START_LEVEL=" + Utils.format5(START_LEVEL) +
                "; CANCEL_DEEP_PRICE_INDEX=" + CANCEL_DEEP_PRICE_INDEX +
                "; MIN_ORDER_CAP_RATIO=" + MIN_ORDER_CAP_RATIO);

        final Currency currency1 = PAIR.m_from;
        final Currency currency2 = PAIR.m_to;
        doInParallel("getAccountInit", new Utils.IExchangeRunnable() {
            @Override
            public void run(Exchange exchange) throws Exception {
                exchange.init(keys);
                AccountData accountData = Fetcher.fetchAccount(exchange);
                m_accountMap.put(exchange, accountData);
                m_startAccountMap.put(exchange, accountData.copy());

                TopData topData = Fetcher.fetchTop(exchange, PAIR);
                TopsData tops = new TopsData(PAIR, topData);
                double evaluate1 = accountData.evaluateAll(tops, currency1, exchange);
                putStartValuate(exchange, currency1, evaluate1);
                double evaluate2 = accountData.evaluateAll(tops, currency2, exchange);
                putStartValuate(exchange, currency2, evaluate2);
                log("Start account(" + exchange.m_name + "): " + accountData +
                                "; evaluate" + Utils.capitalize(currency1.m_name) + ": " + Utils.format5(evaluate1) +
                                "; evaluate" + Utils.capitalize(currency2.m_name) + ": " + Utils.format5(evaluate2)
                );
            }
        });
        double e1 = evalAllExchStart(currency1);
        s_totalStartValuate.put(currency1, e1);
        double e2 = evalAllExchStart(currency2);
        s_totalStartValuate.put(currency2, e2);
        log(" ALL: evaluate" + Utils.capitalize(currency1.m_name) + ": " + Utils.format5(e1) +
                "; evaluate" + Utils.capitalize(currency2.m_name) + ": " + Utils.format5(e2)
        );
    }

    private void logBalance() {
        double totalEvaluate1 = 0;
        double totalEvaluate2 = 0;
        double totalSleep1 = 0;
        double totalSleep2 = 0;
        Currency currency1 = PAIR.m_from;
        Currency currency2 = PAIR.m_to;
        for (Exchange exchange : ALL_EXCHANGES) {
            AccountData accountData = m_accountMap.get(exchange);
            MktDataHolder mdh = m_mdStorage.get(exchange, PAIR);
            TopData topData = mdh.topData();
            TopsData tops = new TopsData(PAIR, topData);
            double evaluate1 = accountData.evaluateAll(tops, currency1, exchange);
            double evaluate2 = accountData.evaluateAll(tops, currency2, exchange);
            Map<Currency, Double> startMap = s_startValuate.get(exchange);
            Double start1 = startMap.get(currency1);
            Double start2 = startMap.get(currency2);
            AccountData startAccountData = m_startAccountMap.get(exchange);
            double sleep1 = startAccountData.evaluateAll(tops, currency1, exchange);
            double sleep2 = startAccountData.evaluateAll(tops, currency2, exchange);

            log(" account(" + exchange.m_name + "): " + accountData + ":\n" +
                            "  evaluate" + Utils.capitalize(currency1.m_name) + ": " + Utils.format5(start1) + " -> " + Utils.format5(evaluate1) + "(" + Utils.format5(sleep1) + ")\n" +
                            "  evaluate" + Utils.capitalize(currency2.m_name) + ": " + Utils.format5(start2) + " -> " + Utils.format5(evaluate2) + "(" + Utils.format5(sleep2) + ")"
            );
            totalEvaluate1 += evaluate1;
            totalEvaluate2 += evaluate2;
            totalSleep1 += sleep1;
            totalSleep2 += sleep2;
        }
        Double totalStart1 = s_totalStartValuate.get(currency1);
        Double totalStart2 = s_totalStartValuate.get(currency2);
        log(" ALL: evaluate" + Utils.capitalize(currency1.m_name) + ": " + Utils.format5(totalStart1) + " -> " + Utils.format5(totalEvaluate1) + " (" + Utils.format5(totalSleep1) + ")" +
                "; evaluate" + Utils.capitalize(currency2.m_name) + ": " + Utils.format5(totalStart2) + " -> " + Utils.format5(totalEvaluate2) + " (" + Utils.format5(totalSleep2) + ")"
        );
        double ratio1 = totalEvaluate1 / totalSleep1;
        double ratio2 = totalEvaluate2 / totalSleep2;
        long millis = System.currentTimeMillis();
        long takes = millis - m_startMillis;
        double pow = ((double)Utils.ONE_DAY_IN_MILLIS) / takes;
        double d1 = Math.pow(ratio1, pow);
        double d2 = Math.pow(ratio2, pow);
        log("  ratio" + Utils.capitalize(currency1.m_name) + ": " + Utils.format5(ratio1) + " -> " + Utils.format5(d1) + "/d" +
            "; ratio" + Utils.capitalize(currency2.m_name) + ": " + Utils.format5(ratio2) + " -> " + Utils.format5(d2) + "/d"
        );
    }

    private double evalAllExchStart(Currency currency) {
        double ret = 0;
        for (Exchange exch : ALL_EXCHANGES) {
            Map<Currency, Double> eMap = s_startValuate.get(exch);
            Double ev = eMap.get(currency);
            ret += ev;
        }
        return ret;
    }

    private void putStartValuate(Exchange exchange, Currency currency, double evaluate) {
        Map<Currency, Double> eMap = s_startValuate.get(exchange);
        if (eMap == null) {
            eMap = new HashMap<Currency, Double>();
            s_startValuate.put(exchange, eMap);
        }
        eMap.put(currency, evaluate);
    }

    private void runIteration(final IterationHolder ih) throws Exception {
        runBfrMkt(ih, this); // check open orders state if any

        // load mkt data
        doInParallel("getMktData", new Utils.IExchangeRunnable() {
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


        BalancedMgr.IBalancedHelper helper = new BalancedMgr.IBalancedHelper() {
            @Override public void queryLiveOrders(Exchange exchange, Pair pair, LiveOrdersMgr.ILiveOrdersCallback callback) throws Exception {
                ih.queryLiveOrders(exchange, pair, callback);
            }

            @Override public TopsData getTops(Exchange exchange) throws Exception {
                TopData topData = m_mdStorage.get(exchange, PAIR).topData();
                TopsData tops = new TopsData(PAIR, topData);
                return tops;
            }

            @Override public Map<Currency, Double> getRatioMap() {
                Map<Currency, Double> ratioMap = new HashMap<Currency, Double>();
                ratioMap.put(PAIR.m_from, 0.5);
                ratioMap.put(PAIR.m_to, 0.5);
                return ratioMap;
            }
        };
        m_balancer.сheckBalanced(helper);
//        сheckBalancesIfNeeded(ih);

        logIterationEnd();
    }

    private boolean hasLiveExchOrder(Exchange exch) {
        for (ExchangesPairData epd : m_exchPairsDatas) {
            if (Utils.contains(epd.m_exchanges, exch)) {
                boolean hasLive = epd.hasLiveExchOrder();
                if (hasLive) {
                    return true;
                }
            }
        }
        return false;
    }

    private void cancelAll() {
        for (ExchangesPairData exchPairsData : m_exchPairsDatas) {
            try {
                exchPairsData.cancelAll();
            } catch (Exception e) {
                err("cancelAll() failed: " + exchPairsData + " :" + e, e);
            }
        }
    }

    private List<AtomicBoolean> runBfrMkt(IterationHolder ih, BiAlgo biAlgo) throws InterruptedException {
        syncFundsIfNeeded();

        List<AtomicBoolean> ret = null;
        for (ExchangesPairData exchPairsData : m_exchPairsDatas) {
            try {
                AtomicBoolean stat = exchPairsData.runBfrMkt(ih, biAlgo);
                ret = Sync.addSync(ret, stat);
            } catch (Exception e) {
                err("runBfrMkt failed: " + exchPairsData + " :" + e, e);
            }
        }
        Sync.wait(ret);
        return ret;
    }

    private void syncFundsIfNeeded() throws InterruptedException {
        if (s_notEnoughFundsCounter > 2) {
            log("notEnoughFundsCounter=" + s_notEnoughFundsCounter + ", try sync accounts");
            for (ExchangesPairData exchPairsData : m_exchPairsDatas) {
                OrderData orderData = exchPairsData.getFirstActiveLive();
                if (orderData != null) {
                    log(" got active live - skip sync accounts: " + orderData);
                    return;
                }
            }
            doInParallel("SyncAccount", new Utils.IExchangeRunnable() {
                @Override public void run(Exchange exchange) throws Exception {
                    AccountData current = m_accountMap.get(exchange);
                    log("Sync account(" + exchange.m_name + "): " + current);
                    AccountData accountData = Fetcher.fetchAccount(exchange);
                    m_accountMap.put(exchange, accountData);
                    log("Synced account(" + exchange.m_name + "): " + accountData );
                }
            });
            s_notEnoughFundsCounter = 0;
        }
    }

    private void runForPairs(IterationHolder ih) {
        for (ExchangesPairData exchPairsData : m_exchPairsDatas) {
            try {
                exchPairsData.runForPair(ih);
            } catch (Exception e) {
                err("runForPair failed: " + exchPairsData + " :" + e, e);
            }
        }
    }

    private void logIterationEnd() {
        for (ExchangesPairData exchPairsData : m_exchPairsDatas) {
            exchPairsData.logIterationEnd();
        }
        m_balancer.logIterationEnd();
    }

    private void doInParallel(String name, final Utils.IExchangeRunnable eRunnable) throws InterruptedException {
        Utils.doInParallel(name, ALL_EXCHANGES, eRunnable);
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

    private boolean isTradeExchange(Exchange exch) {
        return Utils.contains(TRADE_EXCHANGES, exch);
    }

    private void sleepIfNeeded(long start) throws InterruptedException {
        long took = System.currentTimeMillis() - start;
        String str = " iteration took " + took + " ms";
        if (took < MIN_ITERATION_TIME) {
            long toSleep = MIN_ITERATION_TIME - took;
            str += ";  to sleep " + toSleep + " ms...";
            Thread.sleep(toSleep);
        }
        log(str);
    }

    public int getMaxExchNameLen() {
        if (m_maxExchNameLen == 0) { // calc once
            for (Exchange exchange : ALL_EXCHANGES) {
                m_maxExchNameLen = Math.max(m_maxExchNameLen, exchange.name().length());
            }
        }
        return m_maxExchNameLen;
    }

    public static OrderData.OrderPlaceStatus placeOrder(AccountData account, OrderData orderData, OrderState state) throws Exception {
        Exchange exchange = account.m_exchange;
        log("placeOrder(exchange=" + exchange + ") " + orderData.toString(exchange));

        OrderData.OrderPlaceStatus ret;
        if (account.allocateOrder(orderData)) {
            ret = placeOrderToExchange(exchange, orderData, state);
            if (ret != OrderData.OrderPlaceStatus.OK) {
                account.releaseOrder(orderData, exchange);
            } else {
                log(" placeOrderToExchange successful: " + exchange + ", " + orderData + ", account: " + account);
            }
        } else {
            log("ERROR: account allocateOrder unsuccessful: " + exchange + ", " + orderData + ", account: " + account);
            ret = OrderData.OrderPlaceStatus.ERROR;
        }
        return ret;
    }

    private static OrderData.OrderPlaceStatus placeOrderToExchange(Exchange exchange, OrderData orderData, OrderState state) throws Exception {
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
                } else {
                    ret = OrderData.OrderPlaceStatus.ERROR;
                }
            }
            return ret;
        }
    }

    private class ExchangesPairData {
        public final ExchangesPair m_exchangesPair;
        private final Exchange[] m_exchanges;
        private final boolean m_doTrade;
        private final Utils.AverageCounter m_diffAverageCounter;
        private final List<BiAlgoData> m_live = new ArrayList<BiAlgoData>();
        private int m_runs;
        private double m_balanceSum;
//        private boolean m_needCheckBalanced = true;

        public String name() { return m_exchangesPair.name(); }

        private ExchangesPairData(ExchangesPair exchangesPair) {
            m_exchangesPair = exchangesPair;
            m_doTrade = isTradeExchange(m_exchangesPair.m_exchange1) && isTradeExchange(m_exchangesPair.m_exchange2);
            m_diffAverageCounter = new Utils.AverageCounter(MOVING_AVERAGE);
            m_exchanges = m_exchangesPair.toArray();
        }

        public void runForPair(IterationHolder ih) throws Exception {
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
            double avgDiff = m_diffAverageCounter.addAtCurrentTime(diff);
            double diffDiff = diff - avgDiff;
            log(name() + " diff=" + e1.roundPriceStr(diff, PAIR) + // TODO: introduce roundPriceStrPlus - to add some precision
                    ", avgDiff=" + e1.roundPriceStr(avgDiff, PAIR) +
                    ", diffDiff=" + e1.roundPriceStr(diffDiff, PAIR) +
                    ", bidAskDiff=" + e1.roundPriceStr(bidAskDiff, PAIR));

            BiPoint mktPoint = new BiPoint(mdPoint1, mdPoint2, avgDiff, diffDiff, bidAskDiff);

            checkLiveForEnd(ih, mktPoint);
            checkForNew(ih, mktPoint);
            checkIfFarFromMarket();
            checkError();
        }

        private void checkError() throws Exception {
            List<BiAlgoData> toRemove = null;
            for (BiAlgoData data : m_live) {
                BiAlgoState state = data.m_state;
                if (state == BiAlgoState.ERROR) {
                    data.log("Error state, need cancel: " + data);
                    boolean cancelled = data.cancel();
                    data.log(" cancelled: " + cancelled);
                    if (cancelled) {
                        if (toRemove == null) {
                            toRemove = new ArrayList<BiAlgoData>();
                        }
                        toRemove.add(data);
                        addCheckBalancedExchanges();
                    } else {
                        log(name() + "  error: not cancelled: " + data);
                    }
                }
            }
            if (toRemove != null) {
                log(name() + "  toRemove: " + toRemove);
                m_live.removeAll(toRemove);
                log(name() + "   remained live: " + m_live);
            }
        }

        private void addCheckBalancedExchanges() {
            m_balancer.addCheckBalancedExchanges(m_exchangesPair.m_exchange1);
            m_balancer.addCheckBalancedExchanges(m_exchangesPair.m_exchange2);
        }

        private void checkIfFarFromMarket() throws Exception {
            List<BiAlgoData> toRemove = null;
            List<BiAlgoData> toAdd = null;
            for (BiAlgoData data : m_live) {
                BiAlgoState state = data.m_state;
                boolean isOpen = (state == BiAlgoState.OPEN_PLACED) || (state == BiAlgoState.SOME_OPEN);
                boolean isClose = (state == BiAlgoState.CLOSE_PLACED) || (state == BiAlgoState.SOME_CLOSE);
                if (isOpen || isClose) {
                    if( checkIfFarFromMarket(data, isOpen) ) {
                        data.log("FarFromMarket: " + data);
                        BiAlgoData split = trySplit(data, isOpen);

                        boolean cancelled = data.cancel();
                        data.log("cancelled: " + cancelled);
                        if (cancelled) {
                            if (toRemove == null) {
                                toRemove = new ArrayList<BiAlgoData>();
                            }
                            toRemove.add(data);
                            data.log(" added to remove: " + data);
                            if(split != null) {
                                if (toAdd == null) {
                                    toAdd = new ArrayList<BiAlgoData>();
                                }
                                toAdd.add(split);
                                data.log(" added to add: " + split);
                            }
                        } else {
                            data.log("error: unable to cancel all orders: " + cancelled);
                        }
                        addCheckBalancedExchanges();
                    }
                }
            }
            if (toRemove != null) {
                log(name() + "  toRemove: " + toRemove);
                m_live.removeAll(toRemove);
                log(name() + "   remained live: " + m_live);
            }
            if (toAdd != null) {
                log(name() + "  toAdd: " + toAdd);
                m_live.addAll(toAdd);
                log(name() + "   gained live: " + m_live);
            }
        }

        private BiAlgoData trySplit(BiAlgoData data, boolean isOpen) {
            OrderDataExchange[] odes = data.getOrderDataExchanges(isOpen);
            double splitAmount = Double.MAX_VALUE;
            for (OrderDataExchange ode : odes) {
                OrderData od = ode.m_orderData;
                OrderStatus status = od.m_status;
                Exchange exchange = ode.m_exchange;
                if ((status != OrderStatus.FILLED) && (status != OrderStatus.PARTIALLY_FILLED)) {
                    log("can not split [" + exchange + "]. orders: " + Arrays.asList(odes));
                    return null; // can not split.
                }
                double filled = od.m_filled;
                double minOrderToCreate = exchange.minOrderToCreate(PAIR);
                if (filled < minOrderToCreate) {
                    log("can not split [" + exchange + "] (filled(" + filled + ") < minOrderToCreate(" + minOrderToCreate + ")). orders: " + Arrays.asList(odes));
                    return null; // can not split.
                }
                splitAmount = Math.min(splitAmount, filled);
            }
            log("can split; splitAmount=" + splitAmount + "; orders: " + Arrays.asList(odes));
            BiAlgoData split = data.split(splitAmount);
            split.logIterationEnd();
            return split;
        }

        private boolean checkIfFarFromMarket(BiAlgoData data, boolean isOpen) {
            return isOpen
                ? checkIfFarFromMarket(data.m_openOde1, data.m_openOde2)
                : checkIfFarFromMarket(data.m_closeOde1, data.m_closeOde2);
        }

        private boolean checkIfFarFromMarket(OrderDataExchange ode1, OrderDataExchange ode2) {
            return checkIfFarFromMarket(ode1) || checkIfFarFromMarket(ode2);
        }

        private boolean checkIfFarFromMarket(OrderDataExchange ode) {
            OrderData od = ode.m_orderData;
            OrderStatus status = od.m_status;
            boolean notDone = (status == OrderStatus.SUBMITTED) || (status == OrderStatus.PARTIALLY_FILLED);
            if (notDone) {
                Exchange exchange = ode.m_exchange;
                double price = od.m_price;
                MktDataHolder mdh = m_mdStorage.get(exchange, PAIR);
                MktDataPoint point = mdh.m_mdPoint;
                TopData topData = point.m_topData;
                if (topData.isOutsideBibAsk(price)) {
                    DeepData deepData = point.m_deeps;
                    List<DeepData.Deep> deeps = od.m_side.getDeeps(deepData);
                    int priceIndex = deepPriceIndex(price, deeps);
                    log("priceIndex=" + priceIndex + ";  on " + ode.m_exchange);
                    if (priceIndex > CANCEL_DEEP_PRICE_INDEX) {
                        log(" priceIndex is farFromMarket: priceIndex=" + priceIndex + "; deeps=" + deeps);
                        return true;
                    }
                }
            }
            return false;
        }

        private int deepPriceIndex(double price, List<DeepData.Deep> deeps) {
            int priceIndex = -1;
            int size = deeps.size();
            for (int i = 0; i < size - 1; i++) {
                DeepData.Deep deep1 = deeps.get(i);
                DeepData.Deep deep2 = deeps.get(i + 1);
                double price1 = deep1.m_price;
                double price2 = deep2.m_price;
                if (((price1 <= price) && (price <= price2)) || ((price1 >= price) && (price >= price2))) {
                    log(m_exchangesPair.name() + " price " + price + " is between deeps " + i + " and " + (i + 1) + ": deep1=" + deep1 + "; deep2=" + deep2);
                    priceIndex = i;
                    break;
                }
            }
            return priceIndex;
        }

        private void checkLiveForEnd(IterationHolder ih, BiPoint mktPoint) throws Exception {
            for (BiAlgoData data : m_live) {
                if (data.m_state == BiAlgoState.OPEN) {
                    checkForEnd(ih, data, mktPoint);
                }
            }
        }

        private void checkForEnd(IterationHolder ih, BiAlgoData data, BiPoint mktPoint) throws Exception {
            double gain = mktPoint.gain();
            double midMid = mktPoint.midMid();
            double level = gain / midMid;
            Exchange e1 = m_exchangesPair.m_exchange1;
            data.log(name() + " E  gain=" + e1.roundPriceStr(gain, PAIR) +
                    ", midMid=" + e1.roundPriceStr(midMid, PAIR) +
                    ", level=" + Utils.format8(level));

            if ((mktPoint.aboveAverage() && data.m_up)
                    || (!mktPoint.aboveAverage() && !data.m_up)) { // look for opposite difDiffs
                if (level > START_LEVEL) {
                    data.log(name() + "   GOT END");
                    onEnd(ih, data, mktPoint);
                }
            }
        }

        private void checkForNew(IterationHolder ih, BiPoint mktPoint) throws Exception {
            boolean up = !mktPoint.aboveAverage();
            int count = findLiveCount(up);
            if (count >= MAX_LIVE_PER_DIRECTION) {
                log("@@ do not start many on the same direction (up=" + up + ") count=" + count);
                return;
            }

            int tradePlaced = ih.getTradePlaced(m_exchangesPair);
            if (tradePlaced >= MAX_TRADES_PER_ITERATION) {
                log("@@ do not start new - MAX_TRADES_PER_ITERATION(" + MAX_TRADES_PER_ITERATION + ") reached, already started = " + tradePlaced);
            }

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

        private int findLiveCount(boolean up) {
            int count = 0;
            for( BiAlgoData live : m_live ) {
                if( live.m_up == up ) {
                    count++;
                }
            }
            return count;
        }

        private void open(IterationHolder ih, BiPoint mktPoint, BiAlgoData baData) throws Exception {
            boolean up = !mktPoint.aboveAverage();
            boolean start = (baData == null);
            log("start=" + start + ";   up=" + up + ";   PAIR=" + PAIR);
            if (baData != null) {
                baData.log("BiAlgoData=" + baData);
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

            Exchange exchange1 = m_exchangesPair.m_exchange1;
            Exchange exchange2 = m_exchangesPair.m_exchange2;
            log("exchange1=" + exchange1 + ";   exchange2=" + exchange2);

            DeepData.Deep deep1 = side1.getDeep(deeps1);
            log("deep1=" + deep1);
            DeepData.Deep deep2 = side2.getDeep(deeps2);
            log("deep2=" + deep2);

            double size1 = deep1.m_size;
            double size2 = deep2.m_size;
            log("size1=" + size1 + " " + baseCurrency + ";   size2=" + size2 + " " + baseCurrency);

            double deepLocked1 = ih.getDeepLocked(exchange1, side1);
            if (deepLocked1 > 0) {
                size1 -= deepLocked1;
                log(" updated size1=" + size1 + "  - corrected according to deep locked=" + deepLocked1);
            }
            double deepLocked2 = ih.getDeepLocked(exchange2, side2);
            if (deepLocked2 > 0) {
                size2 -= deepLocked2;
                log(" updated size2=" + size2 + "  - corrected according to deep locked=" + deepLocked2);
            }

            double minSize = Math.min(size1, size2);
            log("minMktAvailableSize=" + minSize + " " + baseCurrency);

            AccountData account1 = m_accountMap.get(exchange1);
            log("account1=" + account1);
            AccountData account2 = m_accountMap.get(exchange2);
            log("account2=" + account2);

            Double amount = start
                    ? getOpenAmount(ih, up, mktPoint, minSize)
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

                TopData top1 = mdPoint1.m_topData;
                TopData top2 = mdPoint2.m_topData;
                log("top1=" + top1 + ";  top2=" + top2);

                OrderDataExchange ode1 = new OrderDataExchange(orderData1, exchange1, account1);
                OrderDataExchange ode2 = new OrderDataExchange(orderData2, exchange2, account2);

                if (checkTheSamePending(ode1) || checkTheSamePending(ode2)) {
                    log("  got the same live active - do not start OPEN");
                } else {
                    BiAlgoData data;
                    if (start) {
                        data = new BiAlgoData(m_exchangesPair, up, PAIR, mktPoint, amount, ode1, ode2);
                        m_live.add(data);
                        data.placeOpenOrders(ih);
                    } else {
                        baData.placeCloseOrders(ih, mktPoint, ode1, ode2);
                        data = baData;
                    }
                    if (data.m_state == BiAlgoState.ERROR) {
                        log(name() + "   place orders error, canceling: " + data);
                        boolean cancelled = data.cancel();
                        log(name() + "   cancel res: " + cancelled);
                        if (cancelled) {
                            m_live.remove(data);
                            log(name() + "   remained live: " + m_live);
                        }
                        addCheckBalancedExchanges();
                    }
                    ih.addTradePlaced(m_exchangesPair);
                }
            }
        }

        private boolean checkTheSamePending(OrderDataExchange ode) {
            for(BiAlgoData live : m_live) {
                if( live.checkTheSamePending(ode) ) {
                    return true;
                }
            }
            return false;
        }

        private Double getCloseAmount(BiAlgoData baData) {
            // todo: use max(remained) here
            // todo: check against available in account
            return baData.m_amount;
        }

        private Double getOpenAmount(IterationHolder ih, boolean up, BiPoint mktPoint, double minMktSize) {
            Currency currency1 = PAIR.currencyFrom(up);
            Currency currency2 = PAIR.currencyFrom(!up);
            Currency baseCurrency = PAIR.currencyFrom(false);

            Exchange exchange1 = m_exchangesPair.m_exchange1;
            Exchange exchange2 = m_exchangesPair.m_exchange2;

            AccountData account1 = m_accountMap.get(exchange1);
            AccountData account2 = m_accountMap.get(exchange2);

            double available1 = account1.available(currency1);
            double available2 = account2.available(currency2);
            log("available1=" + Utils.format5(available1) + " " + currency1 + ";  available2=" + Utils.format5(available2) + " " + currency2);

            if (currency1 != baseCurrency) {
                MktDataPoint mdPoint1 = mktPoint.m_mdPoint1;
                double mid = mdPoint1.m_topData.getMid();
                available1 = available1 / mid;
                log(" converted available1=" + Utils.format5(available1) + " " + baseCurrency);
            }
            if (currency2 != baseCurrency) {
                MktDataPoint mdPoint2 = mktPoint.m_mdPoint2;
                double mid = mdPoint2.m_topData.getMid();
                available2 = available2 / mid;
                log(" converted available2=" + Utils.format5(available2) + " " + baseCurrency);
            }

            double minAvailable = Math.min(available1, available2);
            log("minAvailable=" + Utils.format5(minAvailable));

            double minOrderToCreate1 = exchange1.minOrderToCreate(PAIR);
            double minOrderToCreate2 = exchange2.minOrderToCreate(PAIR);
            double minOrderToCreate = Math.max(minOrderToCreate1, minOrderToCreate2);
            log("minOrderToCreate1=" + Utils.format5(minOrderToCreate1) +
                    ";   minOrderToCreate2=" + Utils.format5(minOrderToCreate2) +
                    "; max=" + Utils.format5(minOrderToCreate));

            double mktSize = minMktSize;
            if (minMktSize < minOrderToCreate) {
                log(" mktSize adjusted from minMktSize=" + Utils.format5(minMktSize) + " to minOrderToCreate=" + Utils.format5(minOrderToCreate));
                mktSize = minOrderToCreate;
            }

            double amount = Math.min(mktSize, minAvailable * USE_ACCT_FUNDS);
            log("amount for order=" + Utils.format5(amount) + " " + baseCurrency);

            double capValue = Math.max(minOrderToCreate1, minOrderToCreate2) * MIN_ORDER_CAP_RATIO;
            if(amount > capValue) {
                amount = capValue;
                log("amount CAPPED to=" + Utils.format5(capValue));
            }

            if (amount >= minOrderToCreate1) {
                if (amount >= minOrderToCreate2) {

//                    double lockAmount1 = amount;
//                    log(" lockAmount1=" + Utils.format5(lockAmount1) + " " + baseCurrency);
//                    if (currency1 != baseCurrency) {
//                        MktDataPoint mdPoint1 = mktPoint.m_mdPoint1;
//                        double mid = mdPoint1.m_topData.getMid();
//                        lockAmount1 = lockAmount1 * mid;
//                        log("  converted lockAmount1=" + Utils.format5(lockAmount1) + " " + currency1);
//                    }
//
//                    double lockAmount2 = amount;
//                    log(" lockAmount2=" + Utils.format5(lockAmount2) + " " + baseCurrency);
//                    if (currency2 != baseCurrency) {
//                        MktDataPoint mdPoint2 = mktPoint.m_mdPoint2;
//                        double mid = mdPoint2.m_topData.getMid();
//                        lockAmount2 = lockAmount2 * mid;
//                        log("  converted lockAmount2=" + Utils.format5(lockAmount2) + " " + currency2);
//                    }
//
//                    if (ih.lockAmount(account1, lockAmount1, currency1)) {
//                        if (ih.lockAmount(account2, lockAmount2, currency2)) {

                            return amount;

//                        } else {
//                            log("unable to lock " + lockAmount2 + " " + currency2 + " on " + account2 + ", locked=" + ih.getLocked(exchange2, currency2));
//                            boolean unlocked = ih.unlockAmount(account1, lockAmount1, currency1);
//                            if (!unlocked) {
//                                log("ERROR: unable to unlock " + lockAmount1 + " " + currency1 + " on " + account1 + ", locked=" + ih.getLocked(exchange1, currency1));
//                            }
//                        }
//                    } else {
//                        log("unable to lock " + lockAmount1 + " " + currency1 + " on " + account1 + ", locked=" + ih.getLocked(exchange1, currency1));
//                    }
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
            BiPoint start = data.m_openPoint;
            boolean endUp = !end.aboveAverage();
            double balance1 = startUp
                    ? -start.m_mdPoint1.m_topData.m_ask + end.m_mdPoint1.m_topData.m_bid
                    :  start.m_mdPoint1.m_topData.m_bid - end.m_mdPoint1.m_topData.m_ask;
            double balance2 = startUp
                    ?  start.m_mdPoint2.m_topData.m_bid - end.m_mdPoint2.m_topData.m_ask
                    : -start.m_mdPoint2.m_topData.m_ask + end.m_mdPoint2.m_topData.m_bid;
            double balance = balance1 + balance2;

            data.log(name() + "%%%%%%%%%");
            data.log(name() + "%%%%%% START up=" + startUp + "; top1=" + start.m_mdPoint1.m_topData + "; top2=" + start.m_mdPoint2.m_topData);
            data.log(name() + "%%%%%% END   up=" + endUp + "; top1=" + end.m_mdPoint1.m_topData + "; top2=" + end.m_mdPoint2.m_topData);
            data.log(name() + "%%%%%%");
            data.log(name() + "%%%%%% START buy " + (startUp ? "1" : "2") + " @ " + (startUp ? start.m_mdPoint1.m_topData : start.m_mdPoint2.m_topData).askStr() +
                    "; sell " + (startUp ? "2" : "1") + " @ " + (startUp ? start.m_mdPoint2.m_topData : start.m_mdPoint1.m_topData).bidStr());
            data.log(name() + "%%%%%% END   buy " + (startUp ? "2" : "1") + " @ " + (startUp ? end.m_mdPoint2.m_topData : end.m_mdPoint1.m_topData).askStr() +
                    "; sell " + (startUp ? "1" : "2") + " @ " + (startUp ? end.m_mdPoint1.m_topData : end.m_mdPoint2.m_topData).bidStr());
            data.log(name() + "%%%%%%");
            data.log(name() + "%%%%%% balance1=" + balance1 + "; balance2=" + balance2 + "; balance=" + balance);
            data.log(name() + "%%%%%%");

            open(ih, end, data);
            log("***********************************************");
        }

        public AtomicBoolean runBfrMkt(final IterationHolder ih, final BiAlgo biAlgo) throws Exception {
            List<AtomicBoolean> ret = null;
            for (BiAlgoData data : m_live) {
                data.log(name() + " live: " + data);
                // need to check order state
                AtomicBoolean sync = data.checkLiveOrderState(ih, biAlgo);
                ret = Sync.addSync(ret, sync);
            }
            AtomicBoolean out = null;
            if (ret != null) {
                out = removeFinished(ret); // remove from live if finished
            }
            return out;
        }

        private AtomicBoolean removeFinished(List<AtomicBoolean> ret) {
            final AtomicBoolean finalOut = new AtomicBoolean(false);
            Sync.waitInThreadIfNeeded(ret, new Runnable() {
                @Override public void run() {
                    List<BiAlgoData> toRemove = null;
                    for (BiAlgoData data : m_live) {
                        BiAlgoState state = data.m_state;
                        BiAlgoData candidate = null;
                        if (state == BiAlgoState.CLOSE) {
                            m_runs++;
                            data.log(name() + " reached CLOSE - to remove from live: " + data);
                            candidate = data;
                            logTradeBalance(data);
                        } else if (state == BiAlgoState.CANCEL) {
                            data.log(name() + " reached CANCEL - to remove from live: " + data);
                            candidate = data;
                        }
                        if (candidate != null) {
                            if (toRemove == null) {
                                toRemove = new ArrayList<BiAlgoData>();
                            }
                            toRemove.add(data);
                        }
                    }
                    if (toRemove != null) {
                        log(name() + "  toRemove: " + toRemove);
                        m_live.removeAll(toRemove);
                        log(name() + "   remained live: " + m_live);
                    }
                    Sync.setAndNotify(finalOut);
                }
            });
            return finalOut;
        }

        private void logTradeBalance(BiAlgoData data) {
            double balance1o = data.m_openOde1.getBalance();
            double balance1c = data.m_closeOde1.getBalance();
            double balance1 = balance1o + balance1c;
            double balance2o = data.m_openOde2.getBalance();
            double balance2c = data.m_closeOde2.getBalance();
            double balance2 = balance2o + balance2c;
            double balance = balance1 + balance2;
            m_balanceSum += balance;
            data.log(name() +
                    " runs=" + m_runs +
                    "; balance1=" + balance1o + "+" + balance1c + "=" + balance1 +
                    "; balance2=" + balance2o + "+" + balance2c + "=" + balance2 +
                    "; total balance=" + balance + "; balance sum=" + m_balanceSum
            );
            BiAlgo.this.logBalance();
        }

        public void logIterationEnd() {
            if(!m_live.isEmpty()) {
                log(name() + " logIterationEnd: remained live: " + m_live);
                for(BiAlgoData live : m_live) {
                    live.logIterationEnd();
                }
            }
        }

        public void cancelAll() throws Exception {
            log("cancelAll() on " + name());
            List<BiAlgoData> cancelled = null;
            for (BiAlgoData data : m_live) {
                BiAlgoState state = data.m_state;
                boolean someOpen = (state == BiAlgoState.SOME_OPEN);
                if (someOpen || (state == BiAlgoState.SOME_CLOSE)) {
                    log(" cancelAll() for " + data);
                    boolean ok = data.cancel();
                    if (ok) {
                        if (cancelled == null) {
                            cancelled = new ArrayList<BiAlgoData>();
                        }
                        cancelled.add(data);
                    }
                }
            }
            if (cancelled != null) {
                m_live.removeAll(cancelled);
                if (!m_live.isEmpty()) {
                    log(name() + "   ERROR: after cancelAll remained live: " + m_live);
                }
            }
            m_balancer.cancelAll();
        }

        public OrderData getFirstActiveLive() {
            for(BiAlgoData live : m_live) {
                return live.getFirstActiveLive();
            }
            return null;
        }

        public boolean hasLiveExchOrder() {
// todo: for now allow only in no live pairs, later count total orders qty and check with acct
            return !m_live.isEmpty();

//            for(BiAlgoData live : m_live) {
//                if( live.hasLiveExchOrder() ) {
//                    return true;
//                }
//            }
//            return false;
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
        final long m_time;

        public boolean aboveAverage() { return (m_diffDiff > 0); }

        public BiPoint(MktDataPoint mdPoint1, MktDataPoint mdPoint2, double avgDiff, double diffDiff, double bidAskDiff) {
            m_time = System.currentTimeMillis();
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
}
