package SergeantCohort;

import ProtocolLibs.AppendEntriesProto.AppendEntries;

public interface IAppendEntriesSupplier
{
    /**
       @returns {null} if not still leader
     */
    public AppendEntries.Builder construct();
}