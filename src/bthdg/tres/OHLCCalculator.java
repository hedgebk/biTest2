package bthdg.tres;

import bthdg.exch.TradeData;

public class OHLCCalculator {
    private final long m_barSize;
    private OHLCTick m_tick;

    protected void finishBar(OHLCTick tick) {}

    public OHLCCalculator(long barSize) {
        m_barSize = barSize;
    }

    public boolean update(TradeData tdata) {
        if(m_tick != null) {
            long timestamp = tdata.m_timestamp;
            if( m_tick.inside(timestamp)  ) {
                return m_tick.update(tdata);
            } else {
                finishBar(m_tick);
            }
        }
        startBar(tdata);
        return true;
    }

    private void startBar(TradeData tdata) {
        long timestamp = tdata.m_timestamp;
        long start = timestamp / m_barSize * m_barSize;
        long end = start + m_barSize;
        m_tick = new OHLCTick(start, end, tdata);
    }
}
