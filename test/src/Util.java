package test;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import SergeantCohort.CohortInfo;


public class Util
{
    protected final static int BASE_TCP_PORT = 2222;

    
    /**
       Keys of return map are ids for target cohorts and values are
       sets that we should pass in to their CohortManagers to
       initialize them.
     */
    public static Map<Long,Set<CohortInfo.CohortInfoPair>>
        get_connection_info(long num_cohort_nodes)
    {
        // keys are cohort ids; values are their local cohort info.
        Map<Long, CohortInfo> cohort_map = new HashMap<Long,CohortInfo>();
 
        for (long i = 0; i < num_cohort_nodes; ++i)
        {
            int tcp_port = (int)(BASE_TCP_PORT + i);
            long cohort_id = i;
            // listen locally on all interfaces.
            CohortInfo cohort_info = new CohortInfo (
                "127.0.0.1",tcp_port,cohort_id);

            cohort_map.put(cohort_id,cohort_info);
        }


        Map<Long,Set<CohortInfo.CohortInfoPair>> to_return =
            new HashMap<Long,Set<CohortInfo.CohortInfoPair>>();
        for (long i = 0; i < num_cohort_nodes; ++i)
        {
            CohortInfo local_info = cohort_map.get(i);
            
            Set<CohortInfo.CohortInfoPair> value =
                new HashSet<CohortInfo.CohortInfoPair>();
            for (long j = 0; j < num_cohort_nodes; ++j)
            {
                // do not list self in connection set
                if (i == j)
                    continue;

                CohortInfo remote_info = cohort_map.get(j);

                CohortInfo.CohortInfoPair pair =
                    new CohortInfo.CohortInfoPair(remote_info,local_info);

                value.add(pair);
            }
            to_return.put(i,value);
        }

        return to_return;
    }
}