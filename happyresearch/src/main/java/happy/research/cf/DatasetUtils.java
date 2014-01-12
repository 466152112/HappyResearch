package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.FileIO.Converter;
import happy.coding.io.FileIO.MapWriter;
import happy.coding.io.Lists;
import happy.coding.io.Logs;
import happy.coding.math.Randoms;
import happy.coding.math.Stats;
import happy.coding.system.Systems;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;

public class DatasetUtils
{
	private final static Logger	logger	= Logs.getLogger();

	@SuppressWarnings("unchecked")
	public static <E> List<E>[] splitCollection(List<E> data, int num)
	{
		int size = data.size();
		int lenThread = 1 + size / num;

		List<E>[] test = new List[num];
		for (int i = 0; i < test.length; i++)
		{
			test[i] = new ArrayList<>(lenThread);

			for (int j = 0; j < lenThread; j++)
			{
				int index = i * lenThread + j;
				if (index < size)
				{
					test[i].add(data.get(index));
				} else
				{
					break;
				}
			}
		}
		return test;
	}

	/**
	 * @param trust_data_set
	 * @return Map[]{userTrusteesMap, userTrustorsMap, userTrustRatingsMap}
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes" })
	public static Map[] loadTrustSet2(String trust_data_set) throws Exception
	{
		BufferedReader fr = new BufferedReader(new FileReader(trust_data_set));

		Map<String, Map<String, Double>> userTrusteesMap = new HashMap<>();
		Map<String, Map<String, Double>> userTrustorsMap = new HashMap<>();
		Map<String, List<TrustRating>> userTrustRatingsMap = new HashMap<>();

		String line = null;
		Map<String, Double> tors = null, tees = null;
		List<TrustRating> trs = null;
		while ((line = fr.readLine()) != null)
		{
			String[] data = line.split(Dataset.REGMX);
			String trustor = data[0];
			String trustee = data[1];
			double rating = Double.parseDouble(data[2]);

			// if (trustee == trustor) continue; // to remove self-indicate
			// entry
			TrustRating tr = new TrustRating();
			tr.setRating(rating);
			tr.setTrustee(trustee);
			tr.setTrustor(trustor);

			if (userTrustRatingsMap.containsKey(trustee)) trs = userTrustRatingsMap.get(trustee);
			else trs = new ArrayList<>();
			trs.add(tr);
			userTrustRatingsMap.put(trustee, trs); // {trustee - ratings on
													// trustee}

			if (userTrusteesMap.containsKey(trustor)) tees = userTrusteesMap.get(trustor);
			else tees = new HashMap<>();
			tees.put(trustee, 1.0);
			userTrusteesMap.put(trustor, tees); // {trustor - trusted
												// neighbours}

			if (userTrustorsMap.containsKey(trustee)) tors = userTrustorsMap.get(trustee);
			else tors = new HashMap<>();
			tors.put(trustor, 1.0);
			userTrustorsMap.put(trustee, tors); // {trustee - trustors on the
												// trustee}

		}
		fr.close();

		return new Map[] { userTrusteesMap, userTrustorsMap, userTrustRatingsMap };
	}

	public static Map<String, Map<String, Double>> loadTrusteeSet(String trustSet) throws Exception
	{
		BufferedReader fr = new BufferedReader(new FileReader(trustSet));

		Map<String, Map<String, Double>> userTrustorsMap = new HashMap<>();
		String line = null;
		Map<String, Double> trustors = null;
		while ((line = fr.readLine()) != null)
		{
			if (line.equals("")) continue;
			String[] data = line.split(" ");
			String trustor = data[0];
			String trustee = data[1];
			double trustScore = Double.parseDouble(data[2]);

			if (trustee.equals(trustor)) continue; // to remove self-indicate entry

			if (userTrustorsMap.containsKey(trustee)) trustors = userTrustorsMap.get(trustee);
			else trustors = new HashMap<>();

			trustors.put(trustor, trustScore);
			userTrustorsMap.put(trustee, trustors);
		}
		fr.close();
 
		return userTrustorsMap;
	}

	public static Map<String, Map<String, Double>> loadTrustSet(String trustSet) throws Exception
	{
		BufferedReader fr = new BufferedReader(new FileReader(trustSet));

		Map<String, Map<String, Double>> userTNsMap = new HashMap<>();
		String line = null;
		Map<String, Double> tns = null;
		while ((line = fr.readLine()) != null)
		{
			if (line.equals("")) continue;
			String[] data = line.split(" ");
			String trustor = data[0];
			String trustee = data[1];
			double trustScore = Double.parseDouble(data[2]);

			if (trustee.equals(trustor)) continue; // to remove self-indicate entry

			if (userTNsMap.containsKey(trustor)) tns = userTNsMap.get(trustor);
			else tns = new HashMap<>();

			tns.put(trustee, trustScore);
			userTNsMap.put(trustor, tns);
		}
		fr.close();

		return userTNsMap;
	}

	/**
	 * Read Trust Set and Distrust Set from trust.txt
	 * 
	 * @param trust_data_set
	 * @return Map[]{TrustSet, DistrustSet}
	 * @throws Exception
	 */
	@SuppressWarnings("rawtypes")
	public static Map[] loadTrustDistrustSets(String trust_data_set) throws Exception
	{
		BufferedReader fr = new BufferedReader(new FileReader(trust_data_set));

		/* Trust Set */
		Map<String, List<String>> usersTNsMap = new HashMap<>();

		/* Distrust Set */
		Map<String, List<String>> usersDTNsMap = new HashMap<>();

		String line = null;
		List<String> tns = null, dtns = null;
		while ((line = fr.readLine()) != null)
		{
			String[] data = line.split(" ");
			String trustor = data[0];
			String trustee = data[1];
			int value = Integer.parseInt(data[2]);

			if (trustee.equals(trustor)) continue; // to remove self-indicate entry

			if (value == 1)
			{
				if (usersTNsMap.containsKey(trustor))
				{
					tns = usersTNsMap.get(trustor);
					tns.add(trustee);
				} else
				{
					tns = new ArrayList<>();
					tns.add(trustee);
				}
				usersTNsMap.put(trustor, tns);

			} else if (value == -1)
			{
				if (usersDTNsMap.containsKey(trustor))
				{
					dtns = usersDTNsMap.get(trustor);
					dtns.add(trustee);
				} else
				{
					dtns = new ArrayList<>();
					dtns.add(trustee);
				}
				usersDTNsMap.put(trustor, dtns);
			}

		}
		fr.close();

		return new Map[] { usersTNsMap, usersDTNsMap };
	}

