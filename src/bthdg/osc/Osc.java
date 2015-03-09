package bthdg.osc;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.BaseExch;
import bthdg.exch.Exchange;
import bthdg.exch.Pair;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;
import bthdg.ws.HuobiWs;
import bthdg.ws.IWs;
import bthdg.ws.OkCoinWs;

import java.util.Properties;

public class Osc {
    public static long[] BAR_SIZES;
    public static long AVG_BAR_SIZE;
    public static long MAX_BAR_SIZE = 0;
    public static int LEN1 = 14;
    public static int LEN2 = 14;
    public static int K = 3;
    public static int D = 3;
    public static int PHASES = 1;
    public static double START_LEVEL = 0.005;
    public static double STOP_LEVEL = 0.005;
    public static double START_STOP_LEVEL_MULTIPLY = 3;
    static boolean STICK_TOP_BOTTOM = false;

    public static int PREHEAT_BARS_NUM = calcPreheatBarsNum();

    public static final int INIT_BARS_BEFORE = 4;
    public static final Pair PAIR = Pair.BTC_CNH; // TODO: BTC is hardcoded below
    public static final double CLOSE_PRICE_DIFF = 0.6;
    static final double ORDER_SIZE_TOLERANCE = 0.1;
    public static final double USE_FUNDS_FROM_AVAILABLE = 0.95; // 95%
    static final boolean DO_CLOSE_ORDERS = false;

    private static Osc s_osc; // instance

    private final String m_e;
    private OscProcessor m_processor;
    private String m_propPrefix;
    private Properties m_keys;

    public Osc(String[] args) {
        Log.s_impl = new Log.TimestampLog();

        if(args.length > 0) {
            m_e = args[0];
        } else {
            throw new RuntimeException("no arg params");
        }
    }

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }
    private static int calcPreheatBarsNum() { return LEN1 + LEN2 + (K - 1) + (D - 1); }

    public static void main(String[] args) {
        try {
            s_osc = new Osc(args);
            s_osc.run();

            new ConsoleReader() {
                @Override protected void beforeLine() {
                    System.out.print(">");
                }
                @Override protected boolean processLine(String line) throws Exception {
                    return onConsoleLine(line);
                }
            }.run();

            Thread thread = Thread.currentThread();
            synchronized (thread) {
                thread.wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean onConsoleLine(String line) throws Exception {
        log("onConsoleLine: " + line);
        if (line.equals("stop")) {
            s_osc.onStop();
            return true;
        }
        return false;
    }

    private void onStop() throws Exception {
        log("stop()-----------------------------------------------------");
        m_processor.stop();
    }

    private void run() throws Exception {
        m_keys = BaseExch.loadKeys();
//        Btcn.init(m_keys);

        IWs ws;
        Exchange exchange = Exchange.getExchange(m_e);
        switch (exchange) {
            case HUOBI:
                ws = HuobiWs.create(m_keys);
                break;
            case OKCOIN:
                ws = OkCoinWs.create(m_keys);
                break;
            default:
                throw new RuntimeException("not supported exchange: " + exchange);
        }

        m_propPrefix = ws.getPropPrefix();
        init();

//        BitstampWs.main(args);

        m_processor = new OscProcessor(ws);
        m_processor.start();

        try {
        } catch (Exception e) {
            err("subscribeTrades error" + e, e);
        }
    }

    private void init() {
        String barSizeStr = getProperty("osc.bar_size");
        String[] split = barSizeStr.split("\\|");
        int barSizeLen = split.length;
        log("BAR_SIZE.len=" + barSizeLen);
        BAR_SIZES = new long[barSizeLen];
        long summ = 0;
        for (int i = 0; i < barSizeLen; i++) {
            String str = split[i];
            long millis = Utils.toMillis(str);
            BAR_SIZES[i] = millis;
            log(" BAR_SIZE[" + i + "]=" + millis);
            summ += millis;
            MAX_BAR_SIZE = Math.max(MAX_BAR_SIZE, millis);
        }
        AVG_BAR_SIZE = summ / barSizeLen;
        LEN1 = Integer.parseInt(getProperty("osc.len1"));
        log("LEN1=" + LEN1);
        LEN2 = Integer.parseInt(getProperty("osc.len2"));
        log("LEN2=" + LEN2);
        K = Integer.parseInt(getProperty("osc.k"));
        log("K=" + K);
        D = Integer.parseInt(getProperty("osc.d"));
        log("D=" + D);
        PHASES = Integer.parseInt(getProperty("osc.phases"));
        log("PHASES=" + PHASES);
        START_LEVEL = Double.parseDouble(getProperty("osc.start_level"));
        log("START_LEVEL=" + START_LEVEL);
        STOP_LEVEL = Double.parseDouble(getProperty("osc.stop_level"));
        log("STOP_LEVEL=" + STOP_LEVEL);
        START_STOP_LEVEL_MULTIPLY = Double.parseDouble(getProperty("osc.start_stop_level_multiply"));
        log("START_STOP_LEVEL_MULTIPLY=" + START_STOP_LEVEL_MULTIPLY);
        STICK_TOP_BOTTOM = Boolean.parseBoolean(getProperty("osc.stickTopBottom"));
        log("STICK_TOP_BOTTOM=" + STICK_TOP_BOTTOM);
//        String str2 = properties.getProperty("osc.delayReverseStart");
//        DELAY_REVERSE_START = Boolean.parseBoolean(str2);
//        log("DELAY_REVERSE_START=" + DELAY_REVERSE_START);
//        RATIO_POWER = Double.parseDouble(properties.getProperty("osc.ratioPower"));
//        log("RATIO_POWER=" + RATIO_POWER);

        PREHEAT_BARS_NUM = calcPreheatBarsNum();

        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
    }

    private String getProperty(String key) {
        String ret = m_keys.getProperty(m_propPrefix + key);
        if (ret == null) {
            ret = m_keys.getProperty(key);
        }
        if (ret == null) {
            throw new RuntimeException("no property found for key '" + key + " '");
        }
        return ret;
    }
}
