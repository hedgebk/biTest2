package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.ind.TresIndicator;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TresAlgo {
    public final String m_name;
    public final List<TresIndicator> m_indicators = new ArrayList<TresIndicator>();
    private TresAlgoListener m_listener;

    public TresAlgo(String name) {
        m_name = name;
    }

    public void setListener(TresAlgoListener listener) { m_listener = listener; }

    public static TresAlgo get(String algoName) {
        if (algoName.equals("coppock")) {
            return new CoppockAlgo();
        }
        throw new RuntimeException("unsupported algo '" + algoName + "'");
    }

    public void paintAlgo(Graphics g, TresExchData exchData, ChartAxe xTimeAxe, ChartAxe yPriceAxe) {
        for (TresIndicator indicator : m_indicators) {
            indicator.paint(g, xTimeAxe, yPriceAxe);
        }
    }

    public void onAvgPeak(TresIndicator indicator) {
        notifyAlgoChanged();
    }

    protected void notifyAlgoChanged() {
        if (m_listener != null) {
            m_listener.onAlgoChanged();
        }
    }

    public double getDirection() { return 0; } // [-1 ... 1]

    public JComponent getController(TresCanvas canvas) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 1));
        panel.setBorder(BorderFactory.createLineBorder(Color.black));
        for (TresIndicator indicator : m_indicators) {
            panel.add(indicator.getController(canvas));
        }
        return panel;
    }

    public static class CoppockAlgo extends TresAlgo {
        final CoppockIndicator m_coppockIndicator;

        public CoppockAlgo() {
            super("Coppock");
            m_coppockIndicator = new CoppockIndicator(this);
            m_indicators.add(m_coppockIndicator);
        }

        @Override public void onAvgPeak(TresIndicator indicator) {
            //Direction direction = m_coppockIndicator.m_avgPeakCalculator.m_direction;
            notifyAlgoChanged();
        }

        @Override public double getDirection() { // [-1 ... 1]
            Direction direction = m_coppockIndicator.m_avgPeakCalculator.m_direction;
            return (direction == Direction.FORWARD) ? 1 : -1;
        }
    }

    public interface TresAlgoListener {
        void onAlgoChanged();
    }
}
