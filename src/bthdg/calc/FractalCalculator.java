package bthdg.calc;

public class FractalCalculator extends OHLCCalculator {
    protected OHLCTick m_tick1;
    protected OHLCTick m_tick2;
    protected OHLCTick m_tick3;
    protected OHLCTick m_tick4;
    private boolean m_allTicksDefined;
    public double m_direction;

    protected void onDirectionChanged(long time, double m_direction) {}

    public FractalCalculator(long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        m_tick4 = m_tick3;
        m_tick3 = m_tick2;
        m_tick2 = m_tick1;
        m_tick1 = m_tick;
        super.startNewBar(barStart, barEnd);
    }

    @Override public boolean updateCurrentBar(long time, double price) {
        boolean ret = super.updateCurrentBar(time, price);
        if (ret) { // if any ohlc field was changed
            if (!m_allTicksDefined) {
                m_allTicksDefined = (m_tick4 != null) && (m_tick3 != null) && (m_tick2 != null) && (m_tick1 != null) && (m_tick != null);
            }
            if (m_allTicksDefined) {

//            ? (high[4] < high[2] and high[3] <= high[2] and high[2] >= high[1] and high[2] > high[0])
//            or (high[4] < high[3] and high[3] >= high[2] and high[3] > high[1] and high[2] > high[0] and high[1] > high[0])
//            or (high[4] >= high[3] and high[3] > high[2] and high[2] > high[1] and high[1] > high[0])
                double high0 = m_tick.m_high;
                double high1 = m_tick1.m_high;
                double high2 = m_tick2.m_high;
                double high3 = m_tick3.m_high;
                double high4 = m_tick4.m_high;
                boolean filteredTopF = false;
                if ((high4 < high2 && high3 <= high2 && high2 >= high1 && high2 > high0)
                        || (high4 < high3 && high3 >= high2 && high3 > high1 && high2 > high0 && high1 > high0)
                        || (high4 >= high3 && high3 > high2 && high2 > high1 && high1 > high0)
                        ) {
                    filteredTopF = true;
                }

//            ? (low[4] > low[2] and low[3] >= low[2] and low[2] <= low[1] and low[2] < low[0])
//            or (low[4] > low[3] and low[3] <= low[2] and low[3] < low[1] and low[2] < low[0] and low[1] < low[0])
//            or (low[4] <= low[3] and low[3] <= low[2] and low[2] < low[1] and low[1] < low[0])
                double low0 = m_tick.m_low;
                double low1 = m_tick1.m_low;
                double low2 = m_tick2.m_low;
                double low3 = m_tick3.m_low;
                double low4 = m_tick4.m_low;
                boolean filteredBottomF = false;
                if ((low4 > low2 && low3 >= low2 && low2 <= low1 && low2 < low0)
                        || (low4 > low3 && low3 <= low2 && low3 < low1 && low2 < low0 && low1 < low0)
                        || (low4 <= low3 && low3 <= low2 && low2 < low1 && low1 < low0)
                        ) {
                    filteredBottomF = true;
                }

                double direction = (filteredTopF && !filteredBottomF)
                                        ? -1
                                        : (filteredBottomF && !filteredTopF)
                                            ? 1
                                            : 0;
                if (m_direction != direction) {
                    m_direction = direction;
                    onDirectionChanged(time, m_direction);
                }
            }
        }
        return ret;
    }

//    @Override protected void finishCurrentBar(long time, double price) {
//        if (m_lastCci != null) {
//            bar(m_currentBarEnd, m_lastCci);
//        }
//    }
}
