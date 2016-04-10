package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.TresIndicator;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class ComboAlgo extends TresAlgo implements TresAlgo.TresAlgoListener {
    public static String CONFIG = "emas~,c+o3";
//    public static String CONFIG = "emas~";

    private final List<TresAlgo> m_algos = new ArrayList<TresAlgo>();
    private final ComboIndicator m_comboIndicator;
    private double m_dir;
    private boolean m_changed;

    @Override public double lastTickPrice() { return m_tresExchData.m_lastPrice; }
    @Override public long lastTickTime() { return m_tresExchData.m_lastTickMillis; }
    @Override public Color getColor() { return Color.CYAN; }
    @Override public String getRunAlgoParams() { return "Combo: " + CONFIG; }

    public ComboAlgo(TresExchData tresExchData) {
        super("COMBO", tresExchData);

        String[] configs = CONFIG.split(",");
        for (String algoName : configs) {
            TresAlgo algo = TresAlgo.get(algoName, tresExchData);
            m_algos.add(algo);
            algo.setListener(this);
        }

        m_comboIndicator = new ComboIndicator(this);
        m_indicators.add(m_comboIndicator);
    }


    @Override public void preUpdate(TradeDataLight tdata) {
        m_changed = false;
    }

    @Override public void onValueChange() {
        m_changed = true;
    }

    @Override public void postUpdate(TradeDataLight tdata) {
        if (m_changed) {
            double avg = 0;
            for (TresAlgo algo : m_algos) {
                avg += algo.getDirectionAdjusted();
            }
            avg /= m_algos.size();
            if (m_dir != avg) {
                m_dir = avg;
                long lastTime = 0;
                for (TresAlgo algo : m_algos) {
                    long l = algo.lastTickTime();
                    if(l > lastTime) {
                        lastTime = l;
                    }
                }
                m_comboIndicator.addBar(new ChartPoint(lastTime, avg));
                notifyListener();
            }
            m_changed = false;
        }
    }

    @Override public double getDirectionAdjusted() { return m_dir; }
    @Override public Direction getDirection() { return Direction.get(m_dir); }


    // -----------------------------------------------------------------
    private static class ComboIndicator extends TresIndicator {
        private final ComboAlgo m_comboAlgo;

        @Override public Color getColor() { return Color.red; }
        @Override protected boolean countPeaks() { return false; }
        @Override protected boolean useValueAxe() { return true; }

        public ComboIndicator(ComboAlgo comboAlgo) {
            super("combo", 0.0, comboAlgo);
            m_comboAlgo = comboAlgo;
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
            return new ComboPhasedIndicator(this, exchData, phaseIndex);
        }
    }


    // -----------------------------------------------------------------
    private static class ComboPhasedIndicator extends TresIndicator.TresPhasedIndicator {
        private final List<TresIndicator.TresPhasedIndicator> m_phased = new ArrayList<TresIndicator.TresPhasedIndicator>();

        @Override public Color getColor() { return Color.ORANGE; }
        @Override public double lastTickPrice() { return 0; }
        @Override public long lastTickTime() { return 0; }

        public ComboPhasedIndicator(ComboIndicator comboIndicator, TresExchData exchData, int phaseIndex) {
            super(comboIndicator, exchData, phaseIndex, null);

            for (TresAlgo algo : comboIndicator.m_comboAlgo.m_algos) {
                for (TresIndicator indicator : algo.m_indicators) {
                    TresIndicator.TresPhasedIndicator phased = indicator.createPhased(exchData, phaseIndex);
                    if (phased != null) {
                        m_phased.add(phased);
                    }
                }
            }
        }

        @Override public boolean update(TradeDataLight tdata) {
            for (TresIndicator.TresPhasedIndicator phasedIndicator : m_phased) {
                phasedIndicator.update(tdata);
            }
            return true;
        }
    }
}
