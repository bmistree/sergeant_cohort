package SergeantCohort;

import java.util.Set;
import java.util.HashSet;

import SergeantCohort.CohortConnection.ICohortConnectionFactory;
import SergeantCohort.CohortConnection.ICohortConnection;
import SergeantCohort.CohortConnection.ICohortConnectionListener;
import SergeantCohort.CohortConnection.ICohortMessageListener;

/**
   Makes connections to many cohorts and then tries to elect a leader
   amongst them.
 */
public class CohortManager
    implements ICohortManager, ICohortConnectionListener
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
       Start with no leader when in election state.
     */
    protected Integer current_leader_id = null;
    
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

            // subscribe as connection and message listeners
            connection.add_connection_listener(this);
            //connection.add_cohort_message_listener(this);
        }
    }

    /**
       Actually start connections to other cohort nodes.
     */
    public void start_manager()
    {
        for (ICohortConnection connection : cohort_connections)
            connection.start_service();

        // FIXME: fill in stub.  Still need to send leader messages;
        Util.force_assert("Must fill in start_manager method");
    }
    

    /*************** ICohortManager methods **********/
    @Override
    public void submit_command(byte[] serialized_command)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in submit_command method");
    }

    /***** ICohortConnectionListener overrides*/
    @Override
    public void handle_connection_timeout(ICohortConnection cohort_connection)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in handle_connection_timeout stub");
    }
    @Override
    public void handle_connection_up(ICohortConnection cohort_connection)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in handle_connection_up stub");
    }
}