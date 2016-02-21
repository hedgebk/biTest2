package bthdg.calc;


public class LaguerreMaCalculator extends CloseCalculator {
    public static final int BARS_UNTIL_START = 22;

    public double m_factor;
    private Double m_prevEmaValue;
    public Double m_lastEmaValue;
    private Double m_prevClose;
    private double prevL0;
    private double prevL1;
    private double prevL2;
    private double prevL3;
    private int m_barsCount = 0;

    public LaguerreMaCalculator(double factor, long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
        m_factor = factor;
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        m_prevClose = m_close;
        super.startNewBar(barStart, barEnd);
    }

    @Override protected void finishCurrentBar(long time, double price) {
        Double ema = calcEma();
        if(m_barsCount++ < BARS_UNTIL_START) {
            ema = m_close;
        }
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
        double L0 = (1 - m_factor) * value + m_factor * prevL0;
        double L1 = -m_factor * L0 + prevL0 + m_factor * prevL1;
        double L2 = -m_factor * L1 + prevL1 + m_factor * prevL2;
        double L3 = -m_factor * L2 + prevL2 + m_factor * prevL3;
        double laguerreMa = (L0 + 2 * L1 + 2 * L2 + L3) / 6;
        prevL0 = L0;
        prevL1 = L1;
        prevL2 = L2;
        prevL3 = L3;
        return laguerreMa;
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
