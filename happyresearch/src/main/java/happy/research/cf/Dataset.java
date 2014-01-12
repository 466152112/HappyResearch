package happy.research.cf;

import happy.coding.math.Stats;
import happy.coding.system.Debug;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dataset
{
	private final static Logger	logger	= LoggerFactory.getLogger(Dataset.class);

	public enum DATASET {

		EPINIONS("Epinions"), EXTENDED_EPINIONS("ExtendedEpinions"), MOVIELENS("MovieLens"), FILMTRUST("FilmTrust"), FLIXSTER(
				"Flixster"), JESTER("Jester"), NETFLIX("Netflix"), BOOKCROSSING("BookCrossing"), VIRTUALRATINGS(
				"VirtualRatings"), TEMP("TempDataSet");

		public String	datasetLabel	= null;

		DATASET(String label)
		{
			datasetLabel = label;
		}
	};

	public static String				LABEL				= null;
	public static int					scaleSize			= 0;
	public static double				minScale			= 1.0;
	public static double				maxScale			= 5.0;
	public static double				range				= maxScale - minScale;
	public static double[]				scales				= null;
	public static double				median				= 0.0;
	public static double				relevance_threshold	= 0.0;
	public static double				mean				= 0.0;
	public static double				sd					= 0.0;
	public static double				user_mean			= 0.0;
	public static double				user_sd				= 0.0;
	public static double				item_mean			= 0.0;
	public static double				item_sd				= 0.0;
	public static int					users				= 0;
	public static int					items				= 0;
	public static int					size				= 0;
	public static int					maxUserRating		= 0;
	public static int					minUserRating		= 0;
	public static int					maxItemRating		= 0;
	public static int					minItemRating		= 0;
	public static double				sparsity			= 0.0;
	public static Map<Double, Integer>	scaleNum			= null;
	public static Map<Double, Double>	scaleRatio			= null;

	public static DATASET				dataset				= null;
	public static String				DIRECTORY			= null;
	public static String				TEMP_DIRECTORY		= null;
	public static String				RATING_SET			= "ratings.txt";
	public static String				TRUST_SET			= "trust.txt";
	public static String				DISTRUST_SET		= "distrust.txt";
	public static String				REGMX				= " ";

	public static void printSpecs()
	{
		logger.info("Dataset.label = {}", LABEL);
		logger.info("Dataset.users = {}, items = {}, size = {}", new Object[] { users, items, size });
		logger.info("Dataset.sparsity = {}%, density = {}%", (float) (sparsity * 100), (float) ((1 - sparsity) * 100));
		logger.info("Dataset.scales = {}", scales);

		String dist = "[";
		String rato = "[";
		for (int i = 0; i < scales.length; i++)
		{
			dist += scaleNum.get(scales[i]);
			rato += scaleRatio.get(scales[i]).floatValue() * 100 + "%";
			if (i < scales.length - 1)
			{
				dist += ", ";
				rato += ", ";
			}
		}
		dist += "]";
		rato += "]";

		logger.info("Dataset.scaleDist = {}", dist);
		logger.info("Dataset.scaleDist = {}", rato);
		logger.info("Dataset.mean = {}, sd = {}", (float) mean, (float) sd);
		logger.info("Dataset.user_mean = {}, user_sd = {}", (float) user_mean, (float) user_sd);
		logger.info("Dataset.item_mean = {}, item_sd = {}", (float) item_mean, (float) item_sd);
		logger.info("Dataset.max_user_rating = {}, min_user_rating = {}", maxUserRating, minUserRating);
		logger.info("Dataset.max_item_rating = {}, min_item_rating = {}", maxItemRating, minItemRating);
	}

	public static void init(String label) throws Exception
	{
		LABEL = label;

		boolean found = false;
		for (DATASET d : DATASET.values())
		{
			if (d.datasetLabel.equalsIgnoreCase(LABEL))
			{
				dataset = d;
				found = true;
				break;
			}
		}
		if (!found) dataset = DATASET.TEMP;

		switch (dataset)
		{
			case BOOKCROSSING:
				scales = new double[] { 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0 };
				relevance_threshold = 9.0;
				break;
			case FILMTRUST:
				scales = new double[] { 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0 };
				relevance_threshold = 3.0;
				break;
			case FLIXSTER:
				scales = new double[] { 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0 };
				relevance_threshold = 4.5;
				break;
			default:
				if (Debug.OFF)
				{
					scales = new double[] { 1.0, 2.0, 3.0, 4.0, 5.0 };
					relevance_threshold = 4.5;
				} else
				{
					scales = new double[] { 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0 };
					relevance_threshold = 3.5;
				}
				break;
		}
		scaleSize = scales.length;
		minScale = scales[0];
		maxScale = scales[scaleSize - 1];
		range = maxScale - minScale;
		median = Stats.median(scales);
	}

	/**
	 * load training rating data, the statistics of rating sets are summarized as also
	 * 
	 * @param ratingSet
	 *            filePath of training ratings
	 * @return Map[]{ userRatingsMap, itemRatingsMap } where
	 *         <ul>
	 *         <li>userRatingsMap: { user - {item - rating} }</li>
	 *         <li>itemRatingsMap: { item - {user - rating} }</li>
	 *         </ul>
	 * @throws Exception
	 */
	@SuppressWarnings("rawtypes")
	public static Map[] loadTrainSet(String ratingSet) throws Exception
	{
		HashMap<String, Map<String, Rating>> userRatingsMap = new HashMap<>();
		HashMap<String, Map<String, Rating>> itemRatingsMap = new HashMap<>();

		List<Double> ratings = new ArrayList<>();
		scaleNum = new HashMap<>();
		scaleRatio = new HashMap<>();
		for (Double scale : scales)
		{
			scaleNum.put(scale, 0);
			scaleRatio.put(scale, 0.0);
		}

		maxUserRating = 0;
		minUserRating = 0;
		maxItemRating = 0;
		minItemRating = 0;

		BufferedReader fr = new BufferedReader(new FileReader(ratingSet));
		String line = null;
		while ((line = fr.readLine()) != null)
		{
			if (line.trim().isEmpty()) continue;
			String[] data = line.split(Dataset.REGMX);
			String userId = data[0];
			String itemId = data[1];
			double rating = Double.parseDouble(data[2]);
			Long timestamp = 0l;
			if (data.length > 3) timestamp = Long.parseLong(data[3]);

			if (VirRatingsCF.auto)
			{
				boolean flag = (VirRatingsCF.userIds != null && !VirRatingsCF.userIds.contains(userId))
						|| (VirRatingsCF.userIds == null);

				if (Integer.parseInt(userId) > VirRatingsCF.PhyRatingUpbound && flag) continue;
			}

			Rating r = new Rating();
			r.setUserId(userId);
			r.setItemId(itemId);
			r.setRating(rating);
			r.setTimestamp(timestamp);

			ratings.add(rating);

			Map<String, Rating> itemRatings = null;
			if (userRatingsMap.containsKey(userId)) itemRatings = userRatingsMap.get(userId);
			else itemRatings = new HashMap<>();
			itemRatings.put(itemId, r);
			userRatingsMap.put(userId, itemRatings);

			Map<String, Rating> userRatings = null;
			if (itemRatingsMap.containsKey(itemId)) userRatings = itemRatingsMap.get(itemId);
			else userRatings = new HashMap<>();
			userRatings.put(userId, r);
			itemRatingsMap.put(itemId, userRatings);

			int num = 0;
			if (scaleNum.containsKey(rating)) num = scaleNum.get(rating);
			scaleNum.put(rating, num + 1);
		}
		fr.close();

		/* Retrieve the statistics of the data set */
		users = userRatingsMap.size();
		items = itemRatingsMap.size();
		size = ratings.size();
		sparsity = 1 - (size + 0.0) / (users * items);
		mean = Stats.mean(ratings);
		sd = Stats.sd(ratings, mean);

		//logger.debug("Rating median = {}", Stats.median(ratings));

		for (Entry<Double, Integer> en : scaleNum.entrySet())
			scaleRatio.put(en.getKey(), en.getValue() / (size + 0.0));

		List<Integer> userNum = new ArrayList<>();
		for (Map<String, Rating> data : userRatingsMap.values())
		{
			userNum.add(data.size());

			if (minUserRating == 0) minUserRating = data.size();
			if (maxUserRating < data.size()) maxUserRating = data.size();
			if (minUserRating > data.size()) minUserRating = data.size();
		}

		List<Integer> itemNum = new ArrayList<>();

		// double sumI = 0;
		for (Map<String, Rating> data : itemRatingsMap.values())
		{
			itemNum.add(data.size());

			if (minItemRating == 0) minItemRating = data.size();
			if (maxItemRating < data.size()) maxItemRating = data.size();
			if (minItemRating > data.size()) minItemRating = data.size();

			// sumI += RatingUtils.mean(data.values());
		}

		// System.out.println(sumI / itemRatingsMap.size());

		user_mean = Stats.mean(userNum);
		user_sd = Stats.sd(userNum, user_mean);

		item_mean = Stats.mean(itemNum);
		item_sd = Stats.sd(itemNum, item_mean);

		return new Map[] { userRatingsMap, itemRatingsMap };
	}

	public static HashMap<String, Map<String, Rating>> loadRatingSet(String ratingSet) throws Exception
	{
		HashMap<String, Map<String, Rating>> userRatingsMap = new HashMap<>();

		BufferedReader fr = new BufferedReader(new FileReader(ratingSet));
		String line = null;
		while ((line = fr.readLine()) != null)
		{
			if (line.trim().isEmpty()) continue;
			String[] data = line.split(Dataset.REGMX);
			String userId = data[0];
			String itemId = data[1];
			double rating = Double.parseDouble(data[2]);
			Long timestamp = 0l;
			if (data.length > 3) timestamp = Long.parseLong(data[3]);

			Rating r = new Rating();
			r.setUserId(userId);
			r.setItemId(itemId);
			r.setRating(rating);
			r.setTimestamp(timestamp);

			Map<String, Rating> itemRatings = null;
			if (userRatingsMap.containsKey(userId)) itemRatings = userRatingsMap.get(userId);
			else itemRatings = new HashMap<>();
			itemRatings.put(itemId, r);

			userRatingsMap.put(userId, itemRatings);
		}
		fr.close();

		return userRatingsMap;
	}

	/**
	 * load training test data
	 * 
	 * @param ratingSet
	 *            filePath of test ratings
	 * @return Map[]{ userRatingsMap, itemRatingsMap } where
	 *         <ul>
	 *         <li>userRatingsMap: { user - {item - rating} }</li>
	 *         <li>itemRatingsMap: { item - {user - rating} }</li>
	 *         </ul>
	 * @throws Exception
	 */
	@SuppressWarnings("rawtypes")
	public static Map[] loadTestSet(String ratingSet) throws Exception
	{
		HashMap<String, Map<String, Rating>> userRatingsMap = new HashMap<>();
		HashMap<String, Map<String, Rating>> itemRatingsMap = new HashMap<>();

		BufferedReader fr = new BufferedReader(new FileReader(ratingSet));
		String line = null;
		while ((line = fr.readLine()) != null)
		{
			if (line.trim().isEmpty()) continue;
			String[] data = line.split(Dataset.REGMX);
			String userId = data[0];
			String itemId = data[1];
			double rating = Double.parseDouble(data[2]);
			Long timestamp = 0l;
			if (data.length > 3) timestamp = Long.parseLong(data[3]);

			// if (userId > VirRatingsCF.PhyRatingUpbound + num) continue;

			Rating r = new Rating();
			r.setUserId(userId);
			r.setItemId(itemId);
			r.setRating(rating);
			r.setTimestamp(timestamp);

			Map<String, Rating> itemRatings = null;
			if (userRatingsMap.containsKey(userId)) itemRatings = userRatingsMap.get(userId);
			else itemRatings = new HashMap<>();
			itemRatings.put(itemId, r);
			userRatingsMap.put(userId, itemRatings);

			Map<String, Rating> userRatings = null;
			if (itemRatingsMap.containsKey(itemId)) userRatings = itemRatingsMap.get(itemId);
			else userRatings = new HashMap<>();
			userRatings.put(userId, r);
			itemRatingsMap.put(itemId, userRatings);
		}
		fr.close();

		return new Map[] { userRatingsMap, itemRatingsMap };
	}

	public static void main(String[] args) throws Exception
	{
		ConfigParams.defaultInstance();
		String ratingSet = DIRECTORY + "u1.base";
		loadTrainSet(ratingSet);

		printSpecs();
	}
}
