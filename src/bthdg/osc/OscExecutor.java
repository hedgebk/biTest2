package bthdg.osc;

import bthdg.IIterationContext;
import bthdg.exch.Direction;
import bthdg.exch.OrderData;
import bthdg.exch.OrderSide;
import bthdg.exch.TradeDataLight;
import bthdg.util.Utils;
import bthdg.ws.IWs;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

class OscExecutor extends BaseExecutor {
    private static final double MIN_ORDER_SIZE = 0.1; // btc
    public static final int MIN_REPROCESS_DIRECTION_TIME = 4500;
    public static final double OPPOSITE_DIRECTION_FACTOR = 0.7; // -30%
    public static final double[] AVG_PRICE_PERIOD_RATIOS = new double[] { 0.4, 1.0, 1.7, 2.5, 3.4 };
    private static final long MIN_ORDER_LIVE_TIME = 9000;
    // AvgPriceDirectionAdjuster
    public static final double AVG_PRICE_TREND_TOLERANCE = 0.04;
    private static final double DIRECTION_ADJUSTED_CENTER_TOLERANCE_RATIO = 3;
    public static final double DIRECTION_ADJUSTED_TREND_TOLERANCE = 0.014;
    // AvgStochDirectionAdjuster
    public static final double AVG_STOCH_TREND_THRESHOLD = 0.025;
    public static final double DIRECTION_TREND_THRESHOLD = 0.02;
    private static final double ADJUST_DIRECTION_LEVEL1 = 0.08; // [0 ... LEVEL1]      ->  [0.8 ... 1]x    [LEVEL1_TO ... 1]
    private static final double ADJUST_DIRECTION_LEVEL2 = 0.25; // [LEVEL1 ... LEVEL2] -> +[0 ... 0.6]    +[0 ... ADJUST_DIRECTION_LEVEL2_TO]
                                                                // [LEVEL2 ... 1]      -> +[0.6 ... 1]    +[ADJUST_DIRECTION_LEVEL2_TO ... 1]
    private static final double ADJUST_DIRECTION_LEVEL1_TO = 0.7;
    private static final double ADJUST_DIRECTION_LEVEL2_TO = 0.5;
    public static final int AVG_STOCH_COUNTER_POINTS = 13;
    private static final double AVG_STOCH_DELTA_THREZHOLD = 0.0009;
    private static final double AVG_STOCH_THREZHOLD_LEVEL_BOOST = 0.4; // +40%
    public static final double OUT_OF_MARKET_THRESHOLD = 0.5;

    protected int m_direction;

    private OscState m_state = OscState.NONE;
    private OrderData m_order;
    private List<CloseOrderWrapper> m_closeOrders = new ArrayList<CloseOrderWrapper>();
    private final Utils.AverageCounter[] m_avgPriceCounters = new Utils.AverageCounter[AVG_PRICE_PERIOD_RATIOS.length];
    final TrendCounter m_priceTrendCounter;
    private final AvgStochDirectionAdjuster m_avgStochDirectionAdjuster;
    private final AvgPriceDirectionAdjuster m_avgPriceDirectionAdjuster;
    private NoTradesWatcher m_noTradesWatcher = new NoTradesWatcher();

    @Override protected double minOrderSizeToCreate() { return MIN_ORDER_SIZE; }
    @Override protected void onOrderPlace(OrderData placeOrder, long tickAge, double buy, double sell, TopSource topSource) { m_order = placeOrder; }
    @Override protected long minOrderLiveTime() { return MIN_ORDER_LIVE_TIME; }
    @Override protected double outOfMarketThreshold() { return OUT_OF_MARKET_THRESHOLD; }
    @Override protected boolean haveNotFilledOrder() { return (m_order != null) && !m_order.isFilled(); }
    @Override protected double useFundsFromAvailable() { return Osc.USE_FUNDS_FROM_AVAILABLE; }

    public OscExecutor(IWs ws) {
        super(ws, Osc.PAIR, Osc.AVG_BAR_SIZE);
        m_avgStochDirectionAdjuster = new AvgStochDirectionAdjuster();
        m_avgPriceDirectionAdjuster = new AvgPriceDirectionAdjuster();
        for (int i = 0; i < AVG_PRICE_PERIOD_RATIOS.length; i++) {
            double avgPricePeriodRatio = AVG_PRICE_PERIOD_RATIOS[i];
            m_avgPriceCounters[i] = new Utils.FadingAverageCounter((long) (Osc.AVG_BAR_SIZE * avgPricePeriodRatio));
        }
        m_priceTrendCounter = new TrendCounter(Osc.AVG_BAR_SIZE);
        Thread thread = new Thread(this);
        thread.setName("OscExecutor");
        thread.start();
    }

