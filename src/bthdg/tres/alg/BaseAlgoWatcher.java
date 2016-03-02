package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.tres.TresExchData;

import java.awt.*;

public class BaseAlgoWatcher implements TresAlgo.TresAlgoListener {
    public static double AVG_HALF_BID_ASK_DIF = 0.06;

    protected final TresExchData m_tresExchData;
    public final TresAlgo m_algo;
    private TresAlgo.TresAlgoListener m_listener;

    public void setListener(TresAlgo.TresAlgoListener listener) { m_listener = listener; }

    public BaseAlgoWatcher(TresExchData tresExchData, TresAlgo algo) {
        m_tresExchData = tresExchData;
        m_algo = algo;
        algo.setListener(this);
    }

    // significant value change - in most cases: peak detected
    @Override public void onValueChange() {
        if (m_listener != null) { // notify runAlgoFirst
            m_listener.onValueChange();
        }
    }

    public double getDirectionAdjusted() {
        return m_algo.getDirectionAdjusted();
    }
    public String getRunAlgoParams() {
        return m_algo.getRunAlgoParams();
    }
    public int paintYAxe(Graphics g, ChartAxe xTimeAxe, int yRight, ChartAxe yPriceAxe, ChartAxe yValueAxe) {
        return m_algo.paintYAxe(g, xTimeAxe, yRight, yPriceAxe, yValueAxe);
    }

    public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        m_algo.paintAlgo(g, xTimeAxe, yPriceAxe, cursorPoint);
    }
}
