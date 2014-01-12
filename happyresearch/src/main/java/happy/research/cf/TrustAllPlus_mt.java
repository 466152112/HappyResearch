package happy.research.cf;


/**
 * Predict using TrustAll method. For the items without any ratings, use GlobalAveRating method
 * 
 * @author guoguibing
 * 
 */
public class TrustAllPlus_mt extends DefaultCF_mt
{
	public TrustAllPlus_mt()
	{
		methodId = "TrustAllPlus";
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		double averageRating = RatingUtils.mean(userRatingsMap);

		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new TrustAllPlus_t(i, averageRating));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}
}
