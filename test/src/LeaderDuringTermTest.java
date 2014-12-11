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
   Start several nodes.  Whenever a node gets elected leader, log the
   node that was elected and its term number.
 */
public class LeaderDuringTermTest
{
    public final static int COHORT_PORTS_INDEX = 0;
    public final static int SECONDS_TO_RUN_INDEX = 1;
    public final static int OUTPUT_FILENAME_INDEX = 2;
    
    public final static int HEARTBEAT_TIMEOUT_PERIOD_MS = 200;
    public final static int HEARTBEAT_SEND_PERIOD_MS = 50;
    public final static int MAX_BATCH_SIZE = 20;
    
    // Create a LeaderElectedListener and register it
    protected final static LeaderElectedListener leader_elected_listener =
        new LeaderElectedListener();

    
    public static void main(String[] args)
    {
        /*
          Should be of form (@see Util.produce_cohort_mappings_from_string):
          
          local_id: host,port,other_id,host,port;
                  host,port,other_id,host,port; ...  | 
          local_id: host,port,other_id,host,port;
                  host,port,other_id,host,port; ...  |
         */
        String cohort_ports = args[COHORT_PORTS_INDEX];
        
        int seconds_to_run = Integer.parseInt(args[SECONDS_TO_RUN_INDEX]);
        String output_filename = args[OUTPUT_FILENAME_INDEX];

        Map<Long,Set<CohortInfo.CohortInfoPair>> connection_set =
            Util.produce_cohort_mappings_from_string(cohort_ports);
        
        run(seconds_to_run,connection_set);
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

    
    public static void run(
        int seconds_to_run, Map<Long,Set<CohortInfo.CohortInfoPair>> cohort_map)
    {
        // create lots of managers
        Set<CohortManager> cohort_managers = new HashSet<CohortManager>();


        // create lots of managers
        for (Long cohort_id : cohort_map.keySet())
        {
            Set<CohortInfo.CohortInfoPair> connection_info =
                cohort_map.get(cohort_id);
            CohortManager cohort_manager =
                new CohortManager(
                    connection_info,TCPCohortConnection.CONNECTION_FACTORY,
                    cohort_id,HEARTBEAT_TIMEOUT_PERIOD_MS,
                    HEARTBEAT_SEND_PERIOD_MS, MAX_BATCH_SIZE);
            
            cohort_managers.add(cohort_manager);
        }

        // add listeners for each
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

    public static class LeaderViewNumberTuple
    {
        protected final long leader_id;
        protected final long view_number;

        public LeaderViewNumberTuple(long leader_id,long view_number)
        {
            this.leader_id = leader_id;
            this.view_number = view_number;
        }

        public StringBuffer jsonize()
        {
            StringBuffer to_return = new StringBuffer();
            to_return.append("{");
            to_return.append("\"view_number\": " + view_number + ",");
            to_return.append("\"leader_id\": " + leader_id);
            to_return.append("}");
            return to_return;
        }
    }

    
    public static class LeaderElectedListener 
        implements ILeaderElectedListener
    {
        final List<LeaderViewNumberTuple> leader_view_number_list =
            new ArrayList<LeaderViewNumberTuple>();
        
        @Override
        public synchronized void leader_elected(
            long view_number, long leader_id, long local_cohort_id)
        {
            // only log if message is from leader.  prevents
            // double-counting
            if (leader_id == local_cohort_id)
            {
                LeaderViewNumberTuple to_add =
                    new LeaderViewNumberTuple(leader_id,view_number);
                leader_view_number_list.add(to_add);
            }
        }

        @Override
        public synchronized void election_started(
            long election_view_number, long local_cohort_id)
        {
            System.out.println(
                "Election started for view number " + election_view_number +
                " on cohort " + local_cohort_id );
            // nothing to do here.
        }

        protected StringBuffer jsonize()
        {
            StringBuffer to_return = new StringBuffer();
            to_return.append("[");


            int counter = 0;
            for (LeaderViewNumberTuple tuple: leader_view_number_list)
            {
                to_return.append(tuple.jsonize());
                if (counter != (leader_view_number_list.size() -1))
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
