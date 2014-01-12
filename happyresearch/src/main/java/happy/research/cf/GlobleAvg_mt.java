package happy.research.cf;


/**
 * Use the collection-wide average item rating across all users and all items
 * 
 * @author guoguibing
 * 
 */
public class GlobleAvg_mt extends DefaultCF_mt
{

	public GlobleAvg_mt()
	{
		methodId = "Global_Average_Rating";
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		double averageRating = RatingUtils.mean(userRatingsMap);

		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new GlobalAve_t(i, averageRating));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}

}
