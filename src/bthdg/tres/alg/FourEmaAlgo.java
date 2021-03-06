package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.*;
import bthdg.util.Colors;

import java.awt.*;

public class FourEmaAlgo extends TresAlgo {
    public static double EMA_SIZE = 5;
    public static double DEMA_SIZE = 27;
    public static double TEMA_SIZE = 25;
    public static double LEMA_FACTOR = 0.44;
    public static long BOUND_SMOOCH_RATE = 7;
    public static double BOUND_LEVEL = 0.41;
    public static double MID_SMOOCH_RATE = 5;
    public static double MID_VELOCITY_SIZE = 0.2;
    public static double SUM_SMOOCH_RATE = 5;
    public static double SUM_VELOCITY_SIZE = 0.2;
    public static double START_ZERO_LEVEL = 0.0000016;

    protected final EmaIndicator m_ema;
    protected final DoubleEmaIndicator m_doubleEma;
    protected final TripleEmaIndicator m_tripleEma;
    protected final LaguerreMaIndicator m_laguerreMa;
    protected final TresIndicator m_valIndicator;
    protected final TresIndicator m_levelIndicator;
    private final TresIndicator m_spreadIndicator;
    private final SmoochedIndicator m_smoochedSpreadIndicator;
    private final TresIndicator m_midIndicator;
    private final SmoochedIndicator m_smoochedMidIndicator;
    private final VelocityIndicator m_midVelocityIndicator;
    private final TresIndicator m_sumIndicator;
    private final SmoochedIndicator m_smoochedSumIndicator;
    private final VelocityIndicator m_sumVelocityIndicator;
    private final TresIndicator m_avgVelocityIndicator;
    private final TresIndicator m_leveledAvgVelocityIndicator;
    private final ZeroLeveler m_zeroLeveler;
    private final RangeLeveler m_leveler;
    private Double m_value;
    private Double m_level;
    private Double m_spread;
    private Double m_smoochedSpread;
    private Double m_sum;
    private Double m_mid;
    private Double m_avgVelocity;
    private Double m_leveledAvgVelocity;

    public FourEmaAlgo(TresExchData tresExchData) {
        super("4ema", tresExchData);
        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        m_leveler = new RangeLeveler();
        m_zeroLeveler = new ZeroLeveler(START_ZERO_LEVEL);

        m_ema = new EmaIndicator("ema", this, EMA_SIZE);
        m_indicators.add(m_ema);

        m_doubleEma = new DoubleEmaIndicator("dema", this, DEMA_SIZE, Color.PINK);
        m_indicators.add(m_doubleEma);

        m_tripleEma = new TripleEmaIndicator("tema", this, TEMA_SIZE, Color.magenta);
        m_indicators.add(m_tripleEma);

        m_laguerreMa = new LaguerreMaIndicator("lema", this, LEMA_FACTOR, Color.ORANGE);
        m_indicators.add(m_laguerreMa);

        m_valIndicator = new TresIndicator( "v", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.LIGHT_ORANGE; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean useValueAxe() { return true; }
            @Override protected boolean drawZeroLine() { return true; }
        };
        m_indicators.add(m_valIndicator);

        m_levelIndicator = new TresIndicator( "L", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.SKY; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean useValueAxe() { return true; }
            @Override protected boolean drawZeroLine() { return true; }
        };
        m_indicators.add(m_levelIndicator);

        m_midIndicator = new TresIndicator( "m", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.TRANSP_LIGHT_CYAN; }
            @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean usePriceAxe() { return true; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_smoochedMidIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_midIndicator);

        m_smoochedMidIndicator = new SmoochedIndicator(this, "ms", (long) (MID_SMOOCH_RATE * barSizeMillis)) {
            @Override protected boolean countPeaks() { return false; }
            @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
            @Override protected boolean usePriceAxe() { return true; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_midVelocityIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_smoochedMidIndicator);

        long midVelocitySize = (long) (barSizeMillis * MID_VELOCITY_SIZE);
        m_midVelocityIndicator = new VelocityIndicator(this, "mv", midVelocitySize, 0.1) {
            @Override public String toString() { return "Mid.VelIndicator"; }
        };
        m_indicators.add(m_midVelocityIndicator);

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

        m_smoochedSpreadIndicator = new SmoochedIndicator(this, "[]s", (long) (BOUND_SMOOCH_RATE * barSizeMillis)) {
            @Override protected boolean countPeaks() { return false; }
            @Override protected ILineColor getLineColor() {
                return new ILineColor() {
                    @Override public Color getColor(Double val, Double lastVal) {
                        return val < BOUND_LEVEL ? Color.red : Color.CYAN;
                    }
                };
            }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if(lastPoint != null) {
                    m_smoochedSpread = lastPoint.m_value;
                }
            }
        };
        m_indicators.add(m_smoochedSpreadIndicator);

        m_sumIndicator = new TresIndicator( "s", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.DARK_RED; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean useValueAxe() { return true; }
            @Override protected boolean drawZeroLine() { return true; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_smoochedSumIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_sumIndicator);

        m_smoochedSumIndicator = new SmoochedIndicator(this, "ss", (long) (SUM_SMOOCH_RATE * barSizeMillis)) {
            @Override protected boolean countPeaks() { return false; }
            @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_sumVelocityIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_smoochedSumIndicator);

        long sumVelocitySize = (long) (barSizeMillis * SUM_VELOCITY_SIZE);
        m_sumVelocityIndicator = new VelocityIndicator(this, "sv", sumVelocitySize, 0.1) {
            @Override public String toString() { return "Sum.VelIndicator"; }
        };
        m_indicators.add(m_sumVelocityIndicator);

        m_avgVelocityIndicator = new TresIndicator( "AV", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.DARK_RED; }
            @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean drawZeroLine() { return true; }
        };
        m_indicators.add(m_avgVelocityIndicator);

        m_leveledAvgVelocityIndicator = new TresIndicator( "AVl", 0, this ) {
            @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
            @Override public Color getColor() { return Colors.LIGHT_BLUE; }
//            @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
            @Override protected boolean countPeaks() { return false; }
            @Override protected boolean useValueAxe() { return true; }
            @Override protected boolean drawZeroLine() { return true; }
        };
        m_indicators.add(m_leveledAvgVelocityIndicator);
    }

