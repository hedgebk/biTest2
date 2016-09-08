package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.*;
import bthdg.util.Colors;

import java.awt.*;

public class EmasAlgo extends TresAlgo {
    public static double SUM_PEAK_TOLERANCE = 0.4;
    public static double TEMA_FAST_SIZE = 1.8;
    public static double TEMA_START = 10;
    public static double TEMA_STEP = 5;
    private static final double BOUND_SMOOCH_RATE = 7.0;
    public static double BOUND_LEVEL = 0.3;  // 0.4;
    public static double EMA_SIZE = 1.7; // 0.77;

    protected EmaIndicator m_ema;
    protected TripleEmaIndicator m_fastEma;
    protected TripleEmaIndicator m_tema1;
    protected TripleEmaIndicator m_tema2;
    protected TripleEmaIndicator m_tema3;
    protected TripleEmaIndicator m_tema4;
    private final SmoochedIndicator m_smoochedSpreadIndicator;
    private final TresIndicator m_bidAskMidIndicator;
    protected final TresIndicator m_sumIndicator;
    private boolean m_changed;
    protected Double m_one;
    private Double m_two;
    private Double m_sum;
    private Double m_spread;
    private Double m_smoochedSpread;
    protected double m_boosted;
    private final CursorPainter m_cursorPainter;

    protected boolean countSumPeaks() { return false; }

    public EmasAlgo(TresExchData tresExchData) {
        this("EMAS", tresExchData);
    }

