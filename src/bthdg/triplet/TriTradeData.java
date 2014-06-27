package bthdg.triplet;

import bthdg.Log;
import bthdg.exch.*;
import bthdg.util.Utils;

import java.util.Random;

public class TriTradeData {
    public static final int ID_CHARS_NUM = 6;

    public final long m_startTime;
    public final String m_id;
    public OrderData m_order; // peg/bracket order
    public OrderData[] m_mktOrders; // 2 mkt orders
    public OnePegCalcData m_peg;
    public TriTradeState m_state = TriTradeState.PEG_PLACED;
    public int m_waitMktOrderStep;
    public boolean m_doMktOffset;
    public int m_iterationsNum;

    public TriTradeData(OrderData order, OnePegCalcData peg, boolean doMktOffset, TriTradeState initState) {
        this(System.currentTimeMillis(), null, order, peg, doMktOffset, initState);
    }
    public TriTradeData(long startTime, String parentId, OrderData order, OnePegCalcData peg, boolean doMktOffset, TriTradeState initState) {
        m_startTime = startTime;
        m_id = generateId(parentId);
        m_doMktOffset = doMktOffset;
        m_order = order;
        m_peg = peg;
        m_state = initState;
    }

    public void startMktOrder(IterationData iData, TriTradesData triTradesData, int num/*1 or 2*/, TriTradeState stateForFilled) throws Exception {
        startMktOrder(iData, triTradesData, num, stateForFilled, false);
    }

    public void startMktOrder(IterationData iData, TriTradesData triTradesData, int num/*1 or 2*/, TriTradeState stateForFilled, boolean blind) throws Exception {
        log("startMktOrder(" + num + ") waitStep=" + m_waitMktOrderStep);

        AccountData account = triTradesData.m_account;
        TopsData tops = blind ? iData.getAnyTops() : iData.getTops();

        PairDirection pd = (num == 1) ? m_peg.m_pair2 : m_peg.m_pair3;
        Pair pair = pd.m_pair;
        boolean forward = pd.isForward();
        TopData topData = tops.get(pair);
        OrderSide side = forward ? OrderSide.BUY : OrderSide.SELL;
        boolean withMktOffset = m_doMktOffset && (m_waitMktOrderStep < Triplet.WAIT_MKT_ORDER_STEPS);
        double tryPrice = withMktOffset
                ? Triangle.mktPrice(topData, pd, Triplet.MKT_OFFSET_PRICE_MINUS)
                : Triangle.mktPrice(topData, pd);
        log("  calculated tryPrice" + (withMktOffset ? "*" : "") + "=" + tryPrice + "; side=" + side + "; top=" + topData.toString(Triplet.s_exchange, pair));

        if (m_doMktOffset && (m_waitMktOrderStep >= Triplet.WAIT_MKT_ORDER_STEPS)) {
            log("   no more steps to do MKT offset - will use mkt price = " + tryPrice);
        }

        // evaluate current mkt prices
        Double limitPrice = calcLimitPrice(num, account, tops, pair, forward, topData, side, tryPrice);
        if (limitPrice != null) {
            if(limitPrice == Double.MAX_VALUE) {
                log("!!!!!!!!!!!!!!!!    error. do not place MKT order");
                setState(TriTradeState.ERROR);
                return;
            }
            log("  replacing with limitPrice=" + limitPrice);
            tryPrice = limitPrice;
        }

        CurrencyAmount currencyAmount = calcAmountFromPrevTrade(num, account, pair, forward);
        Currency currency = currencyAmount.m_currency;
        double amount = currencyAmount.m_amount;

        double available = checkAvailable(iData, triTradesData, num, account, currencyAmount);
        if (available != 0) {
            double orderAmount = side.isBuy() ? (amount / tryPrice) : amount;

            String tryPriceStr = Triplet.roundPriceStr(tryPrice, pair);
            String bidPriceStr = Triplet.roundPriceStr(topData.m_bid, pair);
            String askPriceStr = Triplet.roundPriceStr(topData.m_ask, pair);
            String amountStr = Triplet.s_exchange.roundAmountStr(orderAmount, pair);
            String availableStr = Triplet.s_exchange.roundAmountStr(available, pair);
            log("order(" + num + "):" + m_peg.name() + ", pair: " + pair + ", forward=" + forward + ", from=" + currency +
                    "; available=" + availableStr + "; orderAmount=" + amountStr + "; side=" + side +
                    "; tryPrice=[" + bidPriceStr + "; " + tryPriceStr + "; " + askPriceStr + "]");

            // todo: check for physical min order size like 0.01
            OrderData order = new OrderData(pair, side, tryPrice, orderAmount);
            OrderState ordState = (m_doMktOffset || (limitPrice != null)) ? OrderState.LIMIT_PLACED : OrderState.MARKET_PLACED;
            OrderData.OrderPlaceStatus ok = Triplet.placeOrder(account, order, ordState, iData);
            log("   place order = " + ok);
            if (ok == OrderData.OrderPlaceStatus.OK) {
                iData.noSleep();
                int indx = num - 1;
                setState((indx == 0) ? TriTradeState.MKT1_PLACED : TriTradeState.MKT2_PLACED);
                setMktOrder(order, indx);
                triTradesData.forkAndCheckFilledIfNeeded(iData, this, order, stateForFilled);
            } else if (ok == OrderData.OrderPlaceStatus.CAN_REPEAT) {
                log("    some problem, but we can repeat place order on next iteration");
            } else {
                log("    error placing order - setting error in triTrade");
                setState(TriTradeState.ERROR);
            }
        }
        log("startMktOrder(" + num + ") END: " + this);
    }

