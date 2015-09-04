package bthdg.calc;

import bthdg.util.Utils;

import java.util.LinkedList;

public class CciCalculator extends OHLCCalculator {
    private final int m_smaLength;
    private final LinkedList<Double> m_smaPrices = new LinkedList<Double>();
    private boolean m_smaFilled;
    private Double m_lastCci;

    protected void fine(long time, double cci) { }
    protected void bar(long barStart, double cci) { }

    public CciCalculator(int smaLength, long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
        m_smaLength = smaLength;
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        super.startNewBar(barStart, barEnd);
        if (m_smaFilled) {
            m_smaPrices.removeFirst();
            m_smaPrices.addLast(0d); // add last
        } else {
            m_smaPrices.addLast(0d); // add last
            int sizeShort = m_smaPrices.size();
            if (sizeShort == m_smaLength) {
                m_smaFilled = true;
            }
        }
    }

    @Override protected boolean updateCurrentBar(long time, double price) {
        boolean ret = super.updateCurrentBar(time, price);
        double typicalPrice = m_tick.m_close; // this can be HLC or OHLC
        replaceLastElement(m_smaPrices, typicalPrice);
        if (m_smaFilled) {
            double sma = Utils.avg(m_smaPrices);
            double medianDeviation = medianDeviation(sma);
            double cci = (typicalPrice-sma)/(0.015*medianDeviation);
            fine(time, cci);
            m_lastCci = cci;
            return true;
        }
        return ret;
    }

    private double medianDeviation(double average) {
        double sum = 0;
        for (double sma : m_smaPrices) {
            double deviation = Math.abs(average - sma);
            sum += deviation;
        }
        return sum / m_smaLength;
    }

    @Override protected void finishCurrentBar(long barStart, long barEnd, long time, double price) {
        if (m_lastCci != null) {
            bar(m_currentBarStart, m_lastCci);
        }
    }
}
