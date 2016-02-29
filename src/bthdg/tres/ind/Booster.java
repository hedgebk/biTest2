package bthdg.tres.ind;

import bthdg.exch.Direction;
import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;

public class Booster {
    private final TresIndicator m_indicator;
    private final double m_size;
    private Direction m_direction;
    private double m_peak;
    private double m_rate;
    private double m_lastBoosted;

    public Booster(TresIndicator indicator, double size) {
        m_indicator = indicator;
        m_size = size;
    }

    public double update() {
        double ret;
        ChartPoint lastPoint = m_indicator.getLastPoint();
        if (lastPoint != null) {
            TrendWatcher<ChartPoint> peakCalculator = m_indicator.m_peakWatcher.m_avgPeakCalculator;
            Direction direction = peakCalculator.m_direction;
            if (m_direction != direction) { // direction change
                m_peak = peakCalculator.m_peak.m_value;
                double target = m_peak + (direction.isForward() ? m_size : -m_size);
                target = Math.max(Math.min(target, 1), -1);
                m_rate = ((direction.isForward() ? 1 : -1) - m_lastBoosted) / (target - m_peak);
                m_direction = direction;
            }

            double value = lastPoint.m_value;
            ret = (value - m_peak) * m_rate + m_peak;
            ret = Math.max(Math.min(ret, 1), -1);
        } else {
            ret = 0;
        }
        m_lastBoosted = ret;
        return ret;
    }
}
