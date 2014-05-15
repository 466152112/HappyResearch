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

import librec.data.DenseMatrix;
import librec.data.SparseMatrix;
import librec.intf.IterativeRecommender;

/**
 * Xia Ning and George Karypis, <strong>SLIM: Sparse Linear Methods for Top-N
 * Recommender Systems</strong>, ICDM 2011.
 * 
 * @author guoguibing
 * 
 */
public class SLIM extends IterativeRecommender {

	private DenseMatrix itemCorrs; // ~ W

	public SLIM(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "SLIM";
		isRankingPred = true;
	}

	@Override
	protected void initModel() {
		itemCorrs = new DenseMatrix(numItems, numItems);
		itemCorrs.init();

		// set diagonal entries to 0
		for (int i = 0; i < numItems; i++)
			itemCorrs.set(i, i, 0);
	}

	@Override
	protected void buildModel() {
		for (int iter = 1; iter <= maxIters; iter++) {
			
		}
	}

}
