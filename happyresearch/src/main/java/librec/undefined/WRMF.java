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

import happy.coding.io.Logs;
import happy.coding.system.Debug;

import java.util.HashMap;
import java.util.Map;

import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.DiagMatrix;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;
import librec.intf.IterativeRecommender;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * <ul>
 * <li><strong>Binary ratings:</strong> Pan et al., <strong>One-class
 * Collaborative Filtering</strong>, ICDM 2008.</li>
 * <li><strong>Real ratings:</strong> Hu et al., <strong>Collaborative filtering
 * for implicit feedback datasets</strong>, ICDM 2008.</li>
 * </ul>
 * 
 * @author guoguibing
 * 
 */
public class WRMF extends IterativeRecommender {

	private boolean isBinaryRating;
	private double alpha;

	private Map<Integer, Integer> rowSizes;

	public WRMF(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "WRMF"; // weighted low-rank matrix factorization 
		isRankingPred = true;

		isBinaryRating = (maxRate == minRate);

		if (Debug.ON) {
			// default parameters suggested by the papers

			alpha = 40;
			maxIters = 10;
		}

		rowSizes = new HashMap<>();
		for (int u = 0; u < numUsers; u++)
			rowSizes.put(u, trainMatrix.rowSize(u));

	}

	@Override
	protected void buildModel() {
		// for consistency
		DenseMatrix X = P, Y = Q;

		// updating by using alternative least square (ALS) due to large amount of entries to be processed (SGD will be too slow)
		for (int iter = 1; iter <= maxIters; iter++) {

			if (verbose)
				Logs.debug("Fold [{}] current progress at iteration = {}", fold, iter);

			// Step 1: update user factors;
			DenseMatrix Yt = Y.transpose();
			DenseMatrix YtY = Yt.mult(Y);
			for (int u = 0; u < numUsers; u++) {
				if (verbose)
					Logs.debug("Fold [{}] current progress at iteration = {}, user = {}", fold, iter, u);

				// diagonal matrix C^u for each user
				Table<Integer, Integer, Double> cuData = HashBasedTable.create();

				//for (int j = 0; j < numItems; j++)
				//	cuData.put(j, j, wc(u, j));
				for (VectorEntry ve : trainMatrix.row(u)) {
					int j = ve.index();
					cuData.put(j, j, wc(u, j));
				}

				DiagMatrix Cu = new DiagMatrix(numItems, numItems, cuData);
				SparseVector pu = trainMatrix.row(u);

				// binarize real values
				for (VectorEntry ve : pu) {
					double ruj = ve.get();
					ve.set(ruj > 0 ? 1 : 0);
				}

				// Cu - I
				DiagMatrix CuI = Cu.minus(DiagMatrix.eye(numItems));
				// YtY + Yt * (Cu - I) * Y
				DenseMatrix YtCuY = YtY.add(Yt.mult(CuI).mult(Y));
				// (YtCuY + lambda * I)^-1
				DenseMatrix Wu = YtCuY.add(DenseMatrix.eye(numFactors).scale(regU)).inv();
				// Yt * Cu
				DenseMatrix YtCu = Yt.mult(Cu);

				DenseVector xu = Wu.mult(YtCu).mult(pu);

				// udpate user factors
				X.setRow(u, xu);
			}

			// Step 2: update item factors;
			DenseMatrix Xt = X.transpose();
			DenseMatrix XtX = Xt.mult(X);
			for (int i = 0; i < numItems; i++) {
				if (verbose)
					Logs.debug("Fold [{}] current progress at iteration = {}, item = {}", fold, iter, i);
				// diagonal matrix C^i for each item
				Table<Integer, Integer, Double> ciData = HashBasedTable.create();

				//for (int u = 0; u < numUsers; u++)
				//	ciData.put(u, u, wc(u, i));

				for (VectorEntry ve : trainMatrix.column(i)) {
					int u = ve.index();
					ciData.put(u, u, wc(u, i));
				}

				DiagMatrix Ci = new DiagMatrix(numUsers, numUsers, ciData);
				SparseVector pi = trainMatrix.column(i);

				// binarize real values
				for (VectorEntry ve : pi) {
					double ruj = ve.get();
					ve.set(ruj > 0 ? 1 : 0);
				}

				// Ci - I
				DiagMatrix CiI = Ci.minus(DiagMatrix.eye(numUsers));
				// XtX + Xt * (Ci - I) * X
				DenseMatrix XtCiX = XtX.add(Xt.mult(CiI).mult(X));
				// (XtCiX + lambda * I)^-1
				DenseMatrix Wi = XtCiX.add(DenseMatrix.eye(numFactors).scale(regI)).inv();
				// Xt * Ci
				DenseMatrix XtCi = Xt.mult(Ci);

				DenseVector yi = Wi.mult(XtCi).mult(pi);

				// udpate item factors
				Y.setRow(i, yi);
			}

		}
	}

	// compute the weighting or confidence (wc) of a rating ruj
	private double wc(int u, int j) {
		double wc = 0;

		if (isBinaryRating) {
			// user-oriented weighting 
			wc = rowSizes.get(u);
		} else {
			// rating-based confidence
			wc = 1 + alpha * trainMatrix.get(u, j);
		}

		return wc;
	}

	@Override
	public String toString() {
		return String.format("%d,%g,%g,%g,%d", new Object[] { numFactors, regU, regI, alpha, maxIters });
	}

}
