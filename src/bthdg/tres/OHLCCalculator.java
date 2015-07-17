package bthdg.tres;

import bthdg.exch.TradeData;

public class OHLCCalculator {
    private final long m_barSize;
    private OHLCTick m_tick;
    private final long m_barsMillisOffset;

    protected void onBarFinished(OHLCTick tick) {}
    protected void onBarStarted(OHLCTick tick) {}

    public OHLCCalculator(long barSize, long barsMillisOffset) {
        m_barSize = barSize;
        m_barsMillisOffset = barsMillisOffset;
    }

    public boolean update(TradeData tdata) {
        if(m_tick != null) {
            long timestamp = tdata.m_timestamp;
            if( m_tick.inside(timestamp)  ) {
                return m_tick.update(tdata);
            } else {
                onBarFinished(m_tick);
            }
        }
        startBar(tdata);
        return true;
    }

    private void startBar(TradeData tdata) {
        long timestamp = tdata.m_timestamp;
        long start = (timestamp - m_barsMillisOffset) / m_barSize * m_barSize + m_barsMillisOffset;
        long end = start + m_barSize;
        m_tick = new OHLCTick(start, end, tdata);
        onBarStarted(m_tick);
    }
}
