package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.math.Randoms;
import happy.coding.math.Sims;
import happy.coding.math.Stats;
import happy.coding.system.Debug;
import happy.research.cf.ConfigParams.DatasetMode;
import happy.research.utils.SimUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class DefaultCF extends AbstractCF {
	protected static Map<String, Double> itemMeanMap;

	//protected static Map<Double, Integer> scaleNum;
	protected static Map<Double, Integer> distanceNum;

	private Map<Rating, Map<String, Double>> ratingItemSd = null;
	private Map<Rating, Map<String, Double>> ratingItemMu = null;
	private Map<Rating, Map<String, Double>> ratingItemConf = null;
	private Map<Rating, Map<String, Map<Double, Double>>> ratingItemHist = null;

	private Map<Rating, Map<String, Integer>> ratingItemPos = null;
	private Map<Rating, Map<String, Integer>> ratingItemNeg = null;

	/*
	 * trustDirPath: path of the generated trust directory similarityDirPath:
	 * path of the generated similarity directory missingRatingsDirPath: path of
	 * the predicted missing ratings of the trusted neighbours' directory
	 */
	protected static String trustDirPath = null;

	/**
	 * To initialize some variables if data sets need to be reloaded again.
	 */
	@Override
	protected void init() {
		numRunMethod++;
		userRatingsMap = null;
		itemRatingsMap = null;
		userTNsMap = null;
		userDNsMap = null;
		userTrustorsMap = null;

		testRatings = null;
		itemMeanMap = null;

		testUserRatingsMap = null;
		testItemRatingsMap = null;
	}

	@Override
	protected void loadDataset() throws Exception {
		load_trusts();
		load_ratings();
	}

	protected void prepTestRatings() {
		testRatings = null;
		preProcessing();

		logger.debug("Preparing test-rating data ...");

		testRatings = new ArrayList<>();

		if (params.DATASET_MODE == DatasetMode.nicheItems || params.DATASET_MODE == DatasetMode.contrItems) {
			for (Entry<String, Map<String, Rating>> en : itemRatingsMap.entrySet()) {
				Map<String, Rating> ratings = en.getValue();
				switch (params.DATASET_MODE) {
				case nicheItems:
					if (ratings.size() < 5)
						testRatings.addAll(ratings.values());
					break;
				case contrItems:
					if (RatingUtils.std(ratings.values()) > 1.5)
						testRatings.addAll(ratings.values());
					break;
				default:
					break;
				}
			}

		} else {
			for (Entry<String, Map<String, Rating>> en : userRatingsMap.entrySet()) {
				Map<String, Rating> ratings = en.getValue();

				switch (params.DATASET_MODE) {
				case all:
					if (ratings.size() > 0) {
						testRatings.addAll(ratings.values());
					}
					break;
				case coldUsers:
					//Map<Integer, Double> tns = userTNsMap.get(user);
					if (ratings.size() < 5 /*
											 * && (tns == null || tns.size() <
											 * 5)
											 */) {
						testRatings.addAll(ratings.values());
					}
					break;
				case heavyUsers:
					if (ratings.size() > 10) {
						testRatings.addAll(ratings.values());
					}
					break;
				case opinUsers:
					if (ratings.size() > 4 && RatingUtils.std(ratings.values()) > 1.5) {
						testRatings.addAll(ratings.values());
					}
					break;
				case blackSheep:
					if (ratings.size() > 4 && RatingUtils.meanDistance(ratings, itemMeanMap) > 1) {
						testRatings.addAll(ratings.values());
					}
					break;
				default:
					break;
				}
			}
		}

		logger.debug("Done!");
	}

	/**
	 * used for any needed pre-processing before generating testing ratings
	 */
	protected void preProcessing() {
		// used for any needed pre-processing before generating testing ratings
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void load_ratings() throws Exception {
		if (userRatingsMap == null) {
			String ratingSet = null;
			switch (params.VALIDATE_METHOD) {
			case leave_one_out:
				ratingSet = Dataset.DIRECTORY + Dataset.RATING_SET;
				break;
			case cross_validation:
				ratingSet = Dataset.DIRECTORY + params.TRAIN_SET;
				break;
			}
			logger.debug("Loading rating data {}", ratingSet);

			Map[] data = Dataset.loadTrainSet(ratingSet);
			userRatingsMap = data[0];
			itemRatingsMap = data[1];
			//scaleNum = data[2];

			switch (params.DATASET_MODE) {
			case all:
			case blackSheep:
			case nicheItems:
			case contrItems:
				itemMeanMap = RatingUtils.itemMeans(itemRatingsMap);
				break;
			default:
				break;
			}
		}
	}

	protected void load_trusts() throws Exception {
		if (userTNsMap == null) {
			String trustSet = null;
			if (!params.auto_trust_sets) {
				trustSet = Dataset.DIRECTORY + Dataset.TRUST_SET;
				if (!FileIO.exist(trustSet))
					return;
			} else {
				trustSet = current_trust_dir + Dataset.TRUST_SET;
			}

			switch (Dataset.dataset) {
			case EXTENDED_EPINIONS:
				if (!params.auto_trust_sets) {
					String distrustSet = Dataset.DIRECTORY + Dataset.DISTRUST_SET;
					logger.debug("Loading distrust data {}", distrustSet);
					userDNsMap = DatasetUtils.loadTrustSet(distrustSet);
					// no break to continue load trust
				}
			case EPINIONS:
			case FILMTRUST:
			case FLIXSTER:
				logger.debug("Loading trust data {}", trustSet);
				userTNsMap = DatasetUtils.loadTrustSet(trustSet);
				userTrustorsMap = DatasetUtils.loadTrusteeSet(trustSet);
				break;
			default:
				break;
			}

		}
	}

	protected void randomTrustWalk(Rating testRating, Map<String, Double> weights, Map<String, Rating> ratings) {
		String user = testRating.getUserId();

		double epsilon = 0.0001;
		int epochs = 10000;
		int maxLength = 6;
		String itemId = testRating.getItemId();
		boolean fix_phi = true;
		double fix_value = 0.0;

		Map<String, Rating> testRatings = itemRatingsMap.get(itemId);
		if (testRating == null || testRatings.size() < 2)
			return;

		double previousVariance = 0.0;
		double currentVariance = 0.0;
		for (int epoch = 0; epoch < epochs; epoch++) {
			String currentUser = user;
			double probability = 1.0;

			/* one random walk */
			for (int currentLength = 1; currentLength <= maxLength; currentLength++) {
				Map<String, Double> tns = userTNsMap.get(currentUser);
				if (tns == null || tns.size() < 1)
					break;

				/* choose next user */
				int size = tns.size();
				double step = 1.0 / size;
				int tnIndex = Randoms.uniform(size);

				int j = 0;
				for (String i : tns.keySet()) {
					if (j == tnIndex) {
						currentUser = i;
						break;
					}
					j++;
				}

				if (currentUser.equals(user))
					break;
				probability *= step;

				/* if the current user rated the item */
				Map<String, Rating> rs = userRatingsMap.get(currentUser);
				if (rs == null || rs.size() < 1)
					break;
				if (rs.containsKey(itemId)) {
					weights.put(currentUser, probability);
					ratings.put(currentUser, rs.get(itemId));
					break;
				}

				/* compute phi */
				List<Double> sims = new ArrayList<>();
				List<Rating> rats = new ArrayList<>();
				double phi = 0.0;
				double sum = 0.0;

				if (!fix_phi) {
					phi = fix_value;
				} else {
					for (Rating r : rs.values()) {
						String itemJ = r.getItemId();
						Map<String, Rating> jsRS = itemRatingsMap.get(itemJ);

						List<Double> is = new ArrayList<>();
						List<Double> js = new ArrayList<>();

						for (String userI : testRatings.keySet()) {
							if (jsRS.containsKey(userI)) {
								is.add(testRatings.get(userI).getRating());
								js.add(jsRS.get(userI).getRating());
							}
						}
						if (is.size() < 2)
							continue;

						double similarity = Sims.pcc(is, js);
						if (Double.isNaN(similarity))
							continue;
						if (similarity > 0) {
							sims.add(similarity);
							rats.add(r);
							sum += similarity;
							if (phi < similarity)
								phi = similarity;
						}
					}
					/* if no similar items => phi=0 => random>phi => go on */
					phi *= 1.0 / (1 + Math.exp(-currentLength));
				}

				/* stay or go on */
				double random = Randoms.uniform();
				if (random < phi) {
					probability *= phi;
					int index = Randoms.uniform(0, sims.size());

					double prob = sims.get(index) / sum;
					probability *= prob;
					weights.put(currentUser, probability);
					ratings.put(currentUser, rats.get(index));
				} else {
					probability *= (1 - phi);
				}

			}

			/* test if converged */
			double[] data = new double[ratings.size()];
			for (int m = 0; m < data.length; m++)
				data[m] = ratings.get(m).getRating();
			currentVariance = Stats.var(data);
			if (Math.abs(currentVariance - previousVariance) < epsilon)
				break;
			else
				previousVariance = currentVariance;
		}

	}

	protected double similarity(List<Double> as, List<Double> bs, List<String> items, Rating testRating) {
		double similarity = 0;

		switch (params.SIMILARITY_METHOD) {
		case COS:
			similarity = Sims.cos(as, bs);
			break;
		case BS:
			try {
				/* This is the similarity method for IJCAI paper */
				List<Double> priors = learnScalePriors(testRating);

				if (ratingItemSd == null) {
					ratingItemSd = new HashMap<>();
					ratingItemMu = new HashMap<>();
					ratingItemConf = new HashMap<>();
					ratingItemHist = new HashMap<>();
				}
				Map<String, Double> itemSd = null;
				Map<String, Double> itemMu = null;
				Map<String, Double> itemConf = null;
				Map<String, Map<Double, Double>> itemHist = null;
				if (ratingItemSd.containsKey(testRating)) {
					itemSd = ratingItemSd.get(testRating);
					itemMu = ratingItemMu.get(testRating);
					itemConf = ratingItemConf.get(testRating);
					itemHist = ratingItemHist.get(testRating);
				} else {
					itemSd = new HashMap<>();
					itemMu = new HashMap<>();
					itemConf = new HashMap<>();
					itemHist = new HashMap<>();
					for (String item : itemRatingsMap.keySet()) {
						Map<String, Rating> userRatings = itemRatingsMap.get(item);

						List<Double> rs = new ArrayList<>();
						int pos = 0;

						for (Rating r : userRatings.values()) {
							if (r == testRating)
								continue;
							double rate = r.getRating();
							rs.add(rate);

							if (rate > Dataset.median)
								pos++;
						}

						double mean = Stats.mean(rs);
						double deviation = Stats.sdp(rs, mean);
						double conf = 0;
						Map<Double, Double> hist = new HashMap<>();

						if (Debug.OFF) {
							//conf = 1.0 / (1.0 + Math.exp(-rs.size() / 2.0));
							double thrd = 10.0;
							if (rs.size() > thrd)
								conf = 1.0;
							else
								conf = rs.size() / thrd;
						} else if (Debug.ON) {
							conf = pos / (rs.size() + 0.0);
						} else if (Debug.OFF) {
							// new approach: pair-wise rating distance distribution
							Map<Double, Integer> dists = new HashMap<>();
							List<Double> ds = new ArrayList<>();
							int total = 0;
							for (int i = 0; i < rs.size(); i++) {
								double r1 = rs.get(i);
								for (int j = i + 1; j < rs.size(); j++) {
									double r2 = rs.get(j);
									double dist = Math.abs(r1 - r2);
									int cnt = 0;
									if (dists.containsKey(dist))
										cnt = dists.get(dist);
									dists.put(dist, cnt + 1);

									ds.add(dist);

									total++;
								}
							}

							mean = Stats.mean(ds);
							deviation = Stats.sdp(ds, mean);

							conf = 1.0 / (1.0 + Math.exp(-total / 2.0));
							//conf = Maths.log(1 + total, 100);
							if (conf > 1.0)
								conf = 1.0;
							//if (total > 30) conf = 1.0;
							//else conf = total / 30.0;
							//Logs.debug("total = {}", total);

							for (Entry<Double, Integer> en : dists.entrySet()) {
								double ratio = 0;
								ratio = en.getValue() / (rs.size() + 0.0);
								hist.put(en.getKey(), ratio);
							}

						}

						itemSd.put(item, deviation);
						itemMu.put(item, mean);
						itemConf.put(item, conf);
						itemHist.put(item, hist);
					}

					ratingItemSd.clear();
					ratingItemSd.put(testRating, itemSd);

					ratingItemMu.clear();
					ratingItemMu.put(testRating, itemMu);

					ratingItemConf.clear();
					ratingItemConf.put(testRating, itemConf);

					ratingItemHist.clear();
					ratingItemHist.put(testRating, itemHist);
				}
				List<Double> sd = new ArrayList<>();
				List<Double> mu = new ArrayList<>();
				List<Double> cf = new ArrayList<>();
				Map<Integer, Map<Double, Double>> histos = new HashMap<>();
				for (String item : items) {
					int index = sd.size();
					sd.add(itemSd.get(item));
					mu.add(itemMu.get(item));
					cf.add(itemConf.get(item));
					histos.put(index, itemHist.get(item));
				}

				similarity = SimUtils.bsSim(as, bs, priors, sd, mu, histos, cf);

			} catch (Exception e) {
				e.printStackTrace();
			}

			break;
		case PCC:
			if (as.size() < 2 || bs.size() < 2)
				similarity = Double.NaN;
			else
				similarity = Sims.pcc(as, bs);
			break;
		case MSD:
			similarity = Sims.msd(as, bs);
			break;
		case CPC:
			similarity = Sims.cpc(as, bs, Dataset.median);
			break;
		case PIP:
			if (ratingItemMu == null)
				ratingItemMu = new HashMap<>();
			Map<String, Double> itemMeans = null;
			if (ratingItemMu.containsKey(testRating))
				itemMeans = ratingItemMu.get(testRating);
			else {
				itemMeans = new HashMap<>();
				for (String item : itemRatingsMap.keySet()) {
					double sum = 0.0;
					Map<String, Rating> userRatings = itemRatingsMap.get(item);

					int count = 0;
					for (Rating r : userRatings.values()) {
						if (r == testRating)
							continue;
						else {
							count++;
							sum += r.getRating();
						}
					}
					if (count > 0)
						itemMeans.put(item, sum / count);
				}

				ratingItemMu.clear(); // to save memory
				ratingItemMu.put(testRating, itemMeans);
			}

			/* prep item-mean for the co-rated items */
			List<Double> means = new ArrayList<>();
			for (String item : items)
				means.add(itemMeans.get(item));

			similarity = SimUtils.PIPSim(as, bs, means);
			// System.out.println("PIP sim = " + similarity);

			break;
		case SM:
			if (ratingItemPos == null) {
				ratingItemPos = new HashMap<>();
				ratingItemNeg = new HashMap<>();
			}
			Map<String, Integer> itemPos = null;
			Map<String, Integer> itemNeg = null;
			if (ratingItemPos.containsKey(testRating)) {
				itemPos = ratingItemPos.get(testRating);
				itemNeg = ratingItemNeg.get(testRating);
			} else {
				itemPos = new HashMap<>();
				itemNeg = new HashMap<>();

				for (String item : itemRatingsMap.keySet()) {
					Map<String, Rating> userRatings = itemRatingsMap.get(item);

					int pos = 0, neg = 0;
					for (Rating r : userRatings.values()) {
						if (r == testRating)
							continue;
						if (r.getRating() > Dataset.median)
							pos++;
						else
							neg++;
					}

					itemPos.put(item, pos);
					itemNeg.put(item, neg);
				}

				ratingItemPos.clear(); // to save memory
				ratingItemNeg.clear(); // to save memory
				ratingItemPos.put(testRating, itemPos);
				ratingItemNeg.put(testRating, itemNeg);
			}

			List<Double> posSing = new ArrayList<>();
			List<Double> negSing = new ArrayList<>();

			int numUsers = userRatingsMap.keySet().size();
			for (String item : items) {
				double pos = itemPos.get(item);
				double neg = itemNeg.get(item);

				double ps = 1 - pos / numUsers;
				double ns = 1 - neg / numUsers;

				posSing.add(ps);
				negSing.add(ns);
			}

			similarity = SimUtils.SMSim(as, bs, posSing, negSing);
			// System.out.println("SM sim = " + similarity);
			break;
		default:
			break;
		}

		return similarity;
	}

	private List<Double> learnScalePriors(Rating testRating) {
		List<Double> scales = new ArrayList<>();
		double sum = 0.0;
		for (Integer num : Dataset.scaleNum.values())
			sum += num;
		if (testRating.getRating() > 0)
			sum--;
		for (int i = 0; i < Dataset.scaleSize; i++) {
			double scale = (i + 1) * Dataset.minScale;
			int num = Dataset.scaleNum.get(scale);
			if (scale == testRating.getRating())
				num--;
			int size = Dataset.scaleSize;

			double ratio = num / sum;
			scales.add(size * ratio);
		}
		return scales;
	}

}
