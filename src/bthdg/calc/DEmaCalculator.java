package bthdg.calc;

// Double EmaCalculator
public class DEmaCalculator extends EmaCalculator {
    private Double m_lastEmaEmaValue;
    protected Double m_lastDEmaValue;

    public DEmaCalculator(double emaSize, long barSize, long barsMillisOffset) {
        super(emaSize, barSize, barsMillisOffset);
    }

    @Override protected void finishCurrentBar(long time, double price) {
        super.finishCurrentBar(time, price);
        m_lastEmaEmaValue = calcEmaEma();

        if ((m_lastEmaValue != null) && (m_lastEmaEmaValue != null)) {
            // DEMA = ( 2 * EMA(n)) - (EMA(n) of EMA(n))
            double tEma = 2 * m_lastEmaValue - m_lastEmaEmaValue;
            m_lastDEmaValue = tEma;
        }
    }

//    private Double calcEma() {
//        Double close = (m_close == null) ? m_prevClose : m_close;
//        return calcEma(close);
//    }

    private Double calcEmaEma() {
        return calcEmaEma(m_lastEmaValue);
    }

    private Double calcEmaEma(Double value) {
        if (value == null) {
            return null;
        }
        double prevValue = (m_lastEmaEmaValue == null) ? value : m_lastEmaEmaValue;
        // EMA: {Close - EMA(previous day)} x multiplier + EMA(previous day).
        double ema = (value - prevValue) * m_multiplier + prevValue;
        return ema;
    }
}
