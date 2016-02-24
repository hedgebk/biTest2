package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.EmaIndicator;
import bthdg.tres.ind.SmoochedIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.tres.ind.TripleEmaIndicator;
import bthdg.util.Colors;

import java.awt.*;

public class EmasAlgo extends TresAlgo {
    public static double TEMA_FAST_SIZE = 1.8;
    public static double TEMA_START = 10;
    public static double TEMA_STEP = 5;
    private static final double BOUND_SMOOCH_RATE = 7.0;
    public static double BOUND_LEVEL = 0.3;  // 0.4;
    public static double EMA_SIZE = 1.7; // 0.77;

    protected EmaIndicator m_ema;
    protected TripleEmaIndicator m_tripleEma1;
    protected TripleEmaIndicator m_tripleEma10;
    protected TripleEmaIndicator m_tripleEma15;
    protected TripleEmaIndicator m_tripleEma20;
    protected TripleEmaIndicator m_tripleEma25;
    protected final TresIndicator m_oneIndicator;
    private final TresIndicator m_twoIndicator;
    private final TresIndicator m_sumIndicator;
    private final TresIndicator m_spreadIndicator;
    private final SmoochedIndicator m_smoochedSpreadIndicator;
    protected Double m_one;
    private Double m_two;
    private Double m_sum;
    private Double m_spread;
    private Double m_smoochedSpread;

