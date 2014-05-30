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

import happy.coding.io.Strings;
import happy.coding.math.Randoms;

import java.util.List;
import java.util.Map.Entry;

import librec.data.DenseVector;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.SymmMatrix;
import librec.ranking.RankALS;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

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
public class PRankD extends RankALS {

	// item importance
	private DenseVector s;

	// item correlations
	private SymmMatrix itemCorrs;

	// similarity filter
	private double alpha;

	// user, item, item sampling probability
	private Table<Integer, Integer, Double> probs;

	public PRankD(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "PRankD";
		isRankingPred = true;
	}

	@Override
	protected void initModel() {
		super.initModel();

		// pre-processing: binarize training data
		super.binary(trainMatrix);

		alpha = cf.getDouble("PRankD.alpha");

		// compute item popularity
		s = new DenseVector(numItems);
		double maxUsers = 0;

		for (int j = 0; j < numItems; j++) {
			int users = trainMatrix.columnSize(j);

			if (maxUsers < users)
				maxUsers = users;

			s.set(j, users);
		}

		// compute item sampling probability
		probs = HashBasedTable.create();
		for (int u = 0; u < numUsers; u++) {
			// unrated items
			List<Integer> items = trainMatrix.rowZeros(u);

			double sum = 0;
			for (int j : items) {
				sum += s.get(j);
			}

			// add probs
			for (int j : items) {
				probs.put(u, j, s.get(j) / sum);
			}
		}

		// compute item relative importance
		for (int j = 0; j < numItems; j++) {
			s.set(j, s.get(j) / maxUsers);
		}

		// compute item correlations by cosine similarity
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
				double sum = 0, rand = Randoms.random();
				for (Entry<Integer, Double> en : probs.row(u).entrySet()) {
					int k = en.getKey();
					double prob = en.getValue();

					sum += prob;
					if (sum < rand) {
						j = k;
						break;
					}
				}
				double ruj = trainMatrix.get(u, j);

				// compute predictions
				double pui = predict(u, i), puj = predict(u, j);
				double dij = Math.sqrt(1 - itemCorrs.get(i, j));

				double e = s.get(j) * (pui - puj - dij * (rui - ruj));
				double ye = -lRate * e;

				errs += e * e;
				loss += e * e;

				// update vectors
				DenseVector pu = P.row(u), qi = Q.row(i), qj = Q.row(j);
				DenseVector yepu = pu.scale(ye);

				P.setRow(u, pu.minus(qi.minus(qj).scale(ye)));
				Q.setRow(i, qi.minus(yepu));
				Q.setRow(j, qj.add(yepu));
			}

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { binaryHold, (float) alpha, (float) lRate, maxIters }, ",");
	}
}
