package SergeantCohort;

import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

import ProtocolLibs.CohortMessageProto.CohortMessage;
import ProtocolLibs.LeaderCommandProto.LeaderCommand;
import ProtocolLibs.FollowerCommandAckProto.FollowerCommandAck;
import ProtocolLibs.ElectionProposalProto.ElectionProposal;
import ProtocolLibs.ElectionProposalResponseProto.ElectionProposalResponse;
import ProtocolLibs.NewLeaderProto.NewLeader;

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
    /**
       How long to wait before retrying reelecting self.
     */
    public final static long BASE_MAX_TIME_TO_WAIT_FOR_REELECTION_MS = 100L;
    
    /**
       If we transition into election state, by receiving a message
       from another node, wait this long before trying to elect
       ourself.  Handles case where cohort solicits votes (plunging
       everyone into election), and then crashes before becoming
       leader.
     */
    public final static long MS_TO_WAIT_BEFORE_STARTING_SELF_ELECT = 300L;

    
    protected final static Random rand = new Random();
    
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
       We only want to elect leaders who have the most up to date log.
     */
    protected long last_log_index = 0;
    protected long last_log_term = 0;
    
    
    /**
       Also protects current_leader_id, view_number, and
       election_context.
     */
    protected final ReentrantLock state_lock = new ReentrantLock();

    /**
       Start with no leader when in election state.
     */
    protected Long current_leader_id = null;
    
    /**
       Connection information that we should use to connect to remote
       cohort nodes.
     */
    final private Set<ICohortConnection> cohort_connections =
        new HashSet<ICohortConnection>();

    /**
       Id of local cohort.
     */
    final public long local_cohort_id;

    final public int quorum_size;
    
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
        long local_cohort_id)
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

        // added one because connection info does not include me.
        quorum_size = ((int)((connection_info.size() + 1)/2.)) + 1;
        
        
        this.local_cohort_id = local_cohort_id;
        election_context = new ElectionContext(local_cohort_id);
        start_elect_self_thread(0);
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
    protected void start_elect_self_thread(
        final long ms_to_wait_before_electing_self)
    {
        final CohortManager this_ptr = this;
        
        Thread t = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    Thread.sleep(ms_to_wait_before_electing_self);
                }
                catch(InterruptedException ex)
                {
                    Util.force_assert(
                        "Unexpected interruption during " +
                        "start_elect_self_thread");
                }
                this_ptr.elect_self_thread(0);
            }
        };
        t.setDaemon(true);
        t.start();
    }

    /**
       Sends messages to all cohorts to try to elect self as leader.
       Should only be called from start_elect_self_thread.

       Automatically tries re-electing self in case we do not hear
       from enough to form a quorom in a given period of time.  Note
       that this handles the case where we've voted for someone else
       but they went down.
       
       @param num_times_called --- If we do not receive a quorum after
       some period of time, then we retry electing self.  We use
       num_times_called to determine how long we should wait before
       retrying electing self, using exponential backoff.  Each
       num_times_called, we can wait up to 2x longer than previous
       time.
     */
    private void elect_self_thread(int num_times_called)
    {
        ElectionProposal.Builder election_proposal =
            ElectionProposal.newBuilder();
        
        state_lock.lock();
        try
        {
            // election has passed
            if (state != ManagerState.ELECTION)
                return;

            // create a new election context, requiring a new set of
            // voter responses.
            election_context = new ElectionContext(local_cohort_id);
            
            // increment ballot so that know will be sending out
            // election requests for a new, unique ballot number.
            view_number += 1;
            long proposed_view_number = view_number + 1;
            
            // ask all cohorts to vote for me as new leader.
            election_proposal.setNextProposedViewNumber(proposed_view_number);
            election_proposal.setNodeId(local_cohort_id);
            election_proposal.setLastLogIndex(last_log_index);
            election_proposal.setLastLogTerm(last_log_term);            
        }
        finally
        {
            state_lock.unlock();
        }

        CohortMessage.Builder cohort_message =
            CohortMessage.newBuilder();
        cohort_message.setElectionProposal(election_proposal);

        send_message_to_all_connections(cohort_message);


        // wait a random period of time.  If after this time, we have
        // not transitioned out of election then try to elect self
        // again.
        long max_time_to_wait_ms =
            (1L<< num_times_called)*BASE_MAX_TIME_TO_WAIT_FOR_REELECTION_MS;
        long ms_to_wait_before_retry =
            (long)(rand.nextFloat() * max_time_to_wait_ms);
        
        try
        {
            Thread.sleep(ms_to_wait_before_retry);
        }
        catch(InterruptedException ex)
        {
            Util.force_assert("Error: unexpected exception");
        }

        // tail recursion: okay
        elect_self_thread(num_times_called + 1);
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
            long remote_timed_out_id = cohort_connection.remote_cohort_id();
            if (state == ManagerState.FOLLOWER)
            {
                // if leader times out, then try to elect self as new
                // leader.
                if (current_leader_id.equals(remote_timed_out_id))
                {
                    current_leader_id = null;
                    state = ManagerState.ELECTION;
                    election_context =
                        new ElectionContext(local_cohort_id);
                    start_elect_self_thread(0);
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
    public void new_leader(
        ICohortConnection cohort_connection,NewLeader new_leader)
    {
        // FIXME: Fill in stub
        Util.force_assert("Must fill in new_leader stub");
    }

    
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

    /**
       We received an election proposal request.

       Semantics:
          1) Return false if proposed term is less than current
          2) Grant vote if:
               a) Have not voted for anyone else this term
               b) Log is at least as up to date as our log.
     */
    @Override
    public void election_proposal(
        ICohortConnection cohort_connection,
        ElectionProposal election_proposal)
    {
        long proposed_view_number =
            election_proposal.getNextProposedViewNumber();
        boolean vote_granted = false;
        long cohort_id = cohort_connection.remote_cohort_id();

        
        state_lock.lock();
        try
        {
            if (proposed_view_number <= view_number)
            {
                // wrong view number: do not vote for this candidate
                vote_granted = false;
            }
            else
            {
                if ((election_context != null) &&
                    (election_context.voting_for_cohort_id != cohort_id))
                {
                    // already voted for another candidate: do not
                    // vote for this candidate
                    vote_granted = false;
                }
                else if ((election_proposal.getLastLogIndex() < last_log_index) ||
                         (election_proposal.getLastLogTerm() < last_log_term))
                {
                    // candidate's log isn't as up to date as mine: do
                    // not vote for this candidate.
                    vote_granted = false;
                }
                else
                {
                    // vote for this candidate.
                    vote_granted = true;
                    // this way, if we need to retrigger voting while
                    // we're in the election stage, we will start with
                    // view numbers at least as large as the one
                    // already proposed.
                    view_number = proposed_view_number;
                    if (election_context == null)
                    {
                        // means that we had not previously been in an
                        // electing state.  enter one.
                        election_context = new ElectionContext(cohort_id);
                    }

                    if (state != ManagerState.ELECTION)
                    {
                        state = ManagerState.ELECTION;
                        // calling this here handles the case that the
                        // election sender fails: if we aren't in a
                        // stable state after
                        // MS_TO_WAIT_BEFORE_STARTING_SELF_ELECT, then
                        // we will try to elect ourself.
                        start_elect_self_thread(
                            MS_TO_WAIT_BEFORE_STARTING_SELF_ELECT);
                    }
                    
                    
                    // FIXME: may need to stop being leader/notify
                    // others that I am no longer leader.
                }
            }
        }
        finally
        {
            state_lock.unlock();
        }
        
        ElectionProposalResponse.Builder election_proposal_response =
            ElectionProposalResponse.newBuilder();
        election_proposal_response.setProposedViewNumber(proposed_view_number);
        election_proposal_response.setVoteGranted(vote_granted);

        
        CohortMessage.Builder cohort_message =
            CohortMessage.newBuilder();
        cohort_message.setElectionProposalResponse(election_proposal_response);
        cohort_connection.send_message(cohort_message);
    }


    /**
       Receive an election proposal response.
     */
    @Override
    public void election_proposal_response(
        ICohortConnection cohort_connection,
        ElectionProposalResponse election_proposal_resp)
    {
        boolean vote_granted =
            election_proposal_resp.getVoteGranted();
        // nothing to do.
        if (! vote_granted)
            return;
        
        long proposed_view_number =
            election_proposal_resp.getProposedViewNumber();
        long remote_cohort_id = cohort_connection.remote_cohort_id();
        
        state_lock.lock();
        try
        {
            // discard: had already transitioned into new state
            if (election_context == null)
                return;
            
            if ((view_number + 1) != proposed_view_number)
            {
                // discard: vote for an old message
                return;
            }


            election_context.votes_received_set.add(remote_cohort_id);

            // Check to make self leader.
            if (election_context.votes_received_set.size() >= quorum_size)
            {
                view_number = proposed_view_number;
                election_context = null;
                state = ManagerState.LEADER;

                // FIXME: tell everyone else that I am now leader.
            }
        }
        finally
        {
            state_lock.unlock();
        }
    }
}