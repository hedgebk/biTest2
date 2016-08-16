package bthdg.tres.alg;

import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.*;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;

public class EwoCmfAlgo extends TresAlgo {
    private static final String CMF_AXE_NAME = "cmf-axe";

    public static double FAST_EMA_SIZE = 5;  // "ewo_cmf.fast_ema"
    public static double SLOW_EMA_SIZE = 35; // "ewo_cmf.slow_ema"
    public static double EWO_VELOCITY_SIZE = 1.0; // "ewo_cmf.ewo_velocity"

    public static double LENGTH = 20;             // "ewo_cmf.len"
    public static double LENGTH2 = 25;            // "ewo_cmf.len2"
    public static double CMF_VELOCITY_SIZE = 1.0; // "ewo_cmf.cmf_velocity"

    public static double CMF_CORRECT_RATE = 3.0;  // "ewo_cmf.cmf_correct"
    public static double START_ZERO_LEVEL = 0.000001; // "ewo_cmf.zero"

    private final TripleEmaIndicator m_fastTEma;
    private final TripleEmaIndicator m_slowTEma;
    private boolean m_temaChanged;
    private double m_ewo;
    private final TresIndicator m_ewoIndicator;
    private final VelocityIndicator m_ewoVelocityIndicator;

    private final CmfIndicatorInt m_cmfIndicator;
    private final CmfIndicatorInt m_cmfIndicator2;
    private boolean m_cmfChanged;
    private double m_cmfAvg;
    private final ValueIndicator m_cmfAvgIndicator;
    private final VelocityIndicator m_cmfVelocityIndicator;

    private boolean m_velocityChanged;
    private final ValueIndicator m_velocitiesIndicator;
    private final ZeroLeveler m_zeroLeveler;
    private double m_updated;
    private final TresIndicator m_resIndicator;

