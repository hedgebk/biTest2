package bthdg.tres;

import bthdg.ChartAxe;
import bthdg.tres.alg.TresAlgo;

import java.awt.*;

public class TresAlgoWatcher {
    public final TresAlgo m_algo;

    public TresAlgoWatcher(TresAlgo algo) {
        m_algo = algo;
    }

    public void paint(Graphics g, TresExchData exchData, ChartAxe xTimeAxe, ChartAxe yPriceAxe) {
        m_algo.paintAlgo(g, exchData, xTimeAxe, yPriceAxe);
    }
}
