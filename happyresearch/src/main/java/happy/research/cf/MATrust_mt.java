package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.math.Randoms;
import happy.coding.math.Sims;
import happy.coding.math.Stats;
import happy.coding.system.Debug;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;

/**
 * Implementation of the paper "A Multi-aspect Trust-aware Recommender System:
 * Explore the Relationship between Trust and User Similarity"
 * 
 * by Hui et al. (2013)
 * 
 * @author guoguibing
 */
public class MATrust_mt extends DefaultCF_mt {
	private Model model;
	private Map<String, Double> user_ins;
	private static final double epsilon = 1.2;
	private static final double theta = 1.2;

	public MATrust_mt() {
		methodId = "MultiAspect Trust";
		model = null;
		user_ins = null;
	}

	protected double benevolence(String a, String b) {
		Map<String, Rating> asRatings = userRatingsMap.get(a);
		Map<String, Rating> bsRatings = userRatingsMap.get(b);

		if (asRatings == null || asRatings.size() < 1)
			return Double.NaN;
		if (bsRatings == null || bsRatings.size() < 1)
			return Double.NaN;

		List<Double> as = new ArrayList<>();
		List<Double> bs = new ArrayList<>();

		for (Entry<String, Rating> en : asRatings.entrySet()) {
			String itemId = en.getKey();
			if (bsRatings.containsKey(itemId)) {
				as.add(en.getValue().getRating());
				bs.add(bsRatings.get(itemId).getRating());
			}
		}

		return Sims.pcc(as, bs);
	}

	protected double competence(String a, String b, double epsilon) {
		Map<String, Rating> asRatings = userRatingsMap.get(a);
		Map<String, Rating> bsRatings = userRatingsMap.get(b);

		if (asRatings == null || asRatings.size() < 1)
			return Double.NaN;
		if (bsRatings == null || bsRatings.size() < 1)
			return Double.NaN;

		int num_a = asRatings.size();
		int num_b = bsRatings.size();

		double r = 0;
		if (num_b <= num_a)
			r = (num_b + 0.0) / num_a;
		else
			r = 1.0;

		int count = 0;
		int count_ex = 0;

		for (Entry<String, Rating> en : bsRatings.entrySet()) {
			String itemId = en.getKey();
			double bsRating = en.getValue().getRating();

			Map<String, Rating> userRatings = itemRatingsMap.get(itemId);
			for (Rating rate : userRatings.values()) {
				String j = rate.getUserId();
				if (j.equals(b))
					continue;

				double jsRating = rate.getRating();
				double e = Math.abs(bsRating - jsRating);
				if (e < epsilon)
					count++;

				count_ex++;
			}
		}

		double val = (count + 0.0) / count_ex;

		return r * val;
	}

	protected Map<String, Double> integrity() {
		Map<String, Double> user_integrity = new HashMap<>();

		Map<String, Rating> avgRatings = new HashMap<>();
		for (Entry<String, Map<String, Rating>> en : itemRatingsMap.entrySet()) {
			String item = en.getKey();

			int num = 0;
			double sum = 0;
			Map<String, Rating> vals = en.getValue();
			for (Rating r : vals.values()) {
				num++;
				sum += r.getRating();
			}
			double avg = sum / num;

			Rating r = new Rating();
			r.setUserId("average_user");
			r.setItemId(item);
			r.setRating(avg);

			avgRatings.put(item, r);
		}

		for (String u : userTNsMap.keySet()) {
			Map<String, Rating> itemRatings = userRatingsMap.get(u);
			if (itemRatings == null || itemRatings.size() < 1)
				continue;

			List<Double> as = new ArrayList<>();
			List<Double> bs = new ArrayList<>();

			for (Entry<String, Rating> en : itemRatings.entrySet()) {
				String itemId = en.getKey();
				if (avgRatings.containsKey(itemId)) {
					as.add(avgRatings.get(itemId).getRating());
					bs.add(en.getValue().getRating());
				}
			}
			double pcc = Sims.pcc(as, bs);
			if (!Double.isNaN(pcc))
				user_integrity.put(u, pcc);
		}

		for (String u : userDNsMap.keySet()) {
			if (user_integrity.containsKey(u))
				continue;

			Map<String, Rating> itemRatings = userRatingsMap.get(u);
			if (itemRatings == null || itemRatings.size() < 1)
				continue;

			List<Double> as = new ArrayList<>();
			List<Double> bs = new ArrayList<>();

			for (Entry<String, Rating> en : itemRatings.entrySet()) {
				String itemId = en.getKey();
				if (avgRatings.containsKey(itemId)) {
					as.add(avgRatings.get(itemId).getRating());
					bs.add(en.getValue().getRating());
				}
			}
			double pcc = Sims.pcc(as, bs);
			if (!Double.isNaN(pcc))
				user_integrity.put(u, pcc);
		}

		return user_integrity;
	}

