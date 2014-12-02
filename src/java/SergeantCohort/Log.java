package SergeantCohort;

import java.util.List;
import java.util.ArrayList;

import com.google.protobuf.ByteString;

import ProtocolLibs.AppendEntriesProto.AppendEntries;

public class Log
{
    protected final List<LogEntry> log = new ArrayList<LogEntry>();
    /**
       The last known externalized index in the log.
     */
    protected long commit_index = -1;

    /**
       Increment each time that we create an AppendEntries.Builder in
       leader_append.  That way, we can match append entries requests
       to their returned responses.
     */
    protected long nonce_generator = 0;

    public Log()
    {
        // add an empty log entry so that don't have to special-case
        // having an empty log.
        log.add(new LogEntry(null,0));
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