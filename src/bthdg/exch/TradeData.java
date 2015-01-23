package bthdg.exch;

import bthdg.util.Utils;

public class TradeData {
    public static final TradeData END_MARKER = new TradeData(0, 0, 0, 0, null);

    public double m_amount;
    public final double m_price;
    public final long m_timestamp;
    public final long m_tid;
    public final TradeType m_type;

    public TradeData(double amount, double price, long timestamp) {
        this(amount, price, timestamp, 0, null);
    }

    public TradeData(double amount, double price, long timestamp, long tid, TradeType type) {
        m_amount = amount;
        m_price = price;
        m_timestamp = timestamp;
        m_tid = tid;
        m_type= type;
    }

    @Override public String toString() {
        return "TradeData{" +
                "amount=" + Utils.format5(m_amount) +
                ", price=" + Utils.format5(m_price) +
                ", time=" + m_timestamp +
                ", tid=" + m_tid +
                ", type=" + m_type +
                '}';
    }
}
