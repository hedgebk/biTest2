package bthdg.osc;

import bthdg.Fetcher;
import bthdg.IIterationContext;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.util.Utils;
import bthdg.ws.ITopListener;
import bthdg.ws.IWs;

import java.math.RoundingMode;
import java.util.List;

public abstract class BaseExecutor implements Runnable {
    public static final int STATE_NO_CHANGE = 0;
    public static final int STATE_NONE = 1;
    public static final int STATE_ORDER = 2;
    public static final int STATE_ERROR = 3;

    protected static int FLAG_CANCELED               = 1 << 0;
    protected static int FLAG_NEED_CHECK_LIVE_ORDERS = 1 << 1;
    protected static int FLAG_NEED_RECHECK_DIRECTION = 1 << 2;

    protected final long m_startMillis;
    private final IWs m_ws;
    protected final Pair m_pair;
    protected final Exchange m_exchange;
    protected TaskQueueProcessor m_taskQueueProcessor;
    private boolean m_run = true;
    protected boolean m_initialized;
    private boolean m_changed;
    protected TopsData m_initTops;
    protected TopsData m_topsData;
    protected AccountData m_initAccount;
    protected AccountData m_account;
    public double m_buy;
    public double m_sell;
    protected boolean m_maySyncAccount = false;
    protected int m_orderPlaceAttemptCounter;
    protected long m_lastProcessDirectionTime;
    public boolean m_feeding;
    protected OrderPriceMode m_orderPriceMode = OrderPriceMode.NORMAL;

    protected static void log(String s) { Log.log(s); }
    public String dumpWaitTime() { return m_taskQueueProcessor == null ? "" : m_taskQueueProcessor.dumpWaitTime(); }

    // abstract
    protected abstract void gotTop() throws Exception;
    protected abstract void gotTrade(TradeData tradeData) throws Exception;
    protected abstract void cancelAllOrders() throws Exception;
    protected abstract void recheckDirection() throws Exception;
    protected abstract List<OrderData> getAllOrders();
    protected abstract IIterationContext.BaseIterationContext checkLiveOrders() throws Exception;
    protected abstract double getDirectionAdjusted();
    protected abstract double checkAgainstExistingOrders(OrderSide needOrderSide, double orderSize);
    protected abstract int cancelOrderIfPresent() throws Exception;
    protected abstract double minOrderSizeToCreate();
    protected abstract boolean cancelOtherOrdersIfNeeded(OrderSide needOrderSide, double notEnough) throws Exception;
    protected abstract void onOrderPlace(OrderData placeOrder);
    protected abstract boolean checkNoOpenOrders();
    protected abstract long minOrderLiveTime();
    protected abstract double outOfMarketThreshold();
    protected abstract boolean hasOrdersWithMatchedPrice(double tradePrice);
    protected abstract void checkOrdersOutOfMarket() throws Exception;
    protected abstract int checkOrdersState(IIterationContext.BaseIterationContext iContext) throws Exception;
    protected abstract boolean haveNotFilledOrder();
    protected abstract void processTopInt() throws Exception;
    protected abstract double useFundsFromAvailable();

    public BaseExecutor(IWs ws, Pair pair) {
        m_ws = ws;
        m_pair = pair;
        m_exchange = m_ws.exchange();
        m_startMillis = System.currentTimeMillis();
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
        if (!m_initialized) {
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
            m_taskQueueProcessor = new TaskQueueProcessor();
        }
        return m_taskQueueProcessor;
    }

    public void onTrade(TradeData tData) { addTask(new TradeTask(tData)); }
    protected void addTask(TaskQueueProcessor.BaseOrderTask task) { getTaskQueueProcessor().addTask(task); }

