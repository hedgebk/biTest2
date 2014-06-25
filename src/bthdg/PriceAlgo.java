package bthdg;

import bthdg.exch.OrderSide;
import bthdg.exch.TopData;

public enum PriceAlgo {
    MARKET {
        @Override public double getRefPrice(TopData top, OrderSide orderSide) { return orderSide.opposite().mktPrice(top); }
        @Override public double getMktPrice(TopData top, OrderSide side) { return side.mktPrice(top); }
    },
    PEG  {
        @Override public double getRefPrice(TopData top, OrderSide orderSide) { return top.getMid(); }
        @Override public double getMktPrice(TopData top, OrderSide side) { return side.pegPrice(top, null, null); }
    };

    public double getRefPrice(TopData top, OrderSide orderSide) { return 0; }
    public double getMktPrice(TopData top, OrderSide side) { return 0; }
}
