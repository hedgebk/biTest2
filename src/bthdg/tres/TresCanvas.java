package bthdg.tres;

import bthdg.ChartAxe;
import bthdg.exch.Exchange;
import bthdg.osc.OscTick;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

public class TresCanvas extends Canvas {
    private Tres m_tres;
    private Point m_point;
    private ChartAxe m_yAxe;
    private ChartAxe m_xTimeAxe;

    TresCanvas(Tres tres) {
        m_tres = tres;
        setMinimumSize(new Dimension(500, 200));
        setPreferredSize(new Dimension(500, 200));
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
        m_yAxe = new ChartAxe(0, 1, height);
    }

    @Override public void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        long barSize = m_tres.m_barSizeMillis;

        if (m_point != null) {
            paintbarHighlight(g, barSize);
        }

        ArrayList<TresExchData> exchDatas = m_tres.m_exchDatas;
        TresExchData exchData = exchDatas.get(0);
        double lastPrice = exchData.m_lastPrice;
        if (lastPrice != 0) {
            Exchange exchange = exchData.m_ws.exchange();
            String lastStr = exchange.roundPriceStr(lastPrice, Tres.PAIR);
            int fontHeight = g.getFont().getSize();
            g.drawString("Last: " + lastStr, 1, fontHeight);

            PhaseData phaseData = exchData.m_phaseDatas[0];

            TresOscCalculator oscCalculator = phaseData.m_oscCalculator;
            OscTick fineTick = oscCalculator.m_lastFineTick;
            paintOsc(g, fineTick, width, Color.GRAY, 5);

            LinkedList<OHLCTick> ohlcTicks = phaseData.m_ohlcTicks;
            LinkedList<OscTick> bars = oscCalculator.m_oscBars;

            calcXTimeAxe(width, barSize, ohlcTicks, bars);

            ChartAxe yPriceAxe = calcYPriceAxe(height, lastPrice, ohlcTicks);

            paintOHLCTicks(g, ohlcTicks, yPriceAxe);
            paintOscTicks(g, bars);

            int lastPriceY = yPriceAxe.getPointReverse(lastPrice);
            g.setColor(Color.BLUE);
            g.fillRect(width - 7, lastPriceY - 1, 5, 3);
        }

        if (m_point != null) {
            paintCross(g, width, height);
        }
    }

    private void paintbarHighlight(Graphics g, long barSize) {
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

        int maxBarNum = width / 10;
        long minTime = maxTime - barSize * maxBarNum;

        m_xTimeAxe = new ChartAxe(minTime, maxTime, width);
        m_xTimeAxe.m_offset = -25;
    }

    private ChartAxe calcYPriceAxe(int height, double lastPrice, LinkedList<OHLCTick> ohlcTicks) {
        double maxPrice = lastPrice;
        double minPrice = lastPrice;
        for (Iterator<OHLCTick> iterator = ohlcTicks.descendingIterator(); iterator.hasNext(); ) {
            OHLCTick ohlcTick = iterator.next();
            maxPrice = Math.max(maxPrice, ohlcTick.m_high);
            minPrice = Math.min(minPrice, ohlcTick.m_low);
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
            int barHeight = closeY - openY;
            if (barHeight == 0) {
                barHeight = 1;
            }
            g.fillRect(startX + 1, openY, endX - startX - 2, barHeight);
            if (startX < 0) {
                break;
            }
        }
    }

    private void paintOscTicks(Graphics g, LinkedList<OscTick> bars) {
        for (Iterator<OscTick> iterator = bars.descendingIterator(); iterator.hasNext(); ) {
            OscTick tick = iterator.next();
            int endX = paintOsc(g, tick, m_xTimeAxe, Color.RED);
            if (endX < 0) {
                break;
            }
        }
    }

    private int paintOsc(Graphics g, OscTick tick, ChartAxe xAxe, Color color) {
        long startTime = tick.m_startTime;
        long endTime = startTime + m_tres.m_barSizeMillis;
//            int startX = xAxe.getPoint(startTime);
        int endX = xAxe.getPoint(endTime);

        double val1 = tick.m_val1;
        double val2 = tick.m_val2;
        int y1 = m_yAxe.getPointReverse(val1);
        int y2 = m_yAxe.getPointReverse(val2);

        g.setColor(color);
        g.drawRect(endX - 2, y1 - 2, 5, 5);
        g.drawRect(endX - 2, y2 - 2, 5, 5);
        return endX;
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
