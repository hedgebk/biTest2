package bthdg.calc;

import bthdg.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class OscCalculator extends BarCalculator {
    public static boolean BLEND_AVG = true;

    private final int m_len1;
    private final int m_len2;
    private final int m_k;
    private final int m_d;

    private double m_close;
    private double m_prevBarClose;
    private List<Double> m_gains = new ArrayList<Double>();
    private List<Double> m_losss = new ArrayList<Double>();
    private Double m_avgGain = null;
    private Double m_avgLoss = null;
    private List<Double> rsis = new ArrayList<Double>();
    private List<Double> stochs = new ArrayList<Double>();
    private List<Double> stoch1s = new ArrayList<Double>();

    public void fine(long stamp, double stoch1, double stoch2) {}
    public void bar(long barStart, double stoch1, double stoch2) {}

    public OscCalculator(int len1, int len2, int k, int d, long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
        m_len1 = len1;
        m_len2 = len2;
        m_k = k;
        m_d = d;
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        m_prevBarClose = m_close;
    }
    @Override public boolean updateCurrentBar(long time, double price) {
        m_close = price;
        updateCurrentBar(time, false);
        return true;
    }
    @Override protected void finishCurrentBar(long time, double price) {
        updateCurrentBar(time, true);
    }

    public void updateCurrentBar(long stamp, boolean finishBar) {
        if (m_prevBarClose == 0) {
            return;
        }
        double change = m_close - m_prevBarClose;
        double gain = (change > 0) ? change : 0d;
        double loss = (change < 0) ? -change : 0d;
        Double avgGain = null;
        Double avgLoss = null;
        if ((m_avgGain == null) || !BLEND_AVG) {
            if (m_gains.size() == m_len1 - 1) {
                avgGain = Utils.avg(m_gains, gain);
                avgLoss = Utils.avg(m_losss, loss);
            }
            if (finishBar) {
                m_gains.add(gain);
                m_losss.add(loss);
                if (m_gains.size() == m_len1) {
                    m_gains.remove(0);
                    m_losss.remove(0);
                }
            }
        } else { // blend
            avgGain = (m_avgGain * (m_len1 - 1) + gain) / m_len1;
            avgLoss = (m_avgLoss * (m_len1 - 1) + loss) / m_len1;
        }
        if (avgGain != null) {
            if (finishBar) {
                m_avgGain = avgGain;
                m_avgLoss = avgLoss;
            }
            double rsi = (avgLoss == 0) ? 100 : 100 - (100 / (1 + avgGain / avgLoss));
            if (rsis.size() == m_len2 - 1) {
                double highest = Utils.max(rsis, rsi);
                double lowest = Utils.min(rsis, rsi);
                double stoch = (highest == lowest) ? 0 : (rsi - lowest) / (highest - lowest);
                if (stochs.size() == m_k - 1) {
                    double stoch1 = Utils.avg(stochs, stoch);
                    if (stoch1s.size() == m_d - 1) {
                        double stoch2 = Utils.avg(stoch1s, stoch1);

                        fine(stamp, stoch1, stoch2);
                        if (finishBar) {
                            bar(m_currentBarStart, stoch1, stoch2);
                        }
                    }
                    if (finishBar) {
                        stoch1s.add(stoch1);
                        if (stoch1s.size() == m_d) {
                            stoch1s.remove(0);
                        }
                    }
                }
                if (finishBar) {
                    stochs.add(stoch);
                    if (stochs.size() == m_k) {
                        stochs.remove(0);
                    }
                }
            }
            if (finishBar) {
                rsis.add(rsi);
                if (rsis.size() == m_len2) {
                    rsis.remove(0);
                }
            }
        }
    }

    public static class SimpleOscCalculator extends OscCalculator {
        private List<OscTick> ret = new ArrayList<OscTick>();
        private List<OscTick> fine = new ArrayList<OscTick>();
        private boolean m_calcFine;

        public List<OscTick> fine() { return fine; }
        public void setCalcFine(boolean calcFine) { m_calcFine = calcFine; }

        public SimpleOscCalculator(int len1, int len2, int k, int d, long barSize, long barsMillisOffset) {
            super(len1, len2, k, d, barSize, barsMillisOffset);
        }

        @Override public void bar(long barStart, double stoch1, double stoch2) {
            OscTick osc = new OscTick(barStart, stoch1, stoch2);
            ret.add(osc);
        }

        @Override public void fine(long stamp, double stoch1, double stoch2) {
            if (m_calcFine) {
                OscTick osc = new OscTick(stamp, stoch1, stoch2);
                fine.add(osc);
            }
        }

        public List<OscTick> ret() {
            updateCurrentBar(0, true);
            return ret;
        }
    }
}
