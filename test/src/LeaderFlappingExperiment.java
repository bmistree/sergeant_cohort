package test;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import java.io.PrintWriter;
import java.io.IOException;

import SergeantCohort.CohortManager;
import SergeantCohort.CohortInfo;
import SergeantCohort.CohortConnection.TCPCohortConnection;
import SergeantCohort.ILeaderElectedListener;

/**
   Start three nodes, two can't communicate and the third node can
   never be leader.  Log leaders and election timestamps to file.
   
 */
public class LeaderFlappingExperiment
{
    public final static long COHORT_SIZE = 3L;
    public final static int HEARTBEAT_TIMEOUT_PERIOD_MS = 200;
    public final static int HEARTBEAT_SEND_PERIOD_MS = 50;
    public final static int MAX_BATCH_SIZE = 20;
    
    // Create a LeaderElectedListener and register it
    protected final static LeaderElectedListener leader_elected_listener =
        new LeaderElectedListener();

    
    public static void main(String[] args)
    {
        String output_filename = args[0];
        int seconds_to_run = Integer.parseInt(args[1]);
        run(seconds_to_run);
        // results should end up being stored in leader elected
        // listener.  Can then ask it to dump contents of file.
        try
        {
            leader_elected_listener.dump_contents(output_filename);
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }
    }

    
    public static void run(int seconds_to_run)
    {
        Map<Long,Set<CohortInfo.CohortInfoPair>> cohort_map =
            Util.get_connection_info(COHORT_SIZE);

        // create lots of managers
        Set<CohortManager> cohort_managers = new HashSet<CohortManager>();
        Long non_leader_id = null;
        int no_chance_tcp_port = 55555;

        for (Long cohort_id : cohort_map.keySet())
        {            
            Set<CohortInfo.CohortInfoPair> connection_info =
                cohort_map.get(cohort_id);

            boolean cannot_be_leader = false;
            if (non_leader_id == null)
            {
                non_leader_id = cohort_id;
                // first node cannot be leader
                cannot_be_leader = true;
            }
            else
            {
                // make it so that nodes that potentially can be
                // leaders cannot communicate with each other by
                // giving them incorrect ports to listen on and
                // connect to.
                Set<CohortInfo.CohortInfoPair> new_connection_info =
                    new HashSet<CohortInfo.CohortInfoPair>();

                for(CohortInfo.CohortInfoPair pair : connection_info)
                {
                    if (non_leader_id.equals(pair.remote_cohort_info.cohort_id))
                    {
                        // all nodes should be able to connect to
                        // central node.
                        new_connection_info.add(pair);
                        continue;
                    }

                    CohortInfo new_local_cohort_info =
                        new CohortInfo(
                            pair.local_cohort_info.ip_addr_or_hostname,
                            no_chance_tcp_port,
                            pair.local_cohort_info.cohort_id);
                    ++ no_chance_tcp_port;

                    CohortInfo new_remote_cohort_info =
                        new CohortInfo(
                            pair.remote_cohort_info.ip_addr_or_hostname,
                            no_chance_tcp_port,
                            pair.remote_cohort_info.cohort_id);

                    ++ no_chance_tcp_port;
                    
                    CohortInfo.CohortInfoPair new_pair =
                        new CohortInfo.CohortInfoPair(
                            new_remote_cohort_info,new_local_cohort_info);
                    new_connection_info.add(new_pair);
                }

                connection_info = new_connection_info;
            }
            
            CohortManager cohort_manager = 
                new CohortManager(
                    connection_info,TCPCohortConnection.CONNECTION_FACTORY,
                    cohort_id,HEARTBEAT_TIMEOUT_PERIOD_MS,
                    HEARTBEAT_SEND_PERIOD_MS, !cannot_be_leader,
                    MAX_BATCH_SIZE);
            
            cohort_managers.add(cohort_manager);
        }
            
        for (CohortManager cohort_manager : cohort_managers)
        {
            cohort_manager.add_leader_elected_listener(
                leader_elected_listener);
        }

        
        // start all the managers.
        for (CohortManager cohort_manager : cohort_managers)
            cohort_manager.start_manager();

        
        // wait for a while and check if we have a leader.
        try
        {
            // wait five seconds
            Thread.sleep(1000*seconds_to_run);
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
            SergeantCohort.Util.force_assert("Some error.");
        }
    }

