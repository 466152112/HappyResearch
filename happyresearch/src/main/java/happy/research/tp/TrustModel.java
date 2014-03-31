package happy.research.tp;

import happy.coding.io.Configer;
import happy.coding.io.FileIO;
import happy.coding.io.FileIO.MapWriter;
import happy.coding.io.KeyValPair;
import happy.coding.io.Lists;
import happy.coding.io.Logs;
import happy.coding.io.net.Gmailer;
import happy.coding.math.Measures;
import happy.coding.math.Randoms;
import happy.coding.math.Stats;
import happy.coding.system.Dates;
import happy.coding.system.Debug;
import happy.coding.system.Systems;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Table;

/**
 * Abstract class for trust prediction
 * 
 * @author guoguibing
 * 
 */
public abstract class TrustModel {
	// configuration
	protected static Configer conf = null;

	// default model name
	protected static String model = "TrustModel";
	protected static String settings = "";
	protected static String testView = "all";

	// default dataset
	public final static String Epinions = "Epinions";
	public final static String Epinions_Samlpe = "Epinions_Sample";
	public final static String CiaoDVDs = "CiaoDVDs";
	public static String dataset = CiaoDVDs;

	// constant strings
	public static String dirPath = null;
	public final static String sep = ",";

	// dataset objects
	protected static Multimap<String, String> reviews = null;
	protected static Table<String, String, Float> ratings = null;
	protected static Table<String, String, Integer> trusts = null;

	// global factors
	protected Map<String, Float> rbs = null; // global ability as a rater
	protected Map<String, Float> wbs = null; // global ability as a writer
	protected Map<String, Float> gls = null; // global leniency as trust
												// propensity
	protected Map<String, Float> gbs = null; // global benevolence
	protected Map<String, Float> ins = null; // global integrity
	protected Map<String, Float> gts = null; // global trustworthiness
	protected Map<String, Float> rqs = null; // review quality

	// intermediate variables
	protected static Table<String, String, Integer> userWrites = null; // user interactions: {u writes, v rates, #}
	protected static Table<String, String, Integer> userRates = null; // user interactions: {u rates, v writes, #}
	protected static Table<String, String, Integer> userInters = null; // user interactions: {u, v, # interactions}

	protected static Map<String, String> reviewUserMap = null; // {review, writer} mapping
	protected static Map<Float, Double> dists = null;
	protected static Set<String> users = null; // all the users
	protected static Set<String> rws = null; // all the reviews

	// local leniency from user u to user v
	protected Table<String, String, Float> lns = null;
	protected float min_lns = 0f, max_lns = 0f;

	protected static float uw_u = 0; // the mean of number of interactions in userWrites.
	protected static float rw_u = 0;

	protected static Set<String> testUsers = null;

	public void execute() throws Exception {
		// initial ETAF model
		init();

		// evaluate global trust factors
		global();

		// predict trust worthiness
		String path = predict();

		// evalute ETAF performance
		evaluate(path);
	}

	/**
	 * Evaluate users' global trustworthiness
	 */
	protected void global() throws Exception {
		return;
	}

	/**
	 * Evaluate two users' local trustworthiness
	 */
	protected abstract float local(String u, String v) throws Exception;

	/**
	 * Predict the trusted values for the trustors
	 * 
	 * @return the path to the prediction results
	 */
	protected String predict() throws Exception {
		Logs.debug("Predict users' trustworthiness ...");

		// store prediction data to disk
		String path = FileIO.makeDirPath(dirPath, FileIO.getCurrentFolder(), model + "-preds");
		if (FileIO.exist(path))
			FileIO.deleteDirectory(path);
		FileIO.makeDirectory(path);

		Map<String, Float> preds = new HashMap<>();
		for (final String u : testUsers) {

			// predict trustworthiness
			preds.clear();
			for (String v : users) {
				if (u.equals(v))
					continue;

				float tuv = predict(u, v);

				if (tuv > 0)
					preds.put(v, tuv);
			}

			// output trust predictions to save memory
			if (Debug.ON) {
				if (preds.size() > 0) {
					FileIO.writeMap(path + u + ".txt", preds, new MapWriter<String, Float>() {

						@Override
						public String processEntry(String key, Float val) {
							return u + sep + key + sep + val;
						}
					}, false);
				}
			}
		}
		Logs.debug("Done!");

		return path;
	}

