package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.research.cf.ConfigParams.DatasetMode;
import happy.research.cf.ConfigParams.ValidateMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class DefaultCF_mt extends DefaultCF {
	protected static List<Rating>[] ratingArrays = null;
	protected static Thread[] threads = null;
	protected static Performance pf = null;

	protected List<Rating> threadRatings = null;
	protected List<String>[] users;
	protected List<String> threadUsers;
	protected int id = 0;
	protected int currentProgress = 0;
	protected int numRating = 0;

	@Override
	protected Performance runRecAlgorithm() throws Exception {
		makeDirPaths();

		int numThreads = params.RUNTIME_THREADS;
		ratingArrays = DatasetUtils.splitCollection(testRatings, numThreads);
		threads = new Thread[numThreads];

		pf = new Performance(methodId);

		return runMultiThreads();
	}

	protected abstract Performance runMultiThreads() throws Exception;

	protected void makeDirPaths() throws Exception {
		int horizon = params.TRUST_PROPERGATION_LENGTH;
		String trustDir = (params.TIDALTRUST ? "TT" : "MT") + horizon;
		String trustDir2 = params.TIDALTRUST ? "TidalTrust" : "MoleTrust";
		String trustDir0 = null;
		if (params.auto_trust_sets)
			trustDir0 = current_trust_name;
		String[] trustDirs = null;

		if (params.auto_trust_sets) {
			trustDirs = new String[] { Dataset.TEMP_DIRECTORY, trustDir,
					trustDir0, trustDir2 };
		} else {
			trustDirs = new String[] { Dataset.TEMP_DIRECTORY, trustDir,
					trustDir2 };
		}
		trustDirPath = FileIO.makeDirPath(trustDirs);

		FileIO.makeDirectory(trustDirPath);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void prepTestRatings() {
		if (params.VALIDATE_METHOD == ValidateMethod.leave_one_out)
			super.prepTestRatings();
		else if (testRatings == null) {
			String testSet = Dataset.DIRECTORY + params.TEST_SET;
			Logs.debug("Loading test set {}", testSet);
			testRatings = new ArrayList<>();

			Map<String, Map<String, Rating>> userMap = null;
			Map<String, Map<String, Rating>> itemMap = null;
			try {
				Map[] data = Dataset.loadTestSet(testSet);
				userMap = data[0];
				itemMap = data[1];
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (params.DATASET_MODE == DatasetMode.nicheItems
					|| params.DATASET_MODE == DatasetMode.contrItems) {
				for (String item : itemMap.keySet()) {
					Map<String, Rating> trainItemRatings = itemRatingsMap
							.get(item);
					Map<String, Rating> testItemRatings = itemMap.get(item);
					boolean emptyTrain = trainItemRatings == null;

					switch (params.DATASET_MODE) {
					case nicheItems:
						if (emptyTrain || trainItemRatings.size() < 5) {
							testRatings.addAll(testItemRatings.values());
						}
						break;
					case contrItems:
						if (!emptyTrain
								&& RatingUtils.std(trainItemRatings.values()) > 1.5) {
							testRatings.addAll(testItemRatings.values());
						}
						break;
					default:
						break;
					}
				}

			} else {
				for (String user : userMap.keySet()) {
					Map<String, Rating> trainUserRatings = userRatingsMap
							.get(user);
					Map<String, Rating> testUserRatings = userMap.get(user);

					boolean emptyTrain = trainUserRatings == null;
					switch (params.DATASET_MODE) {
					case all:
						if (emptyTrain || trainUserRatings.size() > 0) {
							testRatings.addAll(testUserRatings.values());
						}
						break;
					case coldUsers:
						if (emptyTrain || trainUserRatings.size() <= 4) {
							testRatings.addAll(testUserRatings.values());
						}
						break;
					case heavyUsers:
						if (!emptyTrain && trainUserRatings.size() > 10) {
							testRatings.addAll(testUserRatings.values());
						}
						break;
					case opinUsers:
						if (!emptyTrain
								&& trainUserRatings.size() > 4
								&& RatingUtils.std(trainUserRatings.values()) > 1.5) {
							testRatings.addAll(testUserRatings.values());
						}
						break;
					case blackSheep:
						if (!emptyTrain
								&& trainUserRatings.size() > 4
								&& RatingUtils.meanDistance(trainUserRatings,
										itemMeanMap) > 1) {
							testRatings.addAll(testUserRatings.values());
						}
						break;
					default:
						break;
					}// end of switch
				}// end of for
			}// end of else

		}

	}

}
