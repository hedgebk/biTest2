import java.util.List;

public class ExchangeData {
    private static final double MKT_ORDER_THRESHOLD = 1.3; // market order price allowance +-30%
    public static final double MOVE_BRACKET_ORDER_MIN_AMOUNT = 0.2;

    public final Exchange m_exch;
    public ExchangeState m_state = ExchangeState.NONE;

    // to calc average diff between bid and ask on exchange
    public final Utils.DoubleAverageCalculator m_bidAskDiffCalculator = new Utils.DoubleAverageCalculator<Double>() {
        @Override public double getDoubleValue(Double tick) { return tick; }
    };
    //bitstamp avgBidAskDiff1=2.6743, btc-e avgBidAskDiff2=1.3724
    //bitstamp avgBidAskDiff1=2.1741, btc-e avgBidAskDiff2=1.2498
    //bitstamp avgBidAskDiff1=1.9107, btc-e avgBidAskDiff2=1.3497

    // moving bracket orders
    public OrderData m_buyOpenBracketOrder;
    public OrderData m_sellOpenBracketOrder;
    public Long m_lastProcessedTradesTime = 0l;
    TopData m_lastTop;
    public final Utils.AverageCounter m_averageCounter = new Utils.AverageCounter(Fetcher.MOVING_AVERAGE);

    public ExchangeData(Exchange exch) {
        m_exch = exch;
    }

    @Override public String toString() {
        return "ExchangeData{" +
                "exch=" + m_exch +
                ", state=" + m_state +
                '}';
    }

    public int exchId() { return m_exch.m_databaseId; }
    public String avgBidAskDiffStr() { return Fetcher.format(m_bidAskDiffCalculator.getAverage()); }
    String exchName() { return m_exch.m_name; }
    public boolean waitingForOpenBrackets() { return m_state == ExchangeState.OPEN_BRACKETS_WAITING; }
    public boolean hasOpenCloseBracketExecuted() { return (m_state == ExchangeState.ONE_OPEN_BRACKET_EXECUTED) || (m_state == ExchangeState.CLOSE_BRACKET_EXECUTED); }
    public boolean hasOpenCloseMktExecuted() { return (m_state == ExchangeState.OPEN_AT_MKT_EXECUTED) || (m_state == ExchangeState.CLOSE_AT_MKT_EXECUTED); }

    public OrderData oldCheckExecutedBrackets() throws Exception {
        OrderData ret = null;
        if ((m_buyOpenBracketOrder != null) || (m_sellOpenBracketOrder != null)) {
            long millis0 = System.currentTimeMillis();
            TradesData trades1 = Fetcher.fetchTrades(m_exch);
            long millis1 = System.currentTimeMillis();
            TradesData newTrades = trades1.newTrades(m_lastProcessedTradesTime);
            System.out.println(" loaded " + trades1.size() + " trades for '" + exchName() + "' in "+(millis1 - millis0)+" ms; new " + newTrades.size() + " trades: " + newTrades);

            List<TradesData.TradeData> newTradesList = newTrades.m_trades;
            if (m_buyOpenBracketOrder != null) {
                ret = oldCheckExecutedBracket(m_buyOpenBracketOrder, newTradesList);
            }
            if (m_sellOpenBracketOrder != null) {
                ret = oldCheckExecutedBracket(m_sellOpenBracketOrder, newTradesList);
            }
            // todo: if one of brackets is executed - the another one should be adjusted (cancelled?).

            for (TradesData.TradeData trade : newTradesList) {
                long timestamp = trade.m_timestamp;
                if (timestamp > m_lastProcessedTradesTime) {
                    m_lastProcessedTradesTime = timestamp;
                }
            }
        }
        return ret;
    }