	public static void convertEpinionsTrust() throws Exception
	{
		String dirPath = "D:\\Java\\eclipse\\workspace\\CF-RS\\dataset\\Extended Epinions\\";
		String source = dirPath + "rating.txt";

		String ids = dirPath + "itemId-mappings.txt";
		String target = dirPath + "ratings.txt";

		BufferedReader br = new BufferedReader(new FileReader(new File(source)));
		String line = null;
		Map<String, String> idMap = FileIO.readAsMap(ids);
		// Map<String, Integer> idMap = new HashMap<String, Integer>();
		List<String> lines = new ArrayList<>();

		while ((line = br.readLine()) != null)
		{
			String[] data = line.split(Dataset.REGMX);
			String userId = data[0];
			String itemId = data[1];
			String rating = data[2];

			String iId = idMap.get(itemId);

			String content = userId + Dataset.REGMX + iId + Dataset.REGMX + rating;
			lines.add(content);
			if (lines.size() == 1000)
			{
				FileIO.writeList(target, lines, null, true);
				lines.clear();
			}

		}
		if (lines.size() > 0) FileIO.writeList(target, lines, null, true);

		br.close();

	}

	/**
	 * Sample data set
	 * 
	 * @throws Exception
	 */
	@Ignore
	@Test
	public void sampleRatingDataset() throws Exception
	{
		ConfigParams.defaultInstance();
		String source = Dataset.DIRECTORY + Dataset.RATING_SET;
		String target = Systems.getDesktop() + Dataset.RATING_SET;

		int max = 664_824;
		double percent = 0.06;

		int sample = (int) (max * percent);

		int len = 1000;
		int iteration = sample / len + (sample % len == 0 ? 0 : 1);
		int step = max / iteration;

		int start, end;

		for (int i = 0; i < iteration; i++)
		{
			start = 1 + step * i;
			if (i == iteration - 1)
			{
				end = max;
				len = sample - i * len;
			} else end = start + step;

			logger.debug("len, min, max = {}, {}, {}", new Object[] { len, start, end });

			int[] idsArray = Randoms.nextNoRepeatIntArray(len, start, end);

			String lines = FileIO.readAsString(source, idsArray);
			FileIO.writeString(target, lines, true);
		}

	}

	public static void convertFlixsterTrust() throws Exception
	{
		String unformatted_file = "D:\\Dropbox\\Coding\\Java_Projects\\HappyCoding\\dataset\\Flixter\\trust.txt";
		String formatted_file = "C:\\Users\\guoguibing\\Desktop\\trust.txt";
		BufferedReader br = new BufferedReader(new FileReader(unformatted_file));
		BufferedWriter bw = new BufferedWriter(new FileWriter(formatted_file));
		String line = null;
		while ((line = br.readLine()) != null)
		{
			line = line.replace("\t", Dataset.REGMX);
			String data[] = line.split(Dataset.REGMX);
			int trustor = Integer.parseInt(data[0]);
			int trustee = Integer.parseInt(data[1]);

			String line1 = trustor + Dataset.REGMX + trustee + Dataset.REGMX + "1.0";
			String line2 = trustee + Dataset.REGMX + trustor + Dataset.REGMX + "1.0";
			bw.write(line1 + "\n");
			bw.write(line2 + "\n");
		}
		br.close();
		bw.close();
	}

	public static void convertJester() throws Exception
	{
		String unformatted_file = "D:\\Dropbox\\Coding\\Java_Projects\\HappyCoding\\dataset\\Jester\\ratings_original.txt";
		String formatted_file = "C:\\Users\\guoguibing\\Desktop\\ratings.txt";
		BufferedReader br = new BufferedReader(new FileReader(unformatted_file));
		BufferedWriter bw = new BufferedWriter(new FileWriter(formatted_file));
		String line = null;
		while ((line = br.readLine()) != null)
		{
			line = line.replace("\t", Dataset.REGMX);
			String data[] = line.split(Dataset.REGMX);
			double rating = Double.parseDouble(data[2]);
			if (rating >= 6 && rating <= 10) rating = 5.0;
			else if (rating >= 2) rating = 4.0;
			else if (rating >= -2) rating = 3.0;
			else if (rating >= -6) rating = 2.0;
			else if (rating >= -10) rating = 1.0;

			String msg = data[0] + Dataset.REGMX + data[1] + Dataset.REGMX + rating + "\n";
			bw.write(msg);
		}
		br.close();
		bw.close();
	}

