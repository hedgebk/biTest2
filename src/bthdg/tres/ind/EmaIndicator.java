package bthdg.tres.ind;

import bthdg.calc.EmaCalculator;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class EmaIndicator extends TresIndicator {

    public EmaIndicator(String name, TresAlgo algo) {
        super(name, 0, algo);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedEmaIndicator(this, exchData, phaseIndex);
    }

    @Override protected boolean countPeaks() { return false; }
    @Override public Color getColor() { return Colors.LIGHT_RED; }

//    @Override public void addBar(ChartPoint chartPoint) {
//        if (chartPoint != null) {
//            double value = chartPoint.m_value;
//            m_smoocher.justAdd(value);
//            double value2 = m_smoocher.get();
//            ChartPoint smooched = new ChartPoint(chartPoint.m_millis, value2);
//            super.addBar(smooched);
//        }
//    }


    // ======================================================================================
    public static class PhasedEmaIndicator extends TresPhasedIndicator {
        public static final int _05 = 5;

        private final EmaCalculator m_calculator;

        @Override public Color getColor() { return Color.CYAN; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }

        public PhasedEmaIndicator(EmaIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, null);
            long barSize = exchData.m_tres.m_barSizeMillis;
            long barOffset = exchData.m_tres.getBarOffset(phaseIndex);
            m_calculator = new EmaCalculator(_05, barSize, barOffset) {
                @Override protected void finishCurrentBar(long time, double price) {
                    super.finishCurrentBar(time, price);
                    if (m_pervValue != null) {
                        ChartPoint tick = new ChartPoint(m_currentBarEnd, m_pervValue);
                        if (m_exchData.m_tres.m_collectPoints) {
                            m_points.add(tick); // add to the end
                        }
                        onBar(tick);
                    }
                }
                //                @Override protected void bar(long barEnd, double value) {
//                    ChartPoint tick = new ChartPoint(barEnd, value);
//                    if (m_exchData.m_tres.m_collectPoints) {
//                        m_points.add(tick); // add to the end
//                    }
//                    m_peakCalculator.update(tick);
//                    onBar(tick);
//                }
            };
        }

        @Override public boolean update(long timestamp, double price) {
            return m_calculator.update(timestamp, price);
        }
    }
}
