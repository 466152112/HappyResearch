package happy.research.cf;

import java.sql.Date;

/**
 * Virtual Rating 
 * 
 * @author guoguibing
 *
 */
public class VirRating
{
	private String	userId;
	private String	teeId;
	private double	rating;
	private String	environment;
	private double	presence;
	private double	confidence;
	private Date	rDate;

	@Override
	public String toString()
	{
		return userId + "\t" + teeId + "\t" + rating + "\t" + confidence + "\t" + presence + "\t" + rDate + "\t"
				+ environment;
	}

	public String getUserId()
	{
		return userId;
	}

	public void setUserId(String userId)
	{
		this.userId = userId;
	}

	public String getTeeId()
	{
		return teeId;
	}

	public void setTeeId(String teeId)
	{
		this.teeId = teeId;
	}

	public double getRating()
	{
		return rating;
	}

	public void setRating(double rating)
	{
		this.rating = rating;
	}

	public String getEnvironment()
	{
		return environment;
	}

	public void setEnvironment(String environment)
	{
		this.environment = environment;
	}

	public double getPresence()
	{
		return presence;
	}

	public void setPresence(double presence)
	{
		this.presence = presence;
	}

	public double getConfidence()
	{
		return confidence;
	}

	public void setConfidence(double confidence)
	{
		this.confidence = confidence;
	}

	public Date getrDate()
	{
		return rDate;
	}

	public void setrDate(Date rDate)
	{
		this.rDate = rDate;
	}
}
