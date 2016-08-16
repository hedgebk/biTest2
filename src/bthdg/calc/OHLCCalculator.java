package bthdg.calc;

import java.util.LinkedList;

public class OHLCCalculator extends BarCalculator {
    protected OHLCTick m_tick;

    public OHLCCalculator(long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
    }

    @Override public boolean updateCurrentBar(long time, double price) {
        if (m_tick != null) {
            return m_tick.update(price);
        }
        return false;
    }
    @Override protected void startNewBar(long barStart, long barEnd) {
        m_tick = new OHLCTick(barStart, barEnd);
    }
    @Override protected void finishCurrentBar(long time, double price) {}


    public static class OHLCFrameCalculator extends OHLCCalculator {
        protected final int m_length;
        protected final LinkedList<OHLCTick> m_bars = new LinkedList<OHLCTick>();
        protected boolean m_filled;

        public OHLCFrameCalculator(int length, long barSize, long barsMillisOffset) {
            super(barSize, barsMillisOffset);
            m_length = length;
        }

        @Override protected void startNewBar(long barStart, long barEnd) {
            super.startNewBar(barStart, barEnd);
            m_bars.addLast(m_tick);
            int size = m_bars.size();
            if (size == m_length) {
                m_filled = true;
            }
            if (size > m_length) {
                m_bars.removeFirst();
            }
        }
    }
}
