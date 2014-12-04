package test;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import SergeantCohort.CohortManager;
import SergeantCohort.CohortInfo;
import SergeantCohort.CohortConnection.TCPCohortConnection;
import SergeantCohort.ILeaderElectedListener;

/**
   Start three nodes, two can't communicate and the third node can
   never be leader.  Check if we get resulting flapping if two nodes
   cannot communicate.
 */
public class LeaderFlappingTest
{
    public final static long COHORT_SIZE = 3L;
    public final static int HEARTBEAT_TIMEOUT_PERIOD_MS = 200;
    public final static int HEARTBEAT_SEND_PERIOD_MS = 60;
    
    public static void main(String[] args)
    {
        if (run())
            System.out.println("\nLeader flapping test SUCCEEDED\n");
        else
            System.out.println("\nLeader flapping test FAILED\n");
    }


    /**
       @returns true if test passed.  false if test failed.
     */
    public static boolean run()
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
                    HEARTBEAT_SEND_PERIOD_MS, !cannot_be_leader);
            
            cohort_managers.add(cohort_manager);
        }

        // Create a LeaderElectedListener and register it
        LeaderElectedListener leader_elected_listener =
            new LeaderElectedListener();
            
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
            Thread.sleep(1000*5);
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
            SergeantCohort.Util.force_assert("Some error.");
        }

        // print number of leader changes that occurred.
        System.out.println("\n\n");
        System.out.println(
            leader_elected_listener.total_number_leader_changes);
        System.out.println("\n\n");
        
        return true;
    }


    public static class LeaderElectedListener 
        implements ILeaderElectedListener
    {
        // increments each time we change leaders.
        public int total_number_leader_changes = 0;
        
        @Override
        public void leader_elected(
            long view_number, long leader_id, long local_cohort_id)
        {
            System.out.println(
                "\nGot a leader elected; leader id: " +
                leader_id + "; local_id: " + local_cohort_id +"\n");
            if (leader_id == local_cohort_id)
                ++ total_number_leader_changes;
        }
    }
    
}