	protected double predictability(String a, String b, double theta) {
		Map<String, Rating> asRatings = userRatingsMap.get(a);
		Map<String, Rating> bsRatings = userRatingsMap.get(b);
		if (asRatings == null || asRatings.size() < 1)
			return Double.NaN;
		if (bsRatings == null || bsRatings.size() < 1)
			return Double.NaN;

		int nu = 0, nn = 0, np = 0;
		int common = 0;
		for (Entry<String, Rating> en : asRatings.entrySet()) {
			String item = en.getKey();
			double ra = en.getValue().getRating();
			if (bsRatings.containsKey(item)) {
				double rb = bsRatings.get(item).getRating();

				double e = ra - rb;
				if (e > theta)
					nn++;
				else if (e < -theta)
					np++;
				else
					nu++;

				common++;
			}
		}

		int max = Stats.max(new int[] { nu, nn, np })[0];
		int min = Stats.min(new int[] { nu, nn, np })[0];

		return (max - min + 0.0) / common;
	}

	protected int common_experience(String a, String b) {
		Map<String, Rating> asRatings = userRatingsMap.get(a);
		Map<String, Rating> bsRatings = userRatingsMap.get(b);

		if (asRatings == null || bsRatings == null)
			return 0;
		int num = 0;

		for (String item : asRatings.keySet()) {
			if (bsRatings.containsKey(item)) {
				num++;
			}
		}

		return num;
	}

	protected void train_model() throws Exception {
		int i = 0, s = 20000;
		int num_pos = 0;
		int num_neg = 0;
		double[][] X = new double[s][4];
		double[] Y = new double[s];

		this.user_ins = integrity();

		Logs.debug("Training logistic model ...");
		for (String a : userTNsMap.keySet()) {
			if (!userDNsMap.containsKey(a))
				continue;

			// positive instances
			Map<String, Double> tns = userTNsMap.get(a);
			for (Entry<String, Double> en : tns.entrySet()) {
				String b = en.getKey();
				if (a.equals(b))
					continue;

				double be = benevolence(a, b);
				double co = competence(a, b, epsilon);
				double in = Double.NaN;
				if (user_ins.containsKey(b))
					in = user_ins.get(b);
				double pr = predictability(a, b, theta);

				X[i][0] = be;
				X[i][1] = co;
				X[i][2] = in;
				X[i][3] = pr;
				Y[i] = 1.0;

				i++;
				num_pos++;
			}

			// negative instances
			Map<String, Double> dns = userDNsMap.get(a);
			for (Entry<String, Double> en : dns.entrySet()) {
				String b = en.getKey();
				if (a.equals(b))
					continue;

				double be = benevolence(a, b);
				double co = competence(a, b, epsilon);
				double in = Double.NaN;
				if (user_ins.containsKey(b))
					in = user_ins.get(b);
				double pr = predictability(a, b, theta);

				X[i][0] = be;
				X[i][1] = co;
				X[i][2] = in;
				X[i][3] = pr;
				Y[i] = 0.0;

				i++;
				num_neg++;
			}

		}

		Logs.info("positive intances: " + num_pos + ", negative instances: "
				+ num_neg);

		Problem prob = new Problem();
		prob.l = i; // number of training examples
		prob.n = 5; // number of features + bias
		prob.bias = 1;// the value of bias

		prob.y = new double[i];
		for (int k = 0; k < i; k++)
			prob.y[k] = Y[k];

		prob.x = new FeatureNode[i][];

		for (int k = 0; k < i; k++) {
			List<FeatureNode> fns = new ArrayList<>();
			for (int p = 0; p < 4; p++) {
				if (!Double.isNaN(X[k][p])) {
					fns.add(new FeatureNode(p + 1, X[k][p]));
				}
			}
			prob.x[k] = new FeatureNode[fns.size()];
			for (int m = 0; m < fns.size(); m++) {
				prob.x[k][m] = fns.get(m);
			}
		}

		SolverType solver = SolverType.L2R_LR; // -s 0
		double C = 1.0; // cost of constraints violation
		double eps = 0.001; // stopping criteria

		Parameter param = new Parameter(solver, C, eps);

		Model model = Linear.train(prob, param);

		if (Debug.ON) {
			String dir = Dataset.DIRECTORY + "Models/";
			FileIO.makeDirectory(dir);
			File modelFile = new File(dir + "model.txt");
			model.save(modelFile);
		}

		this.model = model;

		Logs.info("Learned features weights:"
				+ Strings.toString(model.getFeatureWeights()));
		Logs.debug("Done!");
	}

