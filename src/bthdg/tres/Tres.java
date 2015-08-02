package bthdg.tres;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.BaseExch;
import bthdg.exch.Pair;
import bthdg.exch.TradeData;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;
import bthdg.ws.IWs;
import bthdg.ws.WsFactory;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Tres {
    public static final Pair PAIR = Pair.BTC_CNH;
    public static final boolean PAINT_TICK_TIMES_ONLY = false;
    private static Tres s_inst;

    private Properties m_keys;
    long m_barSizeMillis;
    int m_len1;
    int m_len2;
    int m_k;
    int m_d;
    int m_phases;
    public int m_preheatBarsNum;
    int m_ma;
    ArrayList<TresExchData> m_exchDatas;
    private TresFrame m_frame;
    long m_startTickMillis = Long.MAX_VALUE;
    long m_lastTickMillis = 0;
    private final boolean m_processLogs;
    public boolean m_silentConsole;
    public List<Long> m_tickTimes = new ArrayList<Long>();

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public Tres(String[] args) {
        m_processLogs = (args.length > 0);
    }

    public static void main(String[] args) {
        try {
            s_inst = new Tres(args);
            s_inst.start();

            new IntConsoleReader().run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean onConsoleLine(String line) {
        log("onConsoleLine: " + line);
        if (line.equals("stop")) {
            s_inst.onStop();
            return true;
        }
        if (line.equals("ui")) {
            showUI();
        }
        return false;
    }

    private void onStop() {
        log("stop()-----------------------------------------------------");
        for (TresExchData exchData : m_exchDatas) {
            exchData.stop();
        }
        stopFrame();
    }

    private void start() throws IOException {
        m_keys = BaseExch.loadKeys();
        init();

        if (m_processLogs) {
            m_silentConsole = true;
            TresLogProcessor logProcessor = new TresLogProcessor(m_keys, m_exchDatas);
            logProcessor.start();
        } else {
            for (TresExchData exchData : m_exchDatas) {
                exchData.start();
            }
        }
    }

    private void init() {
        String exchangesStr = getProperty("tre.exchanges");
        log("EXCHANGES=" + exchangesStr);
        String[] exchangesArr = exchangesStr.split(",");
        int exchangesLen = exchangesArr.length;
        log(" .len=" + exchangesLen);

        String barSizeStr = getProperty("tre.bar_size");
        log("barSize=" + barSizeStr);
        m_barSizeMillis = Utils.toMillis(barSizeStr);
        log(" .millis=" + m_barSizeMillis);

        m_len1 = Integer.parseInt(getProperty("tre.len1"));
        log("len1=" + m_len1);
        m_len2 = Integer.parseInt(getProperty("tre.len2"));
        log("len2=" + m_len2);
        m_k = Integer.parseInt(getProperty("tre.k"));
        log("k=" + m_k);
        m_d = Integer.parseInt(getProperty("tre.d"));
        log("d=" + m_d);
        m_phases = Integer.parseInt(getProperty("tre.phases"));
        log("phases=" + m_phases);
        m_ma = Integer.parseInt(getProperty("tre.ma"));
        log("ma=" + m_ma);

        m_preheatBarsNum = m_len1 + m_len2 + (m_k - 1) + (m_d - 1);

        m_exchDatas = new ArrayList<TresExchData>(exchangesLen);
        for (String exch : exchangesArr) {
            IWs ws = WsFactory.get(exch, m_keys);
            m_exchDatas.add(new TresExchData(this, ws));
        }

        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
    }

    private String getProperty(String key) {
        String ret = m_keys.getProperty(key);
        if (ret == null) {
            throw new RuntimeException("no property found for key '" + key + "'");
        }
        return ret;
    }

    protected static void showUI() {
        s_inst.showFrame();
    }

    private void showFrame() {
        stopFrame();
        m_frame = new TresFrame(s_inst);
        m_frame.setVisible(true);
        m_frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                m_frame = null;
            }
        });
    }

    private void stopFrame() {
        if (m_frame != null) {
            m_frame.stop();
        }
    }

    public void onTrade(TradeData tdata) {
        long timestamp = tdata.m_timestamp;
        long min = Math.min(m_startTickMillis, timestamp);
        if ((min < m_startTickMillis) && (m_startTickMillis != Long.MAX_VALUE)) {
            TimeZone TZ = TimeZone.getTimeZone("Asia/Hong_Kong"); // utc+08:00 Beijing, Hong Kong, Urumqi
            Calendar NOW_CALENDAR = Calendar.getInstance(TZ, Locale.ENGLISH);
            NOW_CALENDAR.setTimeInMillis(timestamp);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z, zzzz");
            simpleDateFormat.setTimeZone(TZ);
            String str = simpleDateFormat.format(NOW_CALENDAR.getTime());
            log("str="+str);
            log("GOT");
        } else {
            m_startTickMillis = min;
        }
        m_lastTickMillis = Math.max(m_lastTickMillis, timestamp);

        if(PAINT_TICK_TIMES_ONLY) { // collect tickTimes
            m_tickTimes.add(timestamp);
        }

        if (m_frame != null) {
            m_frame.fireUpdated();
        }
    }

    String getState() {
        StringBuilder sb = new StringBuilder();
        for (TresExchData exchData : m_exchDatas) {
            exchData.getState(sb);
        }
        return sb.toString();
    }

    private static class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() { System.out.print(">"); }
        @Override protected boolean processLine(String line) throws Exception { return onConsoleLine(line); }
    }

}
