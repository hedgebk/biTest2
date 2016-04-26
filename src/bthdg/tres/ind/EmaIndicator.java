package bthdg.tres.ind;

import bthdg.calc.EmaCalculator;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class EmaIndicator extends TresIndicator {
    public static final Color COLOR = Colors.setAlpha(Color.orange, 100);

    private final double m_emaSize;

    public EmaIndicator(String name, TresAlgo algo, double emaSize) {
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


    public int calcLastBarDirectionInt() {
        long lastTime = 0;
        PhasedEmaIndicator lastIndicator = null;
        for (TresPhasedIndicator indicator : m_phasedIndicators) {
            PhasedEmaIndicator emaIndicator = (PhasedEmaIndicator) indicator;
            ChartPoint lastBar = emaIndicator.getLastBar();
            if (lastBar != null) {
                long millis = lastBar.m_millis;
//            long millis = emaIndicator.lastTickTime();
                if (lastTime < millis) {
                    lastTime = millis;
                    lastIndicator = emaIndicator;
                }
            }
        }
        int dir = (lastIndicator != null) ? lastIndicator.directionInt() : 0;
        return dir;
    }

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
            int dir = emaIndicator.calcDirection(); // [-1 | 0 | 1]
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
        public int directionInt() { return m_calculator.directionInt(); }
        @Override public boolean update(TradeDataLight tdata) { return m_calculator.update(tdata); }

        public PhasedEmaIndicator(double emsSize, EmaIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, null);
            long barSize = exchData.m_tres.m_barSizeMillis;
            long barOffset = exchData.m_tres.getBarOffset(phaseIndex);
            m_calculator = new EmaCalculator(emsSize, barSize, barOffset) {
                @Override protected void finishCurrentBar(long time, double price) {
                    super.finishCurrentBar(time, price);
                    if (m_lastEmaValue != null) {
                        ChartPoint tick = new ChartPoint(m_currentBarEnd, m_lastEmaValue);
                        collectPointIfNeeded(tick);
                        onBar(tick);
                    }
                }
            };
        }

        public int calcDirection() { // [-1 | 0 |  1]
            return m_calculator.calcDirection(); // [-1 | 0 | 1]
        }
    }
}
