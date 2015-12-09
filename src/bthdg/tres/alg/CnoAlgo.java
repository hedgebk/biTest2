package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CciIndicator;
import bthdg.tres.ind.OscIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.Utils;

import java.awt.*;

public class CnoAlgo extends TresAlgo {
    private final OscIndicator m_oscIndicator;
    private final CciIndicator m_cciIndicator;
    private final AndIndicator m_andIndicator;

    public CnoAlgo(TresExchData tresExchData) {
        super("C+O", tresExchData);
        m_cciIndicator = new CciIndicator(this) {
            @Override protected void onBar() {
                super.onBar();
                recalcAnd();
            }
        };
        m_indicators.add(m_cciIndicator);

        m_oscIndicator = new OscIndicator(this/*, OSC_TOLERANCE*/) {
            @Override protected void onBar() {
                super.onBar();
                recalcAnd();
            }
        };
        m_indicators.add(m_oscIndicator);

        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);
    }

    private void recalcAnd() {
        ChartPoint osc = m_oscIndicator.getLastPoint();
        ChartPoint cci = m_cciIndicator.getLastPoint();
        if ((osc != null) && (cci != null)) {
            double directionAdjusted = getDirectionAdjusted();
            long millis = Math.max(osc.m_millis, cci.m_millis);
            ChartPoint chartPoint = new ChartPoint(millis, directionAdjusted);
            m_andIndicator.addBar(chartPoint);
        }
    }

    @Override public double lastTickPrice() {
        return (m_oscIndicator.lastTickTime() > m_cciIndicator.lastTickTime())
                ? m_oscIndicator.lastTickPrice()
                : m_cciIndicator.lastTickPrice();
    }

    @Override public long lastTickTime() {
        return Math.max(m_oscIndicator.lastTickTime(), m_cciIndicator.lastTickTime());
    }

    @Override public Color getColor() { return Color.red; }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        ChartPoint osc = m_oscIndicator.getLastPoint();
        ChartPoint cci = m_cciIndicator.getLastPoint();
        if ((osc != null) && (cci != null)) {
//            double oscDirectionAdjusted = m_oscIndicator.getDirectionAdjusted();
//            double cciDirectionAdjusted = m_cciIndicator.getDirectionAdjusted();
//            double mid = (oscDirectionAdjusted + cciDirectionAdjusted) / 2;
//            return Math.signum(mid) * Math.sqrt(Math.abs(mid));

            double oscDirectionAdjusted = getDirectionAdjustedByPeakWatchers(m_oscIndicator);
            double cciDirectionAdjusted = getDirectionAdjustedByPeakWatchers(m_cciIndicator);
            return (oscDirectionAdjusted + cciDirectionAdjusted) / 2;
        }
        return 0;
    }

    @Override public Direction getDirection() {
        Direction oscDirection = m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
        Direction cciDirection = m_cciIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
        return Direction.isForward(oscDirection) && Direction.isForward(cciDirection)
                 ? Direction.FORWARD
                 : Direction.isBackward(oscDirection) && Direction.isBackward(cciDirection)
                   ? Direction.BACKWARD
                   : null;
    } // UP/DOWN

    @Override public String getRunAlgoParams() {
        return "Cno "
                + "osc.tlrnc=" + m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
                + "cci.tlrnc=" + m_cciIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance;
    }


    // ======================================================================================
    public static class AndIndicator extends TresIndicator {
        public AndIndicator(TresAlgo algo) {
            super("+", 0, algo);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.red; }
        @Override protected boolean countPeaks() { return false; }
        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }
    }
}