    public EmasAlgo(String name, TresExchData tresExchData) {
        super(name, tresExchData);
        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        if (EMA_SIZE != 0) {
            m_ema = new EmaIndicator("ema", this, EMA_SIZE); // just to show - not used in calculations
            m_indicators.add(m_ema);
        }

        m_fastEma = new TripleEmaIndicator("te", this, TEMA_FAST_SIZE, Color.gray) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_changed = true;
            }
        };
        m_indicators.add(m_fastEma);

        double emaSize = TEMA_START;
        m_tema1 = new TripleEmaIndicator("te1", this, emaSize, Color.magenta) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_changed = true;
            }
        };
        m_indicators.add(m_tema1);

        emaSize += TEMA_STEP;
        m_tema2 = new TripleEmaIndicator("te2", this, emaSize, Color.PINK) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_changed = true;
            }
        };
        m_indicators.add(m_tema2);

        emaSize += TEMA_STEP;
        m_tema3 = new TripleEmaIndicator("te3", this, emaSize, Color.orange) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_changed = true;
            }
        };
        m_indicators.add(m_tema3);

        emaSize += TEMA_STEP;
        m_tema4 = new TripleEmaIndicator("te4", this, emaSize, Color.BLUE) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_changed = true;
            }
        };
        m_indicators.add(m_tema4);

        m_smoochedSpreadIndicator = new SmoochedIndicator(this, "[]s", (long) (BOUND_SMOOCH_RATE * barSizeMillis)) {
            @Override protected boolean countPeaks() { return false; }
            @Override public Color getColor() { return Color.CYAN; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    m_smoochedSpread = lastPoint.m_value;
                }
            }
        };
        m_indicators.add(m_smoochedSpreadIndicator);

        m_sumIndicator = new TresIndicator( "s", SUM_PEAK_TOLERANCE, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.BEGIE; }
            @Override protected boolean countPeaks() { return countSumPeaks(); }
            @Override protected boolean countHalfPeaks() { return countSumPeaks(); }
            @Override protected boolean useValueAxe() { return true; }
            @Override protected boolean drawZeroLine() { return true; }
        };
        m_indicators.add(m_sumIndicator);

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

    @Override public void postUpdate(TradeDataLight tdata) {
        if (m_changed) {
            m_changed = false;
            recalcOne();
            recalcTwo();
            recalcSum();
        }
    }

    protected void recalcOne() {
        ChartPoint fastPoint = m_fastEma.getLastPoint();
        ChartPoint tema3 = m_tema3.getLastPoint();
        ChartPoint tema4 = m_tema4.getLastPoint();
        if ((fastPoint != null) && (tema3 != null) && (tema4 != null)) {
            double fast = fastPoint.m_value;
            double bound1 = tema3.m_value;
            double bound2 = tema4.m_value;
            double boundTop    = Math.max(bound1, bound2);
            double boundBottom = Math.min(bound1, bound2);
            double one = valueToBounds(fast, boundTop, boundBottom);
            if ((m_one == null) || !m_one.equals(one)) {
                m_one = one;
            }
        }
    }

    protected void recalcOneAsWide() {
        ChartPoint fastPoint = m_fastEma.getLastPoint();
        ChartPoint tripleEma10 = m_tema1.getLastPoint();
        ChartPoint tripleEma15 = m_tema2.getLastPoint();
        ChartPoint tripleEma20 = m_tema3.getLastPoint();
        ChartPoint tripleEma25 = m_tema4.getLastPoint();
        if ((fastPoint != null) && (tripleEma10 != null) && (tripleEma15 != null) && (tripleEma20 != null) && (tripleEma25 != null)) {
            double fast = fastPoint.m_value;
            double tema1 = tripleEma10.m_value;
            double tema2 = tripleEma15.m_value;
            double tema3 = tripleEma20.m_value;
            double tema4 = tripleEma25.m_value;
            double boundTop = Math.max(Math.max(tema1, tema2), Math.max(tema3, tema4));
            double boundBottom = Math.min(Math.min(tema1, tema2), Math.min(tema3, tema4));
            double one = valueToBounds(fast, boundTop, boundBottom);
            if ((m_one == null) || !m_one.equals(one)) {
                m_one = one;
            }
        }
    }

    private void recalcTwo() {
        ChartPoint tripleEma10 = m_tema1.getLastPoint();
        ChartPoint tripleEma15 = m_tema2.getLastPoint();
        ChartPoint tripleEma20 = m_tema3.getLastPoint();
        ChartPoint tripleEma25 = m_tema4.getLastPoint();
        if ((tripleEma10 != null) && (tripleEma15 != null) && (tripleEma20 != null) && (tripleEma25 != null)) {
            double tema10 = tripleEma10.m_value;
            double tema15 = tripleEma15.m_value;
            double tema20 = tripleEma20.m_value;
            double tema25 = tripleEma25.m_value;
            if (!Double.isInfinite(tema10) && !Double.isInfinite(tema15) && !Double.isInfinite(tema20) && !Double.isInfinite(tema25)) {
                double boundTop = Math.max(tema15, Math.max(tema20, tema25));
                double boundBottom = Math.min(tema15, Math.min(tema20, tema25));
                double two = valueToBounds(tema10, boundTop, boundBottom);
                if ((m_two == null) || !m_two.equals(two)) {
                    m_two = two;
                }

                boundTop = Math.max(tema10, boundTop);
                boundBottom = Math.min(tema10, boundBottom);
                double spread = boundTop - boundBottom;
                if ((m_spread == null) || !m_spread.equals(spread)) {
                    m_spread = spread;
                    ChartPoint point = new ChartPoint(tripleEma10.m_millis, spread);
                    m_smoochedSpreadIndicator.addBar(point);
                }
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
                ChartPoint lastPoint = m_fastEma.getLastPoint();
                if (lastPoint != null) {
                    long millis = lastPoint.m_millis;
                    ChartPoint point = new ChartPoint(millis, sum);
                    m_sumIndicator.addBar(point);

                    onSumChanged();

                    notifyListener();
                }
            }
        }
    }

    protected void onSumChanged() { }

    @Override public double lastTickPrice() { return m_fastEma.lastTickPrice(); }
    @Override public long lastTickTime() { return m_fastEma.lastTickTime(); }
    @Override public Color getColor() { return Color.LIGHT_GRAY; }
    @Override public String getRunAlgoParams() { return "EMAS"; }
    @Override public double getDirectionAdjusted() { return (m_sum == null) ? 0 : m_sum; }
    @Override public Direction getDirection() {
        double dir = getDirectionAdjusted();
        return (dir == 0) ? null : Direction.get(dir);
    }

    //===========================================================================
    public static class Wide extends EmasAlgo {
        @Override public String getRunAlgoParams() { return "EMAS~"; }

        public Wide(TresExchData tresExchData) {
            this("EMAS~", tresExchData);
        }
        public Wide(String name, TresExchData tresExchData) {
            super(name, tresExchData);
        }
        @Override protected void recalcOne() { recalcOneAsWide(); }
    }


    //===========================================================================
    public static class WideFast extends Wide {
        public WideFast(TresExchData tresExchData) {
            super("EMAS~f", tresExchData);
        }

        @Override protected boolean countSumPeaks() { return true; }
        @Override public String getRunAlgoParams() { return "EMAS~f SUM_PEAK=" + SUM_PEAK_TOLERANCE; }
        @Override public double getDirectionAdjusted() {
            return getDirectionAdjustedByPeakWatchers(m_sumIndicator);
        }
    }


    //===========================================================================
    public static class Boosted extends EmasAlgo {
        private final TresIndicator m_boostedIndicator;
        private final Booster m_booster;

        @Override public String getRunAlgoParams() { return "EMAS*"; }
        @Override public double getDirectionAdjusted() { return m_boosted; }
        @Override protected boolean countSumPeaks() { return true; }

        public Boosted(TresExchData tresExchData) {
            this("EMAS*", tresExchData);
        }
        public Boosted(String name, TresExchData tresExchData) {
            super(name, tresExchData);
            m_boostedIndicator = new TresIndicator( "b", 0, this ) {
                @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
                @Override public Color getColor() { return Colors.LIGHT_MAGNETA; }
                @Override protected boolean countPeaks() { return false; }
                @Override protected boolean useValueAxe() { return true; }
                @Override protected boolean drawZeroLine() { return true; }
            };
            m_indicators.add(m_boostedIndicator);

            m_booster = new Booster(m_sumIndicator, 1.0);
        }

        @Override protected void onSumChanged() {
            double boosted = m_booster.update();
            if (m_boosted != boosted) {
                m_boosted = boosted;
                long millis = m_fastEma.getLastPoint().m_millis;
                ChartPoint point = new ChartPoint(millis, boosted);
                m_boostedIndicator.addBar(point);
            }
        }
    }


    //===========================================================================
    public static class WideBoosted extends Boosted {
        @Override public String getRunAlgoParams() { return "EMAS~*"; }
        @Override public double getDirectionAdjusted() { return m_boosted; }
        @Override protected void recalcOne() { recalcOneAsWide(); }

        public WideBoosted(TresExchData tresExchData) {
            super("EMAS~*", tresExchData);
        }
    }
}
