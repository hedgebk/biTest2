package bthdg.tres.ind;

import bthdg.BaseChartPaint;
import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.exch.TradeDataLight;
import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.*;
import java.util.List;

public abstract class TresIndicator {
    public static final String PRICE_AXE_NAME = "price-axe";
    public static final String VALUE_AXE_NAME = "value-axe";

    private final String m_name;
    public final TresAlgo m_algo;
    private final boolean m_collectPoints;
    public final List<TresPhasedIndicator> m_phasedIndicators = new ArrayList<TresPhasedIndicator>();
    final LinkedList<ChartPoint> m_avgPoints = new LinkedList<ChartPoint>();
    public final List<ChartPoint> m_avgPaintPoints = new ArrayList<ChartPoint>();
    public boolean m_doPaint = false;
    private boolean m_doPaintPhased = false;
    public ChartAxe m_yAxe;
    private ChartPoint m_lastPoint;
    private long m_lastTickTime;
    private double m_lastTickPrice;
    public PeakWatcher m_peakWatcher;
    public PeakWatcher m_halfPeakWatcher;

    public ChartPoint getLastPoint() { return m_lastPoint; }
    protected boolean usePriceAxe() { return false; }
    protected boolean useValueAxe() { return false; }

    public TresIndicator(String name, double peakTolerance, TresAlgo algo) {
        m_name = name;
        m_algo = algo;
        m_collectPoints = m_algo.m_tresExchData.m_tres.m_collectPoints;
        if(peakTolerance > 0) {
            if (countPeaks()) {
                m_peakWatcher = new PeakWatcher(this, peakTolerance);
                if (countHalfPeaks()) {
                    m_halfPeakWatcher = new PeakWatcher(this, peakTolerance / 2.0);
                }
            }
        }
    }

