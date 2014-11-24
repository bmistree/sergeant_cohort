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
}