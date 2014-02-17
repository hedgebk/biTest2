public class ExchangeData {
    private static final double MKT_ORDER_THRESHOLD = 1.3; // market order price allowance +-30%
    public static final double MOVE_BRACKET_ORDER_MIN_AMOUNT = 0.5;
    private static final double MIN_MKT_ORDER_PRICE_CHANGE = 0.0001;

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
    public OrderData m_buyOrder;
    public OrderData m_sellOrder;
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
    public double commissionAmount() { return m_lastTop.getMid() * m_exch.m_fee; }

    public boolean placeBrackets(TopData top, TopData otherTop, double midDiffAverage, double halfTargetDelta) { // ASK > BID
        double buy = otherTop.m_bid - halfTargetDelta + midDiffAverage;
        double sell = otherTop.m_ask + halfTargetDelta + midDiffAverage;

        logOrdersAndPrices(top, buy, sell);

        double amount = calcAmountToOpen();

        m_buyOrder = new OrderData(OrderSide.BUY, buy, amount);
        boolean success = placeOrderBracket(m_buyOrder);
        if (success) {
            m_sellOrder = new OrderData(OrderSide.SELL, sell, amount);
            success = placeOrderBracket(m_sellOrder);
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

    public boolean placeCloseBracket(TopData top, TopData otherTop, double midDiffAverage, OrderSide orderSide, double halfTargetDelta) { // ASK > BID
        boolean isBuy = (orderSide == OrderSide.BUY);
        Double buy = isBuy ? otherTop.m_bid - halfTargetDelta + midDiffAverage : null;
        Double sell = !isBuy ? otherTop.m_ask + halfTargetDelta + midDiffAverage : null;

        logOrdersAndPrices(top, buy, sell);

        boolean success;
        double amount = calcAmountToOpen();
        if(isBuy) {
            m_buyOrder = new OrderData(OrderSide.BUY, buy, amount);
            success = placeOrderBracket(m_buyOrder);
        } else {
            m_sellOrder = new OrderData(OrderSide.SELL, sell, amount);
            success = placeOrderBracket(m_sellOrder);
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

    public boolean moveBrackets(TopData top, TopData otherTop, double midDiffAverage, double halfTargetDelta) {
        double buy = otherTop.m_bid - halfTargetDelta + midDiffAverage; // ASK > BID
        double sell = otherTop.m_ask + halfTargetDelta + midDiffAverage;

        logOrdersAndPrices(top, buy, sell);

        double amount = calcAmountToOpen(); // todo: this can be changed over the time - take special care rater

        // todo: do not move order if changed just a little - define accepted order change delta

        boolean success = true;
        if (m_buyOrder != null) {
            double buyDelta = m_buyOrder.m_price - buy;
            if (Math.abs(buyDelta) < MOVE_BRACKET_ORDER_MIN_AMOUNT) { // do not move order if changed just a little (<0.05)
                System.out.println("  do not move BUY bracket, [" + m_buyOrder.priceStr() + "->" + Fetcher.format(buy) + "] delta=" + buyDelta);
            } else {
                success = cancelOrder(m_buyOrder); // todo: order can be executed at this point, so cancel will fail
                if (success) {
                    m_buyOrder = new OrderData(OrderSide.BUY, buy, amount);
                    success = placeOrderBracket(m_buyOrder);
                } else {
                    System.out.println(" cancel order failed: " + m_buyOrder);
                }
            }
        }

        if (success) {
            if (m_sellOrder != null) {
                double sellDelta = sell - m_sellOrder.m_price;
                if (Math.abs(sellDelta) < MOVE_BRACKET_ORDER_MIN_AMOUNT) { // do not move order if changed just a little
                    System.out.println("  do not move SELL bracket, [" + m_sellOrder.priceStr() + "->" + Fetcher.format(sell) + "] delta=" + sellDelta);
                } else {
                    success = cancelOrder(m_sellOrder);  // todo: order can be executed at this point, so cancel will fail
                    if (success) {
                        m_sellOrder = new OrderData(OrderSide.SELL, sell, amount);
                        success = placeOrderBracket(m_sellOrder);
                    } else {
                        System.out.println(" cancel order failed: " + m_sellOrder);
                    }
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
        System.out.println("'" + exchName() + "' buy: " + logPriceAndChange(m_buyOrder, newBuyPrice) + "  " +
                                                logPriceDelta(top.m_bid, priceNewOrOld(m_buyOrder, newBuyPrice)) + "  " +
                                                "[bid=" + top.bidStr() + ", ask=" + top.askStr() + "]  " + // ASK > BID
                                                logPriceDelta(priceNewOrOld(m_sellOrder, newSellPrice), top.m_ask) + "  " +
                                                "sell: " + logPriceAndChange(m_sellOrder, newSellPrice));
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
                m_buyOrder = mktOrder;
            } else {
                m_sellOrder = mktOrder;
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
        if( orderData != null ) {
            System.out.println("cancelOrder() not implemented yet: " + orderData);
            orderData.m_status = OrderStatus.CANCELLED;
            orderData.m_state = OrderState.NONE;
        }
        return true; // order can be executed at this point, so cancel will fail
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
        checkOrderState(m_buyOrder, iContext); // trace order executions separately
        checkOrderState(m_sellOrder, iContext);
        m_state.checkState(iContext, this);
    }

    public OrderData getFilledBracketOrder() {
        return ((m_buyOrder != null) && (m_buyOrder.m_status == OrderStatus.FILLED))
                ? m_buyOrder
                : ((m_sellOrder != null) && (m_sellOrder.m_status == OrderStatus.FILLED))
                    ? m_sellOrder
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
        cancelBuyOrder(); // todo: order can be executed at this point, so cancel will fail
        cancelSellOrder(); // todo: order can be executed at this point, so cancel will fail
        setState(ExchangeState.NONE); // nothing to wait
    }

    void checkMktBracketExecuted(IterationContext iContext) {
        System.out.println("check if MKT bracket executed");
        boolean buyExecuted = (m_buyOrder != null) && (m_buyOrder.m_status == OrderStatus.FILLED);
        boolean sellExecuted = (m_sellOrder != null) && (m_sellOrder.m_status == OrderStatus.FILLED);
        OrderData openOrder = null;
        if (buyExecuted) {
            if (sellExecuted) {
                System.out.println("we should have only ONE mkt order");
                setState(ExchangeState.ERROR);
            } else {
                System.out.println("BUY OpenMktBracketOrder FILLED");
                openOrder = m_buyOrder;
            }
        } else if (sellExecuted) {
            System.out.println("SELL OpenMktBracketOrder FILLED");
            openOrder = m_sellOrder;
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
        boolean buyExecuted = (m_buyOrder != null) && (m_buyOrder.m_status == OrderStatus.FILLED);
        boolean sellExecuted = (m_sellOrder != null) && (m_sellOrder.m_status == OrderStatus.FILLED);
        OrderData openOrder = null;
        if (buyExecuted) {
            if (sellExecuted) {
                // todo: very rare case - both brackets are executed on the same exchange - fine - just cache-out - diff should be enough
                System.out.println("!!! both brackets are executed - BINGO - just cache-out");
                setState(ExchangeState.ERROR);
            } else {
                // todo: if one of brackets is executed - the another one should be adjusted (for now cancel).
                System.out.println("BUY OpenBracketOrder FILLED, closing opposite bracket: " + m_sellOrder);
                // close opposite
                boolean closed = cancelSellOrder(); // todo: order actually can be already executed and close will failed
                if(!closed) {
                    System.out.println(" error for cancelSellOrder on " + m_sellOrder);
                    setState(ExchangeState.ERROR);
                    return;
                }
                openOrder = m_buyOrder;
            }
        } else if (sellExecuted) {
            // todo: if one of brackets is executed - the another one should be adjusted (for now cancel).
            System.out.println("SELL OpenBracketOrder FILLED, closing opposite bracket: " + m_buyOrder);
            // close opposite
            boolean closed = cancelBuyOrder(); // todo: order can be executed at this point, so cancel will fail
            if(!closed) {
                System.out.println(" error for cancelBuyOrder on " + m_buyOrder);
                setState(ExchangeState.ERROR);
                return;
            }
            openOrder = m_sellOrder;
        } else {
            System.out.println(" no FILLED bracket orders: m_buyOrder=" + m_buyOrder + ", m_sellOrder=" + m_sellOrder);
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

    private boolean cancelBuyOrder() {
        boolean ret = cancelOrder(m_buyOrder);// todo: order can be executed at this point, so cancel will fail
        m_buyOrder = null;
        return ret;
    }

    private boolean cancelSellOrder() {
        boolean ret = cancelOrder(m_sellOrder);// todo: order can be executed at this point, so cancel will fail
        m_sellOrder = null;
        return ret;
    }

    void xAllBracketsPlaced(IterationContext iContext) {
        xBracketPlaced(m_buyOrder);
        xBracketPlaced(m_sellOrder);
        setState(ExchangeState.OPEN_BRACKETS_PLACED);
        iContext.delay(0);
    }

    private void xBracketPlaced(OrderData orderData) {
        orderData.m_status = OrderStatus.SUBMITTED;
        orderData.m_state = OrderState.BRACKET_PLACED;
    }

    public boolean postOrderAtMarket(OrderSide side, TopData top, ExchangeState state) {
        boolean placed = false;
        System.out.println("postOrderAtMarket() exch='"+exchName()+"', side=" + side);
        if (TopData.isLive(top)) {
            double price = side.mktPrice(top);

            logOrdersAndPrices(top, (side == OrderSide.BUY) ? price : null, (side == OrderSide.SELL) ? price : null);

            double amount = calcAmountToOpen();
            OrderData order = new OrderData(side, price, amount);
            boolean success = placeOrder(order, OrderState.MARKET_PLACED);
            if (success) {
                placed = true;
                if (side == OrderSide.BUY) {
                    m_buyOrder = order;
                } else {
                    m_sellOrder = order;
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

    public boolean moveMarketOrderIfNeeded() {
        if ((m_state == ExchangeState.OPEN_AT_MKT_PLACED) || (m_state == ExchangeState.CLOSE_AT_MKT_PLACED)) {
            OrderData order;
            if ((m_buyOrder != null) && (m_buyOrder.m_state == OrderState.MARKET_PLACED)) {
                order = m_buyOrder;
            } else if ((m_sellOrder != null) && (m_sellOrder.m_state == OrderState.MARKET_PLACED)) {
                order = m_sellOrder;
            } else {
                System.out.println("ERROR no mkt order found on " + exchName());
                setState(ExchangeState.ERROR);
                return false;
            }
            if (TopData.isLive(m_lastTop)) {
                double orderPrice = order.m_price;
                OrderSide side = order.m_side;
                double mktPrice = side.mktPrice(m_lastTop);
                double priceDif = Math.abs(orderPrice - mktPrice);
                if (priceDif > MIN_MKT_ORDER_PRICE_CHANGE) {
                    return moveMarketOrder(order);
                } else {
                    System.out.println(" NOT moving MKT order - priceDif=" + Fetcher.format(priceDif) + " : " + order);
                }
            } else {
                System.out.println(" NOT moving MKT order - no fresh top data: " + order);
            }
        }
        return true; // no error
    }

    private boolean moveMarketOrder(OrderData order) {
        if( cancelOrder(order) ) { // todo: order can be executed at this point, so cancel will fail
            if(order.m_status == OrderStatus.PARTIALLY_FILLED) {
                System.out.println("not supported moveMarketOrderIfNeeded for PARTIALLY_FILLED: "+order);
                setState(ExchangeState.ERROR);
                return false;
            }
            double amount = calcAmountToOpen(); // todo: we may have partially executed order here - handle
            OrderSide side = order.m_side;
            double mktPrice = side.mktPrice(m_lastTop);
            OrderData newOrder = new OrderData(side, mktPrice, amount);
            System.out.println(" moving MKT order price: "+order.priceStr() + " -> " + Fetcher.format(mktPrice));
            boolean success = placeOrder(newOrder, OrderState.MARKET_PLACED);
            if (success) {
                if (side == OrderSide.BUY) {
                    m_buyOrder = order;
                } else {
                    m_sellOrder = order;
                }
            } else {
                System.out.println("error re-opening order at MKT: " + order);
                setState(ExchangeState.ERROR);
                return false;
            }
        } else {
            System.out.println(" unable cancel order: " + order);
            setState(ExchangeState.ERROR);
            return false;
        }
        return true; // no error
    }

    public void cleanOrders() {
        m_buyOrder = null;
        m_sellOrder = null;
    }

    public void setAllAsError() {
        System.out.println("setAllAsError() on " + exchName());
        closeOrders();
        setState(ExchangeState.ERROR);
    }
} // ExchangeData
