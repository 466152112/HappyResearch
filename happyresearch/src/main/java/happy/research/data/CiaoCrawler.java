package happy.research.data;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.io.net.WebCrawler;
import happy.coding.system.Debug;
import happy.coding.system.Systems;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class CiaoCrawler extends WebCrawler {
	protected static String folder;

	protected static String sep = ",\t";
	protected String dir;
	private String task;
	private String domain = "dvd.ciao.co.uk";
	private String desktop = Systems.getDesktop();

	CiaoCrawler(String url, String task, int id) throws Exception {
		super(url, id);

		dir = folder + domain;
		dir = FileIO.makeDirectory(dir);
		this.task = task;
	}

	public CiaoCrawler() throws Exception {
		this(null, null, 0);
	}

	public void run_reviews(String url) throws Exception {
		String[] data = url.split(": ");
		String category = data[0];

		String catPath = FileIO.makeDirPath(desktop, domain, category);
		File Dir = new File(catPath);
		File[] prodDirs = Dir.listFiles();
		int tk = prodDirs.length;
		for (int k = 0; k < tk; k++) {
			File prodDir = prodDirs[k];
			// for each sub directory
			if (prodDir.getName().equals("webPages"))
				continue;
			if (!prodDir.isDirectory())
				continue;

			String prodPath = FileIO.makeDirPath(catPath, prodDir.getName());
			List<String> reviews = FileIO.readAsList(prodPath + "reviews.txt");

			String reviewPath = FileIO.makeDirectory(prodPath,
					"Detailed_Reviews");

			int tv = reviews.size();
			for (int v = 0; v < tv; v++) {
				String rv = reviews.get(v);
				String[] rd = rv.split("::");
				String id = rd[0];
				String link = rd[1];

				String rvPath = FileIO.makeDirectory(reviewPath, id);

				String rvFilePath = rvPath + id + ".html";
				String rvRatingPath = rvPath + id + "-ratings.html";
				String html = null;
				if (FileIO.exist(rvFilePath)) {
					if (FileIO.exist(rvRatingPath)) {
						Logs.debug(category + ": " + prodDir.getName() + " ("
								+ (k + 1) + "/" + tk + "): review " + (v + 1)
								+ "/" + tv);
						continue;
					}

					html = FileIO.readAsString(rvFilePath);
				} else {
					html = read_url(link);
					if (html == null)
						continue;

					FileIO.writeString(rvFilePath, html);
				}

				Document doc = Jsoup.parse(html);
				Element e = doc.select(
						"div#CompTooltip2 div.CWMSReviewRatings p a.CWLINKSub")
						.first();
				if (e == null)
					continue; // this review has not been rated by others;

				String js = e.attr("onmouseover");
				String predix = "javascript:this.href=jlinkBuild(";
				int start = predix.length();
				int end = js.lastIndexOf(")");
				String parse = js.substring(start, end);
				parse = parse.replace("'", "");
				parse = parse.replace(",", "");

				String href = "http://dvd.ciao.co.uk" + parse;
				html = read_url(href);
				FileIO.writeString(rvRatingPath, html);

				Logs.debug(category + ": " + prodDir.getName() + " (" + (k + 1)
						+ "/" + tk + "): review " + (v + 1) + "/" + tv);
			}

		}
	}

	public void run_ratings(String url) throws Exception {
		String[] data = url.split(": ");
		String category = data[0];

		String catPath = FileIO.makeDirPath(desktop, domain, category);
		File Dir = new File(catPath);
		File[] prodDirs = Dir.listFiles();
		int tk = prodDirs.length;
		for (int k = 0; k < tk; k++) {
			File prodDir = prodDirs[k];
			// for each product
			if (prodDir.getName().equals("webPages"))
				continue;
			if (!prodDir.isDirectory())
				continue;

			String reviewPath = FileIO.makeDirPath(catPath, prodDir.getName(),
					"Detailed_Reviews");
			File reviewDir = new File(reviewPath);
			File[] reviewDirs = reviewDir.listFiles();
			for (int i = 0; i < reviewDirs.length; i++) {

				File reviewFile = reviewDirs[i];
				String name = reviewFile.getName();
				Logs.debug("{}: {} ({}/{}): {} ({}/{})", category,
						prodDir.getName(), (k + 1), tk, name, (i + 1),
						reviewDirs.length);

				String dirPath = FileIO.makeDirPath(reviewPath, name);
				String ratingPath = dirPath + name + "-ratings.html";
				if (!FileIO.exist(ratingPath))
					continue; // no ratings

				String html = FileIO.readAsString(ratingPath);
				Document doc = Jsoup.parse(html);
				Elements as = doc.select("a.CWLINKSub.CWMSFontSizeSmaller");
				if (as == null || as.size() == 0)
					continue; // no further review rater pages

				for (Element a : as) {
					if (a.hasAttr("id")) // back to ratings/review
						continue;

					String link = "http://dvd.ciao.co.uk" + a.attr("href");

					html = read_url(link);
					int rating = Integer.parseInt(link.substring(link
							.lastIndexOf('=') + 1));
					int page = 1;
					String valuePath = dirPath + "ratings-" + rating + "-";
					FileIO.writeString(valuePath + page + ".html", html);

					// multiple pages
					Document doc2 = Jsoup.parse(html);
					Elements ns = doc2.select("div#Pagination");
					if (ns != null && ns.size() > 0) {
						Element ps = ns.select("li").last();
						int numPages = Integer.parseInt(ps.text());
						if (numPages <= 1)
							continue;

						// for debug purpose
						Logs.info(valuePath + numPages);

						// generate url
						ps = ns.select("li a").first();
						String pLink = ps.attr("href");
						pLink = pLink.substring(0, pLink.lastIndexOf('/') + 1);

						for (int p = 2; p <= numPages; p++) {
							String ppLink = pLink + (p - 1) * 15;

							html = read_url(ppLink);
							FileIO.writeString(valuePath + p + ".html", html);
						}

					}
				}

			}

		}
	}

	public void run_dvd_ratings(String url) throws Exception {
		String[] data = url.split(": ");
		String category = data[0];
		String category_url = data[1];
		category_url = category_url.substring(0, category_url.lastIndexOf('_'));
		String categoryID = category_url.substring(category_url
				.lastIndexOf('_') + 1);

		String catPath = FileIO.makeDirPath(desktop, domain, category);
		File Dir = new File(catPath);
		File[] prodDirs = Dir.listFiles();
		int tk = prodDirs.length;
		for (int k = 0; k < tk; k++) {
			File prodDir = prodDirs[k];
			// for each product
			String productID = prodDir.getName();
			if (productID.equals("webPages"))
				continue;
			if (!prodDir.isDirectory())
				continue;

			String prodPath = FileIO.makeDirPath(catPath, productID);
			String reviewPath = FileIO
					.makeDirPath(prodPath, "Detailed_Reviews");
			String dvdPath = prodPath + "dvd-ratings.txt";
			FileIO.deleteFile(dvdPath);

			File reviewDir = new File(reviewPath);
			File[] reviewDirs = reviewDir.listFiles();
			List<String> reviews = new ArrayList<>();
			for (int i = 0; i < reviewDirs.length; i++) {
				// for each review
				File reviewFile = reviewDirs[i];
				String reviewID = reviewFile.getName();
				Logs.debug("{}: {} ({}/{}): {} ({}/{})", category, productID,
						(k + 1), tk, reviewID, (i + 1), reviewDirs.length);

				String dirPath = FileIO.makeDirPath(reviewPath, reviewID);
				String ratingPath = dirPath + reviewID + ".html";
				if (!FileIO.exist(ratingPath))
					continue; // review page is not existing
				String html = FileIO.readAsString(ratingPath);
				Document doc = Jsoup.parse(html);
				Element div = doc.select("div#OH_BingUserInfo").first();
				if (div == null)
					continue; // no user review exists

				// user-info
				Element a = div.select("p.m-reer-usertab.clearfix a.black")
						.first();
				String raw = a.attr("onmousedown");
				raw = raw.substring(raw.indexOf("(") + 1, raw.lastIndexOf(")"));
				String userUrl = raw.replace(",", "").replace("'", "");
				String userID = userUrl.substring(userUrl.lastIndexOf('_') + 1);

				// user-rating value
				Element r = div.select(
						"p.m-reer-usertab.clearfix img.ratingStars").first();
				String rating = r.attr("alt");

				// user-rating date
				div = doc.select("div#OH_BingUserOpinion").first();
				Element date = div
						.select("div.m-reer-opheader.reviewTitle span.m-reer-ddwrap span[property]")
						.first();
				String datetime = date.attr("content");

				// content
				String review = userID + "," + productID + "," + categoryID
						+ "," + reviewID + "," + rating + "," + datetime + ","
						+ userUrl;
				reviews.add(review);
			}
			FileIO.writeList(dvdPath, reviews);
		}
	}

	public void run_review_ratings(String url) throws Exception {
		String[] data = url.split(": ");
		String category = data[0];
		String catPath = FileIO.makeDirPath(desktop, domain, category);
		File Dir = new File(catPath);
		File[] prodDirs = Dir.listFiles();
		int tk = prodDirs.length;
		for (int k = 0; k < tk; k++) {
			File prodDir = prodDirs[k];
			// for each product
			String productID = prodDir.getName();
			if (productID.equals("webPages"))
				continue;
			if (!prodDir.isDirectory())
				continue;

			String prodPath = FileIO.makeDirPath(catPath, productID);
			String reviewPath = FileIO
					.makeDirPath(prodPath, "Detailed_Reviews");
			String dvdPath = prodPath + "review-ratings.txt";
			FileIO.deleteFile(dvdPath);

			File reviewDir = new File(reviewPath);
			File[] reviewDirs = reviewDir.listFiles();
			for (int i = 0; i < reviewDirs.length; i++) {
				// for each review
				File reviewFile = reviewDirs[i];
				String reviewID = reviewFile.getName();
				Logs.debug("{}: {} ({}/{}): {} ({}/{})", category, productID,
						(k + 1), tk, reviewID, (i + 1), reviewDirs.length);

				String reviewDirPath = FileIO.makeDirPath(reviewPath, reviewID);
				File ratingDir = new File(reviewDirPath);
				File[] ratingDirs = ratingDir.listFiles();
				int fileNum = ratingDirs.length;
				if (fileNum == 0)
					continue; // no any rating files

				List<String> reviews = new ArrayList<>();
				for (int t = 0; t < fileNum; t++) {
					File ratingFile = ratingDirs[t];
					String name = ratingFile.getName();
					if (name.equals(reviewID + ".html"))
						continue; // review page

					// ratings to the review
					String html = FileIO.readAsString(ratingFile.getPath());
					Document doc = Jsoup.parse(html);
					Elements divs = doc.select("div.CWMSRatingBlock");
					for (Element div : divs) {
						String text = div.text();
						int rating = 0;
						if (text.startsWith("not helpful"))
							rating = 1;
						else if (text.startsWith("somewhat helpful"))
							rating = 2;
						else if (text.startsWith("helpful"))
							rating = 3;
						else if (text.startsWith("very helpful"))
							rating = 4;
						else if (text.startsWith("exceptional"))
							rating = 5;
						else if (text.startsWith("off topic"))
							rating = 0;

						// for each user - rating
						Elements lis = div.select("li.clearfix");
						for (Element li : lis) {
							Element a = li.select("a.CWLINKSub").first();
							String link = a.attr("href");
							String userID = link.substring(link
									.lastIndexOf('_') + 1);

							String review = userID + "," + reviewID + ","
									+ rating + "," + link;
							reviews.add(review);
						}
					}
				}
				FileIO.writeList(dvdPath, reviews, null, true);
			}

		}
	}

	public void run_category_reviews(String url) throws Exception {
		String[] data = url.split(": ");
		String category = data[0];
		String catPath = FileIO.makeDirPath(desktop, domain, category);
		File Dir = new File(catPath);
		File[] prodDirs = Dir.listFiles();
		int tk = prodDirs.length;
		String movie_reviews = catPath + "movie-review-ratings.txt";
		FileIO.deleteFile(movie_reviews);

		for (int k = 0; k < tk; k++) {
			File prodDir = prodDirs[k];
			// for each product
			String productID = prodDir.getName();
			if (productID.equals("webPages"))
				continue;
			if (!prodDir.isDirectory())
				continue;

			String prodPath = FileIO.makeDirPath(catPath, productID);
			String dvdPath = prodPath + "review-ratings.txt";
			if (!FileIO.exist(dvdPath))
				continue; // no review ratings

			// read review ratings from each product, and remove the duplicated
			// review ratings
			Set<String> review_ratings = FileIO.readAsSet(dvdPath);
			FileIO.writeList(movie_reviews, review_ratings, null, true);
		}
	}

	/**
	 * Concate all the dvd ratings about the products in a specific category
	 * 
	 * @param url
	 * @throws Exception
	 */
	public void run_category_ratings(String url) throws Exception {
		String[] data = url.split(": ");
		String category = data[0];

		String catPath = FileIO.makeDirPath(desktop, domain, category);
		File Dir = new File(catPath);
		File[] prodDirs = Dir.listFiles();
		int tk = prodDirs.length;

		String ratingFile = catPath + "movie-ratings.txt";
		FileIO.deleteFile(ratingFile);

		for (int k = 0; k < tk; k++) {
			File prodDir = prodDirs[k];
			// for each product
			String productID = prodDir.getName();
			if (productID.equals("webPages"))
				continue;
			if (!prodDir.isDirectory())
				continue;

			Logs.debug("{}: {} ({}/{})", new Object[] { category, productID,
					(k + 1), tk });

			String prodPath = FileIO.makeDirPath(catPath, productID);
			String dvdPath = prodPath + "dvd-ratings.txt";
			if (!FileIO.exist(dvdPath))
				continue;

			List<String> dvd_ratings = FileIO.readAsList(dvdPath);

			FileIO.writeList(ratingFile, dvd_ratings, null, true);
		}
	}

	public void run_category_clean(String url) throws Exception {
		String[] data = url.split(": ");
		String category = data[0];

		String catPath = FileIO.makeDirPath(desktop, domain, category);

		String ratingFile = catPath + "movie-ratings.txt";
		String reviewFile = catPath + "movie-review-ratings.txt";

		String newRatingFile = catPath + "ratings.txt";
		String newReviewFile = catPath + "review-ratings.txt";
		String userFile = catPath + "users.txt";

		Map<String, String> users = new HashMap<>();
		String user = null, link = null, newLine = null;
		// ratings
		List<String> ratings = FileIO.readAsList(ratingFile);
		List<String> newRatings = new ArrayList<>(ratings.size());
		for (String line : ratings) {
			String[] d = line.split(",");

			user = d[0];
			link = d[6];

			users.put(user, link);

			newLine = line.substring(0, line.lastIndexOf(','));
			newRatings.add(newLine);
		}
		FileIO.writeList(newRatingFile, newRatings);
		ratings = null;
		newRatings = null;

		// reviews
		List<String> reviews = FileIO.readAsList(reviewFile);
		List<String> newReviews = new ArrayList<>(reviews.size());
		for (String line : reviews) {
			String[] d = line.split(",");

			user = d[0];
			link = d[3];
			users.put(user, link);
			newLine = line.substring(0, line.lastIndexOf(','));
			newReviews.add(newLine);
		}
		FileIO.writeList(newReviewFile, newReviews);
		reviews = null;
		newReviews = null;

		// users
		FileIO.writeMap(userFile, users);
	}

	public void run_user(String url) throws Exception {
		String[] data = url.split(",");
		String userID = data[0];
		String userUrl = data[1];

		String userPath = FileIO.makeDirPath(desktop, domain,
				"users.ciao.co.uk");
		String html = read_url(userUrl);

		// check if user exists now
		Document doc = Jsoup.parse(html);
		Elements tabs = doc.select("table.tabs");
		if (tabs == null || tabs.size() == 0)
			return; // no such user profile

		userPath = FileIO.makeDirectory(userPath, userID);
		FileIO.writeString(userPath + userID + ".html", html);

		// trusted neighbors
		String link = "http://www.ciao.co.uk/member_view.php/MemberId/"
				+ userID + "/TabId/5/subTabId/1";
		html = read_url(link);

		// find the max pages
		doc = Jsoup.parse(html);
		Element page = doc
				.select("table#comparePricesShowAllTop td.rangepages").first();
		if (page == null)
			return; // no friends at all

		FileIO.writeString(userPath + "friends-1.html", html);

		if (page.text().length() <= 1)
			return; // no more pages

		Element a = page.select("a").last();
		int maxPage = Integer.parseInt(a.text());

		for (int i = 2; i <= maxPage; i++) {
			String nextPage = link + "/Start/" + (i - 1) * 15;
			html = read_url(nextPage);
			FileIO.writeString(userPath + "friends-" + i + ".html", html);
		}
	}

	

	public static void crawl_data() throws Exception {
		if (Debug.OFF) {
			// home page
			CiaoCrawler cc = new CiaoCrawler();
			cc.run_home_page();
			return;
		}

		String sourceFile = "users.txt"; // "dvd.ciao.txt"
		String filePath = FileIO.getResource(sourceFile);
		List<String> urls = FileIO.readAsList(filePath);

		String[] tasks = { "users" };

		int nd = 8;
		for (String task : tasks) {
			Logs.info("Current task: " + task);
			for (int i = 0; i < urls.size(); i += nd) {
				Thread[] tds = new Thread[nd];

				boolean flag = false;
				for (int j = 0; j < nd; j++) {
					if (i + j >= urls.size()) {
						flag = true;
						break;
					}

					String url = urls.get(i + j).trim();
					tds[j] = new Thread(new CiaoCrawler(url, task, i + j + 1));
					tds[j].start();
				}

				for (Thread td : tds) {
					if (td != null)
						td.join();
				}

				if (flag)
					break;
			}
		}
	}

	public static void main(String[] args) throws Exception {
		CiaoCrawler.setFolder(Systems.getDesktop());
		CiaoCrawler.sleep = 5000;
		crawl_data();
	}

	@Override
	public void run_thread() throws Exception {
		switch (task) {
		case "web_pages":
			run_web_pages(url);
			break;
		case "products":
			run_products(url);
			break;
		case "reviews":
			run_reviews(url);
			break;
		case "ratings":
			run_ratings(url);
			break;
		case "dvd-ratings":
			run_dvd_ratings(url);
			break;
		case "category-ratings":
			run_category_ratings(url);
			break;
		case "review-ratings":
			run_review_ratings(url);
			break;
		case "category-reviews":
			run_category_reviews(url);
			break;
		case "category-clean":
			run_category_clean(url);
			break;
		case "users":
			run_user(url);
			break;
		default:
			break;
		}
	}

	private void run_home_page() throws Exception {
		String url = "http://dvd.ciao.co.uk/";
		String html = read_url(url);
		FileIO.writeString(dir + "dvd.ciao.html", html);

		Document doc = Jsoup.parse(html);
		Element categories = doc.getElementById("category_tree_table");
		Elements cs = categories.select("dl");

		List<String> cls = new ArrayList<>();
		for (Element c : cs) {
			Element cat = c.select("dt").first().select("a").first();
			String category = cat.text();
			String link = cat.attr("href");

			cls.add(category + ": " + link);
		}

		FileIO.writeList(dir + "dvd.ciao.txt", cls);
	}

	public void run_web_pages(String url) throws Exception {
		String[] data = url.split(": ");
		String category = data[0];
		String link = data[1];
		String dirPath = FileIO.makeDirectory(dir, category, "webPages");

		int pageSize = 15;
		String html = read_url(link);
		FileIO.writeString(dirPath + "page_" + 1 + ".html", html);

		Document doc = Jsoup.parse(html);
		int maxPage = Integer.parseInt(doc.select(
				"div.CWCiaoKievPagination.clearfix li.last").text());
		Logs.debug(category + ": progress [" + 1 + "/" + maxPage + "]");

		for (int i = 2; i <= maxPage; i++) {
			String pageLink = link + "~s" + (i - 1) * pageSize;
			String content = read_url(pageLink);
			FileIO.writeString(dirPath + "page_" + i + ".html", content);
			Logs.debug(category + ": progress [" + i + "/" + maxPage + "]");
		}
	}

	public void run_products(String url) throws Exception {
		String[] data = url.split(": ");
		String category = data[0];
		// String link = data[1];

		String dirPath = FileIO.makeDirPath(desktop, domain, category);
		List<String> links = FileIO.readAsList(dirPath + "movies.txt");
		int tk = links.size();
		for (int k = 0; k < tk; k++) {
			String link = links.get(k);
			String[] d = link.split("::");
			String id = d[0];
			String name = d[1];
			String productLink = d[2];
			int idx = productLink.lastIndexOf("/");
			String p1 = productLink.substring(0, idx) + "/Reviews";
			String reviewLink = p1 + productLink.substring(idx);

			// create folder
			String path = FileIO.makeDirectory(dirPath, id);

			// product page
			String html = null;

			String pagePath = path + id + ".html";
			if (!FileIO.exist(pagePath)) {
				html = read_url(productLink);
				FileIO.deleteFile(path + name + ".html");
				FileIO.writeString(pagePath, html);
			}

			// product reviews
			// get first page anyway to identify the maximum pages
			path = FileIO.makeDirectory(path, "Reviews");
			String reviewPath = path + "page_1.html";
			if (FileIO.exist(reviewPath)) {
				html = FileIO.readAsString(reviewPath);
			} else {
				html = read_url(reviewLink);
				FileIO.writeString(reviewPath, html);
			}
			Logs.debug(category + ": " + id + " (" + (k + 1) + "/" + tk + ")"
					+ ": page " + 1);

			Document doc = Jsoup.parse(html);
			Elements nav = doc.select("div#Pagination");

			if (!nav.isEmpty()) {
				int maxPage = 1;

				Elements last = nav.select("li.last");
				if (!last.isEmpty())
					maxPage = Integer.parseInt(last.first().text()); // more
																		// than
																		// 11
																		// pages
				else
					maxPage = Integer.parseInt(nav.select("li").last().text()); // less
																				// or
																				// equal
																				// 11
																				// pages

				for (int i = 2; i <= maxPage; i++) {
					String filePath = path + "page_" + i + ".html";
					if (FileIO.exist(filePath))
						continue;

					reviewLink = reviewLink + "/Start/" + ((i - 1) * 15);
					html = read_url(reviewLink);
					FileIO.writeString(filePath, html);

					Logs.debug(category + ": " + id + " (" + (k + 1) + "/" + tk
							+ ")" + ": page " + i + "/" + maxPage);
				}
			}
		}

	}

	public static void setFolder(String folder) {
		CiaoCrawler.folder = folder;
	}
}
