package bthdg.tres.alg;

import bthdg.calc.OscTick;
import bthdg.exch.Direction;
import bthdg.tres.*;
import bthdg.tres.ind.TresIndicator;

import java.awt.*;

public class OscAlgo extends TresAlgo {
    final OscIndicator m_oscIndicator;

    public OscAlgo(TresExchData exchData) {
        super("OSC", exchData);
        exchData.m_oscAlgo = this;

        m_oscIndicator = new OscIndicator(this);
        m_indicators.add(m_oscIndicator);
    }

//    @Override public double lastTickPrice() { return m_tresExchData.m_lastPrice; }
//    @Override public long lastTickTime() { return m_tresExchData.m_lastTickMillis; }
    @Override public double lastTickPrice() { return m_oscIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_oscIndicator.lastTickTime(); }

    @Override public double getDirectionAdjusted() { return m_oscIndicator.getDirectionAdjusted(); } // [-1 ... 1]
//    @Override public double getDirectionAdjusted() { // [-1 ... 1]
//        double directionAdjusted = 0;
//        PhaseData[] phaseDatas = m_tresExchData.m_phaseDatas;
//        for (PhaseData phaseData : phaseDatas) {
//            double direction = phaseData.getDirection();
//            directionAdjusted += direction;
//        }
//        return directionAdjusted/phaseDatas.length;
//    }

    @Override public Direction getDirection() { return m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN

    public static class OscIndicator extends TresIndicator {
        private static final double PEAK_TOLERANCE = 0.1;

        public OscIndicator(bthdg.tres.alg.OscAlgo oscAlgo) {
            super("osc", PEAK_TOLERANCE, oscAlgo);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
            return new OscPhasedIndicator(exchData, phaseIndex); }

        @Override public Color getColor() { return Color.yellow; }
        @Override public Color getPeakColor() { return Color.yellow; }

        private class OscPhasedIndicator extends TresPhasedIndicator {
            private final TresOscCalculator m_oscCalculator;
            private final BaseTresMaCalculator m_maCalculator;

            private double m_direction = 0; // [-1 ... 1].   parked initially

            public OscPhasedIndicator(TresExchData exchData, int phaseIndex) {
                super(OscIndicator.this, exchData, phaseIndex, OscIndicator.PEAK_TOLERANCE);

                m_oscCalculator = new TresOscCalculator(exchData, phaseIndex) {
                    @Override public void bar(long barStart, double stoch1, double stoch2) {
                        super.bar(barStart, stoch1, stoch2);

                        long barEnd = barStart + m_exchData.m_tres.m_barSizeMillis;
                        OscChartPoint tick = new OscChartPoint(barEnd, stoch1, stoch2);
                        if (m_exchData.m_tres.m_collectPoints) {
                            m_points.add(tick); // add to the end
                        }
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

            @Override public boolean update(long timestamp, double price) {
                return m_oscCalculator.update(timestamp, price);
            }

            @Override public Color getColor() { return Color.MAGENTA; }
            @Override public Color getPeakColor() { return Color.MAGENTA; }

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
                    TresMaCalculator.MaCrossData lastMaCrossData = m_maCalculator.lastMaCrossData();
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
    }

    public static class OscChartPoint extends ChartPoint {
        private final double m_stoch2;

        public OscChartPoint(long millis, double stoch1, double stoch2) {
            super(millis, stoch1);
            m_stoch2 = stoch2;
        }
    }

}
