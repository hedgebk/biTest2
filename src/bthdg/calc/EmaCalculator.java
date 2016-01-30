package bthdg.calc;


public class EmaCalculator extends CloseCalculator {
    private final double m_multiplier;
    public Double m_pervValue;
    private Double m_prevClose;

    public EmaCalculator(int emaSize, long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
        // Multiplier: (2 / (Time periods + 1) ) = (2 / (10 + 1) ) = 0.1818 (18.18%)
        m_multiplier = 2.0/ (emaSize + 1);
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        m_prevClose = m_close;
        super.startNewBar(barStart, barEnd);
    }

    @Override protected void finishCurrentBar(long time, double price) {
        Double close = (m_close == null) ? m_prevClose : m_close;
        Double ema = calcEma(close);
        m_pervValue = ema;
    }

    private Double calcEma(Double value) {
        if (value == null) {
            return null;
        }
        double prevValue = (m_pervValue == null) ? value : m_pervValue;
        // EMA: {Close - EMA(previous day)} x multiplier + EMA(previous day).
        double ema = (value - prevValue) * m_multiplier + prevValue;
        return ema;
    }
}
