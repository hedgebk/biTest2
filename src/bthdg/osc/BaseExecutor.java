package bthdg.osc;

import bthdg.Fetcher;
import bthdg.IIterationContext;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.util.Utils;
import bthdg.ws.ITopListener;
import bthdg.ws.IWs;

import java.awt.*;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.List;

public abstract class BaseExecutor implements Runnable {
    public static final int TIMER_SLEEP_TIME = 2000;
    public static final int DEEP_MKT_PIP_RATIO = 1;
    public static boolean DO_TRADE = true;
    public static final int STATE_NO_CHANGE = 0;
    public static final int STATE_NONE = 1;
    public static final int STATE_ORDER = 2;
    public static final int STATE_ERROR = 3;
    public static final int MAX_TICK_AGE_FOR_ORDER = 1500;
    private static final double TOO_FAR_TICK_DISTANCE = 1.2;

    protected static int FLAG_CANCELED  = 1 << 0;
    protected static int FLAG_NEED_CHECK_LIVE_ORDERS = 1 << 1;
    protected static int FLAG_NEED_RECHECK_DIRECTION = 1 << 2;

    protected final long m_startMillis;
    private final IWs m_ws;
    protected final Pair m_pair;
    protected final Exchange m_exchange;
    protected TaskQueueProcessor m_taskQueueProcessor;
    private boolean m_run = true;
    public boolean m_initialized;
    private boolean m_changed;
    protected TopsData m_initTops;
    protected TopsData m_topsData;
    protected AccountData m_initAccount;
    public AccountData m_account;
    public double m_buy;
    public double m_sell;
    private TopSource m_topSource;
    private long m_topMillis; // bud/ask timestamp
    private long m_tradeMillis; // last trade timestamp
    protected boolean m_maySyncAccount = false;
    protected int m_orderPlaceAttemptCounter;
    protected long m_lastProcessDirectionTime;
    public boolean m_feeding;
    protected OrderPriceMode m_orderPriceMode = OrderPriceMode.PEG_TO_MKT;
    final Utils.DoubleDoubleAverageCalculator m_cancelOrderTakesCalc = new Utils.DoubleDoubleAverageCalculator();
    final Utils.DoubleDoubleAverageCalculator m_initAccountTakesCalc = new Utils.DoubleDoubleAverageCalculator();
    final Utils.DoubleDoubleAverageCalculator m_liveOrdersTakesCalc = new Utils.DoubleDoubleAverageCalculator();
    final Utils.DoubleDoubleAverageCalculator m_placeOrderTakesCalc = new Utils.DoubleDoubleAverageCalculator();
    final Utils.DoubleDoubleAverageCalculator m_topTakesCalc = new Utils.DoubleDoubleAverageCalculator();
    public final Utils.DoubleDoubleAverageCalculator m_tickAgeCalc = new Utils.DoubleDoubleAverageCalculator();
    public final LinkedList<TimeFramePoint> m_timeFramePoints = new LinkedList<TimeFramePoint>();
    public LinkedList<TopDataPoint> m_tops = new LinkedList<TopDataPoint>();
    public final Utils.AverageCounter m_buyAvgCounter;
    public final Utils.AverageCounter m_sellAvgCounter;
    private double m_lastTopBid;
    private double m_lastTopAsk;
    private Thread m_timer;

    protected static void log(String s) { Log.log(s); }
    public String dumpWaitTime() { return (m_taskQueueProcessor == null) ? "" : m_taskQueueProcessor.dumpWaitTime(); }

    // abstract
    protected abstract void gotTop() throws Exception;
    protected abstract void gotTrade(TradeDataLight tradeData) throws Exception;
    protected abstract List<String> cancelAllOrders() throws Exception;
    protected abstract void recheckDirection() throws Exception;
    protected abstract List<OrderData> getAllOrders();
    protected abstract IIterationContext.BaseIterationContext checkLiveOrders() throws Exception;
    protected abstract double getDirectionAdjusted();
    protected abstract double checkAgainstExistingOrders(OrderSide needOrderSide, double orderSize);
    protected abstract int cancelOrderIfPresent() throws Exception;
    protected abstract double minOrderSizeToCreate();
    protected abstract boolean cancelOtherOrdersIfNeeded(OrderSide needOrderSide, double notEnough) throws Exception;
    protected abstract void onOrderPlace(OrderData placeOrder, long tickAge, double buy, double sell, TopSource topSource);
    protected abstract boolean checkNoOpenOrders();
    protected abstract long minOrderLiveTime();
    protected abstract double outOfMarketThreshold();
    protected abstract boolean hasOrdersWithMatchedPrice(double tradePrice);
    protected abstract void checkOrdersOutOfMarket() throws Exception;
    protected abstract int checkOrdersState(IIterationContext.BaseIterationContext iContext) throws Exception;
    protected abstract boolean haveNotFilledOrder();
    protected abstract void processTopInt() throws Exception;
    protected abstract double useFundsFromAvailable();

    protected double getAvgOsc() { return 1; }
    protected double maxOrderSizeToCreate() { return 1000000; }

    public BaseExecutor(IWs ws, Pair pair, long barSizeMillis) {
        m_ws = ws;
        m_pair = pair;
        m_exchange = m_ws.exchange();
        m_startMillis = System.currentTimeMillis();
        m_buyAvgCounter = new Utils.FadingAverageCounter(barSizeMillis);
        m_sellAvgCounter = new Utils.FadingAverageCounter(barSizeMillis);
    }

    @Override public void run() {
        while (m_run) {
            try {
                boolean changed;
                synchronized (this) {
                    if (!m_changed) {
                        log("Executor: waiting for update");
                        wait();
                    }
                    changed = m_changed;
                    m_changed = false;
                }
                if (changed) {
                    log("Executor: process update");
                    postRecheckDirection();
                }
            } catch (Exception e) {
                log("error in OscExecutor");
                e.printStackTrace();
            }
        }
    }

    public void init() {
        if (!m_initialized && DO_TRADE) {
            log("not initialized - added InitTask to queue");
            addTask(new InitTask());
        }
    }

    protected void update() {
        synchronized (this) {
            m_changed = true;
            notify();
        }
    }

    public void stop() throws Exception {
        addTask(new StopTaskTask());
        m_ws.stop();
        synchronized (this) {
            m_run = false;
            notify();
        }
    }

    protected TaskQueueProcessor getTaskQueueProcessor() {
        if (m_taskQueueProcessor == null) {
            m_taskQueueProcessor = createTaskQueueProcessor();
        }
        return m_taskQueueProcessor;
    }

    protected TaskQueueProcessor createTaskQueueProcessor() { return new TaskQueueProcessor(); }

    public void onTrade(TradeDataLight tData) {
        addTask(new TradeTask(tData));
    }

