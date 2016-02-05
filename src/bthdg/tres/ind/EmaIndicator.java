package bthdg.tres.ind;

import bthdg.calc.EmaCalculator;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class EmaIndicator extends TresIndicator {
    public static final Color COLOR = Colors.setAlpha(Color.orange, 100);

    private final int m_emaSize;

    public EmaIndicator(String name, TresAlgo algo, int emaSize) {
        super(name, 0, algo);
        m_emaSize = emaSize;
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedEmaIndicator(m_emaSize, this, exchData, phaseIndex);
    }

    @Override protected boolean countPeaks() { return false; }
    @Override public Color getColor() { return COLOR; }
    @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
    @Override protected boolean usePriceAxe() { return true; }

    public double calcDirection() { // [-1 ... 1]
        double ret = 0;
        for (TresPhasedIndicator indicator : m_phasedIndicators) {
            PhasedEmaIndicator emaIndicator = (PhasedEmaIndicator) indicator;
            int dir = emaIndicator.calcDirection(); // [-1 ... 1]
// TODO: do progressive
            ret += dir;
        }
        double avgValue = ret / m_phasedIndicators.size();
        return avgValue;
    }

    public int calcDirectionInt() { // [-1 ... 1]
        double ret = 0;
        for (TresPhasedIndicator indicator : m_phasedIndicators) {
            PhasedEmaIndicator emaIndicator = (PhasedEmaIndicator) indicator;
            int dir = emaIndicator.calcDirection(); // [-1 ... 1]
// TODO: do progressive
            ret += dir;
        }
        return (ret > 0) ? 1 : ((ret < 0) ? -1 : 0);
    }


    // ======================================================================================
    public static class PhasedEmaIndicator extends TresPhasedIndicator {
        private final EmaCalculator m_calculator;

        @Override public Color getColor() { return Color.MAGENTA; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }
        @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }

        public PhasedEmaIndicator(int emsSize, EmaIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, null);
            long barSize = exchData.m_tres.m_barSizeMillis;
            long barOffset = exchData.m_tres.getBarOffset(phaseIndex);
            m_calculator = new EmaCalculator(emsSize, barSize, barOffset) {
                @Override protected void finishCurrentBar(long time, double price) {
                    super.finishCurrentBar(time, price);
                    if (m_lastEmaValue != null) {
                        ChartPoint tick = new ChartPoint(m_currentBarEnd, m_lastEmaValue);
                        if (m_exchData.m_tres.m_collectPoints) {
                            m_points.add(tick); // add to the end
                        }
                        onBar(tick);
                    }
                }
            };
        }

        @Override public boolean update(long timestamp, double price) {
            return m_calculator.update(timestamp, price);
        }

        public int calcDirection() { // [-1 ... 1]
            return m_calculator.calcDirection(); // [-1 ... 1]
        }
    }
}