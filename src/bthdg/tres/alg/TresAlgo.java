package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.ind.TresIndicator;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TresAlgo {
    public final List<TresIndicator> m_indicators = new ArrayList<TresIndicator>();

    public static TresAlgo get(String algoName) {
        if (algoName.equals("coppock")) {
            return new CoppockAlgo();
        }
        throw new RuntimeException("unsupported indicator '" + algoName + "'");
    }

    public void paintAlgo(Graphics g, TresExchData exchData, ChartAxe xTimeAxe, ChartAxe yPriceAxe) {
        for(TresIndicator indicator : m_indicators) {
            indicator.paint(g, exchData, xTimeAxe, yPriceAxe);
        }
    }

    public static class CoppockAlgo extends TresAlgo {
        public CoppockAlgo() {
            m_indicators.add(new CoppockIndicator());
        }
    }
}
