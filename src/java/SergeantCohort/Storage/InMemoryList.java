package SergeantCohort.Storage;

import java.util.List;
import java.util.ArrayList;

import SergeantCohort.Util;
import SergeantCohort.LogEntry;

public class InMemoryList implements IStorage
{
    protected final List<LogEntry> log = new ArrayList<LogEntry>();
    
    /**
       @see IStorage.truncate_suffix
     */
    @Override
    public void truncate_prefix(long index_to_truncate_before)
    {
        // FIXME: truncate_prefix should be implemented
        Util.force_assert("FIXME: truncate_prefix is still a stub method");
    }
    
    /**
       @see IStorage.truncate_suffix
     */
    @Override
    public void truncate_suffix(long index_to_truncate_after)
    {
        // FIXME: truncate_suffix should be implemented
        Util.force_assert("FIXME: truncate_suffix is still a stub method");
    }
    

    @Override
    public long log_size()
    {
        return (long)log.size();
    }

    @Override
    public void append_entry(byte[] contents, long term)
    {
        log.add(new LogEntry(contents,term));
    }

    @Override
    public LogEntry get_entry(long index_to_get_from)
    {
        return log.get((int)index_to_get_from);
    }

    @Override
    public void set_entry(long index_to_set, LogEntry entry)
    {
        log.set((int)index_to_set,entry);
    }

    @Override
    public void remove_entry(long index_to_rm)
    {
        log.remove((int)index_to_rm);
    }
}