    // return opposite order to execute
    private OrderData oldCheckExecutedBracket(OrderData bracketOrder, List<TradesData.TradeData> tradesList) {
        OrderData ret = null;
        OrderSide orderSide = bracketOrder.m_side;
        double orderAmount = bracketOrder.m_amount;
        for (TradesData.TradeData trade : tradesList) {
            double mktPrice = trade.m_price; // ASK > BID
            if (bracketOrder.acceptPrice(mktPrice)) {
                double tradeAmount = trade.m_amount;
                String orderPriceStr = bracketOrder.priceStr();
                System.out.println("@@@@@@@@@@@@@@ we have LMT order " + orderSide + " " + orderAmount + " @ " + orderPriceStr +
                                  " on '" + exchName() + "' got matched trade=" + trade);
                if (orderAmount > tradeAmount) { // for now partial order execution it is complex to handle - we will execute the rest by MKT price
                    System.out.println("@@@@@@@@@@@@@@  for now partial order execution it is complex to handle: orderAmount=" + orderAmount + ", tradeAmount=" + tradeAmount);
                }
                // here we pretend that the whole order was executed for now
                bracketOrder.addExecution(bracketOrder.m_price, bracketOrder.m_amount); // todo: add partial order execution support later.
                ret = new OrderData(orderSide.opposite(), Double.MAX_VALUE /*MKT*/, orderAmount);
                break; // exit for now. continue here with partial orders support.
            }
        }
        return ret;
    }

    public void oldPlaceBrackets(TopData otherTop, double midDiffAverage) {
        double buy = otherTop.m_bid - Fetcher.HALF_TARGET_DELTA + midDiffAverage; // ASK > BID
        double sell = otherTop.m_ask + Fetcher.HALF_TARGET_DELTA + midDiffAverage;

        System.out.println("'" + exchName() + "' buy: " + ((m_buyOpenBracketOrder != null) ? m_buyOpenBracketOrder.priceStr() + "->" : "") + Fetcher.format(buy) + ";  " +
                "sell: " + ((m_sellOpenBracketOrder != null) ? m_sellOpenBracketOrder.priceStr() + "->" : "") + Fetcher.format(sell));

        double amount = Fetcher.calcAmountToOpen(); // todo: get this based on both exch account info

        m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
        m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
    }

    public boolean placeBrackets(TopData top, TopData otherTop, double midDiffAverage) { // ASK > BID
        double buy = otherTop.m_bid - Fetcher.HALF_TARGET_DELTA + midDiffAverage;
        double sell = otherTop.m_ask + Fetcher.HALF_TARGET_DELTA + midDiffAverage;

        logOrdersAndPrices(top, buy, sell);

        double amount = calcAmountToOpen();

        m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
        boolean success = placeOrderBracket(m_buyOpenBracketOrder);
        if (success) {
            m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
            success = placeOrderBracket(m_sellOpenBracketOrder);
            if (success) {
                setState(ExchangeState.OPEN_BRACKETS_PLACED);
            }
        }
        if (!success) {
            System.out.println("ERROR: " + exchName() + " placeBrackets failed");
            setState(ExchangeState.ERROR);
        }
//            iContext.delay(0);
        return success;
    }

    public boolean placeCloseBracket(TopData top, TopData otherTop, double midDiffAverage, OrderSide orderSide) { // ASK > BID
        boolean isBuy = (orderSide == OrderSide.BUY);
        Double buy = isBuy ? otherTop.m_bid - Fetcher.HALF_TARGET_DELTA + midDiffAverage : null;
        Double sell = !isBuy ? otherTop.m_ask + Fetcher.HALF_TARGET_DELTA + midDiffAverage : null;

        logOrdersAndPrices(top, buy, sell);

        boolean success;
        double amount = calcAmountToOpen();
        if(isBuy) {
            m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
            success = placeOrderBracket(m_buyOpenBracketOrder);
        } else {
            m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
            success = placeOrderBracket(m_sellOpenBracketOrder);
        }
        if (success) {
            setState(ExchangeState.CLOSE_BRACKET_PLACED);
        } else {
            System.out.println("ERROR: " + exchName() + " placeBrackets failed");
            setState(ExchangeState.ERROR);
        }
//            iContext.delay(0);
        return success;
    }