    public EmasAlgo(TresExchData tresExchData) {
        super("EMAS", tresExchData);
        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        m_ema = new EmaIndicator("ema", this, EMA_SIZE) {
//            @Override public void addBar(ChartPoint chartPoint) {
//                super.addBar(chartPoint);
//                recalcOne();
//                recalcSum();
//            }
        };
        m_indicators.add(m_ema);

        m_tripleEma1 = new TripleEmaIndicator("te", this, TEMA_FAST_SIZE, Color.gray) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcOne();
                recalcTwo();
                recalcSum();
            }
        };
        m_indicators.add(m_tripleEma1);

        double emaSize = TEMA_START;
        m_tripleEma10 = new TripleEmaIndicator("te1", this, emaSize, Color.magenta) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcOne();
                recalcTwo();
                recalcSum();
            }
        };
        m_indicators.add(m_tripleEma10);

        emaSize = TEMA_START + TEMA_STEP;
        m_tripleEma15 = new TripleEmaIndicator("te2", this, emaSize, Color.PINK) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcOne();
                recalcTwo();
                recalcSum();
            }
        };
        m_indicators.add(m_tripleEma15);

        emaSize = TEMA_START + TEMA_STEP * 2;
        m_tripleEma20 = new TripleEmaIndicator("te3", this, emaSize, Color.orange) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcOne();
                recalcTwo();
                recalcSum();
            }
        };
        m_indicators.add(m_tripleEma20);

        emaSize = TEMA_START + TEMA_STEP * 3;
        m_tripleEma25 = new TripleEmaIndicator("te4", this, emaSize, Color.BLUE) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcOne();
                recalcTwo();
                recalcSum();
            }
        };
        m_indicators.add(m_tripleEma25);

        m_spreadIndicator = new TresIndicator( "[]", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.DARK_GREEN; }
            @Override protected boolean countPeaks() { return false; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_smoochedSpreadIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_spreadIndicator);

        m_smoochedSpreadIndicator = new SmoochedIndicator(this, "[]s", (long) (BOUND_SMOOCH_RATE * barSizeMillis), 0) {
            @Override protected boolean countPeaks() { return false; }
            @Override public Color getColor() { return Color.CYAN; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if(lastPoint != null) {
                    m_smoochedSpread = lastPoint.m_value;
                }
            }
        };
        m_indicators.add(m_smoochedSpreadIndicator);

        m_oneIndicator = new TresIndicator( "1", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.SKY; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean useValueAxe() { return true; }
        };
        m_indicators.add(m_oneIndicator);

        m_twoIndicator = new TresIndicator( "2", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.LIGHT_BLUE; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean useValueAxe() { return true; }
        };
        m_indicators.add(m_twoIndicator);

        m_sumIndicator = new TresIndicator( "s", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.BEGIE; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean useValueAxe() { return true; }
            @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) { drawZeroHLine(g, xTimeAxe, yAxe); }
        };
        m_indicators.add(m_sumIndicator);
    }

    protected void recalcOne() {
        ChartPoint fastPoint = m_tripleEma1.getLastPoint();//m_ema.getLastPoint();
        ChartPoint tripleEma20 = m_tripleEma20.getLastPoint();
        ChartPoint tripleEma25 = m_tripleEma25.getLastPoint();
        if ((fastPoint != null) && (tripleEma20 != null) && (tripleEma25 != null)) {
            double fast = fastPoint.m_value;
            double bound1 = tripleEma20.m_value;
            double bound2 = tripleEma25.m_value;
            double boundTop    = Math.max(bound1, bound2);
            double boundBottom = Math.min(bound1, bound2);
            double one = valueToBounds(fast, boundTop, boundBottom);
            if ((m_one == null) || !m_one.equals(one)) {
                m_one = one;
                ChartPoint point = new ChartPoint(fastPoint.m_millis, m_one);
                m_oneIndicator.addBar(point);
            }
        }
    }

    private void recalcTwo() {
        ChartPoint tripleEma10 = m_tripleEma10.getLastPoint();
        ChartPoint tripleEma15 = m_tripleEma15.getLastPoint();
        ChartPoint tripleEma20 = m_tripleEma20.getLastPoint();
        ChartPoint tripleEma25 = m_tripleEma25.getLastPoint();
        if ((tripleEma10 != null) && (tripleEma15 != null) && (tripleEma20 != null) && (tripleEma25 != null)) {
            double tema10 = tripleEma10.m_value;
            double tema15 = tripleEma15.m_value;
            double tema20 = tripleEma20.m_value;
            double tema25 = tripleEma25.m_value;
            double boundTop    = Math.max(tema15, Math.max(tema20, tema25));
            double boundBottom = Math.min(tema15, Math.min(tema20, tema25));
            double two = valueToBounds(tema10, boundTop, boundBottom);
            if ((m_two == null) || !m_two.equals(two)) {
                m_two = two;
                ChartPoint point = new ChartPoint(tripleEma10.m_millis, two);
                m_twoIndicator.addBar(point);
            }

            boundTop    = Math.max(tema10, boundTop);
            boundBottom = Math.min(tema10, boundBottom);
            double spread = boundTop - boundBottom;
            if ((m_spread == null) || !m_spread.equals(spread)) {
                m_spread = spread;
                ChartPoint point = new ChartPoint(tripleEma10.m_millis, spread);
                m_spreadIndicator.addBar(point);
            }
        }
    }

    private void recalcSum() {
        if ((m_one != null) && (m_two != null)) {
            double sum = (m_one + m_two) / 2;

//            if ((m_spread != null) && (m_spread < LEVEL)) {
//                double mult = m_spread / LEVEL;
//                mult = mult * mult;
//                sum = sum * mult;
//            }
            if ((m_smoochedSpread != null) && (m_smoochedSpread < BOUND_LEVEL)) {
                double mult = m_smoochedSpread / BOUND_LEVEL;
                mult = mult * mult;
                sum = sum * mult;
            }

            if ((m_sum == null) || !m_sum.equals(sum)) {
                m_sum = sum;
                ChartPoint point = new ChartPoint(m_tripleEma1.getLastPoint().m_millis, sum);
                m_sumIndicator.addBar(point);
                notifyListener();
            }
        }
    }

    @Override public double lastTickPrice() { return m_tripleEma1.lastTickPrice(); }
    @Override public long lastTickTime() { return m_tripleEma1.lastTickTime(); }
    @Override public Color getColor() { return Color.LIGHT_GRAY; }
    @Override public String getRunAlgoParams() { return "EMAS"; }

    @Override public double getDirectionAdjusted() {
        return (m_sum == null) ? 0 : m_sum;
    }
    @Override public Direction getDirection() {
        double dir = getDirectionAdjusted();
        return (dir == 0) ? null : Direction.get(dir);
    }

    //===========================================================================
    public static class Wide extends EmasAlgo {
        @Override public String getRunAlgoParams() { return "EMAS~"; }

        public Wide(TresExchData tresExchData) {
            super(tresExchData);
        }

        @Override protected void recalcOne() {
            ChartPoint fastPoint = m_tripleEma1.getLastPoint();
            ChartPoint tripleEma10 = m_tripleEma10.getLastPoint();
            ChartPoint tripleEma15 = m_tripleEma15.getLastPoint();
            ChartPoint tripleEma20 = m_tripleEma20.getLastPoint();
            ChartPoint tripleEma25 = m_tripleEma25.getLastPoint();
            if ((fastPoint != null) && (tripleEma10 != null) && (tripleEma15 != null) && (tripleEma20 != null) && (tripleEma25 != null)) {
                double fast = fastPoint.m_value;
                double tema10 = tripleEma10.m_value;
                double tema15 = tripleEma15.m_value;
                double tema20 = tripleEma20.m_value;
                double tema25 = tripleEma25.m_value;
                double boundTop = Math.max(Math.max(tema10, tema15), Math.max(tema20, tema25));
                double boundBottom = Math.min(Math.min(tema10, tema15), Math.min(tema20, tema25));
                m_one = valueToBounds(fast, boundTop, boundBottom);
                ChartPoint point = new ChartPoint(fastPoint.m_millis, m_one);
                m_oneIndicator.addBar(point);
            }
        }
    }
}
