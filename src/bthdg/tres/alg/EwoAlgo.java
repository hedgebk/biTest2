package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.EmaIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.Colors;

import java.awt.*;

// based on Elliot Wave Oscillator
public class EwoAlgo extends TresAlgo {
    private static final double FAST_EMA_SIZE = 5;
    private static final double SLOW_EMA_SIZE = 35;

    private final EmaIndicator m_fastEma;
    private final EmaIndicator m_slowEma;
    private final TresIndicator m_ewoIndicator;
    private boolean m_changed;
    private double m_ewo;

    public EwoAlgo(TresExchData tresExchData) {
        this("Ewo", tresExchData);
    }

    public EwoAlgo(String name, TresExchData tresExchData) {
        super(name, tresExchData);
//        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        m_fastEma = new EmaIndicator("f", this, FAST_EMA_SIZE) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_changed = true;
            }
        };
        m_indicators.add(m_fastEma);

        m_slowEma = new EmaIndicator("s", this, SLOW_EMA_SIZE) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_changed = true;
            }
        };
        m_indicators.add(m_slowEma);

        m_ewoIndicator = new TresIndicator( "1", 0, this ) {
            @Override public TresIndicator.TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.SKY; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean useValueAxe() { return true; }
        };
        m_indicators.add(m_ewoIndicator);

    }

    @Override public double lastTickPrice() { return m_fastEma.lastTickPrice(); }
    @Override public long lastTickTime() { return m_fastEma.lastTickTime(); }
    @Override public Color getColor() { return Color.LIGHT_GRAY; }
    @Override public String getRunAlgoParams() { return "EWO"; }

    @Override public double getDirectionAdjusted() {
        return 0;
    }
    @Override public Direction getDirection() {
//        double dir = getDirectionAdjusted();
//        return (dir == 0) ? null : Direction.get(dir);
        return null;
    }

    @Override public void postUpdate(TradeDataLight tdata) {
        if (m_changed) {
            m_changed = false;
            recalc();
        }
    }

    private void recalc() {
        ChartPoint fastPoint = m_fastEma.getLastPoint();
        ChartPoint slowPoint = m_slowEma.getLastPoint();
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
}