    protected void addTask(TaskQueueProcessor.BaseOrderTask task) {
        getTaskQueueProcessor().addTask(task);
    }

    protected void addTaskFirst(TaskQueueProcessor.BaseOrderTask task) {
        getTaskQueueProcessor().addTaskFirst(task);
    }

    protected void initImpl() throws Exception {
        initialize();

        log("initImpl() continue: subscribeTop()");
        m_ws.subscribeTop(m_pair, new ITopListener() {
            @Override public void onTop(long timestamp, double buy, double sell) {
                onTopInt(timestamp, buy, sell, TopSource.top_subscribe);
            }
        });

        m_timer = new Thread("timer") {
            @Override public void run() {
                log("timer thread started");
                while(m_run) {
                    try {
                        sleep(TIMER_SLEEP_TIME);
                    } catch (InterruptedException e) {
                        log("timer thread interrupted: " + e);
                    }
                    postRecheckDirection();
                }
                log("timer thread finished");
            }
        };
        m_timer.start();
    }

    protected void initialize() throws Exception {
        m_topsData = Fetcher.fetchTops(m_exchange, m_pair);
        log(" topsData=" + m_topsData);

        initAccount();
        m_initAccount = m_account.copy();
        m_initTops = m_topsData.copy();
        m_initialized = true;
    }