	private double combine_features(double[] ws, double[] fs, int... indexes) {
		double val = 0;

		for (int i : indexes) {
			if (!Double.isNaN(fs[i])) {
				val += ws[i] * fs[i];
			}
		}
		// bias
		val += ws[4] * 1.0;

		// trust
		val = 1.0 / (1.0 + Math.exp(-val));

		return val;
	}

	/**
	 * Trust values are re-generated based on different features<br/>
	 * Distrust values are not re-generated as we will not use them in our work.
	 * 
	 */
	protected void gen_trust() throws Exception {

		double[] ws = model.getFeatureWeights();

		/**
		 * generate trust information
		 */
		Logs.debug("Predict trust values ...");
		for (String a : userTNsMap.keySet()) {
			// positive instances
			Map<String, Double> tns = userTNsMap.get(a);
			for (Entry<String, Double> en : tns.entrySet()) {
				String b = en.getKey();
				if (a.equals(b))
					continue;

				double be = benevolence(a, b);
				double co = competence(a, b, epsilon);
				double in = Double.NaN;
				if (user_ins.containsKey(b))
					in = user_ins.get(b);
				double pr = predictability(a, b, theta);

				double[] fs = new double[4];
				fs[0] = be;
				fs[1] = co;
				fs[2] = in;
				fs[3] = pr;

				// single component
				String line = a + " " + b;
				String content = null;
				int[] indexes = null;
				double val = 0;
				for (int i = 0; i < 4; i++) {
					indexes = new int[] { i };
					val = combine_features(ws, fs, indexes);

					content = line + " " + val;
					output_trust(content, indexes);
				}

				// double components
				for (int i = 0; i < 4; i++) {
					for (int j = i + 1; j < 4; j++) {
						indexes = new int[] { i, j };
						val = combine_features(ws, fs, indexes);

						content = line + " " + val;
						output_trust(content, indexes);
					}
				}

				// three components
				for (int i = 0; i < 4; i++) {
					indexes = Randoms.nextNoRepeatIntArray(3, 0, 4,
							new int[] { i });
					val = combine_features(ws, fs, indexes);

					content = line + " " + val;
					output_trust(content, indexes);
				}

				// four components
				indexes = new int[] { 0, 1, 2, 3 };
				val = combine_features(ws, fs, indexes);

				content = line + " " + val;
				output_trust(content, indexes);

			}

		}

		Logs.debug("Done!");
	}

