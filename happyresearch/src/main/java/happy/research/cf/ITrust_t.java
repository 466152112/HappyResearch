package happy.research.cf;

import happy.coding.io.FileIO;

import java.util.Map;

public class ITrust_t extends Thread_t
{

	private String	trustMetric;
	private String	trustDir;

	public ITrust_t(int id)
	{
		super(id);
		trustMetric = params.readParam("itrust.probe.method");
		trustDir = FileIO.makeDirectory(Dataset.DIRECTORY,
				trustMetric + "-" + params.TRAIN_SET.substring(0, params.TRAIN_SET.lastIndexOf(".base")));
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		try
		{
			String u = testRating.getUserId();
			String file = trustDir + u + ".txt";
			if (!FileIO.exist(file)) return null;

			/* read trust information from trust file */
			Map<String, Double> tns = FileIO.readAsIDMap(file, " ");
			if (tns == null) return null;
			else return new Map[] { tns };

		} catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;

	}
}
