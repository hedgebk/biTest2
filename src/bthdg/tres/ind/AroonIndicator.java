package bthdg.tres.ind;

import bthdg.calc.AroonCalculator;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

// http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon
public class AroonIndicator extends TresIndicator {
    public static double PEAK_TOLERANCE = 0.1;
    public static final Color PHASED_COLOR = Colors.setAlpha(Color.MAGENTA, 25);
    public static final Color COLOR = Color.MAGENTA;

    @Override public Color getColor() { return COLOR; }
    @Override protected boolean countPeaks() { return false; }
    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedAroonIndicator(this, exchData, phaseIndex);
    }

    public AroonIndicator(TresAlgo algo) {
        super( "ar", PEAK_TOLERANCE, algo);
    }


    // ======================================================================================
    public static class PhasedAroonIndicator extends TresPhasedIndicator {
        public static int LENGTH = 42; // 14

        private AroonCalculator m_calculator;

        @Override public Color getColor() { return PHASED_COLOR; }
        @Override public Color getPeakColor() { return PHASED_COLOR; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }
        @Override public double getDirectionAdjusted() { return m_calculator.getDirectionAdjusted(); }

        public PhasedAroonIndicator(AroonIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, PEAK_TOLERANCE);
            m_calculator = new AroonCalculator(LENGTH, exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex)) {
                @Override protected void bar(long barEnd, double value) {
                    ChartPoint tick = new ChartPoint(barEnd, value);
                    if (m_exchData.m_tres.m_collectPoints) {
                        m_points.add(tick); // add to the end
                    }
                    m_peakCalculator.update(tick);
                    onBar(tick);
                }
            };
        }

        @Override public boolean update(long timestamp, double price) {
            return m_calculator.update(timestamp, price);
        }
    }
}
