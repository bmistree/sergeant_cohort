package SergeantCohort;

public interface ILeaderElectedListener
{
    public void leader_elected(
        long view_number, long leader_id, long local_cohort_id);
}