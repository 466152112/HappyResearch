package happy.research.data;

import happy.coding.io.FileIO;
import happy.coding.io.FileIO.Converter;
import happy.coding.io.Logs;
import happy.coding.system.Systems;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
 
public class CiaoParser {
	private static String domain = "dvd.ciao.co.uk";
	private static String desktop = Systems.getDesktop();

	public static void main(String[] args) throws Exception {
		String task = "anony";
		switch (task) {
		case "products":
			CiaoParser.parseCategoryPages();
			break;
		case "reviews":
			CiaoParser.parseReviewPages();
			break;
		case "users":
			CiaoParser.getAllUsers();
			break;
		case "ratings-reviews":
			CiaoParser.getAllRatings();
			break;
		case "trust":
			CiaoParser.getAllTrust();
			break;
		case "anony":
			CiaoParser.anonymous();
			break;
		default:
			break;
		}
	}

	public static void parseCategoryPages() throws Exception {
		String filePath = FileIO.getResource("dvd.ciao.txt");
		List<String> urls = FileIO.readAsList(filePath);

		String dir = Systems.getDesktop() + "dvd.ciao.co.uk\\";
		for (String url : urls) {
			// each category
			String[] data = url.split(": ");
			String category = data[0];
			String dirCate = FileIO.makeDirPath(dir, category);
			String dirPath = FileIO.makeDirPath(dir, category, "webPages");

			// clear
			FileIO.deleteFile(dirCate + "movies.txt");

			File dirs = new File(dirPath);
			for (File f : dirs.listFiles()) {
				// each web page
				String html = FileIO.readAsString(dirPath + f.getName());
				Document doc = Jsoup.parse(html);

				Logs.debug(category + ": " + f.getName());

				List<String> movies = new ArrayList<>();
				Elements products = doc.select("td.prodInfo");
				for (Element product : products) {
					// each product
					Element prod = product.select("p.prodName").first();
					String name = prod.text();
					String link = prod.select("a").first().attr("href");
					String id = link.substring(link.lastIndexOf("_") + 1);

					// number of user reviews
					prod = product.select("p.prodRating").first();
					String cnt = prod.select(".userReviewsCount").text()
							.replace("(", "").replace(")", "");
					int count = 0;
					if (!cnt.isEmpty())
						count = Integer.parseInt(cnt);

					// do not consider movies without any reviews
					if (count > 0) {
						String movie = id + "::" + name + "::" + link;
						movies.add(movie);
					}
				}

				FileIO.writeList(dirCate + "movies.txt", movies, null, true);
			}
		}

	}

	public static void getAllUsers() throws Exception {
		String filePath = FileIO.getResource("dvd.ciao.txt");
		List<String> urls = FileIO.readAsList(filePath);

		String dir = Systems.getDesktop() + "dvd.ciao.co.uk\\";
		String userFile = dir + "users.txt";
		Map<String, String> userMap = new HashMap<>();
		for (String url : urls) {
			// each category
			String[] data = url.split(": ");
			String category = data[0];
			String dirPath = FileIO.makeDirPath(dir, category);

			// users
			String userPath = dirPath + "users.txt";
			List<String> users = FileIO.readAsList(userPath);
			for (String line : users) {
				String[] d = line.split(",");
				userMap.put(d[0], d[1]);
			}
		}

		FileIO.writeMap(userFile, userMap);
	}

	public static void getAllTrust() throws Exception {

		Map<String, String> users = new HashMap<>();
		List<String> userLines = FileIO.readAsList(FileIO
				.getResource("users.txt"));
		for (String line : userLines) {
			String[] data = line.split(",");
			users.put(data[1], data[0]);
		}

		String usersPath = FileIO.makeDirPath(desktop, domain,
				"users.ciao.co.uk");
		FileIO.deleteFile(usersPath + "trust.txt");

		File dir = new File(usersPath);
		File[] files = dir.listFiles();

		for (int i = 0, n = files.length; i < n; i++) {
			// for each user
			File userFile = files[i];
			final String userID = userFile.getName();

			String html = null;
			Document doc = null;
			List<String> friends = new ArrayList<>();
			File[] pages = userFile.listFiles();
			for (int j = 0, m = pages.length; j < m; j++) {
				// for each trust page
				File file = pages[j];
				String name = file.getName();
				if (name.startsWith("friends")) {
					html = FileIO.readAsString(file.getPath());
					doc = Jsoup.parse(html);
					Element trustTable = doc.select("form table.trust").first();
					Elements trs = trustTable.select("tbody tr");
					for (Element tr : trs) {
						Element td = tr.select("td").get(1);
						Element a = td.select("a").first();
						String link = a.attr("href");
						if (users.containsKey(link)) {
							friends.add(users.get(link));
						}
					}
				}
			}

			FileIO.writeList(usersPath + "trust.txt", friends,
					new Converter<String, String>() {

						@Override
						public String transform(String friend) {
							return userID + "," + friend + ",1";
						}
					}, true);
		}

	}

