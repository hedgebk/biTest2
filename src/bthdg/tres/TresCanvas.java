package bthdg.tres;

import bthdg.ChartAxe;
import bthdg.exch.Exchange;
import bthdg.osc.OscTick;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    TresCanvas(Tres tres) {
        m_tres = tres;
        setMinimumSize(new Dimension(500, 200));
        setPreferredSize(new Dimension(500, 200));
        //setDoubleBuffered(true);
        setBackground(Color.BLACK);

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { updatePoint(e.getPoint()); }
            @Override public void mouseExited(MouseEvent e) { updatePoint(null); }
            @Override public void mouseMoved(MouseEvent e) { updatePoint(e.getPoint()); }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
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
        m_maxBars = width / PIX_PER_BAR + 1;
    }

    public void paint(Graphics g) {
        super.paint(g);

        int width = getWidth();
        int height = getHeight();

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

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

        double lastPrice = exchData.m_lastPrice;
        if (lastPrice != 0) {
            Exchange exchange = exchData.m_ws.exchange();
            String lastStr = exchange.roundPriceStr(lastPrice, Tres.PAIR);
            int fontHeight = g.getFont().getSize();
            g.drawString("Last: " + lastStr, 5, fontHeight + 5);

            OscTick fineTick = oscCalculator.m_lastFineTick;
            paintOsc(g, fineTick, width, Color.GRAY, 5);

            ChartAxe yPriceAxe = calcYPriceAxe(height, lastPrice, ohlcTicks);

            paintOHLCTicks(g, ohlcTicks, yPriceAxe);
            paintOscTicks(g, bars);

            TresMaCalculator maCalculator = phaseData.m_maCalculator;
            paintMaTicks(g, maCalculator.m_maTicks, yPriceAxe);

            int lastPriceY = yPriceAxe.getPointReverse(lastPrice);
            g.setColor(Color.BLUE);
            g.fillRect(width - 7, lastPriceY - 1, 5, 3);

            paintYPriceAxe(g, yPriceAxe);
        }

        if (m_point != null) {
            paintCross(g, width, height);
        }
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
            maxTime = lastOhlcTick.m_barStart;
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
        int maxBarNum = areaWidth / PIX_PER_BAR;
        long minTime = maxTime - barSize * maxBarNum;

        m_xTimeAxe = new ChartAxe(minTime, maxTime, areaWidth);
        m_xTimeAxe.m_offset = -yPriceAxeWidth;
    }

    private ChartAxe calcYPriceAxe(int height, double lastPrice, LinkedList<OHLCTick> ohlcTicks) {
        double maxPrice = lastPrice;
        double minPrice = lastPrice;
        int counter = 0;
        for (Iterator<OHLCTick> iterator = ohlcTicks.descendingIterator(); iterator.hasNext(); ) {
            OHLCTick ohlcTick = iterator.next();
            maxPrice = Math.max(maxPrice, ohlcTick.m_high);
            minPrice = Math.min(minPrice, ohlcTick.m_low);
            if(counter++ > m_maxBars) {
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
        g.setColor(Color.WHITE);
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        for (Iterator<TresMaCalculator.MaTick> iterator = maTicks.descendingIterator(); iterator.hasNext(); ) {
            TresMaCalculator.MaTick maTick = iterator.next();
            double ma = maTick.m_ma;
            long barEnd = maTick.m_barEnd;
            int x = m_xTimeAxe.getPoint(barEnd);
            int y = yPriceAxe.getPointReverse(ma);
            if (lastX == Integer.MAX_VALUE) {
                g.fillRect(x - 1, y - 1, 3, 3);
            } else {
                g.drawLine(x, y, lastX, lastY);
            }
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

// candle style
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
        g.setColor(Color.darkGray);
        paintLine(g, 0.2);
        paintLine(g, 0.8);
        g.setColor(Color.CYAN);
        paintLine(g, 0);
        paintLine(g, 1);
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

        if(lastX == Integer.MAX_VALUE) {
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
