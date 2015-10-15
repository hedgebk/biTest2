package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.tres.PhaseData;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.TresIndicator;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class TresAlgo {
    public final String m_name;
    public final TresExchData m_tresExchData;
    public final List<TresIndicator> m_indicators = new ArrayList<TresIndicator>();
    private TresAlgoListener m_listener;

    public abstract double lastTickPrice();
    public abstract long lastTickTime();

    public TresAlgo(String name, TresExchData tresExchData) {
        m_name = name;
        m_tresExchData = tresExchData;
    }

    public void setListener(TresAlgoListener listener) { m_listener = listener; }

    public static TresAlgo get(String algoName, TresExchData tresExchData) {
        if (algoName.equals("coppock")) {
            return new CoppockAlgo(tresExchData);
        } else if (algoName.equals("cci")) {
            return new CciAlgo(tresExchData);
        } else if (algoName.equals("c+c")) {
            return new CncAlgo(tresExchData);
        } else if (algoName.equals("osc")) {
            return new OscAlgo(tresExchData);
        }
        throw new RuntimeException("unsupported algo '" + algoName + "'");
    }

    public static class OscAlgo extends TresAlgo {
        final OscIndicator m_oscIndicator;

        public OscAlgo(TresExchData exchData) {
            super("OSC", exchData);
            exchData.m_oscAlgo = this;

            m_oscIndicator = new OscIndicator(this);
            m_indicators.add(m_oscIndicator);
        }

        @Override public double lastTickPrice() { return m_tresExchData.m_lastPrice; }
        @Override public long lastTickTime() { return m_tresExchData.m_lastTickMillis; }

        @Override public double getDirectionAdjusted() { // [-1 ... 1]
            double directionAdjusted = 0;
            PhaseData[] phaseDatas = m_tresExchData.m_phaseDatas;
            for (PhaseData phaseData : phaseDatas) {
                double direction = phaseData.getDirection();
                directionAdjusted += direction;
            }
            return directionAdjusted/phaseDatas.length;
        }

        @Override public Direction getDirection() { return m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN

        private static class OscIndicator extends TresIndicator {
            private static final double PEAK_TOLERANCE = 0.1;

            public OscIndicator(OscAlgo oscAlgo) {
                super("osc", PEAK_TOLERANCE, oscAlgo);
            }

            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }

            @Override public Color getColor() { return Color.yellow; }
            @Override public Color getPeakColor() { return Color.yellow; }
        }
    }


    public int paintYAxe(Graphics g, ChartAxe xTimeAxe, int right, ChartAxe yPriceAxe) {
        int width = 0;
        for (TresIndicator indicator : m_indicators) {
            int axeWidth = indicator.paintYAxe(g, xTimeAxe, right - width, yPriceAxe);
            width += axeWidth;
        }
        return width;
    }

    public void paintAlgo(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe) {
        for (TresIndicator indicator : m_indicators) {
            indicator.paint(g, xTimeAxe, yPriceAxe);
        }
    }

    public void onAvgPeak(TresIndicator indicator) {
        notifyValueChange();
    }

    public void notifyValueChange() {
        if (m_listener != null) {
            m_listener.onValueChange();
        }
    }

    public double getDirectionAdjusted() { return 0; } // [-1 ... 1]
    public Direction getDirection() { return null; } // UP/DOWN

    public JComponent getController(TresCanvas canvas) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 0));
        panel.setBorder(BorderFactory.createLineBorder(Color.black));
        for (TresIndicator indicator : m_indicators) {
            panel.add(indicator.getController(canvas));
        }
        return panel;
    }

    public String getRunAlgoParams() {
        return "";
    }

    public interface TresAlgoListener {
        void onValueChange();
    }
}
