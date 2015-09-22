package bthdg.tres.ind;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.util.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class TresIndicator {
    private final List<TresPhasedIndicator> m_phasedIndicators = new ArrayList<TresPhasedIndicator>();
    final LinkedList<ChartPoint> m_avgPoints = new LinkedList<ChartPoint>();
    final LinkedList<ChartPoint> m_avgPeaks = new LinkedList<ChartPoint>();
    final List<ChartPoint> m_avgPaintPoints = new ArrayList<ChartPoint>();
    final List<ChartPoint> m_avgPaintPeaks = new ArrayList<ChartPoint>();
    private final TrendWatcher<ChartPoint> m_avgPeakCalculator;

    public TresIndicator(double peakTolerance) {
        m_avgPeakCalculator = new TrendWatcher<ChartPoint>(peakTolerance) {
            @Override protected double toDouble(ChartPoint tick) { return tick.m_value; }
            @Override protected void onNewPeak(ChartPoint peak, ChartPoint last) {
                synchronized (m_avgPeaks) {
                    m_avgPeaks.add(peak);
                }
                onPeak(m_direction);
            }
        };
    }

    public static TresIndicator get(String indicatorName) {
        if (indicatorName.equals("coppock")) {
            return new CoppockIndicator();
        }
        throw new RuntimeException("unsupported indicator '" + indicatorName + "'");
    }

    public abstract TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex);
    public abstract Color getColor();
    public abstract Color getPeakColor();

    protected void onPeak(Direction direction) {

    }

    public TresPhasedIndicator createPhased(TresExchData exchData, int phaseIndex) {
        TresPhasedIndicator phased = createPhasedInt(exchData, phaseIndex);
        m_phasedIndicators.add(phased);
        return phased;
    }

    private void onBar() {
        ChartPoint chartPoint = calcAvg();
        if (chartPoint != null) {
            synchronized (m_avgPoints) {
                m_avgPoints.add(chartPoint);
            }
            m_avgPeakCalculator.update(chartPoint);
        }
    }

    private ChartPoint calcAvg() {
        double ret = 0;
        long maxBarEnd = 0;
        for (TresPhasedIndicator indicator : m_phasedIndicators) {
            ChartPoint lastBar = indicator.getLastBar();
            if (lastBar == null) {
                return null; // not fully ready
            }
            double lastValue = lastBar.m_value;
            long barEnd = lastBar.m_millis;
            maxBarEnd = Math.max(maxBarEnd, barEnd);
            ret += lastValue;
        }
        double avgValue = ret / m_phasedIndicators.size();
        return new ChartPoint(maxBarEnd, avgValue);
    }

    public void paint(Graphics g, TresExchData exchData, ChartAxe xTimeAxe, ChartAxe yPriceAxe) {
        Utils.DoubleDoubleMinMaxCalculator minMaxCalculator = new Utils.DoubleDoubleMinMaxCalculator();
        for (TresPhasedIndicator phIndicator : m_phasedIndicators) {
            phIndicator.cloneChartPoints(xTimeAxe, minMaxCalculator);
            cloneChartPoints(m_avgPoints, m_avgPaintPoints, xTimeAxe, minMaxCalculator);
            cloneChartPoints(m_avgPeaks, m_avgPaintPeaks, xTimeAxe, minMaxCalculator);

            if (minMaxCalculator.hasValue()) {
                adjustMinMaxCalculator(minMaxCalculator);
                Double valMin = Math.min(-0.1, minMaxCalculator.m_minValue);
                Double valMax = Math.max(0.1, minMaxCalculator.m_maxValue);
                ChartAxe yAxe = new ChartAxe(valMin, valMax, yPriceAxe.m_size);
                yAxe.m_offset = yPriceAxe.m_offset;

                preDraw(g, xTimeAxe, yAxe);

                phIndicator.paint(g, xTimeAxe, yAxe);

//                paintCoppockTicks(g, avgCoppockClone, yAxe, COPPOCK_AVG_COLOR);
//                paintCoppockPeaks(g, avgCoppockPeaksClone, yAxe, COPPOCK_AVG_PEAKS_COLOR, true);
            }
        }


//        List<List<ChartPoint>> ticksAr = new ArrayList<List<ChartPoint>>();
//        List<List<ChartPoint>> peaksAr = new ArrayList<List<ChartPoint>>();
//        for (PhaseData phData : phaseDatas) {
//            TresCoppockCalculator calc = phData.m_coppockCalculator;
//            List<ChartPoint> ticks = cloneChartPoints(calc.m_coppockPoints, minMaxCalculator);
//            ticksAr.add(ticks);
//            List<ChartPoint> peaks = cloneChartPoints(calc.m_coppockPeaks, minMaxCalculator);
//            peaksAr.add(peaks);
//        }
//        List<ChartPoint> avgCoppockClone = cloneChartPoints(exchData.m_avgCoppock, minMaxCalculator);
//        List<ChartPoint> avgCoppockPeaksClone = cloneChartPoints(exchData.m_avgCoppockPeakCalculator.m_avgCoppockPeaks, minMaxCalculator);
//        if (minMaxCalculator.hasValue()) {
//            Double valMin = Math.min(-0.1, minMaxCalculator.m_minValue);
//            Double valMax = Math.max(0.1, minMaxCalculator.m_maxValue);
//            ChartAxe yAxe = new ChartAxe(valMin, valMax, getHeight() - 4);
//            yAxe.m_offset = 2;
//
//            g.setColor(COPPOCK_COLOR);
//            int y = yAxe.getPointReverse(0);
//            g.drawLine(0, y, getWidth(), y);
//
//            for (List<ChartPoint> ticksClone : ticksAr) {
//                paintCoppockTicks(g, ticksClone, yAxe, COPPOCK_COLOR);
//            }
//            for (List<ChartPoint> peaksClone : peaksAr) {
//                paintCoppockPeaks(g, peaksClone, yAxe, COPPOCK_PEAKS_COLOR, false);
//            }
//            paintCoppockTicks(g, avgCoppockClone, yAxe, COPPOCK_AVG_COLOR);
//            paintCoppockPeaks(g, avgCoppockPeaksClone, yAxe, COPPOCK_AVG_PEAKS_COLOR, true);
//        }
//
//        List<TresExchData.SymData> coppockSymClone = cloneCoppockSym(exchData.m_сoppockSym);
//        paintCoppockSym(g, coppockSymClone, yPriceAxe);
    }

    protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) { }
    protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {}

    protected static void cloneChartPoints(LinkedList<ChartPoint> points, List<ChartPoint> paintPoints,
                                           ChartAxe xTimeAxe, Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
        paintPoints.clear();
        double minTime = xTimeAxe.m_min;
        double maxTime = xTimeAxe.m_max;
        synchronized (points) {
            for (Iterator<ChartPoint> it = points.descendingIterator(); it.hasNext(); ) {
                ChartPoint chartPoint = it.next();
                long timestamp = chartPoint.m_millis;
                if (timestamp < minTime) { break; }
                if (timestamp > maxTime) { continue; }
                paintPoints.add(chartPoint);
                double val = chartPoint.m_value;
                minMaxCalculator.calculate(val);
            }
        }
    }

    protected static void paintPoints(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe, Color color, List<ChartPoint> paintPoints) {
        g.setColor(color);
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        for (ChartPoint tick : paintPoints) {
            long endTime = tick.m_millis;
            int x = xTimeAxe.getPoint(endTime);
            double val = tick.m_value;
            int y = yAxe.getPointReverse(val);
            if (lastX != Integer.MAX_VALUE) {
                g.drawLine(lastX, lastY, x, y);
            } else {
                g.drawRect(x - 1, y - 1, 2, 2);
            }
            lastX = x;
            lastY = y;
        }
    }

    protected static void paintPeaks(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe, Color color, List<ChartPoint> paintPeaks, boolean big) {
        int size = big ? 6 : 3;
        g.setColor(color);
        for (ChartPoint peak : paintPeaks) {
            long endTime = peak.m_millis;
            int x = xTimeAxe.getPoint(endTime);
            double coppock = peak.m_value;
            int y = yAxe.getPointReverse(coppock);
            g.drawLine(x - size, y - size, x + size, y + size);
            g.drawLine(x + size, y - size, x - size, y + size);
        }
    }


    public static abstract class TresPhasedIndicator {
        private final TresIndicator m_indicator;
        private final TresExchData m_exchData;
        private final int m_phaseIndex;
        final TrendWatcher<ChartPoint> m_peakCalculator;
        final LinkedList<ChartPoint> m_points = new LinkedList<ChartPoint>();
        final LinkedList<ChartPoint> m_peaks = new LinkedList<ChartPoint>();
        final List<ChartPoint> m_paintPoints = new ArrayList<ChartPoint>();
        final List<ChartPoint> m_paintPeaks = new ArrayList<ChartPoint>();
        private ChartPoint m_lastBar;

        public abstract boolean update(long timestamp, double price);
        public abstract Color getColor();
        public abstract Color getPeakColor();

        public ChartPoint getLastBar() { return m_lastBar; }

        public TresPhasedIndicator(TresIndicator tresIndicator, TresExchData exchData, int phaseIndex, double peakTolerance) {
            m_indicator = tresIndicator;
            m_exchData = exchData;
            m_phaseIndex = phaseIndex;
            m_peakCalculator = new TrendWatcher<ChartPoint>(peakTolerance) {
                @Override protected double toDouble(ChartPoint tick) { return tick.m_value; }
                @Override protected void onNewPeak(ChartPoint peak, ChartPoint last) {
                    synchronized (m_peaks) {
                        m_peaks.add(peak);
                    }
//                    m_executor.postRecheckDirection();
                }
            };
        }

        public void onBar(ChartPoint lastBar) {
            m_lastBar = lastBar;
            m_indicator.onBar();
        }

        public void cloneChartPoints(ChartAxe xTimeAxe, Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            TresIndicator.cloneChartPoints(m_points, m_paintPoints, xTimeAxe, minMaxCalculator);
            TresIndicator.cloneChartPoints(m_peaks, m_paintPeaks, xTimeAxe, minMaxCalculator);
        }

        public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
            paintPoints(g, xTimeAxe, yAxe, getColor(), m_paintPoints);
            paintPeaks(g, xTimeAxe, yAxe, getPeakColor(), m_paintPeaks, false);
        }
    }
}
