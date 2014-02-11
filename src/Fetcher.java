import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class Fetcher {
    private static final boolean USE_TOP_TEST_STR = false;
    private static final boolean USE_DEEP_TEST_STR = false;
    private static final boolean USE_TRADES_TEST_STR = false;
    public static final int MIN_DELAY = 1500;
    public static final long MOVING_AVERAGE = 25 * 60 * 1000;
    public static final int EXPECTED_GAIN = 3; // 5
    public static final double COMMISSION_AMOUNT = 7; // 6.8; // 7 ... 19
    public static final double TARGET_DELTA = COMMISSION_AMOUNT + EXPECTED_GAIN;
    public static final double HALF_TARGET_DELTA = TARGET_DELTA / 2;

    private static final int MAX_READ_ATTEMPTS = 100; // 5;
    public static final int START_REPEAT_DELAY = 200;
    public static final int REPEAT_DELAY_INCREMENT = 200;

    public static STATE s_state = STATE.NONE;
    private static double s_ask; // bought @
    private static double s_bid; // sold @
    private static double s_diff; // open diff
    private static double s_totalGain;
    private static int s_totalRuns;
    private static long s_openMillis;

    public static void main(String[] args) {
        System.out.println("Started.  millis=" + System.currentTimeMillis());
        try {
//            TradesData trades1 = fetchTrades(Exchange.BITSTAMP);
//            TradesData trades2 = fetchTrades(Exchange.BTCE);

//            TopData bitstamp = fetchTop(Exchange.BITSTAMP);
//            TopData btce = fetchTop(Exchange.BTCE);
//            TopData campbx = fetchTop(Exchange.CAMPBX);
//            System.out.println("----------------------------------------------");
//            log(Exchange.BITSTAMP, bitstamp);
//            log(Exchange.BTCE, btce);
//            log(Exchange.CAMPBX, campbx);

//            DeepData deep1 = fetchDeep(Exchange.BTCE);
//            DeepData deep2 = fetchDeep(Exchange.BITSTAMP);
//            printDeeps(deep1, deep2);

            pool(Exchange.BITSTAMP, Exchange.BTCE);
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        } finally {
            System.out.println("@@@@@@@@@@@@@@  total: runs=" + s_totalRuns + ", gain=" + s_totalGain);
        }
    }

    private static void pool(Exchange exch1, Exchange exch2) throws Exception {
        PrintStream os = new PrintStream(new FileOutputStream("fetch.leg.txt"));
        try {
            pool(exch1, exch2, os);
        } finally {
            os.close();
        }
    }

    private static void pool(Exchange exch1, Exchange exch2, PrintStream os) throws Exception {
        ExchangeData exch1data = new ExchangeData(exch1);
        ExchangeData exch2data = new ExchangeData(exch2);

        long start = System.currentTimeMillis();
        AverageCounter averageCounter = new AverageCounter(MOVING_AVERAGE);
        long delay; // start with 5sec
        int iterationCounter = 0;
        while(true) {
            System.out.println("----------------------------------------------");
            iterationCounter++;

            exch1data.checkExecutedBrackets();
            exch2data.checkExecutedBrackets();

            long millis0 = System.currentTimeMillis();
            TopData top1 = fetchTop(exch1);
            long top1Millis = System.currentTimeMillis();
            TopData top2 = fetchTop(exch2);
            long top2Millis = System.currentTimeMillis();
            System.out.println("  loaded in " + (top1Millis - millis0) + " and " + (top2Millis - top1Millis) + " ms");

            log(exch1, top1);
            log(exch2, top2);
            System.out.println();

            TopData.TopDataEx diff = calcDiff(top1, top2); // top1 - top2
            System.out.print("diff=" + diff);

            double midDiff = diff.m_mid;
            long millis = System.currentTimeMillis();
            double midDiffAverage = averageCounter.add(millis, midDiff);
            System.out.println(";  avg=" + Utils.XX_YYYY.format(midDiffAverage));

            double delta = midDiff - midDiffAverage;

            exch1data.m_sumBidAskDiff += (top1.m_ask - top1.m_bid); // ASK > BID
            exch2data.m_sumBidAskDiff += (top2.m_ask - top2.m_bid);
            exch1data.m_bidAskDiffCounter++;
            exch2data.m_bidAskDiffCounter++;

            System.out.println("delta: " + Utils.XX_YYYY.format(delta) + ",  waiting for: " + HALF_TARGET_DELTA +
                               ", avgBidAskDiff1=" + exch1data.avgBidAskDiffStr() +
                               ", avgBidAskDiff2=" + exch2data.avgBidAskDiffStr() +
                               ", millis=" + millis +
                               ", tile=" + new Date(millis));
            os.println("" + millis + "\t" + Utils.XX_YYYY.format(midDiff) + "\t" + Utils.XX_YYYY.format(midDiffAverage));
            double waitDistance = TARGET_DELTA;
            if( s_state == STATE.NONE ) {
                double move2to1 = top1.m_bid - top2.m_ask;
                double move1to2 = top2.m_bid - top1.m_ask;
                double q1 = move2to1 - midDiffAverage;
                double q2 = move1to2 + midDiffAverage;
                if( q1 >= HALF_TARGET_DELTA ) {
                    System.out.println("@@@@@@@@@@@@@@ we can open TOP @ MKT");
                    open(exch1, exch2, top1, top2, diff, STATE.TOP);
                } else if( q2 >= HALF_TARGET_DELTA ) {
                    System.out.println("@@@@@@@@@@@@@@ we can open BOTTOM @ MKT");
                    open(exch2, exch1, top2, top1, diff, STATE.BOTTOM);
                } else {
                    exch1data.placeBrackets(top1, top2, midDiffAverage);
                    exch2data.placeBrackets(top2, top1, -midDiffAverage);
                    waitDistance = HALF_TARGET_DELTA - Math.abs(delta);
                }
            } else if( s_state == STATE.TOP ) {
                waitDistance = delta - (s_diff - TARGET_DELTA);
                if( s_diff - midDiff > TARGET_DELTA ) {
                    System.out.println("@@@@@@@@@@@@@@ we can close TOP @ MKT");
                    close(exch1data, exch2data, top1, top2);
                } else if( midDiffAverage - s_diff > TARGET_DELTA ) {
                    System.out.println("@@@@@@@@@@@@@@ we should drop TOP");
                    close(exch1data, exch2data, top1, top2);
                }
            } else if( s_state == STATE.BOTTOM ) {
                waitDistance = (s_diff + TARGET_DELTA) - delta;
                if( midDiff - s_diff > TARGET_DELTA ) {
                    System.out.println("@@@@@@@@@@@@@@ we can close BOTTOM @ MKT");
                    close(exch2data, exch1data, top2, top1);
                } else if (s_diff - midDiffAverage > TARGET_DELTA) {
                    System.out.println("@@@@@@@@@@@@@@ we should drop BOTTOM");
                    close(exch2data, exch1data, top2, top1);
                }
            }
            System.out.println("waitDistance="+waitDistance);
            delay = (long) (MIN_DELAY + MIN_DELAY * 4 * Math.min(1, Math.abs(waitDistance) / HALF_TARGET_DELTA));
            delay = Math.max(delay,1000);
            long running = System.currentTimeMillis() - start;
            System.out.println("wait "+delay+" ms. total running " + Utils.millisToDHMSStr(running) + ", counter="+iterationCounter);
            Thread.sleep(delay);
        }
    }

    private static double calcAmountToOpen() {
        return 1.0; // 1.0 BTC
    }

    private static void close(ExchangeData exch1data, ExchangeData exch2data, TopData top1, TopData top2) {
        double bid = top2.m_bid;
        System.out.println("@@@@@@@@@@@@@@ will sell MKT on " + exch2data.exchName() + " @ " + bid);
        double gain2 = bid - s_ask;
        System.out.println("@@@@@@@@@@@@@@  was bought @ " + s_ask + ", gain=" + gain2);

        double ask = top1.m_ask;
        System.out.println("@@@@@@@@@@@@@@ will buy MKT on " + exch1data.exchName() + " @ " + ask);
        double gain1 = s_bid - ask;
        System.out.println("@@@@@@@@@@@@@@  was sold @ " + s_bid + ", gain=" + gain1);

        double gain = gain1 + gain2;
        System.out.println("@@@@@@@@@@@@@@ this gain=" + gain);
        s_totalGain += gain;
        s_totalRuns++;
        System.out.println("@@@@@@@@@@@@@@  total: runs=" + s_totalRuns + ", gain=" + s_totalGain);

        s_state = STATE.NONE;
        long doneMillis = System.currentTimeMillis() - s_openMillis;
        System.out.println("@@@@@@@@@@@@@@   done in " + Utils.millisToDHMSStr(doneMillis));
    }

    private static void open(Exchange sellExch, Exchange buyExch, TopData sellExchTop, TopData buyExchTop,
                             TopData.TopDataEx diff, STATE state) {
        // ASK > BID
        s_ask = buyExchTop.m_ask;
        System.out.println("@@@@@@@@@@@@@@ will buy on "+buyExch.m_name+" @ " + s_ask);
        s_bid = sellExchTop.m_bid;
        System.out.println("@@@@@@@@@@@@@@ will sell on "+sellExch.m_name+" @ " + s_bid);
        s_diff = s_bid-s_ask; // diff.m_mid;

        s_state = state;
        s_openMillis = System.currentTimeMillis();
    }

    public enum STATE {
        NONE, TOP, BOTTOM
    }

    private static TopData.TopDataEx calcDiff(TopData top1, TopData top2) {
        return new TopData.TopDataEx(top1.m_bid - top2.m_bid, top1.m_ask - top2.m_ask, top1.m_last - top2.m_last,
                                   ((top1.m_bid + top1.m_ask) - (top2.m_bid + top2.m_ask))/2 );
    }

    private static <T> void  removeOld(long limit, TreeMap<Long,T> map) {
        SortedMap<Long,T> toRemove = map.headMap(limit);
        if( !toRemove.isEmpty() ) {
//            System.out.println("keys toRemove="+toRemove);
            ArrayList<Long> keys = new ArrayList<Long>(toRemove.keySet());
            for( Long key: keys) {
                map.remove(key);
            }
        }
    }

    private static void printDeeps(DeepData deep1, DeepData deep2) {
        for( int i = 0; i < 5; i++ ) {
            DeepData.Deep bid1 = deep1.m_bids.get(i);
            DeepData.Deep ask1 = deep1.m_asks.get(i);
            DeepData.Deep bid2 = deep2.m_bids.get(i);
            DeepData.Deep ask2 = deep2.m_asks.get(i);
            System.out.println(Utils.XX_YYYY.format(bid1.m_size)+"@"+ Utils.XX_YYYY.format(bid1.m_price)+"  " +
                               Utils.XX_YYYY.format(ask1.m_size)+"@"+ Utils.XX_YYYY.format(ask1.m_price)+"      " +
                               Utils.XX_YYYY.format(bid2.m_size)+"@"+ Utils.XX_YYYY.format(bid2.m_price)+"  " +
                               Utils.XX_YYYY.format(ask2.m_size)+"@"+ Utils.XX_YYYY.format(ask2.m_price)+"  ");
        }
    }

    private static TradesData fetchTrades(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TRADES);
        System.out.println("jObj=" + jObj);
        TradesData tradesData = exchange.parseTrades(jObj);
        System.out.println("tradesData=" + tradesData);
        return tradesData;
    }

    private static DeepData fetchDeep(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.DEEP);
        System.out.println("jObj=" + jObj);
        DeepData deepData = exchange.parseDeep(jObj);
        System.out.println("deepData=" + deepData);
        return deepData;
    }

    private static TopData fetchTop(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TOP);
        //System.out.println("jObj=" + jObj);
        TopData topData = exchange.parseTop(jObj);
        //System.out.println("topData=" + topData);
        return topData;
    }

    private static Object fetch(Exchange exchange, FetchCommand command) throws Exception {
//        long millis = System.currentTimeMillis();
        long delay = START_REPEAT_DELAY;
        for( int attempt = 1; attempt <= MAX_READ_ATTEMPTS; attempt++ ) {
            try {
                return fetchOnce(exchange, command);
            } catch (Exception e) {
                System.out.println(" loading error (attempt "+attempt+"): " + e);
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            Thread.sleep(delay);
            delay += REPEAT_DELAY_INCREMENT;
        }
        throw new RuntimeException("unable to load after "+MAX_READ_ATTEMPTS+" attempts");
    }

    private static Object fetchOnce(Exchange exchange, FetchCommand command) throws Exception {
        Reader reader;
        if (command.useTestStr()) {
            String str = command.getTestStr(exchange);
            reader = new StringReader(str);
        } else {
            String location = command.getApiEndpoint(exchange);
            System.out.print("loading from " + location + "...  ");
            URL url = new URL(location);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            //con.setRequestMethod("POST");
            con.setUseCaches(false) ;
            con.setConnectTimeout(5000);
            con.setReadTimeout(7000);
            //con.setDoOutput(true);

            //con.setRequestProperty("Content-Type","application/x-www-form-urlencoded") ;
            con.setRequestProperty("User-Agent","Mozilla/5.0") ; //  USER_AGENT     // 'Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)'
                                // "Mozilla/5.0 (compatible; BTCE-API/1.0; MSIE 6.0 compatible; +https://github.com/abwaters/bitstamp-api)"
            //con.setRequestProperty("Accept","application/json, text/javascript, */*; q=0.01");

            InputStream inputStream = con.getInputStream(); //url.openStream();
                // 502 Bad Gateway - The server was acting as a gateway or proxy and received an invalid response from the upstream server
                // 403 Forbidden
            int responseCode = con.getResponseCode();
            if(responseCode != HttpURLConnection.HTTP_OK) {
                System.out.print(" responseCode: " + responseCode);
            }

            int available = inputStream.available();
            System.out.print(" available " + available + " bytes; ");
            reader = new InputStreamReader(inputStream);
        }
        try {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(reader);
            System.out.print("parsed; ");
            return obj;
        } finally {
            reader.close();
        }
    }

    private static void log(Exchange exch, TopData top) {
        System.out.print(exch.m_name +
                ": bid: " + Utils.XX_YYYY.format(top.m_bid) +
                ", ask: " + Utils.XX_YYYY.format(top.m_ask) +
                ", last: " + Utils.XX_YYYY.format(top.m_last) +
                ", bid_ask_dif: " + Utils.XX_YYYY.format(top.m_ask-top.m_bid) +
                "\t\t");
    }



//    The following snippet uses the Google HTTP client library and json-simple to issue a Freebase query and parse the result.
//
//    HttpTransport httpTransport = new NetHttpTransport();
//    HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
//    JSONParser parser = new JSONParser();
//    String query = "[{\"limit\": 5,\"name\":null,\"type\":\"/medicine/disease\"}]";
//    GenericUrl url = new GenericUrl("https://www.googleapis.com/freebase/v1/mqlread");
//    url.put("key", "YOUR-API-KEY-GOES-HERE");
//    url.put("query", query);
//    HttpRequest request = requestFactory.buildGetRequest(url);
//    HttpResponse httpResponse = request.execute();
//    JSONObject response = (JSONObject)parser.parse(httpResponse.parseAsString());
//    JSONArray results = (JSONArray)response.get("result");
//    for (Object result : results) {
//      System.out.println(result.get("name").toString());
//    }

    public static double getDouble(JSONObject object, String key) {
        return getDouble(object.get(key));
    }

    public static double getDouble(Object obj) {
        if(obj instanceof Double) {
            return (Double) obj;
        } else if(obj instanceof Long) {
            return ((Long) obj).doubleValue();
        } else if(obj instanceof String) {
            return Double.parseDouble((String) obj);
        } else {
            throw new RuntimeException("un-supported class: " + obj.getClass());
        }
    }

    public static long getLong(Object obj) {
        if(obj instanceof Long) {
            return (Long) obj;
        } else if(obj instanceof String) {
            return Long.parseLong((String) obj);
        } else {
            throw new RuntimeException("un-supported class: " + obj.getClass());
        }
    }

    public enum FetchCommand {
        TOP {
            @Override public String getTestStr(Exchange exchange) { return exchange.m_topTestStr; }
            @Override public String getApiEndpoint(Exchange exchange) { return exchange.m_apiTopEndpoint; }
            @Override public boolean useTestStr() { return USE_TOP_TEST_STR; }
        },
        DEEP {
            @Override public String getTestStr(Exchange exchange) { return exchange.deepTestStr(); }
            @Override public String getApiEndpoint(Exchange exchange) { return exchange.m_apiDeepEndpoint; }
            @Override public boolean useTestStr() { return USE_DEEP_TEST_STR; }
        },
        TRADES {
            @Override public String getTestStr(Exchange exchange) { return exchange.m_tradesTestStr; }
            @Override public String getApiEndpoint(Exchange exchange) { return exchange.m_apiTradesEndpoint; }
            @Override public boolean useTestStr() { return USE_TRADES_TEST_STR; }
        };

        public String getTestStr(Exchange exchange) { return null; }
        public String getApiEndpoint(Exchange exchange) { return null; }
        public boolean useTestStr() { return false; }
    }

    public static class AverageCounter {
        public final TreeMap<Long,Double> m_map = new TreeMap<Long, Double>(); // sorted by time
        private final long m_limit;

        public AverageCounter(long limit) {
            m_limit = limit;
        }

        public double add(long millis, double addValue) {
            long limit = millis - m_limit;
            removeOld(limit, m_map);
            m_map.put(millis, addValue);

            double summ = 0.0;
            int counter = 0;
            for(Double value: m_map.values()) {
                summ += value;
                counter++;
            }
            return summ/counter;
        }
    }

    public static class ExchangeData {
        public final Exchange m_exch;
        // to calc average diff between bid and ask on exchange
        public double m_sumBidAskDiff = 0;
        public int m_bidAskDiffCounter = 0;
        //bitstamp avgBidAskDiff1=2.6743, btc-eavgBidAskDiff2=1.3724
        //bitstamp avgBidAskDiff1=2.1741, btc-e avgBidAskDiff2=1.2498
        //bitstamp avgBidAskDiff1=1.9107, btc-e avgBidAskDiff2=1.3497

        // moving bracket orders
        public OrderData m_buyBracketOrder;
        public OrderData m_sellBracketOrder;
        public Long m_lastProcessedTradesTime = 0l;
        private OrderData m_buyMktBracketOrder;
        private OrderData m_sellMktBracketOrder;

        public ExchangeData(Exchange exch) {
            m_exch = exch;
        }

        public double avgBidAskDiff() { return m_sumBidAskDiff/m_bidAskDiffCounter; }
        public String avgBidAskDiffStr() { return Utils.XX_YYYY.format(avgBidAskDiff()); }
        private String exchName() { return m_exch.m_name; }

        public void checkExecutedBrackets() throws Exception {
            if ((m_buyBracketOrder != null) || (m_sellBracketOrder != null)) {
                long millis0 = System.currentTimeMillis();
                TradesData trades1 = fetchTrades(m_exch);
                long millis1 = System.currentTimeMillis();
                TradesData newTrades = trades1.newTrades(m_lastProcessedTradesTime);
                System.out.println(" loaded " + trades1.size() + " trades for '" + exchName() + "' in "+(millis1 - millis0)+" ms; new " + newTrades.size() + " trades: " + newTrades);

                List<TradesData.TradeData> newTradesList = newTrades.m_trades;
                if(m_buyBracketOrder != null) {
                    checkExecutedBracket(m_buyBracketOrder, newTradesList);
                }
                if(m_sellBracketOrder != null) {
                    checkExecutedBracket(m_sellBracketOrder, newTradesList);
                }

                for(TradesData.TradeData trade: newTradesList) {
                    long timestamp = trade.m_timestamp;
                    if(timestamp > m_lastProcessedTradesTime) {
                        m_lastProcessedTradesTime = timestamp;
                    }
                }
            }
        }

        private void checkExecutedBracket(OrderData bracketOrder, List<TradesData.TradeData> tradesList) {
            for(TradesData.TradeData trade: tradesList) {
                double mktPrice = trade.m_price; // ASK > BID
                if(bracketOrder.acceptPrice(mktPrice)) {
                    double tradeAmount = trade.m_amount;
                    System.out.println("@@@@@@@@@@@@@@ we have bracket trade "+bracketOrder.m_side+" " + tradeAmount +
                                       " on '" + exchName() + "' @ " + Utils.XX_YYYY.format(mktPrice) +
                                       ", waiting '" + bracketOrder.priceStr() + "', trade=" + trade);
                    double orderAmount = bracketOrder.m_amount;
                    if(orderAmount > tradeAmount) { // for now partial order execution it is complex to handle - we will execute the rest by MKT price
                        System.out.println("@@@@@@@@@@@@@@  for now partial order execution it is complex to handle: orderAmount="+orderAmount+", tradeAmount="+tradeAmount);
                        // m_buyMktBracketOrder = new OrderData(OrderSide.BUY, Double.MAX_VALUE, orderAmount - tradeAmount);
                    }
                }
            }
        }

        public void placeBrackets(TopData top1, TopData otherTop, double midDiffAverage) {
            double buy = otherTop.m_bid - HALF_TARGET_DELTA + midDiffAverage; // ASK > BID
            double sell = otherTop.m_ask + HALF_TARGET_DELTA + midDiffAverage;

            System.out.println("'"+ exchName() +"' buy: "+( (m_buyBracketOrder!=null) ? m_buyBracketOrder.priceStr() + "->" : "" )+ Utils.XX_YYYY.format(buy)+ ";  " +
                                                  "sell: "+( (m_sellBracketOrder!=null) ? m_sellBracketOrder.priceStr() + "->" : "" )+ Utils.XX_YYYY.format(sell));

            double amount = calcAmountToOpen(); // todo: get this based on both exch account info

            m_buyBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
            m_sellBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
        }
    }

    public static class OrderData {
        public OrderSide m_side;
        public double m_price;
        public double m_amount;

        public String priceStr() { return Utils.XX_YYYY.format(m_price); }

        public OrderData(OrderSide side, double price, double amount) {
            m_side = side;
            m_price = price;
            m_amount = amount;
        }

        public boolean acceptPrice(double mktPrice) {
            return m_side.acceptPrice( m_price, mktPrice );
        }
    }

    public static enum OrderSide {
        BUY {
            @Override public boolean acceptPrice(double orderPrice, double mktPrice) {
                return orderPrice >= mktPrice;
            }
        },
        SELL {
            @Override public boolean acceptPrice(double orderPrice, double mktPrice) {
                return orderPrice <= mktPrice;
            }
        };

        public boolean acceptPrice(double orderPrice, double mktPrice) { return false; }
    }
}
