package happy.research.cf;

import happy.coding.io.FileIO;
import happy.research.utils.TidalTrust;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * TidalTrust algorithm
 * 
 * @author guoguibing
 */
public class TTx_mt extends DefaultCF_mt
{
	protected Map<String, Map<String, Double>>	userTrusteesMap		= null;
	protected Map<String, Map<String, Double>>	userTrustorsMap		= null;
	protected Map<String, List<TrustRating>>	userTrustRatingsMap	= null;

	public TTx_mt() throws Exception
	{
		methodId = "TT" + params.TRUST_PROPERGATION_LENGTH;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected void load_trusts() throws Exception
	{
		if (userTNsMap == null)
		{
			switch (Dataset.dataset)
			{
				case MOVIELENS:
				case JESTER:
				case NETFLIX:
					break;
				default:
					String trustSet = Dataset.DIRECTORY + Dataset.TRUST_SET;
					logger.debug("Loading trust data ...");
					Map[] data = DatasetUtils.loadTrustSet2(trustSet);
					userTrusteesMap = data[0];
					userTrustorsMap = data[1];
					userTrustRatingsMap = data[2];
					logger.debug("Done!");
					break;
			}

		}
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		probeTTTnScores();

		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new TTx_t(i));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}

	protected void probeTTTnScores() throws Exception
	{
		int horizon = params.TRUST_PROPERGATION_LENGTH;
		FileIO.makeDirectory(trustDirPath);
		logger.debug("Building TT{} Data to: {}", horizon, trustDirPath);

		for (String user : testUserRatingsMap.keySet())
		{
			File file = new File(trustDirPath + user + ".txt");
			if (file.exists()) continue;

			Map<String, Double> trustScores = TidalTrust.runAlgorithm(userTrusteesMap, userTrustorsMap,
					userTrustRatingsMap, user, horizon);
			if (trustScores != null && trustScores.size() > 0) FileIO.writeMap(file.getPath(), trustScores);
		}
		logger.debug("Done!");
	}

}
