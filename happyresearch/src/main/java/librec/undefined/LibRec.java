// Copyright (C) 2014 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.undefined;

import happy.coding.io.Configer;
import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.io.net.EMailer;
import happy.coding.system.Dates;
import happy.coding.system.Systems;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import librec.baseline.ConstantGuess;
import librec.baseline.GlobalAverage;
import librec.baseline.ItemAverage;
import librec.baseline.MostPopular;
import librec.baseline.RandomGuess;
import librec.baseline.UserAverage;
import librec.data.DataDAO;
import librec.data.DataSplitter;
import librec.data.SparseMatrix;
import librec.ext.AR;
import librec.ext.Hybrid;
import librec.ext.NMF;
import librec.ext.PD;
import librec.ext.PRankD;
import librec.ext.SlopeOne;
import librec.intf.Recommender;
import librec.intf.Recommender.Measure;
import librec.ranking.BPR;
import librec.ranking.CLiMF;
import librec.ranking.GBPR;
import librec.ranking.RankALS;
import librec.ranking.RankSGD;
import librec.ranking.SLIM;
import librec.ranking.WRMF;
import librec.rating.BPMF;
import librec.rating.BiasedMF;
import librec.rating.ItemKNN;
import librec.rating.PMF;
import librec.rating.RSTE;
import librec.rating.RegSVD;
import librec.rating.SVDPlusPlus;
import librec.rating.SoRec;
import librec.rating.SoReg;
import librec.rating.SocialMF;
import librec.rating.TrustMF;
import librec.rating.TrustSVD;
import librec.rating.UserKNN;

/**
 * Main Class of the LibRec Library
 * 
 * @author guoguibing
 * 
 */
public class LibRec {
	// version: MAJOR version (significant changes), followed by MINOR version
	// (small changes, bug fixes)
	private static String version = "1.0";

	// configuration
	private static Configer cf;
	private static String algorithm;

	// params for multiple runs at once
	public static int paramIdx;
	public static boolean isMultRun = true;

	// rate DAO object
	private static DataDAO rateDao;

	// rating matrix
	public static SparseMatrix rateMatrix = null;

	public static void main(String[] args) throws Exception {
		// Logs.debug(LibRec.readme());

		// get configuration file
		cf = new Configer("librec.conf");

		// debug info
		debugInfo();

		// prepare data
		rateDao = new DataDAO(cf.getPath("dataset.training"));
		rateMatrix = rateDao.readData(cf.getDouble("val.binary.threshold"));

		// config general recommender
		Recommender.cf = cf;
		Recommender.rateMatrix = rateMatrix;
		Recommender.rateDao = rateDao;

		// required: only one parameter varying for multiple run
		Recommender.params = RecUtils.buildParams(cf);

		// run algorithms
		if (Recommender.params.size() > 0) {
			// multiple run
			for (Entry<String, List<Float>> en : Recommender.params.entrySet()) {
				for (int i = 0, im = en.getValue().size(); i < im; i++) {
					LibRec.paramIdx = i;
					runAlgorithm();

					// useful for some methods which do not use the parameters
					// defined in Recommender.params
					if (!isMultRun)
						break;
				}
			}

		} else {
			// single run
			runAlgorithm();
		}

		// collect results
		String destPath = FileIO.makeDirectory("Results");
		String dest = destPath + algorithm + "@" + Dates.now() + ".txt";
		FileIO.copyFile("results.txt", dest);

		notifyMe(dest);
	}

	/**
	 * general interface to run a recommendation algorithm
	 */
	private static void runAlgorithm() throws Exception {
		String testPath = cf.getPath("dataset.testing");

		if (cf.getString("recommender").equals("tp"))
			TrustPredictor.update();

		if (!testPath.equals("-1"))
			runTestFile(testPath);
		else if (cf.isOn("is.cross.validation"))
			runCrossValidation();
		else if (cf.getDouble("val.ratio") > 0)
			runRatio();
		else
			runGiven();
	}

	/**
	 * interface to run cross validation approach
	 */
	private static void runCrossValidation() throws Exception {

		int kFold = cf.getInt("num.kfold");
		DataSplitter ds = new DataSplitter(rateMatrix, kFold);

		Thread[] ts = new Thread[kFold];
		Recommender[] algos = new Recommender[kFold];

		boolean isPara = cf.isOn("is.parallel.folds");

		for (int i = 0; i < kFold; i++) {
			Recommender algo = getRecommender(ds.getKthFold(i + 1), i + 1);

			algos[i] = algo;
			ts[i] = new Thread(algo);
			ts[i].start();

			if (!isPara)
				ts[i].join();
		}

		if (isPara)
			for (Thread t : ts)
				t.join();

		// average performance of k-fold
		Map<Measure, Double> avgMeasure = new HashMap<>();
		for (Recommender algo : algos) {
			for (Entry<Measure, Double> en : algo.measures.entrySet()) {
				Measure m = en.getKey();
				double val = avgMeasure.containsKey(m) ? avgMeasure.get(m) : 0.0;
				avgMeasure.put(m, val + en.getValue() / kFold);
			}
		}

		printEvalInfo(algos[0], avgMeasure);
	}