    @Override public void postUpdate(TradeDataLight tdata) {
        recalc();
    }

    private void recalc() {
        ChartPoint emaPoint = m_ema.getLastPoint();
        ChartPoint doubleEmaPoint = m_doubleEma.getLastPoint();
        ChartPoint tripleEmaPoint = m_tripleEma.getLastPoint();
        ChartPoint laguerreMaPoint = m_laguerreMa.getLastPoint();
        if ((emaPoint != null) && (doubleEmaPoint != null) && (tripleEmaPoint != null) && (laguerreMaPoint != null)) {
            long millis = emaPoint.m_millis;
            double ema = emaPoint.m_value;
            double doubleEma = doubleEmaPoint.m_value;
            double tripleEma = tripleEmaPoint.m_value;
            double laguerreMa = laguerreMaPoint.m_value;
            double boundTop    = Math.max(doubleEma, Math.max(tripleEma, laguerreMa));
            double boundBottom = Math.min(doubleEma, Math.min(tripleEma, laguerreMa));
            double value = valueToBounds(ema, boundTop, boundBottom);
            double level = m_leveler.update(ema, boundTop, boundBottom);

            double mid = (boundTop + boundBottom) / 2;
            if ((m_mid == null) || !m_mid.equals(mid)) {
                m_mid = mid;
                ChartPoint point = new ChartPoint(millis, mid);
                m_midIndicator.addBar(point);
            }

            double spread = boundTop - boundBottom;
            if ((m_spread == null) || !m_spread.equals(spread)) {
                m_spread = spread;
                ChartPoint point = new ChartPoint(millis, spread);
                m_spreadIndicator.addBar(point);
            }

            if ((m_value == null) || !m_value.equals(value)) {
                m_value = value;
                ChartPoint point = new ChartPoint(millis, value);
                m_valIndicator.addBar(point);
            }

            if ((m_level == null) || !m_level.equals(level)) {
                m_level = level;
                ChartPoint point = new ChartPoint(millis, level);
                m_levelIndicator.addBar(point);
            }

            if (m_value != null) {
                double sum = level; // value;
                if ((m_smoochedSpread != null) && (m_smoochedSpread < BOUND_LEVEL)) {
                    double mult = m_smoochedSpread / BOUND_LEVEL;
                    mult = mult * mult;
                    sum *= mult;
                }

                if ((m_sum == null) || !m_sum.equals(sum)) {
                    m_sum = sum;
                    ChartPoint point = new ChartPoint(millis, sum);
                    m_sumIndicator.addBar(point);
                    notifyListener();
                }
            }
        }

        ChartPoint midVelocityLastPoint = m_midVelocityIndicator.getLastPoint();
        ChartPoint sumVelocityLastPoint = m_sumVelocityIndicator.getLastPoint();
        if ((midVelocityLastPoint != null) && (sumVelocityLastPoint != null)) {
            double midVelocity = midVelocityLastPoint.m_value;
            double sumVelocity = sumVelocityLastPoint.m_value;
            double avgVelocity = (midVelocity + sumVelocity) / 2;
            if ((m_avgVelocity == null) || !m_avgVelocity.equals(avgVelocity)) {
                m_avgVelocity = avgVelocity;
                long millis = Math.max(midVelocityLastPoint.m_millis, sumVelocityLastPoint.m_millis);
                ChartPoint point = new ChartPoint(millis, avgVelocity);
                m_avgVelocityIndicator.addBar(point);

                double leveledAvgVelocity = m_zeroLeveler.update(avgVelocity);

                if ((m_smoochedSpread != null) && (m_smoochedSpread < BOUND_LEVEL)) {
                    double mult = m_smoochedSpread / BOUND_LEVEL;
                    mult = mult * mult;
                    leveledAvgVelocity *= mult;
                }

                m_leveledAvgVelocity = leveledAvgVelocity;
                point = new ChartPoint(millis, leveledAvgVelocity);
                m_leveledAvgVelocityIndicator.addBar(point);
            }
        }
    }

    @Override public double lastTickPrice() { return m_ema.lastTickPrice(); }
    @Override public long lastTickTime() { return m_ema.lastTickTime(); }
    @Override public Color getColor() { return Color.blue; }
    @Override public String getRunAlgoParams() { return "FourEMA"; }

//    @Override public double getDirectionAdjusted() {
//        return (m_sum == null) ? 0 : m_sum;
//    }
    @Override public double getDirectionAdjusted() {
        return (m_leveledAvgVelocity == null) ? 0 : m_leveledAvgVelocity;
    }

    @Override public Direction getDirection() {
        double dir = getDirectionAdjusted();
        return (dir == 0) ? null : Direction.get(dir);
    }
}
