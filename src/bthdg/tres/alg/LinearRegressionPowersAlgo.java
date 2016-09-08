package bthdg.tres.alg;

import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.LinearRegressionPowerIndicator;
import bthdg.tres.ind.SmoochedIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class LinearRegressionPowersAlgo extends TresAlgo {
    public static int LEN_STEP_START = 10;
    public static int LEN_STEPS_NUM = 5;
    public static int LEN_STEP_SIZE = 10;
    public static double SMOOTH_RATE = 10.0;
    public static double GAIN_POW = 1;
    public static double SMOOTH_GAIN_RATE = 0.21;
    public static double SMOOTH_PRICE_RATE = 1.00;
    public static double SPREAD = 1.0; // for ValueGainer: initial min-max spread for calculations
    public static double OFFSET = 0.0; // for ValueGainer: initial min-max spread offset for calculations
    private static final String AXE_NAME = "lrp*";

    private final List<LinearRegressionPowerIndicatorInt> m_lrpaIndicators = new ArrayList<LinearRegressionPowerIndicatorInt>();
    protected final long m_barSizeMillis;
    private final int m_phases;
    private double m_lastAvg;
    private long m_lastAvgTime;
    private final ValueIndicator m_avgLrpaIndicators;
    private final SmoochedIndicator m_smoochedIndicator;
    private final SlidingGainer m_gainer = new SlidingGainer(GAIN_POW);
    private final ValueIndicator m_gainIndicator;
    private final SmoochedIndicator m_smoochedGainIndicator;
    private double m_lastSmoochedGain;
    private final SmoochedIndicator m_smoochedPriceIndicator;
    private double m_lastSmoochedPrice;
    private final ValueGainer m_priceGainer;
    private final PriceIndicator m_minIndicator;
    private final PriceIndicator m_maxIndicator;
    private final ValueIndicator m_priceGainIndicator;
    private double m_lastMul;
    private final ValueIndicator m_mulIndicator;

    @Override public double lastTickPrice() { return m_lrpaIndicators.get(0).lastTickPrice(); }
    @Override public long lastTickTime() { return m_lrpaIndicators.get(0).lastTickTime(); }
    @Override public Color getColor() { return Colors.BROWN; }
    @Override public double getDirectionAdjusted() { return m_lastMul; }

    LinearRegressionPowersAlgo(TresExchData exchData) {
        super("LRPAs", exchData);

        m_barSizeMillis = exchData.m_tres.m_barSizeMillis;
        m_phases = exchData.m_tres.m_phases;

        int len = LEN_STEP_START;
        for (int i = 0; i < LEN_STEPS_NUM; i++) {
            LinearRegressionPowerIndicatorInt ind = new LinearRegressionPowerIndicatorInt(len);
            m_indicators.add(ind);
            m_lrpaIndicators.add(ind);
            len += LEN_STEP_SIZE;
        }

        m_avgLrpaIndicators = new ValueIndicator(this, "d", Color.blue) {
            @Override protected boolean useValueAxe() { return false; }
            @Override protected String getYAxeName() { return AXE_NAME; }
        };
        m_indicators.add(m_avgLrpaIndicators);

        long frameSizeMillis = (long) (SMOOTH_RATE * m_barSizeMillis);
        m_smoochedIndicator = new SmoochedIndicator(this, "ds", frameSizeMillis) {
            private double m_lastGainerValue = Double.MAX_VALUE;

            @Override protected String getYAxeName() { return AXE_NAME; }
            @Override public Color getColor() { return Color.lightGray; }
            @Override protected boolean drawZeroLine() { return true; }

            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double value = lastPoint.m_value;
                    double gainerValue = m_gainer.update(value);
                    long millis = lastPoint.m_millis;
                    ChartPoint gainPoint = new ChartPoint(millis, gainerValue);
                    if (gainerValue != m_lastGainerValue) {
                        m_lastGainerValue = gainerValue;
                        m_gainIndicator.addBar(gainPoint);
                    }
                    m_smoochedGainIndicator.addBar(gainPoint);
                }
            }

            @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
                double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
                minMaxCalculator.m_minValue = -max;
                minMaxCalculator.m_maxValue = max;
            }
        };
        m_indicators.add(m_smoochedIndicator);

        m_gainIndicator = new ValueIndicator(this, "g", Color.red);
        m_indicators.add(m_gainIndicator);

        long frameSizeMillis2 = (long) (SMOOTH_GAIN_RATE * m_barSizeMillis);
        m_smoochedGainIndicator = new SmoochedIndicator(this, "gs", frameSizeMillis2) {
            @Override protected boolean useValueAxe() { return true; }
            @Override public Color getColor() { return Color.MAGENTA; }
            @Override protected boolean drawZeroLine() { return true; }

            @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
                double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
                minMaxCalculator.m_minValue = -max;
                minMaxCalculator.m_maxValue = max;
            }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double value = lastPoint.m_value;
                    if (value != m_lastSmoochedGain) {
                        m_lastSmoochedGain = value;
                        long millis = lastPoint.m_millis;
                        recalsMul(millis);
                    }
                }
            }
        };
        m_indicators.add(m_smoochedGainIndicator);

        long frameSizeMillis3 = (long) (SMOOTH_PRICE_RATE * m_barSizeMillis);
        m_smoochedPriceIndicator = new SmoochedIndicator(this, "ps", frameSizeMillis3) {
            @Override protected boolean usePriceAxe() { return true; }
            @Override public Color getColor() { return Colors.LIGHT_CYAN; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double value = lastPoint.m_value;
                    if(m_lastSmoochedPrice != value) {
                        m_lastSmoochedPrice = value;
                        long millis = lastPoint.m_millis;
                        recalsMul(millis);
                    }
                }
            }
        };
        m_indicators.add(m_smoochedPriceIndicator);

        m_priceGainer = new ValueGainer(SPREAD, OFFSET);

        m_minIndicator = new PriceIndicator(this, "mi", Color.PINK);
        m_indicators.add(m_minIndicator);

        m_maxIndicator = new PriceIndicator(this, "ma", Color.CYAN);
        m_indicators.add(m_maxIndicator);

        m_priceGainIndicator = new ValueIndicator(this, "pg", Color.white);
        m_indicators.add(m_priceGainIndicator);

        m_mulIndicator = new ValueIndicator(this, "mu", Color.ORANGE);
        m_indicators.add(m_mulIndicator);
    }

    private void recalsMul(long millis) {
        if ((m_lastSmoochedPrice > 0) && (m_lastSmoochedGain != 0)) {
            double priceGainerValue = m_priceGainer.update(m_lastSmoochedPrice, m_lastSmoochedGain);

            double mul = priceGainerValue * m_lastSmoochedGain;
            if (mul != m_lastMul) {
                ChartPoint minPoint = new ChartPoint(millis, m_priceGainer.m_min);
                m_minIndicator.addBar(minPoint);

                ChartPoint maxPoint = new ChartPoint(millis, m_priceGainer.m_max);
                m_maxIndicator.addBar(maxPoint);

                ChartPoint pgPoint = new ChartPoint(millis, priceGainerValue);
                m_priceGainIndicator.addBar(pgPoint);

                m_lastMul = mul;
                ChartPoint mulPoint = new ChartPoint(millis, mul);
                m_mulIndicator.addBar(mulPoint);

                notifyListener();
            }
        }
    }

    private double getAverageDirectionAdjusted() {
        double sum = 0;
        for (LinearRegressionPowerIndicatorInt indicator : m_lrpaIndicators) {
            double direction = indicator.getDirectionAdjusted();
            sum += direction;
        }
        return sum / LEN_STEPS_NUM;
    }

    @Override public void postUpdate(TradeDataLight tdata) {
        double avg = getAverageDirectionAdjusted();
        if (m_lastAvg != avg) {
            long millis = 0;
            for (LinearRegressionPowerIndicatorInt indicator : m_lrpaIndicators) {
                long l = indicator.lastTickTime();
                if (l > millis) {
                    millis = l;
                }
            }

            if (millis > m_lastAvgTime) { // post forward only if time change - no multiple ticks on same millis
                ChartPoint andPoint = new ChartPoint(m_lastAvgTime, m_lastAvg);
                m_avgLrpaIndicators.addBar(andPoint);
                m_smoochedIndicator.addBar(andPoint);

                ChartPoint tickPoint = new ChartPoint(tdata.m_timestamp, tdata.m_price);
                m_smoochedPriceIndicator.addBar(tickPoint);

                m_lastAvgTime = millis;
            }
            m_lastAvg = avg;
        }
    }

    @Override public String getRunAlgoParams() {
        return "LRPs: barSize=" + m_barSizeMillis +
                "; phases=" + m_phases +
                "; len=[sta=" + LEN_STEP_START + ", ste=" + LEN_STEP_SIZE + ", num=" + LEN_STEPS_NUM + "]" +
                "; power=" + Utils.format5(LinearRegressionPowerIndicator.POW);
    }

    // ------------------------------------------------------------------------------------
    private class LinearRegressionPowerIndicatorInt extends LinearRegressionPowerIndicator {
        private LinearRegressionPowerIndicatorInt(int len) {
            super(LinearRegressionPowersAlgo.this, len);
        }

        @Override protected boolean drawZeroLine() { return true; }
        @Override protected String getYAxeName() { return AXE_NAME; }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
            return new PhasedLinearRegressionPowerIndicator(this, exchData, phaseIndex) {
                @Override public double getDirectionAdjusted() {
                    return m_calculator.getVal();
                }
            };
        }
    }

    // ===============================================================================================================
    public static class PriceIndicator extends TresIndicator {
        private final Color m_color;

        PriceIndicator(TresAlgo algo, String name, Color color) {
            this(algo, name, 0, color);
        }
        PriceIndicator(TresAlgo algo, String name, double peakTolerance, Color color) {
            super(name, peakTolerance, algo);
            m_color = color;
        }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return m_color; }
        @Override protected boolean usePriceAxe() { return true; }
    }

    // ------------------------------------------------------------------------------------
    public static class SlidingGainer {
        private final double m_pow;
        private double m_value = 0;
        private double m_max = 0;

        public SlidingGainer(double pow) {
            m_pow = pow;
        }

        public double update(double value) {
            double valueSignum = Math.signum(value);
            double maxSignum = Math.signum(m_max);

            double absMax = Math.abs(m_max);
            if ((valueSignum == maxSignum) && (absMax > 0)) {
                double absValue = Math.abs(value);
                if (absValue > absMax) {
                    // update max
                    m_max = value;
                    m_value = valueSignum;
                } else {
                    double halfMax = absMax / 2;
                    if (absValue >= halfMax) {
                        // step down
                        double fourthMax = halfMax / 2;
                        double zeroLevel = halfMax + fourthMax;
                        double offset = absValue - zeroLevel;
                        m_value = valueSignum * offset / fourthMax;
                    } else {
                        // collapsing
                        m_max = value * 2;
                        m_value = -valueSignum;
                    }
                }

            } else { // first max
                m_max = value;
                m_value = valueSignum;
            }
            return (m_pow == 1) ? m_value : Math.signum(m_value) * Math.pow(Math.abs(m_value), m_pow);
        }
    }

    // ------------------------------------------------------------------------------------
    public static class ValueGainer {
        private final double m_spread;
        private final double m_offset;
        private Boolean m_isUpDirection;
        public double m_min;
        public double m_max;

        public ValueGainer(double spread, double offset) {
            m_spread = spread;
            m_offset = offset;
        }

        public double update(double value, double direction) {
            if (direction != 0) {
                if (direction > 0) {
                    if ((m_isUpDirection == null) || !m_isUpDirection) { // just becomes UP
                        m_isUpDirection = true;
                        m_min = value - m_offset;
                        m_max = value + m_spread - m_offset;
                    }
                    if (value > m_max) {
                        m_max = value;
                    }
                    return Math.max(0, (value - m_min) / (m_max - m_min));
                } else {
                    if ((m_isUpDirection == null) || m_isUpDirection) { // just becomes DOWN
                        m_isUpDirection = false;
                        m_max = value + m_offset;
                        m_min = value - m_spread + m_offset;
                    }
                    if (value < m_min) {
                        m_min = value;
                    }
                    return Math.max(0, (m_max - value) / (m_max - m_min));
                }
            } else {
                return 0;
            }
        }
    }
}
