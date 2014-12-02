package SergeantCohort;

import java.util.Set;
import java.util.HashSet;

/**
   Contains meta information necessary for an election cycle.
 */
public class ElectionContext
{
    public final long voting_for_cohort_id;
    /**
       Which view number we're holding this election for.
     */
    public final long election_view_number;
    
    /**
       Only for those trying to become leaders themselves.
     */
    public final Set<Long> votes_received_set = new HashSet<Long>();
    
    
    public ElectionContext(
        long voting_for_cohort_id,long election_view_number)
    {
        this.voting_for_cohort_id = voting_for_cohort_id;
        // handles case where trying to elect self
        votes_received_set.add(voting_for_cohort_id);
        this.election_view_number = election_view_number;
    }
}