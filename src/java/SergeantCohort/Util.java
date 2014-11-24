package SergeantCohort;


public class Util
{
    public static void force_assert(String message)
    {
        System.out.println(message);
        assert(false);
        System.exit(-1);
    }
}