package happy.research.cf;

import happy.coding.system.Debug;

public class Prediction
{
	private String	userId;
	private String	itemId;
	private double	truth;
	private double	pred;

	/**
	 * the confidence of the prediction
	 */
	private double	conf;

	public Prediction(Rating rating, double pred)
	{
		this(rating, pred, true);
	}

	public Prediction(Rating rating, double pred, boolean scaled)
	{
		this.userId = rating.getUserId();
		this.itemId = rating.getItemId();
		this.truth = rating.getRating();

		if (scaled) this.pred = scaledPred(pred);
		else this.pred = pred;
	}

	@Override
	public String toString()
	{
		return this.userId + " " + this.itemId + " " + this.truth + " " + this.pred;
	}

	/**
	 * We should not deviate this prediction, otherwise the obtained performance is not accurate; 
	 * but some works do suggest to do so.
	 * 
	 * This is especially important for resnick's formula
	 */
	public double scaledPred(double pred)
	{
		if (pred < Dataset.minScale) pred = Dataset.minScale;
		if (pred > Dataset.maxScale) pred = Dataset.maxScale;

		return pred;
	}

	public double error()
	{
		return Math.abs(pred - truth);
	}

	public String getUserId()
	{
		return userId;
	}

	public void setUserId(String userId)
	{
		this.userId = userId;
	}

	public String getItemId()
	{
		return itemId;
	}

	public void setItemId(String itemId)
	{
		this.itemId = itemId;
	}

	public double getTruth()
	{
		return truth;
	}

	public void setTruth(double truth)
	{
		this.truth = truth;
	}

	public double getPred()
	{
		return pred;
	}

	public void setPred(double pred)
	{
		if (Debug.OFF) pred = scaledPred(pred);

		this.pred = pred;
	}

	public double getConf()
	{
		return conf;
	}

	public void setConf(double conf)
	{
		this.conf = conf;
	}
}
