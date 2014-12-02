package SergeantCohort;

public interface ILastViewNumberSupplier
{
    /**
       Returns the term that this manager is currently in or trying to
       get out of.
     */
    public long last_view_number();
}
