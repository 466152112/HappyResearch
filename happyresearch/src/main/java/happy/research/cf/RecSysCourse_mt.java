package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.FileIO.MapWriter;
import happy.coding.io.KeyValPair;
import happy.coding.io.Lists;
import happy.coding.io.Strings;
import happy.coding.math.Sims;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class RecSysCourse_mt extends DefaultCF_mt {
	private final static String dir = "D:\\Dropbox\\PhD\\My Work\\Algorithms\\@Machine Learning\\RecSys\\Assignments\\";

	public RecSysCourse_mt() {
		methodId = "RecSys-Course CF";
	}

	@Override
	protected Performance runMultiThreads() throws Exception {
		A4();

		return null;
	}

	protected void A4() throws Exception {
		String dirPath = dir + "A4" + File.separator;

		Map<String, Map<String, Double>> corrs = new HashMap<>();
		for (String a : userRatingsMap.keySet()) {
			Map<String, Double> ucs = new HashMap<>();

			Map<String, Rating> asRatings = userRatingsMap.get(a);
			for (String b : userRatingsMap.keySet()) {
				if (a == b) {
					ucs.put(b, 1.0);
				} else {
					Map<String, Rating> bsRatings = userRatingsMap.get(b);
					List<Double> as = new ArrayList<>();
					List<Double> bs = new ArrayList<>();
					for (String ai : asRatings.keySet()) {
						if (bsRatings.containsKey(ai)) {
							as.add(asRatings.get(ai).getRating());
							bs.add(bsRatings.get(ai).getRating());
						}
					}
					if (as.size() >= 2) {
						double corr = Sims.pcc(as, bs);
						if (!Double.isNaN(corr))
							ucs.put(b, corr);
					}
				}
			} 
			corrs.put(a, ucs);
		}

		String testA1 = "1648";
		String testA2 = "5136";
		String testB1 = "918";
		String testB2 = "2824";

		float ca = corrs.get(testA1).get(testA2).floatValue();
		float cb = corrs.get(testB1).get(testB2).floatValue();
		assert ca == 0.40298;
		assert cb == -0.31706;

		String[] users = {/* examples */"3712", /* tests */"3867", "860" };
		String file1 = dirPath + "results-part-1.txt";
		String file2 = dirPath + "results-part-2.txt";
		FileIO.deleteFile(file1);
		FileIO.deleteFile(file2);

		for (int k = 0; k < users.length; k++) {
			String user = users[k];
			Map<String, Double> nns = corrs.get(user);
			List<KeyValPair<String>> sorted = Lists.sortMap(nns, true);
			int knn = 0;

			List<KeyValPair<String>> found = new ArrayList<>();
			for (KeyValPair<String> sp : sorted) {
				String u = sp.getKey();
				if (u.equals(user))
					continue;

				found.add(sp);
				if (k == 0)
					System.out.println(sp.getKey() + ":" + sp.getValue());

				knn++;
				if (knn >= 5)
					break;

			}

			// do predictions: part I
			// Map<String, Rating> usRatings = userRatingsMap.get(user);
			Map<String, Double> itemPreds = new HashMap<>();
			for (String item : itemRatingsMap.keySet()) {
				// if (usRatings.containsKey(item)) continue;

				double sum = 0;
				double val = 0;
				for (KeyValPair<String> sp : found) {
					String v = sp.getKey();
					double c = sp.getValue();

					if (userRatingsMap.get(v).containsKey(item)) {
						val += userRatingsMap.get(v).get(item).getRating() * c;
						sum += c;
					}
				}
				if (sum > 0) {
					double pred = val / sum;
					itemPreds.put(item, pred);
				}
			}

			List<KeyValPair<String>> recs = Lists.sortMap(itemPreds, true);
			List<String> lines = new ArrayList<>();
			int recNum = 6;
			int cnt = 0;
			for (int m = 0; m < recs.size(); m++) {
				KeyValPair<String> rec = recs.get(m);
				String val = Strings.toString(rec.getValue(), 3);
				String line = rec.getKey() + " " + val;
				cnt++;
				if (cnt > recNum)
					break;
				lines.add(line);
			}

			String content = Strings.toString(lines);
			if (k == 0)
				System.out.println(content);
			else
				FileIO.writeString(file1, content, true);

			// do prediction - part 2
			itemPreds.clear();
			Map<String, Rating> usRatings = userRatingsMap.get(user);
			double mu = RatingUtils.mean(usRatings, null);
			for (String item : itemRatingsMap.keySet()) {
				// if (usRatings.containsKey(item)) continue;

				double sum = 0;
				double val = 0;
				for (KeyValPair<String> sp : found) {
					String v = sp.getKey();
					double c = sp.getValue();

					Map<String, Rating> vsRatings = userRatingsMap.get(v);
					if (vsRatings.containsKey(item)) {
						double mv = RatingUtils.mean(vsRatings, null);
						double rate = vsRatings.get(item).getRating();

						val += (rate - mv) * c;
						sum += c;
					}
				}
				if (sum > 0) {
					double pred = mu + val / sum;
					itemPreds.put(item, pred);
				}
			}

			recs = Lists.sortMap(itemPreds, true);
			lines.clear();
			cnt = 0;
			for (int m = 0; m < recs.size(); m++) {
				KeyValPair<String> rec = recs.get(m);
				String val = Strings.toString(rec.getValue(), 3);
				String line = rec.getKey() + " " + val;
				cnt++;
				if (cnt > recNum)
					break;
				lines.add(line);
			}

			content = Strings.toString(lines);
			if (k == 0)
				System.out.println(content);
			else
				FileIO.writeString(file2, content, true);

		}
	}

	@Test
	public void convertData() throws Exception {
		String dirPath = dir + "A4" + File.separator;
		String source = dirPath + "recsys-data-sample-rating-matrix.csv";
		List<String> content = FileIO.readAsList(source);

		List<String> users = new ArrayList<>();
		Map<String, Map<String, Double>> data = new HashMap<>();
		String head = content.get(0);
		String[] vals = head.split(",");
		for (String user : vals) {
			if (!user.equals("\"\"")) {
				user = user.replace("\"", "");
				users.add(user);
				data.put(user, new HashMap<String, Double>());
			}
		}

		List<String> items = new ArrayList<>();
		for (int i = 1; i < content.size(); i++) {
			String line = content.get(i);
			vals = line.split(",");
			String item = vals[0].substring(1, vals[0].indexOf(":"));
			items.add(item);

			for (int j = 1; j < vals.length; j++) {
				String rate = vals[j];
				if (!rate.equals("")) {
					String user = users.get(j - 1);
					Map<String, Double> irs = data.get(user);
					irs.put(item, Double.parseDouble(rate));

					data.put(user, irs);
				}
			}
		}

		String filePath = dirPath + "ratings.txt";
		FileIO.deleteFile(filePath);

		for (final String user : users) {
			Map<String, Double> irs = data.get(user);

			FileIO.writeMap(filePath, irs, new MapWriter<String, Double>() {

				@Override
				public String processEntry(String key, Double val) {
					return user + " " + key + " " + val.floatValue();
				}
			}, true);
		}
	}

}
