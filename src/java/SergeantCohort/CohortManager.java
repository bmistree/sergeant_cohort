package SergeantCohort;

import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import ProtocolLibs.CohortMessageProto.CohortMessage;
import ProtocolLibs.AppendEntriesProto.AppendEntries;
import ProtocolLibs.AppendEntriesResponseProto.AppendEntriesResponse;
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
    implements ICohortManager, ICohortMessageListener,
               ILeaderDownListener, IAppendEntriesSupplier
{
    public final static long UNKNOWN_LEADER_ID = -1;
    
    /**
       How long to wait before retrying reelecting self.
     */
    public final static long BASE_MAX_TIME_TO_WAIT_FOR_REELECTION_MS = 100L;
    
    /**
       If we do not receive a heartbeat message in this period of ms,
       then we determine that the connection is dead and notify
       connection listeners.
     */
    protected final int heartbeat_timeout_period_ms;
    /**
       TCPCohortConnection should send a heartbeat this frequently.
     */
    protected final int heartbeat_send_period_ms;

    
    protected final static Random rand = new Random();

    protected final Log log;
    
    protected enum ManagerState
    {
        ELECTION, LEADER, FOLLOWER;
    }

    /**
       Should only be non-null when we become leader. When stop being
       leader, it will automaticaly stop itself.
     */
    protected HeartbeatSendingService heartbeat_sending_service = null;
    /**
       Should only be non-null when we become a follower.  When stop
       being follower, should explicitly stop it.
     */
    protected HeartbeatListeningService heartbeat_listening_service = null;
    
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
       Set to null after election has completed.  May be null when
       election hasn't completed.
     */
    protected ElectionContext election_context;
    /**
       Set to null after no longer leader.
     */
    protected LeaderContext leader_context = null;
    
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
       Start with no leader when in election state.  Set to -1 if do
       not know who leader is.
     */
    protected Long current_leader_id = null;
    
    /**
       Connection information that we should use to connect to remote
       cohort nodes.
     */
    final private Set<ICohortConnection> cohort_connections =
        new HashSet<ICohortConnection>();

    final protected ReentrantLock leader_listeners_lock = new ReentrantLock();
    final protected Set<ILeaderElectedListener> leader_listeners =
        new HashSet<ILeaderElectedListener>();
    
    /**
       Id of local cohort.
     */
    final public long local_cohort_id;

    final public int quorum_size;

    /**
       Debugging term: if true, then this node may try to become
       leader itself.  Otherwise, will never try to become leader on
       its own.
     */
    final protected boolean debug_can_be_leader;
    
    public CohortManager(
        Set<CohortInfo.CohortInfoPair> connection_info,
        ICohortConnectionFactory cohort_connection_factory,
        long local_cohort_id, int heartbeat_timeout_period_ms,
        int heartbeat_send_period_ms, boolean debug_can_be_leader)
    {
        log = new Log(local_cohort_id);
        
        this.debug_can_be_leader = debug_can_be_leader;
        
        for (CohortInfo.CohortInfoPair pair : connection_info)
        {
            ICohortConnection connection =
                cohort_connection_factory.construct(
                    pair.local_cohort_info,pair.remote_cohort_info);

            cohort_connections.add(connection);

            // subscribe as connection and message listeners
            connection.add_cohort_message_listener(this);
        }

        // added one because connection info does not include me.
        quorum_size = ((int)((connection_info.size() + 1)/2.)) + 1;
        
        this.local_cohort_id = local_cohort_id;
        this.heartbeat_timeout_period_ms = heartbeat_timeout_period_ms;
        this.heartbeat_send_period_ms = heartbeat_send_period_ms;
    }
    
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
        long local_cohort_id, int heartbeat_timeout_period_ms,
        int heartbeat_send_period_ms)
    {
        this(
            connection_info, cohort_connection_factory,
            local_cohort_id, heartbeat_timeout_period_ms,
            heartbeat_send_period_ms, true);
    }
    
    /**
       Actually start connections to other cohort nodes.
       Should be called before any other methods
     */
    public void start_manager()
    {
        for (ICohortConnection connection : cohort_connections)
            connection.start_service();
        start_elect_self_thread(0);
    }

    /**
       If node is not currently the leader, it will not add the
       requested entry.
       
       @returns the leader id.  returns null if there is currently no
       leader.
     */
    public Long add_entry_if_leader(byte[] entry_to_try_to_add)
    {
        state_lock.lock();
        try
        {
            if (state == ManagerState.LEADER)
                log.add_to_log(entry_to_try_to_add,view_number);

            return current_leader_id;
        }
        finally
        {
            state_lock.unlock();
        }
    }
    
    public void add_apply_entry_listener(
        IApplyEntryListener to_add)
    {
        log.add_apply_entry_listener(to_add);
    }

    public void remove_apply_entry_listener(
        IApplyEntryListener to_remove)
    {
        log.remove_apply_entry_listener(to_remove);
    }
    
    /**
       For debugging.
       
       @returns --- true if this cohort manager is leader; false
       otherwise.
     */
    public boolean is_leader()
    {
        state_lock.lock();
        try
        {
            return state == ManagerState.LEADER;
        }
        finally
        {
            state_lock.unlock();
        }
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

            // increment ballot so that know will be sending out
            // election requests for a new, unique ballot number.
            view_number += 1;

            // create a new election context, requiring a new set of
            // voter responses.
            election_context = new ElectionContext(local_cohort_id);
            notify_election_started_listeners(view_number);
            
            // ask all cohorts to vote for me as new leader.
            election_proposal.setNextProposedViewNumber(view_number);
            election_proposal.setNodeId(local_cohort_id);
            election_proposal.setLastLogIndex(last_log_index);
            election_proposal.setLastLogTerm(last_log_term);            
        }
        finally
        {
            state_lock.unlock();
        }

        // Debugging boolean: if we cannot be leader, then don't
        // nominate self to be leader.
        if (! debug_can_be_leader)
            return;
        
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


    /***************** ICohortMessageListener overrides ********/
    /**
       Receive append_entries command.

       Directly from Raft paper:
       
       1. Reply false if term < currentTerm
       2. Reply false if log doesn't contain an entry at prevLogIndex
       whose term matches prevLogTerm
       3. If an existing entry conflicts with a new one (same index
       but different terms), delete the existing entry and all that
       follow it
       4. Append any new entries not already in the log
       5. If leaderCommit > commitIndex, set commitIndex =
       min(leaderCommit, index of last new entry)
     */
    @Override
    public void append_entries(
        ICohortConnection cohort_connection,AppendEntries append_entries)
    {
        boolean success = false;
        long nonce = append_entries.getNonce();
        long remote_cohort_id = cohort_connection.remote_cohort_id();
        long received_view_number = append_entries.getViewNumber();
        long current_view_number = 0;

        state_lock.lock();
        try
        {
            check_become_follower(
                remote_cohort_id,received_view_number,true);

            current_view_number = view_number;
            if (current_view_number > received_view_number)
            {
                success = false;
                return;
            }
            else if(current_view_number == received_view_number)
            {
                if (heartbeat_listening_service != null)
                    heartbeat_listening_service.append_entries_message();
            }
            success = log.handle_append_entries(append_entries);
        }
        finally
        {
            state_lock.unlock();

            // generate and send response
            AppendEntriesResponse.Builder append_entries_response =
                AppendEntriesResponse.newBuilder();
            append_entries_response.setNonce(nonce);
            append_entries_response.setViewNumber(current_view_number);
            append_entries_response.setSuccess(success);

            CohortMessage.Builder cohort_message = CohortMessage.newBuilder();
            cohort_message.setAppendEntriesResponse(append_entries_response);
            cohort_connection.send_message(cohort_message);
        }
    }

    /**
       When receive an append entries response
     */
    @Override
    public void append_entries_response(
        ICohortConnection cohort_connection,
        AppendEntriesResponse append_entries_response)
    {
        AppendEntries.Builder to_send_back = null;
        long append_entries_resp_view_num =
            append_entries_response.getViewNumber();
        state_lock.lock();
        try
        {
            check_become_follower(
                UNKNOWN_LEADER_ID,append_entries_resp_view_num,false);
            
            // if message failed because other side was on future view
            // number, then we will no logner be leader and below will
            // be false. ignore message if we are no longer the leader
            if (state != ManagerState.LEADER)
                return;

            // if message failed because other side was on a previous
            // view number, ignore.
            if (append_entries_resp_view_num < view_number)
                return;

            to_send_back =
                leader_context.handle_append_entries_response(
                    append_entries_response, local_cohort_id,
                    view_number, cohort_connection.remote_cohort_id());
        }
        finally
        {
            state_lock.unlock();
        }

        
        // if other node was out of date, then need to actually
        // send an update to it.
        if (to_send_back == null)
            return;
        

        CohortMessage.Builder append_entries_update =
            CohortMessage.newBuilder();
        append_entries_update.setAppendEntries(to_send_back);
        cohort_connection.send_message(append_entries_update);
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
        long remote_cohort_id = cohort_connection.remote_cohort_id();

        state_lock.lock();
        try
        {
            boolean transitioned_to_follower =
                check_become_follower(
                    remote_cohort_id,proposed_view_number,false);
            
            if (proposed_view_number < view_number)
            {
                // wrong view number: do not vote for this candidate
                vote_granted = false;
            }
            else if (view_number == proposed_view_number)
            {
                // if same view number, only way that we vote for
                // other person is if we already voted for them (ie.,
                // we already voted for them or they're leader).
                if (state == ManagerState.FOLLOWER)
                {
                    if (current_leader_id == remote_cohort_id)
                        vote_granted = true;
                }
            }
            //// DEBUG: Should never get to this point because
            //// check_become_follower should already have updated
            //// view_number
            else // proposed view number > current view number
            {
                Util.force_assert("Unexpected else");
            }
            //// END DEBUG


            // Now check whether candidate's log is at least as
            // up-to-date as receiver's log.
            if (vote_granted)
            {
                vote_granted = false;
                
                // check if candidate's log is at least as up to date
                // as our log.
                if ((election_proposal.getLastLogIndex() >= last_log_index) &&
                    (election_proposal.getLastLogTerm() >= last_log_term))
                {
                    // vote for this candidate.
                    vote_granted = true;
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

        long proposed_view_number =
            election_proposal_resp.getProposedViewNumber();
        long remote_cohort_id = cohort_connection.remote_cohort_id();

        state_lock.lock();
        try
        {
            boolean transitioned_to_follower =
                check_become_follower(
                    remote_cohort_id,proposed_view_number,false);

            // if opposite node is farther ahead than we are, then we
            // cannot possibly process vote granted.
            if (transitioned_to_follower)
                return;

            // nothing to do.
            if (! vote_granted)
                return;

            
            // discard: had already transitioned into new state
            if (election_context == null)
                return;
            
            if (view_number != proposed_view_number)
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

                // start heartbeats
                heartbeat_sending_service =
                    new HeartbeatSendingService(
                        heartbeat_send_period_ms, cohort_connections,
                        this);
                heartbeat_sending_service.start();
                leader_context =
                    new LeaderContext(cohort_connections,log,view_number);

                current_leader_id = local_cohort_id;

                notify_leader_listeners();
            }
        }
        finally
        {
            state_lock.unlock();
        }
    }


    /******** IAppendEntriesSupplier overrides ******/
    /**
       Returns null if we are no longer leader.
     */
    @Override
    public AppendEntries.Builder construct(long remote_cohort_id)
    {
        state_lock.lock();
        try
        {
            if (state != ManagerState.LEADER)
                return null;
            return leader_context.produce_leader_append(
                view_number,local_cohort_id,remote_cohort_id);
        }
        finally
        {
            state_lock.unlock();
        }
    }

    /************ ILeaderDownListener overrides ********/
    @Override
    public void leader_down(long leader_id, long view_number_down_at)
    {
        state_lock.lock();
        try
        {
            if (heartbeat_listening_service != null)
                heartbeat_listening_service.stop_service();
            
            if (state != ManagerState.FOLLOWER)
                return;

            if (view_number != view_number_down_at)
                return;


            // transition into election state 
            current_leader_id = null;
            state = ManagerState.ELECTION;
            leader_context = null;
            heartbeat_sending_service = null;
            start_elect_self_thread(0);
        }
        finally
        {
            state_lock.unlock();
        }
    }

    /************* Allow subscribing and removing leader listeners */
    public void add_leader_elected_listener(
        ILeaderElectedListener leader_elected_listener)
    {
        leader_listeners_lock.lock();
        try
        {
            leader_listeners.add(leader_elected_listener);
        }
        finally
        {
            leader_listeners_lock.unlock();
        }
    }

    public void remove_leader_elected_listener(
        ILeaderElectedListener leader_elected_listener)
    {
        leader_listeners_lock.lock();
        try
        {
            leader_listeners.remove(leader_elected_listener);
        }
        finally
        {
            leader_listeners_lock.unlock();
        }
    }

    /**
       Gets called whenever we get a new leader.  Notifies all
       listeners of new leader.
     */
    protected void notify_leader_listeners()
    {
        leader_listeners_lock.lock();
        try
        {
            for (ILeaderElectedListener leader_elected_listener :
                     leader_listeners)
            {
                leader_elected_listener.leader_elected(
                    view_number, current_leader_id, local_cohort_id);
            }
        }
        finally
        {
            leader_listeners_lock.unlock();
        }
    }

    
    /**
       Called while holding state_lock.

       @param new_leader_id --- Can also be -1

       @param received_view_number

       @param called_from_append_entries --- true if called from
       append_entries.

       @returns true if transitioned to follower.  false if did not.
       
     */
    protected boolean check_become_follower(
        long new_leader_id,long received_view_number,
        boolean called_from_append_entries)
    {
        if (view_number > received_view_number)
            return false;

        boolean transition = false;
        // we also notify nodes in case that we previously did not
        // know leader id associated with this view number.
        boolean notify = false;
        if ((view_number == received_view_number) &&
            called_from_append_entries)
        {
            if (state != ManagerState.FOLLOWER)
                transition = true;
            else if (current_leader_id == UNKNOWN_LEADER_ID)
            {
                current_leader_id = new_leader_id;
                notify = true;
            }
        }

        if (view_number < received_view_number)
            transition = true;
            

        if (transition)
        {
            view_number = received_view_number;
            current_leader_id = new_leader_id;
            if (current_leader_id != UNKNOWN_LEADER_ID)
                notify = true;
            
            if (heartbeat_listening_service != null)
                heartbeat_listening_service.stop_service();

            election_context = null;
            leader_context = null;
            heartbeat_sending_service = null;
        
            state = ManagerState.FOLLOWER;
        
            heartbeat_listening_service =
                new HeartbeatListeningService(
                    heartbeat_timeout_period_ms,this,
                    new_leader_id,received_view_number);
            heartbeat_listening_service.start_service();
        }

        if (notify)
            notify_leader_listeners();

        return transition;
    }
    
    /**
       Gets called wehenver we begin a new election on this node.
     */
    protected void notify_election_started_listeners(long election_view_number)
    {
        leader_listeners_lock.lock();
        try
        {
            for (ILeaderElectedListener leader_elected_listener :
                     leader_listeners)
            {
                leader_elected_listener.election_started(
                    election_view_number,local_cohort_id);
            }
        }
        finally
        {
            leader_listeners_lock.unlock();
        }
    }
}