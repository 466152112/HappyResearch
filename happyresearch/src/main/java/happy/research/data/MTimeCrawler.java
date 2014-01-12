package happy.research.data;

import happy.coding.io.FileIO;
import happy.coding.io.Strings;
import happy.coding.io.net.URLReader;
import happy.coding.system.Debug;
import happy.coding.system.Systems;

import java.io.File;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class MTimeCrawler
{
	String	dir	= Systems.getDesktop() + "mtime.com/";

	public MTimeCrawler()
	{
		FileIO.makeDirectory(dir);
	}

	public void crawl_web_pages() throws Exception
	{
		String filePath = "./src/main/resources/mtime.txt";
		List<String> urls = FileIO.readAsList(filePath);

		for (String url : urls)
		{
			String html = URLReader.read(url);
			Document doc = Jsoup.parse(html);
			String name = doc.select("span[property=v:itemreviewed]").text();
			name = Strings.filterWebString(name, '_');

			String dirPath = dir + name + "/";
			FileIO.makeDirectory(dirPath);
			FileIO.writeString(dirPath + name + ".html", html);
		}
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

			MTimeMovie mm = new MTimeMovie();

			String name = doc.select("span[property=v:itemreviewed]").text();
			mm.setName(name);

			String year = doc.select("a.c_666").first().text();
			mm.setYear(Integer.parseInt(year));
		}
	}

	public static void main(String[] args) throws Exception
	{
		MTimeCrawler m = new MTimeCrawler();
		if (Debug.OFF) m.crawl_web_pages();
		if (Debug.OFF) m.parse_overall_ratings();
	}
}
