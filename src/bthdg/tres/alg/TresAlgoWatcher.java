package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.Log;
import bthdg.exch.Direction;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;
import bthdg.util.Colors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class TresAlgoWatcher implements TresAlgo.TresAlgoListener {
    private final TresExchData m_tresExchData;
    public final TresAlgo m_algo;
    final public LinkedList<AlgoWatcherPoint> m_points = new LinkedList<AlgoWatcherPoint>();
    private Direction m_lastDirection;
    private Double m_lastPeakPrice;
    public double m_totalPriceRatio = 1.0;
    private boolean m_doPaint = false;
    private List<AlgoWatcherPoint> m_paintPoints = new ArrayList<AlgoWatcherPoint>();

    private static void log(String s) { Log.log(s); }

    public TresAlgoWatcher(TresExchData tresExchData, TresAlgo algo) {
        m_tresExchData = tresExchData;
        m_algo = algo;
        algo.setListener(this);
    }

    public int paintYAxe(Graphics g, ChartAxe xTimeAxe, int yRight, ChartAxe yPriceAxe) {
        return m_algo.paintYAxe(g, xTimeAxe, yRight, yPriceAxe);
    }

    public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        m_algo.paintAlgo(g, xTimeAxe, yPriceAxe, cursorPoint);

        if (m_doPaint) {
            clonePoints(xTimeAxe);
            paintPoints(g, xTimeAxe, yPriceAxe);
        }
    }

    private void clonePoints(ChartAxe xTimeAxe) {
        double minTime = xTimeAxe.m_min;
        double maxTime = xTimeAxe.m_max;
        m_paintPoints.clear();
        AlgoWatcherPoint rightPlusPoint = null; // paint one extra point at right side
        synchronized (m_points) {
            for (Iterator<AlgoWatcherPoint> it = m_points.descendingIterator(); it.hasNext(); ) {
                AlgoWatcherPoint point = it.next();
                long timestamp = point.m_millis;
                if (timestamp > maxTime) {
                    rightPlusPoint = point;
                    continue;
                }
                if (rightPlusPoint != null) {
                    m_paintPoints.add(rightPlusPoint);
                    rightPlusPoint = null;
                }
                m_paintPoints.add(point);
                if (timestamp < minTime) { break; }
            }
        }
    }

    private void paintPoints(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe) {
        int fontHeight = g.getFont().getSize();
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
        AlgoWatcherPoint lastPoint = null;
        for (AlgoWatcherPoint point : m_paintPoints) {
            long millis = point.m_millis;
            int x = xTimeAxe.getPoint(millis);
            double price = point.m_price;
            int y = yPriceAxe.getPointReverse(price);
            if (lastX != Integer.MAX_VALUE) {
                g.setColor((lastPoint.m_priceRatio > 1) ? Color.GREEN : Color.RED);
                g.drawLine(lastX, lastY, x, y);
            } else {
                g.setColor(Colors.BEGIE);
                g.drawRect(x - 1, y - 1, 2, 2);
            }

            g.drawString(String.format("p: %1$,.2f", price), x, y + fontHeight);

            double priceRatio = point.m_priceRatio;
            g.setColor((priceRatio > 1) ? Color.GREEN : Color.RED);
            g.drawString(String.format("r: %1$,.5f", priceRatio), x, y + fontHeight * 2);

            double totalPriceRatio = point.m_totalPriceRatio;
            g.setColor((totalPriceRatio > 1) ? Color.GREEN : Color.RED);
            g.drawString(String.format("t: %1$,.5f", totalPriceRatio), x, y + fontHeight * 3);

            lastPoint = point;
            lastX = x;
            lastY = y;
        }
    }

    // significant value change - in most cases: peak detected
    @Override public void onValueChange() {
        Direction direction = m_algo.getDirection(); // UP/DOWN
        if (direction == null) { // undefined direction
            return; // no trade
        }
        double lastPrice = m_algo.lastTickPrice();
//log("onValueChange: direction=" + direction + "; lastPeakPrice=" + m_lastPeakPrice + "; lastPrice=" + lastPrice);

        if (m_lastDirection != direction) { // direction changed
            if (m_lastDirection != null) {
                double priceRatio;
                if (direction == Direction.FORWARD) { // up
                    priceRatio = m_lastPeakPrice / lastPrice;
                } else {
                    priceRatio = lastPrice / m_lastPeakPrice;
                }
                m_totalPriceRatio *= priceRatio;
//log(" priceRatio=" + priceRatio + "; m_totalPriceRatio=" + m_totalPriceRatio);

                if (m_tresExchData.m_tres.m_collectPoints) {
                    long lastTickTime = m_algo.lastTickTime();
                    AlgoWatcherPoint data = new AlgoWatcherPoint(lastTickTime, lastPrice, priceRatio, m_totalPriceRatio);
                    synchronized (m_points) {
                        m_points.add(data);
                    }
                }
            }
            m_lastDirection = direction;
            m_lastPeakPrice = lastPrice;
//        } else {
//            AlgoWatcherPoint data = new AlgoWatcherPoint(m_tresExchData.m_lastTickMillis, lastPrice, 0, m_totalPriceRatio);
//            synchronized (m_points) {
//                m_points.add(data);
//            }
        }
    }

    public JComponent getController(final TresCanvas canvas) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 0));
        panel.setBorder(BorderFactory.createLineBorder(Color.black));
        panel.add(new JCheckBox(m_algo.m_name, m_doPaint) {
            @Override protected void fireItemStateChanged(ItemEvent event) {
                super.fireItemStateChanged(event);
                m_doPaint = (event.getStateChange() == ItemEvent.SELECTED);
                canvas.repaint();
            }
        });
        panel.add(m_algo.getController(canvas));
        return panel;
    }

    public static class AlgoWatcherPoint {
        public final long m_millis;
        public final double m_price;
        public final double m_priceRatio;
        public final double m_totalPriceRatio;

        public AlgoWatcherPoint(long millis, double price, double priceRatio, double totalPriceRatio) {
            m_millis = millis;
            m_price = price;
            m_priceRatio = priceRatio;
            m_totalPriceRatio = totalPriceRatio;
        }
    }
}
