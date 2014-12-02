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
        Map<Long,Set<CohortInfo.CohortInfoPair>> to_return =
            new HashMap<Long,Set<CohortInfo.CohortInfoPair>>();
        for (long i = 0; i < num_cohort_nodes; ++i)
        {
            Set<CohortInfo.CohortInfoPair> cohort_info_set =
                new HashSet<CohortInfo.CohortInfoPair>();
            to_return.put(i,cohort_info_set);
            
            int local_cohort_tcp_port_base =
                BASE_TCP_PORT +  (int)((i) * num_cohort_nodes);
            

            for (long j = 0; j < num_cohort_nodes; ++j)
            {
                // don't add a connection for self.
                if (j == i)
                    continue;

                // from i and j, determine which tcp ports each
                // connection should be listening on.
                int remote_cohort_tcp_port_base =
                    BASE_TCP_PORT +  (int)((j) * num_cohort_nodes);

                int local_tcp_port = local_cohort_tcp_port_base + (int)(j);
                int remote_tcp_port = remote_cohort_tcp_port_base + (int)(i);

                
                long local_cohort_id = i;
                long remote_cohort_id = j;


                // create cohort information paris and add them to set.
                CohortInfo local_info = new CohortInfo (
                    "127.0.0.1",local_tcp_port,local_cohort_id);

                CohortInfo remote_info = new CohortInfo (
                    "127.0.0.1",remote_tcp_port,remote_cohort_id);

                CohortInfo.CohortInfoPair pair =
                    new CohortInfo.CohortInfoPair(remote_info,local_info);

                cohort_info_set.add(pair);
            }
        }
        return to_return;
    }
}