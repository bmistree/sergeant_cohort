package SergeantCohort.Storage;

import java.util.List;
import java.util.ArrayList;

import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.google.protobuf.ByteString;

import SergeantCohort.Util;
import SergeantCohort.LogEntry;

import ProtocolLibs.FSLogEntryProto.FSLogEntry;

public class InMemoryList implements IStorage
{
    private final List<LogEntry> log = new ArrayList<LogEntry>();
    private final long local_cohort_id;
    private final static int MAX_ENTRIES_BEFORE_SNAPSHOT = 10;
    
    
    /**
       When we truncate our log from its prefix, the indices in log
       (the ArrayList above) do not match the indices of the actual
       log.

       Eg.,

           log = [1,2,3]
           log.get(2) // == 3
           log.prefix_truncate(1) // log == [2,3]
           log.get(2) // should == 3.
           log.size() // should be 3
           
       Therefore, we're keeping a mapping between the last index that
       we truncated from so that we can index into log.

       Eg.,
           log.get(2)
       turns into
           log.get(2 - base_offset_index)
     */
    private long base_offset_index = 0;

    private BufferedOutputStream output_stream = null;

    public InMemoryList(long local_cohort_id)
    {
        this.local_cohort_id = local_cohort_id;

        try
        {
            output_stream = new BufferedOutputStream(
                new FileOutputStream("log_" + local_cohort_id + ".bin"));
        }
        catch (FileNotFoundException ex)
        {
            ex.printStackTrace();
            Util.force_assert("File exception in memory list");
        }
    }
    
    @Override
    public synchronized void check_write_stably(long last_committed_index)
    {
        // do not have enough outstanding entries to justify flushing
        // to disk; return.
        if ((last_committed_index - base_offset_index) < MAX_ENTRIES_BEFORE_SNAPSHOT)
            return;

        // write indices [base_offset_index,last_committed_index) to fs.
        for (long i = base_offset_index; i < last_committed_index; ++i)
        {
            LogEntry log_entry = get_entry(i);
            FSLogEntry.Builder fs_log_entry= FSLogEntry.newBuilder();
            fs_log_entry.setIndex(i);
            fs_log_entry.setData(ByteString.copyFrom(log_entry.contents));
            try
            {
                fs_log_entry.build().writeDelimitedTo(output_stream);
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
                Util.force_assert(
                    "File exception in memory list when writing.");
            }
        }
        truncate_prefix(last_committed_index);
    }

    /**
       Borrowed from logcabin reference implementation.  When this is
       called, we delete all log indices *prior* to this index.  (Note
       1: Cannot be undone.  Note 2: does not delete
       index_to_truncate_before itself.)

       Currently exposed for testing (and that's the only reason).
     */
    public synchronized void truncate_prefix(long index_to_truncate_before)
    {
        long list_mapped_index = list_mapped_index(index_to_truncate_before);
        long num_removes = list_mapped_index;
        for (int i = 0; i < num_removes; ++i)
            log.remove(0);
        base_offset_index = index_to_truncate_before;
    }


    private synchronized long list_mapped_index (long external_index)
    {
        return external_index - base_offset_index;
    }
    

    @Override
    public synchronized long log_size()
    {
        return ((long)log.size()) + base_offset_index;
    }

    @Override
    public synchronized void append_entry(byte[] contents, long term)
    {
        append_entry(new LogEntry(contents,term));
    }

    @Override
    public synchronized void append_entry(LogEntry entry)
    {
        log.add(entry);
    }
    
    @Override
    public synchronized LogEntry get_entry(long index_to_get_from)
    {
        long list_mapped_index = list_mapped_index(index_to_get_from);
        return log.get((int)list_mapped_index);
    }

    @Override
    public synchronized void set_entry(long index_to_set, LogEntry entry)
    {
        long list_mapped_index = list_mapped_index(index_to_set);
        log.set((int)list_mapped_index,entry);
    }
    
    @Override
    public synchronized void set_entry(
        long index_to_set, byte[] contents, long term)
    {
        // note: not remapping set_entry here because happens in other
        // set_entry call.
        LogEntry entry = new LogEntry(contents,term);
        set_entry(index_to_set,entry);
    }
    
    @Override
    public synchronized void remove_entry(long index_to_rm)
    {
        long list_mapped_index = list_mapped_index(index_to_rm);
        log.remove((int)list_mapped_index);
    }
}