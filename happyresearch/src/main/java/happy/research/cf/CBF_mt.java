package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.KeyValPair;
import happy.coding.io.Lists;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.math.Sims;
import happy.coding.system.Debug;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class CBF_mt extends DefaultCF_mt
{
	//format: {item: {tag: count}}
	public static Map<String, Map<String, Integer>>	itemTags;

	public static List<String>						tags;

	//format: {item: {tag: tf}}
	public static Map<String, Map<String, Double>>	itemVecs;

	//format: {tag: df}
	public static Map<String, Double>				tagIDFs;

	public CBF_mt()
	{
		methodId = "Content-based CF";
	}

	@Override
	protected void loadDataset() throws Exception
	{
		Dataset.RATING_SET = "ratings.csv";
		Dataset.REGMX = ",";
		super.load_ratings();

		// load tags
		load_tags();
	}

	private void load_tags() throws Exception
	{
		String file = "movie-tags.csv";
		if (itemTags == null)
		{
			itemTags = new HashMap<>();
			String path = FileIO.makeDirPath(Dataset.DIRECTORY, file);

			Logs.debug("Load item's tags from {}", Strings.shortStr(path));

			tags = new ArrayList<>();

			BufferedReader br = new BufferedReader(new FileReader(new File(path)));
			String line = null;
			while ((line = br.readLine()) != null)
			{
				String[] data = line.split(",");
				String item = data[0];
				String tag = data[1]; //.toLowerCase().replace(" ", "");

				if (!tags.contains(tag)) tags.add(tag);

				Map<String, Integer> innerMap = null;
				if (itemTags.containsKey(item)) innerMap = itemTags.get(item);
				else innerMap = new HashMap<>();

				int count = 0;
				if (innerMap.containsKey(tag)) count = innerMap.get(tag);
				count++;

				innerMap.put(tag, count);
				itemTags.put(item, innerMap);
			}
			br.close();
		}
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		boolean weighted = !true;
		String dir = "D:\\Dropbox\\PhD\\My Work\\Algorithms\\@Machine Learning\\RecSys\\Assignments\\A3\\";
		String file = "unweighted.txt";
		if (weighted) file = "weighted.txt";

		file = dir + file;
		FileIO.deleteFile(file);

		compItemVectors();

		String[] users = {/* examples */"4045", "144", "3855", "1637", "2919",
		/*tests*/"4934", "3511", "4835", "3362", "1270" };

		for (int k = 0; k < users.length; k++)
		{
			String user = users[k];
			Map<String, Rating> itemRatings = userRatingsMap.get(user);

			double mean = RatingUtils.mean(itemRatings, null);

			// build user profile
			// {tag: score}
			Map<String, Double> userVec = new HashMap<>();
			for (String tag : tags)
				userVec.put(tag, 0.0);

			for (Entry<String, Rating> en : itemRatings.entrySet())
			{
				String item = en.getKey();
				double rate = en.getValue().getRating();

				double temp = rate;
				if (weighted)
				{
					// since we need to sum all items rather than all positively rated items
					rate = Dataset.maxScale;
				}
				if (rate >= 3.5)
				{
					Map<String, Double> itemVec = itemVecs.get(item);

					for (Entry<String, Double> en2 : itemVec.entrySet())
					{
						String tag = en2.getKey();
						double score = en2.getValue();

						if (weighted)
						{
							score *= (temp - mean);
						}

						score += userVec.get(tag);
						userVec.put(tag, score);
					}
				}
			}

			// DO Recommendations: {item: reco-socres}
			Map<String, Double> itemScores = new HashMap<>();
			for (String item : itemVecs.keySet())
			{
				if (itemRatings.containsKey(item)) continue;

				List<Double> us = new ArrayList<>();
				List<Double> vs = new ArrayList<>();

				Map<String, Double> vec = itemVecs.get(item);
				if (Debug.OFF)
				{
					for (Entry<String, Double> en3 : vec.entrySet())
					{
						String tag = en3.getKey();
						double score = en3.getValue();

						if (userVec.containsKey(tag))
						{
							us.add(userVec.get(tag));
							vs.add(score);
						}
					}
				} else
				{
					for (String tag : tags)
					{
						us.add(userVec.get(tag));

						if (vec.containsKey(tag)) vs.add(vec.get(tag));
						else vs.add(0.0);
					}
				}

				double score = Sims.cos(us, vs);
				itemScores.put(item, score);
			}
			List<KeyValPair<String>> pairs = Lists.sortMap(itemScores, true);

			StringBuilder sb = new StringBuilder();
			sb.append("recommendations for user " + user + ":\n");
			for (int i = 0; i < 5; i++)
			{
				KeyValPair<String> pair = pairs.get(i);
				sb.append("  " + pair.getKey() + ": " + Strings.toString(pair.getVal(), 4) + "\n");
			}

			if (k < 5) System.out.println(sb.toString());
			else FileIO.writeString(file, sb.toString(), true);
		}

		return null;
	}

	private void compItemVectors()
	{
		// total items
		int numItems = itemTags.size();

		// build tag - IDF
		tagIDFs = new HashMap<>();
		for (Entry<String, Map<String, Integer>> en : itemTags.entrySet())
		{
			for (String tag : en.getValue().keySet())
			{
				double cnt = 0;
				if (tagIDFs.containsKey(tag)) cnt = tagIDFs.get(tag);
				cnt += 1;

				tagIDFs.put(tag, cnt);
			}
		}
		for (String tag : tagIDFs.keySet())
		{
			double cnt = tagIDFs.get(tag);
			double df = numItems / cnt;
			if (Debug.OFF)
			{
				// in fact, it usually is 1+cnt, rather than cnt
				df = numItems / (1.0 + cnt);
			}

			tagIDFs.put(tag, Math.log(df));
		}

		// build item - TF-IDF
		itemVecs = new HashMap<>();
		for (String item : itemTags.keySet())
		{
			Map<String, Integer> tagCnts = itemTags.get(item);

			Map<String, Double> tagVec = new HashMap<>();
			double squareSum = 0;
			for (Entry<String, Integer> en : tagCnts.entrySet())
			{
				String tag = en.getKey();
				int cnt = en.getValue();

				// typical way: tf = Math.log(1 + cnt);
				// simple way: tf = cnt
				double val = cnt * tagIDFs.get(tag);
				tagVec.put(tag, val);
				squareSum += val * val;
			}

			// normalization
			double norm = Math.sqrt(squareSum);
			for (String tag : tagVec.keySet())
				tagVec.put(tag, tagVec.get(tag) / norm);

			itemVecs.put(item, tagVec);
		}
	}

}
