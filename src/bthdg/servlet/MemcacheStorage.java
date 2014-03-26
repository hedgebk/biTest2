package bthdg.servlet;

import bthdg.Deserializer;
import bthdg.Log;
import bthdg.duplet.PairExchangeData;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;

import java.io.IOException;

public class MemcacheStorage {
    public static final String MEM_CACHE_DATA_KEY = "data";
    public static final String MEM_CACHE_TIME_KEY = "time";
    public static final String MEM_CACHE_CONFIG_KEY = "config";
    private static final MemcacheService s_memCache = MemcacheServiceFactory.getMemcacheService();

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception err) { Log.err(s, err); }

    public static PairExchangeData get(PairExchangeData data) throws IOException {
        Long memTimestamp = (Long) s_memCache.get(MEM_CACHE_TIME_KEY);
        log("memcache timestamp: " + memTimestamp);
        if (data == null) { // try to get from memCache
            log("no data in servletContext");
            if (memTimestamp != null) {
                String serialized = (String) s_memCache.get(MEM_CACHE_DATA_KEY);
                if (serialized != null) {
                    log("got data in memcache");
                    PairExchangeData deserialized = Deserializer.deserialize(serialized);
                    return deserialized;
                } else {
                    log("no serialized data in memcache");
                }
            } else {
                log("no timestamp in memcache");
            }
            return null;
        } else { // we have some data in servlet context - check if it outdated
            long timestamp = data.m_timestamp;
            log("servletContext timestamp: "+timestamp);
            if (memTimestamp != null) {
                if (memTimestamp > timestamp) {
                    log("memcache timestamp is bigger");
                    String serialized = (String) s_memCache.get(MEM_CACHE_DATA_KEY);
                    if (serialized != null) {
                        log("using newer data in memcache");
                        PairExchangeData deserialized = Deserializer.deserialize(serialized);
                        return deserialized;
                    } else {
                        log("no serialized data in memcache");
                    }
                } else {
                    log("memcache timestamp is less or the same");
                }
            } else {
                log("no timestamp in memcache");
            }
            return data;
        }
    }

    public static void save(Long timestamp, String serialized) {
        try {
            s_memCache.put(MEM_CACHE_TIME_KEY, timestamp);
            s_memCache.put(MEM_CACHE_DATA_KEY, serialized);
        } catch (Exception e) { // sometimes we are getting memcache put errors - survive it
            err("memCache put error: " + e, e);
        }
    }

    public static void saveConfig(String config) {
        try {
            s_memCache.put(MEM_CACHE_CONFIG_KEY, config);
        } catch (Exception e) { // sometimes we are getting memcache put errors - survive it
            err("memCache put error: " + e, e);
        }
    }

    public static String getConfig() {
        return (String) s_memCache.get(MEM_CACHE_CONFIG_KEY);
    }
}
