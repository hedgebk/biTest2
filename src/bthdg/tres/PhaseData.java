package bthdg.tres;

import bthdg.exch.TradeDataLight;
import bthdg.osc.BaseExecutor;
import bthdg.tres.alg.BaseAlgoWatcher;
import bthdg.tres.alg.TresAlgo;
import bthdg.tres.ind.TresIndicator;

import java.util.ArrayList;
import java.util.List;

public class PhaseData {
    final TresExchData m_exchData;
    final int m_phaseIndex;
    final TresOHLCCalculator m_ohlcCalculator;
    private final List<TresIndicator.TresPhasedIndicator> m_phasedTradeIndicators = new ArrayList<TresIndicator.TresPhasedIndicator>();
    private final List<TresIndicator.TresPhasedIndicator> m_phasedTopIndicators = new ArrayList<TresIndicator.TresPhasedIndicator>();

    public PhaseData(TresExchData exchData, int phaseIndex) {
        m_exchData = exchData;
        m_phaseIndex = phaseIndex;

        for (BaseAlgoWatcher algoWatcher : exchData.m_playAlgos) {
            TresAlgo algo = algoWatcher.m_algo;
            registerPhasedIndicators(exchData, phaseIndex, algo);
        }

        m_ohlcCalculator = new TresOHLCCalculator(exchData.m_tres, phaseIndex);
    }

    private void registerPhasedIndicators(TresExchData exchData, int phaseIndex, TresAlgo algo) {
        for (TresIndicator indicator : algo.m_indicators) {
            TresIndicator.TresPhasedIndicator phasedIndicator = indicator.createPhased(exchData, phaseIndex);
            if (phasedIndicator != null) {
                m_phasedTradeIndicators.add(phasedIndicator);
            }
        }
        for (TresIndicator indicator : algo.m_topIndicators) {
            TresIndicator.TresPhasedIndicator phasedIndicator = indicator.createPhased(exchData, phaseIndex);
            if (phasedIndicator != null) {
                m_phasedTopIndicators.add(phasedIndicator);
            }
        }
    }

    public void update(TradeDataLight tdata) {
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        for (TresIndicator.TresPhasedIndicator phasedIndicator : m_phasedTradeIndicators) {
            if (phasedIndicator != null) {
                phasedIndicator.update(timestamp, price);
            }
        }

        m_ohlcCalculator.update(timestamp, price);
    }

    public void update(BaseExecutor.TopDataPoint topDataPoint) {
        for (TresIndicator.TresPhasedIndicator phasedIndicator : m_phasedTopIndicators) {
            if (phasedIndicator != null) {
                phasedIndicator.update(topDataPoint.m_timestamp, topDataPoint.getAvgMid());
            }
        }
    }
}
