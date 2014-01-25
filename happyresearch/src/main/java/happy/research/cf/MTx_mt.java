package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.research.utils.MoleTrust;

import java.io.File;
import java.util.Map;

public class MTx_mt extends DefaultCF_mt
{

	public MTx_mt() throws Exception
	{
		methodId = "MT" + params.TRUST_PROPERGATION_LENGTH;
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		probeMTTnScores();

		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new MTx_t(i));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}

	protected void probeMTTnScores() throws Exception
	{
		int horizon = params.TRUST_PROPERGATION_LENGTH;
		Logs.debug("Building MT{} Data to: {} ...", horizon, trustDirPath);

		for (String user : testUserRatingsMap.keySet())
		{
			File userFile = new File(trustDirPath + user + ".txt");
			if (userFile.exists()) continue;

			Map<String, Double> trustScores = MoleTrust.runAlgorithm(userTNsMap, user, horizon);
			if (trustScores.size() > 0) FileIO.writeMap(userFile.getPath(), trustScores);
		}
		Logs.debug("Done!");
	}

}
