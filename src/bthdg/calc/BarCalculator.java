package bthdg.calc;

public class BarCalculator {
    private final long m_barSizeMillis;
    private final long m_barsMillisOffset;
    protected long m_currentBarStart;
    protected long m_currentBarEnd;

    public BarCalculator(long barSizeMillis, long barsMillisOffset) {
        m_barSizeMillis = barSizeMillis;
        m_barsMillisOffset = barsMillisOffset;
    }

    public boolean update(long time, double price) {
        if (m_currentBarEnd < time) {
            if (m_currentBarStart != 0) {
                finishCurrentBar(m_currentBarStart, m_currentBarEnd, time, price);
            }
            m_currentBarStart = (time - m_barsMillisOffset) / m_barSizeMillis * m_barSizeMillis + m_barsMillisOffset;
            m_currentBarEnd = m_currentBarStart + m_barSizeMillis;
            startNewBar(m_currentBarStart, m_currentBarEnd);
        }
        boolean updated = updateCurrentBar(time, price);
        return updated;
    }

    protected void startNewBar(long barStart, long barEnd) {}
    protected boolean updateCurrentBar(long time, double price) { return false; }
    protected void finishCurrentBar(long barStart, long barEnd, long time, double price) {}
}
