package SergeantCohort;

import java.util.List;
import java.util.ArrayList;

import com.google.protobuf.ByteString;

import ProtocolLibs.AppendEntriesProto.AppendEntries;

public class Log
{
    protected final List<LogEntry> log = new ArrayList<LogEntry>();
    /**
       The last known externalized index in the log.  Starting at zero
       here because all logs start with a dummy entry (that way don't
       have to special-case having empty log).
     */
    protected long commit_index = 0;

    /**
       Increment each time that we create an AppendEntries.Builder in
       leader_append.  That way, we can match append entries requests
       to their returned responses.
     */
    protected long nonce_generator = 0;

    public Log()
    {
        // add an empty log entry so that don't have to special-case
        // having an empty log.  Note that commit_index, etc starts
        // appropriately.
        log.add(new LogEntry(null,0));
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
        long msg_term = append_entries.getViewNumber();
        long leader_commit_index = append_entries.getLeaderCommitIndex();
        
        
        // Reply false if log doesn't contain an entry at prevLogIndex
        // whose term matches prevLogTerm
        long msg_prev_log_index = append_entries.getPrevLogIndex();
        if (msg_prev_log_index > (log.size() - 1))
            return false;


        // If an existing entry conflicts with a new one (same index
        // but different terms), delete the existing entry and all that
        // follow it
        int insertion_index = ((int)msg_prev_log_index) + 1;
        // true if a message conflicted with version 
        boolean conflict = false;
        for (ByteString entry : append_entries.getEntriesList())
        {
            byte[] entry_as_byte_array = entry.toByteArray();
            LogEntry new_log_entry = new LogEntry(entry_as_byte_array,msg_term);
            
            if (insertion_index >= log.size())
                log.add(new_log_entry);
            else
            {
                LogEntry prev_log_entry = log.get(insertion_index);
                if (prev_log_entry.term != msg_term)
                    conflict = true;
                log.set(insertion_index,new_log_entry);
            }
            ++insertion_index;
        }

        // if there was a conflict, then we need to delete all
        // subsequent entries that had been stored in log at insertion
        // index and later.
        int number_of_tail_removes = log.size() + 1 - insertion_index;
        for (int i = 0; i < number_of_tail_removes; ++i)
            log.remove(log.size() -1 );
        
        // If leaderCommit > commitIndex, set commitIndex =
        // min(leaderCommit, index of last new entry)
        if (leader_commit_index > commit_index)
            commit_index = Math.min(leader_commit_index, log.size() -1);
        
        return true;
    }
    
    /**
       Called by leader to generate a new set of entries
     */
    public synchronized AppendEntries.Builder leader_append(
        List<byte[]> new_entries,long view_number,long leader_cohort_id)
    {
        nonce_generator += 1;

        AppendEntries.Builder to_return = AppendEntries.newBuilder();
        to_return.setNonce(nonce_generator);
        to_return.setViewNumber(view_number);
        to_return.setLeaderCohortId(leader_cohort_id);
        
        for (byte[] blob : new_entries)
        {
            ByteString byte_string = ByteString.copyFrom(blob);
            to_return.addEntries(byte_string);
        }

        // note: because added an element to log in constructor, don't
        // have to deal with edge case of empty log.
        long prev_index = log.size() -1;
        to_return.setPrevLogIndex(prev_index);
        to_return.setPrevLogTerm(log.get((int)prev_index).term);
        to_return.setLeaderCommitIndex(commit_index);
        
        return to_return;
    }
    
    protected class LogEntry
    {
        protected final byte[] contents;
        protected final long term;

        public LogEntry(byte[] contents, long term)
        {
            this.contents = contents;
            this.term = term;
        }
    }
    
}