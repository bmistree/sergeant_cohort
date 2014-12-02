package SergeantCohort.CohortConnection;

import SergeantCohort.CohortInfo;
import SergeantCohort.ILastViewNumberSupplier;

public interface ICohortConnectionFactory
{
    public ICohortConnection construct(
        CohortInfo local_info, CohortInfo remote_info,
        ILastViewNumberSupplier view_number_supplier);
}
