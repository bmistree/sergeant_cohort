package SergeantCohort.Storage;

import java.util.List;
import java.util.ArrayList;

import SergeantCohort.Util;
import SergeantCohort.LogEntry;

public class InMemoryList implements IStorage
{
    private final List<LogEntry> log = new ArrayList<LogEntry>();

    /**
       When we truncate our log from its prefix, the indices in log
       (the ArrayList above) do not match the indices of the actual
       log.

       Eg.,

           log = [1,2,3]
           log.get(2) // == 2
           log.truncate(2) // log == [2,3]
           log.get(2) // should == 2.
           
       Therefore, we're keeping a mapping between the last index that
       we truncated from so that we can index into log.

       Eg.,
           log.get(2)
       turns into
           log.get(2 - base_offset_index)
     */
    private long base_offset_index = 0;
    
    /**
       @see IStorage.truncate_suffix
     */
    @Override
    public synchronized void truncate_prefix(long index_to_truncate_before)
    {
        long list_mapped_index = list_mapped_index(index_to_truncate_before);
        for (int i = 0; i < (list_mapped_index - 1); ++i)
            log.remove(0);
        base_offset_index = index_to_truncate_before;
    }


    private synchronized long list_mapped_index (long external_index)
    {
        return external_index - base_offset_index;
    }
    
    /**
       @see IStorage.truncate_suffix
     */
    @Override
    public synchronized void truncate_suffix(long index_to_truncate_after)
    {
        long num_removes = log_size() - index_to_truncate_after - 1;
        long list_mapped_index = list_mapped_index(index_to_truncate_after);
        for (int i = 0; i < num_removes; ++i)
            log.remove((int)(list_mapped_index + 1));
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