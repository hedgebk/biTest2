package bthdg.tres;

import bthdg.ChartAxe;
import bthdg.Log;
import bthdg.calc.OHLCTick;
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
    public static final Color OSC_1_LINE_COLOR = Colors.setAlpha(Color.RED, 25);
    public static final Color OSC_2_LINE_COLOR = Colors.setAlpha(Color.BLUE, 25);
    public static final Color OSC_MID_LINE_COLOR = new Color(30, 30, 30, 128);
    public static final int DIRECTION_ARROW_SIZE = 20;
    public static final Color BID_ASK_COLOR = Colors.setAlpha(Color.darkGray, 90);
    public static final Color AVG_OSCS_COLOR = Color.pink;
    public static final Color OSC_PEAKS_COLOR = Colors.setAlpha(Colors.LIGHT_CYAN, 127);
    public static final Color BAR_HIGHLIGHT_COLOR = new Color(32, 32, 32);
    public static final Color COPPOCK_AVG_COLOR = Color.CYAN;
    public static final Color COPPOCK_COLOR = Colors.setAlpha(COPPOCK_AVG_COLOR, 25);
    public static final Color COPPOCK_AVG_PEAKS_COLOR = Color.WHITE;
    public static final Color COPPOCK_PEAKS_COLOR = Colors.setAlpha(COPPOCK_AVG_PEAKS_COLOR, 60);
    public static final Color CCI_COLOR = Colors.setAlpha(Colors.LIGHT_ORANGE, 25);
    public static final Color CCI_AVG_COLOR = new Color(230, 100, 43);
    public static final double[] STEPS = new double[]{0.1, 0.2, 0.5};

    protected static boolean m_paintOrders = true;
    protected static boolean m_paintOrderData = true;
    protected static boolean m_paintOrderIds = false;

    private Tres m_tres;
    private Point m_point;
    private ChartAxe m_yAxe;
    private ChartAxe m_xTimeAxe;
    private double m_zoom = 1;
    private Integer m_dragStartX;
    private Integer m_dragDeltaX;
    private int m_dragDeltaBars;
    private int m_barsShift = 0;
    private List<TradeDataLight> m_paintTades = new ArrayList<TradeDataLight>();
    private List<BaseExecutor.TopDataPoint> m_paintTops = new ArrayList<BaseExecutor.TopDataPoint>();
    private List<TresExchData.OrderPoint> m_ordersToPaint = new ArrayList<TresExchData.OrderPoint>();
    private List<OHLCTick> m_paintOhlcTicks = new ArrayList<OHLCTick>();

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

        if (Tres.PAINT_TICK_TIMES_ONLY) {
            paintTimeTicks(g, width, height);
        } else {
            paintMainChart(g, width, height);
        }
    }

    private void paintMainChart(Graphics g, int width, int height) {
        ArrayList<TresExchData> exchDatas = m_tres.m_exchDatas;
        TresExchData exchData = exchDatas.get(0);
        PhaseData[] phaseDatas = exchData.m_phaseDatas;
        PhaseData phaseData = phaseDatas[0];

        long barSize = m_tres.m_barSizeMillis;
        if (m_xTimeAxe == null) { // guess for very first pass
            calcXTimeAxe(width, barSize, phaseData);
        }

        double lastPrice = exchData.m_lastPrice;
        TresExecutor executor = exchData.m_executor;
        double buyPrice = executor.m_buy;
        double sellPrice = executor.m_sell;
        List<OHLCTick> ohlcTicksClone = cloneOhlcTicks(phaseData.m_ohlcCalculator.m_ohlcTicks);
        List<TresExchData.OrderPoint> ordersClone = getOrderClone(exchData);
        List<BaseExecutor.TopDataPoint> topsClone = getTopsClone(executor.m_tops);
        ChartAxe yPriceAxe = calcYPriceAxe(height, lastPrice, buyPrice, sellPrice, ohlcTicksClone, ordersClone, topsClone);

        ChartAxe yValueAxe = new ChartAxe(0, 0, height - 2);
        yPriceAxe.m_offset = 1;

        int yAxesWidth = paintYAxes(g, height, yPriceAxe, yValueAxe, exchData);
        int chartAreaRight = width - yAxesWidth;
        calcXTimeAxe(chartAreaRight, barSize, phaseData);

        if (m_point != null) {
            paintBarHighlight(g, barSize);
        }
        paintLeftRightBorders(g, height);

        if (lastPrice != 0) {
            paintTopLeftStrings(g, exchData, lastPrice, buyPrice, sellPrice);

            paintOHLCTicks(g, ohlcTicksClone, yPriceAxe);
            paintTops(g, topsClone, yPriceAxe);
            paintTrades(g, exchData.m_trades, yPriceAxe);

            paintAlgos(g, exchData, yPriceAxe);

//            paintMaTicks(g, phaseData.m_maCalculator, yPriceAxe);
            paintOrders(g, exchData, yPriceAxe, ordersClone);

            paintBuyPrice(g, width, buyPrice, yPriceAxe);
            paintSellPrice(g, width, sellPrice, yPriceAxe);
            paintLastPrice(g, width, lastPrice, yPriceAxe);
            paintDirectionArrow(g, executor, chartAreaRight);
        }

        if (m_point != null) {
            paintCross(g, width, height);
        }
    }

    private void paintLeftRightBorders(Graphics g, int height) {
        // paint min/max time : left/right borders
        int minTimeX = m_xTimeAxe.getPoint(m_xTimeAxe.m_min);
        int maxTimeX = m_xTimeAxe.getPoint(m_xTimeAxe.m_max);
        g.setColor(Color.BLUE);
        g.drawLine(minTimeX, 0, minTimeX, height);
        g.drawLine(maxTimeX, 0, maxTimeX, height);
    }

    private void paintTopLeftStrings(Graphics g, TresExchData exchData, double lastPrice, double buyPrice, double sellPrice) {
        Exchange exchange = exchData.m_ws.exchange();
        String lastStr = exchange.roundPriceStr(lastPrice, Tres.PAIR);
        String buyStr = exchange.roundPriceStr(buyPrice, Tres.PAIR);
        String sellStr = exchange.roundPriceStr(sellPrice, Tres.PAIR);
        int fontHeight = g.getFont().getSize();
        g.setColor(Color.BLUE);
        g.drawString("Last: " + lastStr + "; buy: " + buyStr + "; sell: " + sellStr, 5, fontHeight + 5);
        g.setColor(Color.GRAY);
        g.drawString(String.format("Zoom: %1$,.4f", m_zoom) + "; fontHeight=" + fontHeight, 5, fontHeight * 2 + 5);
        g.drawString(getBarsShiftStr(), 5, fontHeight * 3 + 5);
    }

    private int paintYAxes(Graphics g, int height, ChartAxe yPriceAxe, ChartAxe yValueAxe, TresExchData exchData) {
        int yAxesWidth = paintYPriceAxe(g, yPriceAxe);
        int width = getWidth();
        for (TresAlgoWatcher algoWatcher : exchData.m_playAlgos) {
            int axeWidth = algoWatcher.paintYAxe(g, m_xTimeAxe, width - yAxesWidth, yPriceAxe, yValueAxe);
            yAxesWidth += axeWidth;
        }

        if ((yValueAxe.m_max != 0) || (yValueAxe.m_min != 0)) {
            Color color = Color.WHITE;
            int valueAxeWidth = yValueAxe.paintYAxe(g, width - yAxesWidth, color);
            yAxesWidth += valueAxeWidth;
        }

        return yAxesWidth;
    }

    private void paintAlgos(Graphics g, TresExchData exchData, ChartAxe yPriceAxe) {
        for (TresAlgoWatcher algoWatcher : exchData.m_playAlgos) {
            algoWatcher.paint(g, m_xTimeAxe, yPriceAxe, m_point);
        }
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

    private int paintYPriceAxe(Graphics g, ChartAxe yPriceAxe) {
        g.setColor(Color.ORANGE);

        int fontHeight = g.getFont().getSize();
        int halfFontHeight = fontHeight / 2;
        double min = yPriceAxe.m_min;
        double max = yPriceAxe.m_max;

        int step = 1;
        int minLabel = (int) Math.floor(min / step);
        int maxLabel = (int) Math.ceil(max / step);

        int axeWidth = measureYPriceAxeWidth(g, step, minLabel, maxLabel);
        int width = getWidth();
        int x = width - axeWidth;

        int priceY0 = yPriceAxe.getPointReverse(minLabel);
        int priceY1 = yPriceAxe.getPointReverse(minLabel + step);
        int stepHeight = Math.abs(priceY1 - priceY0);

        for (int y = minLabel; y <= maxLabel; y += step) {
            int priceY = yPriceAxe.getPointReverse(y);
            g.drawString(Integer.toString(y), x, priceY + halfFontHeight);
            g.drawLine(x - 2, priceY, x - PRICE_AXE_MARKER_WIDTH, priceY);

            for (double semiStep : STEPS) {
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
        int yPriceAxeWidth = axeWidth + PRICE_AXE_MARKER_WIDTH + 5;
        return yPriceAxeWidth;
    }

    private int measureYPriceAxeWidth(Graphics g, int step, int minLabel, double maxLabel) {
        FontMetrics fontMetrics = g.getFontMetrics();
        int maxWidth = 10;
        for (int y = minLabel; y <= maxLabel; y += step) {
            Rectangle2D bounds = fontMetrics.getStringBounds(Integer.toString(y), g);
            int stringWidth = (int) bounds.getWidth();
            maxWidth = Math.max(maxWidth, stringWidth);
        }
        return maxWidth;
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

    private void calcXTimeAxe(int areaWidth, long barSize, PhaseData phaseData) {
        LinkedList<OHLCTick> ohlcTicks = phaseData.m_ohlcCalculator.m_ohlcTicks;
        long maxTime = 0;
        OHLCTick lastOhlcTick;
        synchronized (ohlcTicks) {
            lastOhlcTick = ohlcTicks.peekLast();
        }
        if (lastOhlcTick != null) {
            maxTime = lastOhlcTick.m_barEnd;
        }
        if (maxTime == 0) {
            maxTime = System.currentTimeMillis();
        }
        maxTime -= (m_barsShift + m_dragDeltaBars) * barSize;

        int pixPerBar = pixPerBar();
        int maxBarNum = areaWidth / pixPerBar + 1;
        long minTime = maxTime - barSize * maxBarNum;
        int barsWidth = maxBarNum * pixPerBar;
        int extraAreaWidth = areaWidth - barsWidth;

        m_xTimeAxe = new ChartAxe(minTime, maxTime, barsWidth);
        m_xTimeAxe.m_offset = extraAreaWidth;
    }

    private ChartAxe calcYPriceAxe(int height, double lastPrice, double buyPrice, double sellPrice,
                                   List<OHLCTick> ohlcTicksClone, List<TresExchData.OrderPoint> ordersClone,
                                   List<BaseExecutor.TopDataPoint> topsClone) {
        double maxPrice = 0;
        double minPrice = Integer.MAX_VALUE;
        for (OHLCTick ohlcTick : ohlcTicksClone) {
            double high = ohlcTick.m_high;
            double low = ohlcTick.m_low;
            maxPrice = Math.max(maxPrice, high);
            minPrice = Math.min(minPrice, low);
        }
        for (TresExchData.OrderPoint order : ordersClone) {
            OrderData orderData = order.m_order;
            double buy = order.m_buy;
            double sell = order.m_sell;
            maxPrice = Math.max(maxPrice, sell);
            minPrice = Math.min(minPrice, buy);

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
            if (sellPrice > 0) {
                maxPrice = Math.max(maxPrice, sellPrice);
            }
            if (buyPrice > 0) {
                minPrice = Math.min(minPrice, buyPrice);
            }
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

    private void paintTrades(Graphics g, LinkedList<TradeDataLight> trades, ChartAxe yPriceAxe) {
        List<TradeDataLight> tradesClone = cloneTrades(trades);
        g.setColor(Color.DARK_GRAY);
        for (TradeDataLight tradeData : tradesClone) {
            long timestamp = tradeData.m_timestamp;
            int x = m_xTimeAxe.getPoint(timestamp);
            double price = tradeData.m_price;
            int y = yPriceAxe.getPointReverse(price);
            g.drawLine(x, y - 1, x, y + 1);
            g.drawLine(x - 1, y, x + 1, y);
        }
    }

    private List<TradeDataLight> cloneTrades(LinkedList<TradeDataLight> trades) {
        double minTime = m_xTimeAxe.m_min;
        double maxTime = m_xTimeAxe.m_max;
        m_paintTades.clear();
        synchronized (trades) {
            for (Iterator<TradeDataLight> it = trades.descendingIterator(); it.hasNext(); ) {
                TradeDataLight tradeData = it.next();
                long timestamp = tradeData.m_timestamp;
                if (timestamp > maxTime) { continue; }
                if (timestamp < minTime) { break; }
                m_paintTades.add(tradeData);
            }
        }
        return m_paintTades;
    }

//    private List<ChartPoint> cloneChartPoints(LinkedList<ChartPoint> points,
//                                              Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
//        double minTime = m_xTimeAxe.m_min;
//        double maxTime = m_xTimeAxe.m_max;
//        List<ChartPoint> ret = new ArrayList<ChartPoint>();
//        synchronized (points) {
//            for (Iterator<ChartPoint> it = points.descendingIterator(); it.hasNext(); ) {
//                ChartPoint chartPoint = it.next();
//                long timestamp = chartPoint.m_millis;
//                if (timestamp < minTime) { break; }
//                if (timestamp > maxTime) { continue; }
//                ret.add(chartPoint);
//                double val = chartPoint.m_value;
//                minMaxCalculator.calculate(val);
//            }
//        }
//        return ret;
//    }

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
            double avgBuySellMid = (avgBuy + avgSell) / 2;
            int yAvgBuy = yPriceAxe.getPointReverse(avgBuy);
            g.setColor(Color.red);
            g.drawLine(x, yAvgBuy, x, yAvgBuy);
            int yAvgSell = yPriceAxe.getPointReverse(avgSell);
            g.setColor(Color.blue);
            g.drawLine(x, yAvgSell, x, yAvgSell);
            int yAvgBuySellMid = yPriceAxe.getPointReverse(avgBuySellMid);
            g.setColor(Color.gray);
            g.drawLine(x, yAvgBuySellMid, x, yAvgBuySellMid);

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

            if (m_paintOrderData) {
                int yy = isBuy ? y + 2 + fontHeight : y - 5;
                int yStep = isBuy ? fontHeight : -fontHeight;
                g.setColor(order.m_side.isBuy() ? Color.BLUE : Color.RED);

                String amount = ((order.m_filled == 0) || (order.m_filled == order.m_amount))
                        ? Utils.X_YYY.format(order.m_amount)
                        : Utils.X_YYY.format(order.m_filled) + "/" + Utils.X_YYY.format(order.m_amount);
                g.drawString(amount, x, yy);
                yy += yStep;

                String tickAgeStr = Utils.millisToDHMSStr(orderPoint.m_tickAge);
                g.drawString(tickAgeStr, x, yy);
                yy += yStep;

                String gainStr = Utils.X_YYYYYYYY.format(orderPoint.m_gainAvg);
                g.drawString(gainStr, x, yy);
                yy += yStep;

                if (m_paintOrderIds) {
                    String ordId = order.m_orderId.substring(order.m_orderId.length() - 6);
                    g.drawString(ordId, x, yy);
                }
            }
        }

        paintTimeFramePoints(g, executor);

        g.setColor(Color.YELLOW);
        int height = getHeight();

        int y = (int) (height - fontHeight * 7.5);
        String[] strings = getState(exchData);
        for (String string : strings) {
            g.drawString(string, 2, y);
            y += fontHeight;
        }

        OrderData order = executor.m_order;
        if (order != null) {
            g.setColor(order.m_side.isBuy() ? Color.BLUE : Color.RED);
            g.drawString(order.toString(), 2, y - fontHeight);
        }
    }

    public static String[] getState(TresExchData exchData) {
        TresExecutor executor = exchData.m_executor;
        double avgFillSize = executor.getAvgFillSize();

        Double avgBuy = executor.m_buyAvgCounter.get();
        Double avgSell = executor.m_sellAvgCounter.get();
        Double avgBidAskDiff = (avgBuy != null) && (avgSell != null) ? avgSell - avgBuy : null;
        String avgBidAskDiffStr = (avgBidAskDiff != null) ? Utils.format3(avgBidAskDiff) : "";

        return new String[]{
                "avgTickAge: " + Utils.format3(executor.m_tickAgeCalc.getAverage()) + "; avgBidAskDiff=" + avgBidAskDiffStr,
                "takes:" + executor.dumpTakesTime(),
                "wait=" + executor.dumpWaitTime(),
                "placed=" + executor.m_ordersPlaced
                        + "; filled=" + executor.m_ordersFilled
                        + "; volume=" + Utils.format3(executor.m_tradeVolume)
                        + "; avgFillSize=" + Utils.format3(avgFillSize),
                "dir.adj=" + Utils.format5(exchData.getDirectionAdjusted()) + "; " + exchData.getRunAlgoParams(),
                "acct: " + executor.m_account,
                executor.valuateGain(),
                executor.valuate()
        };
    }

    private List<TresExchData.OrderPoint> getOrderClone(TresExchData exchData) {
        m_ordersToPaint.clear();
        if (m_paintOrders) {
            LinkedList<TresExchData.OrderPoint> orders = exchData.m_orders;
            double min = m_xTimeAxe.m_min;
            double max = m_xTimeAxe.m_max;
            synchronized (orders) { // avoid ConcurrentModificationException - use local copy
                for (Iterator<TresExchData.OrderPoint> iterator = orders.descendingIterator(); iterator.hasNext(); ) {
                    TresExchData.OrderPoint orderPoint = iterator.next();
                    OrderData order = orderPoint.m_order;
                    long placeTime = order.m_placeTime;
                    if (placeTime > max) { continue; }
                    if (placeTime < min) { break; }
                    m_ordersToPaint.add(orderPoint);
                }
            }
        }
        return m_ordersToPaint;
    }

    private List<OHLCTick> cloneOhlcTicks(LinkedList<OHLCTick> ohlcTicks) {
        double min = m_xTimeAxe.m_min;
        double max = m_xTimeAxe.m_max;
        m_paintOhlcTicks.clear();
        synchronized (ohlcTicks) { // avoid ConcurrentModificationException - use local copy
            for (Iterator<OHLCTick> iterator = ohlcTicks.descendingIterator(); iterator.hasNext(); ) {
                OHLCTick ohlcTick = iterator.next();
                long start = ohlcTick.m_barStart;
                if(start > max) { continue; }
                long end = ohlcTick.m_barEnd;
                if(end < min) { break; }
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
        } else if (status == OrderStatus.CANCELING) {
            fillColor = Color.gray;
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
        } else if (status == OrderStatus.CANCELING) {
            borderColor = Color.ORANGE;
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

    void paintDirectionArrow(Graphics g, TresExecutor executor, int chartAreaRight) {
        double directionAdjusted = executor.getDirectionAdjusted();
        double degrees = directionAdjusted * 90;
        double radians = degrees * Math.PI / 180;
        double dx = DIRECTION_ARROW_SIZE * Math.cos(radians);
        double dy = -DIRECTION_ARROW_SIZE * Math.sin(radians);
        Color color = (directionAdjusted > 0) ? Color.BLUE : (directionAdjusted < 0) ? Color.RED : Color.GRAY;
        g.setColor(color);
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(3));
        int height = getHeight();
        int heightMid = height / 2;
        g.drawLine(chartAreaRight, heightMid, (int) (chartAreaRight + dx), (int) (heightMid + dy));
        g2.setStroke(new BasicStroke(1));
    }

//    private void paintMaTicks(Graphics g, TresMaCalculator maCalculator, ChartAxe yPriceAxe) {
//        LinkedList<TresMaCalculator.MaTick> maTicks = new LinkedList<TresMaCalculator.MaTick>();
//        LinkedList<TresMaCalculator.MaTick> ticks = maCalculator.m_maTicks;
//        synchronized (ticks) { // avoid ConcurrentModificationException - use local copy
//            maTicks.addAll(ticks);
//        }
//        int firstX = Integer.MAX_VALUE;
//        int firstY = Integer.MAX_VALUE;
//        int lastX = Integer.MAX_VALUE;
//        int lastY = Integer.MAX_VALUE;
//        Color nextColor = null;
//        for (Iterator<TresMaCalculator.MaTick> iterator = maTicks.descendingIterator(); iterator.hasNext(); ) {
//            TresMaCalculator.MaTick maTick = iterator.next();
//            double ma = maTick.m_ma;
//            long barEnd = maTick.m_barEnd;
//            int x = m_xTimeAxe.getPoint(barEnd);
//            int y = yPriceAxe.getPointReverse(ma);
//            if (firstX == Integer.MAX_VALUE) {
//                firstX = x;
//                firstY = y;
//            }
//            Color color = maTick.m_maCrossed ? Color.ORANGE : Color.WHITE;
//            g.setColor(nextColor == null ? color : nextColor);
//            if (lastX == Integer.MAX_VALUE) {
//                g.fillRect(x - 1, y - 1, 3, 3);
//            } else {
//                g.drawLine(x, y, lastX, lastY);
//            }
//            nextColor = color;
//            lastX = x;
//            lastY = y;
//            if (x < 0) {
//                break;
//            }
//        }
//
//        if ((firstX != Integer.MAX_VALUE) && (firstY != Integer.MAX_VALUE)) {
//            double directionAdjusted = maCalculator.m_phaseData.m_exchData.getDirectionAdjusted();
//            double degrees = directionAdjusted * 90;
//            double radians = degrees * Math.PI / 180;
//            double dx = DIRECTION_ARROW_SIZE * Math.cos(radians);
//            double dy = -DIRECTION_ARROW_SIZE * Math.sin(radians);
//            Color color = (directionAdjusted > 0) ? Color.BLUE : (directionAdjusted < 0) ? Color.RED : Color.GRAY;
//            g.setColor(color);
//            Graphics2D g2 = (Graphics2D) g;
//            g2.setStroke(new BasicStroke(3));
//            g.drawLine(firstX, firstY, (int) (firstX + dx), (int) (firstY + dy));
//            g2.setStroke(new BasicStroke(1));
//        }
//
//        int canvasWidth = getWidth();
//        int canvasHeight = getHeight();
//        Boolean lastOscUp = null;
//        Double lastPrice = null;
//        double totalPriceRatio = 1;
//        int tradeNum = 0;
//        int fontHeight = g.getFont().getSize();
//
//        LinkedList<TresMaCalculator.MaCrossData> maCd = maCalculator.m_maCrossDatas;
//        LinkedList<TresMaCalculator.MaCrossData> maCrossDatas = new LinkedList<TresMaCalculator.MaCrossData>();
//        synchronized (maCd) { // avoid ConcurrentModificationException - use local copy
//            maCrossDatas.addAll(maCd);
//        }
//        for (TresMaCalculator.MaCrossData maCrossData : maCrossDatas) {
//            Long timestamp = maCrossData.m_timestamp;
//            int x = m_xTimeAxe.getPoint(timestamp);
//            boolean oscUp = maCrossData.m_oscUp;
//            double price = maCrossData.m_price;
//            int y = yPriceAxe.getPointReverse(price);
//            if (lastOscUp != null) {
//                if (lastOscUp != oscUp) {
//                    if (m_paintSym && (x > 0)) {
//                        int dy = y - lastY;
//                        Color color = (lastOscUp && (dy < 0)) || (!lastOscUp && (dy > 0)) ? Color.GREEN : Color.RED;
//                        g.setColor(color);
//                        g.drawLine(lastX, lastY, x, y);
//                    }
//                    if (lastPrice != null) {
//                        double priceRatio = price / lastPrice;
//                        if (!lastOscUp) {
//                            priceRatio = 1 / priceRatio;
//                        }
//                        totalPriceRatio *= priceRatio;
//                        if (m_paintSym && ((x > 0) && (x < canvasWidth))) {
//                            g.drawString(String.format("r: %1$,.5f", priceRatio), x, y + fontHeight);
//                            Color color = (totalPriceRatio > 1) ? Color.GREEN : Color.RED;
//                            g.setColor(color);
//                            g.drawString(String.format("t: %1$,.5f", totalPriceRatio), x, y + fontHeight * 2);
//
//                            g.setColor(TR_COLOR);
//                            g.drawLine(x, 0, x, canvasHeight);
//                        }
//                        tradeNum++;
//                    }
//                    lastOscUp = oscUp;
//                    lastY = y;
//                    lastX = x;
//                    lastPrice = price;
//                }
//            } else { // first
//                lastOscUp = oscUp;
//                lastY = y;
//                lastX = x;
//                lastPrice = price;
//            }
//            if (m_paintSym && (x > 0)) {
//                g.setColor(oscUp ? Color.GREEN : Color.RED);
//                g.drawLine(x, y, x + 5, y + (oscUp ? -5 : 5));
//            }
//        }
//
//        g.setColor(Color.ORANGE);
//        TresExchData exchData = maCalculator.m_phaseData.m_exchData;
//        long runningTimeMillis = exchData.m_lastTickMillis - exchData.m_startTickMillis;
//        String runningStr = Utils.millisToDHMSStr(runningTimeMillis);
//        g.drawString("running: " + runningStr, 5, fontHeight * 5 + 5);
//
//        g.drawString(String.format("ratio: %.5f", totalPriceRatio), 5, fontHeight * 6 + 5);
//
//        double runningTimeDays = ((double) runningTimeMillis) / Utils.ONE_DAY_IN_MILLIS;
//        double aDay = Math.pow(totalPriceRatio, 1 / runningTimeDays);
//        g.drawString(String.format("projected aDay: %.5f", aDay), 5, fontHeight * 7 + 5);
//
//        g.drawString(String.format("trade num: %d", tradeNum), 5, fontHeight * 8 + 5);
//        long tradeFreq = (tradeNum == 0) ? 0 : (runningTimeMillis / tradeNum);
//        g.drawString(String.format("trade every " + Utils.millisToDHMSStr(tradeFreq)), 5, fontHeight * 9 + 5);
//    }

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

//    private void paintOscTicks(Graphics g, LinkedList<OscTick> oscBars) {
//        int canvasWidth = getWidth();
//        int lastX = Integer.MAX_VALUE;
//        int[] lastYs = new int[3];
//        // todo: clone first
//        for (Iterator<OscTick> iterator = oscBars.descendingIterator(); iterator.hasNext(); ) {
//            OscTick tick = iterator.next();
//            int endX = paintOsc(g, tick, lastX, lastYs, canvasWidth);
//            if (endX < 0) { break; }
//            lastX = endX;
//        }
//    }

//    private void paintCoppockTicks(Graphics g, List<ChartPoint> ticksClone, ChartAxe yAxe, Color color) {
//        g.setColor(color);
//        int lastX = Integer.MAX_VALUE;
//        int lastY = Integer.MAX_VALUE;
//        for (ChartPoint tick : ticksClone) {
//            long endTime = tick.m_millis;
//            int x = m_xTimeAxe.getPoint(endTime);
//            double val = tick.m_value;
//            int y = yAxe.getPointReverse(val);
//            if (lastX != Integer.MAX_VALUE) {
//                g.drawLine(lastX, lastY, x, y);
//            } else {
//                g.drawRect(x - 1, y - 1, 2, 2);
//            }
//            lastX = x;
//            lastY = y;
//        }
//    }

//    private void paintCoppockPeaks(Graphics g, List<ChartPoint> peaksClone, ChartAxe yAxe, Color color, boolean big) {
//        int size = big ? 6 : 3;
//        g.setColor(color);
//        for (ChartPoint peakClone : peaksClone) {
//            long endTime = peakClone.m_millis;
//            int x = m_xTimeAxe.getPoint(endTime);
//            double coppock = peakClone.m_value;
//            int y = yAxe.getPointReverse(coppock);
//            g.drawLine(x - size, y - size, x + size, y + size);
//            g.drawLine(x + size, y - size, x - size, y + size);
//        }
//    }

//    private void paintLine(Graphics g, double level) {
//        int y = m_yAxe.getPointReverse(level);
//        g.drawLine(0, y, getWidth(), y);
//    }

//    private int paintOsc(Graphics g, OscTick tick, int lastX, int[] lastYs, int canvasWidth) {
//        long startTime = tick.m_startTime;
//        long endTime = startTime + m_tres.m_barSizeMillis;
//        int x = m_xTimeAxe.getPoint(endTime);
//
//        double val1 = tick.m_val1;
//        double val2 = tick.m_val2;
//        double valMid = tick.getMid();
//        int y1 = m_yAxe.getPointReverse(val1);
//        int y2 = m_yAxe.getPointReverse(val2);
//        int yMid = m_yAxe.getPointReverse(valMid);
//
//        if (lastX < canvasWidth) {
//            if (lastX == Integer.MAX_VALUE) {
//                g.setColor(Color.RED);
//                g.drawRect(x - 2, y1 - 2, 5, 5);
//                g.setColor(Color.BLUE);
//                g.drawRect(x - 2, y2 - 2, 5, 5);
//            } else {
//                g.setColor(OSC_MID_LINE_COLOR);
//                g.drawLine(lastX, lastYs[2], x, yMid);
//                g.setColor(OSC_1_LINE_COLOR);
//                g.drawLine(lastX, lastYs[0], x, y1);
//                g.setColor(OSC_2_LINE_COLOR);
//                g.drawLine(lastX, lastYs[1], x, y2);
//            }
//        }
//        lastYs[0] = y1;
//        lastYs[1] = y2;
//        lastYs[2] = yMid;
//        return x;
//    }
}
