package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.EmaIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.tres.ind.TripleEmaIndicator;
import bthdg.util.Colors;

import java.awt.*;

public class EmasAlgo extends TresAlgo {
    public static final double LEVEL = 0.4;
    private EmaIndicator m_ema3;
    private TripleEmaIndicator m_tripleEma10;
    private TripleEmaIndicator m_tripleEma15;
    private TripleEmaIndicator m_tripleEma20;
    private TripleEmaIndicator m_tripleEma25;
    private final TresIndicator m_oneIndicator;
    private final TresIndicator m_twoIndicator;
    private final TresIndicator m_sumIndicator;
    private final TresIndicator m_spreadIndicator;
    private Double m_one;
    private Double m_two;
    private Double m_sum;
    private Double m_spread;

    public EmasAlgo(TresExchData tresExchData) {
        super("EMAS", tresExchData);

        m_ema3 = new EmaIndicator("ema3", this, 3) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcOne();
                recalcSum();
            }
        };
        m_indicators.add(m_ema3);

        m_tripleEma10 = new TripleEmaIndicator("tema10", this, 10, Color.magenta) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcOne();
                recalcTwo();
                recalcSum();
            }
        };
        m_indicators.add(m_tripleEma10);
        m_tripleEma15 = new TripleEmaIndicator("tema15", this, 15, Color.PINK) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcOne();
                recalcTwo();
                recalcSum();
            }
        };
        m_indicators.add(m_tripleEma15);
        m_tripleEma20 = new TripleEmaIndicator("tema20", this, 20, Color.orange) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcOne();
                recalcTwo();
                recalcSum();
            }
        };
        m_indicators.add(m_tripleEma20);
        m_tripleEma25 = new TripleEmaIndicator("tema25", this, 25, Color.BLUE) {
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
        };
        m_indicators.add(m_spreadIndicator);

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
        };
        m_indicators.add(m_sumIndicator);
    }

    private void recalcOne() {
        ChartPoint ema3 = m_ema3.getLastPoint();
        ChartPoint tripleEma20 = m_tripleEma20.getLastPoint();
        ChartPoint tripleEma25 = m_tripleEma25.getLastPoint();
        if ((ema3 != null) && (tripleEma20 != null) && (tripleEma25 != null)) {
            double value = ema3.m_value;
            double bound1 = tripleEma20.m_value;
            double bound2 = tripleEma25.m_value;
            double boundTop    = Math.max(bound1, bound2);
            double boundBottom = Math.min(bound1, bound2);
            double one = valueToBounds(value, boundTop, boundBottom);
            if ((m_one == null) || !m_one.equals(one)) {
                m_one = one;
                ChartPoint point = new ChartPoint(ema3.m_millis, m_one);
                m_oneIndicator.addBar(point);
            }
        }

//        ChartPoint ema3 = m_ema3.getLastPoint();
//        ChartPoint tripleEma10 = m_tripleEma10.getLastPoint();
//        ChartPoint tripleEma15 = m_tripleEma15.getLastPoint();
//        ChartPoint tripleEma20 = m_tripleEma20.getLastPoint();
//        ChartPoint tripleEma25 = m_tripleEma25.getLastPoint();
//        if ((ema3 != null) && (tripleEma10 != null) && (tripleEma15 != null) && (tripleEma20 != null) && (tripleEma25 != null)) {
//            double value = ema3.m_value;
//            double tema10 = tripleEma10.m_value;
//            double tema15 = tripleEma15.m_value;
//            double tema20 = tripleEma20.m_value;
//            double tema25 = tripleEma25.m_value;
//            double boundTop = Math.max(Math.max(tema10, tema15), Math.max(tema20, tema25));
//            double boundBottom = Math.min(Math.min(tema10, tema15), Math.min(tema20, tema25));
//            m_one = valueToBounds(value, boundTop, boundBottom);
//            ChartPoint point = new ChartPoint(ema3.m_millis, m_one);
//            m_oneIndicator.addBar(point);
//        }
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

            if ((m_spread != null) && (m_spread < LEVEL)) {
                double mult = m_spread / LEVEL;
                mult = mult * mult;
                sum = sum * mult;
            }

            if ((m_sum == null) || !m_sum.equals(sum)) {
                m_sum = sum;
                ChartPoint point = new ChartPoint(m_ema3.getLastPoint().m_millis, sum);
                m_sumIndicator.addBar(point);
                notifyListener();
            }
        }
    }

    private double valueToBounds(double value, double boundTop, double boundBottom) {
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

    @Override public double lastTickPrice() { return m_ema3.lastTickPrice(); }
    @Override public long lastTickTime() { return m_ema3.lastTickTime(); }
    @Override public Color getColor() { return Color.LIGHT_GRAY; }
    @Override public String getRunAlgoParams() { return "EMAS"; }

    @Override public double getDirectionAdjusted() {
        return (m_sum == null) ? 0 : m_sum;
    }

    @Override public Direction getDirection() {
        double dir = getDirectionAdjusted();
        return (dir == 0) ? null : Direction.get(dir);
    }
}
