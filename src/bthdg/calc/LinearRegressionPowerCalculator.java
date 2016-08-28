package bthdg.calc;

// Linear Regression Bull and Bear Power indicator
public class LinearRegressionPowerCalculator extends OHLCCalculator.OHLCFrameCalculator {
    private double m_highestIndex;
    private double m_highest;
    private double m_lowestIndex;
    private double m_lowest;
    private double m_bear;
    private double m_bull;
    private double m_val;

    public double getVal() { return m_val; }

    protected void bar(long currentBarEnd, double val) {}

    protected LinearRegressionPowerCalculator(int length, long barSize, long barsMillisOffset) {
        super(length, barSize, barsMillisOffset);
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        super.startNewBar(barStart, barEnd);
        m_highestIndex = -1;
        m_highest = 0.0;
        m_lowestIndex = -1;
        m_lowest = Double.MAX_VALUE;
    }

    @Override public boolean updateCurrentBar(long time, double price) {
        boolean changed = super.updateCurrentBar(time, price);
        if (changed && m_filled) {
            if (m_highestIndex == -1) {
                double highestIndex = 0;
                double highest = 0;
                int index = m_length - 1;
                for (OHLCTick bar : m_bars) {
                    double high = bar.m_high;
                    if (high > highest) {
                        highest = high;
                        highestIndex = index;
                    }
                    index--;
                }
                m_highestIndex = highestIndex;
                m_highest = highest;
            }
            if (m_lowestIndex == -1) {
                double lowestIndex = 0;
                double lowest = Double.MAX_VALUE;
                int index = m_length - 1;
                for (OHLCTick bar : m_bars) {
                    double low = bar.m_low;
                    if (low < lowest) {
                        lowest = low;
                        lowestIndex = index;
                    }
                    index--;
                }
                m_lowestIndex = lowestIndex;
                m_lowest = lowest;
            }
            double close = m_tick.m_close;
            if (close > m_highest) {
                m_highest = close;
                m_highestIndex = 0;
            }
            if (close < m_lowest) {
                m_lowest = close;
                m_lowestIndex = 0;
            }

            m_bear = -f_exp_lr(m_highest - close, -m_highestIndex);
            if (m_bear > 0) {
                m_bear = 0;
            }
            m_bull = f_exp_lr(close - m_lowest, -m_lowestIndex);
            if (m_bull < 0) {
                m_bull = 0;
            }

//            h_value = highest(close, window) // Highest value for a given number of bars back.
//            l_value = lowest(close, window)
//            hbars = highestbars(close, window) // Highest value offset for a given number of bars back
//            lbars = lowestbars(close, window)
//            bearX = 0-f_exp_lr(h_value-close, hbars)
//            bullX = 0+f_exp_lr(close-l_value, lbars)
//            directionX = 2.5*(bullX + bearX)
//
//            f_exp_lr(_height, _length)=>_ret = _height + (_height/_length)

            m_val = calcValue(m_bull, m_bear);

        }
        return changed;
    }

    protected double calcValue(double bull, double bear) {
        return bull + bear;
    }

    private static double f_exp_lr(double height, double length) {
        return (length == 0) ? 0 : height + (height/length);
    }

    @Override protected void finishCurrentBar(long time, double price) {
        super.finishCurrentBar(time, price);
        if (m_filled) {
            bar(m_currentBarEnd, m_val);
        }
    }

    // ----------------------------------------------------------------------------------
    public static class Normalized extends LinearRegressionPowerCalculator {
        protected Normalized(int length, long barSize, long barsMillisOffset) {
            super(length, barSize, barsMillisOffset);
        }

        @Override protected double calcValue(double bull, double bear) {
            double size = bull - bear;
            return (size == 0) ? 0 : (bull + bear) / size;
        }
    }
}
