package bthdg.calc;


public class AroonCalculator extends OHLCCalculator.OHLCFrameCalculator {
    private Double m_lastHigh;
    private Double m_lastLow;
    public Double m_aroonUp;
    public Double m_aroonDown;
    public Double m_aroonOscillator; // between -1 and 1

    public double getDirectionAdjusted() { return (m_aroonOscillator == null) ? 0 : m_aroonOscillator; }

    protected void bar(long barEnd, double value) {}

    public AroonCalculator(int length, long barSize, long barsMillisOffset) {
        super(length, barSize, barsMillisOffset);
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        super.startNewBar(barStart, barEnd);
        m_lastHigh = 0.0;
        m_lastLow = Double.MAX_VALUE;
    }

    @Override protected void finishCurrentBar(long time, double price) {
        super.finishCurrentBar(time, price);
        if (m_aroonOscillator != null) {
            bar(m_currentBarEnd, m_aroonOscillator);
        }
    }

    @Override public boolean updateCurrentBar(long time, double price) {
        boolean changed = super.updateCurrentBar(time, price);
        if (changed && m_filled) {
            double close = m_tick.m_close;
            if (m_lastHigh < close) {
                m_lastHigh = close;
                // If the MAX occurred M days ago, calculate:
                //  Aroon(UP) = 100 (1 - M/10)   ... it will be between 0 and 100
                double maxIndex = 0;
                double max = 0;
                int index = m_length - 1;
                for (OHLCTick bar : m_bars) {
                    double high = bar.m_high;
                    if (high > max) {
                        max = high;
                        maxIndex = index;
                    }
                    index--;
                }
                //  it will be between 0 and 1
                double aroonUp = 1 - maxIndex / (m_length - 1);
                if ((m_aroonUp == null) || (m_aroonUp != aroonUp)) {
                    m_aroonUp = aroonUp;
                    recalcOscillator();
                }
            }
            if (m_lastLow > close) {
                m_lastLow = close;
                // If the MIN occurred N days ago, calculate:
                //  Aroon(DOWN) = 100 (1 - N/10)   ... it will also be between 0 and 100
                double minIndex = 0;
                double min = Double.MAX_VALUE;
                int index = m_length - 1;
                for (OHLCTick bar : m_bars) {
                    double low = bar.m_low;
                    if (low < min) {
                        min = low;
                        minIndex = index;
                    }
                    index--;
                }
                //  it will be between 0 and 1
                double aroonDown = 1 - minIndex / (m_length - 1);
                if ((m_aroonDown == null) || (m_aroonDown != aroonDown)) {
                    m_aroonDown = aroonDown;
                    recalcOscillator();
                }
            }
        }
        return changed;
    }

    private void recalcOscillator() {
        // Aroon(Oscillator) = Aroon(UP) - Aroon(DOWN)
        //  It'll be between -100 and 100
        if ((m_aroonDown != null) && (m_aroonUp != null)) {
            m_aroonOscillator = m_aroonUp - m_aroonDown;
        }
    }
}
