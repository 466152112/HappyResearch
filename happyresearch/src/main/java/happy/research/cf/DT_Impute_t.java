package happy.research.cf;

import happy.coding.io.FileIO;

import java.util.Map;

/**
 * 
 * @author guoguibing
 * 
 */
public class DT_Impute_t extends Thread_t
{

	public DT_Impute_t(int id)
	{
		super(id);
	}

	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		return useTrustRatings(testRating);
	}

	@Override
	protected double predictMissingRating(Rating r)
	{
		String user = r.getUserId();
		String item = r.getItemId();

		Map<String, String> ratings = null;
		try
		{
			ratings = FileIO.readAsMap(trustDirPath + user + ".txt");
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		double rating = 0.0;
		if (ratings != null && ratings.containsKey(item + ""))
		{
			rating = Double.parseDouble(ratings.get(item + ""));
		}

		return rating;
	}

}
