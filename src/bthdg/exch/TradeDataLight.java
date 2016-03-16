package bthdg.exch;

import bthdg.util.Utils;

import java.io.Serializable;

public class TradeDataLight implements Serializable {
    public final float m_price;
    public final long m_timestamp;

    public TradeDataLight(long timestamp, float price) {
        m_timestamp = timestamp;
        m_price = price;
    }

    @Override public String toString() {
        return "TradeDataLight{price=" + Utils.format5(m_price) +
                ", time=" + m_timestamp + '}';
    }
}
