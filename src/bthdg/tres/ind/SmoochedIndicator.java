package bthdg.tres.ind;

import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Utils;

import java.awt.*;

public class SmoochedIndicator extends TresIndicator {
    private final Utils.FadingAverageCounter m_avgCounter;

    public SmoochedIndicator(TresAlgo algo, String name, long frameSizeMillis) {
        this(algo, name, frameSizeMillis, 0);
    }

    public SmoochedIndicator(TresAlgo algo, String name, long frameSizeMillis, double peakTolerance) {
        super(name, peakTolerance, algo);
        m_avgCounter = new Utils.FadingAverageCounter(frameSizeMillis);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
    @Override public Color getColor() { return Color.green; }
    @Override public Color getPeakColor() { return Color.green; }

    @Override public void addBar(ChartPoint chartPoint) {
        if (chartPoint != null) {
            long millis = chartPoint.m_millis;
            double avg = m_avgCounter.add(millis, chartPoint.m_value);
            if(Double.isInfinite(avg)){
                avg = m_avgCounter.add(millis, chartPoint.m_value);
                System.out.println("ERROR: avg isInfinite: " + avg);
            }
            ChartPoint smoochPoint = new ChartPoint(millis, avg);
            super.addBar(smoochPoint);
        }
    }
}
