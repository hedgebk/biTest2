package bthdg.tres.ind;

import bthdg.calc.CoppockCalculator;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;

public class CoppockIndicator extends TresIndicator {
    @Override public TresPhasedIndicator createPhased(TresExchData exchData, int phaseIndex) {
        return new PhasedCoppockIndicator(this, exchData, phaseIndex);
    }

    public static class PhasedCoppockIndicator extends TresPhasedIndicator {
        private static final double PEAK_TOLERANCE = 0.0075;
        public static final int WMA_LENGTH = 10;
        public static final int LONG_ROC_LENGTH = 14;
        public static final int SHORT_ROС_LENGTH = 11;

        private final CoppockCalculator m_calculator;
        protected ChartPoint m_lastTick;

        public PhasedCoppockIndicator(CoppockIndicator indicatorAvg, TresExchData exchData, int phaseIndex) {
            super(indicatorAvg, exchData, phaseIndex, PEAK_TOLERANCE);
            m_calculator = new CoppockCalculator(WMA_LENGTH, LONG_ROC_LENGTH, SHORT_ROС_LENGTH,
                                                 exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex)) {
                @Override protected void bar(long barEnd, double value) {
                    ChartPoint tick = new ChartPoint(barEnd, value);
                    m_points.add(tick); // add to the end
                    m_peakCalculator.update(tick);
                    m_lastTick = tick;
                }
            };
        }

        @Override public boolean update(long timestamp, double price) {
            return m_calculator.update(timestamp, price);
        }
    }
}
