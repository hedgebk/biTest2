package bthdg.tres.alg;

import bthdg.tres.TresExchData;
import bthdg.tres.ind.EmaIndicator;

import java.awt.*;

public class EmaAlgo extends TresAlgo {
    final EmaIndicator m_emaIndicator;

    public EmaAlgo(TresExchData tresExchData) {
        super("EMA", tresExchData);
        m_emaIndicator = new EmaIndicator("ema", this);
        m_indicators.add(m_emaIndicator);
    }

    @Override public double lastTickPrice() { return m_emaIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_emaIndicator.lastTickTime(); }
    @Override public Color getColor() { return Color.CYAN; }
    @Override public String getRunAlgoParams() { return "EMA"; }
}
