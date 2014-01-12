package happy.research.data;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.system.Systems;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Test;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Table;

public class CiaoDataset {

	public final static String dirPath = "D:\\Java\\Workspace2\\CF-RS\\Datasets\\Ciao\\";
	public final static String ratingSet = dirPath + "movie-ratings.txt";
	public final static String reviewSet = dirPath + "review-ratings.txt";
	public final static String trustSet = dirPath + "trusts.txt";

	/**
	 * Sample a sample dataset for our work
	 * 
	 * @throws Exception
	 */
	@Test
	public void sample() throws Exception {

		Table<String, String, String> reviews = loadMovieReviewSet(ratingSet);

		Table<String, String, Integer> ratings = loadReviewSet(reviewSet);
		Table<String, String, Integer> trusts = loadTrustSet(trustSet);

		String sep = ",";

		List<String> sampleRatings = new ArrayList<>();
		List<String> sampleReviews = new ArrayList<>();

		// first: determine users and trustors
		Set<String> users = new HashSet<>();
		users.addAll(trusts.rowKeySet());
		users.addAll(trusts.columnKeySet());

		Set<String> trustors = new HashSet<>(trusts.rowKeySet());

		// determine reviews
		Set<String> rvs = new HashSet<>();
		for (String trustor : trustors) {

			// {movie : review}
			Map<String, String> rws = reviews.row(trustor);
			for (Entry<String, String> en : rws.entrySet()) {
				// String mv = en.getKey();
				String rv = en.getValue();

				rvs.add(rv);
				sampleReviews.add(trustor + sep + rv);
			}
		}

		// determine ratings
		for (String user : users) {
			Map<String, Integer> rts = ratings.row(user);
			for (Entry<String, Integer> en : rts.entrySet()) {
				String rv = en.getKey();

				if (rvs.contains(rv))
					sampleRatings.add(user + sep + rv + sep + en.getValue());
			}
		}

		// output datasets
		String outPath = Systems.getDesktop();
		FileIO.writeList(outPath + "review-ratings.txt", sampleRatings);
		FileIO.writeList(outPath + "user-reviews.txt", sampleReviews);
	}

	/**
	 * Compute the statistics of the CiaoDVDs dataset
	 * 
	 * @param dirPath
	 *            the directory path of the dataset
	 * @throws Exception
	 */
	@Test
	public void statistics() throws Exception {
		// movie ratings: {user, movie, rating}
		Table<String, String, Integer> ratings = loadRatingSet(ratingSet);

		Multiset<Integer> scales = HashMultiset.create();
		scales.addAll(ratings.values());
		Logs.info("Movie rating scales:");
		Logs.info(scales.toString());
		Logs.info("Users: {}, movies: {}, ratings: {}\n", new Object[] {
				ratings.rowKeySet().size(), ratings.columnKeySet().size(),
				ratings.size() });

		// review ratings: {user, review, rating}
		Table<String, String, Integer> reviews = loadReviewSet(reviewSet);
		scales.clear();
		scales.addAll(reviews.values());
		Logs.info("Movie review scales:");
		Logs.info(scales.toString());
		Logs.info("Users: {}, reviews: {}, ratings: {}\n", new Object[] {
				reviews.rowKeySet().size(), reviews.columnKeySet().size(),
				reviews.size() });

		// trust ratings: {trustor, trustee, rating}
		Table<String, String, Integer> trusts = loadTrustSet(trustSet);
		Logs.info("Trustors: {}, trustees: {}, trusts: {}\n",
				new Object[] { trusts.rowKeySet().size(),
						trusts.columnKeySet().size(), trusts.size() });

		Set<String> users = new HashSet<>();
		users.addAll(ratings.rowKeySet());
		users.addAll(reviews.rowKeySet());
		users.addAll(trusts.rowKeySet());
		users.addAll(trusts.columnKeySet());
		Logs.info("Overall users: {}", users.size());

	}

	/**
	 * load the dataset of trust info
	 * 
	 * @param trustsPath
	 *            the path to the user-trust dataset
	 * @return the trusts dataset table: {trustor, trustee, rating}
	 * @throws Exception
	 */
	protected Table<String, String, Integer> loadTrustSet(String trustsPath)
			throws Exception {
		BufferedReader br;
		String line;
		Table<String, String, Integer> trusts = HashBasedTable.create();
		br = new BufferedReader(new FileReader(new File(trustsPath)));
		while ((line = br.readLine()) != null) {
			String[] data = line.split(",");
			trusts.put(data[0], data[1], Integer.parseInt(data[2]));
		}
		br.close();
		return trusts;
	}

	/**
	 * load the dataset of review ratings;
	 * 
	 * @param reviewsPath
	 *            the path to the review-ratings dataset
	 * @return the review dataset table: {user, review, rating}
	 * @throws Exception
	 */
	protected Table<String, String, Integer> loadReviewSet(String reviewsPath)
			throws FileNotFoundException, IOException {
		BufferedReader br;
		String line;
		Table<String, String, Integer> reviews = HashBasedTable.create();
		br = new BufferedReader(new FileReader(new File(reviewsPath)));
		while ((line = br.readLine()) != null) {
			String[] data = line.split(",");
			reviews.put(data[0], data[1], Integer.parseInt(data[2]));
		}
		br.close();
		return reviews;
	}

	/**
	 * load the dataset of movie ratings;
	 * 
	 * @param ratingsPath
	 *            the path to the moviep-rating dataset
	 * @return the rating dataset table: {user, movie, rating}
	 * @throws Exception
	 */
	protected Table<String, String, Integer> loadRatingSet(String ratingsPath)
			throws Exception {

		Table<String, String, Integer> ratings = HashBasedTable.create();
		BufferedReader br = new BufferedReader(new FileReader(new File(
				ratingsPath)));
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split(",");
			ratings.put(data[0], data[1], Integer.parseInt(data[4]));
		}
		br.close();

		return ratings;
	}

	/**
	 * load the dataset of movie reviews;
	 * 
	 * @param ratingsPath
	 *            the path to the moviep-rating dataset
	 * @return the rating dataset table: {user, movie, review}
	 * @throws Exception
	 */
	protected Table<String, String, String> loadMovieReviewSet(
			String ratingsPath) throws Exception {

		Table<String, String, String> dataset = HashBasedTable.create();
		BufferedReader br = new BufferedReader(new FileReader(new File(
				ratingsPath)));
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split(",");
			dataset.put(data[0], data[1], data[3]);
		}
		br.close();

		return dataset;
	}

}
