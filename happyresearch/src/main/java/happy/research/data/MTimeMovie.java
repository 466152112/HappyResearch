package happy.research.data;

import java.util.List;

public class MTimeMovie
{
	private String			name;
	private String			id;
	private int				year;
	private String			director;
	private List<String>	scenarist;
	private List<String>	actors;
	private List<String>	types;
	private String			douban_url;
	private String			official_url;
	private String			imdb_url;
	private List<String>	countries;
	private List<String>	language;
	private String			releseDates;
	private String			length;
	private List<String>	alias;

	private double			avg_rating;
	private int				num_ratings;
	private List<String>	ratio_rates;

	private String			description;

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public String getId()
	{
		return id;
	}

	public void setId(String id)
	{
		this.id = id;
	}

	public int getYear()
	{
		return year;
	}

	public void setYear(int year)
	{
		this.year = year;
	}

	public String getDirector()
	{
		return director;
	}

	public void setDirector(String director)
	{
		this.director = director;
	}

	public List<String> getScenarist()
	{
		return scenarist;
	}

	public void setScenarist(List<String> scenarist)
	{
		this.scenarist = scenarist;
	}

	public List<String> getActors()
	{
		return actors;
	}

	public void setActors(List<String> actors)
	{
		this.actors = actors;
	}

	public List<String> getTypes()
	{
		return types;
	}

	public void setTypes(List<String> types)
	{
		this.types = types;
	}

	public String getDouban_url()
	{
		return douban_url;
	}

	public void setDouban_url(String douban_url)
	{
		this.douban_url = douban_url;
	}

	public String getOfficial_url()
	{
		return official_url;
	}

	public void setOfficial_url(String official_url)
	{
		this.official_url = official_url;
	}

	public String getImdb_url()
	{
		return imdb_url;
	}

	public void setImdb_url(String imdb_url)
	{
		this.imdb_url = imdb_url;
	}

	public List<String> getCountries()
	{
		return countries;
	}

	public void setCountries(List<String> countries)
	{
		this.countries = countries;
	}

	public List<String> getLanguage()
	{
		return language;
	}

	public void setLanguage(List<String> language)
	{
		this.language = language;
	}

	public String getReleseDates()
	{
		return releseDates;
	}

	public void setReleseDates(String releseDates)
	{
		this.releseDates = releseDates;
	}

	public String getLength()
	{
		return length;
	}

	public void setLength(String length)
	{
		this.length = length;
	}

	public List<String> getAlias()
	{
		return alias;
	}

	public void setAlias(List<String> alias)
	{
		this.alias = alias;
	}

	public double getAvg_rating()
	{
		return avg_rating;
	}

	public void setAvg_rating(double avg_rating)
	{
		this.avg_rating = avg_rating;
	}

	public int getNum_ratings()
	{
		return num_ratings;
	}

	public void setNum_ratings(int num_ratings)
	{
		this.num_ratings = num_ratings;
	}

	public List<String> getRatio_rates()
	{
		return ratio_rates;
	}

	public void setRatio_rates(List<String> ratio_rates)
	{
		this.ratio_rates = ratio_rates;
	}

	public String getDescription()
	{
		return description;
	}

	public void setDescription(String description)
	{
		this.description = description;
	}
}
