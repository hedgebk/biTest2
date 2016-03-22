package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

public abstract class BaseAlgoWatcher implements TresAlgo.TresAlgoListener {
    public static double AVG_HALF_BID_ASK_DIF = 0.10;

    protected final TresExchData m_tresExchData;
    public final TresAlgo m_algo;
    private TresAlgo.TresAlgoListener m_listener;
    protected boolean m_doPaint = false;

    public void setListener(TresAlgo.TresAlgoListener listener) { m_listener = listener; }

    public BaseAlgoWatcher(TresExchData tresExchData, TresAlgo algo) {
        m_tresExchData = tresExchData;
        m_algo = algo;
        algo.setListener(this);
    }

    // significant value change - in most cases: peak detected
    @Override public void onValueChange() {
        if (m_listener != null) { // notify runAlgoFirst
            m_listener.onValueChange();
        }
    }

    public double getDirectionAdjusted() { return m_algo.getDirectionAdjusted(); } // proxy call
    public String getRunAlgoParams() {
        return m_algo.getRunAlgoParams();
    } // proxy call

    public int paintYAxe(Graphics g, ChartAxe xTimeAxe, int yRight, ChartAxe yPriceAxe, ChartAxe yValueAxe) {
        return m_algo.paintYAxe(g, xTimeAxe, yRight, yPriceAxe, yValueAxe);
    }

    public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        m_algo.paintAlgo(g, xTimeAxe, yPriceAxe, cursorPoint);
    }

    public JComponent getController(final TresCanvas canvas) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 0));
        Color color = m_algo.getColor();
        panel.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, color));
        panel.setBackground(color);
        JCheckBox checkBox = new JCheckBox(m_algo.m_name, m_doPaint) {
            @Override protected void fireItemStateChanged(ItemEvent event) {
                super.fireItemStateChanged(event);
                m_doPaint = (event.getStateChange() == ItemEvent.SELECTED);
                canvas.repaint();
            }
        };
        checkBox.setOpaque(false);
        panel.add(checkBox);
        panel.add(m_algo.getController(canvas));
        return panel;
    }

    public abstract double totalPriceRatio();
}
