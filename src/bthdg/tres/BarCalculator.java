package bthdg.tres;

import bthdg.exch.TradeData;

public class BarCalculator {
    private final long m_barSizeMillis;
    private final long m_barsMillisOffset;
    private long m_currentBarStart;
    private long m_currentBarEnd;

    public BarCalculator(long barSizeMillis, long barsMillisOffset) {
        m_barSizeMillis = barSizeMillis;
        m_barsMillisOffset = barsMillisOffset;
    }

    public boolean update(TradeData tdata) {
        long timestamp = tdata.m_timestamp;
        if (m_currentBarEnd < timestamp) {
            if (m_currentBarStart != 0) {
                finishCurrentBar(m_currentBarStart, m_currentBarEnd, tdata);
            }
            m_currentBarStart = (timestamp - m_barsMillisOffset) / m_barSizeMillis * m_barSizeMillis + m_barsMillisOffset;
            m_currentBarEnd = m_currentBarStart + m_barSizeMillis;
            startNewBar(m_currentBarStart, m_currentBarEnd);
        }
        boolean updated = updateCurrentBar(tdata);
        return updated;
    }

    protected boolean updateCurrentBar(TradeData tdata) { return false; }
    protected void startNewBar(long barStart, long barEnd) {}
    protected void finishCurrentBar(long barStart, long barEnd, TradeData tdata) {}
}
