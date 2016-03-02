package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.TresIndicator;
import bthdg.tres.ind.TripleEmaIndicator;
import bthdg.tres.ind.VelocityIndicator;
import bthdg.tres.ind.ZeroLeveler;
import bthdg.util.Colors;

import java.awt.*;

// based on Elliot Wave Oscillator
public class EwoAlgo extends TresAlgo {
    public static double FAST_EMA_SIZE = 5;
    public static double SLOW_EMA_SIZE = 35;
    public static double START_ZERO_LEVEL = 0.000001;
    public static double VELOCITY_SIZE = 1.0;

//    private final EmaIndicator m_fastEma;
//    private final EmaIndicator m_slowEma;
//    private final TresIndicator m_ewoIndicator;
    private final TripleEmaIndicator m_fastTEma;
    private final TripleEmaIndicator m_slowTEma;
    private final TresIndicator m_ewo2Indicator;
    private final VelocityIndicator m_ewo2VelocityIndicator;
    private final ZeroLeveler m_zeroLeveler;
    private final TresIndicator m_resIndicator;
//    private boolean m_changed;
//    private double m_ewo;
    private boolean m_changed2;
    private double m_ewo2;
    private double m_updated;

    public EwoAlgo(TresExchData tresExchData) {
        this("Ewo", tresExchData);
    }

    public EwoAlgo(String name, TresExchData tresExchData) {
        super(name, tresExchData);
        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        m_zeroLeveler = new ZeroLeveler(START_ZERO_LEVEL);

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
            @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
                g.setColor(getColor());
                int y = yAxe.getPointReverse(0);
                g.drawLine(xTimeAxe.getPoint(xTimeAxe.m_min), y, xTimeAxe.getPoint(xTimeAxe.m_max), y);
            }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_ewo2VelocityIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_ewo2Indicator);

        long veloсitySize = (long) (barSizeMillis * VELOCITY_SIZE);
        m_ewo2VelocityIndicator = new VelocityIndicator(this, "vel", veloсitySize, 0) {
            @Override protected boolean countPeaks() { return false; }
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
        m_indicators.add(m_ewo2VelocityIndicator);

        m_resIndicator = new TresIndicator( "res", 0, this ) {
            @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.LIGHT_MAGNETA; }
            @Override protected boolean countPeaks() { return false; }
//            @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
//                g.setColor(getColor());
//                int y = yAxe.getPointReverse(0);
//                g.drawLine(xTimeAxe.getPoint(xTimeAxe.m_min), y, xTimeAxe.getPoint(xTimeAxe.m_max), y);
//            }
        };
        m_indicators.add(m_resIndicator);
    }

    @Override public double lastTickPrice() { return m_fastTEma.lastTickPrice(); }
    @Override public long lastTickTime() { return m_fastTEma.lastTickTime(); }
    @Override public Color getColor() { return Color.LIGHT_GRAY; }
    @Override public String getRunAlgoParams() { return "EWO"; }

    @Override public double getDirectionAdjusted() {
        return m_updated;
//        return m_ewo2;
    }
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
}
