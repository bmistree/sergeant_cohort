package test;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import SergeantCohort.CohortManager;
import SergeantCohort.CohortParamContext;
import SergeantCohort.CohortInfo;
import SergeantCohort.CohortConnection.TCPCohortConnection;
import SergeantCohort.IApplyEntryListener;


/**
   Start three nodes and check that one and only one becomes a leader
   after some point of time.
 */
public class AppendEntriesTest
{
    public final static long COHORT_SIZE = 3L;
    
    public final static long BASE_MAX_TIME_TO_WAIT_FOR_REELECTION_MS = 200L;
    public final static int HEARTBEAT_TIMEOUT_PERIOD_MS = 500;
    public final static int HEARTBEAT_SEND_PERIOD_MS = 150;
    public final static int MAX_BATCH_SIZE = 20;
    public final static int NUM_ENTRIES = 100;
    
    public final static CohortParamContext param_context =
        new CohortParamContext(
            BASE_MAX_TIME_TO_WAIT_FOR_REELECTION_MS,
            HEARTBEAT_TIMEOUT_PERIOD_MS,HEARTBEAT_SEND_PERIOD_MS,
            MAX_BATCH_SIZE);

    
    public static void main(String[] args)
    {
        if (run())
            System.out.println("\nAppend entries test SUCCEEDED\n");
        else
            System.out.println("\nAppend entries test FAILED\n");
    }


    /**
       @returns true if test passed.  false if test failed.
     */
    public static boolean run()
    {
        Map<Long,Set<CohortInfo.CohortInfoPair>> cohort_map =
            Util.get_connection_info(COHORT_SIZE);

        ExternalizedCounter externalized_counter = new ExternalizedCounter();
        
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
            cohort_manager.add_apply_entry_listener(externalized_counter);
        }
        
        // start all the managers.
        for (CohortManager cohort_manager : cohort_managers)
            cohort_manager.start_manager();

        // wait for a while to get a leader
        try
        {
            // wait five seconds
            Thread.sleep(1000*3);
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
            SergeantCohort.Util.force_assert("Some error.");
        }

        // find leader
        CohortManager leader = null;
        for (CohortManager cohort_manager : cohort_managers)
        {
            if (cohort_manager.is_leader())
                leader = cohort_manager;
        }

        // ask leader to add NUM_ENTRIES
        for (int i = 0; i < NUM_ENTRIES; ++i)
        {
            byte[] dummy_entry = new byte[0];
            leader.add_entry_if_leader(dummy_entry);
        }

        // wait for values to be updated.
        try
        {
            Thread.sleep(1000*5);
        }
        catch(InterruptedException ex)
        {
            ex.printStackTrace();
            return false;
        }

        // check that got the correct number of externalized values
        int expected_number_externalized_values =
            NUM_ENTRIES * (int)COHORT_SIZE;

        if (externalized_counter.num_times_applied !=
            expected_number_externalized_values)
        {
            System.out.println("\nNum times applied");
            System.out.println(externalized_counter.num_times_applied);
            System.out.println("\n");
            return false;
        }
        
        return true;
    }

    public static class ExternalizedCounter implements IApplyEntryListener
    {
        public int num_times_applied = 0;
        
        @Override
        public synchronized void apply_entry (byte[] entry)
        {
            ++num_times_applied;
        }
    }
}
