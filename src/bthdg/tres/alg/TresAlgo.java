package bthdg.tres.alg;

import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.ind.TresIndicator;

import java.util.ArrayList;
import java.util.List;

public class TresAlgo {
    public final List<TresIndicator> m_indicarors = new ArrayList<TresIndicator>();

    public static TresAlgo get(String algoName) {
        if (algoName.equals("coppock")) {
            return new CoppockAlgo();
        }
        throw new RuntimeException("unsupported indicator '" + algoName + "'");
    }

    public static class CoppockAlgo extends TresAlgo {
        public CoppockAlgo() {
            m_indicarors.add(new CoppockIndicator());
        }
    }
}
