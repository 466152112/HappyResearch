// Copyright (C) 2014 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.undefined;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 * Data dtructure for user ratings with richer side information
 * 
 * @author guoguibing
 * 
 */
public class Rating {

	private int user;
	private int item;
	private double rate;
	private Date rateDate;

	/**
	 * other associated user/item attributes
	 * 
	 * <p>
	 * Instead of generating separate user/item classes to store their
	 * attributes, we choose to use a map structure to incorporate all the
	 * attribute directly together the ratings, for compacity's sake.
	 * </p>
	 * 
	 */
	private Map<String, Object> attributes;

	public Rating() {
		user = -1;
		item = -1;
		rate = -1;
		rateDate = null;
	}

	public Rating(int u, int j, int ruj) {
		user = u;
		item = j;
		rate = ruj;
		rateDate = null;
	}

	public Rating(int u, int j, int ruj, Date date) {
		this(u, j, ruj);
		rateDate = date;
	}

	public int getUser() {
		return user;
	}

	public void setUser(int user) {
		this.user = user;
	}

	public int getItem() {
		return item;
	}

	public void setItem(int item) {
		this.item = item;
	}

	public double getRate() {
		return rate;
	}

	public void setRate(double rate) {
		this.rate = rate;
	}

	public Date getRateDate() {
		return rateDate;
	}

	public void setRateDate(Date rateDate) {
		this.rateDate = rateDate;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public Object getAttribute(String key) {
		return attributes == null ? null : attributes.get(key);
	}

	public void addAttribute(String key, Object value) {
		if (attributes == null)
			attributes = new HashMap<>();

		attributes.put(key, value);
	}

}
