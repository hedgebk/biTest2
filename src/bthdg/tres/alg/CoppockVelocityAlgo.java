package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.Log;
import bthdg.exch.Direction;
import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;
import bthdg.tres.Tres;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CursorPainter;
import bthdg.tres.ind.SmoochedIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.tres.ind.VelocityIndicator;

import java.awt.*;

public class CoppockVelocityAlgo extends CoppockAlgo {
    public static double PEAK_TOLERANCE  = 0.000000005; // cov_vel:
    public static double FRAME_RATIO = 0.5;             // CovRat: smoother frame ratio
    public static double DIRECTION_CUT_LEVEL = 0.48;     // cov_k: 0.998;
    public static double RANGE_SIZE = 0.002;
    public static final double RANGE_MAX = DIRECTION_CUT_LEVEL + RANGE_SIZE / 2;

    private final VelocityIndicator m_velocityIndicator;
    private final VelocitySmoochedIndicator m_velocitySmoochedIndicator;
//    private final SmoochedIndicator m_smoochedIndicator;
//    private final VelocityIndicator m_smoochedVelocityIndicator;
    private final CursorPainter m_cursorPainter;
    private final AndIndicator m_andIndicator;

    private static void log(String s) { Log.log(s); }

    public CoppockVelocityAlgo(TresExchData tresExchData) {
        super("COPVEL", tresExchData);
        m_cursorPainter = new CursorPainter(m_coppockIndicator);

        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        m_velocityIndicator = new VelocityIndicator(this, "vel", barSizeMillis, PEAK_TOLERANCE) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_velocitySmoochedIndicator.addBar(lastPoint);
            }
            @Override public String toString() { return "CoppockVelocityAlgo.VelocityIndicator"; }
        };
        m_indicators.add(m_velocityIndicator);

        m_velocitySmoochedIndicator = new VelocitySmoochedIndicator(this, barSizeMillis) {
            public Direction m_lastDirection;

            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double value = calcDirectionAdjusted();
                    long millis = lastPoint.m_millis;
                    ChartPoint andPoint = new ChartPoint(millis, value);
                    m_andIndicator.addBar(andPoint);

                    Direction direction = getDirection();
                    if (direction != m_lastDirection) {
                        notifyListener();
                    }
                    m_lastDirection = direction;
                }
            }
            @Override public String toString() { return "CoppockVelocityAlgo.VelocitySmoochedIndicator"; }
            @Override protected boolean centerYZeroLine() { return true; }
        };
        m_indicators.add(m_velocitySmoochedIndicator);

