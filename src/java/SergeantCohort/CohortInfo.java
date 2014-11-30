package SergeantCohort;

public class CohortInfo
{
    public final String ip_addr_or_hostname;
    public final int port;
    public final int cohort_id;

    public CohortInfo (
        String ip_addr_or_hostname, int port, int cohort_id)
    {
        this.ip_addr_or_hostname = ip_addr_or_hostname;
        this.port = port;
        this.cohort_id = cohort_id;
    }


    public static class CohortInfoPair
    {
        public final CohortInfo remote_cohort_info;
        public final CohortInfo local_cohort_info;
        
        public CohortInfoPair(
            CohortInfo remote_cohort_info, CohortInfo local_cohort_info)
        {
            this.remote_cohort_info = remote_cohort_info;
            this.local_cohort_info = local_cohort_info;
        }
    }
}