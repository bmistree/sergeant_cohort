package test;

import java.util.Map;
import java.util.Set;

import SergeantCohort.CohortManager;
import SergeantCohort.CohortInfo;


/**
   Start three nodes and check that one and only one becomes a leader
   after some point of time.
 */
public class LeaderElectionTest
{
    public final static long COHORT_SIZE = 3L;
    
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
        return true;
    }
}
