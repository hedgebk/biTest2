package bthdg.tres;

import bthdg.IIterationContext;
import bthdg.exch.OrderData;
import bthdg.exch.OrderSide;
import bthdg.exch.Pair;
import bthdg.exch.TradeData;
import bthdg.osc.BaseExecutor;
import bthdg.osc.TaskQueueProcessor;
import bthdg.ws.IWs;

import java.util.Arrays;
import java.util.List;

public class TresExecutor extends BaseExecutor {
    private static final long MIN_ORDER_LIVE_TIME = 7000;
    private static final double OUT_OF_MARKET_THRESHOLD = 0.6;
    private static final long MIN_REPROCESS_DIRECTION_TIME = 12000;
    private static final double ORDER_SIZE_TOLERANCE = 0.3;
    private static final double MIN_ORDER_SIZE = 0.10; // btc
    public static final double USE_FUNDS_FROM_AVAILABLE = 0.95; // 95%

    private final TresExchData m_exchData;
    private TresState m_state = TresState.NONE;
    OrderData m_order;
    public int m_ordersPlaced;
    public int m_ordersFilled;
    public double m_tradeVolume;

    @Override protected long minOrderLiveTime() { return MIN_ORDER_LIVE_TIME; }
    @Override protected double outOfMarketThreshold() { return OUT_OF_MARKET_THRESHOLD; }
    @Override protected double minOrderSizeToCreate() { return MIN_ORDER_SIZE; }
    @Override protected double useFundsFromAvailable() { return USE_FUNDS_FROM_AVAILABLE; }
    @Override protected boolean haveNotFilledOrder() { return (m_order != null) && !m_order.isFilled(); }
    @Override protected TaskQueueProcessor createTaskQueueProcessor() { return new TresTaskQueueProcessor(); }

    @Override protected double getAvgOsc() { return m_exchData.calcAvgOsc(); }

    public TresExecutor(TresExchData exchData, IWs ws, Pair pair) {
        super(ws, pair, exchData.m_tres.m_barSizeMillis);
        m_exchData = exchData;
        m_orderPriceMode = OrderPriceMode.DEEP_MKT;
        if (!exchData.m_tres.m_logProcessing) {
            Thread thread = new Thread(this);
            thread.setName("TresExecutor");
            thread.start();
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

    @Override public void onTrade(TradeData tData) {
        TradeTask task = new TradeTask(tData);
        if ((m_order != null) && m_order.m_status.isActive()) {
            if (tData.m_price == m_order.m_price) { // same price trade - process first
                addTaskFirst(task);
                return;
            }
        }
        addTask(task);
    }

    @Override protected void gotTrade(TradeData tradeData) throws Exception {
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
        }
        return super.processDirection();
    }

    @Override protected double getDirectionAdjusted() {
        return m_exchData.getDirectionAdjusted();
    }

    @Override protected void gotTop() throws Exception {
        log("TresExecutor.gotTop() buy=" + m_buy + "; sell=" + m_sell);
        log(" topsData =" + m_topsData);
        m_state.onTop(this);
    }

    @Override protected double checkAgainstExistingOrders(OrderSide needOrderSide, double orderSizeIn) {
        double orderSize = orderSizeIn;
        if (m_order != null) { // we have already live order
            log("     we have already live order:" + m_order);
            OrderSide haveOrderSide = m_order.m_side;
            log("      needOrderSide=" + needOrderSide + "; haveOrderSide=" + haveOrderSide);
            if (needOrderSide == haveOrderSide) {
                double remained = m_order.remained();
                double orderSizeDiff = orderSize - remained;
                log("       remained=" + remained + "; orderSizeDiff=" + orderSizeDiff);
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
            }
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
                log("   order cancelled OK: " + m_order);
                m_order = null;

                postRecheckDirection();
                return STATE_NONE;
            } else {
                log("ERROR in cancel order: " + error + "; " + m_order);
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

    @Override protected void cancelAllOrders() throws Exception {
        log("  cancelAllOrders() " + m_order);
        if (m_order != null) {
            log("   we have existing order, will cancel: " + m_order);
            String error = cancelOrder(m_order);
            if (error == null) {
                m_order = null;
            } else {
                log("    error in cancel order: " + error + "; " + m_order);
                m_order = null;
            }
            log("    order cancel attempted - need sync account since part can be executed in the middle");
        }
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
            setState(checkOrderState(iContext));
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
        TresState state = checkOrderState(iContext);
        return TresState.toCode(state);
    }

    private TresState checkOrderState(IIterationContext.BaseIterationContext iContext) throws Exception {
        m_order.checkState(iContext, m_exchange, m_account,
                null, // TODO - implement exec listener, add partial support - to fire partial close orders
                null);
        log("  order state checked: " + m_order);

        if (m_order.isFilled()) {
            m_orderPlaceAttemptCounter = 0;
            log("$$$$$$   order FILLED: " + m_order);
            onOrderFilled();
            logValuate();
            m_order = null;
            return TresState.NONE;
        } else {
            log("   have order. not yet FILLED: " + m_order);
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

    @Override protected void onOrderPlace(OrderData placeOrder, long tickAge, double buy, double sell, TopSource topSource) {
        m_order = placeOrder;
        m_ordersPlaced++;
        m_exchData.addOrder(placeOrder, tickAge, buy, sell, topSource); // will call postFrameRepaint inside
    }

    private void onOrderFilled() {
        m_ordersFilled++;
        double amount = m_order.m_amount;
        m_tradeVolume += amount;
        m_exchData.m_tres.postFrameRepaint();
    }

    @Override protected void addTopDataPoint(TopDataPoint topDataPoint) {
        super.addTopDataPoint(topDataPoint);
        m_exchData.m_tres.postFrameRepaint();
    }
}
