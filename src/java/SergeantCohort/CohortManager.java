package SergeantCohort;

import java.util.Set;
import java.util.HashSet;

import SergeantCohort.CohortConnection.ICohortConnectionFactory;
import SergeantCohort.CohortConnection.ICohortConnection;

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
    final private Set<ICohortConnection> cohort_connections =
        new HashSet<ICohortConnection>();

    /**
       @param connection_info --- Connection information that we
       should use to connect to remote cohort nodes.

       @param cohort_connection_factory --- Factory to use to generate
       connections.
     */
    public CohortManager(
        Set<CohortInfo.CohortInfoPair> connection_info,
        ICohortConnectionFactory cohort_connection_factory)
    {
        for (CohortInfo.CohortInfoPair pair : connection_info)
        {
            ICohortConnection connection =
                cohort_connection_factory.construct(
                    pair.local_cohort_info,pair.remote_cohort_info);
            cohort_connections.add(connection);
        }
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