package bthdg.calc;


public class EmaCalculator extends CloseCalculator {
    protected final double m_multiplier;
    private Double m_prevEmaValue;
    public Double m_lastEmaValue;
    private Double m_prevClose;

    public EmaCalculator(double emaSize, long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
        // Multiplier: (2 / (Time periods + 1) ) = (2 / (10 + 1) ) = 0.1818 (18.18%)
        m_multiplier = 2.0 / (emaSize + 1);
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        m_prevClose = m_close;
        super.startNewBar(barStart, barEnd);
    }

    @Override protected void finishCurrentBar(long time, double price) {
        Double ema = calcEma();
        m_prevEmaValue = m_lastEmaValue;
        m_lastEmaValue = ema;
    }

    private Double calcEma() {
        Double close = (m_close == null) ? m_prevClose : m_close;
        return calcEma(close);
    }

    private Double calcEma(Double value) {
        if (value == null) {
            return null;
        }
        double prevValue = (m_lastEmaValue == null) ? value : m_lastEmaValue;
        // EMA: {Close - EMA(previous day)} x multiplier + EMA(previous day).
        double ema = (value - prevValue) * m_multiplier + prevValue;
        return ema;
    }

    public int calcDirection() { // [-1 ... 1]
        Double ema = calcEma();
        if ((m_prevEmaValue == null) || (ema == null)) {
            return 0;
        }
        double diff = ema - m_prevEmaValue;
        return (diff > 0) ? 1 : ((diff < 0) ? -1 : 0);
    }

    public int directionInt() {
        if ((m_prevEmaValue == null) || (m_lastEmaValue == null)) {
            return 0;
        }
        double diff = m_lastEmaValue - m_prevEmaValue;
        return (diff > 0) ? 1 : ((diff < 0) ? -1 : 0);
    }


}
