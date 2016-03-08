package bthdg.tres;

import bthdg.Fetcher;
import bthdg.IIterationContext;
import bthdg.exch.*;
import bthdg.osc.BaseExecutor;
import bthdg.osc.OrderPriceMode;
import bthdg.osc.TaskQueueProcessor;
import bthdg.util.Utils;
import bthdg.ws.IWs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class TresExecutor extends BaseExecutor {
    public static OrderPriceMode ORDER_PRICE_MODE = OrderPriceMode.MARKET; // OrderPriceMode.MID_TO_MKT; // OrderPriceMode.DEEP_MKT; // OrderPriceMode.DEEP_MKT_AVG; // OrderPriceMode.MKT_AVG; ; // OrderPriceMode.MID;
    public static final int NO_TRADE_TIMEOUT = 30000;
    public static final int RECONNECT_TIMEOUT = 90000;
    private static final long MIN_ORDER_LIVE_TIME = 7000;
    private static final double OUT_OF_MARKET_THRESHOLD = 0.6;
    private static final long MIN_REPROCESS_DIRECTION_TIME = 12000;
    private static final double ORDER_SIZE_TOLERANCE = 0.3;
    public static final int MAX_STOP_ORDER_AGE = 20000;
    static double MIN_ORDER_SIZE = 0.05; // in btc
    static double MAX_ORDER_SIZE = 1.00; // in btc
    public static final double USE_FUNDS_FROM_AVAILABLE = 0.95; // 95%
    public static boolean s_auto = true;
    public static double s_manualDirection;

    private final TresExchData m_exchData;
    private TresState m_state = TresState.NONE;
    OrderData m_order;
    public int m_ordersPlaced;
    public int m_ordersFilled;
    public double m_tradeVolume;
    Runnable m_stopCallback;
    private long m_lastSeenTradeTime;
    private double m_lastTradePartRate;
    private boolean m_oldLastTradeReconnectRequested;
    private final List<OrderData> m_pendingMktOrders = new ArrayList<OrderData>();
    private long m_lastMktPlaceTime;

    @Override protected long minOrderLiveTime() { return MIN_ORDER_LIVE_TIME; }
    @Override protected double outOfMarketThreshold() { return OUT_OF_MARKET_THRESHOLD; }
    @Override protected double minOrderSizeToCreate() { return MIN_ORDER_SIZE; }
    @Override protected double maxOrderSizeToCreate() { return MAX_ORDER_SIZE; }
    @Override protected double useFundsFromAvailable() { return USE_FUNDS_FROM_AVAILABLE; }
    @Override protected boolean haveNotFilledOrder() { return (m_order != null) && !m_order.isFilled(); }
    @Override protected TaskQueueProcessor createTaskQueueProcessor() { return new TresTaskQueueProcessor(); }
    @Override public double getAvgFillSize() {
        return (m_ordersFilled > 0) ? m_tradeVolume / m_ordersFilled : 0;
    }

    public TresExecutor(TresExchData exchData, IWs ws, Pair pair) {
        super(ws, pair, exchData.m_tres.m_barSizeMillis);
        m_exchData = exchData;
        m_orderPriceMode = ORDER_PRICE_MODE;
        m_collectPoints = exchData.m_tres.m_collectPoints;
        if (!exchData.m_tres.m_logProcessing) {
            Thread thread = new Thread(this);
            thread.setName("TresExecutor");
            thread.start();
        }
        if (Tres.LOG_PARAMS) {
            log("TresExecutor");
            log(" ORDER_PRICE_MODE=" + ORDER_PRICE_MODE);
            log(" MIN_ORDER_LIVE_TIME=" + MIN_ORDER_LIVE_TIME);
            log(" OUT_OF_MARKET_THRESHOLD=" + OUT_OF_MARKET_THRESHOLD);
            log(" MIN_REPROCESS_DIRECTION_TIME=" + MIN_REPROCESS_DIRECTION_TIME);
            log(" ORDER_SIZE_TOLERANCE=" + ORDER_SIZE_TOLERANCE);
            log(" MAX_STOP_ORDER_AGE=" + MAX_STOP_ORDER_AGE);
            log(" MIN_ORDER_SIZE=" + MIN_ORDER_SIZE);
            log(" MAX_ORDER_SIZE=" + MAX_ORDER_SIZE);
            log(" USE_FUNDS_FROM_AVAILABLE=" + USE_FUNDS_FROM_AVAILABLE);
        }
    }

    @Override protected void addTask(TaskQueueProcessor.BaseOrderTask task) {
        if (m_exchData.m_tres.m_logProcessing) {
            throw new RuntimeException("addTask() not applicable for logProcessing mode");
        } else {
            super.addTask(task);
        }
    }

    @Override protected void addTaskFirst(TaskQueueProcessor.BaseOrderTask task) {
        if (m_exchData.m_tres.m_logProcessing) {
            throw new RuntimeException("addTaskFirst() not applicable for logProcessing mode");
        } else {
            super.addTaskFirst(task);
        }
    }

    @Override protected void onTop(TopDataPoint topDataPoint) {
        m_exchData.onTop(topDataPoint);
    }

    public TresState onAfterError() throws Exception {
        log("onAfterError() reset check ...  -------------------------- ");
        if (m_order != null) {
            log(" ERROR m_order != null after reset: " + m_order);
            return TresState.ERROR;
        }
        TresState tresState = checkAllocated(m_pair.m_from);
        if (tresState == TresState.NONE) {
            tresState = checkAllocated(m_pair.m_to);
        }
        return tresState;
    }

    protected TresState checkAllocated(Currency currency) {
        double evalAll = m_account.evaluateAll(m_topsData, currency, m_exchange);
        double allocated = m_account.allocated(currency);
        // sometimes funds on account reflect with delay
        if (allocated > evalAll / 10) {
            log(" ERROR to much allocated " + currency + " after reset: " + m_account);
            return TresState.ERROR;
        }
        return TresState.NONE;
    }

    @Override public void onTrade(TradeDataLight tData) {
        if (DO_TRADE) {
            TradeTask task = new TradeTask(tData);
            if ((m_order != null) && m_order.m_status.isActive()) {
                if (tData.m_price == m_order.m_price) { // same price trade - process first
                    addTaskFirst(task);
                    return;
                }
            }
            addTask(task);
        }
        m_lastSeenTradeTime = System.currentTimeMillis();
    }

    @Override protected void gotTrade(TradeDataLight tradeData) throws Exception {
//        log("TresExecutor.gotTrade() tData=" + tradeData);

        TresState newState = m_state.onTrade(this, tradeData, null);
        setState(newState);

        if (m_feeding) {
            long passed = System.currentTimeMillis() - m_lastProcessDirectionTime;
            if (passed > MIN_REPROCESS_DIRECTION_TIME) {
                log(" no check direction for long time " + passed + "ms - need postRecheckDirection");
                if (m_initialized) {
                    postRecheckDirection();
                } else {
                    log("  postRecheckDirection skipped - not yet initialized");
                }
            }
        }
    }

    @Override protected void recheckDirection() throws Exception {
        log("TresExecutor.recheckDirection() direction=" + getDirectionAdjusted());
        long start = System.currentTimeMillis();
        TimeFramePoint timeFramePoint = addTimeFrame(TimeFrameType.recheckDirection, start);
        setState(m_state.onDirection(this));
        long end = System.currentTimeMillis();
        timeFramePoint.m_end = end;
    }

    @Override public int processDirection() throws Exception {
        if (m_order != null) {
            log("TresExecutor.processDirection() m_order=" + m_order);

            if (m_order.m_status == OrderStatus.CANCELING) {
                checkOrderState(m_order);
                if (m_order.m_status == OrderStatus.CANCELING) {
                    log("  CANCEL request not yet executed: " + m_order);
                    return STATE_NO_CHANGE;
                } else if (m_order.m_status == OrderStatus.CANCELLED) {
                    log("  CANCEL request confirmed: " + m_order);
                    m_order = null;
                    return STATE_ERROR; // need recheck everything
                } else {
                    log("  error: unexpected status of CANCELING order: " + m_order);
                    return STATE_ERROR; // need recheck everything
                }
            }
        }
        checkLastSeenTrade();
        return super.processDirection();
    }

    protected void checkLastSeenTrade() {
        if (m_lastSeenTradeTime > 0) { // do we got any trade at all ?
            long millis = System.currentTimeMillis();
            long lastTradeAge = millis - m_lastSeenTradeTime;
            if (lastTradeAge > NO_TRADE_TIMEOUT) { // no trade in 1 min - start park
                m_lastTradePartRate = Math.max(0, 1 - ((double) lastTradeAge - NO_TRADE_TIMEOUT) / NO_TRADE_TIMEOUT);
                log("lastTradeAge=" + lastTradeAge + " => lastTradePartRate=" + m_lastTradePartRate);
                if (lastTradeAge > RECONNECT_TIMEOUT) { // no trade in 3 min - request reconnect
                    if (!m_oldLastTradeReconnectRequested) {
                        log(" TOO old last trade - request reconnect...");
                        m_oldLastTradeReconnectRequested = true;
                        m_exchData.m_ws.reconnect();
                    }
                }
            } else {
                m_lastTradePartRate = 1.0;
                m_oldLastTradeReconnectRequested = false;
            }
        }
    }

    @Override protected void onErrorInt() throws Exception {
        super.onErrorInt();
        checkLastSeenTrade();
    }

    @Override protected double getDirectionAdjusted() {
        return s_auto
                ? m_exchData.getDirectionAdjusted() * m_lastTradePartRate
                : s_manualDirection;
    }

    @Override protected void gotTop() throws Exception {
        log("TresExecutor.gotTop() buy=" + m_buy + "; sell=" + m_sell);
        log(" topsData =" + m_topsData);
        m_state.onTop(this);
    }

    @Override protected double checkAgainstExistingOrders(OrderSide needOrderSide, double orderSizeIn) {
        log("    checkAgainstExistingOrders: needOrderSide=" + needOrderSide + "; orderSizeIn="+orderSizeIn);
        double orderSize = orderSizeIn;
        if (m_order != null) { // we have already live order
            log("     we have already live order:" + m_order);
            OrderSide haveOrderSide = m_order.m_side;
            log("      needOrderSide=" + needOrderSide + "; haveOrderSide=" + haveOrderSide);
            if (needOrderSide == haveOrderSide) {
                double remained = m_order.remained();
                double orderSizeDiff = orderSize - remained;
                log("       remained=" + remained + "; orderSizeDiff=" + Utils.format8(orderSizeDiff));
                if (orderSizeDiff > 0) {
                    double adjusted = adjustSizeToAvailable(orderSizeDiff);
                    if (orderSizeDiff != adjusted) {
                        log("        orderSizeDiff adjusted by available: from " + orderSizeDiff + " to " + adjusted);
                        orderSizeDiff = adjusted;
                    }
                    orderSize = remained + orderSizeDiff;
                    log("         orderSize'=" + orderSize);
                }
                log("       same order sides: we have order: remainedOrderSize=" + remained + "; needOrderSize=" + orderSize);
                double orderSizeRatio = remained / orderSize;
                double ratioDistanceFromOne = Math.abs(orderSizeRatio - 1);
                log("        orderSizeRatio=" + orderSizeRatio + "; ratioDistanceFromOne=" + ratioDistanceFromOne);
                if (ratioDistanceFromOne < ORDER_SIZE_TOLERANCE) {
                    log("        order Sizes are very close - do not cancel existing order (tolerance=" + ORDER_SIZE_TOLERANCE + ")");
                    return 0;
                }
            } else {
                log("       we have order of another side");
            }
        }

        for (OrderData pOrd : m_pendingMktOrders) {
            OrderSide side = pOrd.m_side;
            double amount = pOrd.m_amount;
            if(side == needOrderSide) {
                log("     we have already pending market of same side:" + pOrd);
                orderSize -= amount;
            } else {
                log("     we have pending market of OPPOSITE side:" + pOrd);
                orderSize += amount;
            }
            log("      order size adjusted to: " + orderSize);
        }
        return orderSize;
    }

    @Override protected int cancelOrderIfPresent() throws Exception {
        if (m_order != null) {
            log("  need cancel existing order: " + m_order);
            String error = cancelOrder(m_order);

            // here we may have desync case - we sent order; the order is partially executed; but we do not know this yet;
            // we cancel order; internally release funds performed, but since part of order is already executed - we may have
            // account mismatch here - resync account to prevent.
            m_maySyncAccount = true;

            if (error == null) {
                log("   order cancel OK: " + m_order);
                m_order = null;

                postRecheckDirection();
                return STATE_NONE;
            } else {
                log("ERROR in cancelOrderIfPresent: " + error + "; " + m_order);
                return STATE_ERROR;
            }
        }
        return STATE_NO_CHANGE;
    }

    @Override protected boolean cancelOtherOrdersIfNeeded(OrderSide needOrderSide, double notEnough) throws Exception {
        return false; // no other orders
    }

    @Override protected boolean checkNoOpenOrders() {
        if (m_order == null) { // we should not have open order at this place
            return true;
        } else {
            log("warning: order still exist - switch to ERROR state: " + m_order);
            return false;
        }
    }

    @Override public List<String> cancelAllOrders() throws Exception {
        log("  cancelAllOrders() order=" + m_order);
        List<String> cancelledOrdIds = null;
        if (m_order != null) {
            log("   we have existing order, will cancel: " + m_order);
            String error = cancelOrder(m_order);
            if (error == null) {
                cancelledOrdIds = new ArrayList<String>();
                cancelledOrdIds.add(m_order.m_orderId);
                m_order = null;
            } else {
                log("    error in cancelAllOrders: " + error + "; " + m_order);
                m_order = null;
            }
            log("    order cancel attempted - need sync account since part can be executed in the middle");
        }
        for (OrderData pendingMktOrder : m_pendingMktOrders) {
            log("   we have pendingMktOrder, will try cancel: " + pendingMktOrder);
            String error = cancelOrder(pendingMktOrder);
            if (error == null) {
                if (cancelledOrdIds == null) {
                    cancelledOrdIds = new ArrayList<String>();
                }
                cancelledOrdIds.add(pendingMktOrder.m_orderId);
            } else {
                log("    error in cancel pendingMktOrder (can be already executed): " + error + "; " + pendingMktOrder);
            }

        }
        m_pendingMktOrders.clear();
        return cancelledOrdIds;
    }

    @Override protected List<OrderData> getAllOrders() {
        if (m_order != null) {
            return Arrays.asList(m_order);
        }
        return null;
    }

    @Override protected IIterationContext.BaseIterationContext checkLiveOrders() throws Exception {
        log("checkLiveOrders()");
        IIterationContext.BaseIterationContext iContext = null;
        if (m_order != null) {
            iContext = getLiveOrdersContext();
            setState(checkIntOrderState(iContext));
        }
        return iContext;
    }

    @Override protected boolean hasOrdersWithMatchedPrice(double tradePrice) {
        if (m_order != null) {
            double ordPrice = m_order.m_price;
            boolean ret = (tradePrice == ordPrice);
            log(" hasOrdersWithMatchedPrice() tradePrice=" + tradePrice + ", orderPrice=" + ordPrice + ";  ret=" + ret);
            return ret;
        }
        return false;
    }

    @Override protected int checkOrdersState(IIterationContext.BaseIterationContext iContext) throws Exception {
        TresState tresState = checkIntOrderState(iContext);
        return TresState.toCode(tresState);
    }

    @Override protected int checkOrderState(IIterationContext.BaseIterationContext iContext, OrderData order) throws Exception {
        TresState state = checkOrderStateInt(iContext, order);
        return TresState.toCode(state);
    }

    private TresState checkIntOrderState(IIterationContext.BaseIterationContext iContext) throws Exception {
        TresState tresState = checkOrderStateInt(iContext, m_order);
        if (tresState == TresState.NONE) { // order was filled
            m_orderPlaceAttemptCounter = 0;
            m_order = null;
        }
        return tresState;
    }

    protected TresState checkOrderStateInt(IIterationContext.BaseIterationContext iContext, OrderData order) throws Exception {
        order.checkState(iContext, m_exchange, m_account,
                null, // TODO - implement exec listener, add partial support - to fire partial close orders
                null);
        log("  order state checked: " + order);

        if (order.isFilled()) {
            log("$$$$$$   order FILLED: " + order);
            onOrderFilled(order);
            logValuate();
            return TresState.NONE;
        } else if( order.m_status == OrderStatus.ERROR ) {
            log(" order status ERROR. switch to error state: " + order);
            return TresState.ERROR;
        } else {
            log("   have order. not yet FILLED: " + order);
            return null; // no change
        }
    }

    private void setState(TresState state) {
        if ((state != null) && (m_state != state)) {
            log("STATE changed from " + m_state + " to " + state);
            m_state = state;
        }
    }

    @Override protected void processTopInt() throws Exception {
        int ret = checkOrderOutOfMarket(m_order);
        processOrderOutOfMarket(ret);
    }

    private void processOrderOutOfMarket(int ret) throws Exception {
        if((ret & FLAG_CANCELED) != 0) {
            m_order = null;
        }
        if((ret & FLAG_NEED_CHECK_LIVE_ORDERS) != 0) {
            checkLiveOrders();
        }
        if((ret & FLAG_NEED_RECHECK_DIRECTION) != 0) {
            postRecheckDirection();
        }
    }

    @Override protected void checkOrdersOutOfMarket() throws Exception {
        if (haveNotFilledOrder()) {
            int ret = checkOrderOutOfMarket(m_order);
            processOrderOutOfMarket(ret);
        }
    }

    @Override protected int onOrderPlace(OrderData placeOrder, long tickAge, double buy, double sell, TopSource topSource) {
        m_order = placeOrder;
        m_ordersPlaced++;
        m_exchData.addOrder(placeOrder, tickAge, buy, sell, topSource, getGainAvg()); // will call postFrameRepaint inside

        OrderType orderType = placeOrder.m_type;
        if (orderType == OrderType.MARKET) {
            m_pendingMktOrders.add(placeOrder);
            m_lastMktPlaceTime = System.currentTimeMillis();
            log(" added to pendingMktOrders. now num=" + m_pendingMktOrders.size() + " : " + placeOrder);
            m_order = null;
            return STATE_NO_CHANGE;
        }
        return STATE_ORDER;
    }

    protected int recheckPendingMktOrders() throws Exception {
        boolean isNotEmpty = !m_pendingMktOrders.isEmpty();
        long lastMktPlaceTimeDiff = System.currentTimeMillis() - m_lastMktPlaceTime;

        log(" recheckPendingMktOrders() pendingMktOrders.num=" + m_pendingMktOrders.size() + "; lastMktPlaceTimeDiff=" + lastMktPlaceTimeDiff);
        if (isNotEmpty && (lastMktPlaceTimeDiff > 5000)) { // give MKT orders chance to be executed via WS
            OrderData od = m_pendingMktOrders.get(0);
            log(" first PendingMktOrder: " + od);
            int status = checkOrderState(od);
            if (status == STATE_NO_CHANGE) {
                log("  MktOrder NOT yet executed: " + od);
            } else {
                m_pendingMktOrders.remove(0);
                if (status == BaseExecutor.STATE_ERROR) {
                    log("  checkOrderState() gives STATE_ERROR -> recheck all pendingMktOrders");
                    while (!m_pendingMktOrders.isEmpty()) {
                        recheckPendingMktOrders();
                    }
                }
            }
            return status;
        }
        return STATE_NO_CHANGE;
    }

    private void onOrderFilled(OrderData order) {
        m_ordersFilled++;
        double amount = order.m_amount;
        m_tradeVolume += amount;
        m_exchData.m_tres.postFrameRepaint();
    }

    @Override protected void addTopDataPoint(TopDataPoint topDataPoint) {
        super.addTopDataPoint(topDataPoint);
        m_exchData.m_tres.postFrameRepaint();
    }

    public void postStopTask() {
        log(" posting RecheckDirectionTask");
        addTask(new StopTask());
    }

    public void postResetTask() {
        log(" posting ResetTask");
        addTask(new ResetTask());
    }

    private void onStopRequested() throws Exception {
        log("TresExecutor.onStopRequested()");
        setState(m_state.onStopRequested(this));
    }

    private void onResetRequested() throws Exception {
        log("TresExecutor.onResetRequested()");
        setState(TresState.ERROR);
    }

    public TresState parkAccount() throws Exception {
        if (!m_initialized) {
            initialize();
        }
        TopsData topsData = m_topsData;
        if (topsData == null) {
            Fetcher.fetchTops(m_exchange, m_exchange.supportedPairs());
        }
        OrderData parkOrder = FundMap.test(m_account, topsData, m_exchange, 0.95);
        if (parkOrder != null) {
            log("   parkOrder=" + parkOrder);
            int rett = placeOrderToExchange(parkOrder);
            if (rett == STATE_ORDER) {
                log("    park order placed: " + parkOrder);
                m_order = parkOrder;
                return TresState.STOP;
            }
            log("    park order place error. post stop task again");
            postStopTask(); // post stop task again
            return TresState.ERROR;
        }
        onStopped();
        return TresState.NONE;
    }

    public void onStopped() { // notify that stopped
        log("     notify that stopped. stopCallback=" + m_stopCallback);
        if (m_stopCallback != null) {
            m_stopCallback.run();
        }
    }

    public boolean tooOldStopOrder() {
        if (m_order != null) {
            long placeTime = m_order.m_placeTime;
            long now = System.currentTimeMillis();
            long orderAge = now - placeTime;
            if (orderAge > MAX_STOP_ORDER_AGE) {
                log("   orderAge=" + orderAge + "; MAX_STOP_ORDER_AGE=" + MAX_STOP_ORDER_AGE);
                return true;
            }
            return false;
        }
        log("   no stop order");
        return true;
    }

    @Override protected int doVoidCycle() throws Exception {
        log("doVoidCycle() m_pendingMktOrders.size=" + m_pendingMktOrders.size() + "; order=" + m_order);
        int ret = recheckPendingMktOrders();
        if (ret == STATE_NO_CHANGE) {
            if ((m_order == null) && m_pendingMktOrders.isEmpty()) {
                if (m_maySyncAccount) {
                    log(" no orders - we may re-check account...");
                    initAccount();
                } else {
                    log(" no orders - we may re-check account allocated...");
                    Currency curr1 = m_pair.m_from;
                    double allocated1 = m_account.allocated(curr1);
                    double all1 = m_account.evaluateAll(m_topsData, curr1, m_exchange);
                    Currency curr2 = m_pair.m_to;
                    double allocated2 = m_account.allocated(curr2);
                    double all2 = m_account.evaluateAll(m_topsData, curr2, m_exchange);
                    if ((allocated1 > all1 * 0.1) || (allocated2 > all2 * 0.1)) {
                        log("   ERROR: no orders but have allocated: " + curr1 + " allocated " + all1 + "; " + curr2 + " allocated " + all2);
                        ret = BaseExecutor.STATE_ERROR;
                    }
                }
            }
        }
        return ret;
    }

    public void onExec(Exec exec) {
        log("onExec() posting ExecTask; exec=" + exec);
        addTaskFirst(new ExecTask(exec));
    }

    private void processExec(Exec exec) throws Exception {
        log("processExec() exec=" + exec);
        final String orderId = exec.m_orderId;
        if (m_order != null) {
            if (m_order.m_orderId.equals(orderId)) {
                log(" exec for pending order: m_order=" + m_order);
                boolean fill = processIfFill(exec, orderId, m_order);
                if (fill) {
                    log("  got FILL pending order: m_order=" + m_order);
                    m_order = null;
                    setState(TresState.NONE);
                }
                return;
            }
        }

        for (OrderData pendingMktOrder : m_pendingMktOrders) {
            if (pendingMktOrder.m_orderId.equals(orderId)) {
                log(" exec for pending mkt order: m_order=" + pendingMktOrder);
                boolean fill = processIfFill(exec, orderId, pendingMktOrder);
                if (fill) {
                    log("  got FILL pending mkt order: m_order=" + pendingMktOrder);
                    m_pendingMktOrders.remove(pendingMktOrder);
                    log("   removed from pendingMktOrders. now size=" + m_pendingMktOrders.size());
                }
                return;
            }
        }

        log(" ERROR exec for not known order: exec=" + exec);
    }

    private boolean processIfFill(Exec exec, String orderId, OrderData order) throws Exception {
        final OrderStatus orderStatus = exec.m_orderStatus;
        // for now process only FILL acync execs
        if (orderStatus == OrderStatus.FILLED) {
            log("  got order FILL");

            HashMap<String, OrdersData.OrdData> map = new HashMap<String, OrdersData.OrdData>();
            double amount = order.m_amount;
            double averagePrice = exec.m_averagePrice;
            OrdersData.OrdData ordData = new OrdersData.OrdData(orderId, amount, amount, 0, averagePrice, 0, "filled", null, order.m_side, order.m_type);
            ordData.m_avgPrice = averagePrice;
            log("   simulate fill with: " + ordData);
            ordData.m_orderStatus = orderStatus;
            map.put(orderId, ordData);
            final OrdersData ordersData = new OrdersData(map);

            IIterationContext.BaseIterationContext iContext = new IIterationContext.BaseIterationContext() {
                @Override public OrdersData getLiveOrders(Exchange exchange) throws Exception {
                    return ordersData;
                }
            };
            checkOrderState(iContext, order);
            return true;
        }
        return false;
    }

    public void onAccount(AccountData accountData) {
        log("onAccount() posting AccountTask; accountData=" + accountData);
        addTask(new AccountTask(accountData));
    }

    private void processAccount(AccountData accountData) {
        log("processAccount() accountData=" + accountData);

        m_account = accountData;

        Currency currencyTo = m_pair.m_to;     // btc=to
        double valuateTo = m_account.evaluateAll(m_topsData, currencyTo, m_exchange);
        log("  valuate" + currencyTo + "=" + valuateTo + " " + currencyTo);
        m_maySyncAccount = false;
    }


    //-------------------------------------------------------------------------------
    public class StopTask extends TaskQueueProcessor.SinglePresenceTask {
        public StopTask() {}

        @Override public TaskQueueProcessor.DuplicateAction isDuplicate(TaskQueueProcessor.BaseOrderTask other) {
            TaskQueueProcessor.DuplicateAction duplicate = super.isDuplicate(other);
            if (duplicate != null) {
                duplicate = TaskQueueProcessor.DuplicateAction.REMOVE_ALL_AND_PUT_AS_FIRST;
            }
            return duplicate;
        }

        @Override public void process() throws Exception {
            onStopRequested();
        }
    }


    //-------------------------------------------------------------------------------
    public class ResetTask extends TaskQueueProcessor.SinglePresenceTask {
        public ResetTask() {}
        @Override public void process() throws Exception {
            onResetRequested();
        }
    }


    //-------------------------------------------------------------------------------
    public class ExecTask extends TaskQueueProcessor.BaseOrderTask {
        private final Exec m_exec;

        @Override public TaskQueueProcessor.DuplicateAction isDuplicate(TaskQueueProcessor.BaseOrderTask other) { return null; }
        public ExecTask(Exec exec) {
            m_exec = exec;
        }
        @Override public void process() throws Exception {
            processExec(m_exec);
        }
    }


    //-------------------------------------------------------------------------------
    public class AccountTask extends TaskQueueProcessor.BaseOrderTask {
        private final AccountData m_accountData;

        @Override public TaskQueueProcessor.DuplicateAction isDuplicate(TaskQueueProcessor.BaseOrderTask other) { return null; }
        public AccountTask(AccountData accountData) {
            m_accountData = accountData;
        }
        @Override public void process() throws Exception {
            processAccount(m_accountData);
        }
    }
}
