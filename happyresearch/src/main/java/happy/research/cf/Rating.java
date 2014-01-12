package happy.research.cf;

/**
 * Rating in the format of "userId itemId rating"
 * 
 * @author guoguibing
 */
public class Rating {
	private String userId;
	private String itemId;
	private double rating;
	private long timestamp;

	/**
	 * the weight of this rating, could refer to confidence of ratings
	 */
	private double weight;

	public Rating() {
	}

	public Rating(String userId, String itemId, double rating) {
		this.userId = userId;
		this.itemId = itemId;
		this.rating = rating;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
	}

	public double getRating() {
		return rating;
	}

	public void setRating(double rating) {
		this.rating = rating;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public String toString() {
		String str = userId + " " + itemId + " " + rating;

		if (weight > 0)
			str += " " + weight;
		if (timestamp > 0)
			str += " " + timestamp;

		return str;
	}

	@Override
	public boolean equals(Object obj) {
		Rating r = (Rating) obj;

		return r.itemId.equals(itemId) && r.userId.equals(userId) && r.rating == rating;
	}

	public double getWeight() {
		return weight;
	}

	public void setWeight(double weight) {
		this.weight = weight;
	}
}
