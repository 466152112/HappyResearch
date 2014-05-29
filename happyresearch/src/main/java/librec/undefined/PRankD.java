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
import librec.data.DenseVector;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.SymmMatrix;
import librec.intf.IterativeRecommender;

/**
 * Neil Hurley, <strong>Personalized Ranking with Diversity</strong>, RecSys
 * 2013.
 * 
 * <p>
 * Our implementation refers to the algorithm illustrated in Figure 3, i.e.,
 * learning by sampling-based stochastic gradient decent (SGD).
 * </p>
 * 
 * @author guoguibing
 * 
 */
public class PRankD extends IterativeRecommender {

	// item popularity (probabilities)
	private DenseVector s;

	// item correlations
	private SymmMatrix itemCorrs;

	// similarity filter
	private double alpha;

	public PRankD(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "PRankD";
		isRankingPred = true;
	}

	@Override
	protected void initModel() {
		super.initModel();

		// compute item popularity
		s = new DenseVector(numItems);
		double maxUsers = 0;
		for (int j = 0; j < numItems; j++) {
			int users = trainMatrix.columnSize(j);

			if (maxUsers < users)
				maxUsers = users;

			s.set(j, users);
		}

		for (int j = 0; j < numItems; j++) {
			// normalization to obtain the probability
			s.set(j, s.get(j) / maxUsers);
		}

		// compute item correlations by cosine similarity
		alpha = 20;
		itemCorrs = buildCorrs(false);
	}

	/**
	 * override this approach to transform item similarity
	 */
	protected double correlation(SparseVector iv, SparseVector jv) {
		double sim = correlation(iv, jv, "cos");

		// to obtain a greater spread of diversity values
		return Math.tanh(alpha * sim);
	}

	@Override
	protected void buildModel() {
		for (int iter = 1; iter <= maxIters; iter++) {

			errs = 0;
			loss = 0;

			// for each rated user-item (u,i) pair
			for (MatrixEntry me : trainMatrix) {
				int u = me.row();
				int i = me.column();
				double rui = me.get();
				if (rui <= 0)
					continue;

				// draw an item j not rated by user u with probability proportional to popularity
				int j = -1;
				double ruj = 0;
				for (int k = 0; k < numItems; k++) {
					double ruk = trainMatrix.get(u, k);
					if (ruk <= 0) {
						// unrated
						double prob = s.get(k);
						double rand = Randoms.random();
						if (prob < rand) {
							j = k;
							break;
						}
					}
				}

				// compute predictions
				double pui = predict(u, i);
				double puj = predict(u, j);
				double dij = itemCorrs.get(i, j);

				double e = s.get(j) * (pui - puj - dij * (rui - ruj));
				double ye = -lRate * e;

				errs += e * e;
				loss += e * e;

				// update vectors
				DenseVector pu = P.row(u), qi = Q.row(i), qj = Q.row(j);

				P.setRow(u, pu.minus(qi.minus(qj).scale(ye)));
				Q.setRow(i, qi.minus(pu.scale(ye)));
				Q.setRow(j, qj.add(pu.scale(ye)));
			}

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}
}
