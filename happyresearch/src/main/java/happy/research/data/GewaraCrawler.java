package happy.research.data;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.io.net.WebCrawler;
import happy.coding.system.Systems;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class GewaraCrawler extends WebCrawler
{
	String	dir		= Systems.getDesktop() + "gewara.com/";
	String	task	= null;

	public GewaraCrawler(String url, String task, int id) throws Exception
	{
		super(url, id);

		FileIO.makeDirectory(dir);
		this.task = task;
	}

	public void crawl_web_pages(String url) throws Exception
	{
		String html = read_url(url);
		Document doc = Jsoup.parse(html);
		String name = doc.select("div.detail_head_name h1").first().text();
		name = Strings.filterWebString(name, '_');

		String dirPath = dir + name + "/";
		FileIO.makeDirectory(dirPath);
		FileIO.writeString(dirPath + name + ".html", html);
	}

	public void parse_overall_ratings() throws Exception
	{
		File directory = new File(dir);

		File[] dirs = directory.listFiles();

		for (File d : dirs)
		{
			String fname = d.getName();
			String file = d.getPath() + "/" + fname + ".html";

			Document doc = Jsoup.parse(FileIO.readAsString(file));
			GewaraMovie gm = new GewaraMovie();

			gm.setName(doc.select("div.detail_head_name h1").first().text());
			gm.setSub_name(doc.select("div.detail_head_name strong").first().attr("title"));

			Element info = doc.select("div.detail_head_desc").first();
			Elements es = info.select("p");
			Element e = null;
			String val = null;
			List<String> vals = null;
			for (Element ex : es)
			{
				e = ex.select("em").first();
				String key = e.text();
				if (key.startsWith("看点")) gm.setMain_point(ex.select("span").first().text());
				else if (key.startsWith("首映")) gm.setReleseDates(e.siblingNodes().get(0).toString());
				else if (key.startsWith("片长")) gm.setLength(e.siblingNodes().get(0).toString());
				else if (key.startsWith("版本")) gm.setVersion(e.siblingNodes().get(0).toString());
				else if (key.startsWith("导演")) gm.setDirector(e.siblingNodes().get(0).toString());
				else if (key.startsWith("语言"))
				{
					vals = new ArrayList<>();
					val = e.siblingNodes().get(0).toString();
					for (String str : val.split("/"))
						vals.add(str);
					gm.setLanguage(vals);
				} else if (key.startsWith("地区"))
				{
					vals = new ArrayList<>();
					val = e.siblingNodes().get(0).toString();
					for (String str : val.split("/"))
						vals.add(str);
					gm.setCountries(vals);
				} else if (key.startsWith("类型"))
				{
					vals = new ArrayList<>();
					val = e.siblingNodes().get(0).toString();
					for (String str : val.split("/"))
						vals.add(str);
					gm.setTypes(vals);
				} else if (key.startsWith("主演"))
				{
					vals = new ArrayList<>();
					val = e.siblingNodes().get(0).toString();
					for (String str : val.split(" "))
						vals.add(str);
					gm.setActors(vals);
				}
			}

			es = doc.select("div#showDown_content p b");
			gm.setDescription(es.get(0).siblingNodes().get(0).toString());

			val = doc.select("#detail_nav li a").first().attr("href");
			gm.setGm_url("http://www.gewara.com" + val);
			gm.setId(val.substring(val.lastIndexOf("/") + 1));

			e = doc.select("div.detail_movieTypeBotm div span").first();
			gm.setAvg_rating(Double.parseDouble(e.text()));

			e = doc.select("a:contains(哇啦)").first();
			val = e.select("span").first().text().replace("(", "").replace(")", "");
			gm.setNum_ratings(Integer.parseInt(val));

			FileIO.writeString(d.getPath() + "/summary.txt", gm.toString());
		}
	}

	public void crawl_comments(String url) throws Exception
	{
		String html = read_url(url);
		Document doc = Jsoup.parse(html);
		String name = doc.select("div.detail_head_name h1").first().text();
		name = Strings.filterWebString(name, '_');

		String val = doc.select("#detail_nav li a").first().attr("href");
		String id = val.substring(val.lastIndexOf("/") + 1);

		String dirPath = dir + name + "/comments/";
		FileIO.makeDirectory(dirPath);

		// save rating pages
		int max = 1;
		boolean maxSet = false;
		url = url + "/commentlist";

		for (int k = 0; k <= max; k++)
		{
			String page_file = dirPath + "page_" + (k + 1) + ".html";
			Logs.debug(name + " comments with page: " + (k + 1) + "/" + (max + 1));

			String contents = null;
			if (!FileIO.exist(page_file))
			{

				String link = "http://www.gewara.com/ajax/common/qryComment.xhtml?pageNumber="
						+ k
						+ "&relatedid="
						+ id
						+ "&title=&issue=false&hasMarks=true&tag=movie&isPic=true&isVideo=false&pages=true&maxCount=20&userLogo=";

				contents = read_url(link);
				FileIO.writeString(page_file, contents);// new String(contents.getBytes("utf-8"), "utf-8"));
			} else
			{
				contents = FileIO.readAsString(page_file);
			}

			// find the maximum page num;
			if (!maxSet)
			{
				Document doc2 = Jsoup.parse(contents);
				Elements es = doc2.select("div#page a");
				Element e = es.get(es.size() - 2);
				max = Integer.parseInt(e.attr("lang"));
				maxSet = true;
			}

		}
	}

	public static void main(String[] args) throws Exception
	{
		String dir_path = "D:/Dropbox/PhD/My Work/Ongoing/Data Crawl/gewara.com/";
		File dir = new File(dir_path);
		String sep = ",\t";

		for (File movie : dir.listFiles())
		{
			if (!movie.isDirectory()) continue;
			String movie_path = movie.getPath();

			File comments = new File(movie_path + "/comments/");

			String file = movie_path + "/comments.csv";
			FileIO.deleteFile(file);

			int total = comments.listFiles().length;
			for (File page : comments.listFiles())
			{
				Logs.debug("Current page: " + page.getName() + "/" + total);

				Document doc = Jsoup.parse(FileIO.readAsString(page.getPath()));

				Elements es = doc.select("div.ui_wala_comment dl");

				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < es.size(); i++)
				{
					Element e = es.get(i);
					Element li = e.select("div.page_wala p").first();

					String user = li.select("a").first().text();
					String user_url = li.select("a").first().attr("href");

					Element eli = li.select("span.ui_grades").first();
					String rate = "";
					String comment = null;
					if (eli != null)
					{
						rate = eli.text();
						comment = eli.nextSibling().toString().substring(1);
					} else
					{
						comment = li.select("a").first().nextSibling().toString().substring(1);
					}

					li = e.select("div.page_replay.page_replay_my.clear").first();
					String time = e.select("span.left").first().text();
					String forward = li.select("a.page_ico.forwards").first().text();
					String reply = li.select("a.page_ico.comment").first().text();

					String line = user + sep + user_url + sep + rate + sep + time + sep + forward + sep + reply + sep
							+ comment;
					if (i < es.size() - 1) line += "\n";
					sb.append(line);
				}
				FileIO.writeString(file, sb.toString(), true);
			}
		}
	}

	public static void crawl_data() throws Exception
	{
		String filePath = FileIO.getResource("gewara.txt");

		List<String> urls = FileIO.readAsList(filePath);
		String[] tasks = { "comments" };

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
					tds[j] = new Thread(new GewaraCrawler(url, task, i + j + 1));
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

	@Override
	public void run_thread() throws Exception
	{
		switch (task)
		{
			case "web_pages":
				crawl_web_pages(url);
				break;
			case "ratings":
				break;
			case "comments":
				crawl_comments(url);
				break;
			case "reviews":
				break;
			default:
				break;
		}
	}
}
