package bthdg;

public class CrossData {
    private static final long TIME_TO_WAIT_PARTIAL = 5000;

    public CrossState m_state;
    public final SharedExchangeData m_buyExch;
    public final SharedExchangeData m_sellExch;
    public OrderData m_buyOrder;
    public OrderData m_sellOrder;

    private static void log(String s) { Log.log(s); }
    public boolean isActive() { return (m_state == CrossState.OPEN_BRACKETS_PLACED); }

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
    }

    private void init(double halfTargetDelta, double midDiffAverage, double amount) {
        // ASK > BID
        TopData buyExchTop = m_buyExch.m_lastTop;
        TopData sellExchTop = m_sellExch.m_lastTop;
        double buy = sellExchTop.m_bid - halfTargetDelta + midDiffAverage;
        double sell = buyExchTop.m_ask + halfTargetDelta - midDiffAverage;

        m_buyOrder  = new OrderData(OrderSide.BUY,  buy,  amount);
        m_sellOrder = new OrderData(OrderSide.SELL, sell, amount);

        log("buy exch " + m_buyExch.m_exchange + ": " +
                ExchangeData.ordersAndPricesStr(buyExchTop, m_buyOrder, null, null, null));
        log("sell exch " + m_sellExch.m_exchange + ": " +
                ExchangeData.ordersAndPricesStr(sellExchTop, null, null, m_sellOrder, null));

        boolean success = m_buyExch.placeOrderBracket(m_buyOrder);
        if (success) {
            success = m_sellExch.placeOrderBracket(m_sellOrder);
            if (success) {
                setState(CrossState.OPEN_BRACKETS_PLACED);
            } else {
                log("ERROR: " + m_sellExch.m_exchange.m_name + " placeBracket failed");
                setState(CrossState.ERROR);
            }
        } else {
            log("ERROR: " + m_buyExch.m_exchange.m_name + " placeBracket failed");
            setState(CrossState.ERROR);
        }
    }

    public void moveBracketsIfNeeded(IterationContext iContext) {

    }

    private void setState(CrossState state) {
        log("CrossData.setState() " + m_state + " -> " + state);
        m_state = state;
    }

    public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
        log("CrossData.checkState() on " + this);
        m_buyOrder.m_state.checkState(iContext, m_buyExch, m_buyOrder);
        m_sellOrder.m_state.checkState(iContext, m_sellExch, m_sellOrder);
        m_state.checkState(iContext, forkData, this);
    }

    public void init(ForkData forkData) {
        double midDiffAverage = forkData.m_pairExData.m_diffAverageCounter.get(); // top1 - top2
        double commissionAmount = forkData.midCommissionAmount();
        double halfTargetDelta = (commissionAmount + Fetcher.EXPECTED_GAIN) / 2;
        log(" commissionAmount=" + Fetcher.format(commissionAmount) + ", halfTargetDelta=" + Fetcher.format(halfTargetDelta));

        double avgDiff = forkData.m_startBuySide ? midDiffAverage : -midDiffAverage;
        init(halfTargetDelta, avgDiff, forkData.m_amount);
    }

    public boolean checkOpenBracketsExecuted(IterationContext iContext, ForkData forkData) throws Exception {
        boolean buyExecuted = m_buyOrder.isFilled();
        boolean sellExecuted = m_sellOrder.isFilled();
        if (buyExecuted) {
            if (sellExecuted) {
                log("!!! both brackets are executed");
                setState(CrossState.BOTH_OPEN_BRACKETS_EXECUTED);
            } else {
                if (m_sellOrder.m_filled > 0) { // other side is already partially filled - need fork first
                    log("BUY OpenBracketOrder FILLED: sellOrder=" + m_sellOrder + ", forking and placing MKT sell order");
                    setState(CrossState.ONE_OPEN_BRACKETS_EXECUTED);
                } else {
                    log("BUY OpenBracketOrder FILLED: sellOrder=" + m_sellOrder + ", placing MKT sell order");
                    placeMktOrder(iContext, forkData, false);
                }
            }
        } else if (sellExecuted) {
            if (m_buyOrder.m_filled > 0) { // other side is already partially filled - need fork first
                log("SELL OpenBracketOrder FILLED: buyOrder=" + m_buyOrder + ", forking and placing MKT sell order");
                setState(CrossState.ONE_OPEN_BRACKETS_EXECUTED);
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

    private void placeMktOrder(IterationContext iContext, ForkData forkData, final boolean isBuyOrder) throws Exception {
        forkData.m_pairExData.doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                if (isBuyOrder) {
                    m_buyOrder = replaceWithMktOrder(OrderSide.BUY, m_buyExch.m_lastTop.m_ask, m_buyOrder, m_buyExch);
                } else {
                    m_sellOrder = replaceWithMktOrder(OrderSide.SELL, m_sellExch.m_lastTop.m_bid, m_sellOrder, m_sellExch);
                }
            }
        });
    }

    private OrderData replaceWithMktOrder(OrderSide side, double price, OrderData ord, SharedExchangeData exch) {
        boolean cancelled = exch.cancelOrder(ord);
        if (cancelled) {
            OrderData mktOrder = new OrderData(side, price, ord.m_amount);
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

    private boolean cancelOrder(OrderData orderData) {
        // todo: implement
        if (orderData != null) {
            if ((orderData.m_status == OrderStatus.SUBMITTED) || (orderData.m_status == OrderStatus.PARTIALLY_FILLED)) {
                log("cancelOrder() not implemented yet: " + orderData);
            } else {
                log("cancelOrder() no need to cancel oder in state: " + orderData);
            }
            orderData.m_status = OrderStatus.CANCELLED;
            orderData.m_state = OrderState.NONE;
        }
        return true; // todo: order can be executed at this point, so cancel will fail
    }


    public double checkOpenBracketsPartiallyExecuted() {
        if(m_buyOrder.isFilled() && m_sellOrder.isPartiallyFilled()) {
            return m_sellOrder.m_filled;
        }
        if(m_sellOrder.isFilled() && m_buyOrder.isPartiallyFilled()) {
            return m_buyOrder.m_filled;
        }

        long time = 0L;
        double qty = 0.0;
        boolean buyStarted = m_buyOrder.isPartiallyFilled();
        boolean sellStarted = m_sellOrder.isPartiallyFilled();
        if (buyStarted) {
            time = m_buyOrder.time();
            qty = m_buyOrder.m_filled;
            if (sellStarted) {
                long time2 = m_sellOrder.time();
                if(time2 < time) {
                    time = time2;
                    qty = m_sellOrder.m_filled;
                }
            }
        } else if (sellStarted) {
            time = m_sellOrder.time();
            qty = m_sellOrder.m_filled;
        } else {
            return 0;
        }
        return (System.currentTimeMillis()-time > TIME_TO_WAIT_PARTIAL) ? qty : 0;
    }

    public CrossData fork(double qty) {
        OrderData buyOrder = m_buyOrder.fork(qty);
        OrderData sellOrder = m_sellOrder.fork(qty);

        CrossData ret = new CrossData(m_buyExch, m_sellExch);
        ret.m_state = m_state;
        ret.m_buyOrder = buyOrder;
        ret.m_sellOrder = sellOrder;

        return ret;
    }
}
