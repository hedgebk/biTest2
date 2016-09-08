package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.exch.TradeDataLight;
import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.EmaIndicator;
import bthdg.tres.ind.SmoochedIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.tres.ind.VelocityIndicator;
import bthdg.util.Colors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;

// Madrid Moving Average Ribbon
public class MmarAlgo extends TresAlgo {
    public static final int FROM = 12;
    public static final int TO = 55;
    public static final int STEP = 2;
    public static final double SMOOCH_RATE = 0.6;
    public static final double VELOCITY_SIZE = 0.8;
    public static final double MID_VELOCITY_SIZE = 0.4;
    public static final double MID_SMOOCH_SIZE = 0.4;
    public static final double AND_SMOOCH_SIZE = 0.1;
    public static final int PREHEAT_BARS = 7;
    public static final double PRICE_SMOOCH_SIZE = 0.25;
    public static final double PRICE_SMOOCH_SIZE2 = 0.5;
    public static final double PRICE_SMOOCH_SIZE3 = 0.75;
    public static final double PRICE_VELOCITY_SIZE = 0.15;
    public static final double PRICE_VELOCITY_SIZE2 = 0.15;
    public static final double PRICE_VELOCITY_SIZE3 = 0.15;

    public static double LEVEL = 0.5;

    final List<EmaIndicatorInt> m_emaIndicators = new ArrayList<EmaIndicatorInt>((TO - FROM) / STEP + 1);
    private VelocityIndicator m_velocityIndicator;
    private SmoochedIndicator m_smoochedFastestIndicator;
    private TresIndicator m_andIndicator;
    private SmoochedIndicator m_smoochedAndIndicator;
    private TresIndicator m_midIndicator;
    private VelocityIndicator m_midVelocityIndicator;
    private SmoochedIndicator m_smoochedMidIndicator;
    private SmoochedIndicator[] m_priceEmaIndicators;
    private EmaIndicator m_fastestEma;
    private EmaIndicator m_midEma;
    private boolean m_doPaintRibbon;
    private Long m_firstBar;
    private Boolean m_lastUp;
    private boolean m_weakDirection;
    private Double m_lastAndDirection = null;

    public MmarAlgo(TresExchData tresExchData) {
        super("Mmar", tresExchData);
        long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        int centerEma = (TO + FROM) / 2;

        for (int emaSize = FROM; emaSize <= TO; emaSize += STEP) {
            final boolean isFastest = (emaSize == FROM);
            final boolean isMid = (m_midEma == null) && (emaSize >= centerEma);

            EmaIndicatorInt ema = isFastest
                ? new EmaIndicatorInt(emaSize) {
                    @Override public Color getColor() { return Color.BLUE; }
                    @Override protected void onBar(ChartPoint chartPoint) {
                        m_smoochedFastestIndicator.addBar(chartPoint);
                    }
                }
                : new EmaIndicatorInt(emaSize) {
                    @Override public Color getColor() { return isMid ? Color.white : super.getColor(); }
                };

            m_indicators.add(ema);
            m_emaIndicators.add(ema);

            if (isFastest) {
                m_fastestEma = ema;
            }
            if (isMid) {
                m_midEma = ema;
            }
        }


        long smoochSize = (long) (SMOOCH_RATE * barSizeMillis);
        m_smoochedFastestIndicator = new SmoochedIndicator(this, "sm", smoochSize, 0.1) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_velocityIndicator.addBar(lastPoint);
            }
//            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean countHalfPeaks() { return false; }
            @Override public Color getColor() { return Color.pink; }
            @Override protected boolean usePriceAxe() { return true; }
        };
        m_indicators.add(m_smoochedFastestIndicator);


        final long velSize = (long) (barSizeMillis * VELOCITY_SIZE);
        m_velocityIndicator = new VelocityIndicator(this, "vel", velSize, 0.3) {
            @Override public String toString() { return "mmar.Vel"; }
            @Override public Color getColor() { return Color.magenta; }
        };
        m_indicators.add(m_velocityIndicator);

        m_andIndicator = new TresIndicator( ".", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.SKY; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean useValueAxe() { return true; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                double value = lastPoint.m_value;
                double sqrtValue = Math.signum(value) * Math.sqrt(Math.abs(value));
                ChartPoint point = new ChartPoint(lastPoint.m_millis, sqrtValue);
                m_smoochedAndIndicator.addBar(point);
            }
        };
        m_indicators.add(m_andIndicator);

        long andSmoochSize = (long) (AND_SMOOCH_SIZE * barSizeMillis);
        m_smoochedAndIndicator = new SmoochedIndicator(this, "and'", andSmoochSize) {
            @Override protected boolean countPeaks() { return false; }
            @Override public Color getColor() { return Color.WHITE; }
            @Override protected boolean useValueAxe() { return true; }
        };
        m_indicators.add(m_smoochedAndIndicator);

        m_midIndicator = new TresIndicator( "m", 0.04, this ) {
            @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.LIGHT_BLUE; }