	public static void convertMovieLensRating(String unformatted_file, String formatted_file, String regex)
			throws Exception
	{
		BufferedReader br = new BufferedReader(new FileReader(unformatted_file));
		BufferedWriter bw = new BufferedWriter(new FileWriter(formatted_file));
		String line = null;
		while ((line = br.readLine()) != null)
		{
			line = line.replaceAll(regex, Dataset.REGMX);
			bw.write(line + "\n");
		}
		br.close();
		bw.close();
	}

	public static void convertJesterRating(String unformatted_file, String formatted_file, String regex)
			throws Exception
	{
		BufferedReader br = new BufferedReader(new FileReader(unformatted_file));
		BufferedWriter bw = new BufferedWriter(new FileWriter(formatted_file));
		String line = null;
		while ((line = br.readLine()) != null)
		{
			line = line.replaceAll(regex, Dataset.REGMX);
			bw.write(line + "\n");
		}
		br.close();
		bw.close();
	}

	public static void convertFilmTrustRating() throws Exception
	{
		String line = null;
		Map<String, Integer> usersMap = new HashMap<>();
		BufferedReader br_users = new BufferedReader(new FileReader(
				"D:\\Dropbox\\Coding\\Java_Projects\\HappyCoding\\dataset\\FilmTrust\\users.dat"));
		while ((line = br_users.readLine()) != null)
		{
			String[] data = line.split("::");
			Integer id = new Integer(data[0]);
			String user = data[1];
			usersMap.put(user, id);
		}
		br_users.close();

		Map<String, Integer> itemsMap = new HashMap<>();
		BufferedReader br_items = new BufferedReader(new FileReader(
				"D:\\Dropbox\\Coding\\Java_Projects\\HappyCoding\\dataset\\FilmTrust\\items.dat"));
		line = null;
		while ((line = br_items.readLine()) != null)
		{
			String[] data = line.split("::");
			Integer id = new Integer(data[0]);
			String item = data[1];
			itemsMap.put(item, id);
		}
		br_items.close();

		BufferedReader br_ratings = new BufferedReader(new FileReader(
				"D:\\Dropbox\\Coding\\Java_Projects\\HappyCoding\\dataset\\FilmTrust\\ratings.dat"));
		BufferedWriter bw_ratings = new BufferedWriter(new FileWriter(
				"D:\\Dropbox\\Coding\\Java_Projects\\HappyCoding\\dataset\\FilmTrust\\ratings.txt"));
		line = null;
		while ((line = br_ratings.readLine()) != null)
		{
			String[] data = line.split("::");
			String user = data[0];
			String item = data[1];
			String rating = data[2];

			int userId = usersMap.get(user);
			int itemId = itemsMap.get(item);
			bw_ratings.write(userId + Dataset.REGMX + itemId + Dataset.REGMX + rating + "\n");
		}
		br_ratings.close();
		bw_ratings.close();

		BufferedReader br_trust = new BufferedReader(new FileReader(
				"D:\\Dropbox\\Coding\\Java_Projects\\HappyCoding\\dataset\\FilmTrust\\trust.dat"));
		BufferedWriter bw_trust = new BufferedWriter(new FileWriter(
				"D:\\Dropbox\\Coding\\Java_Projects\\HappyCoding\\dataset\\FilmTrust\\trust.txt"));
		line = null;
		while ((line = br_trust.readLine()) != null)
		{
			String[] data = line.split("::");
			String trustor = data[0];
			String trustee = data[1];
			String rating = data[2];

			Integer trustorId = usersMap.get(trustor);

			if (trustorId == null)
			{
				trustorId = usersMap.keySet().size() + 1;
				usersMap.put(trustor, trustorId);
			}

			Integer trusteeId = usersMap.get(trustee);
			if (trusteeId == null)
			{
				trusteeId = usersMap.keySet().size() + 1;
				usersMap.put(trustee, trusteeId);
			}

			bw_trust.write(trustorId + Dataset.REGMX + trusteeId + Dataset.REGMX + rating + "\n");
		}
		br_trust.close();
		bw_trust.close();

		BufferedWriter bw_users = new BufferedWriter(new FileWriter(
				"D:\\Dropbox\\Coding\\Java_Projects\\HappyCoding\\dataset\\FilmTrust\\users.dat2"));
		line = null;
		for (Entry<String, Integer> en : usersMap.entrySet())
		{
			String user = en.getKey();
			int id = en.getValue();

			bw_users.write(id + "::" + user + "\n");
		}
		bw_users.close();
	}

	public static void convertItems() throws Exception
	{
		String dirPath1 = "D:\\Data Sets\\Netflix\\netflix\\download\\ratings_items";
		String dirPath2 = "D:\\Data Sets\\Netflix\\netflix\\download\\ratings_users\\ratings_";
		File dir1 = new File(dirPath1);
		File[] files = dir1.listFiles();
		for (File file : files)
		{
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line = null;
			while ((line = br.readLine()) != null)
			{
				String[] data = line.split(Dataset.REGMX);
				int user = Integer.parseInt(data[0]);

				FileWriter bw = new FileWriter(dirPath2 + user + ".txt", true);
				bw.write(line + "\n");
				bw.close();
			}

			br.close();
		}
	}

	public static void combineNetflix() throws Exception
	{
		String dirPath = "D:\\Data Sets\\Netflix\\netflix\\download\\1000";
		File dir = new File(dirPath);
		File[] files = dir.listFiles();
		String dest = "D:\\Data Sets\\Netflix\\netflix\\download\\1000\\" + Dataset.RATING_SET;
		BufferedWriter bw = new BufferedWriter(new FileWriter(dest, true));
		for (File file : files)
		{
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line = null;
			while ((line = br.readLine()) != null)
				bw.write(line + "\n");

			br.close();
		}
		bw.close();
	}

