package bthdg.triplet;

import bthdg.*;
import bthdg.exch.Btce;
import bthdg.exch.TopData;

import java.util.ArrayList;
import java.util.Map;

public class Triangle extends ArrayList<PairDirection> {
    public Triangle(Pair pair1, boolean forward1, Pair pair2, boolean forward2, Pair pair3, boolean forward3) {
        add(new PairDirection(pair1, forward1));
        add(new PairDirection(pair2, forward2));
        add(new PairDirection(pair3, forward3));
    }

    public double calcMkt(Map<Pair, TopData> tops, boolean forward) {
        return forward
                ? calcMkt(tops, get(0).get(forward), get(1).get(forward), get(2).get(forward))
                : calcMkt(tops, get(2).get(forward), get(1).get(forward), get(0).get(forward));
    }

    private static double calcMkt(Map<Pair, TopData> tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        return mulMkt(mulMkt(mulMkt((double) 100, tops.get(pair1.m_pair), pair1), tops.get(pair2.m_pair), pair2), tops.get(pair3.m_pair), pair3);
    }

    public double calcMid(Map<Pair, TopData> tops, boolean forward) {
        return forward
                ? calcMid(tops, get(0).get(forward), get(1).get(forward), get(2).get(forward))
                : calcMid(tops, get(2).get(forward), get(1).get(forward), get(0).get(forward));
    }

    private static double calcMid(Map<Pair, TopData> tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        return mulMid(mulMid(mulMid((double) 100, tops.get(pair1.m_pair), pair1), tops.get(pair2.m_pair), pair2), tops.get(pair3.m_pair), pair3);
    }

    public String name() {
        StringBuilder sb = new StringBuilder();
        for (PairDirection pd : this) {
            String name = pd.getName();
            sb.append(name);
            sb.append(";");
        }
        return sb.toString();
    }

    public OnePegCalcData[] calcPegs(Map<Pair, TopData> tops, boolean forward) {
        PairDirection pd0 = get(0).get(forward);
        PairDirection pd1 = get(1).get(forward);
        PairDirection pd2 = get(2).get(forward);
        return forward ? calcPegs(tops, pd0, pd1, pd2) : calcPegs(tops, pd2, pd1, pd0);
    }

    private static OnePegCalcData[] calcPegs(Map<Pair, TopData> tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        TopData top1 = tops.get(pair1.m_pair);
        TopData top2 = tops.get(pair2.m_pair);
        TopData top3 = tops.get(pair3.m_pair);

        return new OnePegCalcData[] {
                new OnePegCalcData(0, mulMkt(mulMkt(mulPeg(100.0, top1, pair1), top2, pair2), top3, pair3),
                        pair1, pegPrice(top1, pair1),
                        pair2, mktPrice(top2, pair2),
                        pair3, mktPrice(top3, pair3)),
                new OnePegCalcData(1, mulMkt(mulPeg(mulMkt(100.0, top1, pair1), top2, pair2), top3, pair3),
                        pair2, pegPrice(top2, pair2),
                        pair3, mktPrice(top3, pair3),
                        pair1, mktPrice(top1, pair1)),
                new OnePegCalcData(2, mulPeg(mulMkt(mulMkt(100.0, top1, pair1), top2, pair2), top3, pair3),
                        pair3, pegPrice(top3, pair3),
                        pair1, mktPrice(top1, pair1),
                        pair2, mktPrice(top2, pair2))
        };
    }

    private static double mulMid(double in, TopData top, PairDirection pd) {
        double price = pd.m_forward ? in / top.getMid() : in * top.getMid();
        double ret = Exchange.BTCE.roundPrice(price, pd.m_pair);
        return ret;
    }

    private static double mulMkt(double in, TopData top, PairDirection pd) {
        double price = pd.m_forward ? in / top.m_ask : in * top.m_bid; // ASK > BID
        double ret = Exchange.BTCE.roundPrice(price, pd.m_pair);
        return ret;
    }
    private static double mktPrice(TopData top, PairDirection pd) {
        double price = pd.m_forward ? top.m_ask : top.m_bid; // ASK > BID
        double ret = Exchange.BTCE.roundPrice(price, pd.m_pair);
        return ret;
    }

    private static double mulPeg(double in, TopData top, PairDirection pd) {
        double price = pd.m_forward ? in / pegPrice(top, pd) : in * pegPrice(top, pd); // ASK > BID
        double ret = Exchange.BTCE.roundPrice(price, pd.m_pair);
        return ret;
    }

    private static double pegPrice(TopData top, PairDirection pd) {
        Pair pair = pd.m_pair;
        double minPriceStep = Btce.minPriceStep(pair);
        double price = pd.m_forward ? OrderSide.BUY.pegPrice(top, minPriceStep) : OrderSide.SELL.pegPrice(top, minPriceStep); // ASK > BID
        double ret = Exchange.BTCE.roundPrice(price, pd.m_pair);
        return ret;
    }
} // Triangle
