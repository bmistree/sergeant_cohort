package test;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import SergeantCohort.CohortManager;
import SergeantCohort.CohortInfo;
import static SergeantCohort.CohortConnection.TCPCohortConnection.TCPCohortConnectionFactory;


/**
   Start three nodes and check that one and only one becomes a leader
   after some point of time.
 */
public class LeaderElectionTest
{
    public final static long COHORT_SIZE = 3L;
    public final static int HEARTBEAT_TIMEOUT_PERIOD_MS = 500;
    public final static int HEARTBEAT_SEND_PERIOD_MS = 150;
    
    public static void main(String[] args)
    {
        if (run())
            System.out.println("\nLeader election test SUCCEEDED\n");
        else
            System.out.println("\nLeader election test FAILED\n");
    }


    /**
       @returns true if test passed.  false if test failed.
     */
    public static boolean run()
    {
        TCPCohortConnectionFactory tcp_cohort_connection_factory =
            new TCPCohortConnectionFactory(
                HEARTBEAT_TIMEOUT_PERIOD_MS,HEARTBEAT_SEND_PERIOD_MS);

        Map<Long,Set<CohortInfo.CohortInfoPair>> cohort_map =
            Util.get_connection_info(COHORT_SIZE);

        // create lots of managers
        Set<CohortManager> cohort_managers = new HashSet<CohortManager>();
        for (Long cohort_id : cohort_map.keySet())
        {
            Set<CohortInfo.CohortInfoPair> connection_info =
                cohort_map.get(cohort_id);
            CohortManager cohort_manager =
                new CohortManager(
                    connection_info,tcp_cohort_connection_factory,cohort_id);

            cohort_managers.add(cohort_manager);
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

        
        return true;
    }
}
