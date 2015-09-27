package bthdg.exch;

import bthdg.util.Utils;

public class TradeDataLight {
    public final double m_price;
    public final long m_timestamp;

    public TradeDataLight(long timestamp, double price) {
        m_timestamp = timestamp;
        m_price = price;
    }

    @Override public String toString() {
        return "TradeDataLight{price=" + Utils.format5(m_price) +
                ", time=" + m_timestamp + '}';
    }
}