	/**
	 * Predict the trustworthiness of user v w.r.t user u
	 */
	protected float predict(String u, String v) throws Exception {
		// default implementation: return local trustworthiness only
		return local(u, v);
	}

	/**
	 * Initialize the trust model
	 */
	protected void init() throws Exception {
		Logs.debug("Initilize trust model ...");

		Randoms.seed(1);
		rbs = new HashMap<>();
		wbs = new HashMap<>();
		gls = new HashMap<>();
		gbs = new HashMap<>();
		ins = new HashMap<>();
		gts = new HashMap<>();
		rqs = new HashMap<>();
		lns = HashBasedTable.create();

		// load datasets
		if (reviews != null)
			return;

		reviews = DatasetUtils.loadReviews(dirPath + "user-reviews.txt");
		ratings = DatasetUtils.loadRatings(dirPath + "review-ratings.txt");
		trusts = DatasetUtils.loadTrusts(dirPath + "trusts.txt");

		if (Debug.OFF) {
			Logs.info("{} users have written {} reviews.", reviews.keySet().size(), reviews.size());
			Logs.info("{} users have rated {} reviews.", ratings.rowKeySet().size(), ratings.columnKeySet().size());
			Logs.info("{} users have trusted {} other users.", trusts.rowKeySet().size(), trusts.columnKeySet().size());
		}

		rws = new HashSet<>();
		rws.addAll(reviews.values());
		rws.addAll(ratings.columnKeySet());

		users = new HashSet<>();
		users.addAll(reviews.keySet());
		users.addAll(ratings.rowKeySet());

		// {review, user} mapping
		reviewUserMap = new HashMap<>();
		for (String user : reviews.keySet()) {
			Collection<String> rvs = reviews.get(user);
			for (String rv : rvs)
				reviewUserMap.put(rv, user);
		}

		// rating distribution
		Multiset<Float> scales = HashMultiset.create();
		for (Float s : ratings.values())
			scales.add(s);

		dists = new HashMap<>();
		double tts = scales.size();
		for (Float s : scales)
			dists.put(s, scales.count(s) / tts);

		/* prepare the test users */
		testUsers = new HashSet<>();
		switch (testView) {
		case "all":
			testUsers.addAll(trusts.rowKeySet());
			break;

		case "cold":
			int threshold = conf.getInt("num.cold.threshold");
			for (String t : trusts.rowKeySet()) {

				Collection<String> rvs = reviews.get(t);
				Map<String, Float> rts = ratings.row(t);

				if (rvs.size() < threshold && rts.size() < threshold)
					testUsers.add(t);
			}
			break;

		case "warm":
			threshold = conf.getInt("num.warm.threshold");
			for (String t : trusts.rowKeySet()) {

				Collection<String> rvs = reviews.get(t);
				Map<String, Float> rts = ratings.row(t);

				if (rvs.size() >= threshold && rts.size() >= threshold)
					testUsers.add(t);
			}

			break;
		}

		Logs.debug("Test model = {}; test users = {}", testView, testUsers.size());
		settings += sep + testView + sep + testUsers.size();

		String[] allUsers = null; //users.toArray(new String[0]);

		// {user u, user v, number of interactions of u writing reviews rated by
		// v}
		userWrites = null; //HashBasedTable.create();

		// {user u, user v, number of interactions of u rating reviews written
		// by v}
		userRates = null;//HashBasedTable.create();

		// interactions as a review writer
		if (Debug.OFF) {
			// this step is too time-consuming
			int total_w = 0, cnt_w = 0;
			for (int i = 0, n = allUsers.length; i < n; i++) {
				String u = allUsers[i];
				Collection<String> rvs = reviews.get(u);
				if (rvs.size() > 0) {

					for (int j = 0; j < n; j++) {
						if (i == j)
							continue;
						String v = allUsers[j];

						Map<String, Float> rts = ratings.row(v);

						int n_inter = 0;
						for (String rv : rvs) {
							if (rts.containsKey(rv))
								n_inter++;
						}

						if (n_inter > 0) {
							userWrites.put(u, v, n_inter);
							total_w += n_inter;
							cnt_w++;
						}
					}
				}
			}
			uw_u = (total_w + 0.0f) / cnt_w; // 5.04 for ciao; 5.64 for epinions
		}
		uw_u = 5;

		if (Debug.OFF) {
			// interactions as a review rater
			int total_r = 0, cnt_r = 0;
			for (int i = 0, n = allUsers.length; i < n; i++) {
				String u = allUsers[i];
				Map<String, Float> uRates = ratings.row(u);
				if (uRates.size() > 0) {

					for (int j = 0; j < n; j++) {
						if (i == j)
							continue;
						String v = allUsers[j];

						Collection<String> vWrites = reviews.get(v);
						if (vWrites.size() == 0)
							continue;

						int n_inter = 0;
						for (String rv : uRates.keySet()) {
							if (vWrites.contains(rv))
								n_inter++;
						}

						if (n_inter > 0) {
							userRates.put(u, v, n_inter);
							total_r += n_inter;
							cnt_r++;
						}
					}
				}
			}

			double avg = total_r / (cnt_r + 0.0);// 5.04 for ciao

			// total interactions
			userInters = HashBasedTable.create();
			int total = 0, cnt = 0;
			for (int i = 0, n = allUsers.length; i < n; i++) {
				String u = allUsers[i];

				for (int j = 0; j < n; j++) {
					if (i == j)
						continue;

					String v = allUsers[j];

					int n_inter = 0;
					if (userWrites.contains(u, v))
						n_inter += userWrites.get(u, v);
					if (userRates.contains(u, v))
						n_inter += userRates.get(u, v);

					if (n_inter > 0) {
						userInters.put(u, v, n_inter);
						total += n_inter;
						cnt++;
					}
				}
			}

			rw_u = (total + 0.0f) / cnt; // u = 5.65 for ciaodvds; 7.665 for epinions
		}
		rw_u = 5;

		Logs.debug("Done!");
	}