    EwoCmfAlgo(TresExchData tresExchData) {
        super("ewo_cmf", tresExchData);

        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        m_fastTEma = new TripleEmaIndicator("tf", this, FAST_EMA_SIZE, Color.gray) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_temaChanged = true;
            }
        };
        m_indicators.add(m_fastTEma);

        m_slowTEma = new TripleEmaIndicator("ts", this, SLOW_EMA_SIZE, Color.orange) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_temaChanged = true;
            }
        };
        m_indicators.add(m_slowTEma);

        m_ewoIndicator = new TresIndicator( "e2", 0, this ) {
            @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.SKY; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean drawZeroLine() { return true; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_ewoVelocityIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_ewoIndicator);

        long ewoVelocitySize = (long) (barSizeMillis * EWO_VELOCITY_SIZE);
        m_ewoVelocityIndicator = new VelocityIndicator(this, "vel", ewoVelocitySize, 0) {
            @Override protected boolean countPeaks() { return false; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_velocityChanged = true;
            }
        };
        m_indicators.add(m_ewoVelocityIndicator);


        // ------------------------------------------------
        m_cmfIndicator = new CmfIndicatorInt(LENGTH);
        m_indicators.add(m_cmfIndicator);

        m_cmfIndicator2 = new CmfIndicatorInt(LENGTH2);
        m_indicators.add(m_cmfIndicator2);

        m_cmfAvgIndicator = new ValueIndicator(this, "a", Color.blue) {
            @Override protected String getYAxeName() { return CMF_AXE_NAME; }
            @Override protected boolean useValueAxe() { return false; }
            @Override protected boolean drawZeroLine() { return true; }
        };
        m_indicators.add(m_cmfAvgIndicator);

        long cmfVelocitySize = (long) (barSizeMillis * CMF_VELOCITY_SIZE);
        m_cmfVelocityIndicator = new VelocityIndicator(this, "cmf_vel", cmfVelocitySize, 0) {
            @Override protected boolean countPeaks() { return false; }
            @Override public Color getColor() { return Color.magenta; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_velocityChanged = true;
            }
        };
        m_indicators.add(m_cmfVelocityIndicator);

        m_zeroLeveler = new ZeroLeveler(START_ZERO_LEVEL);

        // ------------------------------------------------
        m_velocitiesIndicator = new ValueIndicator(this, "++", Color.blue) {
            @Override protected String getYAxeName() { return "VELOCITIES_AXE_NAME"; }
            @Override protected boolean useValueAxe() { return false; }
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
        m_indicators.add(m_velocitiesIndicator);

        m_resIndicator = new TresIndicator( "res", 0, this ) {
            @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.LIGHT_MAGNETA; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean drawZeroLine() { return true; }
        };
        m_indicators.add(m_resIndicator);
    }

    @Override public double lastTickPrice() { return m_fastTEma.lastTickPrice(); }
    @Override public long lastTickTime() { return m_fastTEma.lastTickTime(); }
    @Override public Color getColor() { return Color.LIGHT_GRAY; }

    @Override public double getDirectionAdjusted() { return m_updated; }

    @Override public String getRunAlgoParams() {
        return "EWO_CMF: fst=" + Utils.format5(FAST_EMA_SIZE) +
                "; slw=" + Utils.format5(SLOW_EMA_SIZE) +
                "; evel=" + Utils.format5(EWO_VELOCITY_SIZE) +
                "; len=" + Utils.format5(LENGTH) +
                "; len2=" + Utils.format5(LENGTH2) +
                "; cvel=" + Utils.format5(CMF_VELOCITY_SIZE) +
                "; corr=" + Utils.format5(CMF_CORRECT_RATE) +
                "; zer=" + Utils.format8(START_ZERO_LEVEL);
    }


//    @Override public Direction getDirection() {
//        double dir = getDirectionAdjusted();
//        return (dir == 0) ? null : Direction.get(dir);
//    }

    @Override public void postUpdate(TradeDataLight tdata) {
        if (m_temaChanged) {
            m_temaChanged = false;
            recalcEwo();
        }
        if (m_cmfChanged) {
            m_cmfChanged = false;
            recalcCmf();
        }
        if (m_velocityChanged) {
            m_velocityChanged = false;
            ChartPoint ewoVelocityPoint = m_ewoVelocityIndicator.getLastPoint();
            ChartPoint cmfVelocityPoint = m_cmfVelocityIndicator.getLastPoint();
            if ((ewoVelocityPoint != null) && (cmfVelocityPoint != null)) {
                double ewoVelocity = ewoVelocityPoint.m_value;
                double cmfVelocity = cmfVelocityPoint.m_value;
                long millis = cmfVelocityPoint.m_millis;
                double velocityAdjusted = ewoVelocity + CMF_CORRECT_RATE * cmfVelocity;
                m_velocitiesIndicator.addBar(new ChartPoint(millis, velocityAdjusted));

                notifyListener();
            }
        }
    }

    private void recalcEwo() {
        ChartPoint fastPoint = m_fastTEma.getLastPoint();
        ChartPoint slowPoint = m_slowTEma.getLastPoint();
        if ((fastPoint != null) && (slowPoint != null)) {
            double fast = fastPoint.m_value;
            double slow = slowPoint.m_value;
            double ewo = fast - slow;
            if (m_ewo != ewo) {
                m_ewo = ewo;
                ChartPoint point = new ChartPoint(fastPoint.m_millis, ewo);
                m_ewoIndicator.addBar(point);
            }
        }
    }

    private void recalcCmf() {
        ChartPoint lastPoint = m_cmfIndicator.getLastPoint();
        if (lastPoint != null) {
            ChartPoint lastPoint2 = m_cmfIndicator2.getLastPoint();
            if (lastPoint2 != null) {
                double cmf = lastPoint.m_value;
                double cmf2 = lastPoint2.m_value;
                double cmfAvg = (cmf + cmf2) / 2;
                if (m_cmfAvg != cmfAvg) {
                    m_cmfAvg = cmfAvg;
                    long millis = lastPoint.m_millis;
                    m_cmfAvgIndicator.addBar(new ChartPoint(millis, cmfAvg)); // just to make value visible - not used in calculations
                    m_cmfVelocityIndicator.addBar(new ChartPoint(millis, cmfAvg));
                }
            }
        }
    }


    // ===============================================================================================================
    private class CmfIndicatorInt extends CmfIndicator {
        @Override protected String getYAxeName() { return CMF_AXE_NAME; }
        @Override protected boolean drawZeroLine() { return true; }

        private CmfIndicatorInt(double length) {
            super(length, 0, EwoCmfAlgo.this);
        }

        @Override public void addBar(ChartPoint chartPoint) {
            super.addBar(chartPoint);
            ChartPoint lastPoint = getLastPoint();
            if (lastPoint != null) {
                m_cmfChanged = true;
            }
        }
    }

}
