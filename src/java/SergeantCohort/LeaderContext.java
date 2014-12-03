package SergeantCohort;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import SergeantCohort.CohortConnection.ICohortConnection;

public class LeaderContext
{
    /**
       Keys are cohort ids, values are next indices to send to node.
       
       For each server, the next index to send to that server.
       Initialized as last log index + 1.
     */
    final protected Map<Long,Long> next_index_map = new HashMap<Long,Long>();
    /**
       Keys are cohort ids, values are index of highest log entry
       known to be replicated on node.  Starts at 0.
     */
    final protected Map<Long,Long> match_index_map = new HashMap<Long,Long>();


    public LeaderContext(
        Set<ICohortConnection> cohort_connections,
        long current_log_size)
    {
        for (ICohortConnection connection : cohort_connections)
        {
            long remote_cohort_id = connection.remote_cohort_id();
            
            // initialized to last log index + 1
            next_index_map.put(remote_cohort_id,current_log_size + 1);

            // initalized to zero
            match_index_map.put(remote_cohort_id, 0L);
        }
    }
}