package SergeantCohort;

public interface ILeaderElectedListener
{
    public void leader_elected(
        long view_number, long leader_id, long local_cohort_id);

    /**
       Gets called whenever enter election state.
     */
    public void election_started(long view_number, long local_cohort_id);
}