	/**
	 * Interface to run ratio-validation approach
	 */
	private static void runRatio() throws Exception {

		DataSplitter ds = new DataSplitter(rateMatrix);
		double ratio = cf.getDouble("val.ratio");

		Recommender algo = getRecommender(ds.getRatio(ratio), -1);
		algo.execute();

		printEvalInfo(algo, algo.measures);
	}

	/**
	 * Interface to run (Given N)-validation approach
	 */
	private static void runGiven() throws Exception {

		DataSplitter ds = new DataSplitter(rateMatrix);
		int n = cf.getInt("num.given.n");
		double ratio = cf.getDouble("val.given.ratio");

		Recommender algo = getRecommender(ds.getGiven(n > 0 ? n : ratio), -1);
		algo.execute();

		printEvalInfo(algo, algo.measures);
	}

	/**
	 * Interface to run testing using data from an input file
	 * 
	 */
	private static void runTestFile(String path) throws Exception {

		DataDAO testDao = new DataDAO(path, rateDao.getUserIds(), rateDao.getItemIds());
		SparseMatrix testMatrix = testDao.readData(false);

		Recommender algo = getRecommender(new SparseMatrix[] { rateMatrix, testMatrix }, -1);
		algo.execute();

		printEvalInfo(algo, algo.measures);
	}

	/**
	 * print out the evaluation information for a specific algorithm
	 */
	private static void printEvalInfo(Recommender algo, Map<Measure, Double> ms) {

		String result = Recommender.getEvalInfo(ms);
		String time = Dates.parse(ms.get(Measure.TrainTime).longValue()) + ","
				+ Dates.parse(ms.get(Measure.TestTime).longValue());
		String evalInfo = String.format("%s,%s,%s,%s", algo.algoName, result, algo.toString(), time);

		Logs.info(evalInfo);
	}

	/**
	 * @throws Exception
	 * 
	 */
	private static void notifyMe(String dest) throws Exception {
		if (!cf.isOn("is.email.notify"))
			return;

		EMailer notifier = new EMailer();
		Properties props = notifier.getProps();

		props.setProperty("mail.debug", "false");

		String host = cf.getString("mail.smtp.host");
		String port = cf.getString("mail.smtp.port");
		props.setProperty("mail.smtp.host", host);
		props.setProperty("mail.smtp.port", port);
		props.setProperty("mail.smtp.auth", cf.getString("mail.smtp.auth"));

		props.put("mail.smtp.socketFactory.port", port);
		props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

		final String user = cf.getString("mail.smtp.user");
		final String pwd = cf.getString("mail.smtp.password");
		props.setProperty("mail.smtp.user", user);
		props.setProperty("mail.smtp.password", pwd);

		props.setProperty("mail.from", user);
		props.setProperty("mail.to", cf.getString("mail.to"));

		props.setProperty("mail.subject", FileIO.getCurrentFolder() + "." + algorithm + " [" + Systems.getIP() + "]");
		props.setProperty("mail.text", "Program was finished @" + Dates.now());

		String msg = "Program [" + algorithm + "] has been finished !";
		notifier.send(msg, dest);
	}

