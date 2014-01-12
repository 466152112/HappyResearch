package happy.research.data;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.io.net.WebCrawler;
import happy.coding.system.Debug;
import happy.coding.system.Systems;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class DoubanCrawler extends WebCrawler
{
	protected static String	folder;
	protected static String	sep	= ",\t";
	protected String		dir;
	private String			task;

	DoubanCrawler(String url, String task, int id) throws Exception
	{
		super(url, id);

		dir = folder + "douban.com/";
		FileIO.makeDirectory(dir);
		this.task = task;
	}

	public DoubanCrawler() throws Exception
	{
		this(null, null, 0);
	}

	public void run_reviews(String url) throws Exception
	{
		url = url.trim();
		String html = read_url(url);
		Document doc = Jsoup.parse(html);
		String name = doc.select("span[property=v:itemreviewed]").text();
		name = Strings.filterWebString(name, '_');

		String dirPath = dir + name + "/reviews/";
		FileIO.makeDirectory(dirPath);

		// save rating pages
		int k = 0;
		url = url + "reviews";
		String link = url;
		while (true)
		{
			k++;
			String page = null;
			String path = dirPath + "page_" + k + ".html";
			if (!FileIO.exist(path))
			{
				page = read_url(link);
				FileIO.writeString(path, page);
				Logs.debug(name + " reviews with page: " + k);
			} else
			{
				page = FileIO.readAsString(path);
			}

			// find the next page link;
			Document doc2 = Jsoup.parse(page);
			Elements es = doc2.select("div#paginator a.next");
			if (es == null || es.size() == 0)
			{
				break;
			} else
			{
				link = url + es.first().attr("href");
			}
		}
	}

	public void parse_ratings() throws Exception
	{
		File directory = new File(dir);
		File[] movies = directory.listFiles();

		// different movies
		for (File movie : movies)
		{
			if (!movie.isDirectory()) continue;
			String movie_path = movie.getPath();
			File ratings = new File(movie_path + "/ratings/");

			// different pages
			String rating_file = movie_path + "/ratings.csv";
			FileIO.deleteFile(rating_file);

			for (File page : ratings.listFiles())
			{
				Document doc = Jsoup.parse(FileIO.readAsString(page.getPath()));
				Elements es = doc.select("div#collections_tab .sub_ins table");

				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < es.size(); i++)
				{
					Element e = es.get(i);
					String line = "";
					Element user = e.select("div.pl2 a").first();
					Element rate = e.select("p.pl").first();

					line += user.text() + sep; // user
					if (rate.select("span").size() == 0) continue; // if no rating, ignore it
					line += rate.select("span").first().attr("class").substring(7, 8) + sep; // rating
					line += rate.text().substring(0, 10) + sep; // time
					line += user.attr("href"); // user url

					if (i < es.size() - 1) line += "\n";

					sb.append(line);
				}

				FileIO.writeString(rating_file, sb.toString(), true);
			}
		}
	}

	public void parse_reviews() throws Exception
	{
		File directory = new File(dir);
		File[] movies = directory.listFiles();

		// different movies
		for (File movie : movies)
		{
			if (!movie.isDirectory()) continue;
			String movie_path = movie.getPath();
			File ratings = new File(movie_path + "/reviews/");

			// different pages
			String rating_file = movie_path + "/reviews.csv";
			FileIO.deleteFile(rating_file);

			for (File page : ratings.listFiles())
			{
				Document doc = Jsoup.parse(FileIO.readAsString(page.getPath()));
				Elements es = doc.select("div.ctsh");

				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < es.size(); i++)
				{
					Element e = es.get(i);

					Element li = e.select("li.nlst").first();
					String detail_url = li.select("a.j.a_unfolder").first().attr("href");
					String title = li.select("a[title]").first().attr("title");

					li = e.select("li.ilst").first();
					String user = li.select("a").first().attr("title");
					String user_url = li.select("a").first().attr("href");

					li = e.select("li.clst.report-link").first();
					String rate = li.select("span.pl.ll.obss").first().child(1).attr("class").substring(7, 8);
					String review = li.select("div.review-short").first().childNode(0).toString().substring(1);

					String str = li.select("span.fleft").first().text();
					String[] val = str.replace("&nbsp", " ").split(" ");
					String datetime = val[0] + " " + val[1];
					String helpful = val[4].substring(0, val[4].indexOf("有用"));

					String line = title + sep + rate + sep + user + sep + user_url + sep + datetime + sep + helpful
							+ sep + detail_url + sep + review;

					if (i < es.size() - 1) line += "\n";

					sb.append(line);
				}

				FileIO.writeString(rating_file, sb.toString(), true);
			}
		}
	}

	public void parse_comments() throws Exception
	{
		File directory = new File(dir);
		File[] movies = directory.listFiles();

		// different movies
		for (File movie : movies)
		{
			if (!movie.isDirectory()) continue;
			String movie_path = movie.getPath();
			File ratings = new File(movie_path + "/comments/");

			// different pages
			String rating_file = movie_path + "/comments.csv";
			FileIO.deleteFile(rating_file);

			for (File page : ratings.listFiles())
			{
				Logs.debug("current page: " + page.getName() + "/" + ratings.listFiles().length);

				Document doc = Jsoup.parse(FileIO.readAsString(page.getPath()));
				Elements es = doc.select("div.comment-item");

				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < es.size(); i++)
				{
					Element e = es.get(i).select("div.comment").first();

					Element li = e.select("span.comment-vote").first();
					String helpful = li.select("span.votes.pr5").first().text();

					li = e.select("span.comment-info").first();

					String user = li.select("a").first().text();
					String user_url = li.select("a").first().attr("href");

					Element eli = li.select("span[title]").first();
					String rate = "";
					String date = null;
					if (eli != null)
					{
						rate = eli.attr("class").substring(7, 8);

						date = li.childNode(4).toString();
					} else
					{
						date = li.childNode(2).toString();
					}
					String comment = e.select("p").first().text();

					String line = user + sep + user_url + sep + rate + sep + date + sep + helpful + sep + comment;

					if (i < es.size() - 1) line += "\n";

					sb.append(line);
				}

				FileIO.writeString(rating_file, sb.toString(), true);
			}
		}
	}

	public void parse_web_pages() throws Exception
	{
		File directory = new File(dir);
		File[] dirs = directory.listFiles();

		for (File d : dirs)
		{
			if (!d.isDirectory()) continue;

			String fname = d.getName();
			String file = d.getPath() + "/" + fname + ".html";

			Document doc = Jsoup.parse(FileIO.readAsString(file));

			DoubanMovie dm = new DoubanMovie();

			String name = doc.select("span[property=v:itemreviewed]").text();
			dm.setName(name);

			String year = doc.select("span.year").text();
			year = year.substring(1, year.lastIndexOf(')'));
			dm.setYear(Integer.parseInt(year));

			Element info = doc.select("div#info").get(0);

			Element e = info.select("span:contains(制片国家/地区)").first();
			String val = e.nextSibling().toString();
			List<String> vals = new ArrayList<>();
			for (String str : val.split("/"))
				vals.add(str.trim());
			dm.setCountries(vals);

			val = info.select("span:contains(语言)").first().nextSibling().toString();
			vals = new ArrayList<>();
			for (String str : val.split("/"))
				vals.add(str.trim());
			dm.setLanguage(vals);

			Elements es = info.select("span:contains(官方网站)");
			if (es != null && es.size() > 0)
			{
				e = es.first().nextElementSibling();
				dm.setOfficial_url(e.attr("href"));
			}

			e = info.select("span:contains(IMDb链接)").first().nextElementSibling();
			dm.setImdb_url(e.attr("href"));

			es = info.select("span[property=v:genre]");
			vals = new ArrayList<>();
			for (Element ex : es)
				vals.add(ex.text());
			dm.setTypes(vals);

			es = info.select("a[rel=v:starring]");
			vals = new ArrayList<>();
			for (Element ex : es)
				vals.add(ex.text());
			dm.setActors(vals);

			es = info.select("span:contains(又名)");
			if (es != null && es.size() > 0)
			{
				val = es.first().nextSibling().toString();
				vals = new ArrayList<>();
				for (String str : val.split("/"))
					vals.add(str.trim());
				dm.setAlias(vals);
			}

			dm.setReleseDates(info.select("span[property=v:initialReleaseDate").first().attr("content"));
			dm.setLength(info.select("span[property=v:runtime").first().text());
			dm.setDirector(info.select("a[rel=v:directedBy]").first().text());

			e = info.select("span:contains(编剧)").first();
			vals = new ArrayList<>();
			for (Element ex : e.select("a"))
				vals.add(ex.text());
			dm.setScenarist(vals);

			dm.setAvg_rating(Double.parseDouble(doc.select("strong[property=v:average]").text()));
			dm.setNum_ratings(Integer.parseInt(doc.select("span[property=v:votes]").first().text()));

			e = doc.select("div[rel=v:rating]").first();
			vals = new ArrayList<>();
			for (String str : e.ownText().split(" "))
				vals.add(str);
			dm.setRatio_rates(vals);

			val = doc.select("meta[http-equiv=mobile-agent]").first().attr("content");
			val = val.substring(val.indexOf("url=") + 4);
			dm.setDouban_url(val);

			val = val.replace("http://m.douban.com/movie/subject/", "").replace("/", "");
			dm.setId(val);

			dm.setDescription(doc.select("span[property=v:summary]").first().text());

			FileIO.writeString(d.getPath() + "/summary.txt", dm.toString());
		}
	}

	public static void parse_data() throws Exception
	{
		DoubanCrawler dc = new DoubanCrawler();
		String[] tasks = { "comments" };

		for (String task : tasks)
		{
			switch (task)
			{
				case "web_pages":
					dc.parse_web_pages();
					break;

				case "ratings":
					dc.parse_ratings();
					break;

				case "reviews":
					dc.parse_reviews();
					break;

				case "comments":
					dc.parse_comments();
					break;

				default:
					break;
			}
		}

	}

	public static void crawl_data() throws Exception
	{
		String filePath = FileIO.getResource("douban.txt");

		List<String> urls = FileIO.readAsList(filePath);
		String[] tasks = { "reviews" };

		int nd = 4;
		for (String task : tasks)
		{
			Logs.info("Current task: " + task);
			for (int i = 0; i < urls.size(); i += nd)
			{
				Thread[] tds = new Thread[nd];

				boolean flag = false;
				for (int j = 0; j < nd; j++)
				{
					if (i + j >= urls.size())
					{
						flag = true;
						break;
					}

					String url = urls.get(i + j).trim();
					tds[j] = new Thread(new DoubanCrawler(url, task, i + j + 1));
					tds[j].start();
				}

				for (Thread td : tds)
				{
					if (td != null) td.join();
				}

				if (flag) break;
			}
		}
	}

	public static void main(String[] args) throws Exception
	{
		if (Debug.OFF)
		{
			DoubanCrawler.setFolder(Systems.getDesktop());
			crawl_data();
		} else
		{
			DoubanCrawler.setFolder("D:/Dropbox/PhD/My Work/Ongoing/Data Crawl/");
			parse_data();
		}

	}

	@Override
	public void run_thread() throws Exception
	{
		switch (task)
		{
			case "web_pages":
				run_web_pages(url);
				break;
			case "ratings":
				run_ratings(url);
				break;
			case "comments":
				run_comments(url);
				break;
			case "reviews":
				run_reviews(url);
				break;
			default:
				break;
		}
	}

	public void run_comments(String url) throws Exception
	{
		url = url.trim();
		String html = read_url(url);
		Document doc = Jsoup.parse(html);
		String name = doc.select("span[property=v:itemreviewed]").text();
		name = Strings.filterWebString(name, '_');

		String dirPath = dir + name + "/comments/";
		FileIO.makeDirectory(dirPath);

		// save rating pages
		int k = 0;
		url = url + "comments";
		String link = url;
		while (true)
		{
			k++;
			String page_file = dirPath + "page_" + k + ".html";

			String contents = null;
			if (!FileIO.exist(page_file))
			{
				contents = read_url(link);
				FileIO.writeString(page_file, contents);
				Logs.debug(name + " comments with page: " + k);
			} else
			{
				contents = FileIO.readAsString(page_file);
			}

			// find the next page link;
			Document doc2 = Jsoup.parse(contents);
			Elements es = doc2.select("div#paginator a.next");
			if (es == null || es.size() == 0)
			{
				break;
			} else
			{
				link = url + es.first().attr("href");
			}
		}
	}

	public void run_web_pages(String url) throws Exception
	{
		String html = read_url(url);
		Document doc = Jsoup.parse(html);
		String name = doc.select("span[property=v:itemreviewed]").text();
		name = Strings.filterWebString(name, '_');

		String dirPath = dir + name + "/";
		FileIO.makeDirectory(dirPath);
		FileIO.writeString(dirPath + name + ".html", html);
	}

	public void run_ratings(String url) throws Exception
	{
		String html = read_url(url);
		Document doc = Jsoup.parse(html);
		String name = doc.select("span[property=v:itemreviewed]").text();
		name = Strings.filterWebString(name, '_');

		String dirPath = dir + name + "/ratings/";
		FileIO.makeDirectory(dirPath);

		// save rating pages
		int k = 0;
		while (true)
		{
			String link = url + "collections?start=" + (k * 20);
			String page = read_url(link);

			k++;
			FileIO.writeString(dirPath + "page_" + k + ".html", page);
			Logs.debug("Current processing page: " + k);

			// if finished;
			Document doc2 = Jsoup.parse(page);
			Elements es = doc2.select("div#collections_tab span.next");
			if (es == null || es.size() == 0)
			{
				break;
			}
		}

	}

	public static void setFolder(String folder)
	{
		DoubanCrawler.folder = folder;
	}
}
