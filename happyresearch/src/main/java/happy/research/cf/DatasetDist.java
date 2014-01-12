package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.FileIO.MapWriter;
import happy.coding.io.Logs;
import happy.coding.system.Debug;
import happy.coding.system.Systems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;
 
public class DatasetDist
{
	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void coldUsers_trust() throws Exception
	{
		ConfigParams.defaultInstance();
		String ratingSet = Dataset.DIRECTORY + Dataset.RATING_SET;
		Map[] data = Dataset.loadTrainSet(ratingSet);

		Map<String, Map<String, Rating>> userRatingsMap = data[0];
		//Map<Integer, Map<Integer, Rating>> itemRatingsMap = data[1];

		Map<String, Map<String, Rating>> coldRatingsMap = new HashMap<>();
		int cold_size = 5;
		for (Entry<String, Map<String, Rating>> en : userRatingsMap.entrySet())
		{
			String user = en.getKey();

			Map<String, Rating> itemRatings = en.getValue();

			if (itemRatings.size() < cold_size) coldRatingsMap.put(user, itemRatings);
		}

		// load trust
		String trustSet = Dataset.DIRECTORY + Dataset.TRUST_SET;
		Map<String, Map<String, Double>> userTrustMap = DatasetUtils.loadTrustSet(trustSet);

		Map<String, Map<String, Double>> coldTrustMap = new HashMap<>();

		// {# trusts, # users}
		Map<Integer, Integer> trustee_user = new HashMap<>();
		int maxTrust = Integer.MIN_VALUE;
		int maxUser = Integer.MIN_VALUE;
		for (String user : coldRatingsMap.keySet())
		{
			if (userTrustMap.containsKey(user))
			{
				coldTrustMap.put(user, userTrustMap.get(user));

				int size = userTrustMap.get(user).size();

				int count = 0;
				if (trustee_user.containsKey(size)) count = trustee_user.get(size);
				count++;

				trustee_user.put(size, count);

				if (maxTrust < size) maxTrust = size;
				if (maxUser < count) maxUser = count;

			}
		}

		// print out
		FileIO.writeMap(Systems.getDesktop() + "ps.txt", trustee_user);

		Systems.pause();

		if (Debug.OFF)
		{
			// print out
			String dir = Systems.getDesktop();
			FileIO.writeMap(dir + "user-ratings.txt", coldRatingsMap, new MapWriter<String, Map<String, Rating>>() {

				@Override
				public String processEntry(String key, Map<String, Rating> val)
				{
					return key + " " + val.size();
				}
			}, false);

			FileIO.writeMap(dir + "user-trust.txt", coldTrustMap, new MapWriter<String, Map<String, Double>>() {

				@Override
				public String processEntry(String key, Map<String, Double> val)
				{
					return key + " " + val.size();
				}
			}, false);
		}
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void coldUsers_ratings() throws Exception
	{
		ConfigParams.defaultInstance();
		String ratingSet = Dataset.DIRECTORY + Dataset.RATING_SET;
		Map[] data = Dataset.loadTrainSet(ratingSet);

		Map<String, Map<String, Rating>> userRatingsMap = data[0];
		Map<String, Map<String, Rating>> itemRatingsMap = data[1];

		Map<String, Map<String, Rating>> coldRatingsMap = new HashMap<>();
		int cold_size = 5;
		for (Entry<String, Map<String, Rating>> en : userRatingsMap.entrySet())
		{
			String user = en.getKey();
			Map<String, Rating> itemRatings = en.getValue();
			if (itemRatings.size() < cold_size) coldRatingsMap.put(user, itemRatings);
		}

		Map<Double, Integer> scaleDist = new HashMap<>(); // {rating value, # items}
		Map<Integer, Integer> sizeDist = new HashMap<>(); // {rating size, # users}
		List<String> items = new ArrayList<>();
		for (Entry<String, Map<String, Rating>> en : coldRatingsMap.entrySet())
		{
			Map<String, Rating> itemRatings = en.getValue();

			Integer size = itemRatings.size();
			Integer count = 0;
			if (sizeDist.containsKey(size)) count = sizeDist.get(size);
			count++;
			sizeDist.put(size, count);

			for (Rating r : itemRatings.values())
			{
				Double rate = r.getRating();
				Integer cnt = 0;

				if (scaleDist.containsKey(rate)) cnt = scaleDist.get(rate);

				cnt++;
				scaleDist.put(rate, cnt);

				String item = r.getItemId();
				if (!items.contains(item)) items.add(item);
			}
		}

		String dir = Systems.getDesktop();
		FileIO.writeMap(dir + "scaleDist.txt", scaleDist);
		FileIO.writeMap(dir + "sizeDist.txt", sizeDist);

		Map<Integer, Integer> itemNums = new HashMap<>();
		for (String item : items)
		{
			Integer cnt = itemRatingsMap.get(item).size();

			Integer cnt2 = 0;
			if (itemNums.containsKey(cnt)) cnt2 = itemNums.get(cnt);
			cnt2++;

			itemNums.put(cnt, cnt2);
		}

		FileIO.writeMap(dir + "itemDist.txt", itemNums);
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void allUsers_ratings() throws Exception
	{
		ConfigParams.defaultInstance();
		String ratingSet = Dataset.DIRECTORY + Dataset.RATING_SET;
		Map[] data = Dataset.loadTrainSet(ratingSet);

		//Map<Integer, Map<Integer, Rating>> userRatingsMap = data[0];
		Map<Integer, Map<Integer, Rating>> itemRatingsMap = data[1];

		Map<Integer, Integer> sizeDist = new HashMap<>();
		for (Integer item : itemRatingsMap.keySet())
		{
			Map<Integer, Rating> userRatings = itemRatingsMap.get(item);

			int size = userRatings.size();
			int cnt = 0;

			if (sizeDist.containsKey(size)) cnt = sizeDist.get(size);
			cnt++;

			sizeDist.put(size, cnt);
		}

		int items = 0;
		int ratings = 0;
		for (Entry<Integer, Integer> en : sizeDist.entrySet())
		{
			items += en.getValue();
			ratings += en.getValue() * en.getKey();
		}
		Logs.debug("Avg = " + ratings / (items + 0.0));
	}

}
