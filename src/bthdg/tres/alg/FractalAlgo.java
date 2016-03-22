package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.osc.BaseExecutor;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CursorPainter;
import bthdg.tres.ind.FractalIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.Colors;

import java.awt.*;

public class FractalAlgo extends TresAlgo {
    public static double TEMA_START = 0.5;
    public static double TEMA_STEP = 0.2;
    public static double VELOCITY_SIZE = 0.2;

    private final FractalIndicator m_fractalIndicator;
//    private final TripleEmaIndicator m_tema1;
//    private final TripleEmaIndicator m_tema2;
//    private final TripleEmaIndicator m_tema3;
//    private final TripleEmaIndicator m_tema4;
//    private final TripleEmaIndicator m_tema5;
//    private final VelocityIndicator m_velocityIndicator;
    private final TresIndicator m_bidAskMidIndicator;
    private final CursorPainter m_cursorPainter;

    public FractalAlgo(TresExchData tresExchData) {
        super("FRA", tresExchData);
        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        m_fractalIndicator = new FractalIndicator(this) {
            @Override protected void onPhaseDirectionChanged() { }

            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                notifyListener();
            }
        };
        m_indicators.add(m_fractalIndicator);


        m_bidAskMidIndicator = new TresIndicator( "L", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.SKY; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean usePriceAxe() { return true; }
            @Override protected boolean drawZeroLine() { return true; }
        };
        m_indicators.add(m_bidAskMidIndicator);

        m_cursorPainter = new CursorPainter(m_bidAskMidIndicator, barSizeMillis / 6, 4);
    }

    @Override public void postUpdate(BaseExecutor.TopDataPoint topDataPoint) {
        ChartPoint point = new ChartPoint(topDataPoint.m_timestamp, topDataPoint.getAvgMid());
        m_bidAskMidIndicator.addBar(point);
    }

    @Override public void paintAlgo(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        super.paintAlgo(g, xTimeAxe, yPriceAxe, cursorPoint);
        g.setColor(Color.WHITE);
        m_cursorPainter.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
    }

    @Override public double lastTickPrice() { return m_fractalIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_fractalIndicator.lastTickTime(); }
    @Override public Color getColor() { return Color.BLUE; }
    @Override public String getRunAlgoParams() { return "FRA bar=" + m_tresExchData.m_tres.m_barSizeMillis; }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        return m_fractalIndicator.getDirectionAdjusted();
    }
    @Override public Direction getDirection() { return Direction.get(getDirectionAdjusted()); } // UP/DOWN
}
