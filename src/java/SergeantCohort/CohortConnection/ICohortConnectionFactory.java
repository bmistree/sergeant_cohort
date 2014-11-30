package SergeantCohort.CohortConnection;

import SergeantCohort.CohortInfo;

public interface ICohortConnectionFactory
{
    public ICohortConnection construct(
        CohortInfo local_info, CohortInfo remote_info);
}