    private void setState(ExchangeState state) {
        System.out.println("Exchange '" + exchName() + "' state " + m_state + " -> " + state);
        m_state = state;
    }

    public boolean moveBrackets(TopData top, TopData otherTop, double midDiffAverage) {
        double buy = otherTop.m_bid - Fetcher.HALF_TARGET_DELTA + midDiffAverage; // ASK > BID
        double sell = otherTop.m_ask + Fetcher.HALF_TARGET_DELTA + midDiffAverage;

        logOrdersAndPrices(top, buy, sell);

        double amount = calcAmountToOpen(); // todo: this can be changed over the time - take special care rater

        // todo: do not move order if changed just a little - define accepted order change delta

        boolean success = true;
        if (m_buyOpenBracketOrder != null) {
            double buyDelta = m_buyOpenBracketOrder.m_price - buy;
            if (Math.abs(buyDelta) < MOVE_BRACKET_ORDER_MIN_AMOUNT) { // do not move order if changed just a little
                System.out.println("  do not move BUY bracket, [" + m_buyOpenBracketOrder.priceStr() + "->" + Fetcher.format(buy) + "] delta=" + buyDelta);
            } else {
                cancelOrder(m_buyOpenBracketOrder);
                m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
                success = placeOrderBracket(m_buyOpenBracketOrder);
            }
        }

        if (success) {
            if (m_sellOpenBracketOrder != null) {
                double sellDelta = sell - m_sellOpenBracketOrder.m_price;
                if (Math.abs(sellDelta) < MOVE_BRACKET_ORDER_MIN_AMOUNT) { // do not move order if changed just a little
                    System.out.println("  do not move SELL bracket, [" + m_sellOpenBracketOrder.priceStr() + "->" + Fetcher.format(sell) + "] delta=" + sellDelta);
                } else {
                    cancelOrder(m_sellOpenBracketOrder);
                    m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
                    success = placeOrderBracket(m_sellOpenBracketOrder);
                }
            }
        }

        if (!success) {
            System.out.println("ERROR: " + exchName() + " moveBrackets failed");
            setState(ExchangeState.ERROR);
        }
        return success;
    }

    void logOrdersAndPrices(TopData top, Double newBuyPrice, Double newSellPrice) {
        System.out.println("'" + exchName() + "' buy: " + logPriceAndChange(m_buyOpenBracketOrder, newBuyPrice) + "  " +
                                                logPriceDelta(top.m_bid, priceNewOrOld(m_buyOpenBracketOrder, newBuyPrice)) + "  " +
                                                "[bid=" + top.bidStr() + ", ask=" + top.askStr() + "]  " + // ASK > BID
                                                logPriceDelta(priceNewOrOld(m_sellOpenBracketOrder, newSellPrice), top.m_ask) + "  " +
                                                "sell: " + logPriceAndChange(m_sellOpenBracketOrder, newSellPrice));
    }

    private Double priceNewOrOld(OrderData order, Double price) {
        if (price != null) {
            return price;
        } else {
            if (order == null) {
                return null;
            } else {
                return order.m_price;
            }
        }
    }

    private static String logPriceDelta(Double price1, Double price2) {
        if ((price1 != null) && (price2 != null)) {
            return "<" + Fetcher.format(price1 - price2) + ">";
        } else {
            return "?";
        }
    }

    private static String logPriceAndChange(OrderData order, Double price) {
        if (order == null) {
            if (price == null) {
                return " ";
            } else {
                return Fetcher.format(price);
            }
        } else {
            String orderPrice = order.priceStr();
            if (price == null) {
                return orderPrice;
            } else {
                return orderPrice + "->" + Fetcher.format(price);
            }
        }
    }

