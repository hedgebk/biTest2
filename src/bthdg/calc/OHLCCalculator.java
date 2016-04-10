package bthdg.calc;

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
}
