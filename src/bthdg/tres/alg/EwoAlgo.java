package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.*;
import bthdg.util.Colors;

import java.awt.*;

// based on Elliot Wave Oscillator
public abstract class EwoAlgo extends TresAlgo {
    public static double FAST_EMA_SIZE = 5;
    public static double SLOW_EMA_SIZE = 35;

    //    private final EmaIndicator m_fastEma;
//    private final EmaIndicator m_slowEma;
//    private final TresIndicator m_ewoIndicator;
    private final TripleEmaIndicator m_fastTEma;
    private final TripleEmaIndicator m_slowTEma;
    private final TresIndicator m_ewo2Indicator;
    //    private boolean m_changed;
//    private double m_ewo;
    private boolean m_changed2;
    private double m_ewo2;

    public EwoAlgo(TresExchData tresExchData) {
        this("Ewo", tresExchData);
    }

    public EwoAlgo(String name, TresExchData tresExchData) {
        super(name, tresExchData);
//        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

//        m_fastEma = new EmaIndicator("ef", this, FAST_EMA_SIZE) {
//            @Override public void addBar(ChartPoint chartPoint) {
//                super.addBar(chartPoint);
//                m_changed = true;
//            }
//        };
//        m_indicators.add(m_fastEma);
//
//        m_slowEma = new EmaIndicator("es", this, SLOW_EMA_SIZE) {
//            @Override public void addBar(ChartPoint chartPoint) {
//                super.addBar(chartPoint);
//                m_changed = true;
//            }
//        };
//        m_indicators.add(m_slowEma);

        m_fastTEma = new TripleEmaIndicator("tf", this, FAST_EMA_SIZE, Color.gray) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_changed2 = true;
            }
        };
        m_indicators.add(m_fastTEma);

        m_slowTEma = new TripleEmaIndicator("ts", this, SLOW_EMA_SIZE, Color.orange) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_changed2 = true;
            }
        };
        m_indicators.add(m_slowTEma);


//        m_ewoIndicator = new TresIndicator( "e1", 0, this ) {
//            @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
//            @Override public Color getColor() { return Colors.LIGHT_RED; }
//            @Override protected boolean countPeaks() { return false; }
//            @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
//                g.setColor(getColor());
//                int y = yAxe.getPointReverse(0);
//                g.drawLine(xTimeAxe.getPoint(xTimeAxe.m_min), y, xTimeAxe.getPoint(xTimeAxe.m_max), y);
//            }
//        };
//        m_indicators.add(m_ewoIndicator);

        m_ewo2Indicator = new TresIndicator( "e2", 0, this ) {
            @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.SKY; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean drawZeroLine() { return true; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                onEwo2(lastPoint);
            }
        };
        m_indicators.add(m_ewo2Indicator);
    }

    protected void onEwo2(ChartPoint lastPoint) {}

    @Override public double lastTickPrice() { return m_fastTEma.lastTickPrice(); }
    @Override public long lastTickTime() { return m_fastTEma.lastTickTime(); }
    @Override public Color getColor() { return Color.LIGHT_GRAY; }

    @Override public Direction getDirection() {
        double dir = getDirectionAdjusted();
        return (dir == 0) ? null : Direction.get(dir);
//        return null;
    }

    @Override public void postUpdate(TradeDataLight tdata) {
//        if (m_changed) {
//            m_changed = false;
//            recalc();
//        }
        if (m_changed2) {
            m_changed2 = false;
            recalc2();
        }
    }