    private double checkAvailable(IterationData iData, TriTradesData triTradesData, int num, AccountData account, CurrencyAmount currencyAmount) throws Exception {
        Currency currency = currencyAmount.m_currency;
        double amount = currencyAmount.m_amount;
        double available = account.available(currency);
        if (amount > available) {
            log(" try cancel PEG orders for " + currency + " - not enough available funds to place MKT: available=" + available + "; needed=" + amount);

            tryCancelPegOrders(iData, triTradesData, account, currencyAmount);

            available = account.available(currency);
            if (amount > available) {
                log("ERROR: still not enough available funds to place MKT: available=" + available);
                setState(TriTradeState.CANCELED);
                // todo: do not cancel too fast - wait several iterations - funds can be released
                available = 0; // not available funds - do not place the order
            } else {
                log(" fine - released enough funds to place MKT(" + num + "): available=" + available + "; needed=" + amount);
            }
        }
        return available;
    }

    private CurrencyAmount calcAmountFromPrevTrade(int num, AccountData account, Pair pair, boolean forward) {
        OrderData prevOrder = (num == 1) ? m_order : m_mktOrders[0];
        OrderSide prevSide = prevOrder.m_side;
        Currency prevEndCurrency = prevOrder.endCurrency();
        double fee = account.getFee(Triplet.s_exchange, prevOrder.m_pair);
        double prevEndAmount = (prevSide.isBuy() ? prevOrder.m_amount : prevOrder.m_amount * prevOrder.m_price) * (1 - fee); // deduct commissions
        log(" prev order " + prevOrder + "; exit amount " + Triplet.roundPriceStr(prevEndAmount, pair) + " " + prevEndCurrency);

        Currency fromCurrency = pair.currencyFrom(forward);
        if (prevEndCurrency != fromCurrency) {
            log("ERROR: currencies are not matched");
        }
        return new CurrencyAmount(prevEndAmount, fromCurrency);
    }

