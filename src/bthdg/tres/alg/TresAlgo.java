package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.exch.TradeDataLight;
import bthdg.osc.BaseExecutor;
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
import java.util.Map;

public abstract class TresAlgo {
    public final String m_name;
    public final TresExchData m_tresExchData;
    public final List<TresIndicator> m_indicators = new ArrayList<TresIndicator>();
    public final List<TresIndicator> m_topIndicators = new ArrayList<TresIndicator>();
    private TresAlgoListener m_listener;

    public abstract double lastTickPrice();
    public abstract long lastTickTime();
    public abstract Color getColor();

    public double getDirectionAdjusted() { return 0; } // [-1 ... 1]
    public Direction getDirection() { return null; } // UP/DOWN
    public void preUpdate(TradeDataLight tdata) { /*noop*/ }
    public void postUpdate(TradeDataLight tdata) { /*noop*/ }
    public void preUpdate(BaseExecutor.TopDataPoint topDataPoint) { /*noop*/ }
    public void postUpdate(BaseExecutor.TopDataPoint topDataPoint) { /*noop*/ }

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
        } else if (algoName.equals("emas*")) {
            return new EmasAlgo.Boosted(tresExchData);
        } else if (algoName.equals("emas~*")) {
            return new EmasAlgo.WideBoosted(tresExchData);
        } else if (algoName.equals("emas~f")) {
            return new EmasAlgo.WideFast(tresExchData);
        } else if (algoName.equals("4ema")) {
            return new FourEmaAlgo(tresExchData);
        } else if (algoName.equals("combo")) {
            return new ComboAlgo(tresExchData);
        } else if (algoName.equals("ewo")) {
            return new EwoAlgo.Old(tresExchData);
        } else if (algoName.equals("ewoN")) {
            return new EwoAlgo.New(tresExchData);
        } else if (algoName.equals("fra")) {
            return new FractalAlgo(tresExchData);
        } else if (algoName.equals("cmf")) {
            return new CmfAlgo.Old(tresExchData);
        } else if (algoName.equals("cmfN")) {
            return new CmfAlgo.New(tresExchData);
        } else if (algoName.equals("sar")) {
            return new SarAlgo(tresExchData);
        } else if (algoName.equals("ewo_cmf")) {
            return new EwoCmfAlgo(tresExchData);
        } else if (algoName.equals("lrp")) {
            return new LinearRegressionPowerAlgo(tresExchData);
        } else if (algoName.equals("lrps")) {
            return new LinearRegressionPowerAlgo.Smoothed(tresExchData);
        } else if (algoName.equals("lrp*")) {
            return new LinearRegressionPowersAlgo.Gained(tresExchData);
        } else if (algoName.equals("lrp**")) {
            return new LinearRegressionPowersAlgo.Gained2(tresExchData);
        }
        throw new RuntimeException("unsupported algo '" + algoName + "'");
    }


    int paintYAxe(Graphics g, ChartAxe xTimeAxe, int right, Map<String,ChartAxe> yAxes) {
        int width = 0;
        for (TresIndicator indicator : m_indicators) {
            int axeWidth = indicator.paintYAxe(g, xTimeAxe, right - width, yAxes);
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
        if(peakWatcher==null) {
            return 0;
        }
        TrendWatcher<ChartPoint> peakCalculator = peakWatcher.m_avgPeakCalculator;
        Direction direction = peakCalculator.m_direction;
        if (direction == null) {
            return 0;
        }
        PeakWatcher halfPeakWatcher = indicator.m_halfPeakWatcher;
        TrendWatcher<ChartPoint> halfPeakTrendWatcher = (halfPeakWatcher == null) ? null : halfPeakWatcher.m_avgPeakCalculator;
        Direction halfDirection = (halfPeakTrendWatcher == null) ? null : halfPeakTrendWatcher.m_direction;
        if (direction == Direction.FORWARD) {
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

    public static double valueToBounds(double value, double boundTop, double boundBottom) { // [-1...1]
        double ret;
        if (value >= boundTop) {
            ret = 1.0;
        } else if (value < boundBottom) {
            ret = -1.0;
        } else if (boundTop == boundBottom) {
            ret = 0.0;
        } else {
            double val = (value - boundBottom) / (boundTop - boundBottom); // [0...1]
            ret = val * 2 - 1; // [-1...1]
        }
        return ret;
    }

    // ========================================================================================
    public interface TresAlgoListener {
        void onValueChange();
    }

    // ===============================================================================================================
    public static class ValueIndicator extends TresIndicator {
        private final Color m_color;

        ValueIndicator(TresAlgo algo, String name, Color color) {
            this(algo, name, 0, color);
        }
        ValueIndicator(TresAlgo algo, String name, double peakTolerance, Color color) {
            super(name, peakTolerance, algo);
            m_color = color;
        }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return m_color; }
        @Override protected boolean useValueAxe() { return true; }
        @Override protected boolean drawZeroLine() { return true; }
    }
}
