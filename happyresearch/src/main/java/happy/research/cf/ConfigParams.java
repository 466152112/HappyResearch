package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.system.Dates;
import happy.coding.system.Systems;
import happy.research.utils.SimUtils.SimMethod;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Usage: ConfigParams.defaultInstance();
 * 
 * @author guoguibing
 */
public class ConfigParams
{
	private static ConfigParams	params;
	private static Properties	p;

	public int					kNN							= 0;
	public int					TOP_N						= 0;
	public int					numRunMethod				= 0;

	public int					RUNTIME_THREADS				= 1;
	public int					TCF_ITERATION				= 1;
	public int					RUNTIME_PROGRESS_STEP		= 100;
	public int					TRUST_PROPERGATION_LENGTH	= 1;
	public String				RESULTS_DIRECTORY			= null;

	public boolean				CLASSIC_CF, HYBRID_CT, TCF, VR_CF, MultAspect, auto_trust_sets;
	public boolean				MOLETRUST, TIDALTRUST, kNRTrust, COGTRUST;
	public boolean				TRUST_ALL, TRUST_ALL_PLUS, GLOBAL_AVERAGE, TRUST_WALKER;

	public boolean				MERGE_TCF, MERGE_TCF2, MERGE_DTN, TCF_MERGE, TCF2_MERGE, DT_IMPUTE;
	public boolean				RECONSTRUCTION, EMAIL_NOTIFICATION;

	public boolean				MF_CV;

	public double				SIMILARITY_THRESHOLD		= 0.0;
	public double				SIGNIFICANCE_THRESHOLD		= 0.0;
	public boolean				AUTO_SIMILARITY				= false;
	public boolean				AUTO_CONFIDENCE				= false;
	public boolean				AUTO_SIGNIFICANCE			= false;
	public boolean				AUTO_TRUST					= false;
	public boolean				AUTO_SIGMA					= false;
	public boolean				AUTO_KNN					= false;
	public boolean				AUTO_TOPN					= false;
	public boolean				AUTO_VIEWS					= false;
	public boolean				AUTO_CV						= false;
	public boolean				BATCH_RUN					= false;
	public double				CONFIDENCE_THRESHOLD		= 0.0;
	public double				TRUST_THRESHOLD				= 0.0;
	public double				COGTRUST_ALPHA				= 0.0;
	public double				COGTRUST_BIAS				= 0.0;
	public double				COGTRUST_EPSILON			= 0.0;

	public double				X_SIGMA						= 0;

	public String				TRAIN_SET					= null;
	public String				TEST_SET					= null;

	public enum DatasetMode {
		all("All"), coldUsers("Cold Users"), heavyUsers("Heavy Users"), opinUsers("Opin. Users"), blackSheep(
				"Black Sheep"), nicheItems("Niche Items"), contrItems("Contr. Items"), batch("Batch");

		public String	label	= null;

		DatasetMode(String _label)
		{
			this.label = _label;
		}

	}

	public enum ValidateMethod {
		leave_one_out, cross_validation
	}

	public enum PredictMethod {
		weighted_average, resnick_formula, remove_bias
	}

	public SimMethod		SIMILARITY_METHOD	= SimMethod.PCC;
	public DatasetMode		DATASET_MODE		= DatasetMode.all;

	public ValidateMethod	VALIDATE_METHOD		= ValidateMethod.leave_one_out;
	public PredictMethod	PREDICT_METHOD		= PredictMethod.weighted_average;

	private ConfigParams()
	{}

	public static ConfigParams defaultInstance() throws Exception
	{
		return defaultInstance("cf.conf");
	}

	public static ConfigParams defaultInstance(String config) throws Exception
	{
		if (params == null)
		{
			params = new ConfigParams();
			params.loadConfig(config);
		}

		return params;
	}

	public String readParam(String key)
	{
		return p.getProperty(key);
	}

	public String readParam(String key, String val)
	{
		return p.getProperty(key, val);
	}

	public double readDouble(String key)
	{
		return Double.parseDouble(readParam(key));
	}

	public int readInt(String key)
	{
		return Integer.parseInt(readParam(key));
	}

	public List<Double> readDoubleList(String key)
	{
		List<Double> data = new ArrayList<>();
		String val = readParam(key);
		String[] values = val.split(",");
		for (String value : values)
		{
			double d = Double.parseDouble(value);
			data.add(d);
		}

		return data;
	}

