package bthdg;

public class CrossData {
    private static final long TIME_TO_WAIT_PARTIAL = 15000;
    private static final long TOO_LONG_TO_WAIT_PARTIAL = 50000;
    private static final double MOVE_BRACKET_ORDER_MIN_PERCENTAGE = 0.1; // move brackets of price change in 10% from mkt price
    private static final double MIN_QTY_TO_FORK = 0.01;

    public final long m_start; // works like ID
    public ForkData m_forkData;
    public CrossState m_state;
    public final SharedExchangeData m_buyExch;
    public final SharedExchangeData m_sellExch;
    public OrderData m_buyOrder;
    public OrderData m_sellOrder;
    private boolean m_isOpenCross; // todo; add to serialize

    private static void log(String s) { Log.log(s); }

    public boolean isActive() { return (m_state == CrossState.BRACKETS_PLACED)
            || (m_state == CrossState.ONE_BRACKET_EXECUTED) || (m_state == CrossState.MKT_BRACKET_PLACED);}

    private static String format(double buy) { return Fetcher.format(buy); }

    @Override public String toString() {
        return "CrossData{" +
                m_buyExch.m_exchange + "->" + m_sellExch.m_exchange +
                " state=" + m_state +
                '}';
    }

    public CrossData(SharedExchangeData buyExch, SharedExchangeData sellExch) {
        m_buyExch = buyExch;
        m_sellExch = sellExch;
        m_state = CrossState.NONE;
        m_start = System.currentTimeMillis();
    }

    public void init(ForkData forkData, boolean isOpenCross) {
        m_forkData = forkData;
        m_isOpenCross = isOpenCross;

        BuySell buySell = calcBuySellPrices(forkData, "CrossData.init...");
        double buy = buySell.m_buy;
        double sell = buySell.m_sell;
        double amount = forkData.m_amount;
        TopData buyExchTop = m_buyExch.m_lastTop;
        TopData sellExchTop = m_sellExch.m_lastTop;

        m_buyOrder  = new OrderData(OrderSide.BUY,  buy, amount);
        m_sellOrder = new OrderData(OrderSide.SELL, sell, amount);

        log("buy exch  " + Utils.pad(m_buyExch.m_exchange.toString(), 8) + ": " +
                ExchangeData.ordersAndPricesStr(buyExchTop, m_buyOrder, null, null, null));
        log("sell exch " + Utils.pad(m_sellExch.m_exchange.toString(), 8) + ": " +
                ExchangeData.ordersAndPricesStr(sellExchTop, null, null, m_sellOrder, null));

        boolean success = m_buyExch.placeOrderBracket(m_buyOrder);
        if (success) {
            success = m_sellExch.placeOrderBracket(m_sellOrder);
            if (success) {
                setState(CrossState.BRACKETS_PLACED);
            } else {
                log("ERROR: " + m_sellExch.m_exchange.m_name + " placeBracket failed");
                setState(CrossState.ERROR);
            }
        } else {
            log("ERROR: " + m_buyExch.m_exchange.m_name + " placeBracket failed");
            setState(CrossState.ERROR);
        }
    }

