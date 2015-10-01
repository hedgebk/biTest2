package bthdg.calc;

import java.util.LinkedList;

public abstract class BarCalculator {
    private final long m_barSizeMillis;
    private final long m_barsMillisOffset;
    protected long m_currentBarStart;
    protected long m_currentBarEnd;

    public BarCalculator(long barSizeMillis, long barsMillisOffset) {
        if(barSizeMillis == 0) {
            throw new RuntimeException( "barSizeMillis==0" );
        }
        m_barSizeMillis = barSizeMillis;
        m_barsMillisOffset = barsMillisOffset;
    }

    public boolean update(long time, double price) {
        if (m_currentBarEnd < time) {
            if (m_currentBarStart != 0) {
                finishCurrentBar(time, price);
            }
            m_currentBarStart = (time - m_barsMillisOffset) / m_barSizeMillis * m_barSizeMillis + m_barsMillisOffset;
            m_currentBarEnd = m_currentBarStart + m_barSizeMillis;
            startNewBar(m_currentBarStart, m_currentBarEnd);
        }
        boolean updated = updateCurrentBar(time, price);
        return updated;
    }

    protected abstract void startNewBar(long barStart, long barEnd);
    protected abstract boolean updateCurrentBar(long time, double price);
    protected abstract void finishCurrentBar(long time, double price);

    protected static void replaceLastElement(LinkedList<Double> list, double price) {
        if (!list.isEmpty()) {
            list.set(list.size() - 1, price); // replace last element
        }
    }
}
