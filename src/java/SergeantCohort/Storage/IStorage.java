package SergeantCohort.Storage;

import SergeantCohort.LogEntry;

public interface IStorage
{
    /**
       Borrowed from logcabin reference implementation.  When this is
       called, we delete all log indices *prior* to this index.  (Note
       1: Cannot be undone.  Note 2: does not delete
       index_to_truncate_before itself.)
     */
    public void truncate_prefix(long index_to_truncate_before);
    /**
       Borrowed from logcabin reference implementation.  When this is
       called, we delete all log indices *after* thisindex.  (Note 1:
       Cannot be undone.  Note 2: does not delete
       index_to_truncate_after.)
     */
    public void truncate_suffix(long index_to_truncate_after);
    
    public long log_size();

    public void append_entry(byte[] contents, long term);
    public LogEntry get_entry(long index_to_get_from);
    public void set_entry(long index_to_set, LogEntry entry);
    public void remove_entry(long index_to_rm);
}