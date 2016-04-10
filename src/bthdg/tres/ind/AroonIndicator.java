package bthdg.tres.ind;

import bthdg.calc.AroonCalculator;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

// http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon
public class AroonIndicator extends TresIndicator {
    public static double PEAK_TOLERANCE = 0.5;
    public static int LENGTH = 38; //21 , 15

    public static final Color PHASED_COLOR = Colors.setAlpha(Color.MAGENTA, 25);
    public static final Color COLOR = Color.MAGENTA;

    private final double m_barRatio;

    @Override public Color getColor() { return COLOR; }
    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedAroonIndicator(this, exchData, phaseIndex, m_barRatio);
    }

    public AroonIndicator(String name, TresAlgo algo, double barRatio) {
        super(name, PEAK_TOLERANCE, algo);
        m_barRatio = barRatio;
    }


    // ======================================================================================
    public static class PhasedAroonIndicator extends TresPhasedIndicator {
        private AroonCalculator m_calculator;

        @Override public Color getColor() { return PHASED_COLOR; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }
        @Override public double getDirectionAdjusted() { return m_calculator.getDirectionAdjusted(); }

        public PhasedAroonIndicator(AroonIndicator indicator, TresExchData exchData, int phaseIndex, double barRatio) {
            super(indicator, exchData, phaseIndex, PEAK_TOLERANCE);
            long barSize = (long) (exchData.m_tres.m_barSizeMillis * barRatio);
            m_calculator = new AroonCalculator(LENGTH, barSize, exchData.m_tres.getBarOffset(phaseIndex)) {
                @Override protected void bar(long barEnd, double value) {
                    ChartPoint tick = new ChartPoint(barEnd, value);
                    collectPointIfNeeded(tick);
                    m_peakCalculator.update(tick);
                    onBar(tick);
                }
            };
        }

        @Override public boolean update(TradeDataLight tdata) {
            long timestamp = tdata.m_timestamp;
            double price = tdata.m_price;
            return m_calculator.update(timestamp, price);
        }
    }
}
