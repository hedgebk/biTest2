package bthdg.tres.ind;

import bthdg.calc.LinearRegressionPowerCalculator;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class LinearRegressionPowerIndicator extends TresIndicator {
    public static int LENGTH = 25; // "lrp.len"
    public static double POW = 1.0; // "lrp.pow"

    private static final Color PHASED_COLOR = Colors.setAlpha(Color.MAGENTA, 30);
    private static final Color COLOR = Color.MAGENTA;

    @Override public Color getColor() { return COLOR; }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedLinearRegressionPowerIndicator(this, exchData, phaseIndex);
    }

    protected LinearRegressionPowerIndicator(TresAlgo algo) {
        super("LRPI", 0, algo);
    }

    private static class PhasedLinearRegressionPowerIndicator extends TresPhasedIndicator {
        private LinearRegressionPowerCalculator m_calculator;

        @Override public Color getColor() { return PHASED_COLOR; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }
        @Override public double getDirectionAdjusted() { return 0; /*m_calculator.getDirectionAdjusted();*/ }
        @Override public boolean update(TradeDataLight tdata) { return m_calculator.update(tdata); }

        PhasedLinearRegressionPowerIndicator(LinearRegressionPowerIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, null);
            m_calculator = new LinearRegressionPowerCalculator(LENGTH, exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex)) {
                @Override protected void bar(long barEnd, double val) {
                    double updated = Math.signum(val) * Math.pow(Math.abs(val), POW);
                    ChartPoint tick = new ChartPoint(barEnd, updated);
                    collectPointIfNeeded(tick);
                    onBar(tick);
                }
            };
        }
    }
}
