package bthdg.tres.alg;

import bthdg.ChartAxe;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.osc.BaseExecutor;
import bthdg.tres.TresExchData;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class FineAlgoWatcher extends BaseAlgoWatcher {
    public static final double MIN_MOVE = 0.033;
    private static final long MOVE_DELAY = 5000; // every 5 sec
    private static final Pair PAIR = Pair.BTC_CNH;

    private final Exchange m_exchange;
    private double m_lastPrice;
    private long m_lastMoveMillis;
    private AccountData m_initAcctData;
    private AccountData m_accountData;
    private TopsData m_initTopsData = new TopsData();
    private TopsData m_topsData = new TopsData();
    private double m_valuateToInit;
    private double m_valuateFromInit;
    private final LinkedList<TresExchData.OrderPoint> m_orders = new LinkedList<TresExchData.OrderPoint>();

    private static void log(String s) { Log.log(s); }

    public FineAlgoWatcher(TresExchData tresExchData, TresAlgo algo) {
        super(tresExchData, algo);
        m_exchange = m_tresExchData.m_ws.exchange();
    }

    @Override public void onValueChange() {
        super.onValueChange();

        double lastPrice = m_algo.lastTickPrice();
        long lastTickTime = m_algo.lastTickTime();
        log("onValueChange: lastTickTime=" + lastTickTime + "; lastPrice=" + lastPrice + " .....................................");

        if ((lastPrice != 0) && (lastTickTime != 0)) {
            m_topsData.put(PAIR, new TopData(lastPrice, lastPrice, lastPrice));
            Currency currencyFrom = PAIR.m_from;
            Currency currencyTo = PAIR.m_to;

            if (m_initAcctData == null) { // first run
                m_initAcctData = new AccountData(m_exchange, 0);
                m_initAcctData.setAvailable(currencyFrom, lastPrice);
                m_initAcctData.setAvailable(currencyTo, 1);
                m_initTopsData.put(PAIR, new TopData(lastPrice, lastPrice, lastPrice));
                log(" initAcctData: PAIR=" + PAIR + "; currencyFrom=" + currencyFrom + "; currencyTo=" + currencyTo);

                m_valuateToInit = m_initAcctData.evaluateAll(m_initTopsData, currencyTo, m_exchange);
                m_valuateFromInit = m_initAcctData.evaluateAll(m_initTopsData, currencyFrom, m_exchange);
                log("  INIT:  valuate" + currencyTo + "=" + m_valuateToInit + " " + currencyTo + "; valuate" + currencyFrom + "=" + m_valuateFromInit + " " + currencyFrom);

                m_accountData = m_initAcctData.copy();
                log("  start acctData=" + m_initAcctData);

            } else {
                double direction = m_algo.getDirectionAdjusted(); // UP/DOWN
                log(" direction=" + Utils.format8(direction));
                double needBuyTo = m_accountData.calcNeedBuyTo(direction, PAIR, m_topsData, m_exchange);
                log(" needBuy" + currencyTo + "=" + Utils.format8(needBuyTo));

                double absOrderSize = Math.abs(needBuyTo);
                OrderSide needOrderSide = (needBuyTo >= 0) ? OrderSide.BUY : OrderSide.SELL;
                log("   needOrderSide=" + needOrderSide + "; absOrderSize=" + Utils.format8(absOrderSize));

                double exchMinOrderToCreate = m_exchange.minOrderToCreate(PAIR);
                if ((absOrderSize >= exchMinOrderToCreate) && (absOrderSize >= MIN_MOVE)) {
                    long timeDiff = lastTickTime - m_lastMoveMillis;
                    if (timeDiff > MOVE_DELAY) {
                        m_accountData.move(currencyFrom, currencyTo, needBuyTo, m_topsData);

                        double gain = totalPriceRatio();
                        log("    gain: " + Utils.format8(gain) + " .....................................");

                        OrderData orderData = new OrderData(PAIR, needOrderSide, lastPrice, absOrderSize);
                        TresExchData.OrderPoint orderPoint = new TresExchData.OrderPoint(orderData, 0, lastPrice, lastPrice, BaseExecutor.TopSource.top_fetch, gain);
                        synchronized (m_orders) {
                            m_orders.add(orderPoint);
                        }
                        m_lastMoveMillis = lastTickTime;
                    } else {
                        log("   need wait. timeDiff=" + timeDiff + " .....................................");
                    }
                } else {
                    log("   small amount to move: " + Utils.format8(absOrderSize) + " .....................................");
                }
            }
            m_lastPrice = lastPrice;
        }
    }

    @Override public double totalPriceRatio() {
        Currency currencyFrom = PAIR.m_from; // cnh=from
        Currency currencyTo = PAIR.m_to;     // btc=to

        double valuateToNow = m_accountData.evaluateAll(m_topsData, currencyTo, m_exchange);
        double valuateFromNow = m_accountData.evaluateAll(m_topsData, currencyFrom, m_exchange);

        double gainTo = valuateToNow / m_valuateToInit;
        double gainFrom = valuateFromNow / m_valuateFromInit;

        double gainAvg = (gainTo + gainFrom) / 2;
        return gainAvg;
    }

    @Override public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        super.paint(g, xTimeAxe, yPriceAxe, cursorPoint);

        if (m_doPaint) {
            List<TresExchData.OrderPoint> points = clonePoints(xTimeAxe);
            log("paint points num=" + points.size());
            paintPoints(g, xTimeAxe, yPriceAxe, points);
        }
    }

    private List<TresExchData.OrderPoint> clonePoints(ChartAxe xTimeAxe) {
        double minTime = xTimeAxe.m_min;
        double maxTime = xTimeAxe.m_max;
        List<TresExchData.OrderPoint> paintPoints = new ArrayList<TresExchData.OrderPoint>();
        TresExchData.OrderPoint rightPlusPoint = null; // paint one extra point at right side
        synchronized (m_orders) {
            for (Iterator<TresExchData.OrderPoint> it = m_orders.descendingIterator(); it.hasNext(); ) {
                TresExchData.OrderPoint point = it.next();
                long timestamp = point.m_tickAge;
                if (timestamp > maxTime) {
                    rightPlusPoint = point;
                    continue;
                }
                if (rightPlusPoint != null) {
                    paintPoints.add(rightPlusPoint);
                    rightPlusPoint = null;
                }
                paintPoints.add(point);
                if (timestamp < minTime) { break; }
            }
        }
        return paintPoints;
    }

    private void paintPoints(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, List<TresExchData.OrderPoint> points) {
        int fontHeight = g.getFont().getSize();
        int lastX = Integer.MAX_VALUE;
        int lastY = Integer.MAX_VALUE;
//        TresExchData.OrderPoint lastPoint = null;
        for (TresExchData.OrderPoint point : points) {
            long millis = point.m_tickAge;
            OrderData order = point.m_order;
            double price = order.m_price;

            int x = xTimeAxe.getPoint(millis);
            int y = yPriceAxe.getPointReverse(price);
            if (lastX != Integer.MAX_VALUE) {
                g.setColor(Colors.BEGIE);
//                g.setColor((lastPoint.m_priceRatio > 1) ? Color.GREEN : Color.RED);
                g.drawLine(lastX, lastY, x, y);
            } else {
                g.setColor(Colors.BEGIE);
                g.drawRect(x - 1, y - 1, 2, 2);
            }

            g.drawString(String.format("p: %1$,.2f", price), x, y + fontHeight);

//            double priceRatio = point.m_priceRatio;
//            g.setColor((priceRatio > 1) ? Color.GREEN : Color.RED);
//            g.drawString(String.format("r: %1$,.5f", priceRatio), x, y + fontHeight * 2);

            double totalPriceRatio = point.m_gainAvg;
            g.setColor((totalPriceRatio > 1) ? Color.GREEN : Color.RED);
            g.drawString(String.format("t: %1$,.5f", totalPriceRatio), x, y + fontHeight * 3);

//            lastPoint = point;
            lastX = x;
            lastY = y;
        }
    }
}
