package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.io.net.Gmailer;
import happy.coding.math.Randoms;
import happy.coding.math.Stats;
import happy.coding.system.Dates;
import happy.coding.system.Debug;
import happy.coding.system.Systems;
import happy.research.cf.ConfigParams.DatasetMode;
import happy.research.cf.ConfigParams.ValidateMethod;
import happy.research.utils.SimUtils;
import happy.research.utils.SimUtils.SimMethod;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author guoguibing
 */
public abstract class AbstractCF
{
	protected static String								methodId			= null;
	protected static String								current_trust_dir	= null;
	protected static String								current_trust_name	= null;
	protected static int								numRunMethod		= 0;
	protected static ConfigParams						params;

	protected static List<String>						printSettings		= new ArrayList<>();
	protected static List<String>						methodSettings		= new ArrayList<>();

	// @Train {user, {item - rating}}
	protected static Map<String, Map<String, Rating>>	userRatingsMap;

	// @Train {item, {user - rating}}
	protected static Map<String, Map<String, Rating>>	itemRatingsMap;

	// @Test {user, {item - rating}}
	protected static Map<String, Map<String, Rating>>	testUserRatingsMap;

	// @Test {item, {user - rating}}
	protected static Map<String, Map<String, Rating>>	testItemRatingsMap;

	// @Test {test ratings}
	protected static List<Rating>						testRatings;

	// @Train {user, {trust user - trust score}}
	protected static Map<String, Map<String, Double>>	userTNsMap, userDNsMap, userTrustorsMap;

	protected abstract void init();

	protected abstract void loadDataset() throws Exception;

	protected abstract void prepTestRatings();

	protected abstract Performance runRecAlgorithm() throws Exception;

	public void execute() throws Exception
	{
		init();

		if (VirRatingsCF.auto) params.BATCH_RUN = true;
		if (params.auto_trust_sets) params.BATCH_RUN = true;

		if (params.BATCH_RUN)
		{
			batchExecute();
			collectResults();
		} else
		{
			singleExecute();
			// collect results when all single run methods finished
		}

	}

	private void singleExecute() throws Exception
	{
		/* load data from data set */
		loadDataset();

		/* prepare test data in different views */
		prepTestRatings();

		formatTestRatings();

		/* execute recommendation algorithm */
		Performance pf = runRecAlgorithm();

		/* print out the performance */
		printPerformance(pf);
	}

	private void formatTestRatings()
	{
		Logs.debug("Format test ratings ...");

		/* format test ratings */
		testUserRatingsMap = new HashMap<>();
		testItemRatingsMap = new HashMap<>();

		for (Rating r : testRatings)
		{
			String user = r.getUserId();
			String item = r.getItemId();

			Map<String, Rating> irs = null;
			Map<String, Rating> urs = null;

			if (testUserRatingsMap.containsKey(user)) irs = testUserRatingsMap.get(user);
			else irs = new HashMap<>();

			if (testItemRatingsMap.containsKey(item)) urs = testItemRatingsMap.get(item);
			else urs = new HashMap<>();

			irs.put(item, r);
			urs.put(user, r);

			testUserRatingsMap.put(user, irs);
			testItemRatingsMap.put(item, urs);
		}

		Logs.debug("# test users = {}, items = {}, ratings = {}", new Object[] { testUserRatingsMap.size(),
				testItemRatingsMap.size(), testRatings.size() });
		Logs.debug("Done!");
	}

