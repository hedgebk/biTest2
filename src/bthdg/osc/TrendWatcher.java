package bthdg.osc;

import bthdg.exch.Direction;

public abstract class TrendWatcher<O> {
    public double m_tolerance;
    O m_peak;
    private O m_peakCandidate;
    public Direction m_direction;

    protected abstract double toDouble(O value);

    public TrendWatcher(double tolerance) {
        m_tolerance = tolerance;
    }

    public void update(O value) {
        if (m_peak == null) {
            m_peak = value;
//            onNewPeak(m_peak);
        } else {
            double tolerance = getTolerance(value);
            double doubleValue = toDouble(value);
            if (m_direction == null) {
                double doublePeak = toDouble(m_peak);
                double diff = doubleValue - doublePeak;
                if (diff > tolerance) {
                    m_direction = Direction.FORWARD;
                    m_peakCandidate = value;
                } else if (diff < -tolerance) {
                    m_direction = Direction.BACKWARD;
                    m_peakCandidate = value;
                }
            } else {
                double doublePeakCandidate = toDouble(m_peakCandidate);
                double diff = doubleValue - doublePeakCandidate;
                if (m_direction == Direction.FORWARD) {
                    if (diff > 0) {
                        m_peakCandidate = value;
                    } else if (diff < -tolerance) {
                        m_direction = Direction.BACKWARD;
                        m_peak = m_peakCandidate;
                        m_peakCandidate = value;
                        onNewPeak(m_peak, value);
                    }
                } else if (m_direction == Direction.BACKWARD) {
                    if (diff < 0) {
                        m_peakCandidate = value;
                    } else if (diff > tolerance) {
                        m_direction = Direction.FORWARD;
                        m_peak = m_peakCandidate;
                        m_peakCandidate = value;
                        onNewPeak(m_peak, value);
                    }
                }
            }
        }
    }

    protected double getTolerance(O value) { return getTolerance(toDouble(value)); }
    protected double getTolerance(double value) { return m_tolerance; }
    protected void onNewPeak(O peak, O last) {}

    //-------------------------------------------------------------------------------
    public static class TrendWatcherDouble extends TrendWatcher<Double> {

        public TrendWatcherDouble(double tolerance) {
            super(tolerance);
        }

        @Override protected double toDouble(Double value) { return value; }
    }
}
