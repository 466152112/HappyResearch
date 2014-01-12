package happy.research.cf;

import java.sql.Date;

/**
 * Physical Rating
 * 
 * @author guoguibing
 *
 */
public class PhyRating
{
	private String	userName;
	private String	teeID;
	private double	rating;
	private Date	rDate;

	@Override
	public String toString()
	{
		return userName + "\t" + teeID + "\t" + rating + "\t" + rDate;
	}

	public String getUserName()
	{
		return userName;
	}

	public void setUserName(String userName)
	{
		this.userName = userName;
	}

	public String getTeeID()
	{
		return teeID;
	}

	public void setTeeID(String productID)
	{
		this.teeID = productID;
	}

	public double getRating()
	{
		return rating;
	}

	public void setRating(double rating)
	{
		this.rating = rating;
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
