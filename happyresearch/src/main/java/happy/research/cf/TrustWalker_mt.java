package happy.research.cf;

public class TrustWalker_mt extends DefaultCF_mt
{

	public TrustWalker_mt()
	{
		methodId = "TrustWalker";
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new TrustWalker_t(i));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}

}
