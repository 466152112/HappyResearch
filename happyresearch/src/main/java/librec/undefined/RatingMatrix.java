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

import java.util.Map;

import librec.data.SparseMatrix;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

/**
 * Data structure for rating matrix with richer side information
 * 
 * Note: another way is to re-use user-item-rating matrix, but now each entry refers to rating inner id rather than
 * rating value.
 * 
 * @author guoguibing
 * 
 */
public class RatingMatrix {

	private int numRows, numColumns;
	private Table<Integer, Integer, Rating> dataTable;

	public RatingMatrix(int numRows, int numColumns) {
		this.numRows = numRows;
		this.numColumns = numColumns;
		dataTable = HashBasedTable.create(numRows, numColumns);
	}

	public Rating get(int row, int column) {
		return dataTable.get(row, column);
	}

	public void set(int row, int column, Rating value) {
		dataTable.put(row, column, value);
	}

	public Map<Integer, Rating> row(int rowId) {
		return dataTable.row(rowId);
	}

	public Map<Integer, Rating> column(int columnId) {
		return dataTable.column(columnId);
	}

	public int numRows() {
		return numRows;
	}

	public int numColumns() {
		return numColumns;
	}

	/**
	 * @return a copy of sparse matrix in the class of {@code SparseMatrix}
	 */
	public SparseMatrix rateMatrix() {
		Table<Integer, Integer, Double> data = HashBasedTable.create();
		Multimap<Integer, Integer> colMap = HashMultimap.create();

		for (Cell<Integer, Integer, Rating> cell : dataTable.cellSet()) {
			Integer row = cell.getColumnKey();
			Integer col = cell.getColumnKey();

			data.put(row, col, cell.getValue().getRate());
			colMap.put(col, row);
		}

		return new SparseMatrix(numRows, numColumns, data, colMap);
	}

}