    protected void initImpl() throws Exception {
        m_topsData = Fetcher.fetchTops(m_exchange, m_pair);
        log(" topsData=" + m_topsData);

        initAccount();
        m_initAccount = m_account.copy();
        m_initTops = m_topsData.copy();
        m_initialized = true;

        log("initImpl() continue: subscribeTop()");
        m_ws.subscribeTop(m_pair, new ITopListener() {
            @Override public void onTop(long timestamp, double buy, double sell) {
//                    log("onTop() timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
                if (buy > sell) {
                    log("ERROR: ignored invalid top data. buy > sell: timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
                    return;
                }
                m_buy = buy;
                m_sell = sell;

                TopData topData = new TopData(buy, sell);
                m_topsData.put(m_pair, topData);
                log(" topsData'=" + m_topsData);

                addTask(new TopTask());
            }
        });
    }

    protected void initAccount() throws Exception {
        AccountData account = m_account;
        m_account = Fetcher.fetchAccount(m_exchange);
        if (m_account != null) {
            log(" account=" + m_account);
            double valuateBtc = m_account.evaluateAll(m_topsData, Currency.BTC, m_exchange);
            log("  valuateBtc=" + valuateBtc + " BTC");
            if (account!= null) {
                account.compareFunds(m_account);
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
        final OrdersData ordersData = Fetcher.fetchOrders(m_exchange, m_pair);
        log(" liveOrders loaded " + ordersData);
        return new IIterationContext.BaseIterationContext() {
            @Override public OrdersData getLiveOrders(Exchange exchange) throws Exception { return ordersData; }
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
        String error = m_account.cancelOrder(order);
        m_maySyncAccount = true;
        return error;
    }

    public void onError() throws Exception {
        log("onError() resetting...  -------------------------- ");
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
        cancelAllOrders();
        initAccount();
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
            return "GAIN: Btc=" + gainBtc + "; Cnh=" + gainCnh + " CNH; avg=" + gainAvg + "; projected=" + projected + "; takes: " + Utils.millisToDHMSStr(takesMillis);
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
            log("   need to sell " + needSellCnh + " CNH; can Sell " + canSellCnh + " CNH; this will buy " + canBuyBtc + " BTC");
            buyBtc = canBuyBtc;
        } else if (needBuyBtc < 0) {
            log("  will sell Btc:");
            double needSellBtc = -needBuyBtc;
            double canSellBtc = Math.min(needSellBtc, haveBtc * useFundsFromAvailable());
            double canBuyCnh = m_topsData.convert(Currency.BTC, Currency.CNH, canSellBtc, m_exchange);
            log("   need to sell " + needSellBtc + " BTC; can Sell " + canSellBtc + " BTC; this will buy " + canBuyCnh + " CNH");
            buyBtc = -canSellBtc;
        } else {
            log("  do not buy/sell anything");
            buyBtc = 0;
        }
        return buyBtc;
    }

    public void postRecheckDirection() {
        log(" posting RecheckDirectionTask");
        addTask(new RecheckDirectionTask());
    }

    public int processDirection() throws Exception {
        m_lastProcessDirectionTime = System.currentTimeMillis();
        double directionAdjusted = getDirectionAdjusted(); // [-1 ... 1]

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
            double minOrderSizeToCreate = minOrderSizeToCreate();
            if (notEnough > minOrderSizeToCreate) {
                boolean cancelPerformed = cancelOtherOrdersIfNeeded(needOrderSide, notEnough);
                if (cancelPerformed) {
                    return STATE_NONE;
                }
            }

            double orderSizeRound = m_exchange.roundAmount(canBuyBtc, m_pair);
            double placeOrderSize = Math.abs(orderSizeRound);
            log("        orderSizeAdjusted=" + Utils.format8(canBuyBtc) + "; orderSizeRound=" + orderSizeRound + "; placeOrderSize=" + Utils.format8(placeOrderSize));

            double exchMinOrderToCreate = m_exchange.minOrderToCreate(m_pair);
            if ((placeOrderSize >= exchMinOrderToCreate) && (placeOrderSize >= minOrderSizeToCreate)) {
                if(checkNoOpenOrders()) {
                    double orderPrice = calcOrderPrice(m_exchange, directionAdjusted, needOrderSide);
                    OrderData placeOrder = new OrderData(m_pair, needOrderSide, orderPrice, placeOrderSize);
                    log("   place orderData=" + placeOrder);

                    if (placeOrderToExchange(m_exchange, placeOrder)) {
                        m_orderPlaceAttemptCounter++;
                        log("    orderPlaceAttemptCounter=" + m_orderPlaceAttemptCounter);
                        onOrderPlace(placeOrder);
                        return STATE_ORDER;
                    } else {
                        log("order place error - switch to ERROR state");
                        return STATE_ERROR;
                    }
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
                return STATE_NONE;
            }
        } else {
            log("order cancel was attempted. time passed. posting recheck direction");
            postRecheckDirection();
        }
        return ret;
    }

    private double calcOrderPrice(Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
        log("   calcOrderPrice() orderPriceMode=" + m_orderPriceMode);
        return m_orderPriceMode.calcOrderPrice(this, exchange, directionAdjusted, needOrderSide);
    }

    protected boolean placeOrderToExchange(Exchange exchange, OrderData order) throws Exception {
        m_maySyncAccount = true;
        if (m_account.allocateOrder(order)) {
            OrderData.OrderPlaceStatus ops = placeOrderToExchange(exchange, order, OrderState.LIMIT_PLACED);
            if (ops == OrderData.OrderPlaceStatus.OK) {
                log(" placeOrderToExchange successful: " + exchange + ", " + order + ", account: " + m_account);
                return true;
            } else {
                m_account.releaseOrder(order, exchange);
            }
        } else {
            log("ERROR: account allocateOrder unsuccessful: " + exchange + ", " + order + ", account: " + m_account);
        }
        return false;
    }

    protected OrderData.OrderPlaceStatus placeOrderToExchange(Exchange exchange, OrderData order, OrderState state) throws Exception {
        OrderData.OrderPlaceStatus ret;
        PlaceOrderData poData = Fetcher.placeOrder(order, exchange);
        log(" PlaceOrderData: " + poData.toString(exchange, order.m_pair));
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

    protected int checkOrderOutOfMarket(OrderData order) throws Exception {
        int ret = 0;
        boolean isBuy = order.m_side.isBuy();
        double orderPrice = order.m_price;

        /// ask  110 will sell
        /// bid  100 will buy
        // order 95 buy
        double threshold = (m_sell - m_buy) * outOfMarketThreshold(); // allow 1/2 bidAdsDiff for order price to be out of mkt prices
        if ((isBuy && (m_buy - threshold > orderPrice)) || (!isBuy && (m_sell + threshold < orderPrice))) {
            log("  order " + order.m_orderId + " " + order.m_side + "@" + orderPrice + " is FAR out of MKT [buy=" + m_buy + "; sell=" + m_sell + "]");
            long liveTime = System.currentTimeMillis() - order.m_placeTime;
            if (liveTime < minOrderLiveTime()) {
                log("   order liveTime=" + liveTime + "ms - wait little bit more");
            } else {
                log("   order liveTime=" + liveTime + "ms - cancel order...");
                String error = cancelOrder(order);
                if (error == null) {
                    ret |= FLAG_CANCELED;
                } else {
                    log("ERROR in cancel order: " + error + "; " + order);
                    log("looks the order was already executed - need check live orders");
                    ret |= FLAG_NEED_CHECK_LIVE_ORDERS;
                }
                log("  order cancel attempted- need sync account since part can be executed in the middle");
                initAccount();
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

    public int processTrade(TradeData tData, IIterationContext.BaseIterationContext inContext) throws Exception {
        double tradePrice = tData.m_price;
        if (hasOrdersWithMatchedPrice(tradePrice)) {
            log("  same price - MAYBE SOME PART OF OUR ORDER EXECUTED ?");
            IIterationContext.BaseIterationContext iContext = getLiveOrdersContextIfNeeded(inContext);
            int oscState = checkOrdersState(iContext);
            return oscState; // check orders state
        }
        if (tradePrice < m_buy) {
            double buy = (m_buy * 2 + tradePrice) / 3;
            log("  buy is adapted to trade price: " + m_buy + " -> " + buy);
            m_buy = buy;
        }
        if (tradePrice > m_sell) {
            double sell = (m_sell * 2 + tradePrice) / 3;
            log("  sell is adapted to trade price: " + m_sell + " -> " + sell);
            m_sell = sell;
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


    //-------------------------------------------------------------------------------
    private class TopTask extends TaskQueueProcessor.SinglePresenceTask {
        public TopTask() {}

        @Override public void process() throws Exception {
            gotTop();
        }
    }

    //-------------------------------------------------------------------------------
    private class StopTaskTask extends TaskQueueProcessor.SinglePresenceTask {
        public StopTaskTask() {}

        @Override public boolean isDuplicate(TaskQueueProcessor.BaseOrderTask other) {
            log("stopping. removed task " + other);
            return true;
        }

        @Override public void process() throws Exception {
            cancelAllOrders();
            m_taskQueueProcessor.stop();
        }
    }

    //-------------------------------------------------------------------------------
    public class TradeTask extends TaskQueueProcessor.BaseOrderTask {
        private final TradeData m_tData;

        public TradeTask(TradeData tData) {
            m_tData = tData;
        }

        @Override public boolean isDuplicate(TaskQueueProcessor.BaseOrderTask other) {
//            if (other instanceof TradeTask) {
//                TradeTask tradeTask = (TradeTask) other;
//                double price = m_tData.m_price;
//                if (tradeTask.m_tData.m_price == price) { // skip same price TradeTask
//                    return true;
//                }
//            }
            return false;
        }

        @Override public void process() throws Exception { gotTrade(m_tData); }
        @Override public String toString() {
            return "TradeTask[tData=" + m_tData + "]";
        }
    }

    //-------------------------------------------------------------------------------
    public class InitTask extends TaskQueueProcessor.SinglePresenceTask {
        public InitTask() {}

        @Override public void process() throws Exception {
            log("InitTask.process()");
            initImpl();
        }
    }

    //-------------------------------------------------------------------------------
    public class RecheckDirectionTask extends TaskQueueProcessor.SinglePresenceTask {
        public RecheckDirectionTask() {}

        @Override public boolean isDuplicate(TaskQueueProcessor.BaseOrderTask other) {
            boolean duplicate = super.isDuplicate(other);
            if (duplicate) {
                log(" skipped RecheckDirectionTask duplicate");
            }
            return duplicate;
        }

        @Override public void process() throws Exception { recheckDirection(); }
    }

    //-------------------------------------------------------------------------------
    protected class CheckLiveOrdersTask extends TaskQueueProcessor.SinglePresenceTask {
        public CheckLiveOrdersTask() {}

        @Override public boolean isDuplicate(TaskQueueProcessor.BaseOrderTask other) {
            boolean duplicate = super.isDuplicate(other);
            if (duplicate) {
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
        NORMAL {
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
                log("   orderPlaceAttemptCounter="+ orderPlaceAttemptCounter +"; orderPriceCounterCorrection=" + orderPriceCounterCorrection + "; adjustedPrice=" + adjustedPrice);
                RoundingMode roundMode = needOrderSide.getPegRoundMode();
                double orderPrice = exchange.roundPrice(adjustedPrice, baseExecutor.m_pair, roundMode);
                log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
                return orderPrice;
            }
        },
        MID {
            @Override
            public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
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

        public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) { return 0; }
    }
}
