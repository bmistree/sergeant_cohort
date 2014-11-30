package SergeantCohort;

import java.util.Set;

/**
   Makes connections to many cohorts and then tries to elect a leader
   amongst them.
 */
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

    /**
       Connection information that we should use to connect to remote
       cohort nodes.
     */
    final Set<CohortInfo.CohortInfoPair> connection_info;

    /**
       @param connection_info ---
       {@link CohortManager#connection_info}
     */
    public CohortManager(Set<CohortInfo.CohortInfoPair> connection_info)
    {
        this.connection_info = connection_info;
    }

    /**
       Actually start connections to other cohort nodes.
     */
    public void start_manager()
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in start_manager");
    }
    

    /*************** ICohortManager methods **********/
    @Override
    public void submit_command(byte[] serialized_command)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in submit_command method");
    }
}