//        @Override protected boolean countPeaks() { return false; }
            @Override protected boolean countHalfPeaks() { return false; }
            @Override protected boolean usePriceAxe() { return true; }
//        @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_smoochedMidIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_midIndicator);

        long midSmoochSize = (long) (MID_SMOOCH_SIZE * barSizeMillis);
        m_smoochedMidIndicator = new SmoochedIndicator(this, "mid'", midSmoochSize) {
            @Override protected boolean countPeaks() { return false; }
            @Override public Color getColor() { return Colors.TRANSP_LIGHT_CYAN; }
            @Override protected boolean usePriceAxe() { return true; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_midVelocityIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_smoochedMidIndicator);

        final long midVelSize = (long) (barSizeMillis * MID_VELOCITY_SIZE);
        m_midVelocityIndicator = new VelocityIndicator(this, "mv", midVelSize, 0.1) {
            @Override public String toString() { return "Mid.Vel"; }
            @Override public Color getColor() { return Color.magenta; }
        };
        m_indicators.add(m_midVelocityIndicator);

        // ----------
        m_priceEmaIndicators = new SmoochedIndicator[3];
        addPriceFollower(0, PRICE_SMOOCH_SIZE,  PRICE_VELOCITY_SIZE);
        addPriceFollower(1, PRICE_SMOOCH_SIZE2, PRICE_VELOCITY_SIZE2);
        addPriceFollower(2, PRICE_SMOOCH_SIZE3, PRICE_VELOCITY_SIZE3);
    }

    private void addPriceFollower(final int index, double priceSmoochSize, double priceVelocitySize) {
        long barSizeMillis = m_tresExchData.m_tres.m_barSizeMillis;

        final long priceVelSize = (long) (barSizeMillis * priceVelocitySize);
        final VelocityIndicator priceVelocityIndicator = new VelocityIndicator(this, "pv" + index, priceVelSize, 0.000015) {
            @Override public String toString() { return "price.Vel" + index; }
            @Override public Color getColor() { return Color.magenta; }
            @Override protected boolean countPeaks() { return true; }
            @Override protected boolean countHalfPeaks() { return false; }

        };

        final TresIndicator zagIndicator = new TresIndicator("zz" + index, 0, this) {
            @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.DARK_RED; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean usePriceAxe() { return true; }
        };

        final TresIndicator zigIndicator = new TresIndicator("z" + index, 0, this) {
            public ChartPoint m_lastPeak;

            @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.LIGHT_BLUE; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean usePriceAxe() { return true; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                if(m_lastPeak != null) {
                    zagIndicator.addBar(new ChartPoint((chartPoint.m_millis + m_lastPeak.m_millis) / 2, (chartPoint.m_value + m_lastPeak.m_value) / 2));
                }
                m_lastPeak = chartPoint;
            }
        };

        final long frameSizeMillis = (long) (barSizeMillis * priceSmoochSize);
        SmoochedIndicator priceEmaIndicator = new SmoochedIndicator(this, "p" + index, frameSizeMillis, 0.1) {

            @Override protected boolean countHalfPeaks() { return false; }
            @Override protected boolean usePriceAxe() { return true; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                priceVelocityIndicator.addBar(getLastPoint());
            }

            @Override protected void onAvgPeak(TrendWatcher<ChartPoint> trendWatcher) {
                ChartPoint peak = trendWatcher.m_peak;
                zigIndicator.addBar(peak);
            }
        };
        m_indicators.add(priceEmaIndicator);
        m_priceEmaIndicators[index] = priceEmaIndicator;

        m_indicators.add(priceVelocityIndicator);
        m_indicators.add(zigIndicator);
        m_indicators.add(zagIndicator);
    }

    @Override public void preUpdate(TradeDataLight tdata) {
        ChartPoint chartPoint = new ChartPoint(tdata.m_timestamp, tdata.m_price);
        for (SmoochedIndicator priceEmaIndicator : m_priceEmaIndicators) {
            priceEmaIndicator.addBar(chartPoint);
        }
    }

    protected void addAndPoint(ChartPoint chartPoint) {
        double direction = calcLastBarDirection();
//        double direction = calcDirection();

        long millis = chartPoint.m_millis;
//        if ((m_lastAndDirection == null) || (m_lastAndDirection != direction)) {
//System.out.println("direction = " + direction);
            m_andIndicator.addBar(new ChartPoint(millis, direction));
            m_lastAndDirection = direction;
//        }
        Double mid = calcMid();
        if (mid != null) {
            m_midIndicator.addBar(new ChartPoint(millis, mid));
        }
    }

    private double calcLastBarDirection() {
        double sum = 0;
        for (EmaIndicatorInt indicator : m_emaIndicators) {
            int dir = indicator.calcLastBarDirectionInt();
            sum += dir;
        }
        double direction = sum / m_emaIndicators.size();
        return direction;
    }

    double calcDirection() { // [-1 ... 1]
        double sum = 0;
        for (EmaIndicatorInt indicator : m_emaIndicators) {
//            double dir = indicator.calcDirection(); // [-1 ... 1]
            int dir = indicator.calcDirectionInt(); // [-1 | 0 | 1]
//            int dir = indicator.calcDirectionInt2(); // [-1 ... 1]
            sum += dir;
        }
        double direction = sum / m_emaIndicators.size();
        return direction;
    }

    private Double calcMid() {
        Double min = null;
        Double max = null;
        for (EmaIndicatorInt indicator : m_emaIndicators) {
            double lastValue = indicator.m_lastValue;
            if (lastValue == 0) {
                return null; // not full
            }
            min = (min == null) ? lastValue : Math.min(min, lastValue);
            max = (max == null) ? lastValue : Math.max(max, lastValue);
        }
        return (min == null || max == null) ? null : (min + max) / 2;
    }

    Boolean up() {
        Double fast = m_fastestEma.calcAvgValue();
        if (fast != null) {
            Double min = null;
            Double max = null;
            for (EmaIndicator indicator : m_emaIndicators) {
                Double val = indicator.calcAvgValue();
                if (val != null) {
                    min = (min == null) ? val : Math.min(min, val);
                    max = (max == null) ? val : Math.max(max, val);
                }
            }
            if ((min != null) && (max != null)) {
                double mid = (max + min) / 2;
                double radius = (max - min) / 2;
                double level = (fast - mid) / radius; // [-1...1]
                Boolean up = (level > LEVEL) ? Boolean.TRUE : ((level < -LEVEL) ? Boolean.FALSE : null);

                if (m_lastUp == null) {
                    if (up != null) {
                        m_lastUp = up;
                        m_weakDirection = false;
                    }
                } else if (m_lastUp) { // up -> ?
                    if (up == null) { // up -> weak down
                        if (!m_weakDirection) {
                            m_lastUp = Boolean.FALSE;
                            m_weakDirection = true;
                        }
                    } else if (up) {
                        m_weakDirection = false;
                    } else { // down
                        m_lastUp = Boolean.FALSE;
                        m_weakDirection = false;
                    }
                } else { // down -> ?
                    if (up == null) { // down -> weak up
                        if (!m_weakDirection) {
                            m_lastUp = Boolean.TRUE;
                            m_weakDirection = true;
                        }
                    } else if (!up) {
                        m_weakDirection = false;
                    } else { // up
                        m_lastUp = Boolean.TRUE;
                        m_weakDirection = false;
                    }
                }
                return m_lastUp;
            }
        }
//        Double fast = m_fastestEma.calcAvgValue();
//        if (fast != null) {
//            Double mid = m_midEma.calcAvgValue();
//            if (mid != null) {
//                return Boolean.valueOf(fast > mid);
//            }
//        }
        return null;
    }

    @Override public double lastTickPrice() { return m_fastestEma.lastTickPrice(); }
    @Override public long lastTickTime() { return m_fastestEma.lastTickTime(); }
    @Override public Color getColor() { return Color.LIGHT_GRAY; }
    @Override public String getRunAlgoParams() { return "MMAR"; }

    @Override public double getDirectionAdjusted() {
        if(m_smoochedAndIndicator == null) {
            return 0;
        }
        ChartPoint lastPoint = m_smoochedAndIndicator.getLastPoint();
        if (lastPoint == null) {
            return 0;
        } else {
            long millis = lastPoint.m_millis;
            if (m_firstBar == null) {
                m_firstBar = millis;
            }
            double passed = millis - m_firstBar;
            double preheatRate = passed / (PREHEAT_BARS * m_tresExchData.m_tres.m_barSizeMillis);
            if (preheatRate > 1) {
                preheatRate = 1;
            }
            return lastPoint.m_value * preheatRate;
        }
    }

    @Override public Direction getDirection() {
        double dir = getDirectionAdjusted();
        return (dir == 0) ? null : Direction.get(dir);
    }

