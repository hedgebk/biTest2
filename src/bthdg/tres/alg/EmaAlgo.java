package bthdg.tres.alg;

import bthdg.exch.Direction;
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


public class EmaAlgo extends TresAlgo {
    public static final int FROM = 5;
    public static final int TO = 110;
    public static final int STEP = 5;

    public static double LEVEL = 0.5;

    final List<EmaIndicatorInt> m_emaIndicators = new ArrayList<EmaIndicatorInt>((TO - FROM) / STEP + 1);
    private final VelocityIndicator m_velocityIndicator;
    private final SmoochedIndicator m_smoochedFastestIndicator;
    private final AndIndicator m_andIndicator;
    private EmaIndicator m_fastestEma;
    private EmaIndicator m_midEma;
    private boolean m_doPaintRibbon;

    public EmaAlgo(TresExchData tresExchData) {
        super("EMA", tresExchData);

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

        long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        long smoochSize = (long) (0.1 * barSizeMillis);
        m_smoochedFastestIndicator = new SmoochedIndicator(this, "sm", smoochSize, 0) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_velocityIndicator.addBar(lastPoint);
            }
            @Override protected boolean countPeaks() { return false; }
            @Override public Color getColor() { return Color.pink; }
//            @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) { drawZeroHLine(g, xTimeAxe, yAxe); }
        };
        m_indicators.add(m_smoochedFastestIndicator);


        final long velSize = (long) (barSizeMillis * 0.25);
        m_velocityIndicator = new VelocityIndicator(this, "vel", velSize, 0.1) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
//                ChartPoint lastPoint = getLastPoint();
//                m_velocitySmoochedIndicator.addBar(lastPoint);
            }
            @Override public String toString() { return "Ema.Vel"; }
            @Override public Color getColor() { return Color.magenta; }
        };
        m_indicators.add(m_velocityIndicator);

        m_andIndicator = new AndIndicator( this );
        m_indicators.add(m_andIndicator);
    }

    Double m_lastAndDirection = null;

    protected void addAndPoint(ChartPoint chartPoint) {
        double direction = calcDirection();
        if ((m_lastAndDirection == null) || (m_lastAndDirection != direction)) {
System.out.println("direction = " + direction);
            m_andIndicator.addBar(new ChartPoint(chartPoint.m_millis, direction));
            m_lastAndDirection = direction;
        }
    }


    private Boolean m_lastUp;
    private boolean m_weakDirection;

    double calcDirection() { // [-1 ... 1]
        double sum = 0;
        for (EmaIndicatorInt indicator : m_emaIndicators) {
//            double dir = indicator.calcDirection(); // [-1 ... 1]
//            int dir = indicator.calcDirectionInt(); // [-1 ... 1]
            int dir = indicator.calcDirectionInt2(); // [-1 ... 1]
            sum += dir;
        }
        double direction = sum / m_emaIndicators.size();
        return direction;
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
    @Override public String getRunAlgoParams() { return "EMA"; }

    @Override public double getDirectionAdjusted() {
        return calcDirection();
    }

    @Override public Direction getDirection() {
        double dir = calcDirection();
        return (dir == 0) ? null : Direction.get(dir);
    }
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
    public static class AndIndicator extends TresIndicator {
        public AndIndicator(TresAlgo algo) {
            super(".", 0, algo);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Colors.SKY; }
        @Override protected boolean countPeaks() { return false; }
    }


    // ======================================================================================
    private class EmaIndicatorInt extends EmaIndicator {
        double m_prevValue = 0;
        double m_lastValue = 0;

        public EmaIndicatorInt(int emaSize) {
            super("ema", EmaAlgo.this, emaSize);
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
