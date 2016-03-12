package bthdg.osc;

import bthdg.Log;
import bthdg.exch.Direction;

public abstract class TrendWatcher<O> {
    public double m_tolerance;
    public O m_peak;
    private O m_peakCandidate;
    public Direction m_direction;
    private O m_lastValue;

    protected abstract double toDouble(O value);

    protected static void log(String s) { Log.log(s); }

    public TrendWatcher(double tolerance) {
        m_tolerance = tolerance;
    }

    public void update(O value) {
        if (m_peak == null) {
            m_peak = value;
        } else {
            double tolerance = getTolerance(value);
            double doubleValue = toDouble(value);
            if (m_direction == null) {
                double doublePeak = toDouble(m_peak);
                double diff = doubleValue - doublePeak;
                if (diff > tolerance) {
                    m_direction = Direction.FORWARD;
                    m_peakCandidate = value;
                    if(Double.isInfinite(toDouble(m_peakCandidate))) {
                        log("Error: peakCandidate is Infinite 1");
                    }
                } else if (diff < -tolerance) {
                    m_direction = Direction.BACKWARD;
                    m_peakCandidate = value;
                    if(Double.isInfinite(toDouble(m_peakCandidate))) {
                        log("Error: peakCandidate is Infinite 2");
                    }
                }
            } else {
                double doublePeakCandidate = toDouble(m_peakCandidate);
                if(Double.isInfinite(doublePeakCandidate)) {
                    log("Error: peakCandidate is Infinite 3");
                }
                double diff = doubleValue - doublePeakCandidate;
                if (m_direction == Direction.FORWARD) { // going up
                    if (diff > 0) { // new value is above current peak candidate ?
                        m_peakCandidate = value; // got new peak candidate
                        if(Double.isInfinite(toDouble(m_peakCandidate))) {
                            log("Error: peakCandidate is Infinite 4");
                        }
                    } else if (diff < -tolerance) { // new value is below the tolerance level ?
                        m_direction = Direction.BACKWARD; // confirmed new peak - now going down
                        m_peak = m_peakCandidate;
                        m_peakCandidate = value;
                        if(Double.isInfinite(toDouble(m_peakCandidate))) {
                            log("Error: peakCandidate is Infinite 5");
                        }
                        onNewPeak(m_peak, value);
                    }
                } else if (m_direction == Direction.BACKWARD) { // going down
                    if (diff < 0) { // new value is below current peak candidate ?
                        m_peakCandidate = value; // got new peak candidate
                        if(Double.isInfinite(toDouble(m_peakCandidate))) {
                            log("Error: peakCandidate is Infinite 6");
                        }
                    } else if (diff > tolerance) { // new value is above the tolerance level ?
                        m_direction = Direction.FORWARD; // confirmed new peak - now going up
                        m_peak = m_peakCandidate;
                        m_peakCandidate = value;
                        if(Double.isInfinite(toDouble(m_peakCandidate))) {
                            log("Error: peakCandidate is Infinite 7");
                        }
                        onNewPeak(m_peak, value);
                    }
                }
            }
        }
        m_lastValue = value;
    }

    protected double getTolerance(O value) { return getTolerance(toDouble(value)); }
    protected double getTolerance(double value) { return m_tolerance; }
    protected O getLastValue() { return m_lastValue; }
    protected void onNewPeak(O peak, O last) {}

    /** @return 1 when just the peak detected; > 1 if moving in the same direction; */
    public double getDirectionForce() {
        double doubleLast = toDouble(m_lastValue);
        double doublePeak = toDouble(m_peak);
        double tolerance = getTolerance(m_lastValue);
        double distance;
        if (m_direction == Direction.FORWARD) { // going up
            distance = doubleLast - doublePeak;
        } else if (m_direction == Direction.BACKWARD) { // going down
            distance = doublePeak - doubleLast;
        } else {
            // not yet direction
            distance = 0;
        }
        return distance / tolerance;
    }

    //-------------------------------------------------------------------------------
    public static class TrendWatcherDouble extends TrendWatcher<Double> {

        public TrendWatcherDouble(double tolerance) {
            super(tolerance);
        }

        @Override protected double toDouble(Double value) { return value; }
    }
}
