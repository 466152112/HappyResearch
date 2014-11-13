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

/**
 * @author guibing
 * 
 */
public class User {

	private int id; // inner user id, used as foreign key to the Rating.user;
	private String rawId; // raw user id, used to store original user id

	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * @param id
	 *            the id to set
	 */
	public void setId(int id) {
		this.id = id;
	}

	/**
	 * @return the rawId
	 */
	public String getRawId() {
		return rawId;
	}

	/**
	 * @param rawId
	 *            the rawId to set
	 */
	public void setRawId(String rawId) {
		this.rawId = rawId;
	}

}
