package bthdg.tres.ind;

import bthdg.Log;
import bthdg.calc.OscTick;
import bthdg.exch.TradeDataLight;
import bthdg.tres.*;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class OscIndicator extends TresIndicator {
    public static double PEAK_TOLERANCE = 0.004;

    public static final Color OSC_AVG_COLOR = Color.yellow;
    public static final Color OSC_COLOR = Colors.setAlpha(Color.yellow, 35);

    protected static void log(String s) { Log.log(s); }

    public OscIndicator(TresAlgo oscAlgo) {
        this(oscAlgo, PEAK_TOLERANCE);
    }

    public OscIndicator(TresAlgo oscAlgo, double tolerance) {
        super("osc", tolerance, oscAlgo);
        if (Tres.LOG_PARAMS) {
            log("OscIndicator");
            log(" PEAK_TOLERANCE=" + tolerance);
        }
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new OscPhasedIndicator(exchData, phaseIndex); }

    @Override public Color getColor() { return OSC_AVG_COLOR; }
    @Override public Color getPeakColor() { return OSC_AVG_COLOR; }

//    @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
//        double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
//        minMaxCalculator.m_minValue = -max;
//        minMaxCalculator.m_maxValue = max;
//    }

    private class OscPhasedIndicator extends TresPhasedIndicator {
        private final TresOscCalculator m_oscCalculator;
        private final BaseTresMaCalculator m_maCalculator;

        private double m_direction = 0; // [-1 ... 1].   parked initially

        @Override public Color getColor() { return OSC_COLOR; }

        public OscPhasedIndicator(TresExchData exchData, int phaseIndex) {
            super(OscIndicator.this, exchData, phaseIndex, OscIndicator.PEAK_TOLERANCE);

            m_oscCalculator = new TresOscCalculator(exchData, phaseIndex) {
                @Override public void bar(long barStart, double stoch1, double stoch2) {
                    super.bar(barStart, stoch1, stoch2);

                    long barEnd = barStart + m_exchData.m_tres.m_barSizeMillis;
                    OscChartPoint tick = new OscChartPoint(barEnd, stoch1, stoch2);
                    collectPointIfNeeded(tick);
                    m_peakCalculator.update(tick);
                    onBar(tick);
                }
            };

            m_maCalculator = new BaseTresMaCalculator(exchData, phaseIndex) {
                @Override protected Boolean calcOscDirection(boolean useLastFineTick, long time) {
                    return OscPhasedIndicator.this.calcOscDirection(useLastFineTick, time);
                }
            };
        }

        @Override public boolean update(TradeDataLight tdata) {
            long timestamp = tdata.m_timestamp;
            double price = tdata.m_price;
            return m_oscCalculator.update(timestamp, price);
        }

        @Override public double lastTickPrice() { return m_oscCalculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_oscCalculator.m_lastTickTime; }

        @Override public double getDirectionAdjusted() {  // [-1 ... 1]
            Boolean oscUp = calcOscDirection(true, System.currentTimeMillis());
            if (oscUp != null) {
                m_direction = oscUp ? 1 : -1;
            }
            return m_direction;
        }

        protected Boolean calcOscDirection(boolean useLastFineTick, long timestamp) {
            if (useLastFineTick) {
                BaseTresMaCalculator.MaCrossData lastMaCrossData = m_maCalculator.lastMaCrossData();
                if (lastMaCrossData != null) {
                    long lastMaCrossTime = lastMaCrossData.m_timestamp;
                    long passed = timestamp - lastMaCrossTime;
                    if (passed < m_exchData.m_tres.m_barSizeMillis / 10) {
                        return null;
                    }
                }
            }

            OscTick newestTick = useLastFineTick ? m_oscCalculator.m_blendedLastFineTick : m_oscCalculator.m_lastBar;
            if (newestTick != null) {
                double newestMid = newestTick.getMid();
                if (newestMid < TresOscCalculator.LOCK_OSC_LEVEL) {
                    return false; // oscDown
                } else if (newestMid > 1 - TresOscCalculator.LOCK_OSC_LEVEL) {
                    return true; // oscUp
                }
                double newestVal1 = newestTick.m_val1;
                double newestVal2 = newestTick.m_val2;
//                if( (newestVal1 < 0.1) && (newestVal2 < 0.1) ) {
//                    return false; // oscDown
//                }
//                if( (newestVal1 > 0.9) && (newestVal2 > 0.9) ) {
//                    return true; // oscUp
//                }
                if (newestVal1 != newestVal2) { // have direction
                    double newestValDiff = Math.abs(newestVal1 - newestVal2);
                    if (newestValDiff > 0.002) {
                        boolean newestOscUp = (newestVal1 > newestVal2);
                        OscTick oldBar = useLastFineTick ? m_oscCalculator.m_lastBar : m_oscCalculator.m_prevBar;
                        if (oldBar != null) {
                            double oldOsc1 = oldBar.m_val1;
                            double oldOsc2 = oldBar.m_val2;
                            if (oldOsc1 != oldOsc2) { // had direction
                                Boolean oldOscUp = (oldOsc1 > oldOsc2);
                                if (oldOscUp == newestOscUp) {
//                                        if(useLastFineTick && (newestMid>0.3) && (newestMid <0.7)) {
//                                            OscTick oldestBar = m_oscCalculator.m_prevBar;
//                                            if (oldestBar != null) {
//                                                double oldestOsc1 = oldestBar.m_val1;
//                                                double oldestOsc2 = oldestBar.m_val2;
//                                                if (oldestOsc1 != oldestOsc2) { // had direction
//                                                    Boolean oldestOscUp = (oldestOsc1 > oldestOsc2);
//                                                    if (oldOscUp == oldestOscUp) {
//                                                        return newestOscUp;
//                                                    }
//                                                }
//                                            }
//                                        } else {
                                        return newestOscUp;
//                                        }
                                }
                            }
                        }
                    }
                }
            }
            return null; // meant undefined direction
        }
    }

    public static class OscChartPoint extends ChartPoint {
        private final double m_stoch2;

        public OscChartPoint(long millis, double stoch1, double stoch2) {
            super(millis, stoch1);
            m_stoch2 = stoch2;
        }
    }

}
