package bthdg.triplet;

import bthdg.*;

public class TriTradeData {
    public OrderData m_order;
    public OrderData[] m_mktOrders;
    public OnePegCalcData m_peg;
    public TriTradeState m_state = TriTradeState.PEG_PLACED;
    public int m_waitMktOrderStep;

    public TriTradeData(OrderData order, OnePegCalcData peg) {

double price = order.m_price;
double priceFromPeg = peg.m_price1;
double rate = price/priceFromPeg;
if((rate < 0.7) || (1.3 < rate)) {
    log("error: got: rate=" + rate);
}
        m_order = order;
        m_peg = peg;
    }

    public void checkState(IterationData iData, TriangleData triangleData) throws Exception {
        m_state.checkState(iData, triangleData, this);
    }

    public void setState(TriTradeState state) {
        log("TriTradeData.setState() " + m_state + " -> " + state);
        m_state = state;
    }

    public void setMktOrder(OrderData order, int indx) {
        if (m_mktOrders == null) {
            m_mktOrders = new OrderData[2];
        }
        m_mktOrders[indx] = order;
        setState((indx == 0) ? TriTradeState.MKT1_PLACED : TriTradeState.MKT2_PLACED);
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

    public TriTradeData forkPeg() {
        OrderData order = m_order;
        OrderData remainedOrder = fork(order, "peg");
        setState(TriTradeState.PEG_FILLED);
        return new TriTradeData(remainedOrder, m_peg);
    }

    public TriTradeData forkMkt(int num /*1 or 2*/) {
        OrderData mktOrder = m_mktOrders[num - 1];
        double amount = mktOrder.m_amount;
        OrderData mktFork = fork(mktOrder, "mkt" + num);
        double ratio = mktFork.m_amount / amount;
        log("  mkt" + num + " order forked at ratio " + ratio + ": " + mktFork);
        OrderData pegOrder = splitOrder(m_order, ratio);
        if (num == 1) {
            TriTradeData ret = new TriTradeData(pegOrder, m_peg);
            ret.setMktOrder(mktFork, 0);
            setState(TriTradeState.MKT1_EXECUTED);
            return ret;
        } else { // 2
            OrderData mkt1 = splitOrder(m_mktOrders[0], ratio);
            TriTradeData ret = new TriTradeData(pegOrder, m_peg);
            ret.setMktOrder(mkt1, 0);
            ret.setMktOrder(mktFork, 1);
            setState(TriTradeState.MKT2_EXECUTED);
            return ret;
        }
    }

    @Override public String toString() {
        return "TriTradeData[" + m_peg.name() + " " +
                "state=" + m_state +
                "; order=" + m_order +
                (((m_mktOrders != null) && (m_mktOrders[0] != null)) ? "; mktOrder1=" + m_mktOrders[0] : "") +
                (((m_mktOrders != null) && (m_mktOrders[1] != null)) ? "; mktOrder2=" + m_mktOrders[1] : "") +
                "]";
    }

    private static void log(String s) {
        Log.log(s);
    }

    public OrderData getMktOrder(int indx) {
        return (m_mktOrders != null) ? m_mktOrders[indx] : null;
    }

    public boolean isMktOrderPartiallyFilled(int indx) {
        OrderData mktOrder = getMktOrder(indx);
        return (mktOrder != null) && mktOrder.isPartiallyFilled(Exchange.BTCE);
    }

}