	private void batchExecute() throws Exception
	{
		if (params.AUTO_VIEWS)
		{
			// multiple data sets/views
			int[] tasks = new int[] { 0, 1, 2, 3, 4, 5, 6 };
			for (int task : tasks)
			{
				switch (task)
				{
					case 0:
						params.DATASET_MODE = DatasetMode.all;
						break;
					case 1:
						params.DATASET_MODE = DatasetMode.coldUsers;
						break;
					case 2:
						params.DATASET_MODE = DatasetMode.heavyUsers;
						break;
					case 3:
						params.DATASET_MODE = DatasetMode.opinUsers;
						break;
					case 4:
						params.DATASET_MODE = DatasetMode.blackSheep;
						break;
					case 5:
						params.DATASET_MODE = DatasetMode.contrItems;
						break;
					case 6:
						params.DATASET_MODE = DatasetMode.nicheItems;
						break;
				}

				if (params.AUTO_CV && (params.VALIDATE_METHOD == ValidateMethod.cross_validation))
				{
					String train = params.TRAIN_SET;
					String test = params.TEST_SET;
					for (int i = 1; i < 6; i++)
					{
						train = train.replaceFirst("\\d", i + "");
						test = test.replaceFirst("\\d", i + "");

						params.TRAIN_SET = train;
						params.TEST_SET = test;

						String setting = "Train.Sets = [" + train + "], Test.Sets = [" + test + "]";
						printSettings.add(setting);

						init();
						singleExecute();
					}
				} else if (params.AUTO_SIMILARITY)
				{
					for (int i = 0; i < 10; i++)
					{
						params.SIMILARITY_THRESHOLD = i * 0.1;
						String setting = "Similarity.threshold = " + (float) params.SIMILARITY_THRESHOLD;
						printSettings.add(setting);
						singleExecute();
					}
				} else if (params.AUTO_TRUST)
				{
					for (int i = 0; i < 10; i++)
					{
						params.TRUST_THRESHOLD = i * 0.1;
						String setting = "Trust.threshold = " + (float) params.TRUST_THRESHOLD;
						printSettings.add(setting);
						singleExecute();
					}
				} else if (params.AUTO_CONFIDENCE)
				{
					for (int i = 0; i < 10; i++)
					{
						params.CONFIDENCE_THRESHOLD = i * 0.1;
						String setting = "Confidence.threshold = " + (float) params.CONFIDENCE_THRESHOLD;
						printSettings.add(setting);
						singleExecute();
					}
				} else if (params.AUTO_KNN)
				{
					for (int i = 1; i < 11; i++)
					{
						params.kNN = i * 5;
						String setting = "kNN = " + params.kNN;
						printSettings.add(setting);
						singleExecute();
					}
				} else if (params.AUTO_TOPN)
				{
					for (int i = 1; i < 11; i++)
					{
						params.TOP_N = i * 5;
						String setting = "Top-N = " + params.TOP_N;
						printSettings.add(setting);
						singleExecute();
					}
				} else
				{
					singleExecute();
				}

			}
		} else if (params.VALIDATE_METHOD == ValidateMethod.cross_validation)
		{
			String train = params.TRAIN_SET;
			String test = params.TEST_SET;

			int num = 1;
			if (params.AUTO_SIMILARITY) num = 10;
			else if (params.AUTO_SIGNIFICANCE) num = 21;
			else if (params.AUTO_TOPN) num = 5;

			for (int k = 0; k < num; k++)
			{
				if (num > 1)
				{
					if (params.AUTO_SIMILARITY)
					{
						params.SIMILARITY_THRESHOLD = k * 0.1;
						printSettings.add("Similarity.threshold = " + (float) params.SIMILARITY_THRESHOLD);
					} else if (params.AUTO_SIGNIFICANCE)
					{
						params.SIGNIFICANCE_THRESHOLD = k * 0.005;
						printSettings.add("Significance.threshold = " + (float) params.SIGNIFICANCE_THRESHOLD);
					} else if (params.AUTO_TOPN)
					{
						if (k == 0) params.TOP_N = 2;
						else params.TOP_N = k * 5;

						printSettings.add("Top.N where N = " + params.TOP_N);
					}
				}

				if (params.AUTO_KNN)
				{
					if (params.SIMILARITY_METHOD == SimMethod.BS)
					{
						SimUtils.alpha = params.readDouble("bs.alpha");;
						SimUtils.beta = params.readDouble("bs.beta");;
						printSettings.add("alpha = " + SimUtils.alpha + ", beta = " + SimUtils.beta);
					}

					for (int i = 1; i < 11; i++)
					{
						params.kNN = i * 5;
						Logs.debug("KNN = {}", params.kNN);

						for (int j = 1; j < 6; j++)
						{
							train = train.replaceFirst("\\d", j + "");
							test = test.replaceFirst("\\d", j + "");

							params.TRAIN_SET = train;
							params.TEST_SET = test;

							if (Debug.ON) methodSettings.add(params.readParam("itrust.probe.method"));

							methodSettings.add("" + params.kNN);

							init();
							singleExecute();
						}
					}
				} else if (params.AUTO_CV)
				{
					boolean flag = params.readParam("bs.params.batch").equalsIgnoreCase("on") ? true : false;
					double alpha = params.readDouble("bs.alpha");
					double beta = params.readDouble("bs.beta");
					if (flag)
					{
						int ma = params.readInt("bs.alpha.start");
						int mb = params.readInt("bs.beta.start");
						for (int m = ma; m < 11; m++)
						{
							SimUtils.alpha = m * 0.1;

							int n = 0;
							if (m == ma) n = mb;
							for (; n < 11; n++)
							{
								SimUtils.beta = n * 0.1;
								if (SimUtils.alpha + SimUtils.beta > 1.0) break;

								for (int i = 1; i < 6; i++)
								{
									train = train.replaceFirst("\\d", i + "");
									test = test.replaceFirst("\\d", i + "");

									params.TRAIN_SET = train;
									params.TEST_SET = test;

									methodSettings.add("" + SimUtils.alpha);
									methodSettings.add("" + SimUtils.beta);

									init();
									singleExecute();
								}
							}
						}
					} else
					{
						SimUtils.alpha = alpha;
						SimUtils.beta = beta;
						for (int i = 1; i < 6; i++)
						{
							train = train.replaceFirst("\\d", i + "");
							test = test.replaceFirst("\\d", i + "");

							params.TRAIN_SET = train;
							params.TEST_SET = test;

							if (params.SIMILARITY_METHOD == SimMethod.BS)
							{
								methodSettings.add("" + SimUtils.alpha);
								methodSettings.add("" + SimUtils.beta);
							}

							if (params.kNN > 0) methodSettings.add("" + params.kNN);

							init();
							singleExecute();
						}
					}
				} else
				{
					singleExecute();
				}
			}
		} else if (params.AUTO_SIMILARITY)
		{
			for (int i = 0; i < 10; i++)
			{
				params.SIMILARITY_THRESHOLD = i * 0.1;
				methodSettings.add("" + params.SIMILARITY_THRESHOLD);
				singleExecute();
			}
		} else if (params.AUTO_TRUST)
		{
			for (int i = 0; i < 10; i++)
			{
				params.TRUST_THRESHOLD = i * 0.1;
				String setting = "Trust.threshold = " + (float) params.TRUST_THRESHOLD;
				printSettings.add(setting);
				singleExecute();
			}
		} else if (params.AUTO_CONFIDENCE)
		{
			for (int i = 0; i < 10; i++)
			{
				params.CONFIDENCE_THRESHOLD = i * 0.1;
				String setting = "Confidence.threshold = " + (float) params.CONFIDENCE_THRESHOLD;
				printSettings.add(setting);
				singleExecute();
			}
		} else if (params.AUTO_KNN)
		{
			for (int i = 1; i < 21; i++)
			{
				params.kNN = i * 5;
				String setting = "kNN = " + params.kNN;
				printSettings.add(setting);
				singleExecute();
			}
		} else if (params.AUTO_TOPN)
		{
			for (int i = 1; i < 11; i++)
			{
				params.TOP_N = i * 10;
				String setting = "Top.N = " + params.TOP_N;
				printSettings.add(setting);
				singleExecute();
			}
		} else if (params.AUTO_SIGMA)
		{
			int num = 40;
			for (int i = 0; i < num + 1; i++)
			{
				params.X_SIGMA = i * 0.1;
				String setting = "x.sigma = " + (float) params.X_SIGMA;
				printSettings.add(setting);
				singleExecute();
			}
		} else if (VirRatingsCF.auto)
		{
			int num = 10;

			int[] users = Randoms.indexs(27, 1357, 1384);
			Logs.debug(Strings.toString(users));

			for (int i = 0; i < num; i++)
			{
				if (i > 0)
				{
					List<Integer> ids = new ArrayList<>();
					for (int j = 0; j < i * 3; j++)
						ids.add(users[j]);

					VirRatingsCF.userIds = ids;
				}

				String setting = "[" + i + "] users ids size = 0"
						+ (VirRatingsCF.userIds == null ? "" : VirRatingsCF.userIds.size());
				printSettings.add(setting);

				init();
				singleExecute();
			}
		} else if (params.auto_trust_sets)
		{
			String dirPath = Dataset.DIRECTORY + "Trust";
			File[] dirs = new File(dirPath).listFiles();
			for (File dir : dirs)
			{
				String name = dir.getName();
				methodSettings.add(name);
				current_trust_name = name;
				current_trust_dir = dir.getPath() + Systems.FILE_SEPARATOR;

				init();
				singleExecute();
			}
		}

	}

