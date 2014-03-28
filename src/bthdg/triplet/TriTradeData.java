package bthdg.triplet;

import bthdg.Log;
import bthdg.OrderData;
import bthdg.OrderState;
import bthdg.OrderStatus;

public class TriTradeData {
    public OrderData m_order;
    public OrderData[] m_mktOrders;
    public OnePegCalcData m_peg;
    public TriTradeState m_state = TriTradeState.PEG_PLACED;
    public int m_waitMktOrder;

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

    public TriTradeData forkPegIfNeeded() {
        if (m_order.isPartiallyFilled()) {
            double filled = m_order.m_filled;
            double remained = m_order.remained();

            log("splitting peg: remained=" + remained + ".  " + m_order);

            OrderData remainedOrder = new OrderData(m_order.m_pair, m_order.m_side, m_order.m_price, remained);
            remainedOrder.m_orderId = m_order.m_orderId;
            remainedOrder.m_status = OrderStatus.SUBMITTED;
            remainedOrder.m_state = m_order.m_state;
            log(" new order (remained): " + remainedOrder);

            TriTradeData triTrade2 = new TriTradeData(remainedOrder, m_peg);

            m_order.m_state = OrderState.NONE;
            m_order.m_status = OrderStatus.FILLED;
            m_order.m_amount = filled;
            log(" existing order: " + m_order);

            m_state = TriTradeState.PEG_FILLED;

            return triTrade2;
        }
        if (m_mktOrders != null) {
            forkIfNeededOnMktOrder(0);
            forkIfNeededOnMktOrder(1);
        }
        return null;
    }

    private void forkIfNeededOnMktOrder(int i) {
        OrderData order = m_mktOrders[i];
        if (order != null) {
            if (order.isPartiallyFilled()) {
                log(" warning: not implemented: partially filled mkt order, split too: " + order);
                // todo: fork
            }
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
}
