package bthdg.tres.ind;

import bthdg.calc.DEmaCalculator;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class DoubleEmaIndicator extends TresIndicator {
    public static final Color COLOR = Colors.setAlpha(new Color(230, 181, 130), 100);

    private final double m_emaSize;
    private final Color m_color;

    public DoubleEmaIndicator(String name, TresAlgo algo, double emaSize) {
        this(name, algo, emaSize, COLOR);
    }
    public DoubleEmaIndicator(String name, TresAlgo algo, double emaSize, Color color) {
        super(name, 0, algo);
        m_emaSize = emaSize;
        m_color = color;
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedDoubleEmaIndicator(m_emaSize, this, exchData, phaseIndex);
    }

    @Override protected boolean countPeaks() { return false; }
    @Override public Color getColor() { return m_color; }
//    @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
    @Override protected boolean usePriceAxe() { return true; }

    // ======================================================================================
    public static class PhasedDoubleEmaIndicator extends TresPhasedIndicator {
        private final DEmaCalculator m_calculator;

        @Override public Color getColor() { return Color.MAGENTA; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }
        @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
//        public int directionInt() { return m_calculator.directionInt(); }

        public PhasedDoubleEmaIndicator(double emsSize, DoubleEmaIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, null);
            long barSize = exchData.m_tres.m_barSizeMillis;
            long barOffset = exchData.m_tres.getBarOffset(phaseIndex);
//            m_calculator = new TripleEmaCalculator(emsSize, barSize, barOffset) {
            m_calculator = new DEmaCalculator(emsSize, barSize, barOffset) {
                @Override protected void finishCurrentBar(long time, double price) {
                    super.finishCurrentBar(time, price);
                    if (m_lastDEmaValue != null) {
                        ChartPoint tick = new ChartPoint(m_currentBarEnd, m_lastDEmaValue);
                        collectPointIfNeeded(tick);
                        onBar(tick);
                    }
                }
            };
        }

        @Override public boolean update(long timestamp, double price) {
            return m_calculator.update(timestamp, price);
        }

//        public int calcDirection() { // [-1 | 0 |  1]
//            return m_calculator.calcDirection(); // [-1 | 0 | 1]
//        }
    }
}
