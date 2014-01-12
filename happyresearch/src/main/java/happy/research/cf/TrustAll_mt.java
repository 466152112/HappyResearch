package happy.research.cf;

public class TrustAll_mt extends DefaultCF_mt
{
	public TrustAll_mt()
	{
		methodId = "TrustAll";
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new TrustAll_t(i));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}
}
