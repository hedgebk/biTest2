package bthdg.tres;

import bthdg.IIterationContext;
import bthdg.exch.OrderData;
import bthdg.exch.OrderSide;
import bthdg.exch.Pair;
import bthdg.exch.TradeData;
import bthdg.osc.BaseExecutor;
import bthdg.osc.TaskQueueProcessor;
import bthdg.ws.IWs;

import java.util.List;

public class TresExecutor extends BaseExecutor {
    private static final long MIN_ORDER_LIVE_TIME = 6000;
    private static final double OUT_OF_MARKET_THRESHOLD = 0.5;
    private static final long MIN_REPROCESS_DIRECTION_TIME = 5000;
    private static final double ORDER_SIZE_TOLERANCE = 0.1;
    private static final double MIN_ORDER_SIZE = 0.01; // btc
    public static final double USE_FUNDS_FROM_AVAILABLE = 0.95; // 95%

    private final TresExchData m_exchData;
    private TresState m_state = TresState.NONE;
    private OrderData m_order;

    @Override protected long minOrderLiveTime() { return MIN_ORDER_LIVE_TIME; }
    @Override protected double outOfMarketThreshold() { return OUT_OF_MARKET_THRESHOLD; }
    @Override protected double minOrderSizeToCreate() { return MIN_ORDER_SIZE; }
    @Override protected double useFundsFromAvailable() { return USE_FUNDS_FROM_AVAILABLE; }
    @Override protected void onOrderPlace(OrderData placeOrder) { m_order = placeOrder; }
    @Override protected boolean haveNotFilledOrder() { return (m_order != null) && !m_order.isFilled(); }

    public TresExecutor(TresExchData exchData, IWs ws, Pair pair) {
        super(ws, pair);
        m_orderPriceMode = OrderPriceMode.MID;
        m_exchData = exchData;
        if (!exchData.m_tres.m_logProcessing) {
            Thread thread = new Thread(this);
            thread.setName("TresExecutor");
            thread.start();
        }
    }

    @Override protected void addTask(TaskQueueProcessor.IOrderTask task) {
        if (m_exchData.m_tres.m_logProcessing) {
            throw new RuntimeException("addTask() not applicable for logProcessing mode");
        } else {
            super.addTask(task);
        }
    }

    @Override protected void gotTrade(TradeData tradeData) throws Exception {
        log("TresExecutor.gotTrade() tData=" + tradeData);

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
        setState(m_state.onDirection(this));
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
            if (error == null) {
                log("   order cancelled OK: " + m_order);
                m_order = null;
                // here we may have desync case - we sent order; the order is partially executed; but we do not know this yet;
                // we cancel order; internally release funds performed, but since part of order is already executed - we may have
                // account mismatch here - resync account to prevent.
                initAccount();
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
        throw new Exception("not implemented");
    }

    @Override protected List<OrderData> getAllOrders() {
        throw new RuntimeException("not implemented");
//        return null;
    }

    @Override protected IIterationContext.BaseIterationContext checkLiveOrders() throws Exception {
        throw new Exception("not implemented");
//        return null;
    }

    @Override protected boolean hasOrdersWithMatchedPrice(double tradePrice) {
        double ordPrice = m_order.m_price;
        boolean ret = tradePrice == ordPrice;
        log(" hasOrdersWithMatchedPrice() tradePrice=" + tradePrice + ", orderPrice=" + ordPrice + ";  ret=" + ret);
        return ret;
    }

    @Override protected void checkOrdersOutOfMarket() throws Exception {
        throw new Exception("not implemented");
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
}
