package bthdg.tres;

import bthdg.ChartAxe;
import bthdg.Log;
import bthdg.calc.OHLCTick;
import bthdg.calc.OscTick;
import bthdg.exch.*;
import bthdg.osc.BaseExecutor;
import bthdg.tres.alg.TresAlgoWatcher;
import bthdg.util.Colors;
import bthdg.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class TresCanvas extends JComponent {
    public static final int PIX_PER_BAR = 12;
    public static final int LAST_PRICE_MARKER_WIDTH = 10;
    public static final int PRICE_AXE_MARKER_WIDTH = 10;
    public static final Color TR_COLOR = new Color(20, 20, 20);
    public static final Color OSC_1_LINE_COLOR = Colors.setAlpha(Color.RED, 44);
    public static final Color OSC_2_LINE_COLOR = Colors.setAlpha(Color.BLUE, 44);
    public static final Color OSC_MID_LINE_COLOR = new Color(30, 30, 30, 128);
    public static final int DIRECTION_ARROW_SIZE = 20;
    public static final Color BID_ASK_COLOR = Colors.setAlpha(Color.darkGray, 90);
    public static final Color AVG_OSCS_COLOR = Color.pink;
    public static final Color OSC_PEAKS_COLOR = Colors.setAlpha(Colors.LIGHT_CYAN, 127);
    public static final Color BAR_HIGHLIGHT_COLOR = new Color(32, 32, 32);
    public static final Color COPPOCK_AVG_COLOR = Color.CYAN;
    public static final Color COPPOCK_COLOR = Colors.setAlpha(COPPOCK_AVG_COLOR, 40);
    public static final Color COPPOCK_AVG_PEAKS_COLOR = Color.WHITE;
    public static final Color COPPOCK_PEAKS_COLOR = Colors.setAlpha(COPPOCK_AVG_PEAKS_COLOR, 60);
    public static final Color CCI_COLOR = Colors.setAlpha(Colors.LIGHT_ORANGE, 40);
    public static final Color CCI_AVG_COLOR = new Color(230, 100, 43);

    protected static boolean m_paintSym = true;
    protected static boolean m_paintOsc = true;
    protected static boolean m_paintCoppock = true;
    protected static boolean m_paintCci = true;

    private Tres m_tres;
    private Point m_point;
    private ChartAxe m_yAxe;
    private ChartAxe m_xTimeAxe;
    private int m_maxBars;
    private int yPriceAxeWidth = PRICE_AXE_MARKER_WIDTH;
    private double m_zoom = 1;
    private Integer m_dragStartX;
    private Integer m_dragDeltaX;
    private int m_dragDeltaBars;
    private int m_barsShift = 0;
    private List<TradeData> m_paintTades = new ArrayList<TradeData>();
    private List<ChartPoint> m_paintAvgOscs = new ArrayList<ChartPoint>();
    private List<ChartPoint> m_paintAvgCcis = new ArrayList<ChartPoint>();
    private List<BaseExecutor.TopDataPoint> m_paintTops = new ArrayList<BaseExecutor.TopDataPoint>();
    private List<TresExchData.OrderPoint> m_paintOrders = new ArrayList<TresExchData.OrderPoint>();
    private List<OHLCTick> m_paintOhlcTicks = new ArrayList<OHLCTick>();
    private List<OscTick> m_paintOscPeaks = new ArrayList<OscTick>();
    private final List<ChartPoint> m_paintAvgOscPeaks = new ArrayList<ChartPoint>();
    private List<ChartPoint> m_paintCciTicks = new ArrayList<ChartPoint>();

    private static void log(String s) { Log.log(s); }

    TresCanvas(Tres tres) {
        m_tres = tres;
        setMinimumSize(new Dimension(800, 500));
        setPreferredSize(new Dimension(800, 500));
        setBackground(Color.BLACK);

        MouseAdapter mouseAdapter = new MouseAdapter() {

            @Override public void mouseEntered(MouseEvent e) { updatePoint(e.getPoint()); }
            @Override public void mouseExited(MouseEvent e) { updatePoint(null); }
            @Override public void mouseMoved(MouseEvent e) { updatePoint(e.getPoint()); }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                onMouseWheelMoved(e);
            }

            @Override public void mousePressed(MouseEvent e) {
                int x = e.getX();
                m_dragStartX = x;
                m_point = e.getPoint();
                repaint(150);
            }

            @Override public void mouseDragged(MouseEvent e) {
                int x = e.getX();
                m_dragDeltaX = x - m_dragStartX;
                m_dragDeltaBars = m_dragDeltaX / pixPerBar();
                m_point = e.getPoint();
                repaint(150);
            }

            @Override public void mouseReleased(MouseEvent e) {
                int x = e.getX();
                int dragDeltaX = x - m_dragStartX;
                int dragDeltaBars = dragDeltaX / pixPerBar();
                m_barsShift += dragDeltaBars;
                m_dragStartX = null;
                m_dragDeltaX = null;
                m_dragDeltaBars = 0;
                m_point = e.getPoint();
                repaint(150);
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
        addMouseWheelListener(mouseAdapter);
    }

    private void onMouseWheelMoved(MouseWheelEvent e) {
        int notches = e.getWheelRotation();
        if (notches < 0) {
            m_zoom *= 1.1;
        } else {
            m_zoom /= 1.1;
        }
        repaint();
    }

    private void updatePoint(Point point) {
        m_point = point;
        repaint(150);
    }

    @Override public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        m_yAxe = new ChartAxe(0, 1, height - 4);
        m_yAxe.m_offset = 2;
        calcMaxBars(width);
    }

    private void calcMaxBars(int width) {
        m_maxBars = width / pixPerBar() + 1;
    }

    private int pixPerBar() {
        double ret = PIX_PER_BAR * m_zoom;
        return (ret < 1) ? 1 : (int)ret;
    }

    @Override public void paint(Graphics g) {
        super.paint(g);

        g.setFont(g.getFont().deriveFont(15.0f));

        int width = getWidth();
        int height = getHeight();

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        if(Tres.PAINT_TICK_TIMES_ONLY) {
            paintTimeTicks(g, width, height);
        } else {
            paintMainChart(g, width, height);
        }
    }

    private void paintMainChart(Graphics g, int width, int height) {
        long barSize = m_tres.m_barSizeMillis;

        if (m_point != null) {
            paintBarHighlight(g, barSize);
        }

        ArrayList<TresExchData> exchDatas = m_tres.m_exchDatas;
        TresExchData exchData = exchDatas.get(0);
        PhaseData[] phaseDatas = exchData.m_phaseDatas;
        PhaseData phaseData = phaseDatas[0];

        TresOscCalculator oscCalculator;
        LinkedList<OscTick> oscBars;
        if (m_tres.m_calcOsc) {
            oscCalculator = phaseData.m_oscCalculator;
            oscBars = oscCalculator.m_oscBars;
        } else {
            oscCalculator = null;
            oscBars = null;
        }
        calcXTimeAxe(width, barSize, phaseData.m_ohlcCalculator.m_ohlcTicks, oscBars);

        List<OHLCTick> ohlcTicksClone = cloneOhlcTicks(phaseData.m_ohlcCalculator.m_ohlcTicks);

        // paint min/max time : left/right borders
        double minTime = m_xTimeAxe.m_min;
        int minTimeX = m_xTimeAxe.getPoint(minTime);
        double maxTime = m_xTimeAxe.m_max;
        int maxTimeX = m_xTimeAxe.getPoint(maxTime);
        g.setColor(Color.BLUE);
        g.drawLine(minTimeX, 0, minTimeX, height);
        g.drawLine(maxTimeX, 0, maxTimeX, height);

        double lastPrice = exchData.m_lastPrice;
        double buyPrice = exchData.m_executor.m_buy;
        double sellPrice = exchData.m_executor.m_sell;
        if (lastPrice != 0) {
            Exchange exchange = exchData.m_ws.exchange();
            String lastStr = exchange.roundPriceStr(lastPrice, Tres.PAIR);
            String buyStr = exchange.roundPriceStr(buyPrice, Tres.PAIR);
            String sellStr = exchange.roundPriceStr(sellPrice, Tres.PAIR);
            int fontHeight = g.getFont().getSize();
            g.drawString("Last: " + lastStr + "; buy: " + buyStr + "; sell: " + sellStr, 5, fontHeight + 5);

            if (m_tres.m_calcOsc) {
                OscTick fineTick = oscCalculator.m_lastFineTick;
                paintFineOsc(g, fineTick, width - 5);

                OscTick blendedTick = oscCalculator.m_blendedLastFineTick;
                OscTick lastBarTick = oscCalculator.m_lastBar;
                paintBlendedOsc(g, lastBarTick, blendedTick, width - 5);
            }

            g.drawString(String.format("Zoom: %1$,.4f", m_zoom) + "; fontHeight=" + fontHeight, 5, fontHeight * 2 + 5);
            g.drawString(getBarsShiftStr(), 5, fontHeight * 3 + 5);

            List<TresExchData.OrderPoint> ordersClone = getOrderClone(exchData);
            List<BaseExecutor.TopDataPoint> topsClone = getTopsClone(exchData.m_executor.m_tops);

            ChartAxe yPriceAxe = calcYPriceAxe(height, lastPrice, buyPrice, sellPrice, ohlcTicksClone, ordersClone, topsClone);
            paintYPriceAxe(g, yPriceAxe);

            paintTops(g, topsClone, yPriceAxe);
            paintTrades(g, exchData.m_trades, yPriceAxe);
            paintOHLCTicks(g, ohlcTicksClone, yPriceAxe);

            if (m_paintOsc) {
                // osc levels
                g.setColor(Color.darkGray);
                paintLine(g, 0.2);
                paintLine(g, 0.8);
            }
            if (m_tres.m_calcOsc && m_paintOsc) {
                for (PhaseData phData : phaseDatas) {
                    TresOscCalculator phaseOscCalculator = phData.m_oscCalculator;
                    paintOscTicks(g, phaseOscCalculator.m_oscBars);
                    paintOscPeaks(g, phaseOscCalculator.m_oscPeaks);
                }
                paintAvgOscs(g, exchData.m_avgOscs);
                paintAvgOscPeaks(g, exchData.m_avgOscsPeakCalculator.m_avgOscsPeaks);
            }
            if (m_paintCoppock) {
                paintCoppock(g, exchData, phaseDatas, yPriceAxe);
            }
            if (m_paintCci) {
                for (PhaseData phData : phaseDatas) {
                    TresCciCalculator phaseCciCalculator = phData.m_cciCalculator;
                    paintCciTicks(g, phaseCciCalculator.m_cciPoints);
//                paintCciPeaks(g, phaseCciCalculator.m_cciPeaks);
                }
                paintAvgCci(g, exchData.m_avgCci);
            }

            paintAlgos(g, exchData, yPriceAxe);

            paintMaTicks(g, phaseData.m_maCalculator, yPriceAxe);
            paintOrders(g, exchData, yPriceAxe, ordersClone);

            paintBuyPrice(g, width, buyPrice, yPriceAxe);
            paintSellPrice(g, width, sellPrice, yPriceAxe);
            paintLastPrice(g, width, lastPrice, yPriceAxe);
        }

        if (m_point != null) {
            paintCross(g, width, height);
        }
    }

    private void paintAlgos(Graphics g, TresExchData exchData, ChartAxe yPriceAxe) {
        for (TresAlgoWatcher algoWatcher : exchData.m_algos) {
            algoWatcher.paint(g, exchData, m_xTimeAxe, yPriceAxe);
        }
    }

    private void paintCoppock(Graphics g, TresExchData exchData, PhaseData[] phaseDatas, ChartAxe yPriceAxe) {
        Utils.DoubleDoubleMinMaxCalculator minMaxCalculator = new Utils.DoubleDoubleMinMaxCalculator();
        List<List<ChartPoint>> ticksAr = new ArrayList<List<ChartPoint>>();
        List<List<ChartPoint>> peaksAr = new ArrayList<List<ChartPoint>>();
        for (PhaseData phData : phaseDatas) {
            TresCoppockCalculator calc = phData.m_coppockCalculator;
            List<ChartPoint> ticks = cloneChartPoints(calc.m_coppockPoints, minMaxCalculator);
            ticksAr.add(ticks);
            List<ChartPoint> peaks = cloneChartPoints(calc.m_coppockPeaks, minMaxCalculator);
            peaksAr.add(peaks);
        }
        List<ChartPoint> avgCoppockClone = cloneChartPoints(exchData.m_avgCoppock, minMaxCalculator);
        List<ChartPoint> avgCoppockPeaksClone = cloneChartPoints(exchData.m_avgCoppockPeakCalculator.m_avgCoppockPeaks, minMaxCalculator);
        if (minMaxCalculator.hasValue()) {
            Double valMin = Math.min(-0.1, minMaxCalculator.m_minValue);
            Double valMax = Math.max(0.1, minMaxCalculator.m_maxValue);
            ChartAxe yAxe = new ChartAxe(valMin, valMax, getHeight() - 4);
            yAxe.m_offset = 2;

            g.setColor(COPPOCK_COLOR);
            int y = yAxe.getPointReverse(0);
            g.drawLine(0, y, getWidth(), y);

            for (List<ChartPoint> ticksClone : ticksAr) {
                paintCoppockTicks(g, ticksClone, yAxe, COPPOCK_COLOR);
            }
            for (List<ChartPoint> peaksClone : peaksAr) {
                paintCoppockPeaks(g, peaksClone, yAxe, COPPOCK_PEAKS_COLOR, false);
            }
            paintCoppockTicks(g, avgCoppockClone, yAxe, COPPOCK_AVG_COLOR);
            paintCoppockPeaks(g, avgCoppockPeaksClone, yAxe, COPPOCK_AVG_PEAKS_COLOR, true);
        }

        List<TresExchData.SymData> coppockSymClone = cloneCoppockSym(exchData.m_сoppockSym);
        paintCoppockSym(g, coppockSymClone, yPriceAxe);
    }

    private void paintCoppockSym(Graphics g, List<TresExchData.SymData> coppockSymClone, ChartAxe yPriceAxe) {
        int fontHeight = g.getFont().getSize();
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        TresExchData.SymData lastCoppockSym = null;
        for (TresExchData.SymData coppockSym : coppockSymClone) {
            long millis = coppockSym.m_millis;
            int x = m_xTimeAxe.getPoint(millis);
            double price = coppockSym.m_price;
            int y = yPriceAxe.getPointReverse(price);
            if (lastX != Integer.MAX_VALUE) {
                g.setColor((lastCoppockSym.m_priceRatio > 1) ? Color.GREEN : Color.RED);
                g.drawLine(lastX, lastY, x, y);
            } else {
                g.setColor(Colors.BEGIE);
                g.drawRect(x - 1, y - 1, 2, 2);
            }

            g.drawString(String.format("p: %1$,.2f", price), x, y + fontHeight);

            double priceRatio = coppockSym.m_priceRatio;
            g.setColor((priceRatio > 1) ? Color.GREEN : Color.RED);
            g.drawString(String.format("r: %1$,.5f", priceRatio), x, y + fontHeight * 2);

            double totalPriceRatio = coppockSym.m_totalPriceRatio;
            g.setColor((totalPriceRatio > 1) ? Color.GREEN : Color.RED);
            g.drawString(String.format("t: %1$,.5f", totalPriceRatio), x, y + fontHeight * 3);

            lastCoppockSym = coppockSym;
            lastX = x;
            lastY = y;
        }
    }

    private List<TresExchData.SymData> cloneCoppockSym(LinkedList<TresExchData.SymData> сoppockSym) {
        double minTime = m_xTimeAxe.m_min;
        double maxTime = m_xTimeAxe.m_max;
        List<TresExchData.SymData> ret = new ArrayList<TresExchData.SymData>();
        synchronized (сoppockSym) {
            for (Iterator<TresExchData.SymData> it = сoppockSym.descendingIterator(); it.hasNext(); ) {
                TresExchData.SymData сoppockSymData = it.next();
                long timestamp = сoppockSymData.m_millis;
                if (timestamp > maxTime) { continue; }
                ret.add(сoppockSymData);
                if (timestamp < minTime) { break; }
            }
        }
        return ret;
    }

    private String getBarsShiftStr() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("barsShift: %d", m_barsShift));
        if (m_dragStartX != null) {
            sb.append(String.format("; dragStartX: %d", m_dragStartX));
        }
        if (m_dragDeltaX != null) {
            sb.append(String.format("; dragDeltaX: %d", m_dragDeltaX));
        }
        if (m_dragDeltaBars != 0) {
            sb.append(String.format("; dragDeltaBars: %d", m_dragDeltaBars));
        }
        return sb.toString();
    }

    private void paintTimeTicks(Graphics g, int width, int height) {
        long minTickTime = Long.MAX_VALUE;
        long maxTickTime = 0;
        for (Long tickTime : m_tres.m_tickTimes) {
            minTickTime = Math.min(minTickTime, tickTime);
            maxTickTime = Math.max(maxTickTime, tickTime);
        }
        ChartAxe yTickTimeAxe = new ChartAxe(minTickTime, maxTickTime, height);
        ChartAxe xTickCountAxe = new ChartAxe(0, m_tres.m_tickTimes.size(), width);

        g.setColor(Color.red);
        int count = 0;
        int prevX = -1;
        int prevY = -1;
        for (Long tickTime : m_tres.m_tickTimes) {
            int x = xTickCountAxe.getPoint(count);
            int y = yTickTimeAxe.getPointReverse(tickTime);
            if ((prevX != -1) && (prevY != -1)) {
                g.drawLine(prevX, prevY, x, y);
            }
            prevX = x;
            prevY = y;
            count++;
        }
    }

    private void paintLastPrice(Graphics g, int width, double lastPrice, ChartAxe yPriceAxe) {
        paintPriceMarker(g, width, lastPrice, yPriceAxe, Color.GREEN, LAST_PRICE_MARKER_WIDTH);
    }

    private void paintBuyPrice(Graphics g, int width, double buyPrice, ChartAxe yPriceAxe) {
        paintPriceMarker(g, width, buyPrice, yPriceAxe, Color.RED, LAST_PRICE_MARKER_WIDTH * 2);
    }

    private void paintSellPrice(Graphics g, int width, double sellPrice, ChartAxe yPriceAxe) {
        paintPriceMarker(g, width, sellPrice, yPriceAxe, Color.BLUE, LAST_PRICE_MARKER_WIDTH * 2);
    }

    private void paintPriceMarker(Graphics g, int width, double price, ChartAxe yPriceAxe, Color color, int markerWidth) {
        if (price > 0) {
            g.setColor(color);
            int lastPriceY = yPriceAxe.getPointReverse(price);
            g.fillRect(width - markerWidth, lastPriceY - 1, markerWidth, 3);
        }
    }

    private void paintYPriceAxe(Graphics g, ChartAxe yPriceAxe) {
        g.setColor(Color.ORANGE);

        int fontHeight = g.getFont().getSize();
        int halfFontHeight = fontHeight / 2;
        double min = yPriceAxe.m_min;
        double max = yPriceAxe.m_max;

        int step = 1;
        int minLabel = (int) Math.floor(min / step);
        int maxLabel = (int) Math.ceil(max / step);

        FontMetrics fontMetrics = g.getFontMetrics();
        int maxWidth = 10;
        for (int y = minLabel; y <= maxLabel; y += step) {
            Rectangle2D bounds = fontMetrics.getStringBounds(Integer.toString(y), g);
            int stringWidth = (int) bounds.getWidth();
            maxWidth = Math.max(maxWidth, stringWidth);
        }
        int width = getWidth();
        int x = width - maxWidth;

        int priceY0 = yPriceAxe.getPointReverse(minLabel);
        int priceY1 = yPriceAxe.getPointReverse(minLabel + step);
        int stepHeight = Math.abs(priceY1 - priceY0);
        double[] steps = new double[]{0.1, 0.2, 0.5};

        for (int y = minLabel; y <= maxLabel; y += step) {
            int priceY = yPriceAxe.getPointReverse(y);
            g.drawString(Integer.toString(y), x, priceY + halfFontHeight);
            g.drawLine(x - 2, priceY, x - PRICE_AXE_MARKER_WIDTH, priceY);

            for (double semiStep : steps) {
                int semiStepsNum = (int) Math.round(1.0 / semiStep);
                int semiStepsHeight = (int) (semiStepsNum * fontHeight * 1.5);
                if (stepHeight > semiStepsHeight) { // can fit
                    double y2 = semiStep;
                    for (int i = 1; i < semiStepsNum; i++) {
                        int priceY2 = yPriceAxe.getPointReverse(y + y2);
                        g.drawString(String.format(" %1$,.1f", y2), x, priceY2 + halfFontHeight);
                        g.drawLine(x - 2, priceY2, x - PRICE_AXE_MARKER_WIDTH / 2, priceY2);
                        y2 += semiStep;
                    }
                    break;
                }
            }
        }
        if (yPriceAxeWidth != maxWidth) { // changed
            calcMaxBars(width);
        }
        yPriceAxeWidth = maxWidth + PRICE_AXE_MARKER_WIDTH + 5;
    }

    private void paintBarHighlight(Graphics g, long barSize) {
        int x = (int) m_point.getX();
        long millis = (long) m_xTimeAxe.getValueFromPoint(x);
        long barStart = millis / barSize * barSize;
        int barStartX = m_xTimeAxe.getPoint(barStart);
        long barEnd = barStart + barSize;
        int barEndX = m_xTimeAxe.getPoint(barEnd);
        g.setColor(BAR_HIGHLIGHT_COLOR);
        g.fillRect(barStartX, 0, barEndX - barStartX, getWidth());
    }

    private void paintCross(Graphics g, int width, int height) {
        int x = (int) m_point.getX();
        int y = (int) m_point.getY();

        g.setColor(Color.LIGHT_GRAY);
        g.drawLine(x, 0, x, height);
        g.drawLine(0, y, width, y);
    }

    private void calcXTimeAxe(int width, long barSize, LinkedList<OHLCTick> ohlcTicks, LinkedList<OscTick> bars) {
        long maxTime = 0;
        OHLCTick lastOhlcTick;
        synchronized (ohlcTicks) {
            lastOhlcTick = ohlcTicks.peekLast();
        }
        if (lastOhlcTick != null) {
            maxTime = lastOhlcTick.m_barEnd;
        }
        if (bars != null) {
            OscTick lastOscTick = bars.peekLast();
            if (lastOscTick != null) {
                long endTime = lastOscTick.m_startTime + barSize;
                maxTime = Math.max(maxTime, endTime);
            }
        }
        if (maxTime == 0) {
            maxTime = System.currentTimeMillis();
        }
        maxTime -= (m_barsShift + m_dragDeltaBars) * barSize;

        int areaWidth = width - yPriceAxeWidth;
        int pixPerBar = pixPerBar();
        int maxBarNum = areaWidth / pixPerBar + 1;
        long minTime = maxTime - barSize * maxBarNum;
        int barsWidth = maxBarNum * pixPerBar;
        int extraAreaWidth = areaWidth - barsWidth;

        m_xTimeAxe = new ChartAxe(minTime, maxTime, barsWidth);
        m_xTimeAxe.m_offset = extraAreaWidth;
    }

    private ChartAxe calcYPriceAxe(int height, double lastPrice, double buyPrice, double sellPrice,
                                   List<OHLCTick> ohlcTicksClone, List<TresExchData.OrderPoint> ordersClone, List<BaseExecutor.TopDataPoint> topsClone) {
        double maxPrice = 0;
        double minPrice = Integer.MAX_VALUE;
        for (OHLCTick ohlcTick : ohlcTicksClone) {
            double high = ohlcTick.m_high;
            double low = ohlcTick.m_low;
            double highLowDiff = high - low;
            maxPrice = Math.max(maxPrice, high + highLowDiff * 0.1);
            minPrice = Math.min(minPrice, low - highLowDiff * 0.1);
        }
        for (TresExchData.OrderPoint order : ordersClone) {
            OrderData orderData = order.m_order;
            double buy = order.m_buy;
            double sell = order.m_sell;
            double buySellDiff = sell - buy;
            maxPrice = Math.max(maxPrice, sell + 0.1 * buySellDiff);
            minPrice = Math.min(minPrice, buy - 0.1 * buySellDiff);

            double price = orderData.m_price;
            maxPrice = Math.max(maxPrice, price);
            minPrice = Math.min(minPrice, price);
        }
        for (BaseExecutor.TopDataPoint paintTop : topsClone) {
            double buy = paintTop.m_bid;
            double sell = paintTop.m_ask;
            maxPrice = Math.max(maxPrice, sell);
            minPrice = Math.min(minPrice, buy);
        }

        if (maxPrice == 0) {
            maxPrice = lastPrice;
            minPrice = lastPrice;
        }
        if (sellPrice > 0) {
            maxPrice = Math.max(maxPrice, sellPrice);
        }
        if (buyPrice > 0) {
            minPrice = Math.min(minPrice, buyPrice);
        }
        double priceDiff = maxPrice - minPrice;
        if (priceDiff == 0) {
            maxPrice += 0.5;
            minPrice -= 0.5;
        }
        priceDiff = maxPrice - minPrice;
        maxPrice += priceDiff * 0.05;
        minPrice -= priceDiff * 0.05;
        ChartAxe yPriceAxe = new ChartAxe(minPrice, maxPrice, height - 2);
        yPriceAxe.m_offset = 1;
        return yPriceAxe;
    }

    private void paintTrades(Graphics g, LinkedList<TradeData> trades, ChartAxe yPriceAxe) {
        List<TradeData> tradesClone = cloneTrades(trades);
        g.setColor(Color.DARK_GRAY);
        for (TradeData tradeData : tradesClone) {
            long timestamp = tradeData.m_timestamp;
            int x = m_xTimeAxe.getPoint(timestamp);
            double price = tradeData.m_price;
            int y = yPriceAxe.getPointReverse(price);
            g.drawLine(x, y - 1, x, y + 1);
            g.drawLine(x - 1, y, x + 1, y);
        }
    }

    private List<TradeData> cloneTrades(LinkedList<TradeData> trades) {
        double minTime = m_xTimeAxe.m_min;
        double maxTime = m_xTimeAxe.m_max;
        m_paintTades.clear();
        synchronized (trades) {
            for (Iterator<TradeData> it = trades.descendingIterator(); it.hasNext(); ) {
                TradeData tradeData = it.next();
                long timestamp = tradeData.m_timestamp;
                if (timestamp < minTime) { break; }
                if (timestamp > maxTime) { continue; }
                m_paintTades.add(tradeData);
            }
        }
        return m_paintTades;
    }

    private void paintAvgOscs(Graphics g, LinkedList<ChartPoint> avgOscs) {
        List<ChartPoint> avgOscsClone = cloneAvgOscs(avgOscs);
        g.setColor(AVG_OSCS_COLOR);
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        for (ChartPoint avgOscPoint : avgOscsClone) {
            long timestamp = avgOscPoint.m_millis;
            int x = m_xTimeAxe.getPoint(timestamp);
            double avgOsc = avgOscPoint.m_value;
            int y = m_yAxe.getPointReverse(avgOsc);
            if(lastX != Integer.MAX_VALUE) {
                g.drawLine(x, y, lastX, lastY);
            }
            lastX = x;
            lastY = y;
        }
    }

    private List<ChartPoint> cloneAvgOscs(LinkedList<ChartPoint> avgOscs) {
        double minTime = m_xTimeAxe.m_min;
        double maxTime = m_xTimeAxe.m_max;
        m_paintAvgOscs.clear();
        synchronized (avgOscs) {
            for (Iterator<ChartPoint> it = avgOscs.descendingIterator(); it.hasNext(); ) {
                ChartPoint chartPoint = it.next();
                long timestamp = chartPoint.m_millis;
                if (timestamp < minTime) { break; }
                if (timestamp > maxTime) { continue; }
                m_paintAvgOscs.add(chartPoint);
            }
        }
        return m_paintAvgOscs;
    }

    private List<ChartPoint> cloneChartPoints(LinkedList<ChartPoint> points,
                                              Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
        double minTime = m_xTimeAxe.m_min;
        double maxTime = m_xTimeAxe.m_max;
        List<ChartPoint> ret = new ArrayList<ChartPoint>();
        synchronized (points) {
            for (Iterator<ChartPoint> it = points.descendingIterator(); it.hasNext(); ) {
                ChartPoint chartPoint = it.next();
                long timestamp = chartPoint.m_millis;
                if (timestamp < minTime) { break; }
                if (timestamp > maxTime) { continue; }
                ret.add(chartPoint);
                double val = chartPoint.m_value;
                minMaxCalculator.calculate(val);
            }
        }
        return ret;
    }

    private void paintAvgCci(Graphics g, LinkedList<ChartPoint> avgCcis) {
        ChartAxe yAxe = cloneAvgCcis(avgCcis);
        g.setColor(CCI_AVG_COLOR);
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        for (ChartPoint avgCciPoint : m_paintAvgCcis) {
            long timestamp = avgCciPoint.m_millis;
            int x = m_xTimeAxe.getPoint(timestamp);
            double avgCci = avgCciPoint.m_value;
            int y = yAxe.getPointReverse(avgCci);
            if (lastX != Integer.MAX_VALUE) {
                g.drawLine(x, y, lastX, lastY);
            }
            lastX = x;
            lastY = y;
        }
    }

    private ChartAxe cloneAvgCcis(LinkedList<ChartPoint> avgCcis) {
        Utils.DoubleDoubleMinMaxCalculator minMaxCalculator = new Utils.DoubleDoubleMinMaxCalculator();
        double minTime = m_xTimeAxe.m_min;
        double maxTime = m_xTimeAxe.m_max;
        m_paintAvgCcis.clear();
        synchronized (avgCcis) {
            for (Iterator<ChartPoint> it = avgCcis.descendingIterator(); it.hasNext(); ) {
                ChartPoint chartPoint = it.next();
                long timestamp = chartPoint.m_millis;
                if (timestamp < minTime) { break; }
                if (timestamp > maxTime) { continue; }
                m_paintAvgCcis.add(chartPoint);
                minMaxCalculator.calculate(chartPoint.m_value);
            }
        }
        Double valMin = minMaxCalculator.m_minValue;
        Double valMax = minMaxCalculator.m_maxValue;
        if (m_paintAvgCcis.isEmpty()) {
            valMin = 0d;
            valMax = 1d;
        }
        ChartAxe yAxe = new ChartAxe(valMin, valMax, getHeight() - 4);
        yAxe.m_offset = 2;
        return yAxe;
    }

    private void paintTops(Graphics g, List<BaseExecutor.TopDataPoint> topsClone, ChartAxe yPriceAxe) {
        for (BaseExecutor.TopDataPoint paintTop : topsClone) {
            long timestamp = paintTop.m_timestamp;
            int x = m_xTimeAxe.getPoint(timestamp);
            double buy = paintTop.m_bid;
            double sell = paintTop.m_ask;
            int yBuy = yPriceAxe.getPointReverse(buy);
            int ySell = yPriceAxe.getPointReverse(sell);
            g.setColor(BID_ASK_COLOR);
            g.drawLine(x, yBuy, x, ySell);

            double avgBuy = paintTop.m_avgBuy;
            double avgSell = paintTop.m_avgSell;
            int yAvgBuy = yPriceAxe.getPointReverse(avgBuy);
            g.setColor(Color.red);
            g.drawLine(x, yAvgBuy, x, yAvgBuy);
            int yAvgSell = yPriceAxe.getPointReverse(avgSell);
            g.setColor(Color.blue);
            g.drawLine(x, yAvgSell, x, yAvgSell);

            double diff = sell - buy;
            double avgDiff = avgSell - avgBuy;
            g.setColor((diff > avgDiff) ? Color.red : Color.blue);
            g.drawLine(x, 1, x, 2);
        }
    }

    private List<BaseExecutor.TopDataPoint> getTopsClone(LinkedList<BaseExecutor.TopDataPoint> tops) {
        double minTime = m_xTimeAxe.m_min;
        double maxTime = m_xTimeAxe.m_max;
        m_paintTops.clear();
        synchronized (tops) {
            for (Iterator<BaseExecutor.TopDataPoint> it = tops.descendingIterator(); it.hasNext(); ) {
                BaseExecutor.TopDataPoint topDataPoint = it.next();
                long timestamp = topDataPoint.m_timestamp;
                if (timestamp < minTime) { break; }
                if (timestamp > maxTime) { continue; }
                m_paintTops.add(topDataPoint);
            }
        }
        return m_paintTops;
    }


    private void paintOrders(Graphics g, TresExchData exchData, ChartAxe yPriceAxe, List<TresExchData.OrderPoint> ordersClone) {
        TresExecutor executor = exchData.m_executor;
        int fontHeight = g.getFont().getSize();
        for (TresExchData.OrderPoint orderPoint : ordersClone) {
            OrderData order = orderPoint.m_order;
            long mPlaceTime = order.m_placeTime;
            int x = m_xTimeAxe.getPoint(mPlaceTime);

            double buy = orderPoint.m_buy;
            double sell = orderPoint.m_sell;
            BaseExecutor.TopSource topSource = orderPoint.m_topSource;
            Color color = topSource.color();
            g.setColor(color);
            int yBuy = yPriceAxe.getPointReverse(buy);
            int ySell = yPriceAxe.getPointReverse(sell);
            g.drawLine(x, yBuy, x, ySell);
            g.drawLine(x-1, yBuy, x+2, yBuy);
            g.drawLine(x-1, ySell, x+2, ySell);

            double price = order.m_price;
            OrderSide side = order.m_side;
            boolean isBuy = side.isBuy();

            OrderStatus status = order.m_status;
            Color borderColor = orderBorderColor(order, isBuy, status);
            Color fillColor = orderFillColor(isBuy, status);

            int y = yPriceAxe.getPointReverse(price);
            Polygon p = new Polygon();
            p.addPoint(x, isBuy ? y - 10 : y + 10);
            p.addPoint(x + 5, y);
            p.addPoint(x - 5, y);

            if (fillColor != null) {
                g.setColor(fillColor);
                g.fillPolygon(p);
            }
            if (borderColor != null) {
                g.setColor(borderColor);
                g.drawPolygon(p);
            }
            g.setColor(order.m_side.isBuy() ? Color.BLUE : Color.RED);
            String amount = ((order.m_filled == 0) || (order.m_filled == order.m_amount))
                    ? Utils.X_YYY.format(order.m_amount)
                    : Utils.X_YYY.format(order.m_filled) + "/" + Utils.X_YYY.format(order.m_amount);
            g.drawString(amount, x, isBuy ? y + 2 + fontHeight : y - 5);

            long tickAge = orderPoint.m_tickAge;
            String tickAgeStr = Utils.millisToDHMSStr(tickAge);
            g.drawString(tickAgeStr, x, isBuy ? y + 2 + fontHeight * 2 : y - 5 - fontHeight);

            String ordId = order.m_orderId.substring(order.m_orderId.length() - 5);
            g.drawString(ordId, x, isBuy ? y + 2 + fontHeight * 3 : y - 5 - fontHeight * 2);
        }

        paintTimeFramePoints(g, executor);

        g.setColor(Color.YELLOW);
        int height = getHeight();
        g.drawString(executor.valuate(), 2, height - fontHeight);
        g.drawString("acct: " + executor.m_account, 2, height - fontHeight * 2);
        g.drawString("dir.adj=" + exchData.getDirectionAdjusted(), 2, height - fontHeight * 3);
        g.drawString("placed=" + executor.m_ordersPlaced + "; filled=" + executor.m_ordersFilled + "; volume=" + executor.m_tradeVolume, 2, height - fontHeight * 4);
        g.drawString("wait=" + executor.dumpWaitTime(), 2, height - fontHeight * 5);
        g.drawString("takes:" + executor.dumpTakesTime(), 2, height - fontHeight * 6);
        g.drawString("avgTickAge: " + executor.m_tickAgeCalc.getAverage(), 2, height - fontHeight * 7);

        OrderData order = executor.m_order;
        if (order != null) {
            g.setColor(order.m_side.isBuy() ? Color.BLUE : Color.RED);
            g.drawString(order.toString(), 2, height - fontHeight * 8);
        }
    }

    private List<TresExchData.OrderPoint> getOrderClone(TresExchData exchData) {
        LinkedList<TresExchData.OrderPoint> orders = exchData.m_orders;
        double min = m_xTimeAxe.m_min;
        double max = m_xTimeAxe.m_max;
        m_paintOrders.clear();
        synchronized (orders) { // avoid ConcurrentModificationException - use local copy
            for (Iterator<TresExchData.OrderPoint> iterator = orders.descendingIterator(); iterator.hasNext(); ) {
                TresExchData.OrderPoint orderPoint = iterator.next();
                OrderData order = orderPoint.m_order;
                long placeTime = order.m_placeTime;
                if(placeTime > max) {
                    continue;
                }
                if(placeTime < min) {
                    break;
                }
                m_paintOrders.add(orderPoint);
            }
        }
        return m_paintOrders;
    }

    private List<OHLCTick> cloneOhlcTicks(LinkedList<OHLCTick> ohlcTicks) {
        double min = m_xTimeAxe.m_min;
        double max = m_xTimeAxe.m_max;
        m_paintOhlcTicks.clear();
        synchronized (ohlcTicks) { // avoid ConcurrentModificationException - use local copy
            for (Iterator<OHLCTick> iterator = ohlcTicks.descendingIterator(); iterator.hasNext(); ) {
                OHLCTick ohlcTick = iterator.next();
                long start = ohlcTick.m_barStart;
                if(start > max) {
                    continue;
                }
                long end = ohlcTick.m_barEnd;
                if(end < min) {
                    break;
                }
                m_paintOhlcTicks.add(ohlcTick);
            }
        }
        return m_paintOhlcTicks;
    }

    private Color orderFillColor(boolean isBuy, OrderStatus status) {
        Color fillColor = null;
        if (status == OrderStatus.PARTIALLY_FILLED) {
            fillColor = Colors.setAlpha(isBuy ? Color.BLUE : Color.RED, 100);
        } else if (status == OrderStatus.FILLED) {
            fillColor = isBuy ? Color.BLUE : Color.RED;
        } else if (status == OrderStatus.CANCELLED) {
            fillColor = Color.gray;
        } else if ((status == OrderStatus.REJECTED) || (status == OrderStatus.ERROR)) {
            fillColor = Color.orange;
        }
        return fillColor;
    }

    private Color orderBorderColor(OrderData order, boolean isBuy, OrderStatus status) {
        Color borderColor = null;
        if (status == OrderStatus.NEW) {
            borderColor = Color.green;
        } else if ((status == OrderStatus.SUBMITTED) || (status == OrderStatus.PARTIALLY_FILLED)) {
            borderColor = isBuy ? Color.BLUE : Color.RED;
        } else if (status == OrderStatus.CANCELLED) {
            if (order.m_filled > 0) {
                borderColor = isBuy ? Color.BLUE : Color.RED;
            }
        }
        return borderColor;
    }

    private void paintTimeFramePoints(Graphics g, TresExecutor executor) {
        LinkedList<BaseExecutor.TimeFramePoint> timeFramePoints = executor.m_timeFramePoints;
        LinkedList<BaseExecutor.TimeFramePoint> fetcherPointsInt;
        synchronized (timeFramePoints) { // avoid ConcurrentModificationException - use local copy
            fetcherPointsInt = new LinkedList<BaseExecutor.TimeFramePoint>(timeFramePoints);
        }
        int yBase = getHeight() - 9;
        int width = getWidth();
        for (Iterator<BaseExecutor.TimeFramePoint> iterator = fetcherPointsInt.descendingIterator(); iterator.hasNext(); ) {
            BaseExecutor.TimeFramePoint fetcherPoint = iterator.next();
            long start = fetcherPoint.m_start;
            long end = fetcherPoint.m_end;
            int startX = m_xTimeAxe.getPoint(start);
            if (startX > width) {
                continue;
            }
            int endX = m_xTimeAxe.getPoint(end);
            if (endX < 0) {
                break;
            }
            BaseExecutor.TimeFrameType type = fetcherPoint.m_type;
            Color color = type.color();
            g.setColor(color);

            int level = type.level();
            int y = yBase + level * 3;
            Graphics2D g2 = (Graphics2D) g;
            g2.setStroke(new BasicStroke(3));
            g.drawLine(startX, y, endX, y);
            g2.setStroke(new BasicStroke(1));
            g.setColor(Color.GRAY);
            g.drawLine(startX, y, startX, y);
        }
    }

    private void paintMaTicks(Graphics g, TresMaCalculator maCalculator, ChartAxe yPriceAxe) {
        LinkedList<TresMaCalculator.MaTick> maTicks = new LinkedList<TresMaCalculator.MaTick>();
        LinkedList<TresMaCalculator.MaTick> ticks = maCalculator.m_maTicks;
        synchronized (ticks) { // avoid ConcurrentModificationException - use local copy
            maTicks.addAll(ticks);
        }
        int firstX = Integer.MAX_VALUE;
        int firstY = Integer.MAX_VALUE;
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        Color nextColor = null;
        for (Iterator<TresMaCalculator.MaTick> iterator = maTicks.descendingIterator(); iterator.hasNext(); ) {
            TresMaCalculator.MaTick maTick = iterator.next();
            double ma = maTick.m_ma;
            long barEnd = maTick.m_barEnd;
            int x = m_xTimeAxe.getPoint(barEnd);
            int y = yPriceAxe.getPointReverse(ma);
            if (firstX == Integer.MAX_VALUE) {
                firstX = x;
                firstY = y;
            }
            Color color = maTick.m_maCrossed ? Color.ORANGE : Color.WHITE;
            g.setColor(nextColor == null ? color : nextColor);
            if (lastX == Integer.MAX_VALUE) {
                g.fillRect(x - 1, y - 1, 3, 3);
            } else {
                g.drawLine(x, y, lastX, lastY);
            }
            nextColor = color;
            lastX = x;
            lastY = y;
            if (x < 0) {
                break;
            }
        }

        if ((firstX != Integer.MAX_VALUE) && (firstY != Integer.MAX_VALUE)) {
            double directionAdjusted = maCalculator.m_phaseData.m_exchData.getDirectionAdjusted();
            double degrees = directionAdjusted * 90;
            double radians = degrees * Math.PI / 180;
            double dx = DIRECTION_ARROW_SIZE * Math.cos(radians);
            double dy = -DIRECTION_ARROW_SIZE * Math.sin(radians);
            Color color = (directionAdjusted > 0) ? Color.BLUE : (directionAdjusted < 0) ? Color.RED : Color.GRAY;
            g.setColor(color);
            Graphics2D g2 = (Graphics2D) g;
            g2.setStroke(new BasicStroke(3));
            g.drawLine(firstX, firstY, (int) (firstX + dx), (int) (firstY + dy));
            g2.setStroke(new BasicStroke(1));
        }

        int canvasWidth = getWidth();
        int canvasHeight = getHeight();
        Boolean lastOscUp = null;
        Double lastPrice = null;
        double totalPriceRatio = 1;
        int tradeNum = 0;
        int fontHeight = g.getFont().getSize();

        LinkedList<TresMaCalculator.MaCrossData> maCd = maCalculator.m_maCrossDatas;
        LinkedList<TresMaCalculator.MaCrossData> maCrossDatas = new LinkedList<TresMaCalculator.MaCrossData>();
        synchronized (maCd) { // avoid ConcurrentModificationException - use local copy
            maCrossDatas.addAll(maCd);
        }
        for (TresMaCalculator.MaCrossData maCrossData : maCrossDatas) {
            Long timestamp = maCrossData.m_timestamp;
            int x = m_xTimeAxe.getPoint(timestamp);
            boolean oscUp = maCrossData.m_oscUp;
            double price = maCrossData.m_price;
            int y = yPriceAxe.getPointReverse(price);
            if (lastOscUp != null) {
                if (lastOscUp != oscUp) {
                    if (m_paintSym && (x > 0)) {
                        int dy = y - lastY;
                        Color color = (lastOscUp && (dy < 0)) || (!lastOscUp && (dy > 0)) ? Color.GREEN : Color.RED;
                        g.setColor(color);
                        g.drawLine(lastX, lastY, x, y);
                    }
                    if (lastPrice != null) {
                        double priceRatio = price / lastPrice;
                        if (!lastOscUp) {
                            priceRatio = 1 / priceRatio;
                        }
                        totalPriceRatio *= priceRatio;
                        if (m_paintSym && ((x > 0) && (x < canvasWidth))) {
                            g.drawString(String.format("r: %1$,.5f", priceRatio), x, y + fontHeight);
                            Color color = (totalPriceRatio > 1) ? Color.GREEN : Color.RED;
                            g.setColor(color);
                            g.drawString(String.format("t: %1$,.5f", totalPriceRatio), x, y + fontHeight * 2);

                            g.setColor(TR_COLOR);
                            g.drawLine(x, 0, x, canvasHeight);
                        }
                        tradeNum++;
                    }
                    lastOscUp = oscUp;
                    lastY = y;
                    lastX = x;
                    lastPrice = price;
                }
            } else { // first
                lastOscUp = oscUp;
                lastY = y;
                lastX = x;
                lastPrice = price;
            }
            if (m_paintSym && (x > 0)) {
                g.setColor(oscUp ? Color.GREEN : Color.RED);
                g.drawLine(x, y, x + 5, y + (oscUp ? -5 : 5));
            }
        }

        g.setColor(Color.ORANGE);
        TresExchData exchData = maCalculator.m_phaseData.m_exchData;
        long runningTimeMillis = exchData.m_lastTickMillis - exchData.m_startTickMillis;
        String runningStr = Utils.millisToDHMSStr(runningTimeMillis);
        g.drawString("running: " + runningStr, 5, fontHeight * 5 + 5);

        g.drawString(String.format("ratio: %.5f", totalPriceRatio), 5, fontHeight * 6 + 5);

        double runningTimeDays = ((double) runningTimeMillis) / Utils.ONE_DAY_IN_MILLIS;
        double aDay = Math.pow(totalPriceRatio, 1 / runningTimeDays);
        g.drawString(String.format("projected aDay: %.5f", aDay), 5, fontHeight * 7 + 5);

        g.drawString(String.format("trade num: %d", tradeNum), 5, fontHeight * 8 + 5);
        long tradeFreq = (tradeNum == 0) ? 0 : (runningTimeMillis / tradeNum);
        g.drawString(String.format("trade every " + Utils.millisToDHMSStr(tradeFreq)), 5, fontHeight * 9 + 5);
    }

    private void paintOHLCTicks(Graphics g, List<OHLCTick> ohlcTicksClone, ChartAxe yPriceAxe) {
        g.setColor(Colors.BROWN);
        for (OHLCTick ohlcTick : ohlcTicksClone) {
            long barStart = ohlcTick.m_barStart;
            int startX = m_xTimeAxe.getPoint(barStart);
            long barEnd = ohlcTick.m_barEnd;
            int endX = m_xTimeAxe.getPoint(barEnd);
            int midX = (startX + endX) / 2;
            int halfWidth = (endX - startX) / 2;

            int highY = yPriceAxe.getPointReverse(ohlcTick.m_high);
            int lowY = yPriceAxe.getPointReverse(ohlcTick.m_low);
            int openY = yPriceAxe.getPointReverse(ohlcTick.m_open);
            int closeY = yPriceAxe.getPointReverse(ohlcTick.m_close);
            int weight;
            if (endX - startX > 15) {
                weight = 5;
            } else if (endX - startX > 9) {
                weight = 3;
            } else {
                weight = 1;
            }

            g.fillRect(midX - weight / 2, highY, weight, lowY - highY);

            // ohlc style
            g.fillRect(startX, openY - weight / 2, halfWidth, weight);
            g.fillRect(endX - halfWidth, closeY - weight / 2, halfWidth, weight);
        }
    }

    private void paintOscTicks(Graphics g, LinkedList<OscTick> oscBars) {
        int canvasWidth = getWidth();
        int lastX = Integer.MAX_VALUE;
        int[] lastYs = new int[3];
        // todo: clone first
        for (Iterator<OscTick> iterator = oscBars.descendingIterator(); iterator.hasNext(); ) {
            OscTick tick = iterator.next();
            int endX = paintOsc(g, tick, lastX, lastYs, canvasWidth);
            if (endX < 0) {
                break;
            }
            lastX = endX;
        }
    }

    private void paintCoppockTicks(Graphics g, List<ChartPoint> ticksClone, ChartAxe yAxe, Color color) {
        g.setColor(color);
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        for (ChartPoint tick : ticksClone) {
            long endTime = tick.m_millis;
            int x = m_xTimeAxe.getPoint(endTime);
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

    private void paintCoppockPeaks(Graphics g, List<ChartPoint> peaksClone, ChartAxe yAxe, Color color, boolean big) {
        int size = big ? 6 : 3;
        g.setColor(color);
        for (ChartPoint peakClone : peaksClone) {
            long endTime = peakClone.m_millis;
            int x = m_xTimeAxe.getPoint(endTime);
            double coppock = peakClone.m_value;
            int y = yAxe.getPointReverse(coppock);
            g.drawLine(x - size, y - size, x + size, y + size);
            g.drawLine(x + size, y - size, x - size, y + size);
        }
    }

    private void paintCciTicks(Graphics g, LinkedList<ChartPoint> ticks) {
        double max = m_xTimeAxe.m_max;
        double min = m_xTimeAxe.m_min;
        // todo: clone first
        double valMin = -0.1;
        double valMax = 0.1;
        m_paintCciTicks.clear();
        for (Iterator<ChartPoint> iterator = ticks.descendingIterator(); iterator.hasNext(); ) {
            ChartPoint tick = iterator.next();
            long endTime = tick.m_millis;
            long startTime = endTime - m_tres.m_barSizeMillis;
            if (startTime > max) { continue; }
            if (endTime < min) { break; }
            m_paintCciTicks.add(tick);
            double val = tick.m_value;
            valMax = Math.max(valMax, val);
            valMin = Math.min(valMin, val);
        }

        ChartAxe yAxe = new ChartAxe(valMin, valMax, getHeight() - 4);
        yAxe.m_offset = 2;

        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;

        g.setColor(CCI_COLOR);
        for (ChartPoint tick : m_paintCciTicks) {
            long endTime = tick.m_millis;
            int x = m_xTimeAxe.getPoint(endTime);
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

    private void paintOscPeaks(Graphics g, LinkedList<OscTick> oscPeaks) {
        g.setColor(OSC_PEAKS_COLOR);

        double min = m_xTimeAxe.m_min;
        double max = m_xTimeAxe.m_max;
        m_paintOscPeaks.clear();
        synchronized (oscPeaks) {  // avoid ConcurrentModificationException - use local copy
            for (Iterator<OscTick> iterator = oscPeaks.descendingIterator(); iterator.hasNext(); ) {
                OscTick tick = iterator.next();
                long startTime = tick.m_startTime;
                if(startTime > max) {
                    continue;
                }
                long endTime = startTime + m_tres.m_barSizeMillis;
                if(endTime < min) {
                    break;
                }
                m_paintOscPeaks.add(tick);
            }
        }

        for (OscTick oscPeak : m_paintOscPeaks) {
            long startTime = oscPeak.m_startTime;
            long endTime = startTime + m_tres.m_barSizeMillis;
            int x = m_xTimeAxe.getPoint(endTime);
            double valMid = oscPeak.getMid();
            int y = m_yAxe.getPointReverse(valMid);
            g.drawLine(x - 5, y - 5, x + 5, y + 5);
            g.drawLine(x + 5, y - 5, x - 5, y + 5);
        }
    }

    private void paintAvgOscPeaks(Graphics g, LinkedList<ChartPoint> avgOscsPeaks) {
        double min = m_xTimeAxe.m_min;
        double max = m_xTimeAxe.m_max;
        m_paintOscPeaks.clear();
        synchronized (m_paintAvgOscPeaks) {  // avoid ConcurrentModificationException - use local copy
            for (Iterator<ChartPoint> iterator = avgOscsPeaks.descendingIterator(); iterator.hasNext(); ) {
                ChartPoint avgOscsPeak = iterator.next();
                long time = avgOscsPeak.m_millis;
                if(time > max) {
                    continue;
                }
                if(time < min) {
                    break;
                }
                m_paintAvgOscPeaks.add(avgOscsPeak);
            }
        }
        g.setColor(AVG_OSCS_COLOR);
        for (ChartPoint avgOscPeak : m_paintAvgOscPeaks) {
            long time = avgOscPeak.m_millis;
            int x = m_xTimeAxe.getPoint(time);
            double avgOsc = avgOscPeak.m_value;
            int y = m_yAxe.getPointReverse(avgOsc);
            g.drawLine(x - 4, y - 4, x + 4, y + 4);
            g.drawLine(x + 4, y - 4, x - 4, y + 4);
        }
    }


    private void paintLine(Graphics g, double level) {
        int y = m_yAxe.getPointReverse(level);
        g.drawLine(0, y, getWidth(), y);
    }

    private int paintOsc(Graphics g, OscTick tick, int lastX, int[] lastYs, int canvasWidth) {
        long startTime = tick.m_startTime;
        long endTime = startTime + m_tres.m_barSizeMillis;
        int x = m_xTimeAxe.getPoint(endTime);

        double val1 = tick.m_val1;
        double val2 = tick.m_val2;
        double valMid = tick.getMid();
        int y1 = m_yAxe.getPointReverse(val1);
        int y2 = m_yAxe.getPointReverse(val2);
        int yMid = m_yAxe.getPointReverse(valMid);

        if (lastX < canvasWidth) {
            if (lastX == Integer.MAX_VALUE) {
                g.setColor(Color.RED);
                g.drawRect(x - 2, y1 - 2, 5, 5);
                g.setColor(Color.BLUE);
                g.drawRect(x - 2, y2 - 2, 5, 5);
            } else {
                g.setColor(OSC_MID_LINE_COLOR);
                g.drawLine(lastX, lastYs[2], x, yMid);
                g.setColor(OSC_1_LINE_COLOR);
                g.drawLine(lastX, lastYs[0], x, y1);
                g.setColor(OSC_2_LINE_COLOR);
                g.drawLine(lastX, lastYs[1], x, y2);
            }
        }
        lastYs[0] = y1;
        lastYs[1] = y2;
        lastYs[2] = yMid;
        return x;
    }

    private void paintFineOsc(Graphics g, OscTick fineOscTick, int offset) {
        if (fineOscTick != null) {
            double val1 = fineOscTick.m_val1;
            paintFineOscPoint(g, Color.RED, offset, val1);
            double val2 = fineOscTick.m_val2;
            paintFineOscPoint(g, Color.BLUE, offset, val2);
        }
    }

    private void paintFineOscPoint(Graphics g, Color color, int offset, double val) {
        int y = m_yAxe.getPointReverse(val);
        g.setColor(color);
        g.drawRect(offset - 2, y - 2, 5, 5);
    }

    private void paintBlendedOsc(Graphics g, OscTick lastBarTick, OscTick blendedTick, int x2) {
        if ((lastBarTick != null) && (blendedTick != null)) {
            long lastBarStartTime = lastBarTick.m_startTime;
            long lastBarEndTime = lastBarStartTime + m_tres.m_barSizeMillis;
            int x1 = m_xTimeAxe.getPoint(lastBarEndTime);

            double lastBarOscVal1 = lastBarTick.m_val1;
            double lastBarOscVal2 = lastBarTick.m_val2;
            int lastBarOscY1 = m_yAxe.getPointReverse(lastBarOscVal1);
            int lastBarOscY2 = m_yAxe.getPointReverse(lastBarOscVal2);

            double blendedTickVal1 = blendedTick.m_val1;
            double blendedTickVal2 = blendedTick.m_val2;
            int blendedTickY1 = m_yAxe.getPointReverse(blendedTickVal1);
            int blendedTickY2 = m_yAxe.getPointReverse(blendedTickVal2);

            g.setColor(Color.GRAY);
            g.drawLine(x1, lastBarOscY1, x2, blendedTickY1);
            g.drawLine(x1, lastBarOscY2, x2, blendedTickY2);
        }
    }
}