    private Double calcLimitPrice(int num/*1 or 2*/, AccountData account, TopsData tops, Pair pair,
                                  boolean forward, TopData topData, OrderSide side, final double mktPrice) {
        Double limitPrice = null;
        String mktPriceStr = Triplet.roundPriceStr(mktPrice, pair);

        double ratio1 = m_order.ratio(account, Triplet.s_exchange); // commission is applied to ratio
        double ratio2 = (num == 1)
                ? m_doMktOffset
                    ? m_peg.mktRatio2(tops, account, Triplet.MKT_OFFSET_PRICE_MINUS)
                    : m_peg.mktRatio2(tops, account)
                : getMktOrder(0).ratio(account, Triplet.s_exchange); // commission is applied to ratio
        double ratio3 = m_doMktOffset
            ? m_peg.mktRatio3(tops, account, Triplet.MKT_OFFSET_PRICE_MINUS)
            : m_peg.mktRatio3(tops, account); // commission is applied to ratio
        double ratio = ratio1 * ratio2 * ratio3;
        log(" ratio1=" + Utils.X_YYYYY.format(ratio1) +
                "; ratio2=" + Utils.X_YYYYY.format(ratio2) +
                "; ratio3=" + Utils.X_YYYYY.format(ratio3) +
                "; ratio=" + Utils.X_YYYYY.format(ratio) +
                ";  mktPrice=" + mktPriceStr);
        int attempt = m_waitMktOrderStep++;
        if (ratio < 1) {
            String topDataStr = topData.toString(Triplet.s_exchange, pair);
            if (ratio < Triplet.TOO_BIG_LOSS_LEVEL) {
                log("!!!!!  MKT conditions gives TOO BIG LOSS - stop trade. ratio=" + ratio + "; pair=" + pair + "; forward=" + forward +
                        "; side=" + side + "; top=" + topDataStr);

                OrderData order1 = m_order;
                OrderData order2 = getMktOrder(0);
                OrderData order3 = getMktOrder(1);

                double price1 = m_peg.m_price1;
                double price2 = m_doMktOffset ? m_peg.m_price2minus : m_peg.m_price2;
                double price3 = m_doMktOffset ? m_peg.m_price3minus : m_peg.m_price3;

                double orderPrice = m_order.m_price;
                double mktPrice1 = m_doMktOffset ? m_peg.calcMktPrice(tops, 1, Triplet.MKT_OFFSET_PRICE_MINUS) : m_peg.calcMktPrice(tops, 1);
                double mktPrice2 = m_doMktOffset ? m_peg.calcMktPrice(tops, 2, Triplet.MKT_OFFSET_PRICE_MINUS) : m_peg.calcMktPrice(tops, 2);

                logOrderEnds(1, order1, price1, orderPrice, m_peg.m_pair1.getSide());
                logOrderEnds(2, order2, price2, mktPrice1, m_peg.m_pair2.getSide());
                logOrderEnds(3, order3, price3, mktPrice2, m_peg.m_pair3.getSide());

                return Double.MAX_VALUE;
            }
            double zeroProfitRatio = (num == 1) ? (1.0 / ratio1 / ratio3) : (1.0 / ratio1 / ratio2);
            double fee = account.getFee(Triplet.s_exchange, pair);
            double zeroProfitPrice = zeroProfitRatio / (1 - fee); // add commission
            if (side.isBuy()) {
                zeroProfitPrice = 1 / zeroProfitPrice;
            }
            double mid = topData.getMid();
            String midStr = Triplet.roundPriceStr(mid, pair);
            log("  MKT conditions do not allow profit on MKT orders close. pair=" + pair + "; forward=" + forward +
                    "; side=" + side + "; zeroProfitPrice=" + zeroProfitPrice + "; top=" + topDataStr +
                    "; mid=" + midStr);

            if (attempt < Triplet.WAIT_MKT_ORDER_STEPS) {
                if ((topData.m_ask > zeroProfitPrice) && (zeroProfitPrice > topData.m_bid)) {
                    boolean betweenMidMkt = side.isBuy()
                            ? ((mid > zeroProfitPrice) && (zeroProfitPrice > mktPrice))
                            : ((mid < zeroProfitPrice) && (zeroProfitPrice < mktPrice));
                    if (betweenMidMkt) {
                        limitPrice = Triplet.roundPrice(zeroProfitPrice, pair);
                        log("    ! zero_Profit_Price is between mid and mkt - try zero price " + mktPriceStr);
                    } else {
                        log("    ! zero_Profit_Price is between mkt edges but too far from mkt - use mkt price " + mktPriceStr);
                    }
                } else {
                    log("    ! zero_Profit_Price is outside of mkt edges");
                    double priceAdd = (mktPrice - mid) * attempt / Triplet.WAIT_MKT_ORDER_STEPS;
                    limitPrice = mid + priceAdd;
                    String priceAddStr = Triplet.roundPriceStr(priceAdd, pair);
                    log("     wait some time - try with mid->mkt orders first: mkt=" + mktPrice + "; mid=" + midStr +
                            "; step=" + attempt + "; priceAdd=" + priceAddStr + ", lmtPrice=" + mktPriceStr);
                    if ((mktPrice > topData.m_ask) || (topData.m_bid > mktPrice)) {
                        log("     ! calculated price is out of mkt bounds : use mid: " + mid);
                        limitPrice = mid;
                    }
                }
            } else {
                log("   we run out of waitMktOrder attempts (" + attempt + "). placing MKT" + num + " @ mkt price " + mktPriceStr);
            }
        } else { // all fine - ratio is profitable
            // todo - try e.g. mkt-10 first  to get better profit
        }
        return limitPrice;
    }

