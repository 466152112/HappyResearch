package librec.undefined;

import happy.coding.io.Configer;
import happy.coding.io.Logs;
import happy.coding.io.Strings;

import java.util.HashMap;
import java.util.Map;

import librec.data.DataDAO;
import librec.data.SparseMatrix;
import librec.data.SparseVector;

import com.google.common.collect.BiMap;

public class TrustAnalysis {

	public TrustAnalysis() {
	}

	public static void main(String[] args) throws Exception {
		Configer cf = new Configer("librec.conf");

		DataDAO dao = new DataDAO(cf.getPath("dataset.training"));
		SparseMatrix rateMatrix = dao.readData();

		BiMap<String, Integer> uids = dao.getUserIds();
		int numUsers = uids.size();

		DataDAO socialDao = new DataDAO(cf.getPath("dataset.social"), uids);
		SparseMatrix socialMatrix = socialDao.readData();

		// keys: {0, 1-5, 6-10, 11-20, 21-max}: 0, 1, 6, 11, 21: number of ratings issued by a user
		// values: number of users who have trust information
		Map<Integer, Integer> map = new HashMap<>();
		map.put(0, 0);
		map.put(1, 0);
		map.put(6, 0);
		map.put(11, 0);
		map.put(21, 0);

		for (int u = 0, um = uids.size(); u < um; u++) {
			SparseVector su = socialMatrix.row(u);
			// have trust information
			if (su.getCount() > 0) {
				int count = 0;
				if (u < numUsers) {
					count = rateMatrix.rowSize(u);
					if (count >= 21)
						count = 21;
					else if (count >= 11)
						count = 11;
					else if (count >= 6)
						count = 6;
					else if (count >= 1)
						count = 1;
				}

				map.put(count, map.get(count) + 1);
			}
		}

		Logs.debug(Strings.toString(map));

	}
}
