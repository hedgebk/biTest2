package bthdg.calc;

public class CloseCalculator extends BarCalculator {
    protected Double m_close;

    public CloseCalculator(long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
    }

    @Override public boolean updateCurrentBar(long time, double price) {
        m_close = price;
        return true;
    }
    @Override protected void startNewBar(long barStart, long barEnd) {
        m_close = null;
    }
    @Override protected void finishCurrentBar(long time, double price) {  /*noop*/  }
}