	private void loadConfig(String configFile) throws Exception
	{
		p = new Properties();
		p.load(new FileInputStream(configFile));

		RUNTIME_THREADS = readInt("runtime.threads");
		RUNTIME_PROGRESS_STEP = readInt("runtime.progress.step");
		RESULTS_DIRECTORY = FileIO.makeDirPath(readParam("results.directory"));
		TRUST_PROPERGATION_LENGTH = readInt("trust.propagation.length");

		SIMILARITY_METHOD = simMethod(p, "similarity.method");
		String similarity = readParam("similarity.threshold");
		if (similarity != null && "batch".equalsIgnoreCase(similarity))
		{
			AUTO_SIMILARITY = true;
			BATCH_RUN = true;
		} else SIMILARITY_THRESHOLD = Double.parseDouble(similarity);

		String confidence = readParam("confidence.threshold");
		if (confidence != null && "batch".equalsIgnoreCase(confidence))
		{
			AUTO_CONFIDENCE = true;
			BATCH_RUN = true;
		} else CONFIDENCE_THRESHOLD = Double.parseDouble(confidence);

		String significance = readParam("significance.threshold");
		if (significance != null && "batch".equalsIgnoreCase(significance))
		{
			AUTO_SIGNIFICANCE = true;
			BATCH_RUN = true;
		} else SIGNIFICANCE_THRESHOLD = Double.parseDouble(significance);

		String trust = readParam("trust.threshold");
		if (trust != null && "batch".equalsIgnoreCase(trust))
		{
			AUTO_TRUST = true;
			BATCH_RUN = true;
		} else TRUST_THRESHOLD = Double.parseDouble(trust);

		String sigmas = readParam("bs.x.sigma");
		if (sigmas != null && "batch".equalsIgnoreCase(sigmas))
		{
			AUTO_SIGMA = true;
			BATCH_RUN = true;
		} else X_SIGMA = Double.parseDouble(sigmas);

		COGTRUST_ALPHA = Double.parseDouble(readParam("CogTrust.alpha"));
		COGTRUST_BIAS = Double.parseDouble(readParam("CogTrust.bias"));
		COGTRUST_EPSILON = Double.parseDouble(readParam("CogTrust.epsilon"));

		// training and test sets
		TRAIN_SET = readParam("train.sets", null);
		TEST_SET = readParam("test.sets", null);

		String knn = readParam("kNN");
		if (knn != null && "batch".equalsIgnoreCase(knn))
		{
			AUTO_KNN = true;
			BATCH_RUN = true;
		} else kNN = Integer.parseInt(knn);

		String topN = readParam("top.n");
		if (topN != null && "batch".equalsIgnoreCase(topN))
		{
			AUTO_TOPN = true;
			BATCH_RUN = true;
		} else TOP_N = Integer.parseInt(topN);

		DATASET_MODE = getDatasetMode(p, "dataset.mode");
		if (DATASET_MODE == DatasetMode.batch)
		{
			AUTO_VIEWS = true;
			BATCH_RUN = true;
		}
		/*
		 * -------------------------- Assemble Dataset class ---------------------
		 */

		VALIDATE_METHOD = getValidateMethod(p, "validating.method");
		PREDICT_METHOD = getPredictMethod(p, "predicting.method");
		AUTO_CV = setFlag(p, "cross.validation.batch");
		if (AUTO_CV) BATCH_RUN = true;

		Dataset.init(readParam("run.dataset").trim());
		Dataset.DIRECTORY = readParam("dataset.directory");
		Dataset.DIRECTORY = Dataset.DIRECTORY.replaceAll("[$]run.dataset[$]", Dataset.LABEL);

		String dir = Dataset.DIRECTORY;
		String subLabel = dir.substring(dir.indexOf(Dataset.LABEL) + Dataset.LABEL.length() + 1);

		if (VALIDATE_METHOD == ValidateMethod.cross_validation) Dataset.DIRECTORY += "5fold";
		Dataset.DIRECTORY = FileIO.makeDirPath(Dataset.DIRECTORY);

		switch (Systems.getOs())
		{
			case Windows:
				Dataset.TEMP_DIRECTORY = "D:\\Data\\" + Dataset.LABEL + "\\" + subLabel;
				break;
			default:
				String str = readParam("dataset.temp.directory");
				Dataset.TEMP_DIRECTORY = str.replaceAll("[$]run.dataset[$]", Dataset.LABEL) + subLabel;
				Dataset.TEMP_DIRECTORY = FileIO.makeDirPath(Dataset.TEMP_DIRECTORY);
				break;
		}

		/*
		 * --------------------------- Run Methods ------------------------------
		 */
		MF_CV = setFlag(p, "MF.cv.run");

		// leave one out
		CLASSIC_CF = setFlag(p, "Classic.cf.run");
		VR_CF = setFlag(p, "VR.cf.run");
		HYBRID_CT = setFlag(p, "Hybrid.ct.run");
		TCF = setFlag(p, "TCF.cf.run");
		TCF_ITERATION = readInt("TCF.iteration");

		TRUST_ALL = setFlag(p, "Trust.all.run");
		TRUST_ALL_PLUS = setFlag(p, "Trust.all.plus.run");
		GLOBAL_AVERAGE = setFlag(p, "Global.average.run");

		MOLETRUST = setFlag(p, "MoleTrust.run");
		TIDALTRUST = setFlag(p, "TidalTrust.run");
		kNRTrust = setFlag(p, "kNRTrust.run");

		MERGE_TCF = setFlag(p, "Merge.TCF.run");
		MERGE_TCF2 = setFlag(p, "Merge.TCF2.run");
		MERGE_DTN = setFlag(p, "Merge.DTN.run");

		TCF_MERGE = setFlag(p, "TCF.Merge.run");
		TCF2_MERGE = setFlag(p, "TCF2.Merge.run");
		DT_IMPUTE = setFlag(p, "DT.Impute.run");
		COGTRUST = setFlag(p, "CogTrust.run");

		MultAspect = setFlag(p, "MultAspect.run");
		auto_trust_sets = setFlag(p, "auto.trust.sets");

		TRUST_WALKER = setFlag(p, "trust.walker.run");
		RECONSTRUCTION = setFlag(p, "reconstruction.run");
		EMAIL_NOTIFICATION = setFlag(p, "results.email.notification");

	}

