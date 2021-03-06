package de.zalando.pgobserver.gatherer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author  jmussler
 */
public class SprocGatherer extends ADBGatherer {

    public static final int QueryID = 1;
    private SprocIdCache idCache = null;
    private Map<Long, List<SprocPerfValue>> valueStore = null;
    private Map<Integer, Long> lastValueStore = new HashMap<Integer, Long>();
    private int sprocsRead = 0;
    private int sprocValuesInserted = 0;

    public SprocGatherer(final Host h, final long interval, final ScheduledThreadPoolExecutor ex) {
        super(h, ex, interval);
        idCache = new SprocIdCache(h);
        valueStore = new TreeMap<Long, List<SprocPerfValue>>();
    }

    public String getQuery() {
        String sql = "select schemaname AS schema_name,"
                + "funcname || '(' || regexp_replace(regexp_replace(regexp_replace ( pg_get_function_identity_arguments(funcid)::text , E'\\\\s*OUT\\\\s*','','g'),E'^[A-Za-z_0-9]+\\\\s*|(,)\\\\s*[A-Za-z_0-9]+', E'\\\\1','g'), E'[a-z_0-9]+\\\\.','','g') || ')' AS function_name "
                + ", calls" + ", self_time" + ", total_time " + "from pg_stat_user_functions "
                + "where not schemaname like any( array['pg%','information_schema'] ) "
                + "and ( schemaname IN ( select name from ( select nspname, rank() OVER ( PARTITION BY substring(nspname from '(.*)_api') ORDER BY nspname DESC) from pg_namespace where nspname like '%_api%' ) apis ( name, rank ) where rank = 1 ) OR schemaname LIKE '%_data' );";

        return sql;
    }

    @Override
    public boolean gatherData() {
        Connection conn = null;

        try {
            conn = DriverManager.getConnection("jdbc:postgresql://" + host.name + ":" + host.port + "/" + host.dbname,
                    host.user, host.password);

            Statement st = conn.createStatement();
            st.execute("SET statement_timeout TO '15s';");

            long time = System.currentTimeMillis();
            List<SprocPerfValue> list = valueStore.get(time);
            if (list == null) {
                list = new LinkedList<SprocPerfValue>();
                valueStore.put(time, list);
            }

            ResultSet rs = st.executeQuery(getQuery());
            while (rs.next()) {
                SprocPerfValue v = new SprocPerfValue();
                v.name = rs.getString("function_name");
                v.schema = rs.getString("schema_name");
                v.selfTime = rs.getLong("self_time");
                v.totalCalls = rs.getLong("calls");
                v.totalTime = rs.getLong("total_time");
                list.add(v);
            }

            rs.close();
            st.close();
            conn.close(); // we close here, because we are done
            conn = null;

            Logger.getLogger(SprocGatherer.class.getName()).log(Level.INFO, "[{0}] finished getting sproc data",
                host.name);

            conn = DBPools.getDataConnection();

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO monitor_data.sproc_performance_data(sp_timestamp, sp_sproc_id, sp_calls, sp_total_time, sp_self_time)    VALUES (?, ?, ?, ?, ?);");

            sprocsRead = 0;
            sprocValuesInserted = 0;

            for (Entry<Long, List<SprocPerfValue>> toStore : valueStore.entrySet()) {
                for (SprocPerfValue v : toStore.getValue()) {
                    // Logger.getLogger(SprocGatherer.class.getName()).log(Level.INFO, v.schema + "." + v.name);

                    sprocsRead++;

                    int id = idCache.getId(conn, v.schema, v.name);

                    if (!(id > 0)) {
                        Logger.getLogger(SprocGatherer.class.getName()).log(Level.SEVERE,
                            "\t could not retrieve sproc key");
                        continue;
                    }

                    Long lastValue = lastValueStore.get(id);

                    if (lastValue != null) {
                        if (lastValue == v.totalCalls) {
                            continue;
                        }
                    }

                    ps.setTimestamp(1, new Timestamp(toStore.getKey()));
                    ps.setInt(2, id);
                    ps.setLong(3, v.totalCalls);
                    ps.setLong(4, v.totalTime);
                    ps.setLong(5, v.selfTime);

                    ps.execute();
                    sprocValuesInserted++;

                    lastValueStore.put(id, v.totalCalls);
                }
            }

            ps.close();
            conn.close();
            conn = null;

            valueStore.clear();

            Logger.getLogger(SprocGatherer.class.getName()).log(Level.INFO,
                "[" + this.getName() + "] Sprocs read: " + this.sprocsRead + " Sprocs written: "
                    + this.sprocValuesInserted);

            return true;
        } catch (SQLException se) {
            Logger.getLogger(SprocGatherer.class.getName()).log(Level.SEVERE, "", se);
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    Logger.getLogger(SprocGatherer.class.getName()).log(Level.SEVERE, "", ex);
                }
            }
        }
    }
}
