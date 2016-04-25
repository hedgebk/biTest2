package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CmfIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.tres.ind.ZeroLeveler;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;

public abstract class CmfAlgo extends TresAlgo {
    private static final String CMF_AXE_NAME = "cmf-axe";

    private final CmfIndicatorInt m_cmfIndicator;
    private final CmfIndicatorInt m_cmfIndicator2;
    private final ValueIndicator m_cmfAvgIndicator;
    private double m_cmfAvg;

    public CmfAlgo(TresExchData tresExchData) {
        super("CMF", tresExchData);
        m_cmfIndicator = new CmfIndicatorInt(CmfIndicator.LENGTH);
        m_indicators.add(m_cmfIndicator);

        m_cmfIndicator2 = new CmfIndicatorInt(CmfIndicator.LENGTH2);
        m_indicators.add(m_cmfIndicator2);

        m_cmfAvgIndicator = new ValueIndicator(this, "a", Color.blue) {
            @Override protected String getYAxeName() { return CMF_AXE_NAME; }
            @Override protected boolean useValueAxe() { return false; }
            @Override protected boolean drawZeroLine() { return true; }
        };
        m_indicators.add(m_cmfAvgIndicator);

    }

    private void recalc(long millis) {
        ChartPoint lastPoint = m_cmfIndicator.getLastPoint();
        if (lastPoint != null) {
            ChartPoint lastPoint2 = m_cmfIndicator2.getLastPoint();
            if (lastPoint2 != null) {
                double cmf = lastPoint.m_value;
                double cmf2 = lastPoint2.m_value;
                double cmfAvg = (cmf + cmf2) / 2;
                if (m_cmfAvg != cmfAvg) {
                    m_cmfAvg = cmfAvg;
                    m_cmfAvgIndicator.addBar(new ChartPoint(millis, cmfAvg));

                    onCmfAvg(cmfAvg, millis);
                }
            }
        }
    }

    protected abstract void onCmfAvg(double cmfAvg, long millis);


    @Override public double lastTickPrice() { return m_cmfIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_cmfIndicator.lastTickTime(); }
    @Override public Color getColor() { return Colors.BEGIE; }

    @Override public Direction getDirection() { return Direction.get(getDirectionAdjusted()); } // UP/DOWN


    // ===============================================================================================================
    private static class ValueIndicator extends TresIndicator {
        private final Color m_color;

        ValueIndicator(TresAlgo algo, String name, Color color) {
            this(algo, name, 0, color);
        }
        ValueIndicator(TresAlgo algo, String name, double peakTolerance, Color color) {
            super(name, peakTolerance, algo);
            m_color = color;
        }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return m_color; }
        @Override protected boolean useValueAxe() { return true; }
        @Override protected boolean drawZeroLine() { return true; }
    }

    // ===============================================================================================================
    private class CmfIndicatorInt extends CmfIndicator {
        @Override protected String getYAxeName() { return CMF_AXE_NAME; }
        @Override protected boolean drawZeroLine() { return true; }

        private CmfIndicatorInt(double length) {
            super(length, 0, CmfAlgo.this);
        }

        @Override public void addBar(ChartPoint chartPoint) {
            super.addBar(chartPoint);
            ChartPoint lastPoint = getLastPoint();
            if (lastPoint != null) {
                recalc(lastPoint.m_millis);
            }
        }
    }


    //======================================================================
    public static class Old extends CmfAlgo {
        private final ValueIndicator m_directionIndicator;

        public Old(TresExchData tresExchData) {
            super(tresExchData);
            m_directionIndicator = new ValueIndicator(this, "+", Color.red);
            m_indicators.add(m_directionIndicator);
        }

        @Override protected void onCmfAvg(double cmfAvg, long millis) {
            double directionAdjusted = (cmfAvg >= CmfIndicator.LEVEL) ? 1 : (cmfAvg <= -CmfIndicator.LEVEL) ? -1 : cmfAvg / CmfIndicator.LEVEL;
            m_directionIndicator.addBar(new ChartPoint(millis, directionAdjusted));
            notifyListener();
        }

        @Override public double getDirectionAdjusted() { // [-1 ... 1]
            ChartPoint lastPoint = m_directionIndicator.getLastPoint();
            return (lastPoint == null) ? 0 : lastPoint.m_value;
        }

        @Override public String getRunAlgoParams() { return "CMF"; }
    }

    //======================================================================
    public static class New extends CmfAlgo {
        private final ZeroLeveler m_zeroLeveler;
        private final ValueIndicator m_directionIndicator;
        private double m_updated;

        public New(TresExchData tresExchData) {
            super(tresExchData);
            m_zeroLeveler = new ZeroLeveler(CmfIndicator.LEVEL);
            m_directionIndicator = new ValueIndicator(this, "+", Color.red);
            m_indicators.add(m_directionIndicator);
        }

        @Override protected void onCmfAvg(double cmfAvg, long millis) {
            double updated = m_zeroLeveler.update(cmfAvg);
            if (m_updated != updated) {
                m_updated = updated;
                ChartPoint point = new ChartPoint(millis, updated);
                m_directionIndicator.addBar(point);
                notifyListener();
            }
        }

        @Override public double getDirectionAdjusted() { // [-1 ... 1]
            ChartPoint lastPoint = m_directionIndicator.getLastPoint();
            return (lastPoint == null) ? 0 : lastPoint.m_value;
        }

        @Override public String getRunAlgoParams() { return "CMFn: len=" + Utils.format5(CmfIndicator.LENGTH) + "; len2=" + Utils.format5(CmfIndicator.LENGTH2); }
    }
}
