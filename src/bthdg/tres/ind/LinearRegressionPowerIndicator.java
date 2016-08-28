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

    private final int m_len;

    @Override public Color getColor() { return COLOR; }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedLinearRegressionPowerIndicator(this, exchData, phaseIndex);
    }

    protected LinearRegressionPowerIndicator(TresAlgo algo) {
        this(algo, LENGTH);
    }

    protected LinearRegressionPowerIndicator(TresAlgo algo, int len) {
        super("LRPI", 0, algo);
        m_len = len;
    }

    // ---------------------------------------------------------------------------------
    protected static class PhasedLinearRegressionPowerIndicator extends TresPhasedIndicator {
        protected LinearRegressionPowerCalculator m_calculator;

        @Override public Color getColor() { return PHASED_COLOR; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }
        @Override public double getDirectionAdjusted() { return 0; /*m_calculator.getDirectionAdjusted();*/ }
        @Override public boolean update(TradeDataLight tdata) { return m_calculator.update(tdata); }

        protected PhasedLinearRegressionPowerIndicator(LinearRegressionPowerIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, null);
            long barOffset = exchData.m_tres.getBarOffset(phaseIndex);
            long barSizeMillis = exchData.m_tres.m_barSizeMillis;
            m_calculator = createCalculator(indicator, barOffset, barSizeMillis);
        }

        protected LinearRegressionPowerCalculator createCalculator(final LinearRegressionPowerIndicator indicator, final long barOffset, final long barSizeMillis) {
            return new LinearRegressionPowerCalculator(indicator.m_len, barSizeMillis, barOffset) {
                @Override protected void bar(long barEnd, double val) {
                    onBar(barEnd, val);
                }
            };
        }

        void onBar(long barEnd, double val) {
            double updated = (POW == 1.0) ? val : (Math.signum(val) * Math.pow(Math.abs(val), POW));
            ChartPoint tick = new ChartPoint(barEnd, updated);
            collectPointIfNeeded(tick);
            onBar(tick);
        }

        // ---------------------------------------------------------------------------------
        private static class Normalized extends PhasedLinearRegressionPowerIndicator {
            Normalized(LinearRegressionPowerIndicator indicator, TresExchData exchData, int phaseIndex) {
                super(indicator, exchData, phaseIndex);
            }

            @Override protected LinearRegressionPowerCalculator.Normalized createCalculator(final LinearRegressionPowerIndicator indicator, final long barOffset, final long barSizeMillis) {
                return new LinearRegressionPowerCalculator.Normalized(indicator.m_len, barSizeMillis, barOffset) {
                    @Override protected void bar(long barEnd, double val) {
                        onBar(barEnd, val);
                    }
                };
            }
        }
    }

    // ---------------------------------------------------------------------------------
    public class Normalized extends LinearRegressionPowerIndicator {

        protected Normalized(TresAlgo algo) {
            super(algo);
        }

        protected Normalized(TresAlgo algo, int len) {
            super(algo, len);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
            return new PhasedLinearRegressionPowerIndicator.Normalized(this, exchData, phaseIndex);
        }
    }
}
