package SergeantCohort;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import ProtocolLibs.AppendEntriesProto.AppendEntries;
import ProtocolLibs.AppendEntriesResponseProto.AppendEntriesResponse;

import SergeantCohort.CohortConnection.ICohortConnection;

public class LeaderContext
{
    /**
       Keys are cohort ids, values are next indices to send to node.
       
       For each server, the next index to send to that server.
       Initialized as last log index + 1.
     */
    final protected Map<Long,Long> next_index_map = new HashMap<Long,Long>();
    /**
       Keys are cohort ids, values are index of highest log entry
       known to be replicated on node.  Starts at 0.
     */
    final protected Map<Long,Long> match_index_map = new HashMap<Long,Long>();

    final protected long leader_term;
    
    final protected Log log;
    
    /**
       Keys are nonces, values are AppendEntries messages associated
       with nonce.
     */
    // FIXME: check here if this is memory leak.
    final protected Map<Long,AppendEntries> unacked_append_messages=
        new HashMap<Long,AppendEntries>();

    public LeaderContext(
        Set<ICohortConnection> cohort_connections, Log log, long leader_term)
    {
        this.log = log;
        this.leader_term = leader_term;
        long current_log_size = this.log.size();

        for (ICohortConnection connection : cohort_connections)
        {
            long remote_cohort_id = connection.remote_cohort_id();
            
            // initialized to last log index + 1
            next_index_map.put(remote_cohort_id,current_log_size + 1);

            // initalized to zero
            match_index_map.put(remote_cohort_id, 0L);
        }
    }

    public synchronized AppendEntries.Builder produce_leader_append(
        long view_number, long local_cohort_id,long to_send_to_cohort_id)
    {
        long index_to_send_from =
            next_index_map.get(to_send_to_cohort_id);

        if (index_to_send_from == 0)
        {
            System.out.println(
                "\n\nIndex to send from is zero for id: " +
                local_cohort_id + "\n\n");
        }
        
        AppendEntries.Builder to_return = 
            log.leader_append(view_number,local_cohort_id,index_to_send_from);
        
        about_to_send_append_entries(to_return);
        return to_return;
    }
    
    
    /**
       An append_entries message that this server is about to send.
     */
    protected void about_to_send_append_entries(
        AppendEntries.Builder append_entries)
    {
        unacked_append_messages.put(
            append_entries.getNonce(),append_entries.build());
    }

    /**
       We received an append entries response to our last
       append_entries message.  Now we should process it.

       @returns null if we don't need to send a new append entries
       with smaller index and retry.
     */
    public synchronized AppendEntries.Builder handle_append_entries_response(
        AppendEntriesResponse append_entries_response,
        long local_cohort_id,long view_number, long remote_cohort_id)
    {
        AppendEntries in_response_to = unacked_append_messages.remove(
            append_entries_response.getNonce());

        //// DEBUG
        if (in_response_to == null)
            Util.force_assert("Received a reply for an unknown message.");
        //// END DEBUG

        if (append_entries_response.getSuccess())
        {
            // FIXME: I'm pretty sure that we can, but would be good
            // to double check if we can get away with setting instead
            // of min.  Not true: because we're sending the response
            // outside of state lock.  Should fix.
            
            // the other side has logged all the entries we asked it
            // to using the append entries message in_response_to.  We
            // can now set the other side's match index (the index of
            // the highest entry known to be replicated on that
            // server) to be the prev_log_index field of the
            // append_entries message we sent, plus the number of
            // entries we sent.
            long new_match_index =
                in_response_to.getPrevLogIndex() +
                ((long)in_response_to.getEntriesList().size());

            match_index_map.put(remote_cohort_id,new_match_index);

            // update next index
            next_index_map.put(remote_cohort_id,new_match_index + 1);
                        
            // check whether we should externalize values and update
            // commit index.
            try_update_commit_index();
            
            return null;
        }

        // failed, decrement next index and try to retransmit
        long prev_next_index = next_index_map.get(remote_cohort_id);
        long new_next_index = prev_next_index -1;
        if (new_next_index == 0)
        {
            Util.force_assert(
                "\n\n Setting next index to zero for local cohort " +
                local_cohort_id + " sending to remote cohort id " +
                remote_cohort_id + " on view number " + view_number + 
                "\n\n");
        }
        next_index_map.put(remote_cohort_id,new_next_index);
        
        return produce_leader_append(
            view_number, local_cohort_id,remote_cohort_id);
    }

    /**
       Check if we have any new values to externalize and fire
       listeners if we do.
       
       If there exists an N such that N > commitIndex, a majority of
       matchIndex[i] ≥ N, and log[N].term == currentTerm: set
       commitIndex = N
     */
    protected void try_update_commit_index()
    {
        while(true)
        {
            long next_commit_index_to_try = log.get_commit_index() + 1;
            if (!can_update_commit_index(next_commit_index_to_try))
                return;

            log.set_commit_index(next_commit_index_to_try);
        }
    }

    /**
       Can update commit_index if a majority of match_indices are >=
       index_to_check and log[index_to_check].term == current_term;
     */
    protected boolean can_update_commit_index(long index_to_check)
    {
        // First, count number of cohorts whose match indices are >=
        // index_to_check.
        long num_cohorts_greater_than_equal = 0;
        for (Long key : match_index_map.keySet())
        {
            Long match_index = match_index_map.get(key);
            if (match_index >= index_to_check)
                ++num_cohorts_greater_than_equal;
        }

        int quorum_size = ((int)((match_index_map.size() + 1)/2.)) + 1;
        if ((1+num_cohorts_greater_than_equal) < quorum_size)
            return false;

        Long term_at_index = log.get_term_at_index(index_to_check);
        if (term_at_index == null)
            return false;

        if (! term_at_index.equals(leader_term))
            return false;
        
        return true;
    }
}