package bthdg.osc;

import bthdg.Log;
import bthdg.exch.Exchange;
import bthdg.exch.OrderSide;
import bthdg.exch.Pair;
import bthdg.util.Utils;

import java.math.RoundingMode;

//-------------------------------------------------------------------------------
public enum OrderPriceMode {

    MID_OR_MARKET("mid_or_market") {
        @Override public boolean isMarketPrice(BaseExecutor baseExecutor, double orderSize) {
            double avgFillSize = baseExecutor.getAvgFillSize();
            return (avgFillSize == 0) ? false : (orderSize > avgFillSize);
        }
        @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            return calcMidOrderPrice(baseExecutor, exchange, needOrderSide);
        }
    },
    MID_THEN_MARKET("mid_then_market") {
        @Override public boolean isMarketPrice(BaseExecutor baseExecutor, double orderSize) {
            int orderPlaceAttemptCounter = baseExecutor.m_orderPlaceAttemptCounter;
            return (orderPlaceAttemptCounter != 0);
        }
        @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            // directionAdjusted [-1 ... 1]
            double buy = baseExecutor.m_buy;
            double sell = baseExecutor.m_sell;
            log("  buy=" + buy + "; sell=" + sell + "; directionAdjusted=" + directionAdjusted + "; needOrderSide=" + needOrderSide);
            double midPrice = (buy + sell) / 2;
            double bidAskDiff = sell - buy;
            log("   midPrice=" + midPrice + "; bidAskDiff=" + bidAskDiff);
            RoundingMode roundMode = needOrderSide.getPegRoundMode();
            double orderPrice = exchange.roundPrice(midPrice, baseExecutor.m_pair, roundMode);
            log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
            return orderPrice;
        }
    },
    MARKET("market") {
        @Override public boolean isMarketPrice(BaseExecutor baseExecutor, double orderSize) {
            return true;
        }
    },
    DEEP_MKT_AVG("deep_mkt_avg") {
        @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            double buy = baseExecutor.m_buy;
            double sell = baseExecutor.m_sell;
            double diff = sell - buy;
            double midPrice = (buy + sell) / 2;
            double avgBuy = baseExecutor.m_buyAvgCounter.get();
            double avgSell = baseExecutor.m_sellAvgCounter.get();
            double avgDiff = avgSell - avgBuy;
            double diffRate = diff / avgDiff;
            double rate = Math.min(1, diffRate);

            Pair pair = baseExecutor.m_pair;
            log("  buy=" + exchange.roundPriceStr(buy, pair) + "; sell=" + exchange.roundPriceStr(sell, pair) +
                    "; diff=" + exchange.roundPriceStr(diff, pair) + "; midPrice=" + Utils.format5(midPrice) +
                    "; avgBuy=" + Utils.format5(avgBuy) + "; avgSell=" + Utils.format5(avgSell) + "; avgDiff=" + Utils.format5(avgDiff) +
                    "; diffRate=" + Utils.format5(diffRate) + "; rate=" + Utils.format5(rate) +
                    "; needOrderSide=" + needOrderSide);
            boolean isBuy = needOrderSide.isBuy();
            int sideDirection = isBuy ? 1 : -1;
            double offset = rate * avgDiff / 2;
            int orderPlaceAttempt = baseExecutor.m_orderPlaceAttemptCounter;
            double pip = exchange.minExchPriceStep(pair);
            double adjustedPrice = midPrice + offset * sideDirection + BaseExecutor.DEEP_MKT_PIP_RATIO * (1 + orderPlaceAttempt) * (isBuy ? pip : -pip);
            log("    sideDirection=" + sideDirection + " offset=" + offset +
                    "; orderPlaceAttempt=" + orderPlaceAttempt + "; pip=" + pip +
                    "; adjustedPrice=" + adjustedPrice);
            RoundingMode roundMode = needOrderSide.getMktRoundMode();
            double orderPrice = exchange.roundPrice(adjustedPrice, pair, roundMode);
            log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
            return orderPrice;
        }
    },
    MKT_AVG("mkt_avg") {
        @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            double buy = baseExecutor.m_buy;
            double sell = baseExecutor.m_sell;
            double diff = sell - buy;
            double midPrice = (buy + sell) / 2;
            double avgBuy = baseExecutor.m_buyAvgCounter.get();
            double avgSell = baseExecutor.m_sellAvgCounter.get();
            double avgDiff = avgSell - avgBuy;
            double diffRate = diff / avgDiff;
            double rate = Math.min(1, diffRate);
            log("  buy=" + buy + "; sell=" + sell + "; diff=" + diff + "; midPrice=" + midPrice +
                    "; avgBuy=" + avgBuy + "; avgSell=" + avgSell + "; avgDiff=" + avgDiff +
                    "; diffRate=" + diffRate + "; rate=" + rate +
                    "; needOrderSide=" + needOrderSide);
            int sideDirection = needOrderSide.isBuy() ? 1 : -1;
            double offset = rate * avgDiff / 2;
            double adjustedPrice = midPrice + offset * sideDirection;
            log("    sideDirection=" + sideDirection + " offset=" + offset + "; adjustedPrice=" + adjustedPrice);
            RoundingMode roundMode = needOrderSide.getMktRoundMode();
            double orderPrice = exchange.roundPrice(adjustedPrice, baseExecutor.m_pair, roundMode);
            log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
            return orderPrice;
        }
    },
    DEEP_MKT("deep_mkt") {
        @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            double buy = baseExecutor.m_buy;
            double sell = baseExecutor.m_sell;
            boolean isBuy = needOrderSide.isBuy();
            double mktPrice = isBuy ? sell : buy;
            log("  buy=" + buy + "; sell=" + sell + "; mktPrice=" + mktPrice + "; needOrderSide=" + needOrderSide);
            double pip = exchange.minExchPriceStep(baseExecutor.m_pair);
            int orderPlaceAttempt = baseExecutor.m_orderPlaceAttemptCounter;
            double adjustedPrice = mktPrice + BaseExecutor.DEEP_MKT_PIP_RATIO * (1 + orderPlaceAttempt) * (isBuy ? pip : -pip);
            log("    orderPlaceAttempt=" + orderPlaceAttempt +
                    "; pip=" + pip +
                    "; adjustedPrice=" + adjustedPrice);
            RoundingMode roundMode = needOrderSide.getMktRoundMode();
            double orderPrice = exchange.roundPrice(mktPrice, baseExecutor.m_pair, roundMode);
            log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
            return orderPrice;
        }
    },
    MID_TO_MKT("mid_to_mkt") { // mid->mkt
        @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            // directionAdjusted [-1 ... 1]
            double buy = baseExecutor.m_buy;
            double sell = baseExecutor.m_sell;
            log("  buy=" + buy + "; sell=" + sell + "; directionAdjusted=" + directionAdjusted + "; needOrderSide=" + needOrderSide);
            double midPrice = (buy + sell) / 2;
            double bidAskDiff = sell - buy;
            log("   midPrice=" + midPrice + "; bidAskDiff=" + bidAskDiff);
            int orderPlaceAttemptCounter = baseExecutor.m_orderPlaceAttemptCounter;
            double orderPriceCounterCorrection = bidAskDiff / BaseExecutor.MID_TO_MKT_STEPS * orderPlaceAttemptCounter;
            double adjustedPrice = midPrice + (needOrderSide.isBuy() ? orderPriceCounterCorrection : -orderPriceCounterCorrection);
            log("   orderPlaceAttemptCounter=" + orderPlaceAttemptCounter + "; orderPriceCounterCorrection=" + orderPriceCounterCorrection + "; adjustedPrice=" + adjustedPrice);
            RoundingMode roundMode = needOrderSide.getPegRoundMode();
            double orderPrice = exchange.roundPrice(adjustedPrice, baseExecutor.m_pair, roundMode);
            log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
            return orderPrice;
        }
    },
    PEG_TO_MKT("peg_to_mkt") { // peg->mkt
        @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            // directionAdjusted [-1 ... 1]
            double buy = baseExecutor.m_buy;
            double sell = baseExecutor.m_sell;
            log("  buy=" + buy + "; sell=" + sell + "; directionAdjusted=" + directionAdjusted + "; needOrderSide=" + needOrderSide);
            double midPrice = (buy + sell) / 2;
            double bidAskDiff = sell - buy;
            double followMktPrice = needOrderSide.isBuy() ? buy : sell;
            log("   midPrice=" + midPrice + "; bidAskDiff=" + bidAskDiff + "; followMktPrice=" + followMktPrice);
            int orderPlaceAttemptCounter = baseExecutor.m_orderPlaceAttemptCounter;
            double orderPriceCounterCorrection = bidAskDiff / 5 * orderPlaceAttemptCounter;
            double adjustedPrice = followMktPrice + (needOrderSide.isBuy() ? orderPriceCounterCorrection : -orderPriceCounterCorrection);
            log("   orderPlaceAttemptCounter=" + orderPlaceAttemptCounter + "; orderPriceCounterCorrection=" + orderPriceCounterCorrection + "; adjustedPrice=" + adjustedPrice);
            RoundingMode roundMode = needOrderSide.getPegRoundMode();
            double orderPrice = exchange.roundPrice(adjustedPrice, baseExecutor.m_pair, roundMode);
            log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
            return orderPrice;
        }
    },
    MKT("mkt") {
        @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            double buy = baseExecutor.m_buy;
            double sell = baseExecutor.m_sell;
            double mktPrice = needOrderSide.isBuy() ? sell : buy;
            log("  buy=" + buy + "; sell=" + sell + "; mktPrice=" + mktPrice + "; needOrderSide=" + needOrderSide);
            RoundingMode roundMode = needOrderSide.getMktRoundMode();
            double orderPrice = exchange.roundPrice(mktPrice, baseExecutor.m_pair, roundMode);
            log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
            return orderPrice;
        }
    },
    MID("mid") {
        @Override public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            return calcMidOrderPrice(baseExecutor, exchange, needOrderSide);
        }
    };

    private final String m_type;

    OrderPriceMode(String type) {
        m_type = type;
    }

    public String getType() { return m_type; }

    protected static void log(String s) { Log.log(s); }

    public double calcOrderPrice(BaseExecutor baseExecutor, Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
        return 0;
    }

    public boolean isMarketPrice(BaseExecutor baseExecutor, double orderSize) {
        return false;
    }

    public static OrderPriceMode get(String orderAlgoStr) {
        for (OrderPriceMode orderPriceMode : OrderPriceMode.values()) {
            if (orderPriceMode.getType().equals(orderAlgoStr)) {
                return orderPriceMode;
            }
        }
        throw new RuntimeException("OrderPriceMode '" + orderAlgoStr + "' not supported");
    }

    protected double calcMidOrderPrice(BaseExecutor baseExecutor, Exchange exchange, OrderSide needOrderSide) {
        double buy = baseExecutor.m_buy;
        double sell = baseExecutor.m_sell;
        double midPrice = (buy + sell) / 2;
        log("  buy=" + buy + "; sell=" + sell + "; midPrice=" + midPrice + "; needOrderSide=" + needOrderSide);
        RoundingMode roundMode = needOrderSide.getPegRoundMode();
        double orderPrice = exchange.roundPrice(midPrice, baseExecutor.m_pair, roundMode);
        log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
        return orderPrice;
    }
}
