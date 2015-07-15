package bthdg.tres;

import bthdg.ChartAxe;
import bthdg.Log;
import bthdg.exch.Exchange;
import bthdg.osc.OscTick;
import bthdg.util.Snoozer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

public class TresFrame extends JFrame implements Runnable {
    private final Tres m_tres;
    private final Snoozer m_snoozer;
    private final JLabel m_label;
    private final Canvas m_canvas;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public TresFrame(Tres tres) throws java.awt.HeadlessException {
        m_tres = tres;
        m_snoozer = new Snoozer(500) {
            protected void wakeUp() {
                updateUI();
            }
        };
        setTitle("Tres");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                m_snoozer.stop();
            }
        });
        JPanel panel1 = new JPanel(new BorderLayout());
        {
            panel1.setBackground(Color.BLACK);
            JPanel panel = new JPanel(new FlowLayout());
            {
                m_label = new JLabel("...");
                panel.add(m_label);
                panel.add(new JButton("BTN"));
            }
            panel1.add(panel, BorderLayout.NORTH);
            m_canvas = new TresCanvas(m_tres);
            panel1.add(m_canvas, BorderLayout.CENTER);
        }
        add(panel1);
        pack();
        toFront();
    }

    public void fireUpdated() { m_snoozer.update(); }

    public void stop() {
        m_snoozer.stop();
        dispose();
    }

    private void updateUI() {
        SwingUtilities.invokeLater(this);
    }

    @Override public void run() {
        m_label.setText(m_tres.getState());
        m_canvas.repaint();
    }

    private static class TresCanvas extends Canvas {
        private Tres m_tres;
        private Point m_point;
        private ChartAxe m_vAxe;

        private TresCanvas(Tres tres) {
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
            m_vAxe = new ChartAxe(0, 1, height);
        }

        @Override public void paint(Graphics g) {
            int width = getWidth();
            int height = getHeight();

            ArrayList<TresExchData> exchDatas = m_tres.m_exchDatas;
            TresExchData exchData = exchDatas.get(0);
            double lastPrice = exchData.m_lastPrice;
            if (lastPrice != 0) {
                Exchange exchange = exchData.m_ws.exchange();
                String lastStr = exchange.roundPriceStr(lastPrice, Tres.PAIR);
                int fontHeight = g.getFont().getSize();
                g.drawString("Last: " + lastStr, 1, fontHeight);

                TresOscCalculator oscCalculator = exchData.m_oscCalculators[0];
                OscTick fineTick = oscCalculator.m_lastFineTick;
                paintOsc(g, fineTick, width, Color.GRAY, 5);
                OscTick lastBar = oscCalculator.m_lastBar;
                paintOsc(g, lastBar, width, Color.WHITE, 15);

                int yOffset = 20;
                int count = 0;
                LinkedList<OscTick> bars = oscCalculator.m_oscBars;
                for( Iterator<OscTick> iterator = bars.descendingIterator(); iterator.hasNext(); ) {
                    OscTick tick = iterator.next();
                    paintOsc(g, tick, width, Color.RED, yOffset);
                    if(count++ > 30) {
                        break;
                    }
                    yOffset += 10;
                }
            }

            if (m_point != null) {
                int x = (int) m_point.getX();
                int y = (int) m_point.getY();
                g.setColor(Color.LIGHT_GRAY);
                g.drawLine(x, 0, x, height);
                g.drawLine(0, y, width, y);
            }
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
            int y = m_vAxe.getPoint(val);
            g.setColor(color);
            g.drawRect(width - offset - 2, y - 2, 5, 5);
        }
    }
}
