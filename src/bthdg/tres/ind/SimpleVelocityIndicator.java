package bthdg.tres.ind;

import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Utils;

import java.awt.*;
import java.util.Map;
import java.util.TreeMap;

public class SimpleVelocityIndicator extends TresIndicator {
    private final SimpleVelocityTracker m_velocityTracker;

    public SimpleVelocityIndicator(TresAlgo algo, String name, long frameSizeMillis, double stepRate, int stepsCount, double fading) {
        super(name, 0, algo);
        m_velocityTracker = new SimpleVelocityTracker(frameSizeMillis, stepRate, stepsCount, fading);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
    @Override public Color getColor() { return Color.orange; }
    @Override protected boolean drawZeroLine() { return true; }

    @Override public void addBar(ChartPoint chartPoint) {
        if (chartPoint != null) {
            long millis = chartPoint.m_millis;
            double value = chartPoint.m_value;
            m_velocityTracker.add(millis, value);
            double velocity = m_velocityTracker.getValue();
            ChartPoint smoochPoint = new ChartPoint(millis, velocity);
            super.addBar(smoochPoint);
        }
    }


    // ===============================================================================================
    private static class SimpleVelocityTracker {
        private final Utils.SlidingValuesFrame[] m_frames;
        private final double m_fading;
        private double m_ratesSum;

        public SimpleVelocityTracker(long frameSizeMillis, double stepRate, int stepsCount, double fading) {
            m_fading = fading;
            m_frames = new Utils.SlidingValuesFrame[stepsCount];
            long frameSize = frameSizeMillis;
            double rate = 1;
            for (int i = 0; i < stepsCount; i++) {
                m_frames[i] = new Utils.SlidingValuesFrame(frameSize);
                frameSize = (long) (frameSize * stepRate);
                m_ratesSum += rate;
                rate *= m_fading;
            }
        }

        public void add(long millis, double value) {
            for (Utils.SlidingValuesFrame frame : m_frames) {
                frame.justAdd(millis, value);
            }
        }

        public double getValue() {
            double rate = 1;
            double sum = 0;
            for (Utils.SlidingValuesFrame frame : m_frames) {
                if (frame.m_full) {
                    TreeMap<Long, Double> map = frame.m_map;
                    Map.Entry<Long, Double> old = map.firstEntry();
                    Double oldest = old.getValue();
                    Map.Entry<Long, Double> newest = map.lastEntry();
                    Double latest = newest.getValue();

                    double diff = latest - oldest;
                    sum += (diff * rate);
                    rate *= m_fading;
                }
            }
            sum /= m_ratesSum;
            return sum;
        }
    }
}
