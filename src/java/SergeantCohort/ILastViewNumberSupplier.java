package SergeantCohort;

public interface ILastViewNumberSupplier
{
    /**
       Returns the last stably agreed on view number (or 0 if have
       never agreed on a view number)
     */
    public int last_view_number();
}
