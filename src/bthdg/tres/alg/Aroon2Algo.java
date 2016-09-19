package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.SmoochedIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.tres.ind.VelocityIndicator;
import bthdg.tres.ind.VelocityRateIndicator;

import java.awt.*;

public class Aroon2Algo extends AroonAlgo {
    public static double PEAK_TOLERANCE4 = 0.96;
    public static final double VELOCITY_SMOOCH_RATE = 1.3;

    private final VelocityIndicator m_velocityIndicator;
    private final SmoochedIndicator m_smoochedVelocityIndicator;
    private final VelocityRateIndicator m_velRateIndicator;
    protected final AndIndicator m_andIndicator;

    @Override protected void onSmoochedBar() { m_velocityIndicator.addBar(m_smoochedIndicator.getLastPoint()); }
    @Override public String getRunAlgoParams() { return "Aroon2"; }

    public Aroon2Algo(TresExchData exchData) {
        this("ARO2", exchData);
    }

    public Aroon2Algo(String name, TresExchData exchData) {
        super(name, exchData);

        final long barSizeMillis = exchData.m_tres.m_barSizeMillis;
        long velocitySize = barSizeMillis/2;
        m_velocityIndicator = new VelocityIndicator(this, "vel", velocitySize, 0.1) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_smoochedVelocityIndicator.addBar(lastPoint);
            }
            @Override public String toString() { return "Aro.VelIndicator"; }
        };
        m_indicators.add(m_velocityIndicator);

        m_smoochedVelocityIndicator = new SmoochedIndicator(this, "sm", (long) (VELOCITY_SMOOCH_RATE * barSizeMillis)) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_velRateIndicator.addBar(lastPoint);
            }
            @Override protected boolean countPeaks() { return false; }
            @Override public Color getColor() { return Color.pink; }
            @Override protected boolean drawZeroLine() { return true; }
            @Override protected boolean centerYZeroLine() { return true; }
        };
        m_indicators.add(m_smoochedVelocityIndicator);

        m_velRateIndicator = new VelocityRateIndicator(this, m_smoochedIndicator) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);

                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double copValue = getDirectionAdjustedByPeakWatchers(m_smoochedIndicator);
                    double copValue2 = copValue * m_velRateIndicator.m_velRateCalc.m_velocityRate;

                    copValue2 = Math.signum(copValue2) * Math.pow(Math.abs(copValue2), 0.25);

                    long millis = lastPoint.m_millis;
                    m_andIndicator.addBar(new ChartPoint(millis, copValue2));
                }
            }
        };
        m_indicators.add(m_velRateIndicator);

        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);
    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        return getDirectionAdjustedByPeakWatchers(m_andIndicator);
    }

    @Override public Direction getDirection() { return m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN


    // ======================================================================================
    protected static class AndIndicator extends TresIndicator {
        public AndIndicator(TresAlgo algo) {
            super("+", PEAK_TOLERANCE4, algo);
        }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.CYAN; }
        @Override protected boolean centerYZeroLine() { return true; }
    }


    // ======================================================================================
    public static class Aroon2SharpAlgo extends Aroon2Algo {
        public Aroon2SharpAlgo(TresExchData exchData) {
            super("ARO2!", exchData);
        }

        @Override public double getDirectionAdjusted() { // [-1 ... 1]
            Direction direction = m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
            return (direction == null) ? 0 : (direction.isForward() ? 1 : -1);
        }

        // --------------------------------------------------------------------------------------------
        @Override public Direction getDirection() { return m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN
        @Override public String getRunAlgoParams() { return "Aroon2Sh"; }
    }

}