	@SuppressWarnings("unchecked")
	protected void printPerformance(Performance pf)
	{
		if (pf == null) return;

		String f6 = "%1.6f";
		String d4 = "%4d";
		String f2 = "%2.2f";
		String format = null;
		String results = Dataset.LABEL + "," + pf.getMethod() + "," + params.DATASET_MODE; // + "," + params.kNN;

		if (printSettings.size() > 0)
		{
			for (String setting : printSettings)
				Logs.info(setting);
			Logs.info(null);
			printSettings.clear();
		}

		if (methodSettings.size() > 0)
		{
			for (String set : methodSettings)
				results += "," + set;
			methodSettings.clear();
		}

		int topN = params.TOP_N;

		if (topN <= 0)
		{
			/* predictive performance */
			Measures ms = pf.prediction(testUserRatingsMap);

			format = "MAE = " + f6 + ", RC[" + d4 + "/" + d4 + "] = " + f2 + "%%, RMSE = " + f6;
			String mae = String.format(format, new Object[] { ms.getMAE(), ms.getCoveredRatings(),
					ms.getTotalRatings(), ms.getRC() * 100, ms.getRMSE() });
			Logs.debug(mae);

			format = "MAUE = " + f6 + ", UC[" + d4 + "/" + d4 + "] = " + f2 + "%%";
			String maue = String.format(format, new Object[] { ms.getMAUE(), ms.getCoveredUsers(), ms.getTotalUsers(),
					ms.getUC() * 100 });
			Logs.debug(maue);
			Logs.debug(null);

			results += "," + ms.getRMSE() + "," + ms.getMAE() + "," + ms.getRC();
			if (Debug.OFF)
			{
				double nMAE = 1 - ms.getMAE() / (Dataset.maxScale - Dataset.minScale);
				double F1 = Stats.hMean(nMAE, ms.getRC());
				results += "," + F1;
			}

		} else
		{
			boolean sort_by_prediction = methodId.startsWith("Merge") ? false : true;
			if (Debug.ON)
			{
				sort_by_prediction = true;
				/* ranking performance */
				Measures ms = pf.ranking(testUserRatingsMap, sort_by_prediction);

				format = "Measures@%d: Precision = " + f6 + ", Recall = " + f6 + ", F1 = " + f6 + ", MAP = " + f6
						+ ", MRR = " + f6 + ", NDCG = " + f6;
				int cutoff = 10;
				String print = String.format(
						format,
						new Object[] { cutoff, ms.getPrecision(cutoff), ms.getRecall(cutoff), ms.getF1(cutoff),
								ms.getMAP(cutoff), ms.getMRR(cutoff), ms.getNDCG(cutoff) });
				Logs.info(print);
				Logs.debug(null);

				results += "\n";
				for (int n : Performance.cutoffs)
				{
					results += (float) ms.getPrecision(n) + "," + (float) ms.getRecall(n) + "," + (float) ms.getF1(n)
							+ "," + (float) ms.getMAP(n) + "," + (float) ms.getMRR(n) + "," + (float) ms.getNDCG(n)
							+ "\n";
				}
			}

			if (Debug.OFF)
			{
				/* diversity performance */
				Measures ms = pf.diversity(new Map[] { userRatingsMap, itemRatingsMap, testItemRatingsMap }, topN,
						sort_by_prediction);

				double F1 = Stats.hMean(ms.getUD(), ms.getSD());
				Logs.debug("UD = {}, SD = {}, F1 = {}", new Object[] { ms.getUD(), ms.getSD(), F1 });
				Logs.debug(null);

				results += "," + topN + "," + ms.getUD() + "," + ms.getSD() + "," + F1;
			}
		}

		if (params.VALIDATE_METHOD == ValidateMethod.cross_validation) results += "," + params.TEST_SET;

		Logs.info(results);
	}

