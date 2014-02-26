package bthdg;

import java.io.IOException;

public class ExchangeData {
    private static final double MKT_ORDER_THRESHOLD = 1.3; // market order price allowance +-30%
    private static final double MIN_MKT_ORDER_PRICE_CHANGE = 0.0001;
    private static final double MOVE_BRACKET_ORDER_MIN_PERCENTAGE = 0.1; // move brackets of price change in 10% from mkt price

    public final Exchange m_exch;
    SharedExchangeData m_shExchData;
    public ExchangeState m_state = ExchangeState.NONE;
    public OrderData m_buyOrder;
    public OrderData m_sellOrder;

    private static void log(String s) { Log.log(s); }

    public ExchangeData(SharedExchangeData shExchData) {
        this(shExchData.m_exchange);
        m_shExchData = shExchData;
    }

    private ExchangeData(Exchange exchange) {
        m_exch = exchange;
    }

    @Override public String toString() {
        return "ExchangeData{" +
                "exch=" + m_exch +
                ", state=" + m_state +
                '}';
    }

    String exchName() { return m_exch.m_name; }
    public boolean waitingForOpenBrackets() { return m_state == ExchangeState.OPEN_BRACKETS_WAITING; }
    public boolean hasOpenCloseBracketExecuted() { return (m_state == ExchangeState.ONE_OPEN_BRACKET_EXECUTED) || (m_state == ExchangeState.CLOSE_BRACKET_EXECUTED); }
    public boolean hasBothBracketsExecuted() { return (m_state == ExchangeState.BOTH_OPEN_BRACKETS_EXECUTED); }
    public boolean hasOpenCloseMktExecuted() { return (m_state == ExchangeState.OPEN_AT_MKT_EXECUTED) || (m_state == ExchangeState.CLOSE_AT_MKT_EXECUTED); }
    public double midCommissionAmount() { return m_shExchData.midCommissionAmount(); }
    public boolean isStopped() { return (m_state == ExchangeState.NONE) || (m_state == ExchangeState.ERROR); }
    private static String format(double buy) { return Fetcher.format(buy); }

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
            log("ERROR: " + exchName() + " placeBrackets failed");
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
            log("ERROR: " + exchName() + " placeBrackets failed");
            setState(ExchangeState.ERROR);
        }
