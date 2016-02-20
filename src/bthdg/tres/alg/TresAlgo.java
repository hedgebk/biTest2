package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.exch.TradeDataLight;
import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresCanvas;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.PeakWatcher;
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
    public abstract Color getColor();

    public double getDirectionAdjusted() { return 0; } // [-1 ... 1]
    public Direction getDirection() { return null; } // UP/DOWN
    public void onTrade(TradeDataLight tdata) { /*noop*/ }

    public TresAlgo(String name, TresExchData tresExchData) {
        m_name = name;
        m_tresExchData = tresExchData;
    }

    public void setListener(TresAlgoListener listener) { m_listener = listener; }

    public static TresAlgo get(String algoName, TresExchData tresExchData) {
        if (algoName.equals("coppock")) {
            return new CoppockAlgo(tresExchData);
        } else if (algoName.equals("cov")) {
            return new CoppockVelocityAlgo(tresExchData);
        } else if (algoName.equals("cci")) {
            return new CciAlgo(tresExchData);
        } else if (algoName.equals("c+c")) {
            return new CncAlgo(tresExchData);
        } else if (algoName.equals("c+o")) {
            return new CnoAlgo(tresExchData);
        } else if (algoName.equals("c+o2")) {
            return new Cno2Algo(tresExchData);
        } else if (algoName.equals("c+o3")) {
            return new Cno3Algo(tresExchData);
        } else if (algoName.equals("c+o3!")) {
            return new Cno3Algo.Cno3SharpAlgo(tresExchData);
        } else if (algoName.equals("c+o3f")) {
            return new Cno3Algo.Cno3FastAlgo(tresExchData);
        } else if (algoName.equals("osc")) {
            return new OscAlgo(tresExchData);
        } else if (algoName.equals("tre")) {
            return new TreAlgo.TreAlgoBlended(tresExchData);
        } else if (algoName.equals("tre!")) {
            return new TreAlgo.TreAlgoSharp(tresExchData);
        } else if (algoName.equals("cop+")) {
            return new CoppockPlusAlgo(tresExchData);
        } else if (algoName.equals("aro")) {
            return new AroonAlgo(tresExchData);
        } else if (algoName.equals("aro+")) {
            return new AroonAlgo.AroonFasterAlgo(tresExchData);
        } else if (algoName.equals("aro2")) {
            return new Aroon2Algo(tresExchData);
        } else if (algoName.equals("aro2!")) {
            return new Aroon2Algo.Aroon2SharpAlgo(tresExchData);
        } else if (algoName.equals("mmar")) {
            return new MmarAlgo(tresExchData);
        } else if (algoName.equals("emas")) {
            return new EmasAlgo(tresExchData);
        } else if (algoName.equals("emas~")) {
            return new EmasAlgo.Wide(tresExchData);
        }
        throw new RuntimeException("unsupported algo '" + algoName + "'");
    }


    public int paintYAxe(Graphics g, ChartAxe xTimeAxe, int right, ChartAxe yPriceAxe, ChartAxe yValueAxe) {
        int width = 0;
        for (TresIndicator indicator : m_indicators) {
            int axeWidth = indicator.paintYAxe(g, xTimeAxe, right - width, yPriceAxe, yValueAxe);
            width += axeWidth;
        }
        return width;
    }

    public void paintAlgo(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        for (TresIndicator indicator : m_indicators) {
            indicator.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
        }
    }

    public void onAvgPeak(TresIndicator indicator, TrendWatcher<ChartPoint> trendWatcher) {
        notifyListener();
    }

    public void notifyListener() {
        if (m_listener != null) {
            m_listener.onValueChange();
        }
    }

    public JComponent getController(TresCanvas canvas) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
//        panel.setBorder(BorderFactory.createLineBorder(Color.black));
        for (TresIndicator indicator : m_indicators) {
            JComponent controller = indicator.getController(canvas);
//            controller.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, Color.black));
            panel.add(controller);
        }
        return panel;
    }

    public String getRunAlgoParams() {
        return "";
    }

    protected static double getDirectionAdjustedByPeakWatchers(TresIndicator indicator) {
        PeakWatcher peakWatcher = indicator.m_peakWatcher;
        TrendWatcher<ChartPoint> peakCalculator = peakWatcher.m_avgPeakCalculator;
        Direction direction = peakCalculator.m_direction;
        PeakWatcher halfPeakWatcher = indicator.m_halfPeakWatcher;
        TrendWatcher<ChartPoint> halfPeakTrendWatcher = halfPeakWatcher.m_avgPeakCalculator;
        Direction halfDirection = halfPeakTrendWatcher.m_direction;
        if (direction == null) {
            return 0;
        } else if (direction == Direction.FORWARD) {
            if (halfDirection == null) {
                return 1.0;
            } else if (halfDirection == Direction.FORWARD) {
                return 1.0;
            } else { // Direction.BACKWARD
                double force = halfPeakTrendWatcher.getDirectionForce();
                double dirAdjusted = -2 * force + 3;
                dirAdjusted = Math.max(dirAdjusted, -1);
                dirAdjusted = Math.min(dirAdjusted, 1);
                return dirAdjusted;
            }
        } else { // Direction.BACKWARD
            if (halfDirection == null) {
                return -1.0;
            } else if (halfDirection == Direction.BACKWARD) {
                return -1.0;
            } else { // Direction.FORWARD
                double force = halfPeakTrendWatcher.getDirectionForce();
                double dirAdjusted = 2 * force - 3;
                dirAdjusted = Math.max(dirAdjusted, -1);
                dirAdjusted = Math.min(dirAdjusted, 1);
                return dirAdjusted;
            }
        }
    }

    // ========================================================================================
    public interface TresAlgoListener {
        void onValueChange();
    }
}