//        m_smoochedIndicator = new SmoochedIndicator(this, "sm", FRAME_RATIO * barSizeMillis, CoppockIndicator.PEAK_TOLERANCE) {
//            private CursorPainter m_cursorPainter = new CursorPainter(this);
//
//            @Override public void addBar(ChartPoint chartPoint) {
//                super.addBar(chartPoint);
//                m_smoochedVelocityIndicator.addBar(getLastPoint());
//            }
//
//            @Override public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
//                super.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
//                g.setColor(Colors.LIGHT_MAGNETA);
//                m_cursorPainter.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
//            }
//        };
//        m_indicators.add(m_smoochedIndicator);
//
//        m_smoochedVelocityIndicator = new VelocityIndicator(this, "smVel", barSizeMillis);
//        m_indicators.add(m_smoochedVelocityIndicator);

        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);

        if (Tres.LOG_PARAMS) {
            log("CoppockVelocityAlgo");
            log(" PEAK_TOLERANCE=" + PEAK_TOLERANCE);
            log(" FRAME_RATIO=" + FRAME_RATIO);
            log(" RANGE_SIZE=" + RANGE_SIZE);
            log(" DIRECTION_CUT_LEVEL=" + DIRECTION_CUT_LEVEL);
        }
    }

    @Override public void paintAlgo(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        super.paintAlgo(g, xTimeAxe, yPriceAxe, cursorPoint);
        g.setColor(Color.WHITE);
        m_cursorPainter.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
    }

    @Override protected void onCoppockBar() {
        ChartPoint lastPoint = m_coppockIndicator.getLastPoint();
        if (lastPoint != null) {
            m_velocityIndicator.addBar(lastPoint);
//            m_smoochedIndicator.addBar(lastPoint);
        }
    }

    @Override public Color getColor() { return Color.BLUE; }
    @Override public double getDirectionAdjusted() {
        return m_velocitySmoochedIndicator.calcDirectionAdjusted();
    }
    @Override public Direction getDirection() {
        return m_velocitySmoochedIndicator.getDirection();
    }

    @Override public String getRunAlgoParams() {
        return "CopVel: tolerance=" + m_coppockIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
                + " RANGE_SIZE=" + RANGE_SIZE
                + " DIRECTION_CUT_LEVEL=" + DIRECTION_CUT_LEVEL
                + " FRAME_RATIO=" + FRAME_RATIO
                + " PEAK_TOLERANCE=" + PEAK_TOLERANCE;
    }


    // ================================================================================
    private static class VelocitySmoochedIndicator extends SmoochedIndicator {
        public State m_state = State.NONE;
        private double m_lastPeak;

        public VelocitySmoochedIndicator(TresAlgo algo, long barSizeMillis) {
            super(algo, "velSm", (long) (CoppockVelocityAlgo.FRAME_RATIO * barSizeMillis), PEAK_TOLERANCE);
        }

        @Override protected boolean countHalfPeaks() { return false; }
        @Override protected boolean drawZeroLine() { return true; }
        @Override protected boolean centerYZeroLine() { return true; }

        public double calcDirectionAdjusted() {
            if (m_state != null) {
                return m_state.calcDirectionAdjusted(this);
            }
            return 0;
        }

        public Direction getDirection() {
            if (m_state != null) {
                return m_state.getDirection(this);
            }
            return null;
        }

        @Override protected void onAvgPeak(TrendWatcher<ChartPoint> trendWatcher) {
            super.onAvgPeak(trendWatcher);
            Direction direction = trendWatcher.m_direction;
            ChartPoint peak = trendWatcher.m_peak;
//ChartPoint last = getLastPoint();
//log("velocitySmoochedIndicator.onAvgPeak(trendWatcher="+trendWatcher+") " +
//        "peak[t=" + peak.m_millis + ", v=" + Utils.format8(peak.m_value) + "]; " +
//        "last[t=" + last.m_millis + ", v=" + Utils.format8(last.m_value) + "]; " +
//        "direction=" + direction);
            double peakValue = peak.m_value;
            State state = direction.isForward() ? State.UP : State.DOWN;
            if (m_state != state) {
                m_lastPeak = peakValue;
                State oldState = m_state;
                m_state = state;
//double directionAdjusted = calcDirectionAdjusted();
//log(" state " + oldState + " -> " + state + "; lastPeak=" + m_lastPeak + "; directionAdjusted="+directionAdjusted);
            } else { // same direction peak again - update if bigger
                double newPeakAbs = Math.abs(peakValue);
                double lastPeakAbs = Math.abs(m_lastPeak);
                if (newPeakAbs > lastPeakAbs) {
                    m_lastPeak = peakValue; // update with farthest
                } else {
                    m_lastPeak = (m_lastPeak + peakValue) / 2; // update with nearest
                }
            }
        }
    }

    // ================================================================================
    public enum State {
        NONE {
        }, DOWN {
            @Override public double calcDirectionAdjusted(VelocitySmoochedIndicator indicator) {
                return calcDirectionAdjusted(indicator, 1.0);
            }
            @Override public Direction getDirection(VelocitySmoochedIndicator indicator) {
                return getDirectionInt(indicator, 1.0);
            }

        }, UP {
            @Override public double calcDirectionAdjusted(VelocitySmoochedIndicator indicator) {
                return calcDirectionAdjusted(indicator, -1.0);
            }
            @Override public Direction getDirection(VelocitySmoochedIndicator indicator) {
                return getDirectionInt(indicator, -1.0);
            }
        };

        protected Direction getDirectionInt(VelocitySmoochedIndicator indicator, double mul) {
//            double adjusted = calcDirectionAdjusted(indicator, mul);
//            return (adjusted >= 0) ? Direction.FORWARD : Direction.BACKWARD;

            double lastPeak = indicator.m_lastPeak;
            double lastValue = indicator.getLastPoint().m_value;
            double signum = Math.signum(lastPeak);
            if (((mul < 0) && (signum < 0)) || ((mul > 0) && (signum > 0))) {
                double range = lastPeak * DIRECTION_CUT_LEVEL;
                return (lastValue >= range) ? Direction.FORWARD : Direction.BACKWARD;
            } else {
                double range = lastPeak * (2 - DIRECTION_CUT_LEVEL);
                return (lastValue <= range) ? Direction.BACKWARD : Direction.FORWARD;
            }
        }

        protected static double calcDirectionAdjusted(VelocitySmoochedIndicator indicator, double mul) {
            double lastPeak = indicator.m_lastPeak;
            double rangeSize = RANGE_SIZE * lastPeak;
            double lastValue = indicator.getLastPoint().m_value;
            double signum = Math.signum(lastPeak);

            double val;
            if (((mul < 0) && (signum < 0)) || ((mul > 0) && (signum > 0))) {
                double rangeStart = lastPeak * RANGE_MAX;
                val = mul * (1.0 - 2 * (rangeStart - lastValue) / rangeSize);
            } else {
                double rangeStart = lastPeak * (2 - RANGE_MAX);
                val = -mul * (1 - 2 * (rangeStart - lastValue) / rangeSize);
            }

            return Math.min(Math.max(val, -1), 1);
        }

        public double calcDirectionAdjusted(VelocitySmoochedIndicator indicator) {
            return 0;
        }

        public Direction getDirection(VelocitySmoochedIndicator indicator) {
            return null;
        }
    }


    // =============================================================================================
    public static class AndIndicator extends TresIndicator {
        public static double PEAK_TOLERANCE = 0.06470;

        public AndIndicator(TresAlgo algo) {
            super("+", PEAK_TOLERANCE, algo);
        }

        @Override protected boolean countPeaks() { return false; }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.red; }
        @Override public Color getPeakColor() { return Color.red; }
    }
}
