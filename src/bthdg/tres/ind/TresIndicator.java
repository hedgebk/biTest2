package bthdg.tres.ind;

import bthdg.ChartAxe;
import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class TresIndicator {
    private final String m_name;
    private final List<TresPhasedIndicator> m_phasedIndicators = new ArrayList<TresPhasedIndicator>();
    final LinkedList<ChartPoint> m_avgPoints = new LinkedList<ChartPoint>();
    final LinkedList<ChartPoint> m_avgPeaks = new LinkedList<ChartPoint>();
    final List<ChartPoint> m_avgPaintPoints = new ArrayList<ChartPoint>();
    final List<ChartPoint> m_avgPaintPeaks = new ArrayList<ChartPoint>();
    public final TrendWatcher<ChartPoint> m_avgPeakCalculator;
    private boolean m_doPaint = true;
    private boolean m_doPaintPhased = false;

    public TresIndicator(String name, double peakTolerance, final TresAlgo algo) {
        m_name = name;
        m_avgPeakCalculator = new TrendWatcher<ChartPoint>(peakTolerance) {
            @Override protected double toDouble(ChartPoint tick) { return tick.m_value; }
            @Override protected void onNewPeak(ChartPoint peak, ChartPoint last) {
                synchronized (m_avgPeaks) {
                    m_avgPeaks.add(peak);
                }
                algo.onAvgPeak(TresIndicator.this);
            }
        };
    }

    public abstract TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex);
    public abstract Color getColor();
    public abstract Color getPeakColor();

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

    public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe) {
        if (m_doPaint) {
            Utils.DoubleDoubleMinMaxCalculator minMaxCalculator = new Utils.DoubleDoubleMinMaxCalculator();
            for (TresPhasedIndicator phIndicator : m_phasedIndicators) {
                if (m_doPaintPhased) {
                    phIndicator.cloneChartPoints(xTimeAxe, minMaxCalculator);
                }
                cloneChartPoints(m_avgPoints, m_avgPaintPoints, xTimeAxe, minMaxCalculator);
                cloneChartPoints(m_avgPeaks, m_avgPaintPeaks, xTimeAxe, minMaxCalculator);
            }
            if (minMaxCalculator.hasValue()) {
                adjustMinMaxCalculator(minMaxCalculator);
                Double valMin = minMaxCalculator.m_minValue;
                Double valMax = minMaxCalculator.m_maxValue;
                ChartAxe yAxe = new ChartAxe(valMin, valMax, yPriceAxe.m_size);
                yAxe.m_offset = yPriceAxe.m_offset;

                preDraw(g, xTimeAxe, yAxe);
                if (m_doPaintPhased) {
                    for (TresPhasedIndicator phIndicator : m_phasedIndicators) {
                        phIndicator.paint(g, xTimeAxe, yAxe);
                    }
                }
                paintPoints(g, xTimeAxe, yAxe, getColor(), m_avgPaintPoints);
                paintPeaks(g, xTimeAxe, yAxe, getPeakColor(), m_avgPaintPeaks, true);
            }
        }
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

    public JComponent getController(final TresCanvas canvas) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 1));
        panel.setBorder(BorderFactory.createLineBorder(Color.black));
        final JCheckBox checkBox2 = new JCheckBox("f", false) {
            @Override protected void fireItemStateChanged(ItemEvent event) {
                super.fireItemStateChanged(event);
                m_doPaintPhased = (event.getStateChange() == ItemEvent.SELECTED);
                canvas.repaint();
            }
        };
        checkBox2.setEnabled(false);
        panel.add(new JCheckBox(m_name, true) {
            @Override protected void fireItemStateChanged(ItemEvent event) {
                super.fireItemStateChanged(event);
                m_doPaint = (event.getStateChange() == ItemEvent.SELECTED);
                checkBox2.setEnabled(m_doPaint);
                canvas.repaint();
            }
        });
        panel.add(checkBox2);
        return panel;
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