    public abstract TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex);
    public abstract Color getColor();

    protected boolean countPeaks() { return true; }
    protected boolean countHalfPeaks() { return true; }
    public Color getPeakColor() { return getColor(); } // the same as main color by def
    protected boolean doPaint() { return m_doPaint; }
    protected ILineColor getLineColor() { return null; }

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
            if (m_collectPoints) {
                synchronized (m_avgPoints) {
                    m_avgPoints.add(chartPoint);
                }
            }
            if (m_peakWatcher != null) {
                m_peakWatcher.m_avgPeakCalculator.update(chartPoint);
                if (m_halfPeakWatcher != null) {
                    m_halfPeakWatcher.m_avgPeakCalculator.update(chartPoint);
                }
            }
        }
        m_lastPoint = chartPoint;
    }

    public Double calcAvgValue() {
        double ret = 0;
        for (TresPhasedIndicator indicator : m_phasedIndicators) {
            ChartPoint lastBar = indicator.getLastBar();
            if (lastBar == null) {
                return null; // not fully ready
            }
            double lastValue = lastBar.m_value;
            ret += lastValue;
        }
        return ret / m_phasedIndicators.size();
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
            if (Double.isNaN(lastValue)) {
                return null; // not fully ready
            }
            long barEnd = lastBar.m_millis;
            maxBarEnd = Math.max(maxBarEnd, barEnd);
            ret += lastValue;
        }
        double avgValue = ret / m_phasedIndicators.size();
        return new ChartPoint(maxBarEnd, avgValue);
    }

    public int paintYAxe(Graphics g, ChartAxe xTimeAxe, int right, Map<String,ChartAxe> yAxes) {
        if (doPaint()) {
            Utils.DoubleDoubleMinMaxCalculator minMaxCalculator = new Utils.DoubleDoubleMinMaxCalculator();
            for (TresPhasedIndicator phIndicator : m_phasedIndicators) {
                if (m_doPaintPhased) {
                    phIndicator.cloneChartPoints(xTimeAxe, minMaxCalculator);
                }
            }
            cloneChartPoints(m_avgPoints, m_avgPaintPoints, xTimeAxe, minMaxCalculator);
            if (countPeaks() && (m_peakWatcher != null)) {
                m_peakWatcher.cloneChartPoints(xTimeAxe, minMaxCalculator);
                if (m_halfPeakWatcher != null) {
                    m_halfPeakWatcher.cloneChartPoints(xTimeAxe, minMaxCalculator);
                }
            }
            if (minMaxCalculator.hasValue()) {
                adjustMinMaxCalculator(minMaxCalculator);
                Double valMin = minMaxCalculator.m_minValue;
                Double valMax = minMaxCalculator.m_maxValue;
                double diff = valMax - valMin;
                double extra = diff * 0.01;
                valMin -= extra;
                valMax += extra;

                String yAxeName = getYAxeName();
                ChartAxe yAxe = yAxes.get(yAxeName);
                if(yAxe == null) { // new axe
                    ChartAxe yPriceAxe = yAxes.get(PRICE_AXE_NAME);
                    m_yAxe = new ChartAxe(valMin - extra, valMax + extra, yPriceAxe.m_size);
                    yAxes.put(yAxeName, m_yAxe);
                    m_yAxe.m_offset = yPriceAxe.m_offset;
                    return paintYAxe(g, right, m_yAxe);
                } else {
                    yAxe.updateBounds(valMin, valMax);
                    m_yAxe = yAxe;
                    return 0;
                }
            }
        }
        m_yAxe = null;
        return 0;
    }

    protected String getYAxeName() {
        if (usePriceAxe()) {
            return PRICE_AXE_NAME;
        } else if (useValueAxe()) {
            return VALUE_AXE_NAME;
        } else {
            return m_name;
        }
    }

    private int paintYAxe(Graphics g, int right, ChartAxe yAxe) {
        Color color = getColor();
        return yAxe.paintYAxe(g, right, color);
    }

    public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        if (doPaint() && (m_yAxe != null)) {
            preDraw(g, xTimeAxe, m_yAxe);
            if (m_doPaintPhased) {
                for (TresPhasedIndicator phIndicator : m_phasedIndicators) {
                    phIndicator.paint(g, xTimeAxe, m_yAxe);
                }
            }
            paintPoints(g, xTimeAxe, m_yAxe, getColor(), getLineColor(), m_avgPaintPoints);
            if (countPeaks() && (m_peakWatcher != null)) {
                Color peakColor = getPeakColor();
                m_peakWatcher.paintPeaks(g, xTimeAxe, m_yAxe, peakColor);
                if (m_halfPeakWatcher != null) {
                    m_halfPeakWatcher.paintPeaks(g, xTimeAxe, m_yAxe, peakColor);
                }
            }
        }
    }

    protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
        if (centerYZeroLine()) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }
    }
    protected boolean drawZeroLine() { return false; }
    protected boolean centerYZeroLine() { return false; }

    protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
        if (drawZeroLine()) {
            drawZeroHLine(g, xTimeAxe, yAxe);
        }
    }

    protected void drawZeroHLine(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
        g.setColor(getColor());
        int y = yAxe.getPointReverse(0);
        g.drawLine(xTimeAxe.getPoint(xTimeAxe.m_min), y, xTimeAxe.getPoint(xTimeAxe.m_max), y);
    }

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

    private static void paintPoints(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe, Color color, ILineColor iLineColor, List<ChartPoint> paintPoints) {
        Color lastColor = null;
        long lastTime = 0;
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        Double lastVal = null;
        for (ChartPoint tick : paintPoints) {
            long endTime = tick.m_millis;
            int x = xTimeAxe.getPoint(endTime);
            double val = tick.m_value;
            int y = yAxe.getPointReverse(val);

            Color lineColor = (iLineColor == null)
                                ? color
                                : (endTime > lastTime)
                                    ? iLineColor.getColor(val, lastVal)
                                    : iLineColor.getColor(lastVal, val);
            if(lineColor != lastColor) {
                g.setColor(lineColor);
                lastColor = lineColor;
            }

            if (lastX != Integer.MAX_VALUE) {
                g.drawLine(lastX, lastY, x, y);
            } else {
                g.drawRect(x - 1, y - 1, 2, 2);
            }
            lastX = x;
            lastY = y;
            lastVal = val;
            lastTime = endTime;
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
            if (cross) {
                BaseChartPaint.drawX(g, x, y, size);
            } else {
                int side = size + size;
                g.drawRect(x - size, y - size, side, side);
            }
        }
    }

    public JComponent getController(final TresCanvas canvas) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        panel.setBackground(getColor());