//            iContext.delay(0);
        return success;
    }

    private void setState(ExchangeState state) {
        log("Exchange '" + exchName() + "' state " + m_state + " -> " + state);
        m_state = state;
    }

    public boolean moveBrackets(TopData top, TopData otherTop, double midDiffAverage, double halfTargetDelta) {
        double buy = otherTop.m_bid - halfTargetDelta + midDiffAverage; // ASK > BID
        double sell = otherTop.m_ask + halfTargetDelta + midDiffAverage;

        logOrdersAndPrices(top, buy, sell);

        double amount = calcAmountToOpen(); // todo: this can be changed over the time - take special care later

        boolean success = true;
        if (m_buyOrder != null) {
            double orderPrice = m_buyOrder.m_price;
            double distance = top.m_bid - orderPrice;
            double buyDelta = buy - orderPrice;
            double deltaPrcnt = Math.abs(buyDelta) / distance;
            if (deltaPrcnt < MOVE_BRACKET_ORDER_MIN_PERCENTAGE) { // do not move order if changed just a little (<10%)
                log("  do not move BUY bracket, [" + m_buyOrder.priceStr() + "->" + format(buy) + "] " +
                        "delta=" + format(buyDelta) + ", deltaPrcnt=" + format(deltaPrcnt));
            } else {
                success = cancelOrder(m_buyOrder); // todo: order can be executed at this point, so cancel will fail
                if (success) {
                    m_buyOrder = new OrderData(OrderSide.BUY, buy, amount);
                    success = placeOrderBracket(m_buyOrder);
                    if (success) {
                        distance = top.m_bid - buy ;
                    } else {
                        log(" moveBrackets - place buy order failed: " + m_buyOrder);
                    }
                } else {
                    log(" moveBrackets - cancel buy order failed: " + m_buyOrder);
                }
            }
            log("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX   distance="+format(distance));
        }

        if (success) {
            if (m_sellOrder != null) {
                double orderPrice = m_sellOrder.m_price;
                double distance = orderPrice - top.m_ask;
                double sellDelta = orderPrice - sell;
                double deltaPrcnt = Math.abs(sellDelta) / distance;
                if (deltaPrcnt < MOVE_BRACKET_ORDER_MIN_PERCENTAGE) { // do not move order if changed just a little (<10%)
                    log("  do not move SELL bracket, [" + m_sellOrder.priceStr() + "->" + format(sell) + "] " +
                            "delta=" + format(sellDelta) + ", deltaPrcnt=" + format(deltaPrcnt));
                } else {
                    success = cancelOrder(m_sellOrder);  // todo: order can be executed at this point, so cancel will fail
                    if (success) {
                        m_sellOrder = new OrderData(OrderSide.SELL, sell, amount);
                        success = placeOrderBracket(m_sellOrder);
                        if (success) {
                            distance = sell - top.m_ask;
                        } else {
                            log(" moveBrackets - place sell order failed: " + m_sellOrder);
                        }
                    } else {
                        log(" moveBrackets - cancel sell order failed: " + m_sellOrder);
                    }
                }
                log("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX   distance="+format(distance));
            }
        }
        if (!success) {
            log("ERROR: " + exchName() + " moveBrackets failed");
            setState(ExchangeState.ERROR);
        }
        return success;
    }

    void logOrdersAndPrices(TopData top, Double newBuyPrice, Double newSellPrice) {
        log("'" + exchName() + "' " + ordersAndPricesStr(top, newBuyPrice, newSellPrice));
    }

    private String ordersAndPricesStr(TopData top, Double newBuyPrice, Double newSellPrice) {
        return "buy: " + logPriceAndChange(m_buyOrder, newBuyPrice) + "  " +
                logPriceDelta((top == null) ? null : top.m_bid, priceNewOrOld(m_buyOrder, newBuyPrice)) + "  " +
                "[bid=" + ((top == null) ? "?" : top.bidStr()) + ", ask=" + ((top == null) ? "?" : top.askStr()) + "]  " + // ASK > BID
                logPriceDelta(priceNewOrOld(m_sellOrder, newSellPrice), (top == null) ? null : top.m_ask) + "  " +
                "sell: " + logPriceAndChange(m_sellOrder, newSellPrice);
    }

    private Double priceNewOrOld(OrderData order, Double newPrice) {
        if (newPrice != null) {
            return newPrice;
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
            return "<" + format(price1 - price2) + ">";
        } else {
            return "?";
        }
    }

    private static String logPriceAndChange(OrderData order, Double price) {
        if (order == null) {
            if (price == null) {
                return " ";
            } else {
                return format(price);
            }
        } else {
            String orderPrice = order.priceStr();
            if (price == null) {
                return orderPrice;
            } else {
                return orderPrice + "->" + format(price);
            }
        }
    }

    private double calcAmountToOpen() {
        return m_shExchData.calcAmountToOpen();
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

    private boolean placeOrderBracket(OrderData orderData) {
        return placeOrder(orderData, OrderState.BRACKET_PLACED);
    }

    public boolean placeOrder(OrderData orderData, OrderState state) {
        // todo: implement
        log("placeOrder() not implemented yet, on '"+exchName()+"': " + orderData);
        orderData.m_status = OrderStatus.SUBMITTED;
        orderData.m_state = state;

        return true;
    }

    public void queryAccountData() throws Exception {
        m_shExchData.queryAccountData();
    }

    public LiveOrdersData fetchLiveOrders() {
        // todo: implement
        log("fetchLiveOrders() not implemented yet");
        return new LiveOrdersData();
    }

    public void checkExchState(IterationContext iContext) throws Exception {
        log("Exch.checkExchState() " + this);
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

    public void closeOrders() {
        cancelBuyOrder(); // todo: order can be executed at this point, so cancel will fail
        cancelSellOrder(); // todo: order can be executed at this point, so cancel will fail
        setState(ExchangeState.NONE); // nothing to wait
    }

    void checkMktBracketExecuted(IterationContext iContext) {
        log("check if MKT bracket executed");
        boolean buyExecuted = (m_buyOrder != null) && (m_buyOrder.m_status == OrderStatus.FILLED);
        boolean sellExecuted = (m_sellOrder != null) && (m_sellOrder.m_status == OrderStatus.FILLED);
        OrderData openOrder = null;
        if (buyExecuted) {
            if (sellExecuted) {
                log("we should have only ONE mkt order");
                setState(ExchangeState.ERROR);
            } else {
                log("BUY OpenMktBracketOrder FILLED");
                openOrder = m_buyOrder;
            }
        } else if (sellExecuted) {
            log("SELL OpenMktBracketOrder FILLED");
            openOrder = m_sellOrder;
        } else {
            log(" no FILLED open MKT bracket order. waiting more");
        }
        if (openOrder != null) {
            log("we open MKT bracket order on '" + exchName() + "': " + openOrder);
            if(m_state == ExchangeState.OPEN_AT_MKT_PLACED) {
                setState(ExchangeState.OPEN_AT_MKT_EXECUTED);
            } else if(m_state == ExchangeState.CLOSE_AT_MKT_PLACED) {
                setState(ExchangeState.CLOSE_AT_MKT_EXECUTED);
            }
            iContext.delay(0);
        }
    }

    void checkSomeBracketExecuted(IterationContext iContext) {
        // todo: note: both can be executed (for OPEN case) in rare cases !!
        // todo: also some bracket can be partially executed - complex scenario
        boolean buyExecuted = (m_buyOrder != null) && m_buyOrder.isFilled();
        boolean sellExecuted = (m_sellOrder != null) && m_sellOrder.isFilled();
        OrderData openOrder = null;
        if (buyExecuted) {
            if (sellExecuted) {
                // todo: very rare case - both brackets are executed on the same exchange - fine - just cache-out - diff should be enough
                log("!!! both brackets are executed on the same exchange (" + exchName() + ")- BINGO - just cache-out");
                setState(ExchangeState.BOTH_OPEN_BRACKETS_EXECUTED);
            } else {
                // todo: if one of brackets is executed - the another one should be adjusted (for now cancel).
                log("BUY OpenBracketOrder FILLED, closing opposite bracket: " + m_sellOrder);
                // close opposite
                boolean closed = cancelSellOrder(); // todo: order actually can be already executed and close will failed
                if(!closed) {
                    log(" error for cancelSellOrder on " + m_sellOrder);
                    setState(ExchangeState.ERROR);
                    return;
                }
                openOrder = m_buyOrder;
            }
        } else if (sellExecuted) {
            // todo: if one of brackets is executed - the another one should be adjusted (for now cancel).
            log("SELL OpenBracketOrder FILLED, closing opposite bracket: " + m_buyOrder);
            // close opposite
            boolean closed = cancelBuyOrder(); // todo: order can be executed at this point, so cancel will fail
            if(!closed) {
                log(" error for cancelBuyOrder on " + m_buyOrder);
                setState(ExchangeState.ERROR);
                return;
            }
            openOrder = m_sellOrder;
        } else {
            log(" no FILLED bracket orders: buyOrder=" + m_buyOrder + ", sellOrder=" + m_sellOrder);
        }
        if (openOrder != null) {
            log("we have open bracket order executed on '" + exchName() + "': " + openOrder);
            if (m_state == ExchangeState.OPEN_BRACKETS_PLACED) {
                setState(ExchangeState.ONE_OPEN_BRACKET_EXECUTED);
            } else if (m_state == ExchangeState.CLOSE_BRACKET_PLACED) {
                setState(ExchangeState.CLOSE_BRACKET_EXECUTED);
            } else {
                log(" unexpected state " + m_state + " on " + this);
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

    public boolean postOrderAtMarket(OrderSide side, TopData top, ExchangeState newState) {
        boolean placed = false;
        log("postOrderAtMarket() exch='"+exchName()+"', side=" + side);
        if (TopData.isLive(top)) {
            double price = side.mktPrice(top);
            logOrdersAndPrices(top, (side == OrderSide.BUY) ? price : null, (side == OrderSide.SELL) ? price : null);

            double mktPrice = side.mktPrice(m_shExchData.m_lastTop);
            double avgPrice = m_shExchData.m_averageCounter.get();
            // do not execute MKT order at price too different from average
            if (Math.max(mktPrice, avgPrice) / Math.min(mktPrice, avgPrice) < MKT_ORDER_THRESHOLD) {
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
                    setState(newState);
                } else {
                    log("error opening order at MKT: " + order);
                    setState(ExchangeState.ERROR);
                }
            } else {
                log("@@@@@@@@@@@@@@ WARNING can not " + side + " MKT on " + exchName() + " @ " + mktPrice + ", MKT_ORDER_THRESHOLD exceed");
                setState(ExchangeState.ERROR);
                return false;
            }
        } else {
            log("will not open OrderAtMarket price - waiting - no live topData now : " + top);
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
                log("ERROR no mkt order found on " + exchName());
                setState(ExchangeState.ERROR);
                return false;
            }
            if (TopData.isLive(m_shExchData.m_lastTop)) {
                double orderPrice = order.m_price;
                OrderSide side = order.m_side;
                double mktPrice = side.mktPrice(m_shExchData.m_lastTop);
                double priceDif = Math.abs(orderPrice - mktPrice);
                if (priceDif > MIN_MKT_ORDER_PRICE_CHANGE) {
                    return moveMarketOrder(order);
                } else {
                    log(" NOT moving MKT order - priceDif=" + format(priceDif) + " : " + order);
                }
            } else {
                log(" NOT moving MKT order - no fresh top data: " + order);
            }
        }
        return true; // no error
    }

    private boolean moveMarketOrder(OrderData order) {
        if( cancelOrder(order) ) { // todo: order can be executed at this point, so cancel will fail
            if(order.m_status == OrderStatus.PARTIALLY_FILLED) {
                log("not supported moveMarketOrderIfNeeded for PARTIALLY_FILLED: "+order);
                setState(ExchangeState.ERROR);
                return false;
            }
            double amount = calcAmountToOpen(); // todo: we may have partially executed order here - handle
            OrderSide side = order.m_side;
            double mktPrice = side.mktPrice(m_shExchData.m_lastTop);
            OrderData newOrder = new OrderData(side, mktPrice, amount);
            log(" moving MKT order price: " + order.priceStr() + " -> " + format(mktPrice));
            boolean success = placeOrder(newOrder, OrderState.MARKET_PLACED);
            if (success) {
                if (side == OrderSide.BUY) {
                    m_buyOrder = newOrder;
                } else {
                    m_sellOrder = newOrder;
                }
            } else {
                log("error re-opening order at MKT: " + order);
                setState(ExchangeState.ERROR);
                return false;
            }
        } else {
            log(" unable cancel order: " + order);
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
        if(m_state != ExchangeState.ERROR) {
            log("setAllAsError() on " + exchName());
            closeOrders();
            setState(ExchangeState.ERROR);
        }
    }

    public void stop() {
        log("stop() on " + exchName());
        closeOrders();
    }

    public void serialize(StringBuilder sb) {
        sb.append("Exch[exch=").append(m_exch.toString());
        sb.append("; state=").append(m_state.toString());
        sb.append("; buy=");
        if (m_buyOrder != null) {
            m_buyOrder.serialize(sb);
        }
        sb.append("; sell=");
        if (m_sellOrder != null) {
            m_sellOrder.serialize(sb);
        }
        sb.append("; ]");
    }

    public static ExchangeData deserialize(Deserializer deserializer) throws IOException {
        deserializer.readObjectStart("Exch");
        deserializer.readPropStart("exch");
        String exchStr = deserializer.readTill("; ");
        deserializer.readPropStart("state");
        String stateStr = deserializer.readTill("; ");
        deserializer.readPropStart("buy");
        OrderData buy = OrderData.deserialize(deserializer);
        deserializer.readPropStart("sell");
        OrderData sell = OrderData.deserialize(deserializer);
        deserializer.readObjectEnd();

        Exchange exchange = Exchange.valueOf(exchStr);
        ExchangeData ret = new ExchangeData(exchange);
        ret.m_state = ExchangeState.valueOf(stateStr);
        ret.m_buyOrder = buy;
        ret.m_sellOrder = sell;

        return ret;
    }

    public void postDeserialize(PairExchangeData ret) {
        m_shExchData = ret.getSharedExch(m_exch);
    }

    public void compare(ExchangeData other) {
        if (m_exch != other.m_exch) {
            throw new RuntimeException("m_exch");
        }
        if (m_state != other.m_state) {
            throw new RuntimeException("m_state");
        }
        if(Utils.compareAndNotNulls(m_buyOrder, other.m_buyOrder)) {
            m_buyOrder.compare(other.m_buyOrder);
        }
        if(Utils.compareAndNotNulls(m_sellOrder, other.m_sellOrder)) {
            m_sellOrder.compare(other.m_sellOrder);
        }
    }

    public void appendState(StringBuilder sb) {
        sb.append("{\"name\": \"")
          .append(exchName())
          .append("\", \"state\": \"")
          .append(m_state)
          .append("\", \"ord\": \"")
          .append(ordersAndPricesStr(m_shExchData.m_lastTop, null, null))
          .append("\"}");
    }
} // ExchangeData
