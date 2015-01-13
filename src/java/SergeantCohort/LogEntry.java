package SergeantCohort;

public class LogEntry
{
    public final byte[] contents;
    public final long term;

    public LogEntry(byte[] contents, long term)
    {
        this.contents = contents;
        this.term = term;
    }
}