package happy.research.cf;

import happy.coding.math.Stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;

public class RatingUtils
{
	protected final static StandardDeviation	sd	= new StandardDeviation();

	/**
	 * to rating values array
	 * 
	 * @param ratings
	 *            the collection of ratings
	 * @return a double array with rating values
	 */
	public static List<Double> toRatings(Collection<Rating> ratings)
	{
		if (ratings == null) return null;
		List<Double> rs = new ArrayList<>();
		for (Rating r : ratings)
			rs.add(r.getRating());

		return rs;
	}

	public static synchronized double std(Collection<Rating> ratings)
	{
		if (ratings == null || ratings.size() < 1) return Double.NaN;

		List<Double> values = toRatings(ratings);

		return Stats.sd(values);
	}

	public static double meanDistance(Map<String, Rating> ratings, Map<String, Double> itemMeanMap)
	{
		if (ratings == null || ratings.size() < 1) return Double.NaN;

		double sum = 0.0;
		double distance = 0.0;
		int count = 0;
		for (Rating r : ratings.values())
		{
			String item = r.getItemId();
			double mean = itemMeanMap.get(item);
			distance = Math.abs(r.getRating() - mean);

			sum += distance;
			count++;
		}

		return sum / count;
	}

	public static double mean(Collection<Rating> ratings)
	{
		double sum = 0.0;
		for (Rating r : ratings)
			sum += r.getRating();
		return sum / ratings.size();
	}

	public static double mean(Map<String, Rating> ratings, Rating testRating)
	{
		double sum = 0.0;
		int count = 0;
		for (Entry<String, Rating> r : ratings.entrySet())
		{
			if (testRating != null)
			{
				String user = r.getValue().getUserId();
				String item = r.getValue().getItemId();

				if (testRating.getUserId().equals(user) && testRating.getItemId().equals(item)) continue;
			}

			count++;
			sum += r.getValue().getRating();
		}
		return sum / count;
	}

	public static double mean(Map<String, Map<String, Rating>> userRatingsMap)
	{
		double sum = 0.0;
		for (Map<String, Rating> rs : userRatingsMap.values())
		{
			double userSum = 0.0;
			for (Rating r : rs.values())
				userSum += r.getRating();

			double userMean = userSum / rs.size();
			sum += userMean;
		}
		return sum / userRatingsMap.size();
	}

	public static Map<String, Double> itemMeans(Map<String, Map<String, Rating>> itemRatingsMap)
	{
		Map<String, Double> map = new HashMap<>();

		for (Entry<String, Map<String, Rating>> en : itemRatingsMap.entrySet())
		{
			String item = en.getKey();
			Map<String, Rating> ratings = en.getValue();
			double mean = Stats.mean(toRatings(ratings.values()));
			if (Double.isNaN(mean)) continue;

			map.put(item, mean);
		}

		return map;
	}
}
