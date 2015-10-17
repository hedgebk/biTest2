package bthdg.tres.ind;

import bthdg.calc.CciCalculator;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

// http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:commodity_channel_index_cci
public class CciIndicator extends TresIndicator {
    public static double PEAK_TOLERANCE = 20;
    public static final Color CCI_COLOR = Colors.setAlpha(Colors.LIGHT_ORANGE, 25);
    public static final Color CCI_AVG_COLOR = new Color(230, 100, 43);

    @Override public Color getColor() { return CCI_AVG_COLOR; }
    @Override public Color getPeakColor() { return CCI_AVG_COLOR; }

    public CciIndicator(TresAlgo algo) {
        super( "Cci", PEAK_TOLERANCE, algo);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedCciIndicator(this, exchData, phaseIndex);
    }

    public static class PhasedCciIndicator extends TresPhasedIndicator {
        public static int SMA_LENGTH = 20;

        private final CciCalculator m_calculator;

        @Override public Color getColor() { return CCI_COLOR; }
        @Override public Color getPeakColor() { return CCI_COLOR; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }

        public PhasedCciIndicator(CciIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, PEAK_TOLERANCE);
            m_calculator = new CciCalculator(SMA_LENGTH, exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex)) {
                @Override protected void bar(long barEnd, double value) {
                    ChartPoint tick = new ChartPoint(barEnd, value);
                    if (m_exchData.m_tres.m_collectPoints) {
                        m_points.add(tick); // add to the end
                    }
                    m_peakCalculator.update(tick);
                    onBar(tick);
                }
            };
        }

        @Override public boolean update(long timestamp, double price) {
            return m_calculator.update(timestamp, price);
        }
    }
}
