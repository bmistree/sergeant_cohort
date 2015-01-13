package SergeantCohort;

import java.util.Set;
import java.util.HashSet;

import java.util.List;
import java.util.ArrayList;

import com.google.protobuf.ByteString;

import SergeantCohort.Storage.IStorage;
import SergeantCohort.Storage.InMemoryList;

import ProtocolLibs.AppendEntriesProto.AppendEntries;


public class Log
{
    protected final IStorage storage;
    
    /**
       The last known externalized index in the log.  Starting at zero
       here because all logs start with a dummy entry (that way don't
       have to special-case having empty log).
     */
    private long commit_index = 0;

    /**
       Increment each time that we create an AppendEntries.Builder in
       leader_append.  That way, we can match append entries requests
       to their returned responses.
     */
    private final Util.LongNonceGenerator nonce_generator =
        new Util.LongNonceGenerator();

    /**
       Index of highest log entry applied to state machine.
     */
    protected long last_applied = 0;

    protected Set<IApplyEntryListener> apply_entry_listener_set =
        new HashSet<IApplyEntryListener>();

    protected long local_cohort_id;
    
    public Log(long local_cohort_id)
    {
        this.local_cohort_id = local_cohort_id;
        storage = new InMemoryList(local_cohort_id);
        
        // add an empty log entry so that don't have to special-case
        // having an empty log.  Note that commit_index, etc starts
        // appropriately.
        storage.append_entry(new byte [0],0);
    }

    /**
       Can only be called from leader context.  Guaranteed safe access
       because call that makes it is eventually holding state_lock for
       cohort.
     */
    public synchronized long get_commit_index()
    {
        return commit_index;
    }
    /**
       Generally set while in leader mode.
     */
    public synchronized void set_commit_index(long new_index)
    {
        commit_index = new_index;
        try_apply();
        storage.check_write_stably(commit_index);
    }

    /**
       Should only be called by leader.
     */
    public synchronized void add_to_log(byte[] contents,long term)
    {
        storage.append_entry(contents,term);
    }
    
    public synchronized int size()
    {
        return (int)storage.log_size();
    }

    public synchronized void add_apply_entry_listener(
        IApplyEntryListener to_add)
    {
        apply_entry_listener_set.add(to_add);
    }

    public synchronized void remove_apply_entry_listener(
        IApplyEntryListener to_remove)
    {
        apply_entry_listener_set.remove(to_remove);
    }

    
    /**
       2. Reply false if log doesn't contain an entry at prevLogIndex
       whose term matches prevLogTerm
       3. If an existing entry conflicts with a new one (same index
       but different terms), delete the existing entry and all that
       follow it
       4. Append any new entries not already in the log
       5. If leaderCommit > commitIndex, set commitIndex =
       min(leaderCommit, index of last new entry)
     */
    public synchronized boolean handle_append_entries(
        AppendEntries append_entries)
    {
        long nonce = append_entries.getNonce();
        long msg_term = append_entries.getViewNumber();
        long leader_commit_index = append_entries.getLeaderCommitIndex();
        
        // Reply false if log doesn't contain an entry at prevLogIndex
        // whose term matches prevLogTerm
        long msg_prev_log_index = append_entries.getPrevLogIndex();
        if (msg_prev_log_index > (storage.log_size() - 1))
        {
            return false;
        }
        
        // If an existing entry conflicts with a new one (same index
        // but different terms), delete the existing entry and all that
        // follow it
        long insertion_index = msg_prev_log_index + 1;
        // true if a message conflicted with version 
        boolean conflict = false;
        for (ByteString entry : append_entries.getEntriesList())
        {
            byte[] entry_as_byte_array = entry.toByteArray();
            
            if (insertion_index >= storage.log_size())
                storage.append_entry(entry_as_byte_array,msg_term);
            else
            {
                LogEntry prev_log_entry = storage.get_entry(insertion_index);
                if (prev_log_entry.term != msg_term)
                    conflict = true;
                storage.set_entry(insertion_index,entry_as_byte_array,msg_term);
            }
            ++insertion_index;
        }

        // if there was a conflict, then we need to delete all
        // subsequent entries that had been stored in log at insertion
        // index and later.
        long number_of_tail_removes = storage.log_size() - insertion_index;
        for (long i = 0; i < number_of_tail_removes; ++i)
            storage.remove_entry(storage.log_size() -1 );
        
        // If leaderCommit > commitIndex, set commitIndex =
        // min(leaderCommit, index of last new entry)
        if (leader_commit_index > commit_index)
            commit_index = Math.min(leader_commit_index, storage.log_size() -1);

        try_apply();
        storage.check_write_stably(commit_index);
        return true;
    }
    
    protected void try_apply()
    {
        while(commit_index > last_applied)
        {
            ++last_applied;
            for (IApplyEntryListener listener : apply_entry_listener_set)
                listener.apply_entry(storage.get_entry(last_applied).contents);
        }
    }
    
    /**
       Called by leader to generate a new set of entries
       
       @param index_to_send_from --- If -1, then ignore.  Otherwise,
       send all entries from index_to_send_from onwards.
     */
    public synchronized AppendEntries.Builder leader_append(
        long view_number,long leader_cohort_id,
        long index_to_send_from)
    {
        List<byte[]> new_entries = new ArrayList<byte[]>();
        long prev_index = storage.log_size() -1;
        if (index_to_send_from != -1)
        {
            prev_index = Math.min(
                index_to_send_from -1,storage.log_size() - 1);
            // FIXME: using int here instead of long.
            for (long i = index_to_send_from; i < storage.log_size(); ++i)
            {
                LogEntry entry  = storage.get_entry(i);
                new_entries.add(entry.contents);
            }
        }

        AppendEntries.Builder to_return = AppendEntries.newBuilder();
        to_return.setNonce(nonce_generator.increment_and_get());
        to_return.setViewNumber(view_number);
        to_return.setLeaderCohortId(leader_cohort_id);
        
        for (byte[] blob : new_entries)
        {
            ByteString byte_string = ByteString.copyFrom(blob);
            to_return.addEntries(byte_string);
        }

        // note: because added an element to log in constructor, don't
        // have to deal with edge case of empty log.
        to_return.setPrevLogIndex(prev_index);
        to_return.setPrevLogTerm(storage.get_entry(prev_index).term);
        to_return.setLeaderCommitIndex(commit_index);
        return to_return;
    }

    /**
       @returns null if index does not exist in log.
     */
    public synchronized Long get_term_at_index(long index)
    {
        if (index >= storage.log_size())
            return null;

        LogEntry entry = storage.get_entry(index);
        return entry.term;
    }
}