    public static class CohortHistoryEvent
    {
        private final long timestamp;
        private final long view_number;
        // if -1, means that it's an election started event.
        private final long leader_id;

        public CohortHistoryEvent(
            long timestamp,long view_number,long leader_id)
        {
            this.timestamp = timestamp;
            this.view_number = view_number;
            this.leader_id = leader_id;
        }

        public StringBuffer jsonize()
        {
            StringBuffer to_return = new StringBuffer();
            to_return.append("{");
            to_return.append("\"timestamp\": " + timestamp + ",");
            to_return.append("\"view_number\": " + view_number + ",");
            to_return.append("\"leader_id\": " + leader_id);
            to_return.append("}");
            return to_return;
        }
    }
    

    public static class CohortHistory
    {
        public final long cohort_id;
        public final List<CohortHistoryEvent> history =
            new ArrayList<CohortHistoryEvent>();
        
        public CohortHistory(long cohort_id)
        {
            this.cohort_id = cohort_id;
        }

        public synchronized void add_election_started_event(long view_number)
        {
            long timestamp = System.nanoTime();
            CohortHistoryEvent to_append =
                new CohortHistoryEvent(timestamp,view_number,-1);
            history.add(to_append);
        }

        public synchronized void add_leader_elected_event(
            long view_number, long leader_id)
        {
            long timestamp = System.nanoTime();
            CohortHistoryEvent to_append =
                new CohortHistoryEvent(timestamp,view_number,leader_id);
            history.add(to_append);
        }

        public synchronized StringBuffer jsonize()
        {
            StringBuffer to_return = new StringBuffer();
            to_return.append("{");
            to_return.append("\"cohort_id\": " + cohort_id + ",");
            to_return.append("\"history\": [");

            for (int i = 0; i < history.size(); ++i)
            {
                CohortHistoryEvent event = history.get(i);
                to_return.append(event.jsonize());
                
                if (i != (history.size() -1))
                    to_return.append(",");
            }

            to_return.append("]}");
            return to_return;
        }
    }
    
    public static class LeaderElectedListener 
        implements ILeaderElectedListener
    {
        final Map<Long, CohortHistory> cohort_map =
            new HashMap<Long,CohortHistory>();
        
        
        @Override
        public synchronized void leader_elected(
            long view_number, long leader_id, long local_cohort_id)
        {
            CohortHistory history = cohort_map.get(local_cohort_id);
            if (history == null)
            {
                history = new CohortHistory(local_cohort_id);
                cohort_map.put(local_cohort_id,history);
            }

            history.add_leader_elected_event(view_number,leader_id);
        }

        @Override
        public synchronized void election_started(
            long election_view_number, long local_cohort_id)
        {
            CohortHistory history = cohort_map.get(local_cohort_id);
            if (history == null)
            {
                history = new CohortHistory(local_cohort_id);
                cohort_map.put(local_cohort_id,history);
            }
            
            history.add_election_started_event(election_view_number);
        }

        protected StringBuffer jsonize()
        {
            StringBuffer to_return = new StringBuffer();
            to_return.append("[");

            int counter = 0;
            for (CohortHistory history : cohort_map.values())
            {
                to_return.append(history.jsonize());
                if (counter != (cohort_map.size() -1))
                    to_return.append(",");
                ++counter;
            }
            to_return.append("]");
            return to_return;
        }
        
        public void dump_contents(String output_filename) throws IOException
        {
            PrintWriter writer = new PrintWriter(output_filename, "UTF-8");
            writer.println(jsonize().toString());
            writer.close();
        }
    }
}
