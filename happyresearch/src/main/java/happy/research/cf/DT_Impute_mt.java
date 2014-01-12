package happy.research.cf;

import java.util.ArrayList;

/**
 * Note: Using distrust information to impute the missing values of the trusted neighbours, in the user-item matrix, and
 * then combine with trust and CF to predict item's rating
 * 
 * @author guoguibing
 * 
 */

public class DT_Impute_mt extends DefaultCF_mt
{

	public DT_Impute_mt() throws Exception
	{
		methodId = "DT_Impute";
		makeDirPaths();

	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		users = DatasetUtils.splitCollection(new ArrayList<>(testUserRatingsMap.keySet()), params.RUNTIME_THREADS);
		for (int i = 0; i < users.length; i++)
		{
			threads[i] = new Thread(new DT_Impute_t1(users[i], i + 1));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new DT_Impute_t(i));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}

}
