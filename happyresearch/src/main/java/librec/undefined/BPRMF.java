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

import happy.coding.math.Randoms;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.intf.IterativeRecommender;

/**
 * 
 * Rendle et al., <strong>BPR: Bayesian Personalized Ranking from Implicit
 * Feedback</strong>, UAI 2009.
 * 
 * @author guoguibing
 * 
 */
public class BPRMF extends IterativeRecommender {

	public BPRMF(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "BPRMF";
		isRankingPred = true;
	}

	@Override
	protected void buildModel() {
		for (int iter = 1; iter <= maxIters; iter++) {
			// draw (u, i, j) from Ds

			int u, i, j;

			while (true) {
				u = Randoms.uniform(numUsers);

				SparseVector pu = trainMatrix.row(u);

				if (pu.getCount() == 0)
					continue;

			}
		}
	}
}
