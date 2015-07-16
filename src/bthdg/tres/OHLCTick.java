package bthdg.tres;

import bthdg.exch.TradeData;

public class OHLCTick {
    public final long m_barStart;
    public final long m_barEnd;
    public final double m_open;
    public double m_high;
    public double m_low;
    public double m_close;

    public OHLCTick(long start, long end, TradeData tdata) {
        m_barStart = start;
        m_barEnd = end;
        double price = tdata.m_price;
        m_open = price;
        m_high = price;
        m_low = price;
        m_close = price;
    }

    public boolean inside(long timestamp) {
        return (timestamp >= m_barStart) && (timestamp < m_barEnd);
    }

    public boolean update(TradeData tdata) {
        boolean updated = false;
        double price = tdata.m_price;
        if (m_close != price) {
            m_close = price;
            updated = true;
        }
        if (m_high < price) {
            m_high = price;
            updated = true;
        }
        if (m_low > price) {
            m_low = price;
            updated = true;
        }
        return updated;
    }
}