	public static void convertNetflix(String[] args) throws Exception
	{
		String dirPath = "D:\\Data Sets\\Netflix\\netflix\\download\\training_set";
		File dir = new File(dirPath);
		File[] files = dir.listFiles();
		String dest = "D:\\Data Sets\\Netflix\\netflix\\download\\ratings\\ratings_";

		for (File file : files)
		{
			BufferedReader br = new BufferedReader(new FileReader(file));
			BufferedWriter bw = null;
			String line = null;
			int item = 0;
			while ((line = br.readLine()) != null)
			{
				if (line.endsWith(":"))
				{
					item = Integer.parseInt(line.split(":")[0]);
					bw = new BufferedWriter(new FileWriter(dest + item + ".txt"));
				} else
				{
					String[] data = line.split(",");
					int user = Integer.parseInt(data[0]);
					int rating = Integer.parseInt(data[1]);

					String msg = user + " " + item + " " + rating + "\n";
					bw.write(msg);
				}
			}

			br.close();
			bw.close();
		}

	}

	/**
	 * Sampling Netflix only containing a fix number of users, each rated at
	 * least a fix number of items
	 * 
	 * @param users
	 *            the number of users
	 * @param items
	 *            the number of items for each user
	 * @throws Exception
	 */
	public static void samplingDataset(int users) throws Exception
	{
		ConfigParams.defaultInstance();

		String source = Dataset.DIRECTORY + Dataset.RATING_SET;
		String dest = Systems.getDesktop() + Dataset.RATING_SET + "." + users;

		Map<String, Map<String, Rating>> userRatingsMap = Dataset.loadRatingSet(source);

		Set<String> userSet = userRatingsMap.keySet();

		int[] indexArray = Randoms.nextNoRepeatIntArray(users, 1, userSet.size(), null);

		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dest)));
		int count = 0;
		int index = 0;
		for (String user : userSet)
		{
			if (count == indexArray[index])
			{
				Map<String, Rating> rs = userRatingsMap.get(user);
				for (Rating r : rs.values())
				{
					String msg = r.getUserId() + Dataset.REGMX + r.getItemId() + Dataset.REGMX + r.getRating() + "\n";
					bw.write(msg);
				}
				index++;
				if (index >= indexArray.length) break;
			}
			count++;
		}

		Logs.debug("index = " + index + " \t count = " + count);
		bw.close();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void samplingDatasetByItems(int num_users, int num_items) throws Exception
	{
		String source = Dataset.DIRECTORY + Dataset.RATING_SET;
		String dirPath = Dataset.DIRECTORY + "Sample_" + num_items + "_items/";
		String dest = dirPath + Dataset.RATING_SET;

		/* Step 1: sample items-related user ratings */
		Map[] data = Dataset.loadTrainSet(source);
		Map<String, Map<String, Rating>> userRatingsMap = data[0];
		Map<String, Map<String, Rating>> itemRatingsMap = data[1];

		List<String> itemSet = new ArrayList<>(itemRatingsMap.keySet());
		int[] itemArray = Randoms.nextNoRepeatIntArray(num_items, 1, itemSet.size(), null);
		List<String> items = new ArrayList<>();
		for (int id : itemArray)
			items.add(itemSet.get(id));

		List<String> users = new ArrayList<>();
		for (String user : userRatingsMap.keySet())
		{
			Map<String, Rating> itemRatings = userRatingsMap.get(user);
			int count = 0;
			for (String item : items)
			{
				if (itemRatings.containsKey(item)) count++;
				if (count >= 5)
				{
					users.add(user);
					break;
				}
			}
		}

		List<String> userSet = null;
		if (num_users < users.size())
		{
			userSet = new ArrayList<>();
			int[] userArray = Randoms.nextNoRepeatIntArray(num_users, 1, users.size(), null);

			for (int id : userArray)
				userSet.add(users.get(id));
		} else
		{
			userSet = users;
		}

		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dest)));
		int count = 0;
		for (String user : userSet)
		{
			Map<String, Rating> itemRatings = userRatingsMap.get(user);
			for (String item : items)
			{
				if (itemRatings.containsKey(item))
				{
					Rating r = itemRatings.get(item);
					String msg = r.getUserId() + Dataset.REGMX + r.getItemId() + Dataset.REGMX + r.getRating() + "\n";
					bw.write(msg);
					count++;
				}
			}
		}

		Logs.debug("Retrieved users: " + userSet.size() + ", items: " + items.size() + ", ratings: " + count);
		Logs.debug("Saved the rating sample to: " + dest);
		bw.close();
	}

	@Test
	public void sampleByItems() throws Exception
	{
		int num_users = 3000;
		int num_items = 2000;

		ConfigParams.defaultInstance();
		String dirPath = Dataset.DIRECTORY + "Sample_" + num_items + "_items/";
		FileIO.deleteDirectory(dirPath);
		FileIO.makeDirectory(dirPath);

		samplingDatasetByItems(num_users, num_items);

		String trustPath = Dataset.DIRECTORY + Dataset.TRUST_SET;
		retrieveTrustData(dirPath, trustPath);

		splitKFoldDataset(dirPath);

		Logs.debug("Data sampling is done!");
	}

	@Test
	public void sampleByUsers() throws Exception
	{
		samplingDataset(5000);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void samplingJester(int users, int items) throws Exception
	{
		ConfigParams.defaultInstance();
		String source = Dataset.DIRECTORY + Dataset.RATING_SET;
		String dest = "C:/Users/guoguibing/Desktop/ratings.txt";

		Map[] maps = Dataset.loadTrainSet(source);
		Map<Integer, List<Rating>> userRatingsMap = maps[0];
		Map<Integer, List<Rating>> data = new HashMap<>();

		for (Entry<Integer, List<Rating>> en : userRatingsMap.entrySet())
		{
			List<Rating> rs = en.getValue();
			if (rs.size() > items)
			{
				data.put(en.getKey(), rs);
			}
		}
		Set<Integer> userSet = data.keySet();
		int indexArray[] = Randoms.nextNoRepeatIntArray(users, 0, userSet.size(), null);

		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dest)));
		int count = 0;
		int index = 0;
		for (int user : userSet)
		{
			if (count == indexArray[index])
			{
				List<Rating> rs = userRatingsMap.get(user);
				for (Rating r : rs)
				{
					String msg = r.getUserId() + Dataset.REGMX + r.getItemId() + Dataset.REGMX + r.getRating() + "\n";
					bw.write(msg);
				}
				index++;
				if (index >= indexArray.length) break;
			}
			count++;
		}

		System.out.println("index = " + index + " \t count = " + count);
		bw.close();
	}

	@Test
	public void samplingNetflix() throws Exception
	{
		ConfigParams.defaultInstance();

		String source = Dataset.DIRECTORY + Dataset.RATING_SET;
		String dest = Dataset.DIRECTORY + "Sample" + Systems.FILE_SEPARATOR + Dataset.RATING_SET;
		FileIO.deleteFile(dest);

		BufferedReader br = new BufferedReader(new FileReader(new File(source)));
		String line = null;
		List<String> lines = new ArrayList<>(3000);
		int numUser = 1000;
		while ((line = br.readLine()) != null)
		{
			String[] data = line.split(" ");
			int userId = Integer.parseInt(data[0]);

			if (userId > numUser) continue;
			lines.add(line);

			if (lines.size() > 1024)
			{
				FileIO.writeList(dest, lines, null, true);
				lines.clear();
			}
		}
		br.close();

		if (lines.size() > 0) FileIO.writeList(dest, lines, null, true);
	}

	/**
	 * Sampling Netflix with each user only rated a few no. of ratings
	 * 
	 * @param mean
	 *            desired number of ratings per user
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void samplingByMean(String src, int mean) throws Exception
	{
		ConfigParams.defaultInstance();
		String source = src;
		if (source == null) source = Dataset.DIRECTORY + Dataset.RATING_SET;

		String dirStr = "C:/Users/guoguibing/Desktop/" + Dataset.LABEL + "/" + mean + "/";
		File dir = new File(dirStr);
		if (!dir.exists()) dir.mkdirs();

		Map[] maps = Dataset.loadTrainSet(source);
		Map<Integer, List<Rating>> userRatingsMap = maps[0];
		Map<Integer, List<Rating>> itemRatingsMap = maps[1];
		double average = 0.0;
		double sum = 0.0;
		for (Entry<Integer, List<Rating>> en : itemRatingsMap.entrySet())
		{
			List<Rating> rs = en.getValue();
			sum += rs.size();
		}
		average = sum / itemRatingsMap.size();

		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dirStr + Dataset.RATING_SET)));

		for (Entry<Integer, List<Rating>> en : userRatingsMap.entrySet())
		{
			List<Rating> rs = en.getValue();
			int size = rs.size();
			int save = (int) (mean * size / average + 0.5);

			int index[] = Randoms.nextNoRepeatIntArray(save, 0, size, null);

			for (int item : index)
			{
				Rating r = rs.get(item);
				String msg = r.getUserId() + Dataset.REGMX + r.getItemId() + Dataset.REGMX + r.getRating() + "\n";
				bw.write(msg);
			}
		}

		bw.close();

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void checkRatingsSpecification(String ratings_set) throws Exception
	{
		Map[] maps = Dataset.loadTrainSet(ratings_set);
		HashMap<Integer, List<Rating>> userRatingsMap = (HashMap<Integer, List<Rating>>) maps[0];

		int count_0 = 0, count_1 = 0, count_2 = 0, count_3 = 0, count_4 = 0, count_5 = 0, count_6 = 0, count_7 = 0, count_8 = 0, count_9 = 0, count = 0;
		int count_00 = 0, count_10 = 0, count_20 = 0, count_30 = 0, count_40 = 0, count_50 = 0, count_60 = 0, count_70 = 0, count_80 = 0, count_90 = 0;

		List<Double> sizes = new ArrayList<>();
		for (int i = 0; i < 63974 - userRatingsMap.size(); i++)
			sizes.add(0.0);

		for (Entry<Integer, List<Rating>> en : userRatingsMap.entrySet())
		{
			List<Rating> ratings = en.getValue();
			sizes.add((double) ratings.size());

			for (Rating r : ratings)
			{
				count++;
				switch ((int) (r.getRating()))
				{
					case -1:
						count_10++;
						break;
					case -2:
						count_20++;
						break;
					case -3:
						count_30++;
						break;
					case -4:
						count_40++;
						break;
					case -5:
						count_50++;
						break;
					case -6:
						count_60++;
						break;
					case -7:
						count_70++;
						break;
					case -8:
						count_80++;
						break;
					case -9:
						count_90++;
						break;
					case 0:
						if (r.getRating() < 0) count_00++;
						else count_0++;
						break;
					case 1:
						count_1++;
						break;
					case 2:
						count_2++;
						break;
					case 3:
						count_3++;
						break;
					case 4:
						count_4++;
						break;
					case 5:
						count_5++;
						break;
					case 6:
						count_6++;
						break;
					case 7:
						count_7++;
						break;
					case 8:
						count_8++;
						break;
					case 9:
						count_9++;
						break;
				}
			}
		}

		double[] da = Lists.toArray(sizes);

		System.out.println("Mean ratings per user = " + Stats.mean(da) + ", std = " + Stats.sd(sizes) + ", max = "
				+ Stats.max(da)[0] + ", min = " + Stats.min(da)[0]);

		HashMap<Integer, List<Rating>> itemRatingsMap = (HashMap<Integer, List<Rating>>) maps[1];
		List<Double> itemSizes = new ArrayList<>();
		double item0 = 150 - itemRatingsMap.size();
		for (int i = 0; i < item0; i++)
			itemSizes.add(0.0);
		for (Entry<Integer, List<Rating>> en : itemRatingsMap.entrySet())
		{
			List<Rating> rs = en.getValue();
			itemSizes.add((double) rs.size());
		}

		double[] dai = Lists.toArray(itemSizes);
		System.out.println("Mean ratings per item = " + Stats.mean(dai) + ", std = " + Stats.sd(itemSizes) + ", max = "
				+ Stats.max(dai)[0]);

		System.out.println("Ratio(r=-9) = " + count_90 + "/" + count + " = " + (count_90 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=-8) = " + count_80 + "/" + count + " = " + (count_80 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=-7) = " + count_70 + "/" + count + " = " + (count_70 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=-6) = " + count_60 + "/" + count + " = " + (count_60 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=-5) = " + count_50 + "/" + count + " = " + (count_50 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=-4) = " + count_40 + "/" + count + " = " + (count_40 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=-3) = " + count_30 + "/" + count + " = " + (count_30 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=-2) = " + count_20 + "/" + count + " = " + (count_20 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=-1) = " + count_10 + "/" + count + " = " + (count_10 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=-0) = " + count_00 + "/" + count + " = " + (count_00 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=0) = " + count_0 + "/" + count + " = " + (count_0 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=1) = " + count_1 + "/" + count + " = " + (count_1 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=2) = " + count_2 + "/" + count + " = " + (count_2 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=3) = " + count_3 + "/" + count + " = " + (count_3 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=4) = " + count_4 + "/" + count + " = " + (count_4 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=5) = " + count_5 + "/" + count + " = " + (count_5 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=6) = " + count_6 + "/" + count + " = " + (count_6 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=7) = " + count_7 + "/" + count + " = " + (count_7 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=8) = " + count_8 + "/" + count + " = " + (count_8 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(r=9) = " + count_9 + "/" + count + " = " + (count_9 + 0.0) * 100.0 / count + "%");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void checkTrustSpecification(String trust_set) throws Exception
	{
		Map[] maps = loadTrustSet2(trust_set);
		Map<Integer, List<Integer>> userTrustees = maps[0];
		int count_1 = 0, count_2 = 0, count_3 = 0, count_4 = 0, count_5 = 0, count_6 = 0, count_7 = 0, count_others = 0;
		int count_11 = 0, count_22 = 0, count_33 = 0, count_44 = 0, count_55 = 0, count_66 = 0, count_77 = 0, count_others8 = 0;

		int count = userTrustees.size();
		int total = 0;
		List<Double> trusteeSizes = new ArrayList<>();
		for (Entry<Integer, List<Integer>> en : userTrustees.entrySet())
		{
			List<Integer> trustees = en.getValue();
			int size = trustees.size();
			total += size;
			trusteeSizes.add((double) size);

			switch (size)
			{
				case 1:
					count_1++;
					count_11 += size;
					break;
				case 2:
					count_2++;
					count_22 += size;
					break;
				case 3:
					count_3++;
					count_33 += size;
					break;
				case 4:
					count_4++;
					count_44 += size;
					break;
				case 5:
					count_5++;
					count_55 += size;
					break;
				case 6:
					count_6++;
					count_66 += size;
					break;
				case 7:
					count_7++;
					count_77 += size;
					break;
				default:
					count_others++;
					count_others8 += size;
					break;
			}
		}

		System.out.println("Trustors No. = " + count);
		System.out.println("Ratio(t=1) = " + count_1 + "/" + count + " = " + (count_1 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(t=2) = " + count_2 + "/" + count + " = " + (count_2 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(t=3) = " + count_3 + "/" + count + " = " + (count_3 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(t=4) = " + count_4 + "/" + count + " = " + (count_4 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(t=5) = " + count_5 + "/" + count + " = " + (count_5 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(t=6) = " + count_6 + "/" + count + " = " + (count_6 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(t=7) = " + count_7 + "/" + count + " = " + (count_7 + 0.0) * 100.0 / count + "%");
		System.out.println("Ratio(t>7) = " + count_others + "/" + count + " = " + (count_others + 0.0) * 100.0 / count
				+ "%");

		System.out.println();
		System.out.println("Ratio(t=1) = " + count_11 + "/" + total + " = " + (count_11 + 0.0) * 100.0 / total + "%");
		System.out.println("Ratio(t=2) = " + count_22 + "/" + total + " = " + (count_22 + 0.0) * 100.0 / total + "%");
		System.out.println("Ratio(t=3) = " + count_33 + "/" + total + " = " + (count_33 + 0.0) * 100.0 / total + "%");
		System.out.println("Ratio(t=4) = " + count_44 + "/" + total + " = " + (count_44 + 0.0) * 100.0 / total + "%");
		System.out.println("Ratio(t=5) = " + count_55 + "/" + total + " = " + (count_55 + 0.0) * 100.0 / total + "%");
		System.out.println("Ratio(t=6) = " + count_66 + "/" + total + " = " + (count_66 + 0.0) * 100.0 / total + "%");
		System.out.println("Ratio(t=7) = " + count_77 + "/" + total + " = " + (count_77 + 0.0) * 100.0 / total + "%");
		System.out.println("Ratio(t>7) = " + count_others8 + "/" + total + " = " + (count_others8 + 0.0) * 100.0
				/ total + "%");

		double[] da = Lists.toArray(trusteeSizes);
		System.out.println("Mean trustees per trustor = " + Stats.mean(da) + ", std = " + Stats.sd(trusteeSizes)
				+ ", max = " + Stats.max(da)[0]);

		List<Double> trustorSizes = new ArrayList<>();
		Map<Integer, List<Integer>> userTrustors = maps[1];
		for (Entry<Integer, List<Integer>> en : userTrustors.entrySet())
		{
			List<Integer> trustors = en.getValue();
			int size = trustors.size();
			trustorSizes.add((double) size);
		}
		double[] dao = Lists.toArray(trustorSizes);
		System.out.println("Mean trustors per trustee = " + Stats.mean(dao) + ", std = " + Stats.sd(trustorSizes)
				+ ", max = " + Stats.max(dao)[0]);
	}

	@SuppressWarnings("rawtypes")
	public static Map[] loadTestSet(String testSet, List<Rating> ratings) throws Exception
	{
		Map<String, Map<String, Rating>> userMap = new HashMap<>();
		Map<String, Map<String, Rating>> itemMap = new HashMap<>();

		BufferedReader fr = new BufferedReader(new FileReader(testSet));
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

			ratings.add(r);

			Map<String, Rating> itemRatings = null;
			if (userMap.containsKey(userId)) itemRatings = userMap.get(userId);
			else itemRatings = new HashMap<>();

			itemRatings.put(itemId, r);
			userMap.put(userId, itemRatings);

			Map<String, Rating> userRatings = null;
			if (itemMap.containsKey(itemId)) userRatings = itemMap.get(itemId);
			else userRatings = new HashMap<>();

			userRatings.put(userId, r);
			itemMap.put(itemId, userRatings);
		}
		fr.close();

		return new Map[] { userMap, itemMap };
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void splitFoldTrustSets() throws Exception
	{
		ConfigParams.defaultInstance();
		String ratings_file = Dataset.DIRECTORY + Dataset.RATING_SET;
		String trust_file = "dataset\\Epinions\\trust.txt";

		Map[] ratingsMap = Dataset.loadTrainSet(ratings_file);
		Map<Integer, List<Rating>> userRatingsMap = ratingsMap[0];

		Set<Integer> users = userRatingsMap.keySet();
		BufferedReader br = new BufferedReader(new FileReader(new File(trust_file)));
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File("C:\\Users\\guoguibing\\Desktop\\trust.txt")));
		String line = null;
		while ((line = br.readLine()) != null)
		{
			String[] data = line.split(Dataset.REGMX);
			Integer trustor = new Integer(data[0]);
			Integer trustee = new Integer(data[1]);

			if (users.contains(trustor) && users.contains(trustee))
			{
				bw.write(line + "\n");
			}
		}
		bw.close();
		br.close();
	}

	/**
	 * Split a data set into k folds
	 * 
	 * @throws Exception
	 */
	// @Ignore
	public static void splitKFoldDataset(String dirPath) throws Exception
	{
		ConfigParams.defaultInstance();
		String ratings_file = dirPath + Dataset.RATING_SET;
		String tempDir = Systems.getDesktop();

		Dataset.loadTrainSet(ratings_file);

		int totalAmount = Dataset.size;

		int kfold = 5;
		int size = totalAmount / kfold;
		int[] exceptions = new int[totalAmount];
		int[] exs = new int[totalAmount];
		int count = 0;
		for (int k = 0; k < kfold; k++)
		{
			logger.debug("Current step k = " + (k + 1));
			int[] indexArray = null;
			if (k < kfold - 1)
			{
				indexArray = Randoms.nextNoRepeatIntArray(size, 1, totalAmount + 1, exceptions);
				for (int index : indexArray)
				{
					exceptions[count++] = index;
					exs[index - 1] = index;
				}
				Arrays.sort(exceptions);
			} else if (k == kfold - 1)
			{
				count = 0;
				indexArray = new int[totalAmount - ((kfold - 1) * size)];
				for (int i = 0; i < exs.length; i++)
				{
					if (exs[i] == 0)
					{
						indexArray[count++] = i + 1;
					}
				}
			}

			BufferedReader br = new BufferedReader(new FileReader(new File(ratings_file)));
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(tempDir + "k" + (k + 1))));
			String line = null;
			int cLine = 0;
			int iCount = 0;
			while ((line = br.readLine()) != null)
			{
				cLine++;
				if (iCount >= indexArray.length) break;
				if (cLine == indexArray[iCount])
				{
					iCount++;
					bw.write(line + "\n");
				}
			}
			bw.flush();
			bw.close();
			br.close();
			logger.debug("[Temporary] Write to " + tempDir + "k" + (k + 1));
		}

		/* combine into 5 folds */
		String dest = FileIO.makeDirPath(dirPath, kfold + "fold");
		// clean destination directory first
		FileIO.deleteDirectory(dest);
		FileIO.makeDirectory(dest);

		for (int i = 0; i < kfold; i++)
		{
			String base = dest + "u" + (i + 1) + ".base";
			String test = dest + "u" + (i + 1) + ".test";

			for (int j = 0; j < kfold; j++)
			{
				String source = tempDir + "k" + (j + 1);

				if (i == j) FileIO.copyFile(source, test);
				else FileIO.writeString(base, FileIO.readAsString(source), true);
			}
			logger.debug("[Finish] Write to " + test);
			logger.debug("[Finish] Write to " + base);
		}

		/* cope trust to k-fold directory */
		FileIO.copyFile(dirPath + Dataset.TRUST_SET, dest + Dataset.TRUST_SET);

		/* clean temporary data */
		for (int i = 0; i < kfold; i++)
		{
			String source = tempDir + "k" + (i + 1);
			FileIO.deleteFile(source);
		}
	}

	public static void retrieveTrustData(String dirPath, String trustPath) throws Exception
	{
		ConfigParams.defaultInstance();
		String ratingSet = dirPath + Dataset.RATING_SET;
		Map<String, Map<String, Rating>> userMap = Dataset.loadRatingSet(ratingSet);

		BufferedReader br = new BufferedReader(new FileReader(trustPath));
		StringBuilder sb = new StringBuilder();
		String line = null;

		while ((line = br.readLine()) != null)
		{
			if (line.isEmpty()) continue;

			String[] data = line.split(Dataset.REGMX);
			String trustor = data[0];
			String trustee = data[1];

			if (userMap.containsKey(trustor) && userMap.containsKey(trustee)) sb.append(line + "\n");
		}
		br.close();

		String filePath = dirPath + Dataset.TRUST_SET;
		FileIO.writeString(filePath, sb.toString());
		Logs.debug("Saved the trust sample to: " + filePath);
	}

	public static void convertBookCrossing() throws Exception
	{
		String dirPath = "D:\\Dropbox\\PhD\\My Work\\Experiments\\Data Sets\\Recommender System Data Set\\Book-Crossing\\";
		String ratingSet = dirPath + "ratings-all.txt";
		String explicitSet = dirPath + "ratings-explicit.txt";
		String implicitSet = dirPath + "ratings-implicit.txt";

		List<String> exList = FileIO.readAsList(ratingSet, new Converter<String, String>() {

			@Override
			public String transform(String line)
			{
				String[] data = line.split(" ");
				int rating = Integer.parseInt(data[2]);
				if (rating > 0) return line;
				else return null;
			}
		});

		List<String> imList = FileIO.readAsList(ratingSet, new Converter<String, String>() {

			@Override
			public String transform(String line)
			{
				String[] data = line.split(" ");
				int rating = Integer.parseInt(data[2]);
				if (rating == 0) return line;
				else return null;
			}
		});

		FileIO.writeList(explicitSet, exList);
		FileIO.writeList(implicitSet, imList);

	}

	@Test
	public void resampleTrust() throws Exception
	{
		ConfigParams.defaultInstance();
		String trustSet = Dataset.DIRECTORY + Dataset.TRUST_SET;
		Map<String, Map<String, Double>> userTNsMap = DatasetUtils.loadTrustSet(trustSet);
		Map<String, Map<String, Double>> tnsMap = new HashMap<>();

		int size = 5;
		for (String user : userTNsMap.keySet())
		{
			Map<String, Double> tns = userTNsMap.get(user);

			if (tns != null && tns.size() > size)
			{
				tnsMap.put(user, tns);
			}
		}

		// print out
		String dir = Systems.getDesktop();
		String path = dir + Dataset.TRUST_SET;
		FileIO.writeMap(path, tnsMap, new MapWriter<String, Map<String, Double>>() {

			@Override
			public String processEntry(String key, Map<String, Double> val)
			{
				StringBuilder sb = new StringBuilder();

				int i = 0;
				for (Entry<String, Double> en : val.entrySet())
				{
					i++;
					String line = key + " " + en.getKey() + " " + en.getValue();
					if (i < val.size()) line += "\n";
					sb.append(line);
				}

				return sb.toString();
			}
		}, false);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void resampleRatings() throws Exception
	{
		ConfigParams.defaultInstance();
		String trustSet = Dataset.DIRECTORY + "\\Resample-5\\" + Dataset.TRUST_SET;
		String ratingSet = Dataset.DIRECTORY + Dataset.RATING_SET;

		Map<String, Map<String, Double>> userTNsMap = DatasetUtils.loadTrustSet(trustSet);
		Map<String, Map<String, Rating>> userRatingsMap = Dataset.loadTrainSet(ratingSet)[0];
		Map<String, Map<String, Rating>> ursMap = new HashMap<>();

		for (String user : userTNsMap.keySet())
		{
			if (userRatingsMap.containsKey(user))
			{
				Map<String, Rating> rs = userRatingsMap.get(user);

				ursMap.put(user, rs);
			}
		}

		// print out
		String path = Dataset.DIRECTORY + "\\Resample-5\\" + Dataset.RATING_SET;
		FileIO.writeMap(path, ursMap, new MapWriter<String, Map<String, Rating>>() {

			@Override
			public String processEntry(String key, Map<String, Rating> val)
			{
				StringBuilder sb = new StringBuilder();

				int i = 0;
				for (Entry<String, Rating> en : val.entrySet())
				{
					i++;
					Rating r = en.getValue();
					String line = r.getUserId() + " " + r.getItemId() + " " + r.getRating();
					if (i < val.size()) line += "\n";
					sb.append(line);
				}

				return sb.toString();
			}
		}, false);
	}

}
