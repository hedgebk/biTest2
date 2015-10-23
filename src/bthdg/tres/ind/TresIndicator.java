package bthdg.tres.ind;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.geom.Rectangle2D;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class TresIndicator {
    public static final int AXE_MARKER_WIDTH = 10;

    private final String m_name;
    final TresAlgo m_algo;
    public final List<TresPhasedIndicator> m_phasedIndicators = new ArrayList<TresPhasedIndicator>();
    final LinkedList<ChartPoint> m_avgPoints = new LinkedList<ChartPoint>();
    final List<ChartPoint> m_avgPaintPoints = new ArrayList<ChartPoint>();
    protected boolean m_doPaint = false;
    private boolean m_doPaintPhased = false;
    protected ChartAxe m_yAxe;
    private ChartPoint m_lastPoint;
    private long m_lastTickTime;
    private double m_lastTickPrice;
    public final PeakWatcher m_peakWatcher;
    public final PeakWatcher m_halfPeakWatcher;

    public ChartPoint getLastPoint() { return m_lastPoint; }

    public TresIndicator(String name, double peakTolerance, TresAlgo algo) {
        m_name = name;
        m_algo = algo;
        m_peakWatcher = new PeakWatcher(this, peakTolerance);
        m_halfPeakWatcher = new PeakWatcher(this, peakTolerance / 2.0);
    }

    public abstract TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex);
    public abstract Color getColor();
    public abstract Color getPeakColor();

    public TresPhasedIndicator createPhased(TresExchData exchData, int phaseIndex) {
        TresPhasedIndicator phased = createPhasedInt(exchData, phaseIndex);
        if (phased != null) {
            m_phasedIndicators.add(phased);
        }
        return phased;
    }

    protected void onBar() {
        ChartPoint chartPoint = calcAvg();
        addBar(chartPoint);
    }

    public void addBar(ChartPoint chartPoint) {
        if (chartPoint != null) {
            if (m_algo.m_tresExchData.m_tres.m_collectPoints) {
                synchronized (m_avgPoints) {
                    m_avgPoints.add(chartPoint);
                }
            }
            m_peakWatcher.m_avgPeakCalculator.update(chartPoint);
            m_halfPeakWatcher.m_avgPeakCalculator.update(chartPoint);
        }
        m_lastPoint = chartPoint;
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

    public int paintYAxe(Graphics g, ChartAxe xTimeAxe, int right, ChartAxe yPriceAxe) {
        if (m_doPaint) {
            Utils.DoubleDoubleMinMaxCalculator minMaxCalculator = new Utils.DoubleDoubleMinMaxCalculator();
            for (TresPhasedIndicator phIndicator : m_phasedIndicators) {
                if (m_doPaintPhased) {
                    phIndicator.cloneChartPoints(xTimeAxe, minMaxCalculator);
                }
            }
            cloneChartPoints(m_avgPoints, m_avgPaintPoints, xTimeAxe, minMaxCalculator);
            m_peakWatcher.cloneChartPoints(xTimeAxe, minMaxCalculator);
            m_halfPeakWatcher.cloneChartPoints(xTimeAxe, minMaxCalculator);
            if (minMaxCalculator.hasValue()) {
                adjustMinMaxCalculator(minMaxCalculator);
                Double valMin = minMaxCalculator.m_minValue;
                Double valMax = minMaxCalculator.m_maxValue;
                double diff = valMax - valMin;
                double extra = diff * 0.01;
                m_yAxe = new ChartAxe(valMin - extra, valMax + extra, yPriceAxe.m_size);
                m_yAxe.m_offset = yPriceAxe.m_offset;

                return paintYAxe(g, right, m_yAxe);
            }
        }
        m_yAxe = null;
        return 0;
    }

    private int paintYAxe(Graphics g, int right, ChartAxe yAxe) {
        g.setColor(getColor());

        int fontHeight = g.getFont().getSize();
        int halfFontHeight = fontHeight / 2;
        double min = yAxe.m_min;
        double max = yAxe.m_max;
        int height = yAxe.m_size;

        int maxLabelsCount = height * 3 / fontHeight / 4;
        double diff = max - min;
        double maxLabelsStep = diff / maxLabelsCount;
        double log = Math.log10(maxLabelsStep);
        int floor = (int) Math.floor(log);
        int points = Math.max(0, -floor);
        double pow = Math.pow(10, floor);
        double mant = maxLabelsStep / pow;
        int stepMant;
        if (mant == 1) {
            stepMant = 1;
        } else if (mant <= 2) {
            stepMant = 2;
        } else if (mant <= 5) {
            stepMant = 5;
        } else {
            stepMant = 1;
            floor++;
            pow = Math.pow(10, floor);
        }
        double step = stepMant * pow;

        double minLabel = Math.floor(min / step) * step;
        double maxLabel = Math.ceil(max / step) * step;

        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(points);
        nf.setMinimumFractionDigits(points);

        FontMetrics fontMetrics = g.getFontMetrics();
        int maxWidth = 10;
        for (double y = minLabel; y <= maxLabel; y += step) {
            String str = nf.format(y);
            Rectangle2D bounds = fontMetrics.getStringBounds(str, g);
            int stringWidth = (int) bounds.getWidth();
            maxWidth = Math.max(maxWidth, stringWidth);
        }

        int x = right - maxWidth;

        for (double val = minLabel; val <= maxLabel; val += step) {
            String str = nf.format(val);
            int y = yAxe.getPointReverse(val);
            g.drawString(str, x, y + halfFontHeight);
            g.drawLine(x - 2, y, x - AXE_MARKER_WIDTH, y);
        }

//        g.drawString("h" + height, x, fontHeight * 2);
//        g.drawString("m" + maxLabelsCount, x, fontHeight * 3);
//        g.drawString("d" + diff, x, fontHeight * 4);
//        g.drawString("m" + maxLabelsStep, x, fontHeight * 5);
//        g.drawString("l" + log, x, fontHeight * 6);
//        g.drawString("f" + floor, x, fontHeight * 7);
//        g.drawString("p" + pow, x, fontHeight * 8);
//        g.drawString("m" + mant, x, fontHeight * 9);
//        g.drawString("s" + stepMant, x, fontHeight * 11);
//        g.drawString("f" + floor, x, fontHeight * 12);
//        g.drawString("p" + pow, x, fontHeight * 13);
//        g.drawString("s" + step, x, fontHeight * 14);
//        g.drawString("p" + points, x, fontHeight * 15);
//
//        g.drawString("ma" + maxLabel, x, fontHeight * 17);
//        g.drawString("mi" + minLabel, x, fontHeight * 18);

        return maxWidth + AXE_MARKER_WIDTH + 2;
    }

    public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        if (m_doPaint && (m_yAxe != null)) {
            preDraw(g, xTimeAxe, m_yAxe);
            if (m_doPaintPhased) {
                for (TresPhasedIndicator phIndicator : m_phasedIndicators) {
                    phIndicator.paint(g, xTimeAxe, m_yAxe);
                }
            }
            paintPoints(g, xTimeAxe, m_yAxe, getColor(), m_avgPaintPoints);
            Color peakColor = getPeakColor();
            m_peakWatcher.paintPeaks(g, xTimeAxe, m_yAxe, peakColor);
            m_halfPeakWatcher.paintPeaks(g, xTimeAxe, m_yAxe, peakColor);
        }
    }

    protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) { }
    protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {}

    protected static void cloneChartPoints(LinkedList<ChartPoint> points, List<ChartPoint> paintPoints,
                                           ChartAxe xTimeAxe, Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
        paintPoints.clear();
        double minTime = xTimeAxe.m_min;
        double maxTime = xTimeAxe.m_max;
        ChartPoint rightPlusPoint = null; // paint one extra point at right side
        synchronized (points) {
            for (Iterator<ChartPoint> it = points.descendingIterator(); it.hasNext(); ) {
                ChartPoint chartPoint = it.next();
                long timestamp = chartPoint.m_millis;
                if (timestamp > maxTime) {
                    rightPlusPoint = chartPoint;
                    continue;
                }
                if (rightPlusPoint != null) {
                    paintPoints.add(rightPlusPoint);
                    rightPlusPoint = null;
                }
                paintPoints.add(chartPoint);
                double val = chartPoint.m_value;
                minMaxCalculator.calculate(val);
                if (timestamp < minTime) { break; }
            }
        }
    }

    protected static void paintPoints(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe, Color color, List<ChartPoint> paintPoints) {
        g.setColor(color);
//int count = 0;
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
//int repeatedX = 0;
        for (ChartPoint tick : paintPoints) {
            long endTime = tick.m_millis;
            int x = xTimeAxe.getPoint(endTime);
            double val = tick.m_value;
            int y = yAxe.getPointReverse(val);
//g.setColor(((count++) % 2 == 0) ? color : Color.white);
            if (lastX != Integer.MAX_VALUE) {
                g.drawLine(lastX, lastY, x, y);
//g.drawLine(lastX, lastY, x, lastY);
//g.drawLine(x, lastY, x, y);
            } else {
                g.drawRect(x - 1, y - 1, 2, 2);
            }
//g.setColor(Color.red);
//g.drawLine(x, y, x, y+1);
//if(lastX == x) {
//    repeatedX++;
//    g.drawLine(x, y+5+repeatedX*3, x, y+6+repeatedX*3);
//} else {
//    repeatedX = 0;
//}

            lastX = x;
            lastY = y;
        }
    }

    protected static void paintPeaks(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe, Color color, List<ChartPoint> paintPeaks,
                                     boolean big, boolean cross) {
        int size = big ? 6 : 3;
        g.setColor(color);
        for (ChartPoint peak : paintPeaks) {
            long endTime = peak.m_millis;
            int x = xTimeAxe.getPoint(endTime);
            double coppock = peak.m_value;
            int y = yAxe.getPointReverse(coppock);
            if(cross) {
                g.drawLine(x - size, y - size, x + size, y + size);
                g.drawLine(x + size, y - size, x - size, y + size);
            } else {
                int side = size + size;
                g.drawRect(x - size, y - size, side, side);
            }
        }
    }

    public JComponent getController(final TresCanvas canvas) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 0));
        panel.setBorder(BorderFactory.createLineBorder(getColor()));
        final JCheckBox checkBox2 = new JCheckBox("f", m_doPaintPhased) {
            @Override protected void fireItemStateChanged(ItemEvent event) {
                super.fireItemStateChanged(event);
                m_doPaintPhased = (event.getStateChange() == ItemEvent.SELECTED);
                canvas.repaint();
            }
        };
        panel.add(new JCheckBox(m_name, m_doPaint) {
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

    public double getDirectionAdjusted() { // [-1...1]
        double sum = 0;
        for (TresPhasedIndicator phasedIndicator : m_phasedIndicators) {
            double direction = phasedIndicator.getDirectionAdjusted();
            sum += direction;
        }
        return sum / m_phasedIndicators.size();
    }

    public void setLastTickTimePrice(long time, double price) {
        m_lastTickTime = time;
        m_lastTickPrice = price;
    }

    public long lastTickTime() {
        if (m_lastTickTime != 0) {
            return m_lastTickTime;
        }
        long lastTickTime = 0;
        for (TresPhasedIndicator phasedIndicator : m_phasedIndicators) {
            long time = phasedIndicator.lastTickTime();
            lastTickTime = Math.max(lastTickTime, time);
        }
        return lastTickTime;
    }

    public double lastTickPrice() {
        if (m_lastTickPrice != 0) {
            return m_lastTickPrice;
        }
        double lastTickPrice = 0;
        long lastTickTime = 0;
        for (TresPhasedIndicator phasedIndicator : m_phasedIndicators) {
            long time = phasedIndicator.lastTickTime();
            if (lastTickTime < time) {
                lastTickTime = time;
                lastTickPrice = phasedIndicator.lastTickPrice();
            }
        }
        return lastTickPrice;
    }

    void onAvgPeak() {
        m_algo.onAvgPeak(TresIndicator.this);
    }


    public static abstract class TresPhasedIndicator {
        final TresIndicator m_indicator;
        protected final TresExchData m_exchData;
        private final int m_phaseIndex;
        public final TrendWatcher<ChartPoint> m_peakCalculator;
        protected final LinkedList<ChartPoint> m_points = new LinkedList<ChartPoint>();
        final LinkedList<ChartPoint> m_peaks = new LinkedList<ChartPoint>();
        final List<ChartPoint> m_paintPoints = new ArrayList<ChartPoint>();
        final List<ChartPoint> m_paintPeaks = new ArrayList<ChartPoint>();
        private ChartPoint m_lastBar;

        public abstract boolean update(long timestamp, double price);
        public abstract Color getColor();
        public abstract Color getPeakColor();
        public abstract double lastTickPrice();
        public abstract long lastTickTime();

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
            paintPeaks(g, xTimeAxe, yAxe, getPeakColor(), m_paintPeaks, false, true);
        }

        public double getDirectionAdjusted() {  // [-1 ... 1]
            Direction direction = m_peakCalculator.m_direction;
            return (direction == null) ? 0 : ((direction == Direction.FORWARD) ? 1.0 : -1.0);
        }
    }
}