	public static void collectResults() throws Exception
	{
		String mode = params.BATCH_RUN ? DatasetMode.batch.label : params.DATASET_MODE.label;
		String program = methodId + " [" + mode + "]";

		/* Collect result files to specific directory */
		Path source = FileSystems.getDefault().getPath("results.txt");
		Path target = FileSystems.getDefault().getPath(
				FileIO.makeDirectory(AbstractCF.params.RESULTS_DIRECTORY + Dataset.LABEL) + program + "@"
						+ Dates.now() + ".txt");
		Files.copy(source, target);
		if (params.BATCH_RUN && params.numRunMethod > 1 && numRunMethod < params.numRunMethod) FileIO.empty(source
				.toString());

		/* Send email to notify results */
		if (params.EMAIL_NOTIFICATION)
		{
			String text = FileIO.readAsString(source.toString());
			Gmailer notifier = new Gmailer();

			//notifier.getProps().setProperty("mail.to", "fanghui1986@gmail.com");
			//notifier.getProps().setProperty("mail.to", "guoguibing@gmail.com");
			notifier.getProps().setProperty("mail.to", "gguo1@e.ntu.edu.sg");
			//notifier.getProps().setProperty("mail.bcc", "gguo1@e.ntu.edu.sg");

			notifier.getProps()
					.setProperty("mail.subject", Dataset.LABEL + ": " + program + " From " + Systems.getIP());
			notifier.send(text, target.toString());
		}
	}

}
