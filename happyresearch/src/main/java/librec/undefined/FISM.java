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
import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;
import librec.intf.IterativeRecommender;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

/**
 * Kabbur et al., FISM: Factored Item Similarity Models for Top-N Recommender
 * Systems, KDD 2013.
 * 
 * @author guoguibing
 * 
 */
public class FISM extends IterativeRecommender {

	private double rho, alpha;
	private int nnz;

	public FISM(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		isRankingPred = true;
	}

	@Override
	protected void initModel() {
		P = new DenseMatrix(numItems, numFactors);
		Q = new DenseMatrix(numItems, numFactors);
		P.init();
		Q.init();

		userBiases = new DenseVector(numUsers);
		itemBiases = new DenseVector(numItems);
		userBiases.init();
		itemBiases.init();

		nnz = trainMatrix.size();
		rho = cf.getDouble("FISM.rho");
		alpha = cf.getDouble("FISM.alpha");

		// pre-processing: binarize training data
		// super.binary(trainMatrix);
	}

	@Override
	protected void buildModel() {

		int sampleSize = (int) (rho * nnz);
		int totalSize = numUsers * numItems;

		for (int iter = 1; iter <= numIters; iter++) {

			errs = 0;
			loss = 0;

			// temporal data
			DenseMatrix PS = new DenseMatrix(numItems, numFactors);
			DenseMatrix QS = new DenseMatrix(numItems, numFactors);

			// new training data by sampling negative values
			Table<Integer, Integer, Double> R = trainMatrix.getDataTable();

			// make a random sample of negative feedback 
			int[] indices = null;
			try {
				indices = Randoms.nextNoRepeatIntArray(sampleSize, totalSize - sampleSize);
			} catch (Exception e) {
				e.printStackTrace();
			}
			int index = 0, count = 0;
			for (int u = 0; u < numUsers; u++) {
				for (int j = 0; j < numItems; j++) {
					double ruj = trainMatrix.get(u, j);
					if (ruj != 0)
						continue; // rated items

					if (count++ == indices[index]) {
						R.put(u, j, 0.0);
						index++;
					}
				}
			}

			// update throughout each user-item-rating (u, j, ruj) cell 
			for (Cell<Integer, Integer, Double> cell : R.cellSet()) {
				int u = cell.getRowKey();
				int j = cell.getColumnKey();
				double ruj = cell.getValue();

				// for efficiency, use the below code to predict ruj instead of simply using "predict(u,j)"
				SparseVector Ru = trainMatrix.row(u);
				double bu = userBiases.get(u), bj = itemBiases.get(j);

				double pred = bu + bj, sum_ij = 0;
				double wu = Math.pow(Ru.getCount() - 1, -alpha);
				for (VectorEntry ve : Ru) {
					int i = ve.index();
					if (i != j)
						sum_ij += DenseMatrix.rowMult(P, i, Q, j);
				}
				pred += wu * sum_ij;

				double euj = pred - ruj;

				errs += euj * euj;
				loss += euj * euj;

				// update bu
				userBiases.add(u, -lRate * (euj + regU * bu));
				
				// update bj
				itemBiases.add(j, -lRate * (euj + regI * bj));

				loss += regU * bu * bu + regI * bj * bj;

				// update qjf
				for (int f = 0; f < numFactors; f++) {
					double qjf = Q.get(j, f);

					double sum_i = 0;
					for (VectorEntry ve : Ru) {
						int i = ve.index();
						if (i != j) {
							sum_i += P.get(i, f);
						}
					}

					double delta = euj * wu * sum_i + regI * qjf;
					QS.add(j, f, -lRate * delta);

					loss += regI * qjf * qjf;
				}

				// update pif
				for (VectorEntry ve : Ru) {
					int i = ve.index();
					if (i != j) {
						for (int f = 0; f < numFactors; f++) {
							double pif = P.get(i, f);
							double delta = euj * wu * Q.get(j, f) + regI * pif;
							PS.add(i, f, -lRate * delta);

							loss += regI * pif * pif;
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
		double pred = userBiases.get(u) + itemBiases.get(j);

		double sum = 0;
		SparseVector Ru = trainMatrix.row(u);
		for (VectorEntry ve : Ru) {
			int i = ve.index();
			if (i != j) {
				sum += DenseMatrix.rowMult(P, i, Q, j);
			}
		}

		return pred + Math.pow(Ru.getCount() - 1, -alpha) * sum;
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { binThold, (float) lRate, numIters }, ",");
	}
}
