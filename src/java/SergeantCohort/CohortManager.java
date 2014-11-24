package SergeantCohort;

import java.util.Set;

public class CohortManager implements ICohortManager
{
    protected enum ManagerState
    {
        ELECTION, LEADER, FOLLOWER;
    }

    /**
       Each node starts in initial state of election.
     */
    protected ManagerState state = ManagerState.ELECTION;

    final Set<CohortInfo> partner_set;
    final CohortInfo manager_info;

    public CohortManager(
        Set<CohortInfo> partner_set, CohortInfo manager_info)
    {
        this.partner_set = partner_set;
        this.manager_info = manager_info;
    }

    /*************** ICohortManager methods **********/
    @Override
    public void submit_command(byte[] serialized_command)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in submit_command method");
    }
    
}