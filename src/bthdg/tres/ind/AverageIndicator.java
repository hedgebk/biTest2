package bthdg.tres.ind;

import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;

public class AverageIndicator extends TresIndicator {
    public static double PEAK_TOLERANCE = 0.1;

    private final Utils.ArrayAverageCounter m_smoocher;

    public AverageIndicator(String name, TresAlgo algo, int size) {
        super(name, PEAK_TOLERANCE, algo);
        m_smoocher = new Utils.ArrayAverageCounter(size);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
    @Override protected boolean countPeaks() { return false; }
    @Override public Color getColor() { return Colors.LIGHT_RED; }

    @Override public void addBar(ChartPoint chartPoint) {
        if (chartPoint != null) {
            double value = chartPoint.m_value;
            m_smoocher.justAdd(value);
            double value2 = m_smoocher.get();
            ChartPoint smooched = new ChartPoint(chartPoint.m_millis, value2);
            super.addBar(smooched);
        }
    }
}