	/**
	 * @return a recommender to be run
	 */
	private static Recommender getRecommender(SparseMatrix[] data, int fold) throws Exception {

		SparseMatrix trainMatrix = data[0], testMatrix = data[1];
		algorithm = cf.getString("recommender");

		switch (algorithm.toLowerCase()) {

		/* ongoing */
			case "trustsvd":
				return new TrustSVD(trainMatrix, testMatrix, fold);
			case "trustsvd2":
				return new TrustSVD2(trainMatrix, testMatrix, fold);
			case "trustsvd_dt":
				return new TrustSVD_DT(trainMatrix, testMatrix, fold);
			case "trustsvd++":
				return new TrustSVDPlusPlus(trainMatrix, testMatrix, fold);
			case "rbmf":
				return new RBMF(trainMatrix, testMatrix, fold);
			case "fusmrmse":
				return new FUSMrmse(trainMatrix, testMatrix, fold);
			case "fusm":
			case "fusmauc":
				return new FUSMauc(trainMatrix, testMatrix, fold);
			case "fust":
				return new FUSTrmse(trainMatrix, testMatrix, fold);
			case "fustauc":
				return new FUSTauc(trainMatrix, testMatrix, fold);
			case "tp":
				return new TrustPredictor(trainMatrix, testMatrix, fold);
			case "sbpr":
				return new SBPR(trainMatrix, testMatrix, fold);
			case "gbpr":
				return new GBPR(trainMatrix, testMatrix, fold);

			case "aaai-basemf":
				return new BaseMF(trainMatrix, testMatrix, fold);
			case "aaai-dmf":
				return new DMF(trainMatrix, testMatrix, fold);
			case "aaai-basenm":
				return new BaseNM(trainMatrix, testMatrix, fold);
			case "aaai-dnm":
				return new DNM(trainMatrix, testMatrix, fold);
			case "aaai-drm":
				return new DRM(trainMatrix, testMatrix, fold);

				/* item ranking */
			case "rankals":
				return new RankALS(trainMatrix, testMatrix, fold);
			case "ranksgd":
				return new RankSGD(trainMatrix, testMatrix, fold);
			case "climf":
				return new CLiMF(trainMatrix, testMatrix, fold);
			case "bpr":
				return new BPR(trainMatrix, testMatrix, fold);
			case "wrmf":
				return new WRMF(trainMatrix, testMatrix, fold);
			case "slim":
				return new SLIM(trainMatrix, testMatrix, fold);
			case "fismrmse":
				return new FISMrmse(trainMatrix, testMatrix, fold);
			case "fism":
			case "fismauc":
				return new FISMauc(trainMatrix, testMatrix, fold);

				/* user ratings */
			case "userknn":
				return new UserKNN(trainMatrix, testMatrix, fold);
			case "itemknn":
				return new ItemKNN(trainMatrix, testMatrix, fold);
			case "regsvd":
				return new RegSVD(trainMatrix, testMatrix, fold);
			case "biasedmf":
				return new BiasedMF(trainMatrix, testMatrix, fold);
			case "svd++":
				return new SVDPlusPlus(trainMatrix, testMatrix, fold);
			case "pmf":
				return new PMF(trainMatrix, testMatrix, fold);
			case "bpmf":
				return new BPMF(trainMatrix, testMatrix, fold);
			case "socialmf":
				return new SocialMF(trainMatrix, testMatrix, fold);
			case "trustmf":
				return new TrustMF(trainMatrix, testMatrix, fold);
			case "rste":
				return new RSTE(trainMatrix, testMatrix, fold);
			case "sorec":
				return new SoRec(trainMatrix, testMatrix, fold);
			case "soreg":
				return new SoReg(trainMatrix, testMatrix, fold);

				/* baselines */
			case "globalavg":
				return new GlobalAverage(trainMatrix, testMatrix, fold);
			case "useravg":
				return new UserAverage(trainMatrix, testMatrix, fold);
			case "itemavg":
				return new ItemAverage(trainMatrix, testMatrix, fold);
			case "random":
				return new RandomGuess(trainMatrix, testMatrix, fold);
			case "constant":
				return new ConstantGuess(trainMatrix, testMatrix, fold);
			case "mostpop":
				return new MostPopular(trainMatrix, testMatrix, fold);

				/* extension */
			case "nmf":
				return new NMF(trainMatrix, testMatrix, fold);
			case "hybrid":
				return new Hybrid(trainMatrix, testMatrix, fold);
			case "slopeone":
				return new SlopeOne(trainMatrix, testMatrix, fold);
			case "pd":
				return new PD(trainMatrix, testMatrix, fold);
			case "ar":
				return new AR(trainMatrix, testMatrix, fold);
			case "prankd":
				return new PRankD(trainMatrix, testMatrix, fold);

			default:
				throw new Exception("No recommender is specified!");
		}
	}

	/**
	 * Print out debug information
	 */
	private static void debugInfo() {
		String cv = "kFold: " + cf.getInt("num.kfold")
				+ (cf.isOn("is.parallel.folds") ? " [Parallel]" : " [Singleton]");

		float ratio = (float) cf.getDouble("val.ratio");
		int givenN = cf.getInt("num.given.n");
		float givenRatio = cf.getFloat("val.given.ratio");

		String cvInfo = cf.isOn("is.cross.validation") ? cv : (ratio > 0 ? "ratio: " + ratio : "given: "
				+ (givenN > 0 ? givenN : givenRatio));

		String testPath = cf.getPath("dataset.testing");
		boolean isTestingFlie = !testPath.equals("-1");
		String mode = isTestingFlie ? String.format("Testing:: %s.", Strings.last(testPath, 38)) : cvInfo;

		if (!Recommender.isRankingPred) {
			String view = cf.getString("rating.pred.view");
			switch (view.toLowerCase()) {
				case "cold-start":
					mode += ", " + view;
					break;
				case "trust-degree":
					mode += String.format(", %s [%d, %d]",
							new Object[] { view, cf.getInt("min.trust.degree"), cf.getInt("max.trust.degree") });
					break;
				case "all":
				default:
					break;
			}
		}

		String debugInfo = String.format("Training: %s, %s", Strings.last(cf.getPath("dataset.training"), 38), mode);
		Logs.info(debugInfo);
	}

	/**
	 * Print out software information
	 */
	public static String readme() {
		return "\nLibRec " + version + " Copyright (C) 2014 Guibing Guo \n\n"

		/* Description */
		+ "LibRec is free software: you can redistribute it and/or modify \n"
				+ "it under the terms of the GNU General Public License as published by \n"
				+ "the Free Software Foundation, either version 3 of the License, \n"
				+ "or (at your option) any later version. \n\n"

				/* Usage */
				+ "LibRec is distributed in the hope that it will be useful, \n"
				+ "but WITHOUT ANY WARRANTY; without even the implied warranty of \n"
				+ "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the \n"
				+ "GNU General Public License for more details. \n\n"

				/* licence */
				+ "You should have received a copy of the GNU General Public License \n"
				+ "along with LibRec. If not, see <http://www.gnu.org/licenses/>.";
	}
}