	/**
	 * Evalute the performance of our ETAF model
	 */
	protected void evaluate(String path) throws Exception {

		Map<String, Float> preds = null;
		Map<String, Integer> trustees = null;
		int topN = 20;
		Set<String> trustors = trusts.rowKeySet();

		List<Float> precs5 = new ArrayList<>();
		List<Float> precs10 = new ArrayList<>();
		List<Float> recalls5 = new ArrayList<>();
		List<Float> recalls10 = new ArrayList<>();
		List<Float> aps = new ArrayList<>();
		List<Float> rrs = new ArrayList<>();
		List<Float> aucs = new ArrayList<>();
		List<Float> ndcgs = new ArrayList<>();

		for (String u : trustors) {
			// load preds
			String dataFile = path + u + ".txt";
			if (!FileIO.exist(dataFile))
				continue;
			preds = loadPreds(dataFile);
			// ground truth
			trustees = trusts.row(u);

			List<KeyValPair<String>> sorted = Lists.sortMap(preds, true);
			List<KeyValPair<String>> recomd = sorted.subList(0, sorted.size() > topN ? topN : sorted.size());

			List<String> rankedList = new ArrayList<>();
			for (KeyValPair<String> kv : recomd)
				rankedList.add(kv.getKey());

			List<String> groundTruth = new ArrayList<>(trustees.keySet());
			int num_dropped_items = 0;
			double AUC = Measures.AUC(rankedList, groundTruth, num_dropped_items);
			double AP = Measures.AP(rankedList, groundTruth);
			double nDCG = Measures.nDCG(rankedList, groundTruth);
			double RR = Measures.RR(rankedList, groundTruth);

			List<Integer> ns = Arrays.asList(5, 10);

			Map<Integer, Double> precs = Measures.PrecAt(rankedList, groundTruth, ns);
			Map<Integer, Double> recalls = Measures.RecallAt(rankedList, groundTruth, ns);

			precs5.add(precs.get(5).floatValue());
			precs10.add(precs.get(10).floatValue());
			recalls5.add(recalls.get(5).floatValue());
			recalls10.add(recalls.get(10).floatValue());

			aucs.add((float) AUC);
			aps.add((float) AP);
			rrs.add((float) RR);
			ndcgs.add((float) nDCG);
		}

		float prec5 = (float) Stats.mean(precs5);
		float prec10 = (float) Stats.mean(precs10);
		float recall5 = (float) Stats.mean(recalls5);
		float recall10 = (float) Stats.mean(recalls10);
		float ndcg = (float) Stats.mean(ndcgs);
		// float auc = (float) Stats.mean(aucs);
		float map = (float) Stats.mean(aps);
		float mrr = (float) Stats.mean(rrs);

		Logs.info("{},{},{},{},{},{},{},{},{}", new Object[] { model, prec5, prec10, recall5, recall10, map, ndcg, mrr,
				settings });

	}