	public static void anonymous() throws Exception {
		String dirPath = FileIO.makeDirPath(desktop, domain);
		String ratingFile = dirPath + "ratings.txt";
		String trustFile = dirPath + "trust.txt";
		String reviewFile = dirPath + "review-ratings.txt";
		String sep = ",";

		Map<String, Integer> userIdMap = new HashMap<>();
		Map<String, Integer> movieIdMap = new HashMap<>();
		Map<String, Integer> genreIdMap = new HashMap<>();
		Map<String, Integer> reviewIdMap = new HashMap<>();

		// ratings
		List<String> newlines = new ArrayList<>();
		BufferedReader br = new BufferedReader(new FileReader(new File(
				ratingFile)));
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split(",");
			String user = data[0];
			String movie = data[1];
			String genre = data[2];
			String review = data[3];
			String rating = data[4];
			String date = data[5];

			if (!userIdMap.containsKey(user))
				userIdMap.put(user, userIdMap.size() + 1);
			if (!movieIdMap.containsKey(movie))
				movieIdMap.put(movie, movieIdMap.size() + 1);
			if (!genreIdMap.containsKey(genre))
				genreIdMap.put(genre, genreIdMap.size() + 1);
			if (!reviewIdMap.containsKey(review))
				reviewIdMap.put(review, reviewIdMap.size() + 1);

			String newline = userIdMap.get(user) + sep + movieIdMap.get(movie)
					+ sep + genreIdMap.get(genre) + sep
					+ reviewIdMap.get(review) + sep + rating + sep + date;
			newlines.add(newline);
		}
		br.close();

		FileIO.writeList(dirPath + "ratings-converted.txt", newlines);

		// review-ratings
		newlines.clear();
		br = new BufferedReader(new FileReader(new File(reviewFile)));
		while ((line = br.readLine()) != null) {
			String[] data = line.split(",");
			String user = data[0];
			String review = data[1];
			String rating = data[2];

			if (!userIdMap.containsKey(user))
				userIdMap.put(user, userIdMap.size() + 1);

			String newline = userIdMap.get(user) + sep
					+ reviewIdMap.get(review) + sep + rating;
			newlines.add(newline);
		}
		br.close();
		FileIO.writeList(dirPath + "review-ratings-converted.txt", newlines);

		// trust
		newlines.clear();
		br = new BufferedReader(new FileReader(new File(trustFile)));
		while ((line = br.readLine()) != null) {
			String[] data = line.split(",");
			String trustor = data[0];
			String trustee = data[1];
			String rating = data[2];
			
			if (!userIdMap.containsKey(trustor))
				userIdMap.put(trustor, userIdMap.size() + 1);
			if (!userIdMap.containsKey(trustee))
				userIdMap.put(trustee, userIdMap.size() + 1);

			String newline = userIdMap.get(trustor) + sep
					+ userIdMap.get(trustee) + sep + rating;
			newlines.add(newline);
		}
		br.close();
		FileIO.writeList(dirPath + "trusts-converted.txt", newlines);
	}

	public static void getAllRatings() throws Exception {
		String filePath = FileIO.getResource("dvd.ciao.txt");
		List<String> urls = FileIO.readAsList(filePath);

		String dir = Systems.getDesktop() + "dvd.ciao.co.uk\\";
		String ratingFile = dir + "ratings.txt";
		String reviewFile = dir + "review-ratings.txt";
		FileIO.deleteFile(ratingFile);
		FileIO.deleteFile(reviewFile);

		for (String url : urls) {
			// each category
			String[] data = url.split(": ");
			String category = data[0];
			String dirPath = FileIO.makeDirPath(dir, category);

			// ratings
			String ratingPath = dirPath + "ratings.txt";
			List<String> ratings = FileIO.readAsList(ratingPath);

			FileIO.writeList(ratingFile, ratings, null, true);

			// reviews
			String reviewPath = dirPath + "review-ratings.txt";
			List<String> reviews = FileIO.readAsList(reviewPath);

			FileIO.writeList(reviewFile, reviews, null, true);
		}
	}

	public static void parseReviewPages() throws Exception {
		String filePath = FileIO.getResource("dvd.ciao.txt");
		List<String> urls = FileIO.readAsList(filePath);

		String dir = Systems.getDesktop() + "dvd.ciao.co.uk\\";
		for (String url : urls) {
			// each category
			String[] data = url.split(": ");
			String category = data[0];
			String dirCate = FileIO.makeDirPath(dir, category);

			File dirs = new File(dirCate);
			for (File f : dirs.listFiles()) {
				// each product folder
				if (f.getName().equals("webPages"))
					continue;
				if (!f.isDirectory())
					continue;

				String prodPath = FileIO.makeDirPath(dirCate, f.getName());
				String revwPath = FileIO.makeDirPath(prodPath, "Reviews");

				List<String> reviews = new ArrayList<>();
				File reviewDirs = new File(revwPath);

				for (File rf : reviewDirs.listFiles()) {
					// each review page
					String html = FileIO.readAsString(rf.getPath());
					Document doc = Jsoup.parse(html);

					Elements es = doc.select("div.m-shortReviewSnippet");
					for (Element e : es) {
						Element a = e.select(
								"p.m-shet-review-title a.ReviewTitle").first();
						if (a == null)
							continue; // some reviews do not have specific link
										// to detailed contents

						// url
						String link = a.attr("href");

						int idx = link.lastIndexOf("_");
						String id = link.substring(idx + 1);

						reviews.add(id + "::" + link);
					}
				}

				FileIO.writeList(prodPath + "reviews.txt", reviews, null, false);
			}

		}

	}
}