    protected void onTopInt(long timestamp, double buy, double sell, TopSource topSource) {
//                    log("onTop() timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
        if (buy > sell) {
            log("ERROR: ignored invalid top data. buy > sell: timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
            return;
        }
        m_buy = buy;
        m_sell = sell;
        double avgBuy = m_buyAvgCounter.addAtCurrentTime(buy);
        double avgSell = m_sellAvgCounter.addAtCurrentTime(sell);

        m_topSource = topSource;
        m_topMillis = System.currentTimeMillis();

        TopData topData = new TopData(buy, sell);
        TopDataPoint topDataPoint = new TopDataPoint(topData, timestamp, avgBuy, avgSell);
        addTopDataPoint(topDataPoint);
        m_topsData.put(m_pair, topData);
        log(" topsData'=" + m_topsData);

        addTask(new TopTask());
    }

    protected void initAccount() throws Exception {
        AccountData oldAccount = m_account;
        long start = System.currentTimeMillis();
        TimeFramePoint timeFramePoint = addTimeFrame(TimeFrameType.account, start);
        AccountData newAccount = Fetcher.fetchAccount(m_exchange);
        long end = System.currentTimeMillis();
        timeFramePoint.m_end = end;
        if (newAccount != null) {
            m_account = newAccount;
            long takes = end - start;
            m_initAccountTakesCalc.addValue((double) takes);
            log(" account loaded in " + Utils.millisToDHMSStr(takes) + ":" + m_account);
            double valuateBtc = m_account.evaluateAll(m_topsData, Currency.BTC, m_exchange);
            log("  valuateBtc=" + valuateBtc + " BTC");
            if (oldAccount != null) {
                oldAccount.compareFunds(m_account);
            }
            m_maySyncAccount = false;
        } else {
            log("account request error");
        }
    }

    protected IIterationContext.BaseIterationContext getLiveOrdersContextIfNeeded(IIterationContext.BaseIterationContext iContext) throws Exception {
        return (iContext == null) ? getLiveOrdersContext() : iContext;
    }

    protected IIterationContext.BaseIterationContext getLiveOrdersContext() throws Exception {
        long start = System.currentTimeMillis();
        TimeFramePoint timeFramePoint = addTimeFrame(TimeFrameType.orders, start);
        final OrdersData ordersData = Fetcher.fetchOrders(m_exchange, m_pair);
        long end = System.currentTimeMillis();
        timeFramePoint.m_end = end;
        long takes = end - start;
        m_liveOrdersTakesCalc.addValue((double) takes);
        log(" liveOrders loaded in " + Utils.millisToDHMSStr(takes) + " :" + ordersData);
        return new IIterationContext.BaseIterationContext() {
            @Override
            public OrdersData getLiveOrders(Exchange exchange) throws Exception {
                return ordersData;
            }
        };
    }

    protected static void linkOfflineOrder(OrderData order, OrdersData liveOrders) {
        if ((order != null) && (order.m_orderId == null)) {
            log("we have offline order - try to match " + order);
            for (OrdersData.OrdData nextOrder : liveOrders.m_ords.values()) {
                if (nextOrder.m_orderAmount == order.m_amount) {
                    log("  found liveOrder with matched amount: " + nextOrder);
                    order.m_orderId = nextOrder.m_orderId;
                    break;
                }
            }
        }
    }

    protected String cancelOrder(OrderData order) throws Exception {
        log("cancelOrder() " + order);
        long start = System.currentTimeMillis();
        TimeFramePoint timeFramePoint = addTimeFrame(TimeFrameType.cancel, start);
        String error = m_account.cancelOrder(order);
        long end = System.currentTimeMillis();
        timeFramePoint.m_end = end;
        long takes = end - start;
        m_cancelOrderTakesCalc.addValue((double) takes);
        log(" order cancelled in " + Utils.millisToDHMSStr(takes) + ": error=" + error);
        m_maySyncAccount = true; // even successfully cancelled order can be concurrently partially filled - resync account when possible
        return error;
    }

    public void onError() throws Exception {
        log("onError() resetting...  -------------------------- ");
        cancelOrdersAndReset();
        log("onError() end");
    }

    public void cancelOrdersAndReset() throws Exception {
        IIterationContext.BaseIterationContext iContext = checkLiveOrders();
        iContext = getLiveOrdersContextIfNeeded(iContext);
        OrdersData liveOrders = iContext.getLiveOrders(m_exchange);
        if (liveOrders != null) {
            log(" liveOrders " + liveOrders);
            // we may have offline order, try to link
            List<OrderData> allOrders = getAllOrders();
            if (allOrders != null) {
                for (OrderData order : allOrders) {
                    linkOfflineOrder(order, liveOrders);
                }
            }
        }
        List<String> cancelledOrdIds = cancelAllOrders();
        cancelOfflineOrders(liveOrders, cancelledOrdIds);
        if (m_initialized) {
            initAccount();
        } else {
            initialize();
        }
    }

    private void cancelOfflineOrders(OrdersData liveOrders, List<String> cancelledOrdIds) {
        if (liveOrders.m_ords != null) {
            log("cancelOfflineOrders() liveOrders ids:" + liveOrders.m_ords.keySet());
            for (OrdersData.OrdData nextOrder : liveOrders.m_ords.values()) {
                String orderId = nextOrder.m_orderId;
                if ((cancelledOrdIds != null) && cancelledOrdIds.contains(orderId)) {
                    log(" orderId=" + orderId + " is registered as cancelled");
                    continue;
                }
                OrderData order = nextOrder.toOrderData();
                log(" cancelling live order: " + order);
                try {
//  NPE can be inside - order side can be null here since loaded from live orders -
//  TODO: guess order side from order price and bid/ask prices
                    String error = cancelOrder(order);
                    if (error != null) {
                        log("  error cancelling live order: " + error);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected void logValuate() {
        // Currencies hardcoded - play from Pair m_pair
        log("{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{");
        double valuateBtcInit = m_initAccount.evaluateAll(m_initTops, Currency.BTC, m_exchange);
        double valuateCnhInit = m_initAccount.evaluateAll(m_initTops, Currency.CNH, m_exchange);
        log("  INIT:  valuateBtc=" + valuateBtcInit + " BTC; valuateCnh=" + valuateCnhInit + " CNH");
        double valuateBtcNow = m_account.evaluateAll(m_topsData, Currency.BTC, m_exchange);
        double valuateCnhNow = m_account.evaluateAll(m_topsData, Currency.CNH, m_exchange);
        log("  NOW:   valuateBtc=" + valuateBtcNow + " BTC; valuateCnh=" + valuateCnhNow + " CNH");
        double valuateBtcSleep = m_initAccount.evaluateAll(m_topsData, Currency.BTC, m_exchange);
        double valuateCnhSleep = m_initAccount.evaluateAll(m_topsData, Currency.CNH, m_exchange);
        log("  SLEEP: valuateBtc=" + valuateBtcSleep + " BTC; valuateCnh=" + valuateCnhSleep + " CNH");
        double gainBtc = valuateBtcNow / valuateBtcInit;
        double gainCnh = valuateCnhNow / valuateCnhInit;
        double gainAvg = (gainBtc + gainCnh) / 2;
        long takesMillis = System.currentTimeMillis() - m_startMillis;
        double pow = ((double) Utils.ONE_DAY_IN_MILLIS) / takesMillis;
        double projected = Math.pow(gainAvg, pow);
        log("  GAIN: Btc=" + gainBtc + "; Cnh=" + gainCnh + " CNH; avg=" + gainAvg + "; projected=" + projected + "; takes: " + Utils.millisToDHMSStr(takesMillis));
        log("}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}");
    }

    protected double getGainAvg() {
        double valuateBtcInit = m_initAccount.evaluateAll(m_initTops, Currency.BTC, m_exchange);
        double valuateCnhInit = m_initAccount.evaluateAll(m_initTops, Currency.CNH, m_exchange);
        double valuateBtcNow = m_account.evaluateAll(m_topsData, Currency.BTC, m_exchange);
        double valuateCnhNow = m_account.evaluateAll(m_topsData, Currency.CNH, m_exchange);
        double gainBtc = valuateBtcNow / valuateBtcInit;
        double gainCnh = valuateCnhNow / valuateCnhInit;
        double gainAvg = (gainBtc + gainCnh) / 2;
        return gainAvg;
    }

    public String valuate() {
        if (m_initialized) {
            double valuateBtcInit = m_initAccount.evaluateAll(m_initTops, Currency.BTC, m_exchange);
            double valuateCnhInit = m_initAccount.evaluateAll(m_initTops, Currency.CNH, m_exchange);
            double valuateBtcNow = m_account.evaluateAll(m_topsData, Currency.BTC, m_exchange);
            double valuateCnhNow = m_account.evaluateAll(m_topsData, Currency.CNH, m_exchange);
            double gainBtc = valuateBtcNow / valuateBtcInit;
            double gainCnh = valuateCnhNow / valuateCnhInit;
            double gainAvg = (gainBtc + gainCnh) / 2;
            long takesMillis = System.currentTimeMillis() - m_startMillis;
            double pow = ((double) Utils.ONE_DAY_IN_MILLIS) / takesMillis;
            double projected = Math.pow(gainAvg, pow);
            return "GAIN: Btc=" + Utils.format5(gainBtc) + "; Cnh=" + Utils.format5(gainCnh) + " CNH; avg=" + Utils.format5(gainAvg)
                    + "; projected=" + Utils.format5(projected) + "; takes: " + Utils.millisToDHMSStr(takesMillis);
        }
        return "---";
    }

    protected double adjustSizeToAvailable(double needBuyBtc) {
        log(" account=" + m_account);
        double haveBtc = m_account.available(Currency.BTC);
        double haveCnh = m_account.available(Currency.CNH);
        log(" haveBtc=" + Utils.format8(haveBtc) + "; haveCnh=" + Utils.format8(haveCnh));
        double buyBtc;
        if (needBuyBtc > 0) {
            log("  will buy Btc:");
            double needSellCnh = m_topsData.convert(Currency.BTC, Currency.CNH, needBuyBtc, m_exchange);
            double canSellCnh = Math.min(needSellCnh, haveCnh * useFundsFromAvailable());
            double canBuyBtc = m_topsData.convert(Currency.CNH, Currency.BTC, canSellCnh, m_exchange);
            log("   need to sell " + Utils.format8(needSellCnh) + " CNH; can Sell " + Utils.format8(canSellCnh) + " CNH; this will buy " + Utils.format8(canBuyBtc) + " BTC");
            buyBtc = canBuyBtc;
        } else if (needBuyBtc < 0) {
            log("  will sell Btc:");
            double needSellBtc = -needBuyBtc;
            double canSellBtc = Math.min(needSellBtc, haveBtc * useFundsFromAvailable());
            double canBuyCnh = m_topsData.convert(Currency.BTC, Currency.CNH, canSellBtc, m_exchange);
            log("   need to sell " + Utils.format8(needSellBtc) + " BTC; can Sell " + Utils.format8(canSellBtc) + " BTC; this will buy " + Utils.format8(canBuyCnh) + " CNH");
            buyBtc = -canSellBtc;
        } else {
            log("  do not buy/sell anything");
            buyBtc = 0;
        }
        return buyBtc;
    }

    public void postRecheckDirection() {
        if (DO_TRADE) {
            log(" posting RecheckDirectionTask");
            addTask(new RecheckDirectionTask());
        }
    }

    public int processDirection() throws Exception {
        m_lastProcessDirectionTime = System.currentTimeMillis();
        double directionAdjusted = getDirectionAdjusted(); // [-1 ... 1]

        if ((m_buy == 0) || (m_sell == 0)) {
            log("  directionAdjusted=" + directionAdjusted + ". but no bid/ask defined: " + m_buy + "/" + m_sell);
            return STATE_NO_CHANGE;
        }

        double valuateBtc = m_account.evaluateAll(m_topsData, Currency.BTC, m_exchange);
        double valuateCnh = m_account.evaluateAll(m_topsData, Currency.CNH, m_exchange);
        log("  valuateBtc=" + valuateBtc + " BTC; valuateCnh=" + valuateCnh + " CNH");

        double haveBtc = m_account.getAllValue(Currency.BTC);
        double haveCnh = m_account.getAllValue(Currency.CNH);
        log("  haveBtc=" + Utils.format8(haveBtc) + " BTC; haveCnh=" + Utils.format8(haveCnh) + " CNH; on account=" + m_account);

        double needBtc = (1 + directionAdjusted) / 2 * valuateBtc;
        double needCnh = (1 - directionAdjusted) / 2 * valuateCnh;
        log("  needBtc=" + Utils.format8(needBtc) + " BTC; needCnh=" + Utils.format8(needCnh) + " CNH");

        double needBuyBtc = needBtc - haveBtc;
        double needSellCnh = haveCnh - needCnh;
        log("  directionAdjusted=" + directionAdjusted + "; needBuyBtc=" + Utils.format8(needBuyBtc) + "; needSellCnh=" + Utils.format8(needSellCnh));

        double orderSize = Math.abs(needBuyBtc);
        OrderSide needOrderSide = (needBuyBtc == 0) ? null : (needBuyBtc > 0) ? OrderSide.BUY : OrderSide.SELL;
        log("   needOrderSide=" + needOrderSide + "; orderSize=" + orderSize);
        double exchMinOrderToCreate = m_exchange.minOrderToCreate(m_pair);
        double minOrderSizeToCreate = minOrderSizeToCreate();
        if ((orderSize < exchMinOrderToCreate) || (orderSize < minOrderSizeToCreate)) {
            log("small orderSize. needOrderSide=" + needOrderSide +
                    "; exchMinOrderToCreate=" + exchMinOrderToCreate + "; minOrderSizeToCreate=" + minOrderSizeToCreate);
            log(" seems funds balance is ok. cancelOrderIfPresent...");

            int ret = cancelOrderIfPresent();
            if (ret == STATE_NO_CHANGE) { // cancel attempt was not performed
                if (m_maySyncAccount) {
                    log("no orders - we may re-check account");
                    initAccount();
                }
            }
            return ret;
        }

        if (orderSize != 0) {
            double orderSizeAdjusted = orderSize;
            if (orderSizeAdjusted > 0) {
                orderSizeAdjusted = (needOrderSide == OrderSide.BUY) ? orderSizeAdjusted : -orderSizeAdjusted;
                log("     signed orderSizeAdjusted=" + Utils.format8(orderSizeAdjusted));

                orderSize = checkAgainstExistingOrders(needOrderSide, orderSize);
                if (orderSize == 0) { // existingOrder(s) OK - no need to changes
                    return STATE_NO_CHANGE;
                }
            }
        }

        int ret = cancelOrderIfPresent();
        if (ret == STATE_NO_CHANGE) { // cancel attempt was not performed

            double canBuyBtc = adjustSizeToAvailable(needBuyBtc);
            log("      needBuyBtc " + Utils.format8(needBuyBtc) + " adjusted by Available: canBuyBtc=" + Utils.format8(canBuyBtc));
            double notEnough = Math.abs(needBuyBtc - canBuyBtc);
            if (notEnough > minOrderSizeToCreate) {
                boolean cancelPerformed = cancelOtherOrdersIfNeeded(needOrderSide, notEnough);
                if (cancelPerformed) {
                    postRecheckDirection();
                    return STATE_NONE;
                }
            }

            double diff = m_sell - m_buy;
            double avgDiff = m_sellAvgCounter.get() - m_buyAvgCounter.get();
            if (diff > avgDiff) {
                log("      bidAskDiff=" + diff + "; avgDiff bidAskDiff=" + avgDiff + " => HALVING order size");
            }

            double orderSizeRound = m_exchange.roundAmount(canBuyBtc, m_pair);
            double placeOrderSize = Math.abs(orderSizeRound);
            log("        orderSizeAdjusted=" + Utils.format8(canBuyBtc) + "; orderSizeRound=" + orderSizeRound + "; placeOrderSize=" + Utils.format8(placeOrderSize));

            double maxOrderSizeToCreate = maxOrderSizeToCreate();
            if (placeOrderSize > maxOrderSizeToCreate) {
                placeOrderSize = maxOrderSizeToCreate;
                log("      placeOrderSize=" + placeOrderSize + "; maxOrderSizeToCreate=" + maxOrderSizeToCreate + " => CAPPED");
            }

            if ((placeOrderSize >= exchMinOrderToCreate) && (placeOrderSize >= minOrderSizeToCreate)) {
                if (checkNoOpenOrders()) {
                    long tickAge = System.currentTimeMillis() - m_topMillis;
                    m_tickAgeCalc.addValue((double) tickAge);
                    double averageTickAge = m_tickAgeCalc.getAverage();
                    log("   tickAge=" + tickAge + "; averageTickAge=" + averageTickAge);
                    if (tickAge > MAX_TICK_AGE_FOR_ORDER) {
                        boolean freshTopLoaded = onTooOldTick(tickAge);
                        if (freshTopLoaded) {
                            return STATE_NO_CHANGE;
                        }
                        log("unable to load fresh top - using OLD");
                    }

                    double orderPrice = calcOrderPrice(m_exchange, directionAdjusted, needOrderSide);
                    OrderData placeOrder = new OrderData(m_pair, needOrderSide, orderPrice, placeOrderSize);
                    log("   place orderData=" + placeOrder);

                    double buy = m_buy;
                    double sell = m_sell;
                    TopSource topSource = m_topSource;
                    int rett = placeOrderToExchange(placeOrder);
                    if (rett == STATE_ORDER) {
                        onOrderPlace(placeOrder, tickAge, buy, sell, topSource); // notify on order place
                    }
                    return rett;
                } else {
                    return STATE_ERROR;
                }
            } else {
                log("warning: small order to create: placeOrderSize=" + placeOrderSize +
                        "; exchMinOrderToCreate=" + exchMinOrderToCreate + "; minOrderSizeToCreate=" + minOrderSizeToCreate);
                if (m_maySyncAccount) {
                    log("no orders - we may re-check account");
                    initAccount();
                }
                return STATE_NO_CHANGE;
            }
        } else {
            log("order cancel was attempted. time passed. posting recheck direction");
            postRecheckDirection();
        }
        return ret;
    }

    protected int placeOrderToExchange(OrderData placeOrder) throws Exception {
        if (placeOrderToExchange(m_exchange, placeOrder)) {
            m_orderPlaceAttemptCounter++;
            log("    orderPlaceAttemptCounter=" + m_orderPlaceAttemptCounter);
            return STATE_ORDER;
        } else {
            log("order place error - switch to ERROR state");
            return STATE_ERROR;
        }
    }

    protected boolean onTooOldTick(long tickAge) {
        boolean ret = false;
        log("too old tick for order: " + tickAge);
        long start = System.currentTimeMillis();
        TimeFramePoint timeFramePoint = addTimeFrame(TimeFrameType.top, start);
        TopData top = Fetcher.fetchTopOnce(m_exchange, m_pair);
        if (top != null) { // we got fresh top data
            long end = System.currentTimeMillis();
            timeFramePoint.m_end = end;
            double buy = top.m_bid;
            double sell = top.m_ask;
            if ((buy == m_lastTopBid) && (sell == m_lastTopAsk)) {
                log("looks STUCK top data. IGNORING: top=" + top);
            } else {
                m_lastTopBid = buy;
                m_lastTopAsk = sell;
                TopDataPoint topDataPoint = new TopDataPoint(top, end, buy, sell);
                addTopDataPoint(topDataPoint);
                long takes = end - start;
                m_topTakesCalc.addValue((double) takes);
                double avgBuy = m_buyAvgCounter.get();
                double avgSell = m_sellAvgCounter.get();
                double avgBidAskDiff = avgSell - avgBuy;
                double farDistance = TOO_FAR_TICK_DISTANCE * avgBidAskDiff;
                double allowBuy = avgBuy - farDistance;
                double allowSell = avgSell + farDistance;
                if ((buy > allowBuy) && (sell < allowSell)) {
                    log("LOADED fresh top=" + top);
                    ret = true;
                    onTopInt(System.currentTimeMillis(), buy, sell, TopSource.top_fetch);
                } else {
                    log("LOADED too far top. IGNORING: top=" + top + "; avgBuy=" + avgBuy + "; avgSell=" + avgSell + ";  current: m_buy=" + m_buy + "; m_sell=" + m_sell);
                }
            }
        }
        postRecheckDirection();
        return ret;
    }

    protected void addTopDataPoint(TopDataPoint topDataPoint) {
        synchronized (m_tops) {
            m_tops.add(topDataPoint);
        }
    }

    private double calcOrderPrice(Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
        log("   calcOrderPrice() orderPriceMode=" + m_orderPriceMode);
        return m_orderPriceMode.calcOrderPrice(this, exchange, directionAdjusted, needOrderSide);
    }

    protected boolean placeOrderToExchange(Exchange exchange, OrderData order) throws Exception {
        if (m_account.allocateOrder(order)) {
            OrderData.OrderPlaceStatus ops = placeOrderToExchange(exchange, order, OrderState.LIMIT_PLACED);
            if (ops == OrderData.OrderPlaceStatus.OK) {
                log(" placeOrderToExchange successful: " + exchange + ", " + order + ", account: " + m_account);
                return true;
            } else {
                m_account.releaseOrder(order, exchange);
                m_maySyncAccount = true; // order place error - need account recync
            }
        } else {
            log("ERROR: account allocateOrder unsuccessful: " + exchange + ", " + order + ", account: " + m_account);
            m_maySyncAccount = true; // allocate error - need account recync
        }
        return false;
    }

    protected OrderData.OrderPlaceStatus placeOrderToExchange(Exchange exchange, OrderData order, OrderState state) throws Exception {
        OrderData.OrderPlaceStatus ret;
        long start = System.currentTimeMillis();
        TimeFramePoint timeFramePoint = addTimeFrame(TimeFrameType.place, start);
        PlaceOrderData poData = Fetcher.placeOrder(order, exchange);
        long end = System.currentTimeMillis();
        timeFramePoint.m_end = end;
        long takes = end - start;
        m_placeOrderTakesCalc.addValue((double) takes);
        log(" order placed in " + Utils.millisToDHMSStr(takes) + ": " + poData.toString(exchange, order.m_pair));
        String error = poData.m_error;
        if (error == null) {
            order.m_status = OrderStatus.SUBMITTED;
            double amount = poData.m_received;
            if (amount != 0) { // not implemented - makes sense for btce
                // see Triplet.placeOrderToExchange()
                String amountStr = order.roundAmountStr(exchange, amount);
                String orderAmountStr = order.roundAmountStr(exchange);
                throw new RuntimeException("  some part of order (" + amountStr + " from " + orderAmountStr + ") is executed at the time of placing ");
            }
            order.m_state = state;
            order.m_placeTime = System.currentTimeMillis();
            ret = OrderData.OrderPlaceStatus.OK;
        } else {
            order.m_status = OrderStatus.ERROR;
            order.m_state = OrderState.NONE;
            if (error.contains("SocketTimeoutException")) {
                ret = OrderData.OrderPlaceStatus.CAN_REPEAT;
                // actually order can be placed, but we will not control it - we will go to ERROR state
            } else if (error.contains("It is not enough") || // It is not enough BTC in the account for sale
                    (error.contains("Insufficient") && error.contains("balance"))) { // Insufficient CNY balance
                ret = OrderData.OrderPlaceStatus.ERROR;
                log("  NotEnoughFunds detected - need sync account");
                initAccount();
            } else {
                ret = OrderData.OrderPlaceStatus.ERROR;
            }
        }
        return ret;
    }

    protected TimeFramePoint addTimeFrame(TimeFrameType type, long start) {
        TimeFramePoint timeFramePoint = new TimeFramePoint(type, start);
        synchronized (m_timeFramePoints) {
            m_timeFramePoints.add(timeFramePoint);
        }
        return timeFramePoint;
    }

    protected int checkOrderOutOfMarket(OrderData order) throws Exception {
        int ret = 0;
        boolean isBuy = order.m_side.isBuy();
        double orderPrice = order.m_price;

        /// ask  110 will sell
        /// bid  100 will buy
        // order 95 buy
        double avgSell = m_sellAvgCounter.get();
        double avgBuy = m_buyAvgCounter.get();
        double avgDiff = avgSell - avgBuy;
        double diff = m_sell - m_buy;
        double mid = (m_sell + m_buy) / 2;
        double halfAvgDiff = avgDiff / 2;
        double adjSell = mid + halfAvgDiff;
        double adjBuy = mid - halfAvgDiff;
        double threshold = avgDiff * outOfMarketThreshold(); // allow 1/2 bidAdsDiff for order price to be out of mkt prices
        double outOfMarketDistance = isBuy ? adjBuy - orderPrice : orderPrice - adjSell;
        double outOfMarketRate = outOfMarketDistance / threshold;
        log(" checkOrderOutOfMarket " + order.m_orderId + " " + order.m_side + "@" + orderPrice +
                "; avgBuy=" + avgBuy + "; avgSell=" + avgSell + "; avgDiff=" + avgDiff +
                "; buy=" + m_buy + "; sell=" + m_sell + "; diff=" + diff + "; mid=" + mid +
                "; adjBuy=" + adjBuy + "; adjSell=" + adjSell + "; threshold=" + threshold +
                "; distance=" + outOfMarketDistance + "; rate=" + outOfMarketRate);
        if (outOfMarketDistance > threshold) {
            log("  out of MKT. try cancel...");
            long liveTime = System.currentTimeMillis() - order.m_placeTime;
            long minOrderLiveTime = minOrderLiveTime();
            long allowOrderLiveTime = (long) (minOrderLiveTime / outOfMarketRate);
            if (liveTime < allowOrderLiveTime) {
                log("   order liveTime=" + liveTime + "ms allowOrderLiveTime=" + allowOrderLiveTime + " - wait little bit more");
            } else {
                log("   order liveTime=" + liveTime + "ms allowOrderLiveTime=" + allowOrderLiveTime + " - cancel order...");
                String error = cancelOrder(order);
                if (error == null) {
                    ret |= FLAG_CANCELED;
                } else {
                    log("ERROR in cancel order: " + error + "; " + order);
                    log("looks the order was already executed - need check live orders");
                    ret |= FLAG_NEED_CHECK_LIVE_ORDERS;
                }
                // order cancel attempted - need sync account since part can be executed in the middle
                m_maySyncAccount = true;
                log("need Recheck Direction");
                ret |= FLAG_NEED_RECHECK_DIRECTION;
            }
        } else {
            // order 101 buy
            /// ask  100 will sell
            /// bid   90 will buy
            if ((isBuy && (m_sell <= orderPrice)) || (!isBuy && (m_buy >= orderPrice))) {
                log("  MKT price [buy=" + m_buy + "; sell=" + m_sell + "] is crossed the order " + order.m_side + "@" + orderPrice + " - starting CheckLiveOrdersTask");
                addTask(new CheckLiveOrdersTask());
            }
        }
        return ret;
    }

    public int processTrade(TradeDataLight tData, IIterationContext.BaseIterationContext inContext) throws Exception {
        double tradePrice = tData.m_price;
        if (hasOrdersWithMatchedPrice(tradePrice)) {
            log("  same price - MAYBE SOME PART OF OUR ORDER EXECUTED ?");
            IIterationContext.BaseIterationContext iContext = getLiveOrdersContextIfNeeded(inContext);
            int oscState = checkOrdersState(iContext);
            return oscState; // check orders state
        }
        long timestamp = tData.m_timestamp;
        if (timestamp >= m_tradeMillis) { // ignore past time ticks
            if (tradePrice < m_buy) {
                double buy = (m_buy * 2 + tradePrice) / 3;
                log("  buy is adapted to trade price: " + m_buy + " -> " + buy);
                m_buy = buy;
                m_topSource = TopSource.trade;
                m_topMillis = timestamp;
            }
            if (tradePrice > m_sell) {
                double sell = (m_sell * 2 + tradePrice) / 3;
                log("  sell is adapted to trade price: " + m_sell + " -> " + sell);
                m_sell = sell;
                m_topSource = TopSource.trade;
                m_topMillis = timestamp;
            }
            m_tradeMillis = timestamp;
        }
        checkOrdersOutOfMarket();
        return STATE_NO_CHANGE; // no change
    }

    public void processTop() throws Exception {
        log("processTop(buy=" + m_buy + ", sell=" + m_sell + ")");
        if (haveNotFilledOrder()) {
            processTopInt();
        }
    }

    public String dumpTakesTime() {
        return " order:" + Utils.format3(m_placeOrderTakesCalc.getAverage()) +
                " live:" + Utils.format3(m_liveOrdersTakesCalc.getAverage()) +
                " account:" + Utils.format3(m_initAccountTakesCalc.getAverage()) +
                " cancel:" + Utils.format3(m_cancelOrderTakesCalc.getAverage()) +
                " top:" + Utils.format3(m_topTakesCalc.getAverage());
    }


    //-------------------------------------------------------------------------------
    public class TopTask extends TaskQueueProcessor.SinglePresenceTask {
        public TopTask() { }

        @Override public void process() throws Exception {
            gotTop();
        }
    }

    //-------------------------------------------------------------------------------
    private class StopTaskTask extends TaskQueueProcessor.SinglePresenceTask {
        public StopTaskTask() {}

        @Override public TaskQueueProcessor.DuplicateAction isDuplicate(TaskQueueProcessor.BaseOrderTask other) {
            log("stopping. removed task " + other);
            return TaskQueueProcessor.DuplicateAction.REMOVE_ALL_AND_PUT_AS_LAST;
        }

        @Override public void process() throws Exception {
            cancelAllOrders();
            m_taskQueueProcessor.stop();
        }
    }

    //-------------------------------------------------------------------------------
    public class TradeTask extends TaskQueueProcessor.BaseOrderTask {
        private final TradeDataLight m_tData;

        public TradeTask(TradeDataLight tData) {
            m_tData = tData;
        }

        @Override public TaskQueueProcessor.DuplicateAction isDuplicate(TaskQueueProcessor.BaseOrderTask other) {
            if (other instanceof TradeTask) {
                TradeTask tradeTask = (TradeTask) other;
                double price = m_tData.m_price;
                if (tradeTask.m_tData.m_price == price) { // skip same price TradeTask if already scheduled
                    return TaskQueueProcessor.DuplicateAction.REMOVE_ALL_AND_PUT_AS_LAST;
                }
            }
            return null;
        }

        @Override public void process() throws Exception {
            gotTrade(m_tData);
        }

        @Override public String toString() { return "TradeTask[tData=" + m_tData + "]"; }
    }

    //-------------------------------------------------------------------------------
    public class InitTask extends TaskQueueProcessor.SinglePresenceTask {
        public InitTask() { }

        @Override public void process() throws Exception {
            log("InitTask.process()");
            initImpl();
        }
    }

    //-------------------------------------------------------------------------------
    public class RecheckDirectionTask extends TaskQueueProcessor.SinglePresenceTask {
        public RecheckDirectionTask() {
        }

        @Override public TaskQueueProcessor.DuplicateAction isDuplicate(TaskQueueProcessor.BaseOrderTask other) {
            TaskQueueProcessor.DuplicateAction duplicate = super.isDuplicate(other);
            if (duplicate != null) {
                duplicate = TaskQueueProcessor.DuplicateAction.REMOVE_ALL_AND_PUT_AS_FIRST;
            }
            return duplicate;
        }

        @Override public void process() throws Exception {
            recheckDirection();
        }
    }

    //-------------------------------------------------------------------------------
    protected class CheckLiveOrdersTask extends TaskQueueProcessor.SinglePresenceTask {
        public CheckLiveOrdersTask() {}

        @Override public TaskQueueProcessor.DuplicateAction isDuplicate(TaskQueueProcessor.BaseOrderTask other) {
            TaskQueueProcessor.DuplicateAction duplicate = super.isDuplicate(other);
            if (duplicate != null) {
                log(" skipped CheckLiveOrdersTask duplicate");
            }
            return duplicate;
        }

        @Override public void process() throws Exception {
            log("CheckLiveOrdersTask.process()");
            checkLiveOrders();
        }
    }

    //-------------------------------------------------------------------------------
    protected enum OrderPriceMode {
        DEEP_MKT_AVG {
            @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
                double buy = baseExecutor.m_buy;
                double sell = baseExecutor.m_sell;
                double diff = sell - buy;
                double midPrice = (buy + sell) / 2;
                double avgBuy = baseExecutor.m_buyAvgCounter.get();
                double avgSell = baseExecutor.m_sellAvgCounter.get();
                double avgDiff = avgSell - avgBuy;
                double diffRate = diff / avgDiff;
                double rate = Math.min(1, diffRate);

                Pair pair = baseExecutor.m_pair;
                log("  buy=" + exchange.roundPriceStr(buy, pair) + "; sell=" + exchange.roundPriceStr(sell, pair) +
                        "; diff=" + exchange.roundPriceStr(diff, pair) + "; midPrice=" + Utils.format5(midPrice) +
                        "; avgBuy=" + Utils.format5(avgBuy) + "; avgSell=" + Utils.format5(avgSell) + "; avgDiff=" + Utils.format5(avgDiff) +
                        "; diffRate=" + Utils.format5(diffRate) + "; rate=" + Utils.format5(rate) +
                        "; needOrderSide=" + needOrderSide);
                boolean isBuy = needOrderSide.isBuy();
                int sideDirection = isBuy ? 1 : -1;
                double offset = rate * avgDiff / 2;
                int orderPlaceAttempt = baseExecutor.m_orderPlaceAttemptCounter;
                double pip = exchange.minAmountStep(pair);
                double adjustedPrice = midPrice + offset * sideDirection + DEEP_MKT_PIP_RATIO * (1 + orderPlaceAttempt) * (isBuy ? pip : -pip);
                log("    sideDirection=" + sideDirection + " offset=" + offset +
                        "; orderPlaceAttempt=" + orderPlaceAttempt + "; pip=" + pip +
                        "; adjustedPrice=" + adjustedPrice );
                RoundingMode roundMode = needOrderSide.getMktRoundMode();
                double orderPrice = exchange.roundPrice(adjustedPrice, pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        },
        MKT_AVG {
            @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
                double buy = baseExecutor.m_buy;
                double sell = baseExecutor.m_sell;
                double diff = sell - buy;
                double midPrice = (buy + sell) / 2;
                double avgBuy = baseExecutor.m_buyAvgCounter.get();
                double avgSell = baseExecutor.m_sellAvgCounter.get();
                double avgDiff = avgSell - avgBuy;
                double diffRate = diff / avgDiff;
                double rate = Math.min(1, diffRate);
                log("  buy=" + buy + "; sell=" + sell + "; diff=" + diff + "; midPrice=" + midPrice +
                        "; avgBuy=" + avgBuy + "; avgSell=" + avgSell + "; avgDiff=" + avgDiff +
                        "; diffRate=" + diffRate + "; rate=" + rate +
                        "; needOrderSide=" + needOrderSide);
                int sideDirection = needOrderSide.isBuy() ? 1 : -1;
                double offset = rate * avgDiff / 2;
                double adjustedPrice = midPrice + offset * sideDirection;
                log("    sideDirection=" + sideDirection + " offset=" + offset + "; adjustedPrice=" + adjustedPrice);
                RoundingMode roundMode = needOrderSide.getMktRoundMode();
                double orderPrice = exchange.roundPrice(adjustedPrice, baseExecutor.m_pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        },
        DEEP_MKT {
            @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
                double buy = baseExecutor.m_buy;
                double sell = baseExecutor.m_sell;
                boolean isBuy = needOrderSide.isBuy();
                double mktPrice = isBuy ? sell : buy;
                log("  buy=" + buy + "; sell=" + sell + "; mktPrice=" + mktPrice + "; needOrderSide=" + needOrderSide);
                double pip = exchange.minAmountStep(baseExecutor.m_pair);
                int orderPlaceAttempt = baseExecutor.m_orderPlaceAttemptCounter;
                double adjustedPrice = mktPrice + DEEP_MKT_PIP_RATIO * (1 + orderPlaceAttempt) * (isBuy ? pip : -pip);
                log("    orderPlaceAttempt=" + orderPlaceAttempt +
                        "; pip=" + pip +
                        "; adjustedPrice=" + adjustedPrice);
                RoundingMode roundMode = needOrderSide.getMktRoundMode();
                double orderPrice = exchange.roundPrice(mktPrice, baseExecutor.m_pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        },
        OSC_REVERSE_AVG {
            @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
                // directionAdjusted [-1 ... 1]
                double buy = baseExecutor.m_buy;
                double sell = baseExecutor.m_sell;
                double diff = sell - buy;
                double avgBuy = baseExecutor.m_buyAvgCounter.get();
                double avgSell = baseExecutor.m_sellAvgCounter.get();
                double avgDiff = avgSell - avgBuy;
                double avgOsc = baseExecutor.getAvgOsc(); // [0 ... 1]
                log("  buy=" + buy + "; sell=" + sell + "; diff=" + diff +
                        "; avgBuy=" + avgBuy + "; avgSell=" + avgSell + "; avgDiff=" + avgDiff +
                        "; avgOsc=" + avgOsc + "; directionAdjusted=" + directionAdjusted + "; needOrderSide=" + needOrderSide);
                double diffRate = diff / avgDiff;
                double rate = diffRate > 1 ? Math.sqrt(diffRate) : 1;
                double midPrice = (buy + sell) / 2;
                double adjustedPrice = midPrice + rate * avgDiff * (0.5 - avgOsc);
                log("    diffRate=" + diffRate + " rate=" + rate + ";  midPrice=" + midPrice + "; adjustedPrice=" + adjustedPrice);
                RoundingMode roundMode = needOrderSide.getMktRoundMode();
                double orderPrice = exchange.roundPrice(adjustedPrice, baseExecutor.m_pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        },
        OSC_REVERSE {
            @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
                // directionAdjusted [-1 ... 1]
                double buy = baseExecutor.m_buy;
                double sell = baseExecutor.m_sell;
                double avgOsc = baseExecutor.getAvgOsc(); // [0 ... 1]
                log("  buy=" + buy + "; sell=" + sell + "; avgOsc=" + avgOsc + "; directionAdjusted=" + directionAdjusted + "; needOrderSide=" + needOrderSide);
                double midPrice = (buy + sell) / 2;
                double bidAskDiff = sell - buy;
                double adjustedPrice = buy + bidAskDiff * (1 - avgOsc);
                log("   midPrice=" + midPrice + "; bidAskDiff=" + bidAskDiff + "; adjustedPrice=" + adjustedPrice);
                RoundingMode roundMode = needOrderSide.getMktRoundMode();
                double orderPrice = exchange.roundPrice(adjustedPrice, baseExecutor.m_pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        },
        OSC {
            @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
                // directionAdjusted [-1 ... 1]
                double buy = baseExecutor.m_buy;
                double sell = baseExecutor.m_sell;
                double avgOsc = baseExecutor.getAvgOsc(); // [0 ... 1]
                log("  buy=" + buy + "; sell=" + sell + "; avgOsc=" + avgOsc + "; directionAdjusted=" + directionAdjusted + "; needOrderSide=" + needOrderSide);
                double midPrice = (buy + sell) / 2;
                double bidAskDiff = sell - buy;
                double adjustedPrice = buy + bidAskDiff * avgOsc;
                log("   midPrice=" + midPrice + "; bidAskDiff=" + bidAskDiff + "; adjustedPrice=" + adjustedPrice);
                RoundingMode roundMode = needOrderSide.getPegRoundMode();
                double orderPrice = exchange.roundPrice(adjustedPrice, baseExecutor.m_pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        },
        MID_TO_MKT { // mid->mkt
            @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
                // directionAdjusted [-1 ... 1]
                double buy = baseExecutor.m_buy;
                double sell = baseExecutor.m_sell;
                log("  buy=" + buy + "; sell=" + sell + "; directionAdjusted=" + directionAdjusted + "; needOrderSide=" + needOrderSide);
                double midPrice = (buy + sell) / 2;
                double bidAskDiff = sell - buy;
                log("   midPrice=" + midPrice + "; bidAskDiff=" + bidAskDiff);
                int orderPlaceAttemptCounter = baseExecutor.m_orderPlaceAttemptCounter;
                double orderPriceCounterCorrection = bidAskDiff / 5 * orderPlaceAttemptCounter;
                double adjustedPrice = midPrice + (needOrderSide.isBuy() ? orderPriceCounterCorrection : -orderPriceCounterCorrection);
                log("   orderPlaceAttemptCounter=" + orderPlaceAttemptCounter + "; orderPriceCounterCorrection=" + orderPriceCounterCorrection + "; adjustedPrice=" + adjustedPrice);
                RoundingMode roundMode = needOrderSide.getPegRoundMode();
                double orderPrice = exchange.roundPrice(adjustedPrice, baseExecutor.m_pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        },
        PEG_TO_MKT { // peg->mkt
            @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
                // directionAdjusted [-1 ... 1]
                double buy = baseExecutor.m_buy;
                double sell = baseExecutor.m_sell;
                log("  buy=" + buy + "; sell=" + sell + "; directionAdjusted=" + directionAdjusted + "; needOrderSide=" + needOrderSide);
                double midPrice = (buy + sell) / 2;
                double bidAskDiff = sell - buy;
                double followMktPrice = needOrderSide.isBuy() ? buy : sell;
                log("   midPrice=" + midPrice + "; bidAskDiff=" + bidAskDiff + "; followMktPrice=" + followMktPrice);
                int orderPlaceAttemptCounter = baseExecutor.m_orderPlaceAttemptCounter;
                double orderPriceCounterCorrection = bidAskDiff / 5 * orderPlaceAttemptCounter;
                double adjustedPrice = followMktPrice + (needOrderSide.isBuy() ? orderPriceCounterCorrection : -orderPriceCounterCorrection);
                log("   orderPlaceAttemptCounter=" + orderPlaceAttemptCounter + "; orderPriceCounterCorrection=" + orderPriceCounterCorrection + "; adjustedPrice=" + adjustedPrice);
                RoundingMode roundMode = needOrderSide.getPegRoundMode();
                double orderPrice = exchange.roundPrice(adjustedPrice, baseExecutor.m_pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        },
        MKT {
            @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
                double buy = baseExecutor.m_buy;
                double sell = baseExecutor.m_sell;
                double mktPrice = needOrderSide.isBuy() ? sell : buy;
                log("  buy=" + buy + "; sell=" + sell + "; mktPrice=" + mktPrice + "; needOrderSide=" + needOrderSide);
                RoundingMode roundMode = needOrderSide.getMktRoundMode();
                double orderPrice = exchange.roundPrice(mktPrice, baseExecutor.m_pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        },
        MID {
            @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
                double buy = baseExecutor.m_buy;
                double sell = baseExecutor.m_sell;
                double midPrice = (buy + sell) / 2;
                log("  buy=" + buy + "; sell=" + sell + "; midPrice=" + midPrice + "; needOrderSide=" + needOrderSide);
                RoundingMode roundMode = needOrderSide.getPegRoundMode();
                double orderPrice = exchange.roundPrice(midPrice, baseExecutor.m_pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        };

        public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            return 0;
        }
    }

    public enum TimeFrameType {
        place {
            @Override public Color color() { return Color.blue; }
        },
        cancel {
            @Override public Color color() { return Color.red; }
        },
        orders {
            @Override public Color color() { return Color.green; }
        },
        account {
            @Override public Color color() { return Color.orange; }
        },
        recheckDirection {
            @Override public Color color() { return Color.white;}
            @Override public int level() { return 2; }
        },
        top {
            @Override public Color color() { return Color.CYAN; }
        };

        public Color color() { return null; }
        public int level() { return 1; }
    }

    public class TimeFramePoint {
        public final TimeFrameType m_type;
        public final long m_start;
        public long m_end;

        public TimeFramePoint(TimeFrameType type, long start) {
            this(type, start, start);
        }

        public TimeFramePoint(TimeFrameType type, long start, long end) {
            m_type = type;
            m_start = start;
            m_end = end;
        }
    }

    public enum TopSource {
        top_subscribe {
            @Override public Color color() { return Color.darkGray; }
        },
        top_fetch {
            @Override public Color color() { return Color.red; }
        },
        trade {
            @Override public Color color() { return Color.orange; }
        };
        public Color color() { return null; }
    }

    public class TopDataPoint {
        public final double m_bid;
        public final double m_ask;
        public final long m_timestamp;
        public final double m_avgBuy;
        public final double m_avgSell;

        public TopDataPoint(TopData topData, long timestamp, double avgBuy, double avgSell) {
            m_bid = topData.m_bid;
            m_ask = topData.m_ask;
            m_timestamp = timestamp;
            m_avgBuy = avgBuy;
            m_avgSell = avgSell;
        }
    }
}