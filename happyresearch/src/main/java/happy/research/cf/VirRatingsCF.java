package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.FileIO.Converter;
import happy.coding.math.Stats;
import happy.coding.system.Systems;
import happy.research.cf.ConfigParams.PredictMethod;
import happy.research.utils.SimUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

public class VirRatingsCF extends DefaultCF_mt
{
	// {user, {item, confidence}}
	protected static Map<String, Map<String, Rating>>	userConfidencesMap;
	// {item, {user, confidence}}
	protected static Map<String, Map<String, Rating>>	itemConfidencesMap;

	private Performance									pf2					= new Performance("Virtual Enabled");

	public static boolean								auto				= !true;
	private static boolean								defaultConf			= true;
	private static boolean								virEnabled			= true;
	private static boolean								isVR				= !true;

	public final static int								PhyRatingUpbound	= 1356;
	protected final static double						conf_ws				= 0.6595;
	protected final static double						conf_vr				= 0.7556;
	protected final static double						conf_pr				= 0.5;									//0.5
	//private final static double							conf_default		= isVR ? conf_vr : conf_ws;
	private final static double							conf_default		= conf_pr;

	public static List<Integer>							userIds				= null;

	public VirRatingsCF()
	{
		methodId = "VirRatings-CF";
	}

	@Override
	protected void loadDataset() throws Exception
	{
		load_confidences();

		Dataset.RATING_SET = "Ratings" + Systems.FILE_SEPARATOR + (isVR ? "ratings-all-vr.txt" : "ratings-all-ws.txt");

		super.loadDataset();

		// Dataset.printSpecs();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void load_confidences() throws Exception
	{ 
		if (userConfidencesMap == null)
		{
			String confidenceSet = Dataset.DIRECTORY + "Confidences" + Systems.FILE_SEPARATOR
					+ (isVR ? "confidences-all-vr.txt" : "confidences-all-ws.txt");
			logger.debug("Loading confidence data {}", confidenceSet);

			Map[] data = Dataset.loadTrainSet(confidenceSet);
			userConfidencesMap = data[0];
			itemConfidencesMap = data[1];

			logger.debug("Done!");
		}
	}

	@Override
	protected void prepTestRatings()
	{
		logger.debug("Preparing test-rating data ...");
		if (testRatings == null)
		{
			// only physical ratings needs to be tested.
			testRatings = new ArrayList<>();

			for (Entry<String, Map<String, Rating>> entry : userRatingsMap.entrySet())
			{
				String user = entry.getKey();

				Map<String, Rating> val = entry.getValue();
				if (Integer.parseInt(user) <= PhyRatingUpbound && val.size() > 0)
				{
					switch (params.DATASET_MODE)
					{
						case all:
							testRatings.addAll(val.values());
							break;
						case coldUsers:
							if (val.size() < 5) testRatings.addAll(val.values());
							break;
						default:
							break;
					}

				}
			}

		}
		logger.debug("Done!");
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		if (virEnabled) printSettings.add("Virtual ratings enabled");
		else printSettings.add("Physical ratings only");

		String dirPath = Dataset.DIRECTORY;
		String filename = "predictable.txt";

		List<Rating> predictRatings = null;
		if (!virEnabled) predictRatings = new ArrayList<>();
		else predictRatings = FileIO.readAsList(dirPath + filename, new Converter<String, Rating>() {

			@Override
			public Rating transform(String line)
			{
				String[] data = line.split(" ");
				Rating r = new Rating();
				r.setUserId(data[0]);
				r.setItemId(data[1]);
				r.setRating(Double.parseDouble(data[2]));
				return r;
			}
		});

		// only 199 test ratings, hence no need to split
		for (Rating testRating : testRatings)
		{
			String testUser = testRating.getUserId();
			String testItem = testRating.getItemId();

			Map<String, Rating> asRatings = userRatingsMap.get(testUser);
			if (asRatings.size() <= 1) continue;

			// mean of user A
			double meanA = 0;
			if (params.PREDICT_METHOD == PredictMethod.resnick_formula)
			{
				meanA = RatingUtils.mean(asRatings, testRating);
				if (Double.isNaN(meanA)) continue;
			}

			// find similar users
			Map<String, Double> nnSims = new HashMap<>();
			Map<String, Double> nnRatings = new HashMap<>();

			for (String nn : userRatingsMap.keySet())
			{
				if (nn.equals(testUser)) continue;
				if (!virEnabled && Integer.parseInt(nn) > PhyRatingUpbound) continue; // physical ratings only

				Map<String, Rating> bsRatings = userRatingsMap.get(nn);
				if (bsRatings == null) continue;
				if (!bsRatings.containsKey(testItem)) continue;
				double bsRating = bsRatings.get(testItem).getRating();

				List<Double> a = new ArrayList<>();
				List<Double> b = new ArrayList<>();
				List<Double> ac = new ArrayList<>();
				List<Double> bc = new ArrayList<>();
				for (Entry<String, Rating> en : asRatings.entrySet())
				{
					String item = en.getKey();
					Rating ar = en.getValue();
					if (ar == testRating) continue;

					if (bsRatings.containsKey(item))
					{
						a.add(ar.getRating());
						b.add(bsRatings.get(item).getRating());

						boolean test = defaultConf && Integer.parseInt(nn) > PhyRatingUpbound;

						ac.add(test ? conf_default : userConfidencesMap.get(testUser).get(item).getRating());
						bc.add(test ? conf_default : userConfidencesMap.get(nn).get(item).getRating());
					}
				}

				if (a.size() < 1) continue;

				double sim = SimUtils.distanceSim(a, b, ac, bc);
				//Logs.debug(Strings.toString1L(a) + "," + Strings.toString1L(b) + ", " + sim);
				if (sim > params.SIMILARITY_THRESHOLD)
				{
					nnSims.put(nn, sim);
					nnRatings.put(nn, bsRating);
				}
			}

			// do predictions
			if (nnSims.size() > 0)
			{
				double sum = 0, sumC = 0;
				double weights = 0.0;
				for (Entry<String, Double> en : nnSims.entrySet())
				{
					String nn = en.getKey();
					double sim = en.getValue();

					double bsConf = 0.0;
					if (defaultConf && Integer.parseInt(nn) > PhyRatingUpbound) bsConf = conf_default;
					else bsConf = userConfidencesMap.get(nn).get(testItem).getRating();

					double bsRating = nnRatings.get(nn);
					boolean combineWithConf = !true;
					if (combineWithConf) bsRating *= bsConf;

					double meanB = 0.0;
					if (params.PREDICT_METHOD == PredictMethod.resnick_formula)
					{
						Map<String, Rating> bsRatings = userRatingsMap.get(nn);
						meanB = RatingUtils.mean(bsRatings, null);
						if (Double.isNaN(meanB)) continue;
					}

					double weight = sim;
					sum += weight * (bsRating - meanB);
					sumC += sim * bsConf;
					weights += Math.abs(weight);
				}

				if (weights <= 0.0) continue;
				double confidence = sumC / weights;
				double prediction = meanA + sum / weights;

				Prediction pred = new Prediction(testRating, prediction);
				pred.setConf(confidence);

				pf.addPredicts(pred);

				if (!virEnabled) predictRatings.add(testRating);
				else
				{
					for (Rating test : predictRatings)
					{
						String u = test.getUserId();
						String i = test.getItemId();

						if (u.equals(testUser) && i.equals(testItem))
						{
							Prediction pred2 = new Prediction(testRating, prediction);
							pred2.setConf(confidence);

							pf2.addPredicts(pred2);
							break;
						}
					}
				}

			}
		}

		if (!virEnabled)
		{
			// output testable physical ratings when physical ratings only
			FileIO.writeList(dirPath + filename, predictRatings, new Converter<Rating, String>() {

				@Override
				public String transform(Rating t)
				{
					return t.getUserId() + " " + t.getItemId() + " " + t.getRating();
				}

			}, false);
		}
		return pf;
	}

	@Override
	protected void printPerformance(Performance pf)
	{
		// super.printPerformance(pf);
		logger.info("------------------ {} ------------------", pf.getMethod(), params.DATASET_MODE);

		if (printSettings.size() > 0)
		{
			for (String setting : printSettings)
				logger.info(setting);
			logger.info(null);

			printSettings.clear();
		}

		Measures ms = pf.prediction(testUserRatingsMap);
		logger.info("MAE = {}, MACE = {}", (float) ms.getMAE(), (float) ms.getMACE());

		logger.info(null);
		logger.info("" + (float) ms.getMAE());
		logger.info("" + (float) ms.getMACE());
		logger.info("" + (float) ms.getRC() * 100 + "%");
		logger.info(null);

		if (virEnabled)
		{
			Measures ms2 = pf2.prediction(testUserRatingsMap);
			logger.info("Predictable MAE = {}, MACE = {}", (float) ms.getMAE(), (float) ms.getMACE());

			logger.info(null);
			logger.info("" + (float) ms2.getMAE());
			logger.info("" + (float) ms2.getMACE());
		}
	}

	@Override
	protected void init()
	{
		super.init();

		userConfidencesMap = null;
	}

	@Test
	public void center()
	{
		double[] prices = { 4, 4, 2, 2, 4, 3, 4, 4, 4, 4, 3, 3, 2, 5, 3, 4, 2, 5, 5, 4, 3, 5, 1, 2, 3, 2, 3, 3, 3, 3,
				2, 2, 3, 2, 3, 2, 2, 2, 3, 3, 3, 4, 4, 3, 2, 2, 5, 4, 2, 2, 2, 1, 2, 5, 1, 1, 3, 2, 2, 2, 2, 2, 2, 2,
				4, 3, 5, 2, 4, 1, 2, 4, 2, 2, 3, 4, 1, 4, 3, 4, 4, 3, 3, 4, 3, 3, 3, 5, 4, 4, 3, 2, 3, 3, 3, 2, 3, 3,
				3, 4, 3, 3, 4, 4, 5, 4, 5, 4, 3, 3, 5, 3, 4, 4, 4, 4, 4, 3, 3, 3, 5, 4, 4, 3, 4, 4, 4, 3, 4, 3, 2, 4,
				3, 1, 4, 3, 5, 3, 3, 3, 1, 1, 1, 4, 4, 3, 2, 3, 3, 4, 3, 3, 4, 4, 3, 1, 5, 2, 5, 3, 2, 3, 3, 1, 2, 3,
				3, 2, 4, 4, 3, 4, 2, 3, 3, 4, 3, 4, 4, 3, 4, 4, 4, 4, 2, 4, 3, 4, 2, 4, 4, 3, 4, 4, 3, 3, 3, 4, 3, 4,
				3, 3, 3, 3, 4, 3, 3, 5, 4, 5, 4, 4, 4, 4, 5, 2, 2, 4, 4, 4, 5, 3, 5, 3, 3, 3, 3, 3, 3, 3, 3, 1, 5, 3,
				3, 3, 3, 5, 2, 3, 3, 4, 4, 3, 4, 4, 3, 5, 4, 3, 2, 3, 2, 3, 3, 4, 4, 2, 4, 3, 3, 4, 4, 3, 2, 2, 4, 2,
				1, 1, 2, 2, 3, 2, 3, 3, 2, 3, 3, 3, 4, 5, 4, 4, 5, 5, 3, 4, 5, 4, 4, 2, 3, 1, 3, 4, 4, 3, 4, 3, 3, 4,
				3, 5, 3, 3, 2, 3, 2, 2, 5, 2, 1, 3, 3, 3, 3, 3, 2, 3, 3, 5, 4, 4, 4, 4, 3, 4, 4, 3, 4, 4, 2, 3, 4, 5,
				2, 5, 4, 5, 1, 5, 5, 5, 5, 2, 2, 2, 3, 3, 4, 2, 3, 1, 4, 4, 4, 4, 4, 4, 1, 3, 5, 3, 2, 4, 3, 3, 1, 2,
				3, 4, 3, 4, 3, 1, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 4, 5, 4, 4, 3, 4, 4, 3, 2, 4, 5, 4, 4, 2, 4, 4, 4,
				4, 3, 2, 2, 5, 3, 4, 3, 3, 4, 4, 4, 3, 3, 3, 3, 2, 3, 2, 2, 5, 5, 5, 5, 5, 5, 5, 5, 4, };

		double mean = Stats.mean(prices);
		double median = Stats.median(prices);
		double mode = Stats.mode(prices);

		logger.debug("Mean = {}, Median = {}, Mode = {}", new Object[] { mean, median, mode });
	}

}