    private double calcAmountToOpen() {
        // could be different depending on exchange since the price is different
        return 1.0 /*BTC*/ ;  // todo: get this based on both exch account info
    }

    public TopData fetchTop() throws Exception {
        m_lastTop = Fetcher.fetchTop(m_exch);
        m_averageCounter.add(System.currentTimeMillis(), m_lastTop.getMid());
        return m_lastTop;
    }

    public TopData fetchTopOnce() throws Exception {
        TopData top = Fetcher.fetchTopOnce(m_exch);
        if( top != null ) { // we got fresh top data
            m_lastTop = top; // update top
            m_averageCounter.add(System.currentTimeMillis(), m_lastTop.getMid());
        } else {
            if(m_lastTop != null) {
                m_lastTop.setObsolete();
            }
        }
        return m_lastTop;
    }

    public boolean oldExecuteOpenMktOrder(OrderData mktOrder) {
        OrderSide side = mktOrder.m_side;
        double mktPrice = side.mktPrice(m_lastTop);
        double avgPrice = m_averageCounter.get();
        // do not execute MKT order at price too different from average
        if( Math.max(mktPrice, avgPrice) / Math.min(mktPrice, avgPrice) < MKT_ORDER_THRESHOLD ) {
            System.out.println("@@@@@@@@@@@@@@ we will open "+side+" MKT on " + exchName() + " @ " + mktPrice);
            mktOrder.m_price = mktPrice; // here we pretend that the whole order can be executed quickly
            // todo: in real life we have to send order, wait execution, only then to process further
            mktOrder.addExecution(mktPrice, mktOrder.m_amount);
            if(side == OrderSide.BUY) {
                m_buyOpenBracketOrder = mktOrder;
            } else {
                m_sellOpenBracketOrder = mktOrder;
            }
            // todo: if one of brackets is executed - another one should be adjusted (cancelled?).
            return true;
        } else {
            System.out.println("@@@@@@@@@@@@@@ WARNING can not "+side+" MKT on " + exchName() + " @ " + mktPrice + ", MKT_ORDER_THRESHOLD exceed");
            return false;
        }
    }

    private boolean cancelOrder(OrderData orderData) {
        // todo: implement
        System.out.println("cancelOrder() not implemented yet: " + orderData);
        orderData.m_status = OrderStatus.CANCELLED;
        orderData.m_state = OrderState.NONE;
        return true;
    }

    private boolean placeOrderBracket(OrderData orderData) {
        return placeOrder(orderData, OrderState.BRACKET_PLACED);
    }

    public boolean placeOrder(OrderData orderData, OrderState state) {
        // todo: implement
        System.out.println("placeOrder() not implemented yet, on '"+exchName()+"': " + orderData);
        orderData.m_status = OrderStatus.SUBMITTED;
        orderData.m_state = state;

        return true;
    }

    public void queryAccountData() {
        // todo: implement
        System.out.println("queryAccountData() not implemented yet");
    }

    public LiveOrdersData fetchLiveOrders() {
        // todo: implement
        System.out.println("fetchLiveOrders() not implemented yet");
        return new LiveOrdersData();
    }

    public TradesData fetchTrades() throws Exception {
        // todo: make fetch tradesOnce()
        return Fetcher.fetchTradesOnce(m_exch);
    }

    public TradesData filterOnlyNewTrades(TradesData trades) {
        TradesData newTrades = trades.newTrades(m_lastProcessedTradesTime);
        for (TradesData.TradeData trade : newTrades.m_trades) {
            long timestamp = trade.m_timestamp;
            if (timestamp > m_lastProcessedTradesTime) {
                m_lastProcessedTradesTime = timestamp;
            }
        }
        return newTrades;
    }

    public void checkExchState(IterationContext iContext) throws Exception {
        checkOrderState(m_buyOpenBracketOrder, iContext); // trace order executions separately
        checkOrderState(m_sellOpenBracketOrder, iContext);
        m_state.checkState(iContext, this);
    }

