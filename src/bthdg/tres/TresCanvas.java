package bthdg.tres;

import bthdg.ChartAxe;
import bthdg.Log;
import bthdg.exch.Exchange;
import bthdg.osc.OscTick;

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

    private Tres m_tres;
    private Point m_point;
    private ChartAxe m_yAxe;
    private ChartAxe m_xTimeAxe;
    private int m_maxBars;
    private int yPriceAxeWidth;
    private double m_zoom = 1;
    private Integer m_dragStartX;
    private Integer m_dragDeltaX;
    private Integer m_dragDeltaBars;

    private static void log(String s) { Log.log(s); }

    TresCanvas(Tres tres) {
        m_tres = tres;
        setMinimumSize(new Dimension(500, 200));
        setPreferredSize(new Dimension(500, 200));
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
                log("Mouse press x=" + x);
                m_dragStartX = x;
                repaint();
            }

            @Override public void mouseDragged(MouseEvent e) {
                int x = e.getX();
                log("Mouse drag x=" + x);
                m_dragDeltaX = x - m_dragStartX;
                log(" dragDeltaX=" + m_dragDeltaX);
                m_dragDeltaBars = m_dragDeltaX / pixPerBar();
                log("  dragDeltaBars=" + m_dragDeltaBars);
                repaint();
            }

            @Override public void mouseReleased(MouseEvent e) {
                int x = e.getX();
                log("Mouse release x=" + x);
                int dragDeltaX = x - m_dragStartX;
                log(" dragDeltaX=" + dragDeltaX);
                int dragDeltaBars = dragDeltaX / pixPerBar();
                log("  dragDeltaBars=" + dragDeltaBars);
                m_dragStartX = null;
                m_dragDeltaX = null;
                repaint();
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
        addMouseWheelListener(mouseAdapter);
    }

    private void onMouseWheelMoved(MouseWheelEvent e) {
        int notches = e.getWheelRotation();
        if (notches < 0) {
            log("Mouse wheel moved UP " + -notches + " notch(es)");
            m_zoom *= 1.1;
        } else {
            log("Mouse wheel moved DOWN " + notches + " notch(es)");
            m_zoom /= 1.1;
        }
        log(" m_zoom=" + m_zoom);
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
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        long barSize = m_tres.m_barSizeMillis;

        if (m_point != null) {
            paintBarHighlight(g, barSize);
        }

        ArrayList<TresExchData> exchDatas = m_tres.m_exchDatas;
        TresExchData exchData = exchDatas.get(0);
        PhaseData phaseData = exchData.m_phaseDatas[0];
        TresOscCalculator oscCalculator = phaseData.m_oscCalculator;
        LinkedList<OHLCTick> ohlcTicks = phaseData.m_ohlcCalculator.m_ohlcTicks;
        LinkedList<OscTick> bars = oscCalculator.m_oscBars;
        calcXTimeAxe(width, barSize, ohlcTicks, bars);

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
            paintOsc(g, fineTick, width, Color.GRAY, 5);

            g.drawString(String.format("Zoom: %1$,.4f", m_zoom), 5, fontHeight * 2 + 5);

            if (m_dragStartX != null) {
                g.drawString(String.format("dragStartX: %d", m_dragStartX), 5, fontHeight * 3 + 5);
            }
            if (m_dragDeltaX != null) {
                g.drawString(String.format("dragDeltaX: %d", m_dragDeltaX), 5, fontHeight * 4 + 5);
            }
            if (m_dragDeltaBars != null) {
                g.drawString(String.format("dragDeltaBars: %d", m_dragDeltaBars), 5, fontHeight * 5 + 5);
            }

            ChartAxe yPriceAxe = calcYPriceAxe(height, lastPrice, ohlcTicks);
            paintYPriceAxe(g, yPriceAxe);

            paintOHLCTicks(g, ohlcTicks, yPriceAxe);
            paintOscTicks(g, bars);

            TresMaCalculator maCalculator = phaseData.m_maCalculator;
            paintMaTicks(g, maCalculator.m_maTicks, yPriceAxe);

            paintLastPrice(g, width, lastPrice, yPriceAxe);
        }

        if (m_point != null) {
            paintCross(g, width, height);
        }
    }

    private void paintLastPrice(Graphics g, int width, double lastPrice, ChartAxe yPriceAxe) {
        int lastPriceY = yPriceAxe.getPointReverse(lastPrice);
        g.setColor(Color.BLUE);
        g.fillRect(width - 7, lastPriceY - 1, 5, 3);
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
            g.drawLine(x - 5, priceY, x - 15, priceY);
        }
        if (yPriceAxeWidth != maxWidth) { // changed
            calcMaxBars(width);
        }
        yPriceAxeWidth = maxWidth;
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

        int areaWidth = width - yPriceAxeWidth;
        int pixPerBar = pixPerBar();
        int maxBarNum = areaWidth / pixPerBar;
        long minTime = maxTime - barSize * maxBarNum;
        int barsWidth = maxBarNum * pixPerBar;
        int extraAreaWidth = areaWidth - barsWidth;

        m_xTimeAxe = new ChartAxe(minTime, maxTime, barsWidth);
        m_xTimeAxe.m_offset = extraAreaWidth;
    }

    private ChartAxe calcYPriceAxe(int height, double lastPrice, LinkedList<OHLCTick> ohlcTicks) {
        double maxPrice = lastPrice;
        double minPrice = lastPrice;
        int counter = 0;
        for (Iterator<OHLCTick> iterator = ohlcTicks.descendingIterator(); iterator.hasNext(); ) {
            OHLCTick ohlcTick = iterator.next();
            maxPrice = Math.max(maxPrice, ohlcTick.m_high);
            minPrice = Math.min(minPrice, ohlcTick.m_low);
            if (counter++ > m_maxBars) {
                break;
            }
        }
        double priceDiff = maxPrice - minPrice;
        if (priceDiff < 1) {
            double extra = (1 - priceDiff) / 2;
            maxPrice += extra;
            minPrice -= extra;
        }
        ChartAxe yPriceAxe = new ChartAxe(minPrice, maxPrice, height - 2);
        yPriceAxe.m_offset = 1;
        return yPriceAxe;
    }

    private void paintMaTicks(Graphics g, LinkedList<TresMaCalculator.MaTick> maTicks, ChartAxe yPriceAxe) {
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        Color nextColor = null;
        for (Iterator<TresMaCalculator.MaTick> iterator = maTicks.descendingIterator(); iterator.hasNext(); ) {
            TresMaCalculator.MaTick maTick = iterator.next();
            double ma = maTick.m_ma;
            long barEnd = maTick.m_barEnd;
            int x = m_xTimeAxe.getPoint(barEnd);
            int y = yPriceAxe.getPointReverse(ma);
            Color color = maTick.m_maCrossed ? Color.YELLOW : Color.WHITE;
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
    }

    private void paintOHLCTicks(Graphics g, LinkedList<OHLCTick> ohlcTicks, ChartAxe yPriceAxe) {
        g.setColor(Color.GREEN);
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

//            // candle style
//            int barHeight = closeY - openY;
//            if (barHeight == 0) {
//                barHeight = 1;
//            }
//            g.fillRect(startX + 1, openY, endX - startX - 2, barHeight);

            // ohlc style
            g.drawLine(startX + 1, openY, midX, openY);
            g.drawLine(midX, closeY, endX, closeY);

            if (startX < 0) {
                break;
            }
        }
    }

    private void paintOscTicks(Graphics g, LinkedList<OscTick> oscTicks) {
        g.setColor(Color.darkGray);
        paintLine(g, 0.2);
        paintLine(g, 0.8);
//        g.setColor(Color.CYAN);
//        paintLine(g, 0);
//        paintLine(g, 1);

        int lastX = Integer.MAX_VALUE;
        int[] lastYs = new int[2];
        for (Iterator<OscTick> iterator = oscTicks.descendingIterator(); iterator.hasNext(); ) {
            OscTick tick = iterator.next();
            int endX = paintOsc(g, tick, m_xTimeAxe, lastX, lastYs);
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

    private int paintOsc(Graphics g, OscTick tick, ChartAxe xAxe, int lastX, int[] lastYs) {
        long startTime = tick.m_startTime;
        long endTime = startTime + m_tres.m_barSizeMillis;
        int x = xAxe.getPoint(endTime);

        double val1 = tick.m_val1;
        double val2 = tick.m_val2;
        int y1 = m_yAxe.getPointReverse(val1);
        int y2 = m_yAxe.getPointReverse(val2);

        if (lastX == Integer.MAX_VALUE) {
            g.setColor(Color.RED);
            g.drawRect(x - 2, y1 - 2, 5, 5);
            g.setColor(Color.BLUE);
            g.drawRect(x - 2, y2 - 2, 5, 5);
        } else {
            g.setColor(Color.RED);
            g.drawLine(lastX, lastYs[0], x, y1);
            g.setColor(Color.BLUE);
            g.drawLine(lastX, lastYs[1], x, y2);
        }
        lastYs[0] = y1;
        lastYs[1] = y2;
        return x;
    }

    private void paintOsc(Graphics g, OscTick tick, int width, Color color, int offset) {
        if (tick != null) {
            double val1 = tick.m_val1;
            paintOscPoint(g, width, color, offset, val1);
            double val2 = tick.m_val2;
            paintOscPoint(g, width, color, offset, val2);
        }
    }

    private void paintOscPoint(Graphics g, int width, Color color, int offset, double val) {
        int y = m_yAxe.getPointReverse(val);
        g.setColor(color);
        g.drawRect(width - offset - 2, y - 2, 5, 5);
    }
}
