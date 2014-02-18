package librec.undefined;

import happy.coding.io.Configer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import librec.intf.Recommender;
import librec.main.LibRec;

/**
 * Recommender Utility Class for Configing Recommenders
 * 
 * @author guoguibing
 * 
 */
public class RecUtils {

	public static Map<String, List<Double>> buildParams(Configer cf) {
		Map<String, List<Double>> params = new HashMap<>();

		// regularization
		addMKey(cf, params, "val.reg.user");
		addMKey(cf, params, "val.reg.item");
		addMKey(cf, params, "val.reg.social");

		return params;
	}

	private static void addMKey(Configer cf, Map<String, List<Double>> params, String key) {
		List<Double> values = cf.getRange(key);
		if (values.size() > 1)
			params.put(key, values);
	}

	/**
	 * get the current value for key which supports multiple runs
	 * 
	 * @param params
	 *            parameter-values map
	 * @param key
	 *            parameter key
	 * @return current value for a parameter
	 */
	public static double getMKey(Map<String, List<Double>> params, String key) {
		double alpha = 0;
		if (params != null && params.containsKey(key)) {
			alpha = params.get(key).get(LibRec.paramIdx);
			LibRec.isMultRun = true;
		} else {
			alpha = Recommender.cf.getDouble(key);
			LibRec.isMultRun = false;
		}

		return alpha;
	}
	
}