    public OrderData getFilledBracketOrder() {
        return ((m_buyOpenBracketOrder != null) && (m_buyOpenBracketOrder.m_status == OrderStatus.FILLED))
                ? m_buyOpenBracketOrder
                : ((m_sellOpenBracketOrder != null) && (m_sellOpenBracketOrder.m_status == OrderStatus.FILLED))
                    ? m_sellOpenBracketOrder
                    : null;
    }

    private void checkOrderState(OrderData orderData, IterationContext iContext) throws Exception {
        if (orderData != null) {
            orderData.m_state.checkState(iContext, this, orderData);
        }
    }

//        public void onBracketExecuted(OrderData orderData) {
//            // set flag that at least one bracket is executed, actually can be both executed
//            // will be analyzed later
//            m_bracketExecuted = true;
//        }

    public void closeOrders() {
        closeBuyOrder();
        closeSellOrder();
        setState(ExchangeState.NONE); // nothing to wait
    }

    void checkMktBracketExecuted(IterationContext iContext) {
        System.out.println("check if MKT bracket executed");
        boolean buyExecuted = (m_buyOpenBracketOrder != null) && (m_buyOpenBracketOrder.m_status == OrderStatus.FILLED);
        boolean sellExecuted = (m_sellOpenBracketOrder != null) && (m_sellOpenBracketOrder.m_status == OrderStatus.FILLED);
        OrderData openOrder = null;
        if (buyExecuted) {
            if (sellExecuted) {
                System.out.println("we should have only ONE mkt order");
                setState(ExchangeState.ERROR);
            } else {
                System.out.println("BUY OpenMktBracketOrder FILLED");
                openOrder = m_buyOpenBracketOrder;
            }
        } else if (sellExecuted) {
            System.out.println("SELL OpenMktBracketOrder FILLED");
            openOrder = m_sellOpenBracketOrder;
        } else {
            System.out.println(" no FILLED open MKT bracket order. waiting more");
        }
        if (openOrder != null) {
            System.out.println("we open MKT bracket order on '" + exchName() + "': " + openOrder);
            if(m_state == ExchangeState.OPEN_AT_MKT_PLACED) {
                setState(ExchangeState.OPEN_AT_MKT_EXECUTED);
            } else if(m_state == ExchangeState.CLOSE_AT_MKT_PLACED) {
                setState(ExchangeState.CLOSE_AT_MKT_EXECUTED);
            }
            iContext.delay(0);
        }
    }

    void checkSomeBracketExecuted(IterationContext iContext) {
        System.out.println("check if some bracket executed"); // todo: note: both can be executed (for OPEN case) in rare cases !!
        // todo: also some bracket can be partially executed - complex scenario
        boolean buyExecuted = (m_buyOpenBracketOrder != null) && (m_buyOpenBracketOrder.m_status == OrderStatus.FILLED);
        boolean sellExecuted = (m_sellOpenBracketOrder != null) && (m_sellOpenBracketOrder.m_status == OrderStatus.FILLED);
        OrderData openOrder = null;
        if (buyExecuted) {
            if (sellExecuted) {
                // todo: very rare case - both brackets are executed on the same exchange - fine - just cache-out - diff should be enough
                System.out.println("!!! both brackets are executed - BINGO - just cache-out");
                setState(ExchangeState.ERROR);
            } else {
                // todo: if one of brackets is executed - the another one should be adjusted (for now cancel).
                System.out.println("BUY OpenBracketOrder FILLED, closing opposite bracket: " + m_sellOpenBracketOrder);
                // close opposite
                closeSellOrder();
                openOrder = m_buyOpenBracketOrder;
            }
        } else if (sellExecuted) {
            // todo: if one of brackets is executed - the another one should be adjusted (for now cancel).
            System.out.println("SELL OpenBracketOrder FILLED, closing opposite bracket: " + m_buyOpenBracketOrder);
            // close opposite
            closeBuyOrder();
            openOrder = m_sellOpenBracketOrder;
        } else {
            System.out.println(" no FILLED bracket orders: m_buyOpenBracketOrder=" + m_buyOpenBracketOrder + ", m_sellOpenBracketOrder=" + m_sellOpenBracketOrder);
        }
        if (openOrder != null) {
            System.out.println("we have open bracket order executed on '" + exchName() + "': " + openOrder);
            if (m_state == ExchangeState.OPEN_BRACKETS_PLACED) {
                setState(ExchangeState.ONE_OPEN_BRACKET_EXECUTED);
            } else if (m_state == ExchangeState.CLOSE_BRACKET_PLACED) {
                setState(ExchangeState.CLOSE_BRACKET_EXECUTED);
            } else {
                System.out.println(" unexpected state "+m_state+" on " + this);
                setState(ExchangeState.ERROR);
            }
            iContext.delay(0);
        }
    }

