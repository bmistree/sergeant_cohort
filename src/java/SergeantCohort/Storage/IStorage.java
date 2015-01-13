package SergeantCohort.Storage;

import SergeantCohort.LogEntry;

public interface IStorage
{
    public long log_size();
    public void check_write_stably(long last_committed_index);

    public void append_entry(byte[] contents, long term);
    public void append_entry(LogEntry to_append);
    public LogEntry get_entry(long index_to_get_from);
    public void set_entry(long index_to_set, LogEntry entry);
    public void set_entry(long index_to_set, byte[]contents, long term);
    public void remove_entry(long index_to_rm);
}