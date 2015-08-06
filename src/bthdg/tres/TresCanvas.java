package bthdg.tres;

import bthdg.ChartAxe;
import bthdg.Log;
import bthdg.exch.Exchange;
import bthdg.exch.OrderData;
import bthdg.exch.OrderSide;
import bthdg.exch.OrderStatus;
import bthdg.osc.OscTick;
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

public class TresCanvas extends JComponent {
    public static final int PIX_PER_BAR = 12;
    public static final int LAST_PRICE_MARKER_WIDTH = 7;
    public static final int PRICE_AXE_MARKER_WIDTH = 10;
    public static final Color TR_COLOR = new Color(20, 20, 20);
    public static final Color OSC_1_LINE_COLOR = Colors.setAlpha(Color.RED, 128);
    public static final Color OSC_2_LINE_COLOR = Colors.setAlpha(Color.BLUE, 128);
    public static final Color OSC_MID_LINE_COLOR = new Color(30,30,30);

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

    public void paint(Graphics g) {
        super.paint(g);

        int width = getWidth();
        int height = getHeight();

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        if(m_tres.PAINT_TICK_TIMES_ONLY) {
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
        PhaseData phaseData = exchData.m_phaseDatas[0];
        TresOscCalculator oscCalculator = phaseData.m_oscCalculator;
        LinkedList<OHLCTick> ohlcTicks = phaseData.m_ohlcCalculator.m_ohlcTicks;
        LinkedList<OscTick> oscBars = oscCalculator.m_oscBars;
        calcXTimeAxe(width, barSize, ohlcTicks, oscBars);

        // paint min/max time : left/right borders
        double minTime = m_xTimeAxe.m_min;
        int minTimeX = m_xTimeAxe.getPoint(minTime);
        double maxTime = m_xTimeAxe.m_max;
        int maxTimeX = m_xTimeAxe.getPoint(maxTime);
        g.setColor(Color.BLUE);
        g.drawLine(minTimeX, 0, minTimeX, height);
        g.drawLine(maxTimeX, 0, maxTimeX, height);

        double lastPrice = exchData.m_lastPrice;
        if (lastPrice != 0) {
            Exchange exchange = exchData.m_ws.exchange();
            String lastStr = exchange.roundPriceStr(lastPrice, Tres.PAIR);
            int fontHeight = g.getFont().getSize();
            g.drawString("Last: " + lastStr, 5, fontHeight + 5);

            OscTick fineTick = oscCalculator.m_lastFineTick;
            paintFineOsc(g, fineTick, width - 5);

            OscTick blendedTick = oscCalculator.m_blendedLastFineTick;
            OscTick lastBarTick = oscCalculator.m_lastBar;
            paintBlendedOsc(g, lastBarTick, blendedTick, width - 5);

            g.drawString(String.format("Zoom: %1$,.4f", m_zoom), 5, fontHeight * 2 + 5);

            if (m_dragStartX != null) {
                g.drawString(String.format("dragStartX: %d", m_dragStartX), 5, fontHeight * 3 + 5);
            }
            if (m_dragDeltaX != null) {
                g.drawString(String.format("dragDeltaX: %d", m_dragDeltaX), 5, fontHeight * 4 + 5);
            }
            if (m_dragDeltaBars != 0) {
                g.drawString(String.format("dragDeltaBars: %d", m_dragDeltaBars), 5, fontHeight * 5 + 5);
            }
            g.drawString(String.format("barsShift: %d", m_barsShift), 5, fontHeight * 6 + 5);

            ChartAxe yPriceAxe = calcYPriceAxe(height, lastPrice, ohlcTicks, barSize);
            paintYPriceAxe(g, yPriceAxe);

            paintOHLCTicks(g, ohlcTicks, yPriceAxe);

            LinkedList<OscTick> oscPeaks = oscCalculator.m_oscPeaks;
            paintOscTicks(g, oscBars, oscPeaks);

            TresMaCalculator maCalculator = phaseData.m_maCalculator;
            paintMaTicks(g, maCalculator, yPriceAxe);

            paintOrders(g, exchData, yPriceAxe);

            paintLastPrice(g, width, lastPrice, yPriceAxe);
        }

        if (m_point != null) {
            paintCross(g, width, height);
        }
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
        int lastPriceY = yPriceAxe.getPointReverse(lastPrice);
        g.setColor(Color.BLUE);
        g.fillRect(width - LAST_PRICE_MARKER_WIDTH + 2, lastPriceY - 1, LAST_PRICE_MARKER_WIDTH, 3);
    }

    private void paintYPriceAxe(Graphics g, ChartAxe yPriceAxe) {
        g.setColor(Color.ORANGE);

        int fontHeight = g.getFont().getSize();
        int halfFontHeight = fontHeight / 2;
        double min = yPriceAxe.m_min;
        double max = yPriceAxe.m_max;

        int step = 1;
        int minLabel = (int) Math.ceil(min / step);
        int maxLabel = (int) Math.floor(max / step);

        int maxWidth = 0;
        for (int y = minLabel; y <= maxLabel; y += step) {
            Rectangle2D bounds = g.getFontMetrics().getStringBounds(Integer.toString(y), g);
            int width = (int) bounds.getWidth();
            maxWidth = Math.max(maxWidth, width);
        }
        int width = getWidth();
        int x = width - maxWidth;
        for (int y = minLabel; y <= maxLabel; y += step) {
            int priceY = yPriceAxe.getPointReverse(y);
            g.drawString(Integer.toString(y), x, priceY + halfFontHeight);
            g.drawLine(x - 5, priceY, x - PRICE_AXE_MARKER_WIDTH, priceY);
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
        g.setColor(Color.DARK_GRAY);
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
        OHLCTick lastOhlcTick = ohlcTicks.peekLast();
        if (lastOhlcTick != null) {
            maxTime = lastOhlcTick.m_barEnd;
        }
        OscTick lastOscTick = bars.peekLast();
        if (lastOscTick != null) {
            long endTime = lastOscTick.m_startTime + barSize;
            maxTime = Math.max(maxTime, endTime);
        }
        if (maxTime == 0) {
            maxTime = System.currentTimeMillis();
        }
        maxTime -= (m_barsShift + m_dragDeltaBars) * barSize;

        int areaWidth = width - yPriceAxeWidth;
        int pixPerBar = pixPerBar();
        int maxBarNum = areaWidth / pixPerBar;
        long minTime = maxTime - barSize * maxBarNum;
        int barsWidth = maxBarNum * pixPerBar;
        int extraAreaWidth = areaWidth - barsWidth;

        m_xTimeAxe = new ChartAxe(minTime, maxTime, barsWidth);
        m_xTimeAxe.m_offset = extraAreaWidth;
    }

    private ChartAxe calcYPriceAxe(int height, double lastPrice, LinkedList<OHLCTick> ohlcTicks, long barSize) {
        double timeMin = m_xTimeAxe.m_min;
        double timeMax = m_xTimeAxe.m_max;
        double maxPrice = 0;
        double minPrice = Integer.MAX_VALUE;
        for (Iterator<OHLCTick> iterator = ohlcTicks.iterator(); iterator.hasNext(); ) {
            OHLCTick ohlcTick = iterator.next();
            long barStart = ohlcTick.m_barStart;
            long barEnd = barStart + barSize;
            if ((barEnd >= timeMin) && (barStart <= timeMax)) {
                maxPrice = Math.max(maxPrice, ohlcTick.m_high);
                minPrice = Math.min(minPrice, ohlcTick.m_low);
            }
        }
        if (maxPrice == 0) {
            maxPrice = lastPrice;
            minPrice = lastPrice;
        }
        double priceDiff = maxPrice - minPrice;
        if (priceDiff == 0) {
            maxPrice += 0.5;
            minPrice -= 0.5;
        }
        ChartAxe yPriceAxe = new ChartAxe(minPrice, maxPrice, height - 2);
        yPriceAxe.m_offset = 1;
        return yPriceAxe;
    }

    private void paintOrders(Graphics g, TresExchData exchData, ChartAxe yPriceAxe) {
        LinkedList<OrderData> orders = exchData.m_orders;
        LinkedList<OrderData> ordersInt = new LinkedList<OrderData>();
        synchronized (orders) { // avoid ConcurrentModificationException - use local copy
            ordersInt.addAll(orders);
        }
        for (Iterator<OrderData> iterator = ordersInt.descendingIterator(); iterator.hasNext(); ) {
            OrderData order = iterator.next();
            long mPlaceTime = order.m_placeTime;
            int x = m_xTimeAxe.getPoint(mPlaceTime);
            if (x < 0) {
                break;
            }

            double price = order.m_price;
            OrderSide side = order.m_side;
            boolean isBuy = side.isBuy();

            Color borderColor = null;
            OrderStatus status = order.m_status;
            if (status == OrderStatus.NEW) {
                borderColor = Color.green;
            } else if ((status == OrderStatus.SUBMITTED) || (status == OrderStatus.PARTIALLY_FILLED)) {
                borderColor = isBuy ? Color.BLUE : Color.RED;
            }

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
        }

        g.setColor(Color.YELLOW);
        int fontHeight = g.getFont().getSize();
        int height = getHeight();
        TresExecutor executor = exchData.m_executor;
        g.drawString(executor.valuate(), 2, height - fontHeight);
        g.drawString("dir.adj=" + exchData.getDirectionAdjusted(), 2, height - fontHeight * 2);
        g.drawString("placed=" + executor.m_ordersPlaced + "; filled=" + executor.m_ordersFilled, 2, height - fontHeight * 3);

        OrderData order = executor.m_order;
        if (order != null) {
            g.setColor(order.m_side.isBuy() ? Color.BLUE : Color.RED);
            g.drawString("" + order, 2, height - fontHeight * 4);
        }
    }

    private void paintMaTicks(Graphics g, TresMaCalculator maCalculator, ChartAxe yPriceAxe) {
        LinkedList<TresMaCalculator.MaTick> maTicks = new LinkedList<TresMaCalculator.MaTick>();
        LinkedList<TresMaCalculator.MaTick> ticks = maCalculator.m_maTicks;
        synchronized (ticks) { // avoid ConcurrentModificationException - use local copy
            maTicks.addAll(ticks);
        }
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        Color nextColor = null;
        for (Iterator<TresMaCalculator.MaTick> iterator = maTicks.descendingIterator(); iterator.hasNext(); ) {
            TresMaCalculator.MaTick maTick = iterator.next();
            double ma = maTick.m_ma;
            long barEnd = maTick.m_barEnd;
            int x = m_xTimeAxe.getPoint(barEnd);
            int y = yPriceAxe.getPointReverse(ma);
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
        for (Iterator<TresMaCalculator.MaCrossData> iterator = maCrossDatas.iterator(); iterator.hasNext(); ) {
            TresMaCalculator.MaCrossData maCrossData = iterator.next();
            Long timestamp = maCrossData.m_timestamp;
            int x = m_xTimeAxe.getPoint(timestamp);
            boolean oscUp = maCrossData.m_oscUp;
            double price = maCrossData.m_price;
            int y = yPriceAxe.getPointReverse(price);
            if (lastOscUp != null) {
                if (lastOscUp != oscUp) {
                    if (x > 0) {
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
                        if ((x > 0) && (x < canvasWidth)) {
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
            if (x > 0) {
                g.setColor(oscUp ? Color.GREEN : Color.RED);
                g.drawLine(x, y, x + 5, y + (oscUp ? -5 : 5));
            }
        }

        g.setColor(Color.ORANGE);
        TresExchData exchData = maCalculator.m_phaseData.m_exchData;
        long runningTimeMillis = exchData.m_lastTickMillis - exchData.m_startTickMillis;
        String runningStr = Utils.millisToDHMSStr(runningTimeMillis);
        g.drawString("running: " + runningStr, 5, fontHeight * 8 + 5);

        g.drawString(String.format("ratio: %.5f", totalPriceRatio), 5, fontHeight * 9 + 5);

        double runningTimeDays = ((double) runningTimeMillis) / Utils.ONE_DAY_IN_MILLIS;
        double aDay = Math.pow(totalPriceRatio, 1 / runningTimeDays);
        g.drawString(String.format("projected aDay: %.5f", aDay), 5, fontHeight * 10 + 5);

        g.drawString(String.format("trade num: %d", tradeNum), 5, fontHeight * 11 + 5);
        long tradeFreq = (tradeNum == 0) ? 0 : (runningTimeMillis / tradeNum);
        g.drawString(String.format("trade every " + Utils.millisToDHMSStr(tradeFreq)), 5, fontHeight * 12 + 5);
    }

    private void paintOHLCTicks(Graphics g, LinkedList<OHLCTick> ohlcTicks, ChartAxe yPriceAxe) {
        g.setColor(Color.GRAY);
        for (Iterator<OHLCTick> iterator = ohlcTicks.descendingIterator(); iterator.hasNext(); ) {
            OHLCTick ohlcTick = iterator.next();
            long barStart = ohlcTick.m_barStart;
            int startX = m_xTimeAxe.getPoint(barStart);
            long barEnd = ohlcTick.m_barEnd;
            int endX = m_xTimeAxe.getPoint(barEnd);
            int midX = (startX + endX) / 2;

            int highY = yPriceAxe.getPointReverse(ohlcTick.m_high);
            int lowY = yPriceAxe.getPointReverse(ohlcTick.m_low);
            int openY = yPriceAxe.getPointReverse(ohlcTick.m_open);
            int closeY = yPriceAxe.getPointReverse(ohlcTick.m_close);
            g.drawLine(midX, highY, midX, lowY);

            // ohlc style
            g.drawLine(startX + 1, openY, midX, openY);
            g.drawLine(midX, closeY, endX, closeY);

            if (startX < 0) {
                break;
            }
        }
    }

    private void paintOscTicks(Graphics g, LinkedList<OscTick> oscBars, LinkedList<OscTick> oscPeaks) {
        // levels
        g.setColor(Color.darkGray);
        paintLine(g, 0.2);
        paintLine(g, 0.8);

        // top/bottom borders
//        g.setColor(Color.CYAN);
//        paintLine(g, 0);
//        paintLine(g, 1);

        int canvasWidth = getWidth();
        g.setColor(Colors.LIGHT_CYAN);
        for (Iterator<OscTick> iterator = oscPeaks.descendingIterator(); iterator.hasNext(); ) {
            OscTick tick = iterator.next();
            long startTime = tick.m_startTime;
            long endTime = startTime + m_tres.m_barSizeMillis;
            int x = m_xTimeAxe.getPoint(endTime);
            if (x < 0) {
                break;
            }
            if (x > canvasWidth) {
                continue;
            }
            double valMid = tick.getMid();
            int y = m_yAxe.getPointReverse(valMid);
            g.drawLine(x - 5, y - 5, x + 5, y + 5);
            g.drawLine(x + 5, y - 5, x - 5, y + 5);
        }

        int lastX = Integer.MAX_VALUE;
        int[] lastYs = new int[3];
        for (Iterator<OscTick> iterator = oscBars.descendingIterator(); iterator.hasNext(); ) {
            OscTick tick = iterator.next();
            int endX = paintOsc(g, tick, lastX, lastYs, canvasWidth);
            if (endX < 0) {
                break;
            }
            lastX = endX;
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

    private void paintBlendedOsc(Graphics g, OscTick lastBarTick, OscTick blendedTick, int offset) {
        if ((lastBarTick != null) && (blendedTick != null)) {
            long lastBarStartTime = lastBarTick.m_startTime;
            long lastBarEndTime = lastBarStartTime + m_tres.m_barSizeMillis;
            int x1 = m_xTimeAxe.getPoint(lastBarEndTime);

            double lastBarOscVal1 = lastBarTick.m_val1;
            double lastBarOscVal2 = lastBarTick.m_val2;
            int lastBarOscY1 = m_yAxe.getPointReverse(lastBarOscVal1);
            int lastBarOscY2 = m_yAxe.getPointReverse(lastBarOscVal2);

            int x2 = offset;

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
