package bthdg.calc;

public class OHLCCalculator extends BarCalculator {
    protected OHLCTick m_tick;

    public OHLCCalculator(long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
    }

    @Override protected boolean updateCurrentBar(long time, double price) {
        return m_tick.update(price);
    }
    @Override protected void startNewBar(long barStart, long barEnd) {
        m_tick = new OHLCTick(barStart, barEnd);
    }
    @Override protected void finishCurrentBar(long barStart, long barEnd, long time, double price) {}
}
