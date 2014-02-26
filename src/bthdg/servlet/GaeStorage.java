package bthdg.servlet;

import bthdg.Deserializer;
import bthdg.Log;
import bthdg.PairExchangeData;
import com.google.appengine.api.datastore.*;

import java.io.IOException;
import java.util.logging.Logger;

public class GaeStorage {
    public static final String GAE_DATA_KEY = "data";
    public static final String GAE_TIME_KEY = "time";

    private static DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    public static final String KIND = "Record";

    private static void log(String s) { Log.log(s); }

    public static Entity getRecord(String name) {
        Key key = KeyFactory.createKey(KIND, name);
        return findEntity(key);
    }

    public static Entity findEntity(Key key) {
        try {
            return datastore.get(key);
        } catch (EntityNotFoundException e) {
            return null;
        }
    }

    public static PairExchangeData get(PairExchangeData data) throws IOException {
        Entity timeEntity = getRecord(GAE_TIME_KEY);
        if (timeEntity != null) {
            Long dbTimestamp = (Long) timeEntity.getProperty(GAE_TIME_KEY);
            log("gae timestamp: " + dbTimestamp);
            if (dbTimestamp != null) {
                if ((data == null) || (data.m_timestamp < dbTimestamp)) {
                    log("no data or gae timestamp bigger - try to use gae");
                    Entity dataEntity = getRecord(GAE_DATA_KEY);
                    if (dataEntity != null) {
                        Text text = (Text) dataEntity.getProperty(GAE_DATA_KEY);
                        log(" gae  data: " + text);
                        if (text != null) {
                            String serialized = text.getValue();
                            PairExchangeData deserialized = Deserializer.deserialize(serialized);
                            return deserialized;
                        } else {
                            log("no data prop in gae entity");
                        }
                    } else {
                        log("no data entity in gae");
                    }
                } else {
                    log("gae timestamp is less or equals");
                }
            } else {
                log("no timestamp prop in gae entity");
            }
        } else {
            log("no timestamp entity in gae");
        }
        return data;
    }

    public static void save(Long timestamp, String serialized) {
        save(GAE_TIME_KEY, timestamp);
        saveText(GAE_DATA_KEY, serialized);
    }

    private static void save(String name, Object value) {
        Entity record = getRecord(name);
        if (record == null) {
            record = new Entity(KIND, name);
        }
        record.setProperty(name, value);
        datastore.put(record);
    }

    private static void saveText(String name, String value) {
        Entity record = getRecord(name);
        if (record == null) {
            record = new Entity(KIND, name);
        }
        record.setProperty(name, new Text(value));
        datastore.put(record);
    }
}