    private void logOrderEnds(int i, OrderData order, double expectedPrice, double mktPrice, OrderSide side) {
        if (order == null) {
            double delta = side.isBuy() ? expectedPrice - mktPrice : mktPrice - expectedPrice;
            log(" order" + i + ": " + Utils.padLeft(side.toString(), 4) +
                    " " + Utils.padLeft(Utils.format8(expectedPrice), 13) +
                    " -> " + Utils.padLeft(Utils.format8(mktPrice), 13) +
                    "; delta=" + Utils.format8(delta));
        } else {
            order.logOrderEnds(m_id, i, expectedPrice);
        }
    }

    private void tryCancelPegOrders(IterationData iData, TriTradesData triTradesData,
                                    AccountData account, CurrencyAmount currencyAmount) throws Exception {
        for (TriTradeData nextTriTrade : triTradesData.m_triTrades) {
            if (nextTriTrade.m_state == TriTradeState.PEG_PLACED) {
                OrderData order = nextTriTrade.m_order;
                Currency startCurrency = order.startCurrency();
                Currency currency = currencyAmount.m_currency;
                if (startCurrency == currency) {
                    log("  found PEG order for " + currency + " " + nextTriTrade.m_peg.name() + " " + order);
                    boolean canceled = triTradesData.cancelOrder(order, iData);
                    if (canceled) {
                        nextTriTrade.setState(TriTradeState.CANCELED);
                        double available = account.available(currency);
                        if (currencyAmount.m_amount < available) {
                            break;
                        }
                    } else {
                        log("   cancel peg order error for " + order);
                    }
                }
            }
        }
    }

    private String generateId(String parentId) {
        Random rnd = new Random();
        StringBuilder buf = new StringBuilder(ID_CHARS_NUM + 3);
        buf.append('{');
        for (int i = 0; i < ID_CHARS_NUM; i++) {
            buf.append((char) ('A' + rnd.nextInt(25)));
        }
        if (parentId != null) {
            buf.append('-');
            buf.append(parentId);
        }
        buf.append('}');
        return buf.toString();
    }

    public void checkState(IterationData iData, TriTradesData triTradesData) throws Exception {
        m_state.checkState(iData, triTradesData, this);
    }

    public void setState(TriTradeState state) {
        if (m_state != state) {
            log("TriTradeData.setState() " + m_state + " -> " + state);
            m_state = state;
        }
    }

    public void setMktOrder(OrderData order, int indx) {
        if (m_mktOrders == null) {
            m_mktOrders = new OrderData[2];
        }
        m_mktOrders[indx] = order;
    }

    private OrderData fork(OrderData order, String name) {
        double filled = order.m_filled;
        double remained = order.remained();

        log("forking " + name + ": remained=" + remained + ".  " + order);

        OrderData remainedOrder = new OrderData(order.m_pair, order.m_side, order.m_price, remained);
        remainedOrder.m_orderId = order.m_orderId;
        remainedOrder.m_status = OrderStatus.SUBMITTED;
        remainedOrder.m_state = order.m_state;
        log(" new order (remained): " + remainedOrder);

        order.m_state = OrderState.NONE;
        order.m_status = OrderStatus.FILLED;
        order.m_amount = filled;
        log(" existing order: " + order);
        return remainedOrder;
    }

    private OrderData splitOrder(OrderData order, double remainedRatio) {
        double amount = order.m_amount;
        double remained = amount * remainedRatio;
        double filled = amount - remained;

        log("forking order at ratio " + remainedRatio + ". amount=" + amount + "; remained=" + remained + ".  " + order);

        OrderData remainedOrder = new OrderData(order.m_pair, order.m_side, order.m_price, remained);
        remainedOrder.m_orderId = order.m_orderId;
        remainedOrder.m_status = order.m_status;
        remainedOrder.m_state = order.m_state;
        remainedOrder.m_filled = remained;
        log(" new order (remained): " + remainedOrder);

        order.m_amount = filled;
        order.m_filled = filled;
        log(" existing order: " + order);

        return remainedOrder;
    }

    public TriTradeData forkPegOrBracket(String name, TriTradeState state) {
        log("   forking "+name+"..");
        OrderData remainedOrder = fork(m_order, name);
        log("   remainedOrder=" + remainedOrder);
        setState(TriTradeState.PEG_FILLED);
        TriTradeData triTradeData = new TriTradeData(m_startTime, m_id, remainedOrder, m_peg, m_doMktOffset, state);
        log("   created new triTradeData=" + triTradeData);
        return triTradeData;
    }

