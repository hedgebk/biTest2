package bthdg.exch;

import bthdg.util.Utils;

public class TradeData {
    public double m_amount;
    public final double m_price;
    public final long m_timestamp;
    public final long m_tid;
    public final TradesData.TradeType m_type;
    public final OrderSide m_orderSide;

    public TradeData(double amount, double price, long timestamp, long tid, TradesData.TradeType type) {
        this(amount, price, timestamp, tid, type, null);
    }

    public TradeData(double amount, double price, long timestamp, long tid, TradesData.TradeType type, OrderSide orderSide) {
        m_amount = amount;
        m_price = price;
        m_timestamp = timestamp;
        m_tid = tid;
        m_type= type;
        m_orderSide = orderSide;
    }

    @Override public String toString() {
        return "TradeData{" +
                "amount=" + Utils.format5(m_amount) +
                ", price=" + Utils.format5(m_price) +
                ", time=" + m_timestamp +
                ", tid=" + m_tid +
                ", type=" + m_type +
                ((m_orderSide != null) ? ", orderSide=" + m_orderSide : "") +
                '}';
    }
}
