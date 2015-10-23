package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.Utils;

import java.awt.*;
import java.util.Map;

public class CoppockVelocityAlgo extends CoppockAlgo {
    private static final long RATIO = 2;

    private final SmoochedIndicator m_smoochedIndicator;
    private final VelocityIndicator m_velocityIndicator;

    public CoppockVelocityAlgo(TresExchData tresExchData) {
        super(tresExchData);
        long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        m_smoochedIndicator = new SmoochedIndicator(this, RATIO * barSizeMillis) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    m_velocityIndicator.addBar(lastPoint);
                }
            }
        };
        m_indicators.add(m_smoochedIndicator);

        m_velocityIndicator = new VelocityIndicator(this, barSizeMillis);
        m_indicators.add(m_velocityIndicator);
    }

    @Override protected void onCoppockBar() {
        ChartPoint lastPoint = m_coppockIndicator.getLastPoint();
        if (lastPoint != null) {
            m_smoochedIndicator.addBar(lastPoint);
        }
    }


    private static class VelocityTracker extends Utils.SlidingValuesFrame {
        public VelocityTracker(long frameSizeMillis) {
            super(frameSizeMillis);
        }

        public double getValue() {
            if (m_map.size() > 1) {
                Map.Entry<Long, Double> oldest = m_map.firstEntry();
                Map.Entry<Long, Double> last = m_map.lastEntry();
                double diff = last.getValue() - oldest.getValue();
                if (!m_full) {
                    long timeDiff = last.getKey() - oldest.getKey();
                    double fullRatio = ((double) timeDiff) / m_frameSizeMillis;
                    diff *= fullRatio;
                }
                return diff;
            }
            return 0; // parked
        }
    }


    public static class VelocityIndicator extends TresIndicator {
        private final VelocityTracker m_velocityTracker;

        public VelocityIndicator(TresAlgo algo, long frameSizeMillis) {
            super("v", 0.1, algo);
            m_velocityTracker = new VelocityTracker(frameSizeMillis);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.orange; }
        @Override public Color getPeakColor() { return Color.orange; }

        @Override public void addBar(ChartPoint chartPoint) {
            long millis = chartPoint.m_millis;
            double value = chartPoint.m_value;
            m_velocityTracker.justAdd(millis, value);
            double velocity = m_velocityTracker.getValue();
            ChartPoint smoochPoint = new ChartPoint(millis, velocity);
            super.addBar(smoochPoint);
        }

        @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
            g.setColor(Color.orange);
            int y = yAxe.getPointReverse(0);
            g.drawLine(xTimeAxe.getPoint(xTimeAxe.m_min), y, xTimeAxe.getPoint(xTimeAxe.m_max), y);
        }
    }

    public static class SmoochedIndicator extends TresIndicator {
        public static double PEAK_TOLERANCE = CoppockIndicator.PEAK_TOLERANCE;

        private final Utils.FadingAverageCounter m_avgCounter;

        public SmoochedIndicator(TresAlgo algo, long frameSizeMillis) {
            super("s", PEAK_TOLERANCE, algo);
            m_avgCounter = new Utils.FadingAverageCounter(frameSizeMillis);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.green; }
        @Override public Color getPeakColor() { return Color.green; }

        @Override public void addBar(ChartPoint chartPoint) {
            long millis = chartPoint.m_millis;
            double avg = m_avgCounter.add(millis, chartPoint.m_value);
            ChartPoint smoochPoint = new ChartPoint(millis, avg);
            super.addBar(smoochPoint);
        }

        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }
    }
}