	private DatasetMode getDatasetMode(Properties p, String label)
	{
		String valueString = readParam(label, DatasetMode.all.name());
		for (DatasetMode m : DatasetMode.values())
		{
			if (m.name().equalsIgnoreCase(valueString)) return m;
		}
		return DatasetMode.all;
	}

	private SimMethod simMethod(Properties p, String label)
	{
		String value = readParam(label, DatasetMode.all.name());
		for (SimMethod m : SimMethod.values())
		{
			if (m.name().equalsIgnoreCase(value)) return m;
		}
		return SimMethod.PCC;
	}

	private ValidateMethod getValidateMethod(Properties p, String label)
	{
		String valueString = readParam(label, ValidateMethod.leave_one_out.name());
		for (ValidateMethod m : ValidateMethod.values())
		{
			if (m.name().equalsIgnoreCase(valueString)) return m;
		}
		return ValidateMethod.leave_one_out;
	}

	private PredictMethod getPredictMethod(Properties p, String label)
	{
		String valueString = readParam(label, PredictMethod.weighted_average.name());
		for (PredictMethod m : PredictMethod.values())
		{
			if (m.name().equalsIgnoreCase(valueString)) return m;
		}
		return PredictMethod.weighted_average;
	}

	private boolean setFlag(Properties p, String str)
	{
		String valueString = readParam(str, "off").trim();

		if (valueString.equalsIgnoreCase("on"))
		{
			if (str.endsWith(".run")) numRunMethod++;
			return true;
		}

		return false;
	}

	public void printSpecs()
	{
		Logs.info("Starts at {}", Dates.now());
		Logs.info(null);

		String suffix = Strings.repeat('-', 22);
		Logs.info(suffix + " Global Setting " + suffix);
		Logs.info("Currently data set   = {} ({})", Dataset.LABEL, Dataset.DIRECTORY);
		Logs.info("Validation approach  = {}", VALIDATE_METHOD.name());
		Logs.info("Prediction method    = {}", PREDICT_METHOD.name());
		Logs.info("Calculate similarity = {}", params.SIMILARITY_METHOD);

		Logs.info(null);
		Logs.info(suffix + " Method Results " + suffix);
	}
}