    public TriTradeData forkMkt(int num /*1 or 2*/) {
        OrderData mktOrder = m_mktOrders[num - 1];
        double amount = mktOrder.m_amount;
        OrderData mktFork = fork(mktOrder, "mkt" + num);
        double ratio = mktFork.m_amount / amount;
        log("  MKT" + num + " order forked at ratio " + ratio + ": " + mktFork);
        OrderData pegOrder = splitOrder(m_order, ratio);
        if (num == 1) {
            TriTradeData ret = new TriTradeData(m_startTime, m_id, pegOrder, m_peg, m_doMktOffset, TriTradeState.MKT1_PLACED);
            ret.setMktOrder(mktFork, 0);
            setState(TriTradeState.MKT1_EXECUTED);
            log("  created fork: " + ret);
            return ret;
        } else { // 2
            OrderData mkt1 = splitOrder(m_mktOrders[0], ratio);
            TriTradeData ret = new TriTradeData(m_startTime, m_id, pegOrder, m_peg, m_doMktOffset, TriTradeState.MKT2_PLACED);
            ret.setMktOrder(mkt1, 0);
            ret.setMktOrder(mktFork, 1);
            setState(TriTradeState.MKT2_EXECUTED);
            log("  created fork: " + ret);
            return ret;
        }
    }

    @Override public String toString() {
        return toString(Triplet.s_exchange);
    }

    public String toString(Exchange exchange) {
        return "TriTradeData[" + m_id + " " + m_peg.name() + " " +
                "state=" + m_state +
                "; order=" + m_order.toString(exchange) +
                (((m_mktOrders != null) && (m_mktOrders[0] != null)) ? "; mktOrder1=" + m_mktOrders[0].toString(exchange) : "") +
                (((m_mktOrders != null) && (m_mktOrders[1] != null)) ? "; mktOrder2=" + m_mktOrders[1].toString(exchange) : "") +
                "]";
    }

    public void log(String s) {
        Log.log(m_id + " " + s);
    }

    public OrderData getMktOrder(int indx) {
        return (m_mktOrders != null) ? m_mktOrders[indx] : null;
    }

    public boolean isMktOrderPartiallyFilled(int indx) {
        OrderData mktOrder = getMktOrder(indx);
        return (mktOrder != null) && mktOrder.isPartiallyFilled(Triplet.s_exchange);
    }

    public TriTradeData forkIfNeeded() {
        TriTradeData forkRemained = null;
        switch (m_state) {
            case PEG_PLACED:
                if(m_order.isPartiallyFilled(Triplet.s_exchange)) {
                    log("PEG order is partially filled - splitting: " + m_order.toString(Triplet.s_exchange));
                    forkRemained = forkPegOrBracket("peg", TriTradeState.PEG_PLACED);
                }
                break;
            case BRACKET_PLACED:
                if(m_order.isPartiallyFilled(Triplet.s_exchange)) {
                    log("BRACKET order is partially filled - splitting: " + m_order.toString(Triplet.s_exchange));
                    forkRemained = forkPegOrBracket("bracket", TriTradeState.BRACKET_PLACED);
                }
                break;
            case MKT1_PLACED:
                if(isMktOrderPartiallyFilled(0)) {
                    log("MKT1 order is partially filled - splitting: " + getMktOrder(0).toString(Triplet.s_exchange));
                    forkRemained = forkMkt(1);
                }
                break;
            case MKT2_PLACED:
                if(isMktOrderPartiallyFilled(1)) {
                    log("MKT2 order is partially filled - splitting: " + getMktOrder(1).toString(Triplet.s_exchange));
                    forkRemained = forkMkt(2);
                }
                break;
            default:
                if(m_order.isPartiallyFilled(Triplet.s_exchange) ||
                    isMktOrderPartiallyFilled(0) ||
                    isMktOrderPartiallyFilled(1)) {
                    log("warning: unexpected state - some order is partially filled: " + this);
                }
        }
        return forkRemained;
    }

    public double level(AccountData account) {
        return m_peg.level();  // 100.602408
    }

    public static class CurrencyAmount {
        public double m_amount;
        public Currency m_currency;

        public CurrencyAmount(double amount, Currency currency) {
            m_amount = amount;
            m_currency = currency;
        }
    }
}