//    private void recalc() {
//        ChartPoint fastPoint = m_fastEma.getLastPoint();
//        ChartPoint slowPoint = m_slowEma.getLastPoint();
//        if ((fastPoint != null) && (slowPoint != null)) {
//            double fast = fastPoint.m_value;
//            double slow = slowPoint.m_value;
//            double ewo = fast - slow;
//            if (m_ewo != ewo) {
//                m_ewo = ewo;
//                ChartPoint point = new ChartPoint(fastPoint.m_millis, ewo);
//                m_ewoIndicator.addBar(point);
//            }
//        }
//    }

    private void recalc2() {
        ChartPoint fastPoint = m_fastTEma.getLastPoint();
        ChartPoint slowPoint = m_slowTEma.getLastPoint();
        if ((fastPoint != null) && (slowPoint != null)) {
            double fast = fastPoint.m_value;
            double slow = slowPoint.m_value;
            double ewo = fast - slow;
            if (m_ewo2 != ewo) {
                m_ewo2 = ewo;
                ChartPoint point = new ChartPoint(fastPoint.m_millis, ewo);
                m_ewo2Indicator.addBar(point);

                notifyListener();
            }
        }
    }


    //======================================================================
    public static class Old extends EwoAlgo {
        public static double START_ZERO_LEVEL = 0.000001;
        public static double VELOCITY_SIZE = 1.0;
        public static double SMOOTH_RATE = 0.1;

        //    private final SmoochedIndicator m_smoochedEwo2Indicator;
        private final VelocityIndicator m_ewo2VelocityIndicator;
        private final SmoochedIndicator m_smoochedEwo2VelocityIndicator;
        private final ZeroLeveler m_zeroLeveler;
        private final TresIndicator m_resIndicator;
        private double m_updated;

        @Override public double getDirectionAdjusted() { return m_updated; }
        @Override public String getRunAlgoParams() { return "EWO"; }

        public Old(TresExchData tresExchData) {
            super("Ewo", tresExchData);
            final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

            m_zeroLeveler = new ZeroLeveler(START_ZERO_LEVEL);

//        long frameSizeMillis = (long) (0.1 * barSizeMillis);
//        m_smoochedEwo2Indicator = new SmoochedIndicator(this, "sm", frameSizeMillis, 0) {
//            @Override public Color getColor() { return Color.lightGray; }
//            @Override protected boolean countPeaks() { return false; }
//            @Override public void addBar(ChartPoint chartPoint) {
//                super.addBar(chartPoint);
//                m_ewo2VelocityIndicator.addBar(getLastPoint());
//            }
//        };
//        m_indicators.add(m_smoochedEwo2Indicator);

            long velocitySize = (long) (barSizeMillis * VELOCITY_SIZE);
            m_ewo2VelocityIndicator = new VelocityIndicator(this, "vel", velocitySize, 0) {
                @Override protected boolean countPeaks() { return false; }
                @Override public void addBar(ChartPoint chartPoint) {
                    super.addBar(chartPoint);
                    m_smoochedEwo2VelocityIndicator.addBar(getLastPoint());
                }
            };
            m_indicators.add(m_ewo2VelocityIndicator);

            long frameSizeMillis2 = (long) (SMOOTH_RATE * barSizeMillis);
            m_smoochedEwo2VelocityIndicator = new SmoochedIndicator(this, "velsm", frameSizeMillis2, 0) {
                @Override public Color getColor() { return Color.blue; }
                @Override protected boolean countPeaks() { return false; }
                @Override protected boolean drawZeroLine() { return true; }
                @Override public void addBar(ChartPoint chartPoint) {
                    super.addBar(chartPoint);
                    ChartPoint lastPoint = getLastPoint();
                    if (lastPoint != null) {
                        double value = lastPoint.m_value;
                        double updated = m_zeroLeveler.update(value);
                        if (m_updated != updated) {
                            m_updated = updated;
                            ChartPoint point = new ChartPoint(lastPoint.m_millis, updated);
                            m_resIndicator.addBar(point);
                        }
                    }
                }
            };
            m_indicators.add(m_smoochedEwo2VelocityIndicator);

            m_resIndicator = new TresIndicator( "res", 0, this ) {
                @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
                @Override public Color getColor() { return Colors.LIGHT_MAGNETA; }
                @Override protected boolean countPeaks() { return false; }
                @Override protected boolean drawZeroLine() { return true; }
            };
            m_indicators.add(m_resIndicator);
        }

        @Override protected void onEwo2(ChartPoint lastPoint) {
//                m_smoochedEwo2Indicator.addBar(lastPoint);
            m_ewo2VelocityIndicator.addBar(lastPoint);
        }
    }


    //======================================================================
    public static class New extends EwoAlgo {
        public static double START_ZERO_LEVEL = 0.1;

        private final ZeroLeveler m_zeroLeveler;
        private final TresIndicator m_resIndicator;
        private double m_updated;

        @Override public double getDirectionAdjusted() { return m_updated; }
        @Override public String getRunAlgoParams() { return "EWOn"; }

        public New(TresExchData tresExchData) {
            super("EwoN", tresExchData);
            m_zeroLeveler = new ZeroLeveler(START_ZERO_LEVEL);

            m_resIndicator = new TresIndicator( "res", 0, this ) {
                @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
                @Override public Color getColor() { return Colors.LIGHT_MAGNETA; }
                @Override protected boolean countPeaks() { return false; }
                @Override protected boolean drawZeroLine() { return true; }
            };
            m_indicators.add(m_resIndicator);
        }

        @Override protected void onEwo2(ChartPoint lastPoint) {
//            m_ewo2VelocityIndicator.addBar(lastPoint);
            if (lastPoint != null) {
                double value = lastPoint.m_value;
                double updated = m_zeroLeveler.update(value);
                if (m_updated != updated) {
                    m_updated = updated;
                    ChartPoint point = new ChartPoint(lastPoint.m_millis, updated);
                    m_resIndicator.addBar(point);
                }
            }
        }
    }
}