    public void update(int delta) {
        synchronized (this) {
            int direction = m_direction;
            m_direction += delta;
            log("update(delta=" + delta + ") direction updated from " + direction + " -> " + m_direction);
            update();
        }
    }

    @Override protected void initImpl() throws Exception {
        log("OscExecutor.initImpl()................");
        super.initImpl();
    }

    private void setState(OscState state) {
        if ((state != null) && (m_state != state)) {
            log("STATE changed from " + m_state + " to " + state);
            m_state = state;
        }
    }

    @Override protected void recheckDirection() throws Exception {
        log("OscExecutor.recheckDirection() m_direction=" + m_direction);
        setState(m_state.onDirection(this));
    }

    @Override protected void gotTop() throws Exception {
//            log("OscExecutor.gotTop() buy=" + buy + "; sell=" + sell);
//            log(" topsData =" + m_topsData);
        m_state.onTop(this);
    }

    @Override protected void gotTrade(TradeDataLight tData) throws Exception {
        log("OscExecutor.gotTrade() tData=" + tData);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < AVG_PRICE_PERIOD_RATIOS.length; i++) {
            double avg = m_avgPriceCounters[i].get();
            builder.append(" avg").append(i + 1).append("=").append(avg);
        }
        Double last = m_priceTrendCounter.getLast();
        Double oldest = m_priceTrendCounter.getOldest();
        double trend = last - oldest;
        log(builder.toString() + "; last=" + last + "; oldest=" + oldest + "; trend=" + trend);

        IIterationContext.BaseIterationContext iContext = checkCloseOrdersStateIfNeeded(tData, null);
        OscState newState = m_state.onTrade(this, tData, iContext);
        setState(newState);

