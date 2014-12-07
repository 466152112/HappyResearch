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

import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;
import librec.intf.IterativeRecommender;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

/**
 * FUSM: Factored User Similarity Models for Top-N Recommender Systems
 * 
 * @author guoguibing
 * 
 */
public class FUSMrmse extends IterativeRecommender {

	private float rho, alpha;
	private int nnz;

	private float regLambda, regBeta, regGamma;

	public FUSMrmse(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		isRankingPred = true;
	}

	@Override
	protected void initModel() {
		P = new DenseMatrix(numUsers, numFactors);
		Q = new DenseMatrix(numUsers, numFactors);
		P.init(0.01);
		Q.init(0.01);

		userBias = new DenseVector(numUsers);
		itemBias = new DenseVector(numItems);
		userBias.init(0.01);
		itemBias.init(0.01);

		nnz = trainMatrix.size();
		rho = cf.getFloat("FISM.rho");
		alpha = cf.getFloat("FISM.alpha");

		regLambda = cf.getFloat("FISM.reg.lambda");
		regBeta = cf.getFloat("FISM.reg.beta");
		regGamma = cf.getFloat("FISM.reg.gamma");

	}

	@Override
	protected void buildModel() {

		int sampleSize = (int) (rho * nnz);
		int totalSize = trainMatrix.numRows() * numItems;

		for (int iter = 1; iter <= numIters; iter++) {

			errs = 0;
			loss = 0;

			// temporal data
			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);
			DenseMatrix QS = new DenseMatrix(numUsers, numFactors);

			// new training data by sampling negative values
			Table<Integer, Integer, Double> R = trainMatrix.getDataTable();

			// make a random sample of negative feedback (total - nnz)
			List<Integer> indices = null;
			try {
				indices = Randoms.randInts(sampleSize, 0, totalSize - nnz);
			} catch (Exception e) {
				e.printStackTrace();
			}
			int index = 0, count = 0;
			boolean isDone = false;
			for (int u = 0; u < trainMatrix.numRows(); u++) {
				for (int j = 0; j < numItems; j++) {
					double ruj = trainMatrix.get(u, j);
					if (ruj != 0)
						continue; // rated items

					if (count++ == indices.get(index)) {
						R.put(u, j, 0.0);
						index++;
						if (index >= indices.size()) {
							isDone = true;
							break;
						}
					}
				}
				if (isDone)
					break;
			}

			// update throughout each user-item-rating (u, j, ruj) cell
			for (Cell<Integer, Integer, Double> cell : R.cellSet()) {
				int u = cell.getRowKey();
				int j = cell.getColumnKey();
				double ruj = cell.getValue();

				// for efficiency, use the below code to predict ruj instead of
				// simply using "predict(u,j)"
				SparseVector Cj = trainMatrix.column(j);
				double bu = userBias.get(u), bj = itemBias.get(j);

				double sum_vu = 0;
				int cnt = 0;
				for (VectorEntry ve : Cj) {
					int v = ve.index();
					// for training, i and j should be equal as j may be rated
					// or unrated
					if (v != u) {
						sum_vu += DenseMatrix.rowMult(P, v, Q, u);
						cnt++;
					}
				}

				double wu = cnt > 0 ? Math.pow(cnt, -alpha) : 0;
				double puj = bu + bj + wu * sum_vu;

				double euj = puj - ruj;

				errs += euj * euj;
				loss += euj * euj;

				// update bu
				userBias.add(u, -lRate * (euj + regLambda * bu));

				// update bj
				itemBias.add(j, -lRate * (euj + regGamma * bj));

				loss += regLambda * bu * bu + regGamma * bj * bj;

				// update quf
				for (int f = 0; f < numFactors; f++) {
					double quf = Q.get(u, f);

					double sum_v = 0;
					for (VectorEntry ve : Cj) {
						int v = ve.index();
						if (v != u) {
							sum_v += P.get(v, f);
						}
					}

					double delta = euj * wu * sum_v + regBeta * quf;
					QS.add(u, f, -lRate * delta);

					loss += regBeta * quf * quf;
				}

				// update pvf
				for (VectorEntry ve : Cj) {
					int v = ve.index();
					if (v != u) {
						for (int f = 0; f < numFactors; f++) {
							double pvf = P.get(v, f);
							double delta = euj * wu * Q.get(u, f) + regBeta
									* pvf;
							PS.add(v, f, -lRate * delta);

							loss += regBeta * pvf * pvf;
						}
					}
				}
			}

			P = P.add(PS);
			Q = Q.add(QS);

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int j) {
		double sum = 0;
		int count = 0;

		SparseVector Cj = trainMatrix.column(j);
		for (VectorEntry ve : Cj) {
			int v = ve.index();
			// for test, i and j will be always unequal as j is unrated
			if (v != u) {
				sum += DenseMatrix.rowMult(P, v, Q, u);
				count++;
			}
		}

		double wj = count > 0 ? Math.pow(count, -alpha) : 0;

		return userBias.get(u) + itemBias.get(j) + wj * sum;
	}

	@Override
	public String toString() {
		return super.toString()
				+ ","
				+ Strings.toString(new Object[] { rho, alpha, regLambda,
						regBeta, regGamma }, ",");
	}
}
