package bthdg.exch;

import bthdg.util.Utils;

public class TradeData extends TradeDataLight {
    public double m_amount;
    public final long m_tid;
    public final TradeType m_type;

    public TradeData(float amount, float price, long timestamp) {
        this(amount, price, timestamp, 0, null);
    }

    public TradeData(float amount, float price, long timestamp, long tid, TradeType type) {
        super(timestamp, price);
        m_amount = amount;
        m_tid = tid;
        m_type= type;
    }

    @Override public String toString() {
        return "TrData{" +
                "sz=" + Utils.format5(m_amount) +
                ", pr=" + Utils.format5(m_price) +
                ", tm=" + m_timestamp +
                ", tid=" + m_tid +
                ", typ=" + m_type +
                '}';
    }
}