	private void output_trust(String line, int... indexes) throws Exception {
		String dir = Dataset.DIRECTORY + "Trust/";
		for (int i = 0; i < indexes.length; i++) {
			int index = indexes[i];
			if (i > 0)
				dir += "_";
			dir += index;
		}
		dir += "/";
		FileIO.makeDirectory(dir);
		String file = dir + "trust.txt";

		FileIO.writeString(file, line, true);
	}

	/**
	 * This stub method is used for initially testing (previous)
	 * 
	 * @throws Exception
	 */
	protected void train_model_stub() throws Exception {

		/**
		 * training settings
		 */
		int i = 0, s = 20000;
		int num_pos = 0;
		int num_neg = 0;
		double[][] X = new double[s][4];
		double[] Y = new double[s];

		/**
		 * testing settings
		 */
		int i_test = 0, s_test = 20000;
		int test_pos = 0;
		int test_neg = 0;
		double[][] X_test = new double[s_test][4];
		double[] Y_test = new double[s_test];

		int count = 0;

		Map<String, Double> user_ins = integrity();

		for (String a : userTNsMap.keySet()) {

			if (!userDNsMap.containsKey(a))
				continue;

			// prepare for the training data
			count++;

			if (count > 300)
				break;

			if (count < 200) {

				// positive instances
				Map<String, Double> tns = userTNsMap.get(a);
				for (Entry<String, Double> en : tns.entrySet()) {
					String b = en.getKey();
					if (a.equals(b))
						continue;

					double be = benevolence(a, b);
					double co = competence(a, b, epsilon);
					double in = Double.NaN;
					if (user_ins.containsKey(b))
						in = user_ins.get(b);
					double pr = predictability(a, b, theta);

					X[i][0] = be;
					X[i][1] = co;
					X[i][2] = in;
					X[i][3] = pr;
					Y[i] = 1.0;

					i++;
					num_pos++;
				}

				// negative instances
				Map<String, Double> dns = userDNsMap.get(a);
				for (Entry<String, Double> en : dns.entrySet()) {
					String b = en.getKey();
					if (a.equals(b))
						continue;

					double be = benevolence(a, b);
					double co = competence(a, b, epsilon);
					double in = Double.NaN;
					if (user_ins.containsKey(b))
						in = user_ins.get(b);
					double pr = predictability(a, b, theta);

					X[i][0] = be;
					X[i][1] = co;
					X[i][2] = in;
					X[i][3] = pr;
					Y[i] = 0.0;

					i++;
					num_neg++;
				}
				// System.out.println("count=" + count);
			}

			else {
				// Logs.debug("Collecting data for testing process ...");
				Map<String, Double> tns = userTNsMap.get(a);
				for (Entry<String, Double> en : tns.entrySet()) {
					String b = en.getKey();
					if (a.equals(b))
						continue;

					double be = benevolence(a, b);
					double co = competence(a, b, epsilon);
					double in = Double.NaN;
					if (user_ins.containsKey(b))
						in = user_ins.get(b);
					double pr = predictability(a, b, theta);

					X_test[i_test][0] = be;
					X_test[i_test][1] = co;
					X_test[i_test][2] = in;
					X_test[i_test][3] = pr;
					Y_test[i_test] = 1.0;

					i_test++;
					test_pos++;
				}

				// negative instances
				Map<String, Double> dns = userDNsMap.get(a);
				for (Entry<String, Double> en : dns.entrySet()) {
					String b = en.getKey();
					if (a.equals(b))
						continue;

					double be = benevolence(a, b);
					double co = competence(a, b, epsilon);
					double in = Double.NaN;
					if (user_ins.containsKey(b))
						in = user_ins.get(b);
					double pr = predictability(a, b, theta);

					X_test[i_test][0] = be;
					X_test[i_test][1] = co;
					X_test[i_test][2] = in;
					X_test[i_test][3] = pr;
					Y_test[i_test] = 0.0;

					i_test++;
					test_neg++;
				}
			}

		}

		Logs.info("positive intance: " + num_pos + ", negative instance: "
				+ num_neg);
		Logs.info("positive intance for test: " + test_pos
				+ ", negative instance for test: " + test_neg);

		// Logs.debug("Applying logistic regression ...");

		// testing setting
		int total = i_test;
		// int test_num_neg = 0, test_num_pos = 0, test_neg1 = test_neg,
		// test_pos1 = test_pos;

		Feature[][] test_xs = new FeatureNode[total][];
		double[] test_ys = new double[total];

		// training setting
		Problem prob = new Problem();
		prob.l = i; // number of training examples
		prob.n = 5; // number of features + bias
		prob.bias = 1;// the value of bias

		prob.y = new double[i];
		for (int k = 0; k < i; k++)
			prob.y[k] = Y[k];

		prob.x = new FeatureNode[i][];

		// train data
		for (int k = 0; k < i; k++) {
			List<FeatureNode> fns = new ArrayList<>();
			for (int p = 0; p < 4; p++) {
				if (!Double.isNaN(X[k][p])) {
					fns.add(new FeatureNode(p + 1, X[k][p]));
				}
			}
			prob.x[k] = new FeatureNode[fns.size()];
			for (int m = 0; m < fns.size(); m++) {
				prob.x[k][m] = fns.get(m);
			}
		}

		// test data
		for (int k = 0; k < i_test; k++) {
			test_ys[k] = Y_test[k];
			List<FeatureNode> fns = new ArrayList<>();
			for (int p = 0; p < 4; p++) {
				if (!Double.isNaN(X_test[k][p])) {
					fns.add(new FeatureNode(p + 1, X_test[k][p]));
				}
			}
			test_xs[k] = new FeatureNode[fns.size()];
			for (int m = 0; m < fns.size(); m++) {
				test_xs[k][m] = fns.get(m);
			}
		}

		// total = test_num_pos + test_num_neg;

		SolverType solver = SolverType.L2R_LR; // -s 0
		double C = 1.0; // cost of constraints violation
		double eps = 0.001; // stopping criteria

		Parameter param = new Parameter(solver, C, eps);
		Model model = Linear.train(prob, param);

		if (Debug.ON) {
			String dir = "Models/";
			FileIO.makeDirectory(dir);
			// File modelFile = new File(dir + "model_" + a + ".txt");
			File modelFile = new File(dir + "model.txt");
			model.save(modelFile);

			// load model or use it directly
			model = Model.load(modelFile);
		}

		int correct[] = new int[20];
		int correct_trust[] = new int[20];
		int correct_distrust[] = new int[20];
		for (int k = 0; k < total; k++) {
			Feature[] instance = test_xs[k];
			double[] prob_estimates = new double[2];
			double label = Linear.predictProbability(model, instance,
					prob_estimates);

			if (Debug.OFF) {
				if (test_ys[k] == label)
					// correct++;
					Logs.debug("(" + test_ys[k] + ", " + label + ": "
							+ Strings.toString(prob_estimates) + ")");
			} else {
				double estimate = prob_estimates[0];
				// double threshold = (num_pos + 0.0) / (num_pos + num_neg);

				for (int t = 0; 0.05 * t < 1; t++) {

					if (estimate > 0.05 * t)
						label = 1.0;
					else
						label = 0.0;

					if (test_ys[k] == label) {
						correct[t]++;
						if (label == 1)
							correct_trust[t]++;
					}
				}
			}
		}
		for (int j = 0; j < 20; j++) {
			correct_distrust[j] = correct[j] - correct_trust[j];
			double accuracy = (correct[j] + 0.0) / total;
			System.out.println("threshold=" + j
					+ ": the number of correct trust prediction="
					+ correct_trust[j] + "; the number of distrust prediction="
					+ correct_distrust[j]);
			System.out.println("accuracy=" + accuracy);
		}
	}

	@Override
	protected Performance runMultiThreads() throws Exception {
		train_model();
		gen_trust();

		return null;
	}
}
