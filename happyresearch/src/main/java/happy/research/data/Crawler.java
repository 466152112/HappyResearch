package happy.research.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.hp.hpl.jena.util.FileManager;

public class Crawler
{

	/**
	 * Crawl user profiles
	 * 
	 * @throws Exception
	 */
	public static void crawlUsers() throws Exception
	{
		String source = "filmTrustSources.txt";
		String prefix = "http://trust.mindswap.org/cgi-bin/FilmTrust/foaf.cgi?user=";
		String basePath = "C:/Users/guoguibing/Desktop/";

		/* Read Sources */
		BufferedReader br = new BufferedReader(new FileReader(new File(source)));
		String line = null;
		Map<String, String> userHrefs = new HashMap<>();
		while ((line = br.readLine()) != null)
		{
			if (line.startsWith("<li>"))
			{
				int index1 = line.indexOf("user=") + 5;
				int index2 = line.indexOf(">", index1);
				String user = line.substring(index1, index2).replaceAll(" ", "");
				if (user.contains("&"))
				{
					int index = user.indexOf("&");
					user = user.substring(0, index);
				}
				String href = prefix + user;
				if (!userHrefs.containsKey(user)) userHrefs.put(user, href);
			}
		}
		br.close();

		/* Read & Write User Models */
		String dirPath = basePath + "userProfiles/";
		File dir = new File(dirPath);
		if (!dir.exists()) dir.mkdirs();

		int count = 0;
		for (Entry<String, String> entry : userHrefs.entrySet())
		{
			System.out.println("Count = " + ++count + "/" + userHrefs.size());
			String user = entry.getKey();
			String href = entry.getValue();

			// user = user.replace('`', '-');
			user = user.replace('|', '-');
			String filePath = dirPath + user + ".rdf";
			File file = new File(filePath);
			if (file.exists() && file.length() > 0) continue;

			String content = FileManager.get().readWholeFileAsUTF8(href);
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			bw.write(content);
			bw.close();
		}
	}

	/**
	 * Crawl movies data, using rating data as a source of movie's ids
	 * 
	 * @throws Exception
	 */
	public static void crawlMovies() throws Exception
	{
		String ratings = "ratings.txt";
		String prefix = "http://trust.mindswap.org/cgi-bin/FilmTrust/filmRDF.cgi?movie=";
		String basePath = "C:/Users/guoguibing/Desktop/";

		List<String> movies = new ArrayList<>();
		BufferedReader br = new BufferedReader(new FileReader(new File(ratings)));
		String line = null;
		while ((line = br.readLine()) != null)
		{
			String[] data = line.split("::");
			String movie = data[1];
			if (!movies.contains(movie)) movies.add(movie);
		}
		br.close();

		/* Read and Write Movies data */
		String dirPath = basePath + "movies/";
		File dir = new File(dirPath);
		if (!dir.exists()) dir.mkdirs();

		int count = 0;
		for (String movie : movies)
		{
			System.out.println("Current in progress = " + ++count + "/" + movies.size());
			String filePath = dirPath + movie + ".rdf";
			File file = new File(filePath);
			if (file.exists() && file.length() > 0) continue;

			String movieHref = prefix + movie;
			String content = FileManager.get().readWholeFileAsUTF8(movieHref);
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			bw.write(content);
			bw.close();
		}

	}

	public static void main(String[] args) throws Exception
	{
		Crawler.crawlUsers();
		// Crawler.crawlMovies();
	}

}