//        panel.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, getColor()));
        final JCheckBox checkBox2 = new JCheckBox("f", m_doPaintPhased) {
            @Override protected void fireItemStateChanged(ItemEvent event) {
                super.fireItemStateChanged(event);
                m_doPaintPhased = (event.getStateChange() == ItemEvent.SELECTED);
                canvas.repaint();
            }
        };
        checkBox2.setOpaque(false);
        JCheckBox checkBox1 = new JCheckBox(m_name, m_doPaint) {
            @Override protected void fireItemStateChanged(ItemEvent event) {
                super.fireItemStateChanged(event);
                m_doPaint = (event.getStateChange() == ItemEvent.SELECTED);
                checkBox2.setEnabled(m_doPaint);
                canvas.repaint();
            }
        };
        checkBox1.setOpaque(false);
        panel.add(checkBox1);
        panel.add(checkBox2);
        if (m_phasedIndicators.isEmpty()) {
            checkBox2.setVisible(false);
        }
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

    protected void onAvgPeak(TrendWatcher<ChartPoint> trendWatcher) {
        m_algo.onAvgPeak(TresIndicator.this, trendWatcher);
    }


    // =================================================================================
    public static abstract class TresPhasedIndicator {
        final TresIndicator m_indicator;
        protected final TresExchData m_exchData;
        private final int m_phaseIndex;
        public TrendWatcher<ChartPoint> m_peakCalculator;
        protected final LinkedList<ChartPoint> m_points = new LinkedList<ChartPoint>();
        final LinkedList<ChartPoint> m_peaks = new LinkedList<ChartPoint>();
        final List<ChartPoint> m_paintPoints = new ArrayList<ChartPoint>();
        final List<ChartPoint> m_paintPeaks = new ArrayList<ChartPoint>();
        private ChartPoint m_lastBar;

        /** @return true if current bar changed */
        public abstract boolean update(TradeDataLight tdata);
        public abstract Color getColor();
        public abstract double lastTickPrice();
        public abstract long lastTickTime();

        public ChartPoint getLastBar() { return m_lastBar; }
        public Color getPeakColor() { return getColor(); }
        protected ILineColor getLineColor() { return null; }

        public TresPhasedIndicator(TresIndicator tresIndicator, TresExchData exchData, int phaseIndex, Double peakTolerance) {
            m_indicator = tresIndicator;
            m_exchData = exchData;
            m_phaseIndex = phaseIndex;
            if(peakTolerance != null) {
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
            paintPoints(g, xTimeAxe, yAxe, getColor(), getLineColor(), m_paintPoints);
            paintPeaks(g, xTimeAxe, yAxe, getPeakColor(), m_paintPeaks, false, true);
        }


        public double getDirectionAdjusted() {  // [-1 ... 1]
            if (m_peakCalculator == null) {
                return 0;
            }
            Direction direction = m_peakCalculator.m_direction;
            return (direction == null) ? 0 : ((direction == Direction.FORWARD) ? 1.0 : -1.0);
        }

        protected void collectPointIfNeeded(ChartPoint tick) {
            if (m_exchData.m_tres.m_collectPoints) {
                m_points.add(tick); // add to the end
            }
        }
    }

    // =================================================================================
    public interface ILineColor {
        Color getColor(Double val, Double lastVal);


        ILineColor PRICE = new ILineColor() {
            @Override public Color getColor(Double val, Double lastVal) {
                return (val == null) || (lastVal == null) || val.equals(lastVal)
                        ? Color.white
                        : (val > lastVal) ? Color.GREEN : Color.RED;
            }
        };
    }
}