    private void closeBuyOrder() {
        closeOrder(m_buyOpenBracketOrder);
        m_buyOpenBracketOrder = null;
    }

    private void closeSellOrder() {
        closeOrder(m_sellOpenBracketOrder);
        m_sellOpenBracketOrder = null;
    }

    void xAllBracketsPlaced(IterationContext iContext) {
        xBracketPlaced(m_buyOpenBracketOrder);
        xBracketPlaced(m_sellOpenBracketOrder);
        setState(ExchangeState.OPEN_BRACKETS_PLACED);
        iContext.delay(0);
    }

    private void xBracketPlaced(OrderData orderData) {
        orderData.m_status = OrderStatus.SUBMITTED;
        orderData.m_state = OrderState.BRACKET_PLACED;
    }

//        private void xCheckExecutedBrackets(TradesData newTrades) {
//            List<TradesData.TradeData> newTradesList = newTrades.m_trades;
//            if (m_buyOpenBracketOrder != null) {
//                ret = checkExecutedBracket(m_buyOpenBracketOrder, newTradesList);
//            }
//            if (m_sellOpenBracketOrder != null) {
//                ret = checkExecutedBracket(m_sellOpenBracketOrder, newTradesList);
//            }
//            // todo: if one of brackets is executed - the another one should be adjusted (cancelled?).
//        }

    private boolean closeOrder(OrderData orderData) {
        System.out.println("closeOrder() not implemented: " + orderData);
        if (orderData != null) {
            orderData.m_status = OrderStatus.CANCELLED;
            orderData.m_state = OrderState.NONE;
        }
        return true; // todo: but actually can be already executed
    }

    public boolean postOrderAtMarket(OrderSide side, TopData top, ExchangeState state) {
        boolean placed = false;
        System.out.println("postOrderAtMarket() openedExch='"+exchName()+"', side=" + side);
        if (TopData.isLive(top)) {
            double price = side.mktPrice(top);

            logOrdersAndPrices(top, (side == OrderSide.BUY) ? price : null, (side == OrderSide.SELL) ? price : null);

            double amount = calcAmountToOpen();
            OrderData order = new OrderData(side, price, amount);
            boolean success = placeOrder(order, OrderState.MARKET_PLACED);
            if (success) {
                placed = true;
                if (side == OrderSide.BUY) {
                    m_buyOpenBracketOrder = order;
                } else {
                    m_sellOpenBracketOrder = order;
                }
                setState(state);
            } else {
                System.out.println("error opening order at MKT: " + order);
                setState(ExchangeState.ERROR);
            }
        } else {
            System.out.println("will not open OrderAtMarket price - waiting - no live topData now : " + top);
        }
        return placed;
    }

    public void cleanOrders() {
        m_buyOpenBracketOrder = null;
        m_sellOpenBracketOrder = null;
    }

    public void setAllAsError() {
        System.out.println("setAllAsError() on " + exchName());
        closeOrders();
        setState(ExchangeState.ERROR);
    }
} // ExchangeData