//    @Override public double getDirectionAdjusted() {
//        return calcDirection();
//    }
//
//    @Override public Direction getDirection() {
//        double dir = calcDirection();
//        return (dir == 0) ? null : Direction.get(dir);
//    }

//    @Override public double getDirectionAdjusted() {
//        Boolean up = up();
//        return (up == null) ? 0 : (up ? 1 : -1);
//    }
//
//    @Override public Direction getDirection() {
//        Boolean up = up();
//        return (up == null) ? null : Direction.get(up);
//    }

    @Override public JComponent getController(final TresCanvas canvas) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        panel.setBackground(getColor());
        JCheckBox ribbonChBx = new JCheckBox("ribbon", m_doPaintRibbon) {
            @Override protected void fireItemStateChanged(ItemEvent event) {
                super.fireItemStateChanged(event);
                m_doPaintRibbon = (event.getStateChange() == ItemEvent.SELECTED);
                canvas.repaint();
            }
        };
        panel.add(ribbonChBx);

        for (TresIndicator indicator : m_indicators) {
            if (!m_emaIndicators.contains(indicator)) {
                JComponent controller = indicator.getController(canvas);
                panel.add(controller);
            }
        }

        return panel;
    }

    // ======================================================================================
    private class EmaIndicatorInt extends EmaIndicator {
        double m_prevValue = 0;
        double m_lastValue = 0;

        public EmaIndicatorInt(int emaSize) {
            super("mmar", MmarAlgo.this, emaSize);
        }

        @Override protected boolean doPaint() { return m_doPaintRibbon; }

        @Override public void addBar(ChartPoint chartPoint) {
            if (chartPoint != null) {
                double value = chartPoint.m_value;
                if ((m_lastValue == 0) || (m_lastValue != value)) {
                    super.addBar(chartPoint);
                    m_prevValue = m_lastValue;
                    m_lastValue = value;
                    addAndPoint(chartPoint);
                    onBar(chartPoint);
                    notifyListener();
                }
            }
        }

        protected void onBar(ChartPoint chartPoint) {}

        public int calcDirectionInt2() {
            return (m_prevValue==0 || m_lastValue==0)
                    ? 0
                    : m_lastValue > m_prevValue
                        ? 1
                        : m_lastValue < m_prevValue ? -1 : 0;
        }
    }
}
