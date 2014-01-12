package happy.research.cf;

import java.util.Map;
import java.util.Map.Entry;

public class SlopeOne_t extends Thread_t
{
	/* item-item deviation matrix*/
	protected Map<String, Map<String, Double>>	devMatrix;
	/* item-item frequency matrix*/
	protected Map<String, Map<String, Integer>>	freqMatrix;

	public SlopeOne_t(int id, Map<String, Map<String, Double>> _diffMatrix,
			Map<String, Map<String, Integer>> _freqMatrix)
	{
		super(id);
		devMatrix = _diffMatrix;
		freqMatrix = _freqMatrix;
	}

	/**
	 * predict a rating for {@code testItem}
	 * 
	 * @param itemRatings test user's ratings
	 * @param testItem test item
	 * @param weighted whether use weighted or simple slope one 
	 * 
	 * @return prediction value
	 */
	public double predict(Map<String, Rating> itemRatings, String testItem, boolean weighted)
	{
		if (!devMatrix.containsKey(testItem)) return Double.NaN; // not predictable

		double sum = 0.0;
		int cnt = 0;

		for (String item : itemRatings.keySet())
		{
			double rate = itemRatings.get(item).getRating();

			if (!devMatrix.get(testItem).containsKey(item)) continue;

			int freq = freqMatrix.get(testItem).get(item);
			double pred = devMatrix.get(testItem).get(item) + rate;

			if (weighted)
			{
				sum += freq * pred;
				cnt += freq;
			} else
			{
				sum += pred;
				cnt++;
			}
		}

		return sum / cnt;
	}

	@Override
	protected void runCrossValidation()
	{
		for (Entry<String, Map<String, Rating>> en : threadMap.entrySet())
		{
			String testUser = en.getKey();
			Map<String, Rating> itemRatings = userRatingsMap.get(testUser);
			// have not rated any items
			if (itemRatings == null) continue;

			Map<String, Rating> testRatings = en.getValue();
			for (String testItem : testRatings.keySet())
			{
				double pred = predict(itemRatings, testItem, true);
				if (Double.isNaN(pred)) continue;

				Rating rating = testRatings.get(testItem);

				pf.addPredicts(new Prediction(rating, pred));
			}
		}
	}

	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		// TODO Auto-generated method stub
		return null;
	}

}
