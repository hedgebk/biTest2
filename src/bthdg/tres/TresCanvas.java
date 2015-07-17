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
    private ChartAxe m_xAxe;

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
            int x = (int) m_point.getX();
            int y = (int) m_point.getY();
            long millis = (long) m_xAxe.getValueFromPoint(x);
            long barStart = millis / barSize * barSize;
            int barStartX = m_xAxe.getPoint(barStart);
            long barEnd = barStart + barSize;
            int barEndX = m_xAxe.getPoint(barEnd);
            g.setColor(Color.DARK_GRAY);
            g.fillRect(barStartX, 0, barEndX - barStartX, getWidth());
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
//                OscTick lastBar = oscCalculator.m_lastBar;
//                paintOsc(g, lastBar, width, Color.WHITE, 15);

            long maxTime = 0;
            LinkedList<OHLCTick> ohlcTicks = phaseData.m_ohlcTicks;
            OHLCTick lastOhlcTick = ohlcTicks.peekLast();
            if (lastOhlcTick != null) {
                maxTime = lastOhlcTick.m_barStart;
            }
            LinkedList<OscTick> bars = oscCalculator.m_oscBars;
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

            m_xAxe = new ChartAxe(minTime, maxTime, width);
            m_xAxe.m_offset = -25;

            double maxPrice = lastPrice;
            double minPrice = lastPrice;
            for (Iterator<OHLCTick> iterator = ohlcTicks.descendingIterator(); iterator.hasNext(); ) {
                OHLCTick ohlcTick = iterator.next();
                maxPrice = Math.max(maxPrice, ohlcTick.m_high);
                minPrice = Math.min(minPrice, ohlcTick.m_low);
            }
            ChartAxe yAxe = new ChartAxe(minPrice, maxPrice, height - 2);
            yAxe.m_offset = 1;

            g.setColor(Color.GREEN);
            for (Iterator<OHLCTick> iterator = ohlcTicks.descendingIterator(); iterator.hasNext(); ) {
                OHLCTick ohlcTick = iterator.next();
                long barStart = ohlcTick.m_barStart;
                int startX = m_xAxe.getPoint(barStart);
                long barEnd = ohlcTick.m_barEnd;
                int endX = m_xAxe.getPoint(barEnd);
                int midX = (startX + endX) / 2;

                int highY = yAxe.getPointReverse(ohlcTick.m_high);
                int lowY = yAxe.getPointReverse(ohlcTick.m_low);
                int openY = yAxe.getPointReverse(ohlcTick.m_open);
                int closeY = yAxe.getPointReverse(ohlcTick.m_close);
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
            for (Iterator<OscTick> iterator = bars.descendingIterator(); iterator.hasNext(); ) {
                OscTick tick = iterator.next();
                int endX = paintOsc(g, tick, m_xAxe, Color.RED);
                if (endX < 0) {
                    break;
                }
            }

            int lastPriceY = yAxe.getPointReverse(lastPrice);
            g.setColor(Color.BLUE);
            g.fillRect(width - 7, lastPriceY - 1, 5, 3);
        }

        if (m_point != null) {
            int x = (int) m_point.getX();
            int y = (int) m_point.getY();

            g.setColor(Color.LIGHT_GRAY);
            g.drawLine(x, 0, x, height);
            g.drawLine(0, y, width, y);
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
