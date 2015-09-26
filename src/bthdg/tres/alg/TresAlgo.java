package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CciIndicator;
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
        } else if (algoName.equals("c+c")) {
            return new CncAlgo();
        }
        throw new RuntimeException("unsupported algo '" + algoName + "'");
    }

    public int paintYAxe(Graphics g, ChartAxe xTimeAxe, int right, ChartAxe yPriceAxe) {
        int width = 0;
        for (TresIndicator indicator : m_indicators) {
            int axeWidth = indicator.paintYAxe(g, xTimeAxe, right - width, yPriceAxe);
            width += axeWidth;
        }
        return width;
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

    public Double getDirection() { return null; } // [-1 ... 1]

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

        @Override public Double getDirection() { // [-1 ... 1]
            Direction direction = m_coppockIndicator.m_avgPeakCalculator.m_direction;
            return (direction == Direction.FORWARD) ? 1.0 : -1.0;
        }
    }

    public static class CncAlgo extends TresAlgo {
        public static final int CCI_CORRECTION_RATIO = 8000;

        final CoppockIndicator m_coppockIndicator;
        final CciIndicator m_cciIndicator;
        final AndIndicator m_andIndicator;

        public CncAlgo() {
            super("c+c");
            m_coppockIndicator = new CoppockIndicator(this) {
                @Override protected void onBar() {
                    super.onBar();
                    recalcAnd();
                }
            };
            m_indicators.add(m_coppockIndicator);
            m_cciIndicator = new CciIndicator(this) {
                @Override protected void onBar() {
                    super.onBar();
                    recalcAnd();
                }
            };
            m_indicators.add(m_cciIndicator);
            m_andIndicator = new AndIndicator(this);
            m_indicators.add(m_andIndicator);
        }

        private void recalcAnd() {
            ChartPoint coppock = m_coppockIndicator.getLastPoint();
            ChartPoint cci = m_cciIndicator.getLastPoint();
            if ((coppock != null) && (cci != null)) {
                double coppockValue = coppock.m_value;
                double cciValue = cci.m_value;
                double and = coppockValue + cciValue / CCI_CORRECTION_RATIO;
                long coppockMillis = coppock.m_millis;
                long cciMillis = cci.m_millis;
                long millis = Math.max(coppockMillis, cciMillis);
                ChartPoint chartPoint = new ChartPoint(millis, and);
                m_andIndicator.addBar(chartPoint);
            }
        }

        @Override public Double getDirection() { // [-1 ... 1]
            Direction direction = m_andIndicator.m_avgPeakCalculator.m_direction;
            return (direction == Direction.FORWARD) ? 1.0 : -1.0;
        }

        private class AndIndicator extends TresIndicator {
            private static final double PEAK_TOLERANCE = 0.015;

            public AndIndicator(TresAlgo algo) {
                super("+", PEAK_TOLERANCE, algo);
            }

            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Color.red; }
            @Override public Color getPeakColor() { return Color.red; }
        }
    }

    public interface TresAlgoListener {
        void onAlgoChanged();
    }
}
