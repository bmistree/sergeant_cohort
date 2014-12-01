package SergeantCohort;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

import ProtocolLibs.CohortMessageProto.CohortMessage;
import ProtocolLibs.LeaderCommandProto.LeaderCommand;
import ProtocolLibs.FollowerCommandAckProto.FollowerCommandAck;
import ProtocolLibs.ElectionProposalProto.ElectionProposal;
import ProtocolLibs.ElectionProposalResponseProto.ElectionProposalResponse;

import SergeantCohort.CohortConnection.ICohortConnectionFactory;
import SergeantCohort.CohortConnection.ICohortConnection;
import SergeantCohort.CohortConnection.ICohortConnectionListener;
import SergeantCohort.CohortConnection.ICohortMessageListener;

/**
   Makes connections to many cohorts and then tries to elect a leader
   amongst them.
 */
public class CohortManager
    implements ICohortManager, ICohortConnectionListener,
               ICohortMessageListener
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
       view_number's meaning in different states:

       In ELECTION state, the number represents a passed view (not a
       future view).

       IN FOLLOWER or LEADER state, the number represents the current
       view number.
     */
    protected long view_number = 0;
    /**
       Set to null after election has completed.
     */
    protected ElectionContext election_context;

    
    /**
       Also protects current_leader_id, view_number, and
       election_context.
     */
    protected final ReentrantLock state_lock = new ReentrantLock();

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
       Id of local cohort.
     */
    final public int local_cohort_id;
    
    /**
       @param connection_info --- Connection information that we
       should use to connect to remote cohort nodes.

       @param cohort_connection_factory --- Factory to use to generate
       connections.

       @param local_cohort_id
     */
    public CohortManager(
        Set<CohortInfo.CohortInfoPair> connection_info,
        ICohortConnectionFactory cohort_connection_factory,
        int local_cohort_id)
    {
        for (CohortInfo.CohortInfoPair pair : connection_info)
        {
            ICohortConnection connection =
                cohort_connection_factory.construct(
                    pair.local_cohort_info,pair.remote_cohort_info);
            cohort_connections.add(connection);

            // subscribe as connection and message listeners
            connection.add_connection_listener(this);
            connection.add_cohort_message_listener(this);
        }
        
        this.local_cohort_id = local_cohort_id;
        election_context = new ElectionContext(view_number,local_cohort_id);
        start_elect_self_thread();
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

    /**
       Generate a thread to start trying to change view number.
     */
    protected void start_elect_self_thread()
    {
        final CohortManager this_ptr = this;
        
        Thread t = new Thread()
        {
            @Override
            public void run()
            {
                this_ptr.elect_self_thread();
            }
        };
    }
    
    /**
       Sends messages to all cohorts to try to elect self as leader.
       Should only be called from start_elect_self_thread.
     */
    private void elect_self_thread()
    {
        state_lock.lock();
        long new_view_number = -1;
        try
        {
            // election has passed.
            if (election_context.last_view_number < view_number)
                return;

            // another node swooped in and already got us to vote for
            // it.
            if (election_context.voting_for_cohort_id != local_cohort_id)
                return;
            
            new_view_number = election_context.last_view_number + 1;
        }
        finally
        {
            state_lock.unlock();
        }


        // ask all cohorts to vote for me as new leader.
        ElectionProposal.Builder election_proposal =
            ElectionProposal.newBuilder();
        election_proposal.setNextProposedViewNumber(new_view_number);
        election_proposal.setNodeId(local_cohort_id);

        CohortMessage.Builder cohort_message =
            CohortMessage.newBuilder();
        cohort_message.setElectionProposal(election_proposal);

        send_message_to_all_connections(cohort_message);
    }

    /**
       Send a message out of all connections.  
     */
    protected void send_message_to_all_connections(
        CohortMessage.Builder to_send)
    {
        for (ICohortConnection connection : cohort_connections)
        {
            // FIXME: what happens if cannot enqueue message????
            if (! connection.send_message(to_send))
            {
                Util.force_assert(
                    "FIXME: handle case where cannot send message");
            }
        }
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
        state_lock.lock();
        
        try
        {
            int remote_timed_out_id = cohort_connection.remote_cohort_id();
            if (state == ManagerState.FOLLOWER)
            {
                // if leader times out, then try to elect self as new
                // leader.
                if (current_leader_id.equals(remote_timed_out_id))
                {
                    current_leader_id = null;
                    state = ManagerState.ELECTION;
                    election_context =
                        new ElectionContext(view_number,local_cohort_id);
                    start_elect_self_thread();
                }
            }
        }
        finally
        {
            state_lock.unlock();
        }
    }
    
    @Override
    public void handle_connection_up(ICohortConnection cohort_connection)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in handle_connection_up stub");
    }


    /***************** ICohortMessageListener overrides ********/
    @Override
    public void leader_command(
        ICohortConnection cohort_connection,LeaderCommand leader_command)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in leader_command stub");
    }

    @Override
    public void follower_command_ack(
        ICohortConnection cohort_connection,
        FollowerCommandAck follower_command_ack)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in follower_command_ack stub");
    }

    @Override
    public void election_proposal(
        ICohortConnection cohort_connection,
        ElectionProposal election_proposal)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in election_proposal stub");
    }
    
    @Override
    public void election_proposal_response(
        ICohortConnection cohort_connection,
        ElectionProposalResponse election_proposal_resp)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in election_proposal_response stub");
    }
}