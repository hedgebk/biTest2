package bthdg.osc;

import bthdg.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class OscCalculator {
    public static boolean BLEND_AVG = true;
    public static int LEN1 = 14;
    public static int LEN2 = 14;
    public static int K = 3;
    public static int D = 3;

    private final long m_barSize;
    private final long m_barsMillisOffset;

    private Long m_currBarStart;
    private Long m_currBarEnd;
    private double m_close;
    private Double m_prevBarClose;
    private List<Double> m_gains = new ArrayList<Double>();
    private List<Double> m_losss = new ArrayList<Double>();
    private Double m_avgGain = null;
    private Double m_avgLoss = null;
    private List<Double> rsis = new ArrayList<Double>();
    private List<Double> stochs = new ArrayList<Double>();
    private List<Double> stoch1s = new ArrayList<Double>();

    public void fine(long stamp, double stoch1, double stoch2) {}
    public void bar(Long barStart, double stoch1, double stoch2) {}

    public OscCalculator(long barSize) {
        this(barSize, 0);
    }

    public OscCalculator(long barSize, long barsMillisOffset) {
        m_barSize = barSize;
        m_barsMillisOffset = barsMillisOffset;
    }

    public boolean update(long stamp, double price) {
        boolean newBarStarted = false;
        if (m_currBarStart == null) {
            startNewBar(stamp, null, 0);
            newBarStarted = true;
        }
        if (stamp < m_currBarEnd) { // one more tick in current bar
            m_close = price;
            update(stamp, false);
        } else { // bar fully defined
            update(stamp, true);
            startNewBar(stamp, m_close, price);
            newBarStarted = true;
        }
        return newBarStarted;
    }

    protected void update(long stamp, boolean finishBar) {
        if (m_prevBarClose == null) {
            return;
        }
        double change = m_close - m_prevBarClose;
        double gain = (change > 0) ? change : 0d;
        double loss = (change < 0) ? -change : 0d;
        Double avgGain = null;
        Double avgLoss = null;
        if ((m_avgGain == null) || !BLEND_AVG) {
            if (m_gains.size() == LEN1 - 1) {
                avgGain = Utils.avg(m_gains, gain);
                avgLoss = Utils.avg(m_losss, loss);
            }
            if (finishBar) {
                m_gains.add(gain);
                m_losss.add(loss);
                if (m_gains.size() == LEN1) {
                    m_gains.remove(0);
                    m_losss.remove(0);
                }
            }
        } else {
            avgGain = (m_avgGain * (LEN1 - 1) + gain) / LEN1;
            avgLoss = (m_avgLoss * (LEN1 - 1) + loss) / LEN1;
        }
        if (avgGain != null) {
            if (finishBar) {
                m_avgGain = avgGain;
                m_avgLoss = avgLoss;
            }
            double rsi = (avgLoss == 0) ? 100 : 100 - (100 / (1 + avgGain / avgLoss));
            if (rsis.size() == LEN2 - 1) {
                double highest = Utils.max(rsis, rsi);
                double lowest = Utils.min(rsis, rsi);
                double stoch = (rsi - lowest) / (highest - lowest);
                if (stochs.size() == K - 1) {
                    double stoch1 = Utils.avg(stochs, stoch);
                    if (stoch1s.size() == D - 1) {
                        double stoch2 = Utils.avg(stoch1s, stoch1);

                        fine(stamp, stoch1, stoch2);
                        if (finishBar) {
                            bar(m_currBarStart, stoch1, stoch2);
                        }
                    }
                    if (finishBar) {
                        stoch1s.add(stoch1);
                        if (stoch1s.size() == D) {
                            stoch1s.remove(0);
                        }
                    }
                }
                if (finishBar) {
                    stochs.add(stoch);
                    if (stochs.size() == K) {
                        stochs.remove(0);
                    }
                }
            }
            if (finishBar) {
                rsis.add(rsi);
                if (rsis.size() == LEN2) {
                    rsis.remove(0);
                }
            }
        }
    }

    private void startNewBar(long stamp, Double prevBarClose, double close) {
        m_currBarStart = (stamp - m_barsMillisOffset) / m_barSize * m_barSize + m_barsMillisOffset;
        m_currBarEnd = m_currBarStart + m_barSize;
        m_prevBarClose = prevBarClose;
        m_close = close;
    }

    public static class SimpleOscCalculator extends OscCalculator {
        private List<OscTick> ret = new ArrayList<OscTick>();
        private List<OscTick> fine = new ArrayList<OscTick>();
        private boolean m_calcFine;

        public List<OscTick> fine() { return fine; }

        public void setCalcFine(boolean calcFine) { m_calcFine = calcFine; }

        public SimpleOscCalculator(long barSize, long barsMillisOffset) {
            super(barSize, barsMillisOffset);
        }


        @Override public void bar(Long barStart, double stoch1, double stoch2) {
            OscTick osc = new OscTick(barStart, stoch1, stoch2);
            ret.add(osc);
        }

        @Override public void fine(long stamp, double stoch1, double stoch2) {
            if(m_calcFine) {
                OscTick osc = new OscTick(stamp, stoch1, stoch2);
                fine.add(osc);
            }
        }

        public List<OscTick> ret() {
            update(0, true);
            return ret;
        }
    }
}
