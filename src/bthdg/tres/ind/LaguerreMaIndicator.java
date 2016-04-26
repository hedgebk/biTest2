package bthdg.tres.ind;

import bthdg.calc.LaguerreMaCalculator;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class LaguerreMaIndicator extends TresIndicator {
    public static final Color COLOR = Colors.setAlpha(new Color(230, 149, 156), 100);

    private final double m_factor;
    private final Color m_color;

    public LaguerreMaIndicator(String name, TresAlgo algo, double factor) {
        this(name, algo, factor, COLOR);
    }
    public LaguerreMaIndicator(String name, TresAlgo algo, double factor, Color color) {
        super(name, 0, algo);
        m_factor = factor;
        m_color = color;
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedLaguerreMaIndicator(m_factor, this, exchData, phaseIndex);
    }

    @Override protected boolean countPeaks() { return false; }
    @Override public Color getColor() { return m_color; }
//    @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
    @Override protected boolean usePriceAxe() { return true; }

    // ======================================================================================
    public static class PhasedLaguerreMaIndicator extends TresPhasedIndicator {
        private final LaguerreMaCalculator m_calculator;

        @Override public Color getColor() { return Color.MAGENTA; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }
        @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
        @Override public boolean update(TradeDataLight tdata) { return m_calculator.update(tdata); }
//        public int directionInt() { return m_calculator.directionInt(); }

        public PhasedLaguerreMaIndicator(double factor, LaguerreMaIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, null);
            long barSize = exchData.m_tres.m_barSizeMillis;
            long barOffset = exchData.m_tres.getBarOffset(phaseIndex);
//            m_calculator = new TripleEmaCalculator(emsSize, barSize, barOffset) {
            m_calculator = new LaguerreMaCalculator(factor, barSize, barOffset) {
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

//        public int calcDirection() { // [-1 | 0 |  1]
//            return m_calculator.calcDirection(); // [-1 | 0 | 1]
//        }
    }
}
