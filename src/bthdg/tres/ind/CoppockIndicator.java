package bthdg.tres.ind;

import bthdg.ChartAxe;
import bthdg.calc.CoppockCalculator;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;

// http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:coppock_curve
public class CoppockIndicator extends TresIndicator {
    public static double PEAK_TOLERANCE = 0.007433;
    public static final Color COPPOCK_AVG_COLOR = Color.CYAN;
    public static final Color COPPOCK_AVG_PEAKS_COLOR = Color.WHITE;
    public static final Color COPPOCK_COLOR = Colors.setAlpha(COPPOCK_AVG_COLOR, 25);
    public static final Color COPPOCK_PEAKS_COLOR = Colors.setAlpha(COPPOCK_AVG_PEAKS_COLOR, 60);

    @Override public Color getColor() { return COPPOCK_AVG_COLOR; }
    @Override public Color getPeakColor() { return COPPOCK_AVG_PEAKS_COLOR; }
    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedCoppockIndicator(this, exchData, phaseIndex);
    }

    public CoppockIndicator(TresAlgo algo) {
        super("Cop", PEAK_TOLERANCE, algo);
    }

    @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
        double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
        minMaxCalculator.m_minValue = -max;
        minMaxCalculator.m_maxValue = max;
    }

    @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
        g.setColor(COPPOCK_COLOR);
        int y = yAxe.getPointReverse(0);
        g.drawLine(xTimeAxe.getPoint(xTimeAxe.m_min), y, xTimeAxe.getPoint(xTimeAxe.m_max), y);
    }

    public static class PhasedCoppockIndicator extends TresPhasedIndicator {
        public static int WMA_LENGTH = 13; // 10
        public static int SHORT_ROС_LENGTH = 11;
        public static int LONG_ROC_LENGTH = 15; // 14

        private final CoppockCalculator m_calculator;

        @Override public Color getColor() { return COPPOCK_COLOR; }
        @Override public Color getPeakColor() { return COPPOCK_PEAKS_COLOR; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }

        public PhasedCoppockIndicator(CoppockIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, PEAK_TOLERANCE);
            m_calculator = new CoppockCalculator(WMA_LENGTH, LONG_ROC_LENGTH, SHORT_ROС_LENGTH,
                                                 exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex)) {
                @Override protected void bar(long barEnd, double value) {
                    ChartPoint tick = new ChartPoint(barEnd, value);
                    if (m_exchData.m_tres.m_collectPoints) {
                        m_points.add(tick); // add to the end
                    }
                    m_peakCalculator.update(tick);
                    onBar(tick);
                }

                @Override protected void setLastTickTimePrice(long time, double price) {
                    m_indicator.setLastTickTimePrice(time, price);
                }
            };
        }

        @Override public boolean update(long timestamp, double price) {
            return m_calculator.update(timestamp, price);
        }
    }
}
