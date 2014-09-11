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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.undefined;

import happy.coding.io.Configer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import librec.intf.Recommender;

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
		addMKey(cf, params, "val.reg");
		addMKey(cf, params, "val.reg.social");
		addMKey(cf, params, "val.reg.distrust");

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

			List<Double> vals = params.get(key);
			int maxIdx = vals.size() - 1;
			int idx = LibRec.paramIdx > maxIdx ? maxIdx : LibRec.paramIdx;

			alpha = vals.get(idx);
			LibRec.isMultRun = true;
		} else {
			alpha = Recommender.cf.getDouble(key);
			// LibRec.isMultRun = false;
		}

		return alpha;
	}

}
