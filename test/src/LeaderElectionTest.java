package test;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import SergeantCohort.CohortManager;
import SergeantCohort.CohortParamContext;
import SergeantCohort.CohortInfo;
import SergeantCohort.CohortConnection.TCPCohortConnection;


/**
   Start three nodes and check that one and only one becomes a leader
   after some point of time.
 */
public class LeaderElectionTest
{
    public final static long COHORT_SIZE = 3L;

    public final static long BASE_MAX_TIME_TO_WAIT_FOR_REELECTION_MS = 200L;
    public final static int HEARTBEAT_TIMEOUT_PERIOD_MS = 500;
    public final static int HEARTBEAT_SEND_PERIOD_MS = 150;
    public final static int MAX_BATCH_SIZE = 20;
    public final static CohortParamContext param_context =
        new CohortParamContext(
            BASE_MAX_TIME_TO_WAIT_FOR_REELECTION_MS,
            HEARTBEAT_TIMEOUT_PERIOD_MS,HEARTBEAT_SEND_PERIOD_MS,
            MAX_BATCH_SIZE);

    
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
                    connection_info,TCPCohortConnection.CONNECTION_FACTORY,
                    cohort_id,param_context);

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

        // check that have one and only one leader
        int num_leaders = 0;
        for (CohortManager cohort_manager : cohort_managers)
        {
            if (cohort_manager.is_leader())
                ++num_leaders;
        }

        if (num_leaders != 1)
            return false;
        
        return true;
    }
}
