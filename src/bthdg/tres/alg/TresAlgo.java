package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CciIndicator;
import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TresAlgo {
    public final String m_name;
    public final TresExchData m_tresExchData;
    public final List<TresIndicator> m_indicators = new ArrayList<TresIndicator>();
    private TresAlgoListener m_listener;

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
            return tresExchData.getOscAlgo();
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

    public static class CoppockAlgo extends TresAlgo {
        final CoppockIndicator m_coppockIndicator;

        public CoppockAlgo(TresExchData tresExchData) {
            super("COPPOCK", tresExchData);
            m_coppockIndicator = new CoppockIndicator(this);
            m_indicators.add(m_coppockIndicator);
        }

        @Override public double getDirectionAdjusted() { return m_coppockIndicator.getDirectionAdjusted(); } // [-1 ... 1]
        @Override public Direction getDirection() { return m_coppockIndicator.m_avgPeakCalculator.m_direction; } // UP/DOWN

        @Override public String getRunAlgoParams() {
            return "COPPOCK.tolerance=" + m_coppockIndicator.m_avgPeakCalculator.m_tolerance;
        }
    }

    public static class CciAlgo extends TresAlgo {
        final CciIndicator m_cciIndicator;

        public CciAlgo(TresExchData tresExchData) {
            super("CCI", tresExchData);
            m_cciIndicator = new CciIndicator(this);
            m_indicators.add(m_cciIndicator);
        }

        @Override public double getDirectionAdjusted() { return m_cciIndicator.getDirectionAdjusted(); } // [-1 ... 1]
        @Override public Direction getDirection() { return m_cciIndicator.m_avgPeakCalculator.m_direction; } // UP/DOWN

        @Override public String getRunAlgoParams() {
            return "CCI.tolerance=" + m_cciIndicator.m_avgPeakCalculator.m_tolerance;
        }
    }

    public static class CncAlgo extends TresAlgo {
        public static double CCI_CORRECTION_RATIO = 7408;

        final CoppockIndicator m_coppockIndicator;
        final CciIndicator m_cciIndicator;
        final AndIndicator m_andIndicator;

        public CncAlgo(TresExchData tresExchData) {
            super("C+C", tresExchData);
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

        @Override public double getDirectionAdjusted() { // [-1 ... 1]
            Direction direction = m_andIndicator.m_avgPeakCalculator.m_direction;
            return (direction == null) ? 0 : ((direction == Direction.FORWARD) ? 1.0 : -1.0);
        }
        @Override public Direction getDirection() { return m_andIndicator.m_avgPeakCalculator.m_direction; } // UP/DOWN

        public static class AndIndicator extends TresIndicator {
            public static double PEAK_TOLERANCE = 0.06470;

            public AndIndicator(TresAlgo algo) {
                super("+", PEAK_TOLERANCE, algo);
            }

            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Color.red; }
            @Override public Color getPeakColor() { return Color.red; }
            @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
                double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
                minMaxCalculator.m_minValue = -max;
                minMaxCalculator.m_maxValue = max;
            }
        }
    }

    public interface TresAlgoListener {
        void onValueChange();
    }
}
