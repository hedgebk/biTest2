package bthdg.calc;

public class OHLCTick {
    public final long m_barStart;
    public final long m_barEnd;
    public double m_open = 0;
    public double m_high = 0;
    public double m_low = Double.MAX_VALUE;
    public double m_close = 0;

    public OHLCTick(long start, long end) {
        m_barStart = start;
        m_barEnd = end;
    }

    /** @return true if any field in OHLC tick was changed */
    public boolean update(double price) {
        boolean updated = false;
        if (m_open == 0) {
            m_open = price;
            updated = true;
        }
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
