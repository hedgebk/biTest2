package bthdg.triplet;

import bthdg.exch.Pair;
import bthdg.exch.TradesData;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class TradesAggregator {
    public static final String FILE_NAME = "triplet.trades.propertes";
    public static final int UPDATE_TIME = 60000; // every minute

    public Map<Pair, Integer> m_tradesMap = new HashMap<Pair, Integer>();
    private long m_lastUpdateTime = 0;

    public void load() throws IOException {
        Properties props = new Properties();
        File file = new File(FILE_NAME);
        if (file.exists()) {
            FileReader reader = new FileReader(file);
            try {
                props.load(reader);
                for (Pair pair : Triplet.PAIRS) {
                    String name = pair.name();
                    String val = props.getProperty(name);
                    if (val != null) {
                        try {
                            int count = Integer.parseInt(val);
                            m_tradesMap.put(pair, count / 2);
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } finally {
                reader.close();
            }
        }
    }

    public void save() throws IOException {
        Properties props = new Properties();
        for (Pair pair : Triplet.PAIRS) {
            Integer count = m_tradesMap.get(pair);
            if (count != null) {
                String name = pair.name();
                props.setProperty(name, count.toString());
            }
        }
        FileWriter writer = new FileWriter(new File(FILE_NAME));
        try {
            props.store(writer, "trades frequency");
        } finally {
            writer.close();
        }
    }

    public void update(Map<Pair, TradesData> tradesMap) throws IOException {
        for (Pair pair : Triplet.PAIRS) {
            TradesData tData = tradesMap.get(pair);
            if (tData != null) {
                int size = tData.size();
                if (size > 0) {
                    Integer count = m_tradesMap.get(pair);
                    if (count == null) {
                        count = size;
                    } else {
                        count += size;
                    }
                    m_tradesMap.put(pair, count);
                }
            }
        }
        long mills = System.currentTimeMillis();
        if (mills - m_lastUpdateTime > UPDATE_TIME) {
            save();
            m_lastUpdateTime = mills;
        }
    }
}