        if (m_feeding) {
            long passed = System.currentTimeMillis() - m_lastProcessDirectionTime;
            if (passed > Math.min(Osc.AVG_BAR_SIZE, MIN_REPROCESS_DIRECTION_TIME)) {
                log(" no check direction for long time " + passed + "ms - postRecheckDirection");
                postRecheckDirection();
            }
        }
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
                if (ratioDistanceFromOne < Osc.ORDER_SIZE_TOLERANCE) {
                    log("        order Sizes are very close - do not cancel existing order (tolerance=" + Osc.ORDER_SIZE_TOLERANCE + ")");
                    return 0;
                }
            }
        }
        return orderSize;
    }

    @Override protected boolean checkNoOpenOrders() {
        if (m_order == null) { // we should not have open order at this place
            return true;
        } else {
            log("warning: order still exist - switch to ERROR state: " + m_order);
            return false;
        }
    }

    @Override protected boolean cancelOtherOrdersIfNeeded(OrderSide needOrderSide, double notEnough) throws Exception {
        double cumCancelOrdersSize = getCumCancelOrdersSize(needOrderSide);
        log("      notEnough="+notEnough+" for needOrderSide=" + needOrderSide + " we have cumCancelOrdersSize=" + Utils.format8(cumCancelOrdersSize));
        if (cumCancelOrdersSize > 0) {
            cancelFarestSameDirectionCloseOrder(needOrderSide);
            log("order cancel was attempted. time passed. posting recheck direction");
            postRecheckDirection();
            return true; // cancel performed
        }
        return false;
    }

    private IIterationContext.BaseIterationContext checkCloseOrdersStateIfNeeded(TradeDataLight tData, IIterationContext.BaseIterationContext inContext) throws Exception {
        IIterationContext.BaseIterationContext iContext = inContext;
        if (!m_closeOrders.isEmpty()) {
            double tradePrice = tData.m_price;
            boolean gotMatchedPrice = false;
            for (CloseOrderWrapper closeOrderWrapper : m_closeOrders) {
                OrderData closeOrder = closeOrderWrapper.m_closeOrder;
                if (closeOrder.acceptPrice(tradePrice)) {
                    log("trade price " + tData + " matched with close order " + closeOrder);
                    gotMatchedPrice = true;
                    break;
                }
            }
            if (gotMatchedPrice) {
                iContext = checkCloseOrdersState(iContext);
            }
        }
        return iContext;
    }

    @Override protected IIterationContext.BaseIterationContext checkLiveOrders() throws Exception {
        log("checkLiveOrders()");
        IIterationContext.BaseIterationContext iContext = null;
        if (!m_closeOrders.isEmpty()) {
            iContext = checkCloseOrdersState(null);
        }
        if (m_order != null) {
            iContext = getLiveOrdersContextIfNeeded(iContext);
            setState(checkOrderState(iContext));
        }
        return iContext;
    }

    private IIterationContext.BaseIterationContext checkCloseOrdersState(IIterationContext.BaseIterationContext inContext) throws Exception {
        IIterationContext.BaseIterationContext iContext = getLiveOrdersContextIfNeeded(inContext);
        log(" checking close orders state...");
        boolean someCloseFilled = false;
        for( ListIterator<CloseOrderWrapper> it = m_closeOrders.listIterator(); it.hasNext(); ) {
            CloseOrderWrapper closeOrderWrapper = it.next();
            OrderData closeOrder = closeOrderWrapper.m_closeOrder;
            closeOrder.checkState(iContext, m_exchange, m_account,
                    null, // TODO - implement exec listener, add partial support - to fire partial close orders
                    null);
            log("  closeOrder state checked: " + closeOrder);
            if (closeOrder.isFilled()) {
                OrderData openOrder = closeOrderWrapper.m_order;
                log("$$$$$$   closeOrder FILLED: " + closeOrder + "; open order: " + openOrder);
                it.remove();
                someCloseFilled = true;
            }
        }
        if(someCloseFilled) {
            log("some close order filled - resync account");
            // here we may have desync case - resync account to prevent.
            initAccount();
            log("post recheck direction");
            postRecheckDirection();
            logValuate();
        }
        return iContext;
    }

    @Override public void onTrade(TradeDataLight tData) {
        m_noTradesWatcher.onTrade();
        long timestamp = tData.m_timestamp;
        for (int i = 0; i < AVG_PRICE_PERIOD_RATIOS.length; i++) {
            m_avgPriceCounters[i].justAdd(timestamp, tData.m_price);
        }
        m_priceTrendCounter.addPoint(timestamp, tData.m_price);
        super.onTrade(tData);
    }


    @Override protected double getDirectionAdjusted() { // [-1 ... 1]
        double directionAdjustedIn = ((double)m_direction) / Osc.PHASES / Osc.BAR_SIZES.length; // directionAdjusted  [-1 ... 1]
        log("processDirection() direction=" + m_direction + "; directionAdjustedIn=" + directionAdjustedIn);

        if ((directionAdjustedIn < -1) || (directionAdjustedIn > 1)) {
            log("ERROR: invalid directionAdjusted=" + directionAdjustedIn);
            if (directionAdjustedIn < -1) {
                directionAdjustedIn = -1;
            }
            if (directionAdjustedIn > 1) {
                directionAdjustedIn = 1;
            }
        }

        double directionAdjusted = m_avgStochDirectionAdjuster.adjustDirection(directionAdjustedIn);
        if (directionAdjusted != 0) { // do not change if zeroed
            directionAdjusted = m_avgPriceDirectionAdjuster.adjustDirection(directionAdjustedIn, directionAdjusted);
            directionAdjusted = m_noTradesWatcher.adjustDirection(directionAdjusted);
        }
        return directionAdjusted;
    }

    private void cancelFarestSameDirectionCloseOrder(OrderSide needOrderSide) throws Exception {
        log("  cancelFarestSameDirectionCloseOrder(needOrderSide=" + needOrderSide + ") size=" + m_closeOrders.size());
        if (!m_closeOrders.isEmpty() && (needOrderSide != null)) {
            Double farestPrice = null;
            CloseOrderWrapper farestCloseOrder = null;
            for (CloseOrderWrapper next : m_closeOrders) {
                OrderData closeOrder = next.m_closeOrder;
                OrderSide closeOrderSide = closeOrder.m_side;
                log("   next closeOrder " + closeOrder);
                if (closeOrderSide == needOrderSide) {
                    double closeOrderPrice = closeOrder.m_price;
                    if ((farestPrice == null) || (needOrderSide.isBuy() ? (farestPrice > closeOrderPrice) : (farestPrice < closeOrderPrice))) {
                        farestPrice = closeOrderPrice;
                        log("    farestPrice updated " + farestPrice);
                        farestCloseOrder = next;
                    }
                }
            }
            if (farestCloseOrder != null) {
                log("   got farestCloseOrder " + farestCloseOrder);
                OrderData closeOrder = farestCloseOrder.m_closeOrder;
                log("    need cancel existing close order: " + closeOrder);
                String error = cancelOrder(closeOrder);
                if (error == null) {
                    m_closeOrders.remove(farestCloseOrder);
                } else {
                    // basically we may have no such order error here - when executed concurrently
                    log("ERROR canceling close order: " + error + "; " + m_order);
                    log("we had problems canceling close orders - re-check close orders state...");
                    checkCloseOrdersState(null);
                }
            }
        }
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
                return STATE_NONE;
            } else {
                log("ERROR in cancel order: " + error + "; " + m_order);
                return STATE_ERROR;
            }
        }
        return STATE_NO_CHANGE;
    }

    private double getCumCancelOrdersSize(OrderSide haveOrderSide) {
        double cumCancelOrdersSize = 0;
        for (CloseOrderWrapper close : m_closeOrders) {
            OrderData closeOrder = close.m_closeOrder;
            OrderSide cancelOrderSide = closeOrder.m_side;
            if (haveOrderSide == cancelOrderSide) {
                double size = closeOrder.remained();
                cumCancelOrdersSize += size;
            }
        }
        return cumCancelOrdersSize;
    }

    @Override public int processTrade(TradeDataLight tData, IIterationContext.BaseIterationContext inContext) throws Exception {
        log("processTrade(tData=" + tData + ") m_order=" + m_order);
        return super.processTrade(tData, inContext);
    }

    @Override protected int checkOrdersState(IIterationContext.BaseIterationContext iContext) throws Exception {
        OscState oscState = checkOrderState(iContext);
        return OscState.toCode(oscState);
    }

    @Override protected void checkOrdersOutOfMarket() throws Exception {
        if (haveNotFilledOrder()) {
            int ret = checkOrderOutOfMarket(m_order);
            processOrderOutOfMarket(ret);
        }
    }

    @Override protected boolean hasOrdersWithMatchedPrice(double tradePrice) {
        if (m_order != null) {
            double ordPrice = m_order.m_price;
            log(" hasOrdersWithMatchedPrice() tradePrice=" + tradePrice + ", orderPrice=" + ordPrice);
            return (tradePrice == ordPrice);
        }
        return false;
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

    private OscState checkOrderState(IIterationContext.BaseIterationContext iContext) throws Exception {
        m_order.checkState(iContext, m_exchange, m_account,
                null, // TODO - implement exec listener, add partial support - to fire partial close orders
                null);
        log("  order state checked: " + m_order);

        if (m_order.isFilled()) {
            m_orderPlaceAttemptCounter = 0;
            log("$$$$$$   order FILLED: " + m_order);
            logValuate();
            if (Osc.DO_CLOSE_ORDERS) {
                OrderSide orderSide = m_order.m_side;
                OrderSide closeSide = orderSide.opposite();
                double closePrice = m_order.m_price + (orderSide.isBuy() ? Osc.CLOSE_PRICE_DIFF : -Osc.CLOSE_PRICE_DIFF);
                // todo: correct price if it is not outside bid-ask
                double closeSize = m_order.m_amount * Osc.USE_FUNDS_FROM_AVAILABLE; // do not use ALL available funds

                OrderData closeOrder = new OrderData(m_pair, closeSide, closePrice, closeSize);
                log("  placing closeOrder=" + closeOrder);

                boolean placed = placeOrderToExchange(m_exchange, closeOrder);
                if (placed) {
                    m_closeOrders.add(new CloseOrderWrapper(closeOrder, m_order));
                    m_order = null;
                    return OscState.NONE;
                } else {
                    m_order = null;
                    log("ERROR placing closeOrder=" + closeOrder);
                    return OscState.ERROR;
                }
            } else {
                m_order = null;
                return OscState.NONE;
            }
        } else {
            log("   order not yet FILLED: " + m_order);
        }
        return null; // no change
    }

    @Override protected void processTopInt() throws Exception {
        int ret = checkOrderOutOfMarket(m_order);
        processOrderOutOfMarket(ret);
    }

    @Override protected List<OrderData> getAllOrders() {
        boolean isNotNullOrder = (m_order != null);
        int size = (isNotNullOrder ? 1 : 0) + m_closeOrders.size();
        if (size > 0) {
            List<OrderData> ret = new ArrayList<OrderData>(size);
            if (isNotNullOrder) {
                ret.add(m_order);
            }
            if(!m_closeOrders.isEmpty()) {
                for (CloseOrderWrapper wrapper : m_closeOrders) {
                    OrderData closeOrder = wrapper.m_closeOrder;
                    ret.add(closeOrder);
                }
            }
            return ret;
        }
        return null;
    }

    @Override protected List<String> cancelAllOrders() throws Exception {
        List<String> cancelledOrdIds = null;
        if (m_order != null) {
            log("  we have existing order, will cancel: " + m_order);
            String error = cancelOrder(m_order);
            if (error == null) {
                cancelledOrdIds = new ArrayList<String>();
                cancelledOrdIds.add(m_order.m_orderId);
                m_order = null;
            } else {
                log("error in cancel order: " + error + "; " + m_order);
                m_order = null;
            }
        }
        if (!m_closeOrders.isEmpty()) {
            log("  we have existing close orders, will cancel");
            for (ListIterator<CloseOrderWrapper> iterator = m_closeOrders.listIterator(); iterator.hasNext(); ) {
                CloseOrderWrapper next = iterator.next();
                OrderData closeOrder = next.m_closeOrder;
                log("  need cancel existing close order: " + closeOrder);
                String error = cancelOrder(closeOrder);
                if (error != null) {
                    if(cancelledOrdIds == null) {
                        cancelledOrdIds = new ArrayList<String>();
                    }
                    cancelledOrdIds.add(closeOrder.m_orderId);
                    log("error canceling close order: " + error + "; " + m_order);
                }
                iterator.remove();
            }
        }
        return cancelledOrdIds;
    }

    @Override public void stop() throws Exception {
        super.stop();
        m_taskQueueProcessor.stop();
        log("stop() finished");
    }

    public void onAvgStoch(double avgStoch) {
        m_avgStochDirectionAdjuster.updateAvgStoch(avgStoch);
        m_feeding = true; // preheat finished, got some bars
    }

    //-------------------------------------------------------------------------------
    public static class TrendCounter extends Utils.AverageCounter {
        private final Utils.FadingAverageCounter m_avgPriceCounter;

        public TrendCounter(long size) {
            super(size);
            m_avgPriceCounter = new Utils.FadingAverageCounter(size);
        }

        public boolean isTrendUp() {
            double trend = getTrend();
            return (trend > 0);
        }

        public double getTrend() {
            Double last = getLast();
            Double oldest = getOldest();
            return last - oldest;
        }

        public void addPoint(long timestamp, double value) {
            double avg = m_avgPriceCounter.add(timestamp, value);
            justAdd(timestamp, avg);
        }
    }

    //-------------------------------------------------------------------------------
    private class AvgStochDirectionAdjuster {
        private Utils.ArrayAverageCounter m_avgStochCounter = new Utils.ArrayAverageCounter(AVG_STOCH_COUNTER_POINTS);
        private Utils.ArrayAverageCounter m_avgStochCounter2 = new Utils.ArrayAverageCounter(3);
        private Utils.ArrayAverageCounter m_avgStochDeltaBlender = new Utils.ArrayAverageCounter(10);
        private Double m_prevBlend;
        private Double m_prevBlend2;
        private TrendWatcher.TrendWatcherDouble m_avgStochTrendWatcher = new TrendWatcher.TrendWatcherDouble(AVG_STOCH_TREND_THRESHOLD);
        private TrendWatcher.TrendWatcherDouble m_directionTrendWatcher = new TrendWatcher.TrendWatcherDouble(DIRECTION_TREND_THRESHOLD);
        private Double m_lastAvgStochDeltaBlend;
        private double m_minAvgStochDeltaBlend = 0;
        private double m_maxAvgStochDeltaBlend = 0;

        public void updateAvgStoch(double avgStoch) {
            double blend = m_avgStochCounter.add(avgStoch);
            boolean full = m_avgStochCounter.m_full;
            log("updateAvgStoch(avgStoch=" + avgStoch + ") blend=" + blend + "; full=" + full);
            if (full) {
                Direction prevDirection = m_avgStochTrendWatcher.m_direction;
                m_avgStochTrendWatcher.update(blend);
                Direction newDirection = m_avgStochTrendWatcher.m_direction;
                log(" peak=" + m_avgStochTrendWatcher.m_peak + "; direction=" + newDirection);
                if ((prevDirection != null) && (newDirection != null) && (prevDirection != newDirection)) {
                    log("  direction trend changed from " + prevDirection + "to " + newDirection);
                }
                m_prevBlend = blend;
            }
            double blend2 = m_avgStochCounter2.add(avgStoch);
            boolean full2 = m_avgStochCounter2.m_full;
            log(" avgStoch=" + avgStoch + "; blend2=" + blend2 + "; full2=" + full2);
            if (full2) {
                if (m_prevBlend2 != null) {
                    double avgStochDelta = blend2 - m_prevBlend2;
                    log("  m_prevBlend2=" + m_prevBlend2 + "; blend2=" + blend2 + "; avgStochDelta=" + Utils.format8(avgStochDelta));
                    double avgStochDeltaBlend = m_avgStochDeltaBlender.add(avgStochDelta);
                    if (m_avgStochDeltaBlender.m_full) {
                        log("   avgStochDeltaBlend=" + Utils.format8(avgStochDeltaBlend));
                        m_lastAvgStochDeltaBlend = avgStochDeltaBlend;
                    }
                }
                m_prevBlend2 = blend2;
            }
        }

        public double adjustDirection(double directionAdjusted) { // directionAdjusted  [-1 ... 1]
            log("  AvgStochDirectionAdjuster.adjustDirection() adjustDirection=" + directionAdjusted);
            m_directionTrendWatcher.update(directionAdjusted);
            Double peakDirection = m_directionTrendWatcher.m_peak;
            double ret = directionAdjusted;
            Double peakAvgStoch = m_avgStochTrendWatcher.m_peak;
            if ((peakAvgStoch != null) && (peakDirection != null)) {
                Direction directionDirection = m_directionTrendWatcher.m_direction;
                Direction avgStochDirection = m_avgStochTrendWatcher.m_direction;
                log("  directions: avgStochDirection=" + avgStochDirection + " directionDirection=" + directionDirection );

                if (avgStochDirection != directionDirection) {
                    ret = ret / 2;
                    log("   opposite directions: halved from " + directionAdjusted + " to " + ret);
                }

                double distanceFromPeak = avgStochDirection.applyDirection(m_prevBlend - peakAvgStoch);
                log("boost?: distanceFromPeak=" + distanceFromPeak + "; direction=" + avgStochDirection + "; peakAvgStoch=" + peakAvgStoch + "; m_prevBlend=" + m_prevBlend);
                // distanceFromPeak  [0 ... 1]
                Double boosted;
                if (distanceFromPeak < ADJUST_DIRECTION_LEVEL1) {          //[0   ... 0.1]
//                    double q = LEVEL1 - distanceFromPeak; //[0.1 ... 0  ]
//                    double r = q / LEVEL1;                //[1   ... 0  ]
//                    double w = r * (1 - LEVEL1_TO);       //[0.2 ... 0  ]
//                    double e = 1 - w;                     //[0.8 ... 1  ]
//log("  first range: e=" + e);
//                    ret = directionAdjusted * e;
                    log("  first range: no change");
                    boosted = null;
                } else if (distanceFromPeak < ADJUST_DIRECTION_LEVEL2) {   //[0.1 ... 0.2]
                    double q = distanceFromPeak - ADJUST_DIRECTION_LEVEL1; //[0   ... 0.1]
                    double r = q / (ADJUST_DIRECTION_LEVEL2 - ADJUST_DIRECTION_LEVEL1);     //[0   ... 1  ]
                    double w = r * ADJUST_DIRECTION_LEVEL2_TO;             //[0   ... 0.6]
                    log("  second range: w=" + w);
                    boosted = boostDirection(ret, w, avgStochDirection);
                } else {                                  //[0.2 ... 1]
                    double q = distanceFromPeak - ADJUST_DIRECTION_LEVEL2; //[0 ... 0.8]
                    double r  = q / (1 - ADJUST_DIRECTION_LEVEL2);         //[0 ... 1  ]
                    double w = r * (1 - ADJUST_DIRECTION_LEVEL2_TO);       //[0 ... 0.4]
                    double e = ADJUST_DIRECTION_LEVEL2_TO + w;             //[0.6 ... 1]
                    log("  third range: e=" + e);
                    boosted = boostDirection(ret, e, avgStochDirection);
                }
                if (boosted != null) {
                    log(" boosted from " + ret + " to " + boosted);
                    ret = boosted;
                }
            }

            if (m_lastAvgStochDeltaBlend != null) {
                m_minAvgStochDeltaBlend = Math.min(m_minAvgStochDeltaBlend, m_lastAvgStochDeltaBlend);
                m_maxAvgStochDeltaBlend = Math.max(m_maxAvgStochDeltaBlend, m_lastAvgStochDeltaBlend);
                log("lastAvgStochDeltaBlend=" + Utils.format8(m_lastAvgStochDeltaBlend)
                        + " minAvgStochDeltaBlend=" + Utils.format8(m_minAvgStochDeltaBlend)
                        + " maxAvgStochDeltaBlend=" + Utils.format8(m_maxAvgStochDeltaBlend));
                double boosted;
                double add;
                if (m_lastAvgStochDeltaBlend > 0) {
                    double distanceToOne = 1 - ret;
                    if (m_lastAvgStochDeltaBlend > AVG_STOCH_DELTA_THREZHOLD) {
                        double topAvgStochDeltaBlend = Math.max(m_maxAvgStochDeltaBlend, 2 * AVG_STOCH_DELTA_THREZHOLD);
                        add = (m_lastAvgStochDeltaBlend - AVG_STOCH_DELTA_THREZHOLD)
                                / (topAvgStochDeltaBlend - AVG_STOCH_DELTA_THREZHOLD)
                                * (1 - AVG_STOCH_THREZHOLD_LEVEL_BOOST)
                                + AVG_STOCH_THREZHOLD_LEVEL_BOOST;
                        log(" zoneA: add=" + Utils.format8(add) + "; distanceToOne=" + Utils.format8(distanceToOne));
                    } else {
                        add = m_lastAvgStochDeltaBlend / AVG_STOCH_DELTA_THREZHOLD * AVG_STOCH_THREZHOLD_LEVEL_BOOST;
                        log(" zoneB: add=" + Utils.format8(add) + "; distanceToOne=" + Utils.format8(distanceToOne));
                    }
                    boosted = ret + distanceToOne * add;
                } else {
                    double distanceToOne = 1 + ret;
                    if (m_lastAvgStochDeltaBlend < -AVG_STOCH_DELTA_THREZHOLD) {
                        double bottomAvgStochDeltaBlend = Math.min(m_minAvgStochDeltaBlend, -2 * AVG_STOCH_DELTA_THREZHOLD);
                        add = (m_lastAvgStochDeltaBlend - AVG_STOCH_DELTA_THREZHOLD)
                                / (bottomAvgStochDeltaBlend - AVG_STOCH_DELTA_THREZHOLD)
                                * (1 - AVG_STOCH_THREZHOLD_LEVEL_BOOST)
                                + AVG_STOCH_THREZHOLD_LEVEL_BOOST;
                        log(" zoneC: add=" + Utils.format8(add) + "; distanceToOne=" + Utils.format8(distanceToOne));
                    } else {
                        add = -m_lastAvgStochDeltaBlend / AVG_STOCH_DELTA_THREZHOLD * AVG_STOCH_THREZHOLD_LEVEL_BOOST;
                        log(" zoneD: add=" + Utils.format8(add) + "; distanceToOne=" + Utils.format8(distanceToOne));
                    }
                    boosted = ret - distanceToOne * add;
                }
                log(" boosted from " + ret + " to " + boosted);
                ret = boosted;
            }
            return ret;
        }

        private double boostDirection(double directionAdjusted, double rate, Direction avgStochDirection) {
            Direction directionAdjustedDirection = Direction.get(directionAdjusted);
            double directionAbs = Math.abs(directionAdjusted);
            double newDirectionAbs;
            if (directionAdjustedDirection == avgStochDirection) { // same directions
                double distanceToOne = 1 - directionAbs;
                double plus = distanceToOne * rate;
                newDirectionAbs = directionAbs + plus;
                log("   boostDirection() same directions: distanceToOne=" + distanceToOne + "; plus=" + plus);
            } else { // opposite directions
                double distanceToOne = 1 + directionAbs;
                double minus = distanceToOne * rate;
                newDirectionAbs = directionAbs - minus;
                log("   boostDirection() opposite directions: distanceToOne=" + distanceToOne + "; minus=" + minus);
            }
            double ret = directionAdjustedDirection.applyDirection(newDirectionAbs);
            log("    ret=" + ret);
            return ret;
        }
    }

    //-------------------------------------------------------------------------------
    private class AvgPriceDirectionAdjuster {
        private TrendWatcher.TrendWatcherDouble[] m_avgPriceTrendWatchers = new TrendWatcher.TrendWatcherDouble[AVG_PRICE_PERIOD_RATIOS.length];
        private TrendWatcher.TrendWatcherDouble m_directionAdjustedTrendWatcher = new TrendWatcher.TrendWatcherDouble(DIRECTION_ADJUSTED_TREND_TOLERANCE) {
            @Override protected double getTolerance(double value) { // [-1 ... 0 ... 1]
                double absValue = Math.abs(value); // [1 ... 0 ... 1]
                double distanceToEdge = 1 - absValue; // [0 ... 1 ... 0]
                return m_tolerance * (1 + distanceToEdge * (DIRECTION_ADJUSTED_CENTER_TOLERANCE_RATIO - 1));
            }
        };

        public double adjustDirection(double directionAdjustedIn, double directionAdjusted) { // directionAdjusted  [-1 ... 1]
            double ret = directionAdjusted;

            m_directionAdjustedTrendWatcher.update(directionAdjustedIn);
            Direction directionAdjustedDirection = m_directionAdjustedTrendWatcher.m_direction;

            for (int i = 0; i < AVG_PRICE_PERIOD_RATIOS.length; i++) {
                int index = i + 1;
                double avgPrice = m_avgPriceCounters[i].get();
                TrendWatcher.TrendWatcherDouble avgPriceTrendWatcher = m_avgPriceTrendWatchers[i];
                if (avgPriceTrendWatcher == null) {
                    avgPriceTrendWatcher = new TrendWatcher.TrendWatcherDouble(AVG_PRICE_TREND_TOLERANCE);
                    m_avgPriceTrendWatchers[i] = avgPriceTrendWatcher;
                }
                avgPriceTrendWatcher.update(avgPrice);
                Direction avgPriceDirection = avgPriceTrendWatcher.m_direction;
                log(" avgPriceDirection" + index + "=" + avgPriceDirection + "; directionAdjustedDirection=" + directionAdjustedDirection);

                if ((avgPriceDirection != null) && (directionAdjustedDirection != null) && (avgPriceDirection != directionAdjustedDirection)) {
                    if ((directionAdjustedDirection.isBackward() && avgPriceDirection.isForward() && (directionAdjustedIn < 0.8)) ||
                        (directionAdjustedDirection.isForward() && avgPriceDirection.isBackward() && (directionAdjustedIn > -0.8))) {
                        log("  opposite directions. avgPrice" + index + "=" + avgPrice + "; avgPricePeak" + index + "=" + avgPriceTrendWatcher.m_peak +
                                "; directionAdjusted=" + directionAdjustedIn + "; directionAdjustedPeak=" + m_directionAdjustedTrendWatcher.m_peak);
                        double directionChilled = ret * OPPOSITE_DIRECTION_FACTOR;
                        log("  direction chilled" + index + " from " + ret + " to " + directionChilled);
                        ret = directionChilled;
                    } else {
                        log("  skipped oppositeDirections - in edge top zone. avgPrice" + index + "=" + avgPrice + "; avgPricePeak" + index + "=" + avgPriceTrendWatcher.m_peak +
                                "; directionAdjusted=" + directionAdjustedIn + "; directionAdjustedPeak=" + m_directionAdjustedTrendWatcher.m_peak);
                    }
                }
            }
            return ret;
        }
    }

    //-------------------------------------------------------------------------------
    private class NoTradesWatcher {
        private long m_lastTradeTime = System.currentTimeMillis();
        private long m_checkPeriodEnd = 0;
        private long m_maxNoTradesPeriod = 0;
        private long m_lastNoTradesPeriod = 0;
        private Utils.ArrayAverageCounter m_avgNoTradesPeriod = new Utils.ArrayAverageCounter(3);

        public void onTrade() {
            long time = System.currentTimeMillis();
            long noTradesPeriod = time - m_lastTradeTime;
            if (noTradesPeriod > 1) { // skip processed one by one trades batch
                if (m_checkPeriodEnd > 0) {
                    m_maxNoTradesPeriod = Math.max(noTradesPeriod, m_maxNoTradesPeriod);
                    if (time > m_checkPeriodEnd) { // collecting stat
                        log("checkPeriodEnd passed for Period=" + m_lastNoTradesPeriod + "ms. m_maxNoTradesPeriod=" + m_maxNoTradesPeriod);
                        m_lastNoTradesPeriod = m_maxNoTradesPeriod;
                        m_avgNoTradesPeriod.add(m_lastNoTradesPeriod);
                        m_maxNoTradesPeriod = 0;
                        m_checkPeriodEnd = time + m_lastNoTradesPeriod; // wait new period
                    }
                } else {
                    m_checkPeriodEnd = time + 10000; // start with 10 sec period check
                    log("start with 10 sec period NoTrades check");
                }
                m_lastTradeTime = time;
            }
        }

        public double adjustDirection(double directionAdjusted) { // directionAdjusted  [-1 ... 1]
            double ret = directionAdjusted;
            double avgNoTradesPeriod = m_avgNoTradesPeriod.get();
            if (avgNoTradesPeriod > 10000) {
                log("  avgNoTradesPeriod=" + avgNoTradesPeriod);
                double rate = avgNoTradesPeriod / 10000;
                ret = directionAdjusted / rate;
                log("  direction chilledZ from " + directionAdjusted + " to " + ret);
            }
            return ret;
        }
    }

    //-------------------------------------------------------------------------------
    public static class CloseOrderWrapper {
        protected final OrderData m_closeOrder;
        protected final OrderData m_order;

        public CloseOrderWrapper(OrderData closeOrder, OrderData order) {
            m_closeOrder = closeOrder;
            m_order = order;
        }

        @Override public String toString() {
            return "CloseOrderWrapper[m_closeOrder=" + m_closeOrder + "; m_order=" + m_order + "]";
        }
    }
}
