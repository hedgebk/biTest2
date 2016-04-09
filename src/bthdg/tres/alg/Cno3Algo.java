package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.*;
import bthdg.util.Utils;

import java.awt.*;

public class Cno3Algo extends TresAlgo {
    private static final Color COLOR = new Color(30, 100, 73);
    public static double SMOOTH_RATE = 0.6; // "cno3.smooch"
    public static final double AND_PEAK_TOLERANCE = 0.13;
    public static double SMOOTH_PEAK_TOLERANCE = 0.18; // "cno3.peak"

    private final CciIndicator m_cciIndicator;
    private final CciIndicator.CciAdjustedIndicator m_cciAdjustedIndicator;
    private final OscIndicator m_oscIndicator;
    private final MidIndicator m_midIndicator;
//    private final AndIndicator m_andIndicator;
    protected final AndIndicator m_andIndicator2;
    protected final SmoochedIndicator m_smoochedIndicator;

    @Override public Color getColor() { return COLOR; }

    public Cno3Algo(TresExchData tresExchData) {
        this("C3O", tresExchData);
    }

    public Cno3Algo(String name, TresExchData tresExchData) {
        super(name, tresExchData);
        m_cciIndicator = new CciIndicator(this) {
            @Override protected void onBar() {
                super.onBar();
                ChartPoint cci = getLastPoint();
                m_cciAdjustedIndicator.addBar(cci);
            }
        };
        m_indicators.add(m_cciIndicator);

        m_cciAdjustedIndicator = new CciIndicator.CciAdjustedIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint cci = getLastPoint();
                m_midIndicator.addBar1(cci);
            }
        };
        m_indicators.add(m_cciAdjustedIndicator);

        m_oscIndicator = new OscIndicator(this/*, OSC_TOLERANCE*/) { // use def OSC_TOLERANCE for now
            @Override protected void onBar() {
                super.onBar();
                ChartPoint osc = getLastPoint();
                m_midIndicator.addBar2(osc);
            }
        };
        m_indicators.add(m_oscIndicator);

        m_midIndicator = new MidIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                if (chartPoint != null) {
                    super.addBar(new ChartPoint(chartPoint.m_millis, (chartPoint.m_value - 0.5) * 2));
                    ChartPoint osc = getLastPoint();
//                    m_andIndicator.addBar(osc);
                    m_andIndicator2.addBar(osc);
                }
            }
        };
        m_indicators.add(m_midIndicator);

//        m_andIndicator = new AndIndicator(this) {
//            @Override public void addBar(ChartPoint chartPoint) {
//                super.addBar(chartPoint);
//                ChartPoint osc = getLastPoint();
//                m_smoochedIndicator.addBar(osc);
//            }
//        };
//        m_indicators.add(m_andIndicator);

        m_andIndicator2 = new AndIndicator(this, "+'") {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint osc = getLastPoint();
                m_smoochedIndicator.addBar(osc);
            }

            @Override protected double updateOutValue(double inValue) {
                double outValue = inValue * 2; // [-2 ... 2]
                if(outValue > 0) {
                    outValue -= 1;
                } else {
                    outValue += 1;
                }
                return outValue;
            }
        };
        m_indicators.add(m_andIndicator2);

        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;
        m_smoochedIndicator = new SmoochedIndicator(this, "sm", (long) (SMOOTH_RATE * barSizeMillis), SMOOTH_PEAK_TOLERANCE) {
            @Override protected boolean useValueAxe() { return true; }
            //            @Override protected boolean countPeaks() { return false; }
            @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
                double max = Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue));
                minMaxCalculator.m_minValue = -max;
                minMaxCalculator.m_maxValue = max;
            }
        };
        m_indicators.add(m_smoochedIndicator);
    }

    @Override public double lastTickPrice() {
        return (m_oscIndicator.lastTickTime() > m_cciIndicator.lastTickTime())
                ? m_oscIndicator.lastTickPrice()
                : m_cciIndicator.lastTickPrice();
    }

    @Override public long lastTickTime() {
        return Math.max(m_oscIndicator.lastTickTime(), m_cciIndicator.lastTickTime());
    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        ChartPoint lastPoint = m_smoochedIndicator.getLastPoint();
        return (lastPoint == null) ? 0 : lastPoint.m_value;
    }
    @Override public Direction getDirection() {  // UP/DOWN
        return m_smoochedIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
//        return m_andIndicator2.m_peakWatcher.m_avgPeakCalculator.m_direction;
    }

    @Override public String getRunAlgoParams() {
        return "Cno3";
//                + " Mid.tlrnc=" + m_midIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
//                + " osc.tlrnc=" + m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
//                + " cci.tlrnc=" + m_cciIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance;
    }


    // ======================================================================================
    public static class AndIndicator extends TresIndicator {
        private double m_maxAbs;
        private double m_lastInValue = 0;

        public AndIndicator(TresAlgo algo) {
            this(algo, "+");
        }
        public AndIndicator(TresAlgo algo, String name) {
            super(name, AND_PEAK_TOLERANCE, algo);
        }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.CYAN; }
        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }

        @Override public void addBar(ChartPoint chartPoint) {
            if (chartPoint != null) {
                double inValue = chartPoint.m_value;
                double abs = Math.abs(inValue);
                double outValue;
                if (((m_lastInValue > 0) && (inValue > 0)) || ((m_lastInValue < 0) && (inValue < 0))) { // same sign
                    m_maxAbs = Math.max(m_maxAbs, abs);
                    outValue = inValue / m_maxAbs; // [-1...1]
                    outValue = updateOutValue(outValue);
                } else { // different sign - zero cross
                    m_maxAbs = abs;
                    outValue = Math.signum(inValue); // 1 | -1
                }
                m_lastInValue = inValue;
                super.addBar(new ChartPoint(chartPoint.m_millis, outValue));
            }
        }

        protected double updateOutValue(double outValue) { return outValue; }
    }

    // ======================================================================================
    public static class Cno3SharpAlgo extends Cno3Algo {

        public Cno3SharpAlgo(TresExchData tresExchData) {
            super("C3O!", tresExchData);
        }

        @Override public double getDirectionAdjusted() { // [-1 ... 1]
            Direction direction = m_smoochedIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
//            Direction direction = m_andIndicator2.m_peakWatcher.m_avgPeakCalculator.m_direction;
            return (direction == null)
                    ? 0
                    : direction.isForward() ? 1 : -1;
        }
    }

    // ======================================================================================
    public static class Cno3FastAlgo extends Cno3Algo {

        public Cno3FastAlgo(TresExchData tresExchData) {
            super("C3Of", tresExchData);
        }

        @Override public double getDirectionAdjusted() { // [-1 ... 1]
            return getDirectionAdjustedByPeakWatchers(m_smoochedIndicator);
        }
    }
}
