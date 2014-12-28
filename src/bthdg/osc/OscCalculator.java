package bthdg.osc;

import bthdg.BaseChartPaint;
import bthdg.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class OscCalculator {
    public  static boolean BLEND_AVG = true;

    private final long m_barSize;
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

    private List<OscTick> ret = new ArrayList<OscTick>();
    private List<OscTick> fine = new ArrayList<OscTick>();
    private long m_barsMillisOffset;
    private boolean m_calcFine;

    public OscCalculator(long barSize) {
        m_barSize = barSize;
    }

    public boolean update(BaseChartPaint.Tick tick) {
        boolean newBarStarted = false;
        long stamp = tick.m_stamp;
        if (m_currBarStart == null) {
            startNewBar(stamp, null, 0);
            newBarStarted = true;
        }
        if (stamp < m_currBarEnd) { // one more tick in current bar
            m_close = tick.m_price;
            update(stamp, false);
        } else { // bar fully defined
            update(stamp, true);
            startNewBar(stamp, m_close, tick.m_price);
            newBarStarted = true;
        }
        return newBarStarted;
    }

    private void update(long stamp, boolean finishBar) {
        if (m_prevBarClose == null) {
            return;
        }
        double change = m_close - m_prevBarClose;
        double gain = (change > 0) ? change : 0d;
        double loss = (change < 0) ? -change : 0d;
        Double avgGain = null;
        Double avgLoss = null;
        if ((m_avgGain == null) || !BLEND_AVG) {
            if (m_gains.size() == PaintOsc.LEN1 - 1) {
                avgGain = Utils.avg(m_gains, gain);
                avgLoss = Utils.avg(m_losss, loss);
            }
            if (finishBar) {
                m_gains.add(gain);
                m_losss.add(loss);
                if (m_gains.size() == PaintOsc.LEN1) {
                    m_gains.remove(0);
                    m_losss.remove(0);
                }
            }
        } else {
            avgGain = (m_avgGain * (PaintOsc.LEN1 - 1) + gain) / PaintOsc.LEN1;
            avgLoss = (m_avgLoss * (PaintOsc.LEN1 - 1) + loss) / PaintOsc.LEN1;
        }
        if (avgGain != null) {
            if (finishBar) {
                m_avgGain = avgGain;
                m_avgLoss = avgLoss;
            }
            double rsi = (avgLoss == 0) ? 100 : 100 - (100 / (1 + avgGain / avgLoss));
            if (rsis.size() == PaintOsc.LEN2 - 1) {
                double highest = Utils.max(rsis, rsi);
                double lowest = Utils.min(rsis, rsi);
                double stoch = (rsi - lowest) / (highest - lowest);
                if (stochs.size() == PaintOsc.K - 1) {
                    double stoch1 = Utils.avg(stochs, stoch);
                    if (stoch1s.size() == PaintOsc.D - 1) {
                        double stoch2 = Utils.avg(stoch1s, stoch1);
                        if (finishBar) {
                            OscTick osc = new OscTick(m_currBarStart, stoch1, stoch2);
                            ret.add(osc);
                        }
                        if (m_calcFine) {
                            OscTick osc = new OscTick(stamp, stoch1, stoch2);
                            fine.add(osc);
                        }
                    }
                    if (finishBar) {
                        stoch1s.add(stoch1);
                        if (stoch1s.size() == PaintOsc.D) {
                            stoch1s.remove(0);
                        }
                    }
                }
                if (finishBar) {
                    stochs.add(stoch);
                    if (stochs.size() == PaintOsc.K) {
                        stochs.remove(0);
                    }
                }
            }
            if (finishBar) {
                rsis.add(rsi);
                if (rsis.size() == PaintOsc.LEN2) {
                    rsis.remove(0);
                }
            }
        }
    }

    public List<OscTick> ret() {
        update(0, true);
        return ret;
    }

    public List<OscTick> fine() { return fine; }

    private void startNewBar(long stamp, Double prevBarClose, double close) {
        m_currBarStart = (stamp - m_barsMillisOffset) / m_barSize * m_barSize + m_barsMillisOffset;
        m_currBarEnd = m_currBarStart + m_barSize;
        m_prevBarClose = prevBarClose;
        m_close = close;
    }

    public void setBarsMillisOffset(long barsMillisOffset) { m_barsMillisOffset = barsMillisOffset; }

    public void setCalcFine(boolean calcFine) { m_calcFine = calcFine; }
}
