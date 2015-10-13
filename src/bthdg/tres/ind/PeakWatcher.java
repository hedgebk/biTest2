package bthdg.tres.ind;

import bthdg.ChartAxe;
import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;
import bthdg.util.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class PeakWatcher {
    final LinkedList<ChartPoint> m_avgPeaks = new LinkedList<ChartPoint>();
    final LinkedList<ChartPoint> m_avgPeakTime = new LinkedList<ChartPoint>();
    public final TrendWatcher<ChartPoint> m_avgPeakCalculator;
    private final boolean m_collectPoints;
    final List<ChartPoint> m_avgPaintPeaks = new ArrayList<ChartPoint>();
    final List<ChartPoint> m_avgPaintPeakTime = new ArrayList<ChartPoint>();

    public PeakWatcher(final TresIndicator indicator, double peakTolerance) {
        m_collectPoints = indicator.m_algo.m_tresExchData.m_tres.m_collectPoints;
        m_avgPeakCalculator = new TrendWatcher<ChartPoint>(peakTolerance) {
            @Override protected double toDouble(ChartPoint tick) { return tick.m_value; }
            @Override protected void onNewPeak(ChartPoint peak, ChartPoint last) {
                if (m_collectPoints) {
                    synchronized (m_avgPeaks) {
                        m_avgPeaks.add(peak);
                    }
                    synchronized (m_avgPeakTime) {
                        m_avgPeakTime.add(last);
                    }
                }
                indicator.onAvgPeak();
            }
        };
    }

    public void cloneChartPoints(ChartAxe xTimeAxe, Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
        TresIndicator.cloneChartPoints(m_avgPeaks, m_avgPaintPeaks, xTimeAxe, minMaxCalculator);
        TresIndicator.cloneChartPoints(m_avgPeakTime, m_avgPaintPeakTime, xTimeAxe, minMaxCalculator);
    }

    public void paintPeaks(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe, Color peakColor) {
        TresIndicator.paintPeaks(g, xTimeAxe, yAxe, peakColor, m_avgPaintPeaks, true, true);
        TresIndicator.paintPeaks(g, xTimeAxe, yAxe, peakColor, m_avgPaintPeakTime, false, false);
    }
}