	protected Map<String, Float> loadPreds(String path) throws Exception {
		Map<String, Float> preds = new HashMap<>();

		BufferedReader br = new BufferedReader(new FileReader(new File(path)));
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split(sep);

			preds.put(data[1], Float.parseFloat(data[2]));
		}
		br.close();

		return preds;
	}

	protected float weight(int n) {
		return Debug.OFF ? 1 - 1 / (1.0f + n) : logic(n, 0.1f, 5);
	}

	protected float logic(float x, float alpha, float u) {
		float p = -alpha * (x - u);
		return (float) (1.0 / (1.0 + Math.exp(p)));
	}

	protected float alpha(int n, int min) {
		if (n >= min)
			return 1.0f;

		return (float) Math.sin(0.5 * Math.PI * ((n + 0.0) / min));
	}

	protected float[] minMax(Collection<? extends Number> data) {
		float min = Float.MAX_VALUE;
		float max = Float.MIN_VALUE;

		for (Number n : data) {
			Float r = n.floatValue();

			if (min > r)
				min = r;
			if (max < r)
				max = r;
		}

		return new float[] { min, max };
	}

	public static void main(String[] args) throws Exception {

		// Logs.config(FileIO.getResource("log4j.properties"), false);
		conf = new Configer("tp.conf");

		dataset = conf.getString("dataset");
		testView = conf.getString("test.view");

		dirPath = FileIO.makeDirPath(conf.getPath("dataset.dir"), dataset);

		switch (conf.getString("method")) {
		case "ETAF":
			ETAF tm = new ETAF();
			for (Double alpha : conf.getRange("val.ETAF.alpha")) {
				// alpha for trust combination
				tm.alpha = alpha.floatValue();

				for (Double gamma : conf.getRange("val.ETAF.gamma")) {
					// gamma for ability combination
					tm.gamma = gamma.floatValue();

					tm.isIn = conf.isOn("is.ETAF.in");
					if (tm.isIn) {
						for (Double eta : conf.getRange("val.ETAF.eta")) {
							// eta for integrity combination
							tm.eta = eta.floatValue();

							Logs.debug("Settings: alpha = {}, gamma = {}, eta = {}, in = {}", new Object[] { alpha,
									gamma, eta, tm.isIn });
							settings = alpha + sep + gamma + sep + eta + sep + tm.isIn;

							tm.execute();
						}
					} else {
						// not to consider integrity at all
						tm.eta = 1f;

						Logs.debug("Settings: alpha = {}, gamma = {}, in = {}", new Object[] { alpha, gamma, tm.isIn });
						settings = alpha + sep + gamma + sep + tm.isIn;

						tm.execute();
					}
				}
			}
			break;
		case "TAF":
			new TAF().execute();
			break;
		case "EPT":
			EPT ept = new EPT();
			List<Double> Nmins = conf.getRange("num.EPT.Nmin");
			for (Double n : Nmins) {
				ept.Nmin = n.intValue();

				Logs.debug("Settings: Nmin = {}", ept.Nmin);
				settings = "" + ept.Nmin;

				ept.execute();
			}
			break;
		default:
			break;
		}

		String destPath = FileIO.makeDirectory(dirPath, "Results");
		String dest = destPath + model + "@" + Dates.now() + ".txt";
		FileIO.copyFile("results.txt", dest);

		if (conf.isOn("is.email.notify")) {
			Gmailer notifier = new Gmailer();

			notifier.getProps().setProperty("mail.to", "gguo1@e.ntu.edu.sg");
			notifier.getProps().setProperty("mail.subject",
					FileIO.getCurrentFolder() + "-" + model + " is finished @ " + Systems.getIP());
			notifier.send("Program " + model + " has been finished!", dest);
		}
	}

}
