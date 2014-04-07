package happy.research.tp;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.math.Randoms;
import happy.coding.system.Systems;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import librec.data.DataDAO;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;

import org.junit.Test;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Table;

public class DatasetUtils {

	public final static String sep = ",";

	/**
	 * load user-review dataset
	 * 
	 * @return movie-review dataset: {user, a list of reviews}
	 */
	public static Multimap<String, String> loadReviews(String path) throws Exception {
		Multimap<String, String> dataset = HashMultimap.create();

		BufferedReader br = new BufferedReader(new FileReader(new File(path)));
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split(",");
			dataset.put(data[0], data[1]);
		}

		br.close();
		return dataset;
	}

	/**
	 * load review-ratings dataset, note that the rating is normalized to (0, 1]
	 * 
	 * @return review-ratings dataset: {user, review, rating}
	 */
	public static Table<String, String, Float> loadRatings(String path) throws Exception {
		return loadRatings(path, true);
	}

	public static Table<String, String, Float> loadRatings(String path, boolean normalized) throws Exception {
		Table<String, String, Float> dataset = HashBasedTable.create();

		BufferedReader br = new BufferedReader(new FileReader(new File(path)));
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split(",");
			// TODO: we add 1 to existing ratings to handle the case of 0 for
			// ciao dataset
			int rate = Integer.parseInt(data[2]);

			float rating = rate;
			if (normalized)
				rating = TrustModel.dataset.equals(TrustModel.CiaoDVDs) ? (rate + 1) / 6.0f : rate / 5.0f;

			dataset.put(data[0], data[1], rating);
		}

		br.close();
		return dataset;
	}

	/**
	 * load user-trust dataset
	 * 
	 * @return user-trusts dataset: {trustor, trustee, trust}
	 */
	public static Table<String, String, Integer> loadTrusts(String path) throws Exception {
		Table<String, String, Integer> dataset = HashBasedTable.create();

		BufferedReader br = new BufferedReader(new FileReader(new File(path)));
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split(",");

			dataset.put(data[0], data[1], Integer.parseInt(data[2]));
		}

		br.close();
		return dataset;
	}

	/**
	 * Converted the format of extended epinions dataset' so that our programs
	 * can directly function on the transformed dataset.
	 * 
	 */
	@Test
	public void formatEpinions() throws Exception {
		String dirPath = "D:\\Java\\Workspace\\CF-RS\\Datasets\\ExtendedEpinions\\";
		String sourcePath = dirPath + "Original Dataset\\";
		String destPath = dirPath + "UMAP2014\\";

		Set<String> trustors = new HashSet<>();
		Set<String> users = new HashSet<>();

		Map<String, Integer> userIdMap = new HashMap<>();
		Map<String, Integer> reviewIdMap = new HashMap<>();

		// trusts.txt: {trustorId, trusteeId, trust}
		String destFile = destPath + "trusts.txt";
		FileIO.deleteFile(destFile);
		List<String> contents = new ArrayList<>(2002); // 2000*0.75 = 1500
		BufferedReader br = new BufferedReader(new FileReader(new File(sourcePath + "user_rating.txt")));
		String line = null, newline = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split("\t");
			String trustor = data[0];
			String trustee = data[1];
			int trust = Integer.parseInt(data[2]);
			if (trust == 1) {
				trustors.add(trustor);
				users.add(trustor);
				users.add(trustee);

				if (!userIdMap.containsKey(trustor))
					userIdMap.put(trustor, userIdMap.size() + 1);

				if (!userIdMap.containsKey(trustee))
					userIdMap.put(trustee, userIdMap.size() + 1);

				newline = userIdMap.get(trustor) + sep + userIdMap.get(trustee) + sep + trust;
				contents.add(newline);

				if (contents.size() > 1500) {
					FileIO.writeList(destFile, contents, null, true);
					contents.clear();
				}
			}
		}
		br.close();
		if (contents.size() > 0)
			FileIO.writeList(destFile, contents, null, true);
		contents.clear();

		// user-reviews.txt: {userId,reviewId}
		destFile = destPath + "user-reviews.txt";
		FileIO.deleteFile(destFile);

		br = new BufferedReader(new FileReader(new File(sourcePath + "mc.txt")));
		while ((line = br.readLine()) != null) {
			String[] data = line.split("\\|");
			String userId = data[1];
			String reviewId = data[0];

			// only keep reviews written by trustors
			if (trustors.contains(userId)) {

				if (!reviewIdMap.containsKey(reviewId))
					reviewIdMap.put(reviewId, reviewIdMap.size() + 1);

				newline = userIdMap.get(userId) + sep + reviewIdMap.get(reviewId);
				contents.add(newline);

				if (contents.size() > 1500) {
					FileIO.writeList(destFile, contents, null, true);
					contents.clear();
				}
			}
		}
		br.close();

		if (contents.size() > 0)
			FileIO.writeList(destFile, contents, null, true);
		contents.clear();

		// review-ratings.txt: {userId, reviewId, rating}
		destFile = destPath + "review-ratings.txt";
		FileIO.deleteFile(destFile);

		br = new BufferedReader(new FileReader(new File(sourcePath + "ratings.txt")));
		while ((line = br.readLine()) != null) {
			String[] data = line.split("\t");
			String userId = data[1];
			String reviewId = data[0];
			int rate = Integer.parseInt(data[2]);

			// only keep ratings given by users
			if (users.contains(userId) && reviewIdMap.containsKey(reviewId)) {
				newline = userIdMap.get(userId) + sep + reviewIdMap.get(reviewId) + sep + rate;
				contents.add(newline);

				if (contents.size() > 1500) {
					FileIO.writeList(destFile, contents, null, true);
					contents.clear();
				}
			}
		}
		br.close();
		if (contents.size() > 0)
			FileIO.writeList(destFile, contents, null, true);
		contents.clear();
	}

	/**
	 * Sample a small dataset from the whole and large Epinions dataset
	 * 
	 * @throws Exception
	 */
	@Test
	public void sampleEpinions() throws Exception {

		String dirPath = null;
		switch (Systems.getOs()) {
		case Windows:
			dirPath = "D:\\Java\\Workspace\\CF-RS\\Datasets\\UMAP2014\\";
			break;
		case Linux:
		case Unix:
			dirPath = "/home/gguo1/Java/Workspace/CF-RS/Datasets/UMAP2014/";
			break;
		}

		String sourcePath = FileIO.makeDirPath(dirPath, "Epinions");
		String destPath = FileIO.makeDirectory(dirPath, "Epinions_Sample");
		if (FileIO.exist(destPath))
			FileIO.deleteDirectory(destPath);
		FileIO.makeDirectory(destPath);

		Table<String, String, Integer> trust = loadTrusts(sourcePath + "trusts.txt");
		List<String> ts = new ArrayList<>(trust.rowKeySet());

		// randomly sample 1500 users
		int[] idxes = Randoms.nextNoRepeatIntArray(1500, ts.size());
		List<String> trustors = new ArrayList<>();
		for (int idx : idxes)
			trustors.add(ts.get(idx));

		String newline = null;
		List<String> newlines = new ArrayList<>(2002);
		String destFile = destPath + "trusts.txt";
		FileIO.deleteFile(destFile);

		Set<String> users = new HashSet<>();
		users.addAll(trustors);

		for (String t : trustors) {
			Map<String, Integer> tees = trust.row(t);
			for (Entry<String, Integer> en : tees.entrySet()) {
				String trustee = en.getKey();
				users.add(trustee);

				newline = t + sep + trustee + sep + en.getValue();
				newlines.add(newline);
				if (newlines.size() >= 1500) {
					FileIO.writeList(destFile, newlines, null, true);
					newlines.clear();
				}
			}
		}
		if (newlines.size() > 0)
			FileIO.writeList(destFile, newlines, null, true);
		newlines.clear();

		// user reviews
		Multimap<String, String> urvs = loadReviews(sourcePath + "user-reviews.txt");
		destFile = destPath + "user-reviews.txt";
		FileIO.deleteFile(destFile);

		Set<String> reviews = new HashSet<>();
		for (String u : users) {
			Collection<String> rvs = urvs.get(u);
			reviews.addAll(rvs);

			for (String rv : rvs) {
				newline = u + sep + rv;
				newlines.add(newline);
				if (newlines.size() >= 1500) {
					FileIO.writeList(destFile, newlines, null, true);
					newlines.clear();
				}
			}
		}
		if (newlines.size() > 0)
			FileIO.writeList(destFile, newlines, null, true);
		newlines.clear();

		// review ratings
		Table<String, String, Float> urts = loadRatings(sourcePath + "review-ratings.txt", false);
		destFile = destPath + "review-ratings.txt";
		FileIO.deleteFile(destFile);

		for (String u : users) {
			Map<String, Float> rts = urts.row(u);
			for (String rv : reviews) {
				if (rts.containsKey(rv)) {
					newline = u + sep + rv + sep + rts.get(rv).intValue();
					newlines.add(newline);
					if (newlines.size() >= 1500) {
						FileIO.writeList(destFile, newlines, null, true);
						newlines.clear();
					}
				}
			}
		}
		if (newlines.size() > 0)
			FileIO.writeList(destFile, newlines, null, true);
		newlines.clear();
	}

	@Test
	public void distribution() throws Exception {
		String dirPath = "D:\\Java\\Datasets\\UMAP2014\\CiaoDVDs\\";

		String ratingPath = dirPath + "review-ratings.txt";
		DataDAO rateDao = new DataDAO(ratingPath);
		SparseMatrix rateMatrix = rateDao.readData();
		int rows = rateMatrix.numRows();
		// int cols = rateMatrix.numColumns();

		String reviewPath = dirPath + "user-reviews.txt";
		DataDAO reviewDao = new DataDAO(reviewPath, rateDao.getUserIds(), rateDao.getItemIds());
		SparseMatrix reviewMatrix = reviewDao.readData(new int[] { 0, 1 }, true);

		String trustPath = dirPath + "trusts.txt";
		DataDAO dao = new DataDAO(trustPath);
		SparseMatrix trustMatrix = dao.readData();

		Multiset<Integer> nums = HashMultiset.create();

		for (MatrixEntry me : trustMatrix) {
			int u = me.row();
			int v = me.column();

			// u writes, v rates
			int num = 0;
			SparseVector urs = reviewMatrix.row(u);

			if (v < rows) {
				SparseVector vrs = rateMatrix.row(v);
				for (VectorEntry ve : urs) {
					int rw = ve.index();
					if (vrs.contains(rw))
						num++;
				}
				nums.add(num);
			}

			// u rates, v writes
		}

		Logs.debug(nums);
	}
}