    public void moveBracketsIfNeeded(final IterationContext iContext, final ForkData forkData) throws Exception {
        if( m_buyOrder.isPartiallyFilled() || m_sellOrder.isPartiallyFilled() ) {
            log("do not move brackets - one is partially filled - will be forked soon");
            return;
        }

        forkData.m_pairExData.doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                BuySell buySell = calcBuySellPrices(forkData, "moveBracketsIfNeeded...");
                double buy = buySell.m_buy;
                double sell = buySell.m_sell;
                TopData buyExchTop = m_buyExch.m_lastTop;
                TopData sellExchTop = m_sellExch.m_lastTop;
                double amount = forkData.m_amount;

                log("buy exch " + m_buyExch.m_exchange + ": " +
                        ExchangeData.ordersAndPricesStr(buyExchTop, m_buyOrder, buy, null, null));
                log("sell exch " + m_sellExch.m_exchange + ": " +
                        ExchangeData.ordersAndPricesStr(sellExchTop, null, null, m_sellOrder, sell));

                double buyOrderPrice = m_buyOrder.m_price;
                double sellOrderPrice = m_sellOrder.m_price;

                boolean success = true;
                double buyDistance = buyExchTop.m_bid - buyOrderPrice;
                double buyDelta = buy - buyOrderPrice;
                double deltaPrcnt = Math.abs(buyDelta) / buyDistance;
                if (deltaPrcnt < MOVE_BRACKET_ORDER_MIN_PERCENTAGE) { // do not move order if changed just a little (<10%)
                    log("  do not move BUY bracket, [" + m_buyOrder.priceStr() + "->" + format(buy) + "] " +
                            "delta=" + format(buyDelta) + ", deltaPrcnt=" + format(deltaPrcnt));
                } else {
                    log("  move BUY bracket, [" + m_buyOrder.priceStr() + "->" + format(buy) + "] " +
                            "delta=" + format(buyDelta) + ", deltaPrcnt=" + format(deltaPrcnt));
                    success = m_buyExch.cancelOrder(m_buyOrder); // todo: order can be executed at this point, so cancel will fail
                    if (success) {
                        m_buyOrder = new OrderData(OrderSide.BUY, buy, amount);
                        success = m_buyExch.placeOrderBracket(m_buyOrder);
                        if (success) {
                            buyDistance = buyExchTop.m_bid - buy;
                        } else {
                            log("ERROR: moveBrackets - place buy order failed: " + m_buyOrder);
                        }
                    } else {
                        log("ERROR: moveBrackets - cancel buy order failed: " + m_buyOrder);
                    }
                }

                double sellDistance = sellOrderPrice - sellExchTop.m_ask;
                double sellDelta = sellOrderPrice - sell;
                deltaPrcnt = Math.abs(sellDelta) / sellDistance;
                if (deltaPrcnt < MOVE_BRACKET_ORDER_MIN_PERCENTAGE) { // do not move order if changed just a little (<10%)
                    log("  do not move SELL bracket, [" + m_sellOrder.priceStr() + "->" + format(sell) + "] " +
                            "delta=" + format(sellDelta) + ", deltaPrcnt=" + format(deltaPrcnt));
                } else {
                    log("  move SELL bracket, [" + m_buyOrder.priceStr() + "->" + format(buy) + "] " +
                            "delta=" + format(buyDelta) + ", deltaPrcnt=" + format(deltaPrcnt));
                    success = m_sellExch.cancelOrder(m_sellOrder);  // todo: order can be executed at this point, so cancel will fail
                    if (success) {
                        m_sellOrder = new OrderData(OrderSide.SELL, sell, amount);
                        success = m_sellExch.placeOrderBracket(m_sellOrder);
                        if (success) {
                            sellDistance = sell - sellExchTop.m_ask;
                        } else {
                            log("ERROR: moveBrackets - place sell order failed: " + m_sellOrder);
                        }
                    } else {
                        log("ERROR: moveBrackets - cancel sell order failed: " + m_sellOrder);
                    }
                }

                if (success) {
                    double minDistance = Math.min(Math.abs(buyDistance), Math.abs(sellDistance));
                    long delay = (minDistance < 4.0) ? (long) ((minDistance / 4.0) * 6000) : 6000;
                    iContext.delay(delay);
                } else {
                    setState(CrossState.ERROR);
                    iContext.delay(0);
                }
            }
        });
    }

    private BuySell calcBuySellPrices(ForkData forkData, String prefix) {
        double commissionAmount = forkData.m_pairExData.midCommissionAmount();
        double halfTargetDelta = commissionAmount + Fetcher.EXPECTED_GAIN / 2;
        log("calcBuySellPrices: " + prefix + " commissionAmount=" + format(commissionAmount) + ", halfTargetDelta=" + format(halfTargetDelta));
        TopData buyExchTop = m_buyExch.m_lastTop;
        TopData sellExchTop = m_sellExch.m_lastTop;
        double buy = Fetcher.PRICE_ALGO.getRefPrice(sellExchTop, OrderSide.BUY);
        double sell = Fetcher.PRICE_ALGO.getRefPrice(buyExchTop, OrderSide.SELL);
        log(" sell exch " + m_sellExch.m_exchange + ": " + sellExchTop + " -> buy ref price = " + format(buy));
        log(" buy exch " + m_buyExch.m_exchange + ": " + buyExchTop + " -> sell ref price = " + format(sell));
        ForkDirection direction = forkData.m_direction;
        if (m_isOpenCross) {
            double midDiffAverage = forkData.m_pairExData.m_diffAverageCounter.get(); // top1 - top2
            double avgDiff = direction.apply(midDiffAverage);
            buy = buy - halfTargetDelta + avgDiff;
            sell = sell + halfTargetDelta - avgDiff;
        } else {
            double openPriceDiff = forkData.m_openCross.priceDiff();
            double expectedClosePriceDiff = openPriceDiff + m_forkData.m_direction.apply(halfTargetDelta * 2);
            double currentPriceDiff = forkData.m_pairExData.midDiff();
            log(" fork.direction=" + m_forkData.m_direction + ", openPriceDiff=" + format(openPriceDiff) +
                "; expectedClosePriceDiff=" + format(expectedClosePriceDiff) + ", currentPriceDiff=" + format(currentPriceDiff));
            double dif = direction.apply(expectedClosePriceDiff);
            buy = buy - dif;
            sell = sell + dif;
        }
        log("  adjusted: buy=" + format(buy) + "; sell=" + format(sell));
        buy = m_buyExch.roundPrice(buy);
        sell = m_sellExch.roundPrice(sell);
        log("  rounded: buy=" + format(buy) + "; sell=" + format(sell));
        return new BuySell(buy, sell);
    }

    private double priceDiff() {
        return m_sellOrder.m_price - m_buyOrder.m_price;
    }

    private void setState(CrossState state) {
        log("CrossData.setState() " + m_state + " -> " + state);
        m_state = state;
    }

    public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
        log("CrossData.checkState() on " + this);
        if (m_buyOrder != null) { // order can be not placed in case of error
            m_buyOrder.checkState(iContext, m_buyExch, this);
        }
        if (m_sellOrder != null) { // order can be not placed in case of error
            m_sellOrder.checkState(iContext, m_sellExch, this);
        }
        m_state.checkState(iContext, forkData, this);
    }

    public boolean checkBracketsExecuted(IterationContext iContext, ForkData forkData) throws Exception {
        boolean buyExecuted = m_buyOrder.isFilled();
        boolean sellExecuted = m_sellOrder.isFilled();
        if (buyExecuted) {
            if (sellExecuted) {
                log("!!! both brackets are executed");
                setState(CrossState.BOTH_BRACKETS_EXECUTED);
            } else {
                if (m_sellOrder.m_filled > 0) { // other side is already partially filled - need fork first
                    log("BUY OpenBracketOrder FILLED: sellOrder=" + m_sellOrder + ", forking and placing MKT sell order");
                    setState(CrossState.ONE_BRACKET_EXECUTED);
                } else {
                    log("BUY OpenBracketOrder FILLED: sellOrder=" + m_sellOrder + ", placing MKT sell order");
                    placeMktOrder(iContext, forkData, false);
                }
            }
        } else if (sellExecuted) {
            if (m_buyOrder.m_filled > 0) { // other side is already partially filled - need fork first
                log("SELL OpenBracketOrder FILLED: buyOrder=" + m_buyOrder + ", forking and placing MKT sell order");
                setState(CrossState.ONE_BRACKET_EXECUTED);
            } else {
                log("SELL OpenBracketOrder FILLED: buyOrder=" + m_buyOrder + ", placing MKT sell order");
                placeMktOrder(iContext, forkData, true);
            }
        } else {
            log(" no FILLED bracket orders: buyOrder=" + m_buyOrder + ", sellOrder=" + m_sellOrder);
            // todo: probably check first if fork allowed - then pospond moving brackets
            return true;
        }
        return false;
    }

    private void placeMktOrder(final IterationContext iContext, ForkData forkData, final boolean isBuyOrder) throws Exception {
        forkData.m_pairExData.doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                moveMktBracket(iContext, isBuyOrder);
            }
        });
    }

    private OrderData replaceWithMktOrder(OrderData ord, SharedExchangeData shExch) {
        OrderSide side = ord.m_side;
        double price = Fetcher.PRICE_ALGO.getMktPrice(shExch.m_lastTop, side);

        boolean cancelled = shExch.cancelOrder(ord);
        if (cancelled) {
            OrderData mktOrder = new OrderData(side, price, ord.m_amount);
            boolean success = shExch.placeOrder(mktOrder, OrderState.MARKET_PLACED);
            if (success) {
                setState(CrossState.MKT_BRACKET_PLACED);
                return mktOrder;
            } else {
                log("Error submitting MKT order " + mktOrder);
            }
        } else {
            log("Error canceling order " + ord);
        }
        setState(CrossState.ERROR);
        return null;
    }

    public double needFork() {
        // check filled/partially filled case first
        if (m_buyOrder.isFilled() && m_sellOrder.isPartiallyFilled()) {
            if ((m_sellOrder.m_filled > MIN_QTY_TO_FORK) && (m_sellOrder.remained() > MIN_QTY_TO_FORK)) { // do not fork if orders becomes too small
                return m_sellOrder.m_filled;
            }
        }
        if (m_sellOrder.isFilled() && m_buyOrder.isPartiallyFilled()) {
            if ((m_buyOrder.m_filled > MIN_QTY_TO_FORK) && (m_buyOrder.remained() > MIN_QTY_TO_FORK)) { // do not fork if orders becomes too small
                return m_buyOrder.m_filled;
            }
        }

        // then check submitted/partially filled case
        long time = 0L;
        double filled = 0.0;
        double remained = 0.0;
        boolean buyStarted = m_buyOrder.isPartiallyFilled();
        boolean sellStarted = m_sellOrder.isPartiallyFilled();
        if (buyStarted) {
            time = m_buyOrder.time();
            filled = m_buyOrder.m_filled;
            remained = m_buyOrder.remained();
            if (sellStarted) {
                long time2 = m_sellOrder.time();
                if(time2 < time) {
                    time = time2;
                    filled = m_sellOrder.m_filled;
                    remained = m_sellOrder.remained();
                }
            }
        } else if (sellStarted) {
            time = m_sellOrder.time();
            filled = m_sellOrder.m_filled;
            remained = m_sellOrder.remained();
        } else {
            return 0;
        }
        if ((filled > MIN_QTY_TO_FORK) && (remained > MIN_QTY_TO_FORK)) { // do not fork if orders becomes too small
            return ((System.currentTimeMillis() - time > TIME_TO_WAIT_PARTIAL) ? filled : 0);
        }
        log("we do not fork because order becomes too small to handle: filled=" + filled + ", remained=" + remained);
        return 0;
    }

    public boolean stuckTooLong() {
        boolean buyStarted = m_buyOrder.isPartiallyFilled();
        boolean sellStarted = m_sellOrder.isPartiallyFilled();
        long time;
        if (buyStarted) {
            time = m_buyOrder.time();
            if (sellStarted) {
                long time2 = m_sellOrder.time();
                if (time2 < time) {
                    time = time2;
                }
            }
        } else if (sellStarted) {
            time = m_sellOrder.time();
        } else {
            return false;
        }
        long stuckTime = System.currentTimeMillis() - time;
        boolean tooLong = (stuckTime > TOO_LONG_TO_WAIT_PARTIAL);
        if (tooLong) {
            log("Cross stuck for too long (" + stuckTime + "ms) to wait for partial fill on " + this);
            log(" buyOrder: " + m_buyOrder);
            log(" sellOrder: " + m_sellOrder);
        }
        return tooLong;
    }

    public CrossData fork(double qty) {
        OrderData buyOrder = m_buyOrder.fork(qty);
        OrderData sellOrder = m_sellOrder.fork(qty);

        CrossData ret = new CrossData(m_buyExch, m_sellExch);
        ret.m_state = m_state;
        ret.m_buyOrder = buyOrder;
        ret.m_sellOrder = sellOrder;
        ret.m_forkData = m_forkData;
        ret.m_isOpenCross = m_isOpenCross;

        return ret;
    }

    public void checkMarketBracketsExecuted(IterationContext iContext, ForkData forkData) throws Exception {
        if (m_buyOrder.isFilled() && m_sellOrder.isFilled()) {
            log("both BUY and SELL orders executed with priceDiff=" + priceDiff());
            log(" BUY on: " + m_buyExch.m_exchange + " " + m_buyOrder);
            log(" SELL on: " + m_sellExch.m_exchange + " " + m_sellOrder);
            setState(CrossState.BOTH_BRACKETS_EXECUTED);
        } else if (m_buyOrder.m_state == OrderState.MARKET_PLACED) {
            moveMktBracketIfNeeded(iContext, forkData, m_buyOrder);
        } else if (m_sellOrder.m_state == OrderState.MARKET_PLACED) {
            moveMktBracketIfNeeded(iContext, forkData, m_sellOrder);
        } else {
            log("Error: no mkt order: buyOrder=" + m_buyOrder + ", sellOrder=" + m_sellOrder);
        }
    }

    private void moveMktBracketIfNeeded(final IterationContext iContext, ForkData forkData, final OrderData order) throws Exception {
        if (order.isPartiallyFilled()) {
            log("do not move partially filled MKT bracket: " + order);
            return;
        }
        forkData.m_pairExData.doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                boolean isBuyOrder = (order.m_side == OrderSide.BUY);
                moveMktBracket(iContext, isBuyOrder);
            }
        });
    }

    private void moveMktBracket(IterationContext iContext, boolean buyOrder) {
        if (buyOrder) {
            m_buyOrder = replaceWithMktOrder(m_buyOrder, m_buyExch);
        } else {
            m_sellOrder = replaceWithMktOrder(m_sellOrder, m_sellExch);
        }
        iContext.delay(2000);
    }

    public boolean isNotStarted() {
        return (m_buyOrder.m_filled == 0.0) && (m_sellOrder.m_filled == 0.0);
    }

    public void stop() {
        if (m_buyOrder.isActive()) {
            m_buyExch.cancelOrder(m_buyOrder);
        }
        if (m_sellOrder.isActive()) {
            m_sellExch.cancelOrder(m_sellOrder);
        }
        setState(CrossState.STOP);
    }

    public boolean isStopped() {
        return !m_buyOrder.isActive() && !m_sellOrder.isActive();
    }

    private OrderData increaseOrderAmount(SharedExchangeData exch, OrderData ord, double amount) {
        OrderSide side = ord.m_side;
        boolean cancelled = exch.cancelOrder(ord);
        if (cancelled) {
            double orderAmount = ord.m_amount;
            double orderPrice = ord.m_price;

            OrderData mktOrder = new OrderData(side, orderPrice, ord.m_amount);
            boolean success = exch.placeOrder(mktOrder, OrderState.MARKET_PLACED);
            if (success) {
                setState(CrossState.MKT_BRACKET_PLACED);
                return mktOrder;
            } else {
                log("Error submitting MKT order " + mktOrder);
            }
        } else {
            log("Error canceling order " + ord);
        }
        setState(CrossState.ERROR);
        return null;
    }

    public boolean increaseOpenAmount(PairExchangeData pairExchangeData, ForkDirection direction) {
        boolean success = m_buyExch.cancelOrder(m_buyOrder);
        if (success) {
            success = m_sellExch.cancelOrder(m_sellOrder);
            if (success) {
                double amount = pairExchangeData.calcAmount(direction);
                if (amount > OrderData.MIN_ORDER_QTY) {
                    double buyPrice = m_buyOrder.m_price;
                    double sellPrice = m_sellOrder.m_price;

                    m_buyOrder = new OrderData(OrderSide.BUY, buyPrice, amount);
                    m_sellOrder = new OrderData(OrderSide.SELL, sellPrice, amount);

                    log("buy " + amount + " @ " + m_buyExch.m_exchange + ": " +
                            ExchangeData.ordersAndPricesStr(m_buyExch.m_lastTop, m_buyOrder, null, null, null));
                    log("sell " + amount + " @ " + m_sellExch.m_exchange + ": " +
                            ExchangeData.ordersAndPricesStr(m_sellExch.m_lastTop, null, null, m_sellOrder, null));

                    success = m_buyExch.placeOrderBracket(m_buyOrder);
                    if (success) {
                        success = m_sellExch.placeOrderBracket(m_sellOrder);
                        if (success) {
                            setState(CrossState.BRACKETS_PLACED);
                        } else {
                            log("ERROR: " + m_sellExch.m_exchange.m_name + " placeBracket failed");
                        }
                    } else {
                        log("ERROR: " + m_buyExch.m_exchange.m_name + " placeBracket failed");
                    }
                } else {
                    log("not enough amount to open order. amount=" + amount);
                    stop();
                    return false;
                }
            } else {
                log("Error canceling order " + m_sellOrder);
            }
        } else {
            log("Error canceling order " + m_buyOrder);
        }

        if (!success) {
            setState(CrossState.ERROR);
        }

        return success;
    }

    String getLiveTime() {
        return Utils.millisToDHMSStr(System.currentTimeMillis() - m_start);
    }
}
