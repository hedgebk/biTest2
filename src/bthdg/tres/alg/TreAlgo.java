package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CciIndicator;
import bthdg.tres.ind.OscIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;

public class TreAlgo extends TresAlgo {
    final OscIndicator m_oscIndicator;
    final CciIndicator m_cciIndicator;
    final AndIndicator m_andIndicator;
    private Direction m_lastDirection;

    public TreAlgo(TresExchData exchData) {
        super("Tre", exchData);
        m_oscIndicator = new OscIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcAnd();
            }
        };
        m_indicators.add(m_oscIndicator);

        m_cciIndicator = new CciIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcAnd();
            }
        };
        m_indicators.add(m_cciIndicator);

        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);
    }

    private void recalcAnd() {
        ChartPoint osc = m_oscIndicator.getLastPoint();
        ChartPoint cci = m_cciIndicator.getLastPoint();
        if ((osc != null) && (cci != null)) {
            double oscValue = getDirectionAdjustedByPeakWatchers(m_oscIndicator);
            double cciValue = getDirectionAdjustedByPeakWatchers(m_cciIndicator);
            double and = (oscValue + cciValue) / 2;
            long oscMillis = osc.m_millis;
            long cciMillis = cci.m_millis;
            long millis = Math.max(oscMillis, cciMillis);
            ChartPoint chartPoint = new ChartPoint(millis, and);
            m_andIndicator.addBar(chartPoint);

            Direction direction = getDirection();
            if (direction != m_lastDirection) {
                notifyListener();
            }
            m_lastDirection = direction;
        }
    }

    @Override public String getRunAlgoParams() {
        return "TreAlgo";
    }

    @Override public double lastTickPrice() {
        return (m_oscIndicator.lastTickTime() > m_cciIndicator.lastTickTime())
                ? m_oscIndicator.lastTickPrice()
                : m_cciIndicator.lastTickPrice();
    }
    @Override public long lastTickTime() { return Math.max(m_oscIndicator.lastTickTime(), m_cciIndicator.lastTickTime()); }
    @Override public Color getColor() { return Colors.LIGHT_MAGNETA; }
    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        ChartPoint lastPoint = m_andIndicator.getLastPoint();
        if (lastPoint != null) {
            return lastPoint.m_value;
        }
        return 0;
    }

    @Override public Direction getDirection() {
        return Direction.get(getDirectionAdjusted());
    } // UP/DOWN


    // ===========================================================================================
    public static class AndIndicator extends TresIndicator {
        public static double PEAK_TOLERANCE = 0.1;

        public AndIndicator(TresAlgo algo) {
            super("+", PEAK_TOLERANCE, algo);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
//        @Override protected boolean countPeaks() { return false; }
        @Override public Color getColor() { return Colors.LIGHT_MAGNETA; }
        @Override public Color getPeakColor() { return Colors.LIGHT_MAGNETA; }
        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }
    }
}
