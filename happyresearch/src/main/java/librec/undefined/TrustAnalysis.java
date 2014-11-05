package librec.undefined;

import happy.coding.io.Configer;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.math.Sims;
import happy.coding.math.Stats;
import happy.coding.system.Debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import librec.data.DataDAO;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;

import com.google.common.collect.BiMap;

public class TrustAnalysis {

	public static void main(String[] args) throws Exception {
		Configer cf = new Configer("librec.conf");

		DataDAO dao = new DataDAO(cf.getPath("dataset.training"));
		SparseMatrix rateMatrix = dao.readData();

		BiMap<String, Integer> uids = dao.getUserIds();
		int numUsers = uids.size();

		DataDAO socialDao = new DataDAO(cf.getPath("dataset.social"), uids);
		SparseMatrix socialMatrix = socialDao.readData();

		if (Debug.OFF) {

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

		if (Debug.OFF) {
			// user id, average rating
			SparseVector iv = new SparseVector(uids.size());
			for (int u = 0, um = uids.size(); u < um; u++) {
				if (u < numUsers) {
					SparseVector ru = rateMatrix.row(u);
					if (ru.getCount() > 0)
						iv.set(u, ru.mean());
				}
			}

			// trusted neighbors average
			SparseVector jv = new SparseVector(uids.size());
			for (int u = 0, um = uids.size(); u < um; u++) {
				if (u < numUsers) {
					SparseVector tu = socialMatrix.row(u);

					int count = tu.getCount();
					if (count > 0) {
						// calculate average 
						double sum = 0;
						for (VectorEntry ve : tu) {
							int v = ve.index();
							double avg = iv.get(v);
							sum += avg;
						}

						double mean = sum / count;
						jv.set(u, mean);
					}
				}
			}

			// compute similarity
			List<Double> is = new ArrayList<>();
			List<Double> js = new ArrayList<>();

			for (Integer idx : jv.getIndex()) {
				if (iv.contains(idx)) {
					is.add(iv.get(idx));
					js.add(jv.get(idx));
				}
			}

			double sim = Sims.pcc(is, js);
			Logs.debug("Similarity = {}", sim);
		}

		if (Debug.ON) {
			// trusters
			List<Double> sims = new ArrayList<>();
			for (int u = 0, um = uids.size(); u < um; u++) {
				if (u < numUsers) {
					SparseVector ru = rateMatrix.row(u);
					SparseVector su = socialMatrix.column(u);

					List<Double> is = new ArrayList<>();
					List<Double> js = new ArrayList<>();

					for (VectorEntry ve : ru) {
						int i = ve.index();
						double rui = ve.get();

						// avg rating from trusting users
						double sum = 0;
						int count = 0;

						for (VectorEntry se : su) {
							int v = se.index();

							if (v < numUsers) {
								double rvi = rateMatrix.get(v, i);
								if (rvi > 0) {
									sum += rvi;
									count++;
								}
							}
						}

						if (count > 0) {
							is.add(rui);
							js.add(sum / count);
						}
					}

					if (is.size() >= 2) {
						double sim = Sims.pcc(is, js);
						if (!Double.isNaN(sim))
							sims.add(sim);
					}
				}
			}

			//FileIO.writeList(FileIO.desktop + "sims.txt", sims);
			Logs.debug("mean = {}, std = {}", Stats.mean(sims), Stats.sd(sims));

			// 0: <=0; 1: (0.0, 0.1], 2: 0.1-0.2, ..., 9: 0.9-1.0
			Map<Integer, Integer> map = new HashMap<>();
			map.put(0, 0);
			map.put(1, 0);
			map.put(2, 0);
			map.put(3, 0);
			map.put(4, 0);
			map.put(5, 0);
			map.put(6, 0);
			map.put(7, 0);
			map.put(8, 0);
			map.put(9, 0);

			for (double sim : sims) {
				if (sim <= 0)
					map.put(0, map.get(0) + 1);

				for (int i = 9; i > 0; i--)
					if (sim > i * 0.1) {
						map.put(i, map.get(i) + 1);
						break;
					}
			}

			Logs.debug("\n{}", Strings.toString(map));
		}
		
		if (Debug.ON) {
			// trustees
			List<Double> sims = new ArrayList<>();
			for (int u = 0, um = uids.size(); u < um; u++) {
				if (u < numUsers) {
					SparseVector ru = rateMatrix.row(u);
					SparseVector su = socialMatrix.row(u);
					
					List<Double> is = new ArrayList<>();
					List<Double> js = new ArrayList<>();
					
					for (VectorEntry ve : ru) {
						int i = ve.index();
						double rui = ve.get();
						
						// avg rating from trusted users
						double sum = 0;
						int count = 0;
						
						for (VectorEntry se : su) {
							int v = se.index();
							
							if (v < numUsers) {
								double rvi = rateMatrix.get(v, i);
								if (rvi > 0) {
									sum += rvi;
									count++;
								}
							}
						}
						
						if (count > 0) {
							is.add(rui);
							js.add(sum / count);
						}
					}
					
					if (is.size() >= 2) {
						double sim = Sims.pcc(is, js);
						if (!Double.isNaN(sim))
							sims.add(sim);
					}
				}
			}
			
			//FileIO.writeList(FileIO.desktop + "sims.txt", sims);
			Logs.debug("mean = {}, std = {}", Stats.mean(sims), Stats.sd(sims));
			
			// 0: <=0; 1: (0.0, 0.1], 2: 0.1-0.2, ..., 9: 0.9-1.0
			Map<Integer, Integer> map = new HashMap<>();
			map.put(0, 0);
			map.put(1, 0);
			map.put(2, 0);
			map.put(3, 0);
			map.put(4, 0);
			map.put(5, 0);
			map.put(6, 0);
			map.put(7, 0);
			map.put(8, 0);
			map.put(9, 0);
			
			for (double sim : sims) {
				if (sim <= 0)
					map.put(0, map.get(0) + 1);
				
				for (int i = 9; i > 0; i--)
					if (sim > i * 0.1) {
						map.put(i, map.get(i) + 1);
						break;
					}
			}
			
			Logs.debug("\n{}", Strings.toString(map));
		}

	}
}
