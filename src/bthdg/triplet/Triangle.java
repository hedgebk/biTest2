package bthdg.triplet;

import bthdg.exch.*;

import java.util.ArrayList;

public class Triangle extends ArrayList<PairDirection> {
    public Triangle(Currency currency1, Currency currency2, Currency currency3) {
        add(PairDirection.get(currency1, currency2));
        add(PairDirection.get(currency2, currency3));
        add(PairDirection.get(currency3, currency1));
    }

    public double calcMkt(TopsData tops, Direction direction) {
        return direction.m_forward
                ? calcMkt(tops, get(0).get(direction), get(1).get(direction), get(2).get(direction))
                : calcMkt(tops, get(2).get(direction), get(1).get(direction), get(0).get(direction));
    }

    public double calcMkt(TopsData tops, Direction direction, double offset) {
        return direction.m_forward
                ? calcMkt(tops, get(0).get(direction), get(1).get(direction), get(2).get(direction), offset)
                : calcMkt(tops, get(2).get(direction), get(1).get(direction), get(0).get(direction), offset);
    }

    private static double calcMkt(TopsData tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        return mulMkt(mulMkt(mulMkt((double) 100, tops.get(pair1.m_pair), pair1), tops.get(pair2.m_pair), pair2), tops.get(pair3.m_pair), pair3);
    }

    private static double calcMkt(TopsData tops, PairDirection pair1, PairDirection pair2, PairDirection pair3, double offset) {
        return mulMkt(mulMkt(mulMkt((double) 100, tops.get(pair1.m_pair), pair1, offset), tops.get(pair2.m_pair), pair2, offset), tops.get(pair3.m_pair), pair3, offset);
    }

    public double calcMid(TopsData tops, Direction direction) {
        return direction.m_forward
                ? calcMid(tops, get(0).get(direction), get(1).get(direction), get(2).get(direction))
                : calcMid(tops, get(2).get(direction), get(1).get(direction), get(0).get(direction));
    }

    private static double calcMid(TopsData tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
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

    public OnePegCalcData[] calcPegs(TopsData tops, Direction direction, double level) {
        PairDirection pd0 = get(0).get(direction);
        PairDirection pd1 = get(1).get(direction);
        PairDirection pd2 = get(2).get(direction);
        return (direction == Direction.FORWARD) ? calcPegs(tops, pd0, pd1, pd2, level) : calcPegs(tops, pd2, pd1, pd0, level);
    }

    private static OnePegCalcData[] calcPegs(TopsData tops, PairDirection pair1, PairDirection pair2, PairDirection pair3, double level) {
        TopData top1 = tops.get(pair1.m_pair);
        TopData top2 = tops.get(pair2.m_pair);
        TopData top3 = tops.get(pair3.m_pair);
        double offset = Triplet.MKT_OFFSET_PRICE_MINUS;

        return new OnePegCalcData[] {
                new OnePegCalcData(0, level,
                        mulMkt(mulMkt(mulPeg(100.0, top1, pair1), top2, pair2), top3, pair3),
                        mulMkt(mulMkt(mulPeg(100.0, top1, pair1), top2, pair2, offset), top3, pair3, offset),
                        pair1, pegPrice(top1, pair1),
                        pair2, mktPrice(top2, pair2), mktPrice(top2, pair2, offset),
                        pair3, mktPrice(top3, pair3), mktPrice(top3, pair3, offset)),
                new OnePegCalcData(1, level,
                        mulMkt(mulPeg(mulMkt(100.0, top1, pair1), top2, pair2), top3, pair3),
                        mulMkt(mulPeg(mulMkt(100.0, top1, pair1, offset), top2, pair2), top3, pair3, offset),
                        pair2, pegPrice(top2, pair2),
                        pair3, mktPrice(top3, pair3), mktPrice(top3, pair3, offset),
                        pair1, mktPrice(top1, pair1), mktPrice(top1, pair1, offset)),
                new OnePegCalcData(2, level,
                        mulPeg(mulMkt(mulMkt(100.0, top1, pair1), top2, pair2), top3, pair3),
                        mulPeg(mulMkt(mulMkt(100.0, top1, pair1, offset), top2, pair2, offset), top3, pair3),
                        pair3, pegPrice(top3, pair3),
                        pair1, mktPrice(top1, pair1), mktPrice(top1, pair1, offset),
                        pair2, mktPrice(top2, pair2), mktPrice(top2, pair2, offset))
        };
    }

    public static double mulMid(double in, TopData top, PairDirection pd) {
        double mid = midPrice(top, pd);
        double ret = pd.isForward() ? in / mid : in * mid;
        return ret;
    }

    public static double midPrice(TopData top, PairDirection pd) {
        double price = top.getMid();
        double ret = Triplet.roundPrice(price, pd.m_pair);
        return ret;
    }

    public static double mulMkt(double in, TopData top, PairDirection pd) {
        double mkt = mktPrice(top, pd);
        double ret = pd.isForward() ? in / mkt : in * mkt; // ASK > BID
        return ret;
    }
    public static double mktPrice(TopData top, PairDirection pd) {
        double price = pd.getSide().mktPrice(top); // ASK > BID
        return price;
    }
    public static double followPrice(TopData top, PairDirection pd) {
        double price = pd.getSide().opposite().mktPrice(top);
        return price;
    }

    public static double mulMkt(double in, TopData top, PairDirection pd, double offset) {
        double mktPrice = mktPrice(top, pd, offset);
        double ret = pd.isForward() ? in / mktPrice : in * mktPrice; // ASK > BID
        return ret;
    }

    public static double mktPrice(TopData top, PairDirection pd, double offset) {
        double delta = (top.m_ask - top.m_bid) * offset;
        OrderSide side = pd.getSide();
        double mktPrice = side.mktPrice(top);
        double price = pd.isForward() ? mktPrice - delta : mktPrice + delta; // ASK > BID
        // the price is changed from quoted by exchange - need to be rounded
        double ret = Triplet.s_exchange.roundPrice(price, pd.m_pair, side.getMktRoundMode());
        return ret;
    }

    public static double mulPeg(double in, TopData top, PairDirection pd) {
        double pegPrice = pegPrice(top, pd);
        double ret = pd.isForward() ? in / pegPrice : in * pegPrice; // ASK > BID
        return ret;
    }

    public static double pegPrice(TopData top, PairDirection pd) {
        Pair pair = pd.m_pair;
        double minPriceStep = Triplet.s_exchange.minOurPriceStep(pair);
        double exchStep = Triplet.s_exchange.minExchPriceStep(pair);
        OrderSide side = pd.getSide(); // ASK > BID
        double price = side.pegPrice(top, minPriceStep, exchStep); // the price is changed here from quoted by exchange - need to be rounded
        double ret = Triplet.s_exchange.roundPrice(price, pair, side.getPegRoundMode()); // round to direction based on order side
        return ret;
    }

    public double level(AccountData account) {
        double fee1 = get(0).getFee(account, Triplet.s_exchange);
        double fee2 = get(1).getFee(account, Triplet.s_exchange);
        double fee3 = get(2).getFee(account, Triplet.s_exchange);
        double feeRate = (1 + fee1) * (1 + fee2) * (1 + fee3);
        return feeRate * 100;  // 100.602408
    }
} // Triangle
