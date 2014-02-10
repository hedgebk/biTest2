import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.SortedMap;
import java.util.TreeMap;

public class Fetcher {
    private static final boolean USE_TOP_TEST_STR = false;
    private static final boolean USE_DEEP_TEST_STR = false;
    private static final boolean USE_TRADES_TEST_STR = false;
    public static final DecimalFormat XX_YYYY = new DecimalFormat("#,##0.0000");
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
    private static Double s_exch1buy;
    private static Double s_exch1buyAmount;
    private static Double s_exch1sell;
    private static Double s_exch1sellAmount;
    private static Double s_exch2buy;
    private static Double s_exch2buyAmount;
    private static Double s_exch2sell;
    private static Double s_exch2sellAmount;

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
            System.out.println("@@@@@@@@@@@@@@  total: runs="+s_totalRuns+", gain=" + s_totalGain);
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
//        TreeMap<Long,TopData.TopDataEx> diffs = new TreeMap<Long, TopData.TopDataEx>(); // sorted by time
        long delay; // start with 5sec
        double sumBidAskDiff1 = 0, sumBidAskDiff2 = 0;
        Long lastProcessedTradesTime1 = 0L, lastProcessedTradesTime2 = 0L;
        int counter = 0;
        while(true) {
            System.out.println("----------------------------------------------");

            if ((s_exch1buy != null) || (s_exch1sell != null)) {
                long millis0 = System.currentTimeMillis();
                TradesData trades1 = fetchTrades(exch1);
                long millis1 = System.currentTimeMillis();
                TradesData newTrades = trades1.newTrades(lastProcessedTradesTime1);
                System.out.println(" loaded " + trades1.size() + " trades for '" + exch1 + "' in "+(millis1 - millis0)+" ms; new " + newTrades.size() + " trades: " + newTrades);
                for(TradesData.TradeData trade: newTrades.m_trades) {
                    double price = trade.m_price; // ASK > BID
                    if((s_exch1buy != null) && (s_exch1buy > price)) {
                        System.out.println("@@@@@@@@@@@@@@ we can trade exch1buy @ "+price+", trade="+trade);
                        s_exch1buy = null; // for now partial order execution is complex to handle - we will execute the rest by MKT price
                    }
                    if((s_exch1sell != null) && (s_exch1sell < price)) {
                        System.out.println("@@@@@@@@@@@@@@ we can trade exch1sell @ "+price+", trade="+trade);
                        s_exch1sell = null; // for now partial order execution is complex to handle - we will execute the rest by MKT price
                    }
                    long timestamp = trade.m_timestamp;
                    if(timestamp > lastProcessedTradesTime1) {
                        lastProcessedTradesTime1 = timestamp;
                    }
                }
            }
            if ((s_exch2buy != null) || (s_exch2sell != null)) {
                long millis0 = System.currentTimeMillis();
                TradesData trades2 = fetchTrades(exch2);
                long millis1 = System.currentTimeMillis();
                TradesData newTrades = trades2.newTrades(lastProcessedTradesTime2);
                System.out.println(" loaded " + trades2.size() + " trades for '" + exch2 + "' in "+(millis1 - millis0)+" ms; new " + newTrades.size() + " trades: " + newTrades);
                for(TradesData.TradeData trade: newTrades.m_trades) {
                    double price = trade.m_price; // ASK > BID
                    if((s_exch2buy != null) && (s_exch2buy > price)) {
                        System.out.println("@@@@@@@@@@@@@@ we can trade exch2buy @ "+price+", trade="+trade);
                        s_exch2buy = null;
                    }
                    if((s_exch2sell != null) && (s_exch2sell < price)) {
                        System.out.println("@@@@@@@@@@@@@@ we can trade exch2sell @ "+price+", trade="+trade);
                        s_exch2sell = null;
                    }
                    long timestamp = trade.m_timestamp;
                    if(timestamp > lastProcessedTradesTime2) {
                        lastProcessedTradesTime2 = timestamp;
                    }
                }
            }

            long millis0 = System.currentTimeMillis();
            TopData top1 = fetchTop(exch1);
            long top1Millis = System.currentTimeMillis();
            TopData top2 = fetchTop(exch2);
            long top2Millis = System.currentTimeMillis();
            System.out.println("  loaded in " + (top1Millis - millis0) + " and " + (top2Millis - top1Millis) + " ms");

            log(exch1, top1);
            log(exch2, top2);
            System.out.println();
            long millis = System.currentTimeMillis();
//            long limit = millis - MOVING_AVERAGE;
//            System.out.println("millis="+millis+", limit="+limit);

//            removeOld(limit, diffs);

            TopData.TopDataEx diff = calcDiff(top1, top2); // top1 - top2
            System.out.print("diff=" + toStr(diff));
//            diffs.put(millis, diff);
            double midDiff = diff.m_mid;
            double midDiffAverage = averageCounter.add(millis, midDiff);
            System.out.println(";  avg=" + XX_YYYY.format(midDiffAverage));

            double delta = midDiff - midDiffAverage;

            sumBidAskDiff1 += (top1.m_ask - top1.m_bid); // ASK > BID
            sumBidAskDiff2 += (top2.m_ask - top2.m_bid);

            counter++;
            System.out.println("delta: " + XX_YYYY.format(delta) + ",  waiting for: "+HALF_TARGET_DELTA +
                               ", avgBidAskDiff1="+XX_YYYY.format(sumBidAskDiff1/counter) +
                               ", avgBidAskDiff2="+XX_YYYY.format(sumBidAskDiff2/counter) +
                               ", millis="+millis +
                               ", tile="+new Date(millis));
            os.println(""+millis+"\t" + XX_YYYY.format(midDiff)+"\t" + XX_YYYY.format(midDiffAverage));
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
                    double exch1buy = top2.m_bid - HALF_TARGET_DELTA + midDiffAverage;
                    double exch1sell = top2.m_ask + HALF_TARGET_DELTA + midDiffAverage;
                    double exch2buy = top1.m_bid - HALF_TARGET_DELTA - midDiffAverage;
                    double exch2sell = top1.m_ask + HALF_TARGET_DELTA - midDiffAverage;

                    System.out.println("1buy: "+( (s_exch1buy!=null) ? XX_YYYY.format(s_exch1buy) + "->" : "" )+XX_YYYY.format(exch1buy)+ "; " +
                                       "2sell: "+( (s_exch2sell!=null) ? XX_YYYY.format(s_exch2sell) + "->" : "" )+XX_YYYY.format(exch2sell)+ "; " +
                                       "2buy: "+( (s_exch2buy!=null) ? XX_YYYY.format(s_exch2buy) + "->" : "" )+XX_YYYY.format(exch2buy)+ "; " +
                                       "1sell: "+( (s_exch1sell!=null) ? XX_YYYY.format(s_exch1sell) + "->" : "" )+XX_YYYY.format(exch1sell));

//                    double w1 = exch1sell-top2.m_ask;
//                    double w2 = exch2sell-top1.m_ask;
//                    System.out.println("check: w1: "+XX_YYYY.format(w1)+
//                                       ", w2: "+XX_YYYY.format(w2) +
//                                       ", summ: "+XX_YYYY.format(w1+w2) );
                    double amount = calcAmountToOpen();

                    s_exch1buy = exch1buy;
                    s_exch1buyAmount = amount;
                    s_exch1sell = exch1sell;
                    s_exch1sellAmount = amount;
                    s_exch2buy = exch2buy;
                    s_exch2buyAmount = amount;
                    s_exch2sell = exch2sell;
                    s_exch2sellAmount = amount;

                    waitDistance = HALF_TARGET_DELTA - Math.abs(delta);
                }
            } else if( s_state == STATE.TOP ) {
                waitDistance = delta - (s_diff - TARGET_DELTA);
                if( s_diff - midDiff > TARGET_DELTA ) {
                    System.out.println("@@@@@@@@@@@@@@ we can close TOP @ MKT");
                    close(exch1, exch2, top1, top2);
                } else if( midDiffAverage - s_diff > TARGET_DELTA ) {
                    System.out.println("@@@@@@@@@@@@@@ we should drop TOP");
                    close(exch1, exch2, top1, top2);
                }
            } else if( s_state == STATE.BOTTOM ) {
                waitDistance = (s_diff + TARGET_DELTA) - delta;
                if( midDiff - s_diff > TARGET_DELTA ) {
                    System.out.println("@@@@@@@@@@@@@@ we can close BOTTOM @ MKT");
                    close(exch2, exch1, top2, top1);
                } else if (s_diff - midDiffAverage > TARGET_DELTA) {
                    System.out.println("@@@@@@@@@@@@@@ we should drop BOTTOM");
                    close(exch2, exch1, top2, top1);
                }
            }
            System.out.println("waitDistance="+waitDistance);
            delay = (long) (MIN_DELAY + MIN_DELAY * 4 * Math.min(1, Math.abs(waitDistance) / HALF_TARGET_DELTA));
            delay = Math.max(delay,1000);
            long running = System.currentTimeMillis() - start;
            System.out.println("wait "+delay+" ms. total running " + Utils.millisToDHMSStr(running) + ", counter="+counter);
            Thread.sleep(delay);
        }
    }

    private static double calcAmountToOpen() {
        return 1.0; // 1.0 BTC
    }

    private static String toStr(TopData.TopDataEx top) {
        return "TopData{" +
                "bid=" + XX_YYYY.format(top.m_bid) +
                ", ask=" + XX_YYYY.format(top.m_ask) +
                ", last=" + XX_YYYY.format(top.m_last) +
                ", mid=" + XX_YYYY.format(top.m_mid) +
                '}';
    }

    private static void close(Exchange exch1, Exchange exch2, TopData top1, TopData top2) {
        double bid = top2.m_bid;
        System.out.println("@@@@@@@@@@@@@@ will sell on "+exch2.m_name+" @ " + bid);
        double gain2 = bid - s_ask;
        System.out.println("@@@@@@@@@@@@@@  was bought @ " + s_ask + ", gain=" + gain2);

        double ask = top1.m_ask;
        System.out.println("@@@@@@@@@@@@@@ will buy at "+exch1.m_name+" @ " + ask);
        double gain1 = s_bid - ask;
        System.out.println("@@@@@@@@@@@@@@  was sold @ " + s_bid + ", gain=" + gain1);

        double gain = gain1 + gain2;
        System.out.println("@@@@@@@@@@@@@@ this gain=" + gain);
        s_totalGain += gain;
        s_totalRuns++;
        System.out.println("@@@@@@@@@@@@@@  total: runs="+s_totalRuns+", gain=" + s_totalGain);

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
            System.out.println(XX_YYYY.format(bid1.m_size)+"@"+XX_YYYY.format(bid1.m_price)+"  " +
                               XX_YYYY.format(ask1.m_size)+"@"+XX_YYYY.format(ask1.m_price)+"      " +
                               XX_YYYY.format(bid2.m_size)+"@"+XX_YYYY.format(bid2.m_price)+"  " +
                               XX_YYYY.format(ask2.m_size)+"@"+XX_YYYY.format(ask2.m_price)+"  ");
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
            } catch (Exception e) {
                System.out.println(" loading error (attempt "+attempt+"): " + e);
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            Thread.sleep(delay);
            delay += REPEAT_DELAY_INCREMENT;
        }
        throw new RuntimeException("unable to load after "+MAX_READ_ATTEMPTS+" attempts");
    }

    private static void log(Exchange exch, TopData top) {
        System.out.print(exch.m_name +
                ": bid: " + XX_YYYY.format(top.m_bid) +
                ", ask: " + XX_YYYY.format(top.m_ask) +
                ", last: " + XX_YYYY.format(top.m_last) +
                ", bid_ask_dif: " + XX_YYYY.format(top.m_ask-top.m_bid) +
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
            @Override public String getTestStr(Exchange exchange) { return exchange.m_deepTestStr; }
            @Override public String getApiEndpoint(Exchange exchange) { return exchange.m_apiDeepEndpoint; }
            @Override public boolean useTestStr() { return USE_DEEP_TEST_STR; }
        }, TRADES {
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
            long limit = millis - MOVING_AVERAGE;
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

    private static class ExchangeData {
        public final Exchange m_exch1;

        public ExchangeData(Exchange exch1) {
            m_exch1 = exch1;
        }
    